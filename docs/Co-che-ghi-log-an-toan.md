# Cơ chế ghi log mà không lộ bí mật

Tài liệu này trả lời câu hỏi: **ứng dụng cần log để tìm lỗi, nhưng làm sao log không trở thành chỗ rò rỉ chính những thứ mà mã hoá đầu cuối, BCrypt và DPAPI đang bảo vệ?**

Cách trình bày giống các tài liệu cơ chế khác: đi từ cách ngây thơ, tìm lỗ hổng, phân tích cần gì, rồi sinh ra bước tiếp theo. Ở mỗi chỗ phải chọn có so sánh các phương án.

Nhân vật:
- **An** là người dùng.
- **Khôi** là người phát triển, cần đọc log để sửa lỗi.
- **Minh** là người đọc được log mà không nên đọc: người mượn máy, người được gửi file log để "xem giúp lỗi", hay người có quyền vào VPS.

---

## Bước 0 — Ghi hết ra log cho dễ gỡ lỗi

**Làm gì:** Khôi cho ứng dụng ghi lại mọi thứ vào sổ nhật ký: nội dung từng tin nhắn, mật khẩu An gõ khi đăng nhập, vé đăng nhập server cấp, chìa khoá của từng buổi nói chuyện.

**Về kỹ thuật:** `LOG.info("Gui tin: " + text)`, `log.info("Dang nhap {} mat khau {}", user, password)`, in hex khoá AES phiên.

**Đáp ứng yêu cầu:** gỡ lỗi rất dễ. Thấy ngay tin nào đi đâu, khoá có khớp không.

**→ Lỗ hổng:** sổ nhật ký trở thành **bản sao rõ của mọi bí mật**.

**Phân tích vấn đề:**
- **Log nằm ở nhiều nơi và sống lâu:**
  - trên máy client là file trong thư mục người dùng;
  - trên VPS là `journalctl` của service `chat-p2p`;
  - và trong mọi bản sao lưu của hai chỗ đó.
- **Log hay được chia sẻ:** người dùng gặp lỗi thường gửi nguyên file log cho người khác xem.
- **Log phá vỡ từng lớp bảo vệ:**
  - Có nội dung tin trong log: mã hoá đầu cuối vô nghĩa.
  - Có mật khẩu trong log trên server: BCrypt vô nghĩa.
  - Có token: Minh dùng được tài khoản của An trong 12 giờ.
  - Có khoá AES phiên: Minh giải mã được mọi gói đã bắt được.
- Không cần tấn công gì phức tạp, chỉ cần **đọc một file văn bản**.

**Để giải quyết thì cần gì:**
- **R1:** **mặc định** log không chứa nội dung tin, mật khẩu, token hay khoá.
- **R2:** việc che phải **khó quên**. Không thể trông vào việc người viết code tự nhớ ở từng dòng log.
- **R3:** log vẫn phải **đủ thông tin để gỡ lỗi**: biết tin nào, dài bao nhiêu, gửi cho ai, có bị gửi lại không.

**Xét các phương án:**
- **Không ghi log gì cả:** an toàn tuyệt đối. Nhưng khi demo trước hội đồng mà kết nối không lên, không có gì để biết vì sao. Không đạt R3.
- **Ghi log tuỳ ý, dặn nhau "đừng ghi bí mật":** chỉ cần một người quên là lộ. Không đạt R2.
- **Mọi giá trị nhạy cảm đều phải đi qua một hàm trung gian; hàm này quyết định in thật hay in `***`:** chỉ phải làm đúng ở một chỗ, và nhìn dòng log là biết giá trị nào nhạy cảm. Đây là hướng được chọn.

---

## Bước 1 — Một lớp trung gian che bí mật

**Làm gì:**
- Khi ghi vào sổ, mọi thứ bí mật đều phải đưa qua một người kiểm duyệt.
  - Mật khẩu, vé, chìa khoá: người kiểm duyệt ghi `***`.
  - Nội dung tin nhắn: chỉ ghi "dài 42 ký tự".
