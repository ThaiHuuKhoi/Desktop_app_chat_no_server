# Cơ chế hiển thị nội dung từ người khác một cách an toàn

Tài liệu này trả lời câu hỏi: **mọi thứ hiện trên màn hình chat đều do máy của người khác gửi tới. Làm sao hiển thị đẹp (in đậm, link, ảnh, cảm xúc, thông báo) mà người gửi có ý xấu không treo được máy mình, không lừa được mình bấm vào thứ nguy hiểm, và không làm lộ nội dung ra ngoài?**

Mã hoá đầu cuối chỉ bảo đảm tin **đến đúng từ người đó**. Nó không bảo đảm **người đó tử tế**. Một thành viên trong phòng, hoặc một client đã bị sửa, gửi gì tới máy mình cũng được.

Cách trình bày giống các tài liệu cơ chế khác: đi từ cách ngây thơ, tìm lỗ hổng, phân tích cần gì, rồi sinh ra bước tiếp theo. Ở mỗi chỗ phải chọn có so sánh các phương án.

Nhân vật:
- **An** là người xem tin.
- **Minh** là thành viên cùng phòng có ý xấu, gửi nội dung "độc".

---

## Bước 0 — Hiển thị tin bằng một trình duyệt nhúng

**Làm gì:** để tin nhắn có chữ đậm, link, ảnh, Khôi cho mỗi tin hiện trong một khung trình duyệt nhỏ. Minh viết gì thì khung hiện đúng như vậy.

**Về kỹ thuật:** đổi Markdown sang HTML rồi đưa vào `WebView`. Đây là cách nhiều ứng dụng chat dựng trên công nghệ web hay làm.

**Đáp ứng yêu cầu:** làm đẹp gì cũng được, gần như không tốn công.

**→ Lỗ hổng:** khung trình duyệt **chạy bất cứ thứ gì Minh gửi**.

**Phân tích vấn đề:**
- **Chạy mã:** Minh gửi một đoạn có `<script>`. Đoạn mã chạy ngay trong ứng dụng của An, đọc được nội dung khung chat, giả các nút bấm.
- **Lộ địa chỉ:** Minh gửi `<img src="http://may-cua-minh/anh.png">`. Máy An tự tải ảnh về, và Minh biết ngay địa chỉ IP của An, biết An vừa đọc tin, dù cả phòng đang đi qua TURN để giấu IP (xem [Co-che-ket-noi-p2p.md](Co-che-ket-noi-p2p.md)).
- **Link đánh lừa:** Minh viết `<a href="http://trang-gia.com">https://vietcombank.com.vn</a>`. Chữ hiện một đằng, bấm vào đi một nẻo.
- **Mở file trên máy:** link dạng `file:///C:/...`, hoặc các giao thức lạ, có thể mở chương trình trên máy An.

**Để giải quyết thì cần gì:**
- **R1:** nội dung người khác gửi chỉ là **chữ**, không bao giờ là mã chạy được.
- **R2:** hiển thị tin **không tự tải gì** từ mạng.
- **R3:** link phải **hiện đúng địa chỉ thật** của nó, và chỉ mở được link web `http`/`https`.

**Xét các phương án:**
- **Giữ trình duyệt nhúng nhưng lọc HTML:** phải viết hoặc mang thêm một bộ lọc HTML. Lọc HTML cho đúng là việc rất khó, bỏ sót một thẻ là hỏng hết. `WebView` còn nặng và tốn bộ nhớ khi có hàng trăm tin.
- **Chỉ hiện chữ thô:** an toàn tuyệt đối, nhưng không có in đậm, khối code hay link bấm được.
- **Tự phân tích một tập con nhỏ của Markdown rồi dựng thẳng thành các ô chữ của JavaFX:** không có HTML ở đâu cả, nên không có gì để chạy; chỉ hỗ trợ đúng những gì cần. Đây là hướng được chọn.

---

## Bước 1 — Tập con Markdown, dựng thành ô chữ, link luôn hiện địa chỉ thật

**Làm gì:**
- Ứng dụng chỉ hiểu vài ký hiệu: `**đậm**`, `*nghiêng*`, `~~gạch~~`, `` `code` ``, khối code, danh sách, trích dẫn.
- Mọi thứ khác, kể cả các thẻ HTML, hiện nguyên văn là chữ.
- Địa chỉ bắt đầu bằng `http://` hoặc `https://` thì tự thành link, và chữ hiện ra chính là địa chỉ đó.
- Cố ý **không** hỗ trợ kiểu `[chữ hiện](địa chỉ)`, để không ai giấu được địa chỉ thật sau một dòng chữ.
- Bấm link thì chỉ mở được nếu là `http`/`https`, bằng trình duyệt của máy.

