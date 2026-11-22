# Cơ chế truyền file

Tài liệu này trả lời câu hỏi: **gửi một file tới 100 MB qua đường UDP, nơi mỗi gói chỉ chở được khoảng 1 KB và có thể mất bất cứ lúc nào, làm sao file tới đủ, đúng từng byte, đủ nhanh, và không làm hại máy người nhận?**

Tài liệu [Tra-loi-giao-thuc-truyen-file.md](Tra-loi-giao-thuc-truyen-file.md) là bản thống nhất giao thức giữa hai thành viên, ghi các quyết định D1 đến D11. Tài liệu này trình bày lại cùng cơ chế đó theo chuỗi thiết kế: vì sao có từng phần.

Cách trình bày giống các tài liệu cơ chế khác: đi từ cách ngây thơ, tìm lỗ hổng, phân tích cần gì, rồi sinh ra bước tiếp theo. Ở mỗi chỗ phải chọn có so sánh các phương án.

Nhân vật:
- **An** gửi file.
- **Bình** nhận file.
- **Minh** là người trong phòng có ý xấu, có thể gửi file "độc" cho Bình.

---

## Bước 0 — Gửi cả file trong một lá thư

**Làm gì:** An nhét nguyên một cuốn album ảnh vào một phong bì rồi gửi đi như thư thường.

**Về kỹ thuật:** đọc cả file vào bộ nhớ, đặt vào một bản tin và gửi qua `P2pDataChannel` như một tin nhắn.

**→ Lỗ hổng:** phong bì quá to, không gửi được.