- Những thứ không bí mật vẫn ghi đầy đủ: mã tin, ai gửi cho ai, gửi lại lần mấy, đã ký nhận chưa.
- Riêng con dấu danh tính dùng cả đời thì không bao giờ được ghi, kể cả trong chế độ gỡ lỗi.

**Về kỹ thuật:** lớp `SecretLogging` trong module `common`, dùng chung cho cả client lẫn server.
- `reveal(String)`: trả về `***`, hoặc `(rong)` nếu giá trị rỗng.
- `revealHex(byte[])`: dùng cho khoá.
- `text(String)`: dùng cho nội dung tin, trả về `"<n> ky tu"`.

Private key danh tính ECDSA không có hàm nào để in ra.

**Trong code:**
- Lớp che bí mật: [SecretLogging.java:15-56](../common/src/main/java/com/datn/chatp2p/common/log/SecretLogging.java#L15-L56). Ghi chú "không bao giờ log private key danh tính" ở [dòng 12-13](../common/src/main/java/com/datn/chatp2p/common/log/SecretLogging.java#L12-L13).
- Server: mật khẩu và token khi đăng ký, đăng nhập, đăng xuất đều đi qua `reveal` ([AuthService.java:38](../signaling-server/src/main/java/com/datn/chatp2p/signaling/auth/AuthService.java#L38), [65](../signaling-server/src/main/java/com/datn/chatp2p/signaling/auth/AuthService.java#L65), [97](../signaling-server/src/main/java/com/datn/chatp2p/signaling/auth/AuthService.java#L97), [107](../signaling-server/src/main/java/com/datn/chatp2p/signaling/auth/AuthService.java#L107)). Token trong `JOIN` bị từ chối cũng vậy ([SignalingWebSocketHandler.java:214](../signaling-server/src/main/java/com/datn/chatp2p/signaling/ws/SignalingWebSocketHandler.java#L214)).
- Client: nội dung tin khi gửi và nhận đi qua `SecretLogging.text`. Mật khẩu phòng và token đi qua `reveal` ([P2pChatSession.java:121-128](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L121-L128)). `RoomConfig.toString()` chỉ in "có mật khẩu hay không" ([RoomConfig.java:27-32](../client-javafx/src/main/java/com/datn/chatp2p/client/session/RoomConfig.java#L27-L32)).
- Quy tắc ghi ngay đầu lớp cấu hình log: "KHÔNG log nội dung tin nhắn, mật khẩu, token, khoá - chỉ id, độ dài, tên, số lượng" ([ConsoleLogging.java:27](../client-javafx/src/main/java/com/datn/chatp2p/client/util/ConsoleLogging.java#L27)).

**Đáp ứng yêu cầu:**
- **R1:** mặc định in `***` hoặc độ dài.
- **R2:** một chỗ duy nhất quyết định; dòng log nào gọi `reveal` hay `text` thì nhìn là biết.
- **R3:** vẫn có mã tin, độ dài, người nhận, số lần gửi lại.

**→ Lỗ hổng:** có những lỗi **chỉ nhìn thấy khi xem chính giá trị bí mật**.

**Phân tích vấn đề:**
- Ví dụ: hai máy không giải mã được tin của nhau. Muốn biết vì sao, Khôi cần so khoá gửi của An với khoá nhận của Bình.
- Ví dụ khác: tin hiện sai ký tự tiếng Việt. Khôi cần xem đúng chuỗi đã gửi đi.
- Nếu không có cách nào xem, Khôi sẽ tạm sửa code để in thẳng ra, rồi quên xoá. Đó chính là kiểu sai lầm ở Bước 0.

**Để giải quyết thì cần gì:**
- **R4:** có một **chế độ gỡ lỗi** in được giá trị thật, chỉ bật khi **cố ý**.
- **R5:** khi chế độ đó đang bật, phải có **cảnh báo rõ** ngay đầu log, để không ai vô tình để nó bật trên máy thật hay chia sẻ log đó đi.

---

## Bước 2 — Chế độ gỡ lỗi phải bật có chủ ý và có cảnh báo

**Làm gì:**
- Muốn sổ ghi cả bí mật, Khôi phải tự tay bật một công tắc riêng khi khởi động ứng dụng. Công tắc mặc định tắt.
- Khi công tắc bật, dòng đầu tiên của sổ là một cảnh báo in đậm: "Đang ghi cả nội dung tin, mật khẩu, vé, chìa khoá. Chỉ dùng khi thử, không chia sẻ sổ này."
- Chỉ trong chế độ này, sổ mới ghi chìa khoá của buổi nói chuyện.

**Về kỹ thuật:**
- Bật bằng `-Dchat.logSecrets=true` hoặc biến môi trường `CHAT_P2P_LOG_SECRETS=true`. Giá trị được đọc một lần lúc khởi động và không đổi được lúc đang chạy.
- `WARNING_BANNER` được in ở client khi khởi động và ở server khi tạo `AuthService`.
- Các dòng in khoá AES gửi/nhận và public key ECDH chỉ chạy khi `SecretLogging.enabled()`.
- Mức log chi tiết (`FINE`) là một công tắc **khác**: `-Dchat.debug=true`. Bật để xem gói bị gửi lại hay bị bỏ qua mà **không** làm lộ bí mật.

**Trong code:**
- Đọc công tắc và nội dung cảnh báo: [SecretLogging.java:17-23](../common/src/main/java/com/datn/chatp2p/common/log/SecretLogging.java#L17-L23).
- In cảnh báo ở client: [ChatApplication.java:33-34](../client-javafx/src/main/java/com/datn/chatp2p/client/ChatApplication.java#L33-L34). Ở server: [AuthService.java:32-33](../signaling-server/src/main/java/com/datn/chatp2p/signaling/auth/AuthService.java#L32-L33).
- Chỉ in khoá phiên khi bật: [PeerConnection.java:277](../p2p-core/src/main/java/com/datn/chatp2p/p2p/PeerConnection.java#L277). Chỉ in nội dung lịch sử khi bật: [P2pChatSession.java:762](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L762).
- Công tắc mức chi tiết tách riêng: [ChatApplication.java:26](../client-javafx/src/main/java/com/datn/chatp2p/client/ChatApplication.java#L26), [ConsoleLogging.java:37-52](../client-javafx/src/main/java/com/datn/chatp2p/client/util/ConsoleLogging.java#L37-L52).

**Đáp ứng yêu cầu:**
- **R4:** gỡ lỗi được, nhưng phải bật có chủ ý.
- **R5:** có cảnh báo ở đầu log cả hai phía.

**→ Lỗ hổng:** dù không có bí mật, **log vẫn là một kho dữ liệu cần quản lý**.

**Phân tích vấn đề:**
- **Log trên client:** khi chạy bản cài đặt, không có cửa sổ terminal. Log phải được ghi ra file thì mới tìm lỗi được. Nhưng ghi file mãi thì ổ đĩa đầy dần.
- **Thư viện ice4j rất "lắm lời":** mỗi lần thử ICE, nó ghi hàng trăm dòng, kèm toàn bộ địa chỉ mạng của máy.
- **Log có thể bị làm ngập:** Minh gửi liên tục gói rác vào cổng UDP của An. Nếu mỗi gói bị bỏ đều sinh một dòng log, terminal và file của An đầy rác, và các dòng quan trọng bị trôi mất.

**Để giải quyết thì cần gì:**
- **R6:** log ra file có **giới hạn dung lượng** và tự xoay vòng.
- **R7:** thư viện bên ngoài mặc định chỉ ghi **cảnh báo trở lên**.
- **R8:** những dòng log mà kẻ khác **gây ra được từ bên ngoài** phải bị giới hạn số lượng.

---

## Bước 3 — Giới hạn nơi ghi, mức ghi và số dòng

**Làm gì:**
- Sổ trên máy An nằm trong thư mục riêng của An. Mỗi tập tối đa 5 MB, giữ tối đa 5 tập, tập cũ nhất bị xoá khi đầy.
- Các thư viện bên ngoài chỉ được ghi khi có chuyện đáng lo, trừ khi Khôi bật chế độ chi tiết.
- Với mỗi đường riêng, dòng "đã bỏ qua một gói lạ" chỉ được ghi tối đa 20 lần. Từ đó trở đi thì im lặng.

**Về kỹ thuật:**
- `FileHandler` ghi vào `~/.chat-p2p-java/logs/client-%g.log`, mỗi file 5 MB, giữ 5 file, mã hoá UTF-8.
- `org.ice4j` và `org.jitsi` để ở mức `WARNING`, chỉ lên `INFO` khi `chat.debug=true`.
- `P2pDataChannel.logDropped` ghi ở mức `FINE` và dừng sau 20 dòng cho mỗi kênh.

**Trong code:**
- File log xoay vòng: [ConsoleLogging.java:54-76](../client-javafx/src/main/java/com/datn/chatp2p/client/util/ConsoleLogging.java#L54-L76).
- Mức log của ice4j: [ConsoleLogging.java:49-51](../client-javafx/src/main/java/com/datn/chatp2p/client/util/ConsoleLogging.java#L49-L51).
- Giới hạn 20 dòng cho gói bị bỏ: [P2pDataChannel.java:211-223](../p2p-core/src/main/java/com/datn/chatp2p/p2p/channel/P2pDataChannel.java#L211-L223).

**Đáp ứng yêu cầu:**
- **R6:** tối đa 25 MB, tự xoay vòng.
- **R7:** ice4j chỉ ghi cảnh báo.
- **R8:** tối đa 20 dòng cho mỗi kênh.

**→ Lỗ hổng còn lại:** `SecretLogging` che **bí mật**, nhưng chưa che **siêu dữ liệu nhạy cảm**. Chưa có gì ngăn **dữ liệu do người khác điền** làm giả dòng log.

**Phân tích vấn đề:**
- **Thẻ phòng có mật khẩu nằm trong log:**
  - Mã phòng gửi lên server có dạng `tên~thẻ`, mà thẻ được tính từ mật khẩu phòng (xem [Co-che-phong-mat-khau.md](Co-che-phong-mat-khau.md), Bước 1).
  - Server ghi nguyên mã phòng vào log ở nhiều chỗ, ví dụ lúc vào phòng, rời phòng hay bị từ chối ([SignalingWebSocketHandler.java:233](../signaling-server/src/main/java/com/datn/chatp2p/signaling/ws/SignalingWebSocketHandler.java#L233), [259](../signaling-server/src/main/java/com/datn/chatp2p/signaling/ws/SignalingWebSocketHandler.java#L259), [285](../signaling-server/src/main/java/com/datn/chatp2p/signaling/ws/SignalingWebSocketHandler.java#L285), [323](../signaling-server/src/main/java/com/datn/chatp2p/signaling/ws/SignalingWebSocketHandler.java#L323)). Client cũng in "roomId trên server=…" ([P2pChatSession.java:124](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L124)).
  - Ai đọc được log sẽ có đủ tên phòng và thẻ để ngồi đoán mật khẩu ngoại tuyến, không bị giới hạn 5 lần mỗi phút.
- **Địa chỉ mạng:**
  - Log client có danh sách địa chỉ ICE của máy, kể cả địa chỉ trong mạng nội bộ ([IceP2pConnectionEstablisher.java:199](../p2p-core/src/main/java/com/datn/chatp2p/p2p/ice/IceP2pConnectionEstablisher.java#L199), [361](../p2p-core/src/main/java/com/datn/chatp2p/p2p/ice/IceP2pConnectionEstablisher.java#L361)).
  - Log server có IP của người thử sai mật khẩu.
  - Những thứ này không phải bí mật mật mã, nhưng là dữ liệu cá nhân.
- **Log giả:**
  - Tên hiển thị của khách do khách tự đặt. Server chỉ giới hạn độ dài 64 ký tự, không lọc ký tự xuống dòng, rồi ghi tên đó vào log, ví dụ `Peer {} ({}) da vao phong {}`.
  - Khách đặt tên chứa ký tự xuống dòng kèm một dòng giả, chẳng hạn "… INFO Dang nhap thanh cong 'admin'". Log trên VPS sẽ có một dòng trông như thật.
- **Thời gian lưu log trên server:** `journalctl` giữ log theo cấu hình mặc định của hệ điều hành. Chưa có quy định lưu bao lâu.

**Để giải quyết thì cần gì:**
- **R9:** thẻ phòng chỉ được ghi ở dạng **không dùng để đoán mật khẩu được**.
- **R10:** chuỗi do người dùng điền phải được **làm sạch ký tự điều khiển** trước khi ghi log.
- **R11:** địa chỉ mạng chỉ ghi đầy đủ ở chế độ chi tiết. Log trên server có **thời hạn lưu** rõ ràng.

---

## Bước 4 — *(đề xuất, chưa làm)* Che thẻ phòng, làm sạch chuỗi người dùng, giới hạn thời gian lưu

**Làm gì:**
- Trong sổ, phòng có mật khẩu chỉ hiện là "phòng X, có mật khẩu", kèm vài ký tự đầu của một mã băm thứ hai để vẫn phân biệt được các phòng trùng tên.
- Tên người dùng ghi vào sổ được bỏ các ký tự xuống dòng và ký tự điều khiển.
- Địa chỉ mạng chỉ ghi đầy đủ khi Khôi bật chế độ chi tiết.
- Sổ trên server chỉ giữ trong một thời hạn nhất định, ví dụ 14 ngày.

**Về kỹ thuật:**
- Thêm `SecretLogging.roomId(String)`: nếu có `~` thì trả về `tên~<8 hex đầu của SHA-256(thẻ)>`. Mã băm thứ hai này không dùng để kiểm tra mật khẩu được, vì kẻ đoán phải ra được chính thẻ.
- Thêm `SecretLogging.userText(String)`: thay `\p{Cntrl}` bằng `?` và cắt độ dài. Dùng cho `userName`, `roomId` và các giá trị lấy từ bản tin của người khác.
- `describeCandidates` chỉ in số lượng và loại địa chỉ (`host`, `srflx`, `relay`) ở mức `INFO`; in địa chỉ đầy đủ ở mức `FINE`.
- Trên VPS: đặt `MaxRetentionSec=14day` trong `journald.conf`, hoặc dùng `journalctl --vacuum-time=14d` định kỳ.
- Cũng có thể chặn ký tự điều khiển ngay ở server khi `JOIN` (`INVALID_JOIN`), để tên hiển thị trên giao diện của người khác cũng sạch.

**Đáp ứng yêu cầu:**
- **R9:** thẻ phòng không còn trong log.
- **R10:** không làm giả được dòng log.
- **R11:** địa chỉ mạng chỉ hiện ở chế độ chi tiết, log server có thời hạn.

---

## Tóm tắt chuỗi thiết kế

| Bước | Làm gì | Vá được gì | Sinh ra vấn đề gì |
|---|---|---|---|
| 0 | Ghi mọi thứ ra log | — | Log là bản sao rõ của mọi bí mật |
| 1 | `SecretLogging`: `***` cho mật khẩu, token, khoá; độ dài cho nội dung; không bao giờ log khoá danh tính | Mặc định không lộ bí mật, che ở một chỗ duy nhất | Có lỗi chỉ thấy khi xem giá trị thật |
| 2 | Chế độ `chat.logSecrets` bật có chủ ý, có cảnh báo; tách riêng khỏi `chat.debug` | Gỡ lỗi được mà không để lộ thường trực | Log vẫn cần giới hạn nơi ghi và số lượng |
| 3 | File 5 × 5 MB xoay vòng, ice4j chỉ `WARNING`, gói rác tối đa 20 dòng | Không đầy đĩa, không bị làm ngập | Thẻ phòng, IP vẫn trong log; tên người dùng làm giả được dòng log |
| 4 *(đề xuất)* | Che thẻ phòng, làm sạch ký tự điều khiển, ẩn IP, giới hạn thời gian lưu | Log không còn giúp đoán mật khẩu hay làm giả | — |

```
B0 ghi hết
 ──log là bản sao bí mật─────────► B1 SecretLogging che mặc định
 ──không gỡ lỗi được─────────────► B2 chế độ bí mật có chủ ý + cảnh báo
 ──log là kho cần quản lý────────► B3 xoay vòng, giảm ồn, giới hạn dòng
 ──siêu dữ liệu + log giả────────► B4 che thẻ, làm sạch, thời hạn lưu (đề xuất)
```