**Về kỹ thuật:**
- `MarkdownParser` trả về danh sách khối (đoạn, mục danh sách, trích dẫn, khối code) và các đoạn chữ mang kiểu.
- `MarkdownView` dựng chúng thành `Text`, `Label`, `Hyperlink` của JavaFX. Không có `WebView`, không có HTML, không có thẻ ảnh.
- `LinkOpener.open` kiểm tra scheme bằng `URI.getScheme()` trước khi gọi `HostServices.showDocument`.

**Trong code:**
- Phạm vi hỗ trợ và lý do bỏ `[chữ](url)`: [MarkdownParser.java:11-22](../client-javafx/src/main/java/com/datn/chatp2p/client/view/MarkdownParser.java#L11-L22).
- Nhận diện URL `http(s)`, bỏ dấu câu dính ở cuối: [MarkdownParser.java:155-166](../client-javafx/src/main/java/com/datn/chatp2p/client/view/MarkdownParser.java#L155-L166).
- Dựng thành node JavaFX; link có tooltip hiện đúng địa chỉ: [MarkdownView.java:66-71](../client-javafx/src/main/java/com/datn/chatp2p/client/view/MarkdownView.java#L66-L71).
- Chỉ mở `http`/`https`: [LinkOpener.java:21-35](../client-javafx/src/main/java/com/datn/chatp2p/client/util/LinkOpener.java#L21-L35).

**Đáp ứng yêu cầu:**
- **R1:** không có HTML nên không có mã nào chạy được.
- **R2:** không có thẻ ảnh nên không tự tải gì.
- **R3:** link hiện đúng địa chỉ; chỉ mở `http`/`https`.

**→ Lỗ hổng:** bộ phân tích chạy trên **dữ liệu do Minh chọn**, và Minh có thể chọn đúng loại dữ liệu làm nó chạy rất chậm.

**Phân tích vấn đề:**
- Bộ phân tích gặp dấu `*` thì tìm dấu `*` đóng tương ứng ở phía sau.
- Minh gửi chuỗi `*a *a *a …` lặp lại hàng trăm nghìn lần, không có dấu đóng hợp lệ nào. Với mỗi dấu mở, bộ phân tích lại quét tới tận cuối chuỗi, tức là n lần quét, mỗi lần dài n: độ phức tạp **O(n²)**.
- Việc phân tích chạy trên luồng giao diện JavaFX. Cửa sổ của An **đơ hẳn**.
- Trước khi sửa, đường tin nhắn trực tiếp chỉ bị chặn ở 1 MB của kênh UDP, nên một tin khoảng 750 nghìn ký tự là đủ treo máy người nhận.
- Đây là kiểu tấn công từ chối dịch vụ bằng độ phức tạp thuật toán, không cần nhiều băng thông.

**Để giải quyết thì cần gì:**
- **R4:** mọi nội dung từ người khác phải có **giới hạn độ dài**, áp dụng ở **mọi đường vào**: tin trực tiếp, bản sửa, lịch sử chia sẻ.
- **R5:** bộ phân tích phải chạy **gần tuyến tính** kể cả với dữ liệu xấu nhất.

---

## Bước 2 — Giới hạn độ dài và phân tích tuyến tính

**Làm gì:**
- Tin nào dài quá 12.000 ký tự thì An vẫn ký nhận, để Minh khỏi gửi lại mãi, nhưng không hiển thị. Bản sửa tin và tin trong lịch sử cũng chịu cùng giới hạn.
- Bộ phân tích ghi nhớ: "từ vị trí này trở đi chắc chắn không còn dấu `*` đóng hợp lệ nào". Lần sau gặp dấu mở ở vị trí sau đó, nó không quét lại nữa.

**Về kỹ thuật:**
- `GroupHistory.MAX_TEXT_CHARS = 12_000`, được kiểm tra trong `handleMessage`, `handleMessageEdit`, `editMessage` và `GroupHistory.isAcceptable`.
- `findClosing` có bảng ghi nhớ theo từng loại dấu. Tập vị trí ứng viên chỉ thu hẹp dần, nên đã biết "không có dấu đóng từ f" thì mọi vị trí sau f cũng không có.
- Test: chuỗi `"*a "` lặp 200.000 lần, khoảng 600 nghìn ký tự, phải phân tích xong trong 5 giây và giữ nguyên văn bản.

**Trong code:**
- Giới hạn 12.000 ký tự: [GroupHistory.java:21-25](../client-javafx/src/main/java/com/datn/chatp2p/client/session/GroupHistory.java#L21-L25).
- Tin trực tiếp quá dài: vẫn ACK, không hiển thị: [P2pChatSession.java:477-485](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L477-L485).
- Bản sửa quá dài bị bỏ: xem `handleMessageEdit` trong [Co-che-chat-nhom-va-rieng.md](Co-che-chat-nhom-va-rieng.md), Bước 5.
- Tìm dấu đóng có ghi nhớ: [MarkdownParser.java:186-210](../client-javafx/src/main/java/com/datn/chatp2p/client/view/MarkdownParser.java#L186-L210).
- Test độ phức tạp: [MarkdownParserComplexityTest.java](../client-javafx/src/test/java/com/datn/chatp2p/client/view/MarkdownParserComplexityTest.java).

**Đáp ứng yêu cầu:**
- **R4:** 12.000 ký tự ở mọi đường vào.
- **R5:** dữ liệu xấu nhất cũng xong trong thời gian ngắn, có test giữ.

**→ Lỗ hổng:** tin nhắn không phải chỗ duy nhất Minh đưa được thứ gì đó lên màn hình, hay ra ngoài màn hình, của An.

**Phân tích vấn đề:** liệt kê các đường khác.
- **Cảm xúc:** nếu cho thả bất kỳ chuỗi nào, Minh chèn được một đoạn chữ dài ngay dưới tin của người khác.
- **Báo đã xem:** một gói mang hàng nghìn mã tin, mỗi mã làm giao diện cập nhật một lần.
- **Thông báo trên màn hình:** nếu thông báo hiện nội dung tin, thì nội dung đó nằm trên màn hình khoá và trong trung tâm thông báo của hệ điều hành, ngoài tầm của mã hoá đầu cuối.
- **Tên file:** tên do Minh đặt, có thể là `../../` hay `CON`.
- **Ảnh xem trước:** ảnh do Minh gửi được giải mã và hiện ngay trong khung chat.

**Để giải quyết thì cần gì:**
- **R6:** mỗi đường đưa dữ liệu lên giao diện đều phải có **giới hạn**: tập giá trị cho phép, số phần tử, độ dài.
- **R7:** thông báo ra ngoài ứng dụng **không chứa nội dung tin**.
- **R8:** ảnh chỉ được hiện khi **file đã nhận đủ và đã kiểm tra**; việc giải mã ảnh không được làm đơ giao diện.

---

## Bước 3 — Giới hạn từng đường dữ liệu khác lên giao diện

**Làm gì:**
- **Cảm xúc:** chỉ có 5 biểu tượng cố định 👍 ❤️ 😂 😮 😢. Thư mang biểu tượng khác bị bỏ.
- **Báo đã xem:** mỗi gói chỉ được xét tối đa 20 mã tin.
- **Thông báo trên màn hình:** chỉ ghi "Minh đã gửi tin nhắn trong phòng X", hoặc "Minh đã nhắn riêng cho bạn". Không bao giờ có nội dung.
- **Tên file:** được làm sạch trước khi ghi xuống đĩa, chỉ ghi vào thư mục Downloads, không ghi đè.
- **Ảnh xem trước:** chỉ hiện khi file đã nhận xong và đã khớp dấu SHA-256. Ảnh được tải ở chế độ nền, thu nhỏ cỡ 300 × 220. Bộ nhớ đệm ảnh chỉ giữ 40 ảnh gần nhất.

**Về kỹ thuật:**
- `ReactionPayload.ALLOWED`: cả bên gửi lẫn bên nhận đều kiểm tra.
- `MAX_READ_IDS_PER_PACKET = 20`.
- `DesktopNotifier.notify` chỉ nhận tiêu đề và câu mô tả do `RoomController` tự tạo.
- `FileNames.sanitize`: bỏ đường dẫn, ký tự cấm, tên thiết bị Windows.
- `Image(uri, 300, 220, preserveRatio, smooth, backgroundLoading=true)`; ảnh lỗi thì không hiện.

**Trong code:**
- Bộ cảm xúc cố định: [ReactionPayload.java:12](../common/src/main/java/com/datn/chatp2p/common/protocol/ReactionPayload.java#L12). Kiểm tra khi nhận ở `handleReaction` (xem [Co-che-chat-nhom-va-rieng.md](Co-che-chat-nhom-va-rieng.md), Bước 4).
- Giới hạn 20 mã tin: [P2pChatSession.java:529](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L529).
- Thông báo chỉ có tên: [DesktopNotifier.java:18-19](../client-javafx/src/main/java/com/datn/chatp2p/client/util/DesktopNotifier.java#L18-L19), nơi gọi ở [RoomController.java:1045-1050](../client-javafx/src/main/java/com/datn/chatp2p/client/view/RoomController.java#L1045-L1050). Cài đặt cũng ghi rõ "Thông báo chỉ hiện tên người gửi, không hiện nội dung tin nhắn" ([SettingsDialog.java:160](../client-javafx/src/main/java/com/datn/chatp2p/client/view/SettingsDialog.java#L160)).
- Làm sạch tên file: xem [Co-che-truyen-file.md](Co-che-truyen-file.md), Bước 4.
- Ảnh xem trước: [TranscriptCell.java:148-180](../client-javafx/src/main/java/com/datn/chatp2p/client/view/TranscriptCell.java#L148-L180).

**Đáp ứng yêu cầu:**
- **R6:** mỗi đường đều có giới hạn riêng.
- **R7:** thông báo không có nội dung.
- **R8:** ảnh chỉ hiện sau khi đã kiểm tra SHA-256, tải nền, có thu nhỏ.

**→ Lỗ hổng còn lại:** vẫn còn những cách **đánh lừa mắt người** mà các giới hạn trên chưa chặn.

**Phân tích vấn đề:**
- **Ký tự đảo chiều chữ:**
  - Unicode có ký tự điều khiển hướng chữ, như `U+202E` (Right-to-Left Override). Đặt nó vào giữa chuỗi thì phần sau hiện ngược.
  - Minh đặt tên file `anh_bien\u202Egnp.exe`. Màn hình hiện `anh_bienexe.png`, trông như ảnh, nhưng thật ra là file chạy được.
  - Bộ làm sạch tên file chỉ bỏ ký tự điều khiển mã 0 đến 31 ([FileNames.java:24](../p2p-core/src/main/java/com/datn/chatp2p/p2p/filetransfer/FileNames.java#L24)), không bỏ các ký tự hướng chữ.
  - Tên hiển thị và nội dung tin cũng không được lọc, nên Minh có thể dùng cách này để giả tên người khác hoặc giả một đường link.
- **Link mở ngay không hỏi:**
  - Bấm vào là trình duyệt mở luôn.
  - Tên miền dùng chữ quốc tế trông giống hệt chữ Latinh, như `vietcombаnk.com` với chữ `а` của bảng chữ Kirin, vẫn hiện y như thật. Ứng dụng không cảnh báo, cũng không hiện dạng `xn--…`.
- **Ảnh "bom giải nén":**
  - Một file PNG chỉ vài trăm KB nhưng khai kích thước 30.000 × 30.000 điểm ảnh, giải nén ra có thể cần nhiều GB bộ nhớ.
  - Ảnh xem trước gọi bộ giải mã của JavaFX trên file đó mà không kiểm tra kích thước khai báo trước. Tuỳ bộ giải mã, việc này có thể làm cạn bộ nhớ ứng dụng.
  - Kiểm tra SHA-256 chỉ chứng minh file đúng là thứ Minh gửi, không chứng minh file vô hại.

**Để giải quyết thì cần gì:**
- **R9:** loại bỏ, hoặc hiện ra rõ ràng, các ký tự điều khiển hướng chữ trong tên file, tên hiển thị và nội dung tin.
- **R10:** trước khi mở link, cho người dùng thấy **tên miền thật**, kể cả dạng `xn--…` khi tên miền có chữ quốc tế, và hỏi lại.
- **R11:** đọc **kích thước khai báo** của ảnh trước khi giải mã; quá ngưỡng thì không xem trước.

---

## Bước 4 — *(đề xuất, chưa làm)* Lọc ký tự hướng chữ, hỏi trước khi mở link, kiểm tra kích thước ảnh

**Làm gì:**
- Tên file, tên người và nội dung tin được bỏ các ký tự đảo chiều chữ trước khi hiện ra. Tên file thì bỏ luôn trước khi lưu xuống đĩa.
- Bấm link thì hiện hộp "Mở trang **vietcombаnk.com** (xn--vietcombnk-…)?" kèm hai nút "Mở" và "Huỷ".
- Ảnh khai kích thước quá lớn thì chỉ hiện biểu tượng file, không hiện ảnh xem trước.

**Về kỹ thuật:**
- Thêm các ký tự `U+200E`, `U+200F`, `U+202A` đến `U+202E`, `U+2066` đến `U+2069` vào `FileNames.FORBIDDEN_CHARS`. Thêm một hàm `MessageTexts.stripBidi` dùng cho tên hiển thị và `MarkdownView`.
- `LinkOpener.open` hiện `Alert` xác nhận, dùng `java.net.IDN.toASCII(host)` để hiện dạng punycode khi tên miền khác ASCII.
- Đọc header ảnh bằng `ImageIO.getImageReaders(...).getWidth(0) / getHeight(0)`, không giải mã, trước khi tạo `Image`. Ví dụ quá 8.000 × 8.000 thì bỏ qua xem trước.

**Đáp ứng yêu cầu:**
- **R9:** không còn giả được tên hay đuôi file.
- **R10:** thấy tên miền thật trước khi mở.
- **R11:** không giải mã ảnh có kích thước bất thường.

---

## Bảng tổng hợp các đường dữ liệu từ người khác lên giao diện

| Đường vào | Giới hạn hiện có | Còn hở |
|---|---|---|
| Nội dung tin | Không có HTML; tập con Markdown; tối đa 12.000 ký tự; phân tích gần tuyến tính | Ký tự đảo chiều chữ |
| Link trong tin | Hiện đúng địa chỉ; không có `[chữ](url)`; chỉ `http`/`https` | Mở ngay, không cảnh báo tên miền giống |
| Tên hiển thị | Server giới hạn 64 ký tự; tài khoản thì server ép đúng tên; nhãn "Khách" cho khách | Ký tự đảo chiều chữ, ký tự điều khiển |
| Cảm xúc | Bộ 5 biểu tượng cố định | — |
| Báo đã xem | Tối đa 20 mã tin mỗi gói | — |
| Lịch sử chia sẻ | 200 tin mỗi người; nhãn "chưa xác minh" (xem [Co-che-lich-su-tin-nhan.md](Co-che-lich-su-tin-nhan.md)) | Mất nhãn khi lưu trên máy |
| Tên file | Làm sạch, chỉ ghi vào Downloads, không ghi đè | Ký tự đảo chiều chữ |
| Ảnh xem trước | Chỉ sau khi khớp SHA-256; tải nền; thu nhỏ; đệm 40 ảnh | Chưa kiểm tra kích thước khai báo |
| Thông báo hệ điều hành | Chỉ tên người gửi và tên phòng | — |

---

## Tóm tắt chuỗi thiết kế

| Bước | Làm gì | Vá được gì | Sinh ra vấn đề gì |
|---|---|---|---|
| 0 | Hiện tin bằng HTML trong `WebView` | — | Chạy mã, lộ IP qua ảnh, link đánh lừa |
| 1 | Tập con Markdown dựng thành node JavaFX; link hiện đúng địa chỉ; chỉ `http`/`https` | Không còn gì chạy được hay tự tải về | Phân tích O(n²) bị lợi dụng để treo máy |
| 2 | Giới hạn 12.000 ký tự ở mọi đường vào; `findClosing` có ghi nhớ | Không treo được giao diện | Còn các đường khác lên giao diện |
| 3 | Cảm xúc cố định, 20 mã đã xem, thông báo không có nội dung, ảnh chỉ sau SHA-256 | Mỗi đường đều có giới hạn | Ký tự đảo chiều chữ, link tên miền giống, ảnh bom giải nén |
| 4 *(đề xuất)* | Lọc ký tự hướng chữ, hỏi trước khi mở link, kiểm tra kích thước ảnh | Chặn các cách đánh lừa mắt người | — |

```
B0 HTML trong WebView
 ──chạy mã, lộ IP, link giả──────► B1 tập con Markdown, node JavaFX
 ──O(n²) treo giao diện──────────► B2 giới hạn độ dài + phân tích tuyến tính
 ──còn đường khác lên giao diện──► B3 giới hạn từng đường
 ──đánh lừa mắt người────────────► B4 lọc bidi, hỏi link, kiểm tra ảnh (đề xuất)
```