**Phân tích vấn đề:**
- `P2pDataChannel` chỉ chấp nhận bản tin tối đa 1 MB sau khi ghép mảnh ([P2pDataChannel.java:239](../p2p-core/src/main/java/com/datn/chatp2p/p2p/channel/P2pDataChannel.java#L239)). Giới hạn này có để chống làm cạn bộ nhớ bên nhận (xem [Co-che-gui-tin-tin-cay.md](Co-che-gui-tin-tin-cay.md), Bước 6).
- Kể cả nếu nới giới hạn ra: một file 100 MB bị chia thành khoảng 160.000 mảnh nhỏ. Mất **một** mảnh bất kỳ là phải gửi lại **cả** 100 MB.
- Đọc cả file vào bộ nhớ cũng làm ứng dụng ngốn RAM.
- Bình còn không có quyền từ chối: file cứ thế đổ về.

**Để giải quyết thì cần gì:**
- **R1:** chia file thành **nhiều phần nhỏ**, mỗi phần gửi và xác nhận riêng. Mất phần nào thì chỉ gửi lại phần đó.
- **R2:** đọc file từ ổ đĩa theo từng phần, ghi xuống ổ đĩa theo từng phần, không giữ cả file trong RAM.
- **R3:** người nhận phải **đồng ý trước** khi dữ liệu bắt đầu chảy về.

**Xét các phương án:**
- **Tải file lên server, người nhận tải về:** đơn giản, có thể tiếp tục khi người nhận vắng mặt. Nhưng server phải lưu trữ và chịu băng thông, và file nằm trên server, đi ngược mục tiêu P2P.
- **Mở một kết nối TCP riêng chỉ để chở file:** TCP tự lo gửi lại. Nhưng như đã phân tích ở tài liệu gửi tin tin cậy, việc mở lối xuyên NAT bằng TCP kém ổn định, và TURN của dự án mới chạy UDP.
- **Chia file thành từng phần, gửi qua chính kết nối P2P đã mã hoá, tự làm xác nhận:** dùng lại đường truyền và lớp mã hoá sẵn có. Đây là hướng được chọn.

---

## Bước 1 — Hỏi trước, rồi gửi từng phần

**Làm gì:**
- Trước khi gửi, An gửi một tờ giới thiệu: "Tôi muốn gửi file album.zip, 40 MB, chia thành 10.000 phần, mỗi phần 4 KB, dấu kiểm tra của cả file là …".
- Bình thấy tờ giới thiệu hiện lên với hai nút "Nhận" và "Từ chối".
- Tờ giới thiệu sai thông số, file quá 100 MB, hay Bình đang nhận một file khác của An, thì máy Bình **tự từ chối** mà không cần hỏi.
- Bình bấm "Nhận" thì An mới bắt đầu gửi từng phần. Mỗi phần ghi rõ "phần số mấy".
- Tờ giới thiệu cũng có thể rơi dọc đường, nên An cứ mỗi giây gửi lại một lần cho tới khi có câu trả lời. Sau 60 giây không ai trả lời thì An thôi.

**Về kỹ thuật:** có 5 loại bản tin.

| Bản tin | Chiều | Dùng để |
|---|---|---|
| `FILE_OFFER` | gửi → nhận | `fileId`, tên, kích thước, `chunkSize`, `totalChunks`, `sha256` |
| `FILE_ANSWER` | nhận → gửi | Đồng ý, hoặc từ chối kèm lý do: `REJECTED`, `FILE_TOO_LARGE`, `INVALID_OFFER`, `BUSY` |
| `FILE_CHUNK` | gửi → nhận | Một phần của file |
| `FILE_ACK` | nhận → gửi | Báo đã nhận những phần nào (Bước 2) |
| `FILE_CANCEL` | hai chiều | Huỷ, kèm lý do |

Các giới hạn:
- File tối đa 100 MB.
- Mỗi phần mặc định 4 KB. Bên nhận chấp nhận tối đa 16 KB mỗi phần.
- `totalChunks` phải đúng bằng `ceil(kích thước / chunkSize)`.
- `sha256` phải là 64 ký tự hex.

Bên gửi đọc từng phần từ file bằng `FileChannel`. Bên nhận ghi từng phần vào đúng vị trí trong một file tạm `.part`.

**Trong code:**
- Giới hạn và kiểm tra tờ giới thiệu: [FileTransferLimits.java:27-31](../p2p-core/src/main/java/com/datn/chatp2p/p2p/filetransfer/FileTransferLimits.java#L27-L31), [74-94](../p2p-core/src/main/java/com/datn/chatp2p/p2p/filetransfer/FileTransferLimits.java#L74-L94).
- Bên gửi mở file, tính SHA-256, tạo tờ giới thiệu: [FileSender.java:40-70](../p2p-core/src/main/java/com/datn/chatp2p/p2p/filetransfer/FileSender.java#L40-L70).
- Bên nhận tự từ chối khi thông số sai hoặc khi đang bận, nếu không thì chờ người dùng bấm: [FileTransferManager.java:325-351](../client-javafx/src/main/java/com/datn/chatp2p/client/session/FileTransferManager.java#L325-L351).
- Nút "Nhận" và "Từ chối": [TranscriptCell.java:192-193](../client-javafx/src/main/java/com/datn/chatp2p/client/view/TranscriptCell.java#L192-L193).
- Gửi lại tờ giới thiệu mỗi giây, chờ tối đa 60 giây: [ReliableTransferConfig.java:17-18](../p2p-core/src/main/java/com/datn/chatp2p/p2p/filetransfer/ReliableTransferConfig.java#L17-L18).
- Kiểm tra từng phần (đúng `fileId`, chỉ số hợp lệ, độ dài đúng, chưa nhận) rồi ghi vào đúng vị trí: [FileReceiver.java:134-167](../p2p-core/src/main/java/com/datn/chatp2p/p2p/filetransfer/FileReceiver.java#L134-L167).

**Đáp ứng yêu cầu:**
- **R1:** file được chia thành các phần 4 KB.
- **R2:** đọc và ghi từng phần trên ổ đĩa.
- **R3:** người nhận phải bấm "Nhận"; thông số sai thì tự từ chối.

**→ Lỗ hổng:** chưa có cách biết **phần nào đã tới, phần nào bị mất**.

**Phân tích vấn đề:**
- Gửi một phần rồi chờ xác nhận mới gửi phần tiếp theo thì quá chậm. Mỗi phần mất trọn một vòng đi về. Qua TURN, một vòng đi về khoảng 86 ms, nên 10.000 phần mất gần 15 phút.
- Gửi dồn tất cả mà không chờ thì làm tràn hàng đợi nhận của ice4j, vốn chỉ chứa khoảng 100 gói, và mất hàng loạt.
- Xác nhận kiểu "đã nhận tới phần số N" thì không nói được chuyện phần 5 mất nhưng phần 6 đến 20 đã tới. An sẽ phải gửi lại cả phần 6 đến 20.

**Để giải quyết thì cần gì:**
- **R4:** cho phép nhiều phần **đang trên đường cùng lúc**, nhưng có giới hạn để không làm tràn bên nhận.
- **R5:** xác nhận phải nói được **chính xác những phần nào đã tới**, kể cả các phần nằm sau một chỗ hổng.
- **R6:** phần mất phải được gửi lại. Đường truyền chết hẳn thì phải dừng, không gửi lại mãi.

---

## Bước 2 — Cửa sổ trượt và xác nhận có chọn lọc

**Làm gì:**
- An cho tối đa 8 phần đang trên đường cùng lúc. Phần nào được xác nhận thì An gửi tiếp phần mới, giống một cửa sổ trượt dần qua file.
- Bình gửi giấy xác nhận dạng "đã nhận liền mạch tới phần 40, ngoài ra có thêm phần 42, 43, 45". An biết ngay chỉ cần gửi lại phần 41 và 44.
- Bình gửi giấy xác nhận sau mỗi 4 KB mới nhận theo thứ tự, hoặc sau 50 ms không có phần mới.
- Phần nào quá hạn chưa được xác nhận thì An gửi lại. Thời gian chờ gấp đôi sau mỗi lần: 500 ms, 1 giây, 2 giây, tối đa 4 giây. Gửi lại 10 lần vẫn không được thì An báo đường truyền có vấn đề.

**Về kỹ thuật:**
- `FILE_ACK` mang ba trường:
  - `contiguousUpTo`: đã có liền mạch từ phần 0 tới phần này.
  - `receivedAbove`: danh sách tối đa 64 chỉ số đã nhận nằm sau chỗ hổng.
  - `verified`: dùng ở Bước 4.
- Mỗi lần gửi lại, phần đó được mã hoá lại với nonce mới, nên không bị cửa sổ chống phát lại chặn nhầm.

**Trong code:**
- Cấu hình: cửa sổ, thời gian chờ, số lần gửi lại tối đa: [ReliableTransferConfig.java:17-18](../p2p-core/src/main/java/com/datn/chatp2p/p2p/filetransfer/ReliableTransferConfig.java#L17-L18), [28-31](../p2p-core/src/main/java/com/datn/chatp2p/p2p/filetransfer/ReliableTransferConfig.java#L28-L31).
- Cửa sổ 8 phần cho mạng thật, giữ dưới hàng đợi 100 gói của ice4j: [FileTransferManager.java:86-93](../client-javafx/src/main/java/com/datn/chatp2p/client/session/FileTransferManager.java#L86-L93).
- Nhịp gửi xác nhận: 4 KB, hoặc 50 ms, tối đa 64 chỉ số: [FileTransferManager.java:96-107](../client-javafx/src/main/java/com/datn/chatp2p/client/session/FileTransferManager.java#L96-L107).
- Bên gửi tin cậy với cửa sổ trượt: [ReliableChunkSender.java](../p2p-core/src/main/java/com/datn/chatp2p/p2p/filetransfer/ReliableChunkSender.java). Mô tả vòng đời ở đầu lớp.

**Đáp ứng yêu cầu:**
- **R4:** tối đa 8 phần đang bay cùng lúc.
- **R5:** `contiguousUpTo` kèm `receivedAbove` cho biết chính xác những phần đã tới.
- **R6:** gửi lại với thời gian chờ tăng dần, tối đa 10 lần.

**→ Lỗ hổng:** chỉ cần mất **1%** số gói là tốc độ sụt **gần 100 lần**.

**Phân tích vấn đề:**
- Đo bằng `FileTransferBenchmarkTest`, gửi 4 MB qua ICE thật trên localhost. Với phiên bản ban đầu (phần 512 byte, cửa sổ 64): không mất gói được 6,4 MB/s, nhưng mất 1% gói chỉ còn **0,20 MB/s**.
- Nguyên nhân: một phần bị mất làm cả cửa sổ dừng lại chờ hết hạn 500 ms. Trong lúc đó các phần gửi sau nó **đã tới nơi**, và giấy xác nhận đã báo điều đó. Vậy mà An vẫn đứng chờ.

**Để giải quyết thì cần gì:**
- **R7:** phát hiện một phần bị mất **sớm hơn** hạn chờ, dựa vào việc các phần gửi sau nó đã được xác nhận.
- **R8:** chọn kích thước phần và kích thước cửa sổ **bằng số đo**, không phải bằng cảm tính.

---

## Bước 3 — Gửi lại nhanh và chọn thông số bằng số đo

**Làm gì:**
- Nếu An thấy đã có ít nhất 3 phần **gửi sau** phần 41 được xác nhận, mà phần 41 vẫn chưa, thì An gửi lại phần 41 ngay, không chờ hết nửa giây.
- Con số 3, chứ không phải 1, là để chịu được chuyện thư thỉnh thoảng đến lộn thứ tự một chút.
- Mỗi phần được gửi lại nhanh tối đa 50 lần, để không tạo vòng lặp vô tận khi đường truyền thật sự hỏng.
- Kích thước phần và cửa sổ được chọn sau khi đo thử nhiều cách kết hợp.

**Về kỹ thuật:**
- Gửi lại nhanh kiểu TCP RACK. Việc so sánh dựa trên **thứ tự lần gửi**, không dựa trên chỉ số phần. Nhờ vậy, một bản gửi lại mà lại bị mất cũng được phát hiện theo đúng cách đó.
- Kết quả đo khi chọn thông số:
  - Phần 4 KB, khoảng 7,3 KB sau khi mã hoá, đi thành khoảng 7 datagram.
  - Phần 8 KB nhanh ngang khi mạng sạch, nhưng sụt mạnh khi mất gói: mỗi phần gồm khoảng 14 mảnh, mất 1 mảnh là mất cả phần.
  - Cửa sổ 16 nhanh ngang cửa sổ 8 khi mạng sạch, nhưng dễ làm tràn hàng đợi nhận.

**Trong code:**
- Ngưỡng 3 và giới hạn 50 lần: [ReliableChunkSender.java:82](../p2p-core/src/main/java/com/datn/chatp2p/p2p/filetransfer/ReliableChunkSender.java#L82), [89](../p2p-core/src/main/java/com/datn/chatp2p/p2p/filetransfer/ReliableChunkSender.java#L89).
- Phần 4 KB cho mạng thật, kèm ghi chú số đo: [FileTransferManager.java:71-85](../client-javafx/src/main/java/com/datn/chatp2p/client/session/FileTransferManager.java#L71-L85).

**Kết quả đo** (4 MB qua ICE thật trên localhost, trung vị 3 lần; [Tai-lieu-ky-thuat.md](Tai-lieu-ky-thuat.md), mục F.1):

| Mất gói | Trước: phần 512 B, cửa sổ 64 | Sau: phần 4 KB, cửa sổ 8, gửi lại nhanh |
|---|---|---|
| 0% | 6,4 MB/s | 17,9 MB/s |
| 1% | 0,20 MB/s | 18,1 MB/s |
| 3% | 0,12 MB/s | 18,0 MB/s |
| 5% | 0,09 MB/s | 5,5 MB/s |

**Đáp ứng yêu cầu:**
- **R7:** phần mất được gửi lại ngay khi có 3 phần gửi sau nó tới nơi.
- **R8:** thông số chọn theo kết quả đo.

**→ Lỗ hổng:** file tới đủ các phần, nhưng **có đúng là file An định gửi không**? Và **ghi xuống máy Bình có an toàn không**?

**Phân tích vấn đề:**
- **Về tính đúng:** từng phần đã được bảo vệ bằng AES-GCM, nên không ai sửa được trên đường. Nhưng:
  - Một lỗi trong lúc ghép, chẳng hạn ghi sai vị trí hay nhầm giữa hai lần gửi, vẫn có thể làm hỏng file mà không phần nào báo lỗi.
  - File trên máy An có thể bị sửa giữa lúc tính dấu kiểm tra và lúc gửi.
- **Về giấy xác nhận cuối:** An chỉ được coi là gửi xong khi Bình xác nhận "đã kiểm tra, file đúng". Nhưng tờ xác nhận cuối có thể rơi mất. An sẽ chờ mãi, trong khi Bình đã đóng giao dịch.
- **Về an toàn ổ đĩa:** tên file do người gửi tự khai. Minh có thể đặt tên:
  - `../../AppData/Roaming/Microsoft/Windows/Start Menu/Programs/Startup/x.exe` để ghi file ra ngoài thư mục tải về, vào chỗ tự chạy khi khởi động máy;
  - `CON.txt`, tên thiết bị đặc biệt của Windows;
  - trùng tên một file Bình đang có, để ghi đè lên nó.

**Để giải quyết thì cần gì:**
- **R9:** kiểm tra **cả file** sau khi ghép bằng dấu kiểm tra mà người gửi đã báo từ đầu. Không khớp thì xoá.
- **R10:** chỉ coi là thành công khi bên nhận đã kiểm tra xong. Mất tờ xác nhận cuối thì phải **lấy lại được**.
- **R11:** file đang nhận dở **không được trông như file hoàn chỉnh**.
- **R12:** tên file phải được **làm sạch**: không có đường dẫn, không có ký tự cấm, không trùng tên thiết bị, và **không bao giờ ghi đè** file đã có.

---

## Bước 4 — Kiểm tra SHA-256 cả file và ghi file an toàn

**Làm gì:**
- Bình ghi các phần vào một file nháp đuôi `.part`.
- Đủ phần thì Bình tính dấu kiểm tra của cả file và so với dấu An ghi trên tờ giới thiệu.
  - Khớp: đổi tên file nháp thành tên thật trong thư mục Downloads, rồi gửi tờ "đã kiểm tra, file đúng".
  - Không khớp: xoá file nháp và báo huỷ.
- Bình nhớ các file vừa nhận xong trong 30 giây. Trong thời gian đó, nếu An gửi thêm một phần nào của file ấy, nghĩa là An chưa nhận được tờ xác nhận cuối, thì Bình gửi lại tờ xác nhận cuối.
- Phía An, đủ phần mà chưa thấy xác nhận cuối thì gửi lại phần cuối cùng theo thời gian chờ tăng dần, để nhắc Bình.
- Tên file được làm sạch trước khi ghi:
  - bỏ mọi phần đường dẫn;
  - thay các ký tự cấm bằng `_`;
  - tránh tên thiết bị của Windows;
  - cắt còn tối đa 200 ký tự.
- Nếu đã có file trùng tên thì đặt tên khác, chẳng hạn "album (1).zip". Không bao giờ ghi đè.

**Về kỹ thuật:**
- SHA-256 của cả file được tính ngay khi tạo `FILE_OFFER` và gửi kèm theo.
- Bên nhận băm lại file `.part`, so sánh, rồi `Files.move` **không** dùng `REPLACE_EXISTING`.
- Trạng thái của file đã đóng được giữ 30 giây để trả lời các tờ giới thiệu trùng và các phần đến muộn.
- Giao diện hiện "Đã nhận · đã kiểm tra SHA-256".

**Trong code:**
- Kiểm tra SHA-256, sai thì xoá, đúng thì đổi tên không ghi đè: [FileReceiver.java:237-272](../p2p-core/src/main/java/com/datn/chatp2p/p2p/filetransfer/FileReceiver.java#L237-L272), [289-302](../p2p-core/src/main/java/com/datn/chatp2p/p2p/filetransfer/FileReceiver.java#L289-L302).
- Huỷ thì xoá file `.part`: [FileReceiver.java:274-282](../p2p-core/src/main/java/com/datn/chatp2p/p2p/filetransfer/FileReceiver.java#L274-L282).
- Làm sạch tên file: [FileNames.java](../p2p-core/src/main/java/com/datn/chatp2p/p2p/filetransfer/FileNames.java), các bước 1 đến 5 trong `sanitize`.
- Giữ trạng thái 30 giây để gửi lại xác nhận cuối: [FileTransferManager.java:109](../client-javafx/src/main/java/com/datn/chatp2p/client/session/FileTransferManager.java#L109), [521](../client-javafx/src/main/java/com/datn/chatp2p/client/session/FileTransferManager.java#L521).
- Thư mục nhận là `~/Downloads`: [RoomController.java:837-840](../client-javafx/src/main/java/com/datn/chatp2p/client/view/RoomController.java#L837-L840).
- Chữ hiển thị: [FileTransferTexts.java:40-41](../client-javafx/src/main/java/com/datn/chatp2p/client/view/FileTransferTexts.java#L40-L41).

**Đáp ứng yêu cầu:**
- **R9:** kiểm tra SHA-256 cả file.
- **R10:** chỉ thành công khi có `verified = true`; mất tờ xác nhận cuối thì lấy lại được trong 30 giây.
- **R11:** file dở dang mang đuôi `.part`.
- **R12:** tên được làm sạch và không bao giờ ghi đè.

**→ Lỗ hổng còn lại:** truyền đúng và an toàn trên ổ đĩa rồi, nhưng **chậm trên Internet thật** và **mong manh khi mất kết nối**.

**Phân tích vấn đề:**
- **Cửa sổ cố định:** 8 phần × 4 KB = 32 KB cho mỗi vòng đi về. Qua TURN với vòng đi về khoảng 86 ms, tốc độ tối đa chỉ khoảng **0,37 MB/s**, bất kể đường truyền thực sự rộng tới đâu. Con số 18 MB/s ở trên là đo trên localhost.
- **Không tiếp tục được:** kết nối ICE đứt giữa chừng, chẳng hạn khi đổi Wi-Fi, thì giao dịch bị huỷ, file `.part` bị xoá, phải gửi lại từ đầu.
- **Gửi cho nhóm là gửi N lần:** gửi file vào phòng nhóm là gửi riêng cho từng người ([RoomController.java:825-834](../client-javafx/src/main/java/com/datn/chatp2p/client/view/RoomController.java#L825-L834)). Nhóm 7 người thì An phải tải lên 7 lần.
- **Không cảnh báo loại file:** SHA-256 chỉ chứng minh Bình nhận đúng file Minh gửi. Nó không nói gì về chuyện file đó có hại hay không. Ứng dụng chưa cảnh báo khi nhận các file chạy được như `.exe`, `.bat`, `.js`.

**Để giải quyết thì cần gì:**
- **R13:** cửa sổ phải **tự lớn lên** khi đường truyền tốt, và **thu lại** khi bắt đầu mất gói.
- **R14:** kết nối lại thì **tiếp tục** từ những phần đã có.
- **R15:** cảnh báo người dùng trước khi mở các loại file chạy được.

---

## Bước 5 — *(đề xuất, chưa làm)* Cửa sổ tự điều chỉnh, tiếp tục sau khi mất kết nối, cảnh báo file chạy được

**Làm gì:**
- Đường thông thoáng thì An cho thêm dần số phần đang bay. Thấy bắt đầu mất phần thì giảm một nửa.
- Mất kết nối giữa chừng thì Bình giữ lại file nháp và cuốn sổ ghi những phần đã có. Kết nối lại, Bình gửi tờ "tôi đã có những phần này", và An chỉ gửi phần còn thiếu.
- File có đuôi chạy được thì hiện cảnh báo "File này có thể chạy chương trình trên máy bạn. Chỉ mở nếu bạn tin người gửi".

**Về kỹ thuật:**
- Điều khiển tắc nghẽn kiểu AIMD, tăng cộng giảm nhân như TCP Reno. `windowSize` trở thành biến thay đổi theo thời gian thay vì hằng số.
- Tiếp tục truyền: giữ `.part` cùng `BitSet` các phần đã nhận theo `fileId` và `sha256`. `FILE_ANSWER` mang thêm danh sách phần đã có.
- Danh sách đuôi file nguy hiểm kiểm tra trong `FileTransferTexts` hoặc `TranscriptCell`.

**Trong code (dự kiến):** sửa `ReliableChunkSender` để cửa sổ thay đổi theo ACK và theo việc mất gói; sửa `FileReceiver.cancel` để giữ lại `.part` khi lý do huỷ là mất kết nối; thêm trường vào `FileAnswerPayload`.

**Đáp ứng yêu cầu:**
- **R13:** cửa sổ tự điều chỉnh.
- **R14:** tiếp tục được sau khi kết nối lại.
- **R15:** có cảnh báo với file chạy được.

---

## Tóm tắt chuỗi thiết kế

| Bước | Làm gì | Vá được gì | Sinh ra vấn đề gì |
|---|---|---|---|
| 0 | Cả file trong một bản tin | — | Quá giới hạn 1 MB, mất một mảnh là mất hết |
| 1 | `FILE_OFFER`/`FILE_ANSWER`, chia phần, giới hạn 100 MB, tự từ chối offer sai hoặc khi bận | Người nhận đồng ý trước, đọc ghi từng phần | Không biết phần nào mất |
| 2 | Cửa sổ trượt, `contiguousUpTo` + `receivedAbove`, gửi lại 500 ms tới 4 s | Gửi lại đúng phần mất | Mất 1% gói còn 0,20 MB/s |
| 3 | Gửi lại nhanh với ngưỡng 3; phần 4 KB, cửa sổ 8 chọn theo số đo | 18 MB/s ở mức mất 1–3% gói | File có đúng không, ghi có an toàn không |
| 4 | SHA-256 cả file, `.part`, xác nhận `verified`, giữ trạng thái 30 giây, làm sạch tên, không ghi đè | File đúng từng byte, không ghi ra ngoài Downloads | Chậm trên Internet, mất kết nối là làm lại |
| 5 *(đề xuất)* | Cửa sổ AIMD, tiếp tục truyền, cảnh báo file chạy được | Nhanh hơn, bền hơn, an toàn hơn cho người dùng | — |

```
B0 cả file một bản tin
 ──quá lớn, mất là mất hết──────► B1 hỏi trước + chia phần
 ──không biết phần nào mất──────► B2 cửa sổ trượt + ACK chọn lọc
 ──mất 1% gói thì chậm 90 lần───► B3 gửi lại nhanh + thông số theo số đo
 ──đúng chưa, an toàn chưa──────► B4 SHA-256 + .part + làm sạch tên
 ──chậm, mong manh──────────────► B5 AIMD + tiếp tục + cảnh báo (đề xuất)
```
