# CƠ CHẾ ĐĂNG NHẬP — GIẢI THÍCH BẰNG VÍ DỤ ĐỜI THƯỜNG, KÈM ĐỐI CHIẾU CODE

**Ngày:** 22/11/2026 · **Phiên bản code:** nhánh `main`, commit `a0ee278` (số dòng bên dưới đúng với bản này)

Tài liệu này giải thích cơ chế đăng nhập của ứng dụng theo dạng **chuỗi thiết kế**: mỗi bước được phân tích để tìm lỗ hổng, và lỗ hổng đó sinh ra bước tiếp theo để vá. Tại mỗi vấn đề, các phương án khác nhau được đem ra so sánh trước khi chọn. Cơ chế mã hoá tin nhắn được giải thích riêng trong [Co-che-ma-hoa-tin-nhan.md](Co-che-ma-hoa-tin-nhan.md); chi tiết kỹ thuật đầy đủ nằm trong [Tai-lieu-ky-thuat.md](Tai-lieu-ky-thuat.md), mục I.1 (tài khoản) và J.3 (kiểm định bảo mật).

Đường dẫn rút gọn dùng trong tài liệu:
- `server/…` = `signaling-server/src/main/java/com/datn/chatp2p/signaling/`
- `client/…` = `client-javafx/src/main/java/com/datn/chatp2p/client/`

---

## Nhân vật

| Trong ví dụ | Trong hệ thống | Trong code |
|---|---|---|
| **An** | Người dùng muốn đăng nhập | `client/…/view/AuthController.java`, `client/…/session/AuthClient.java` |
| **Minh** | Kẻ muốn mạo danh An hoặc lấy tài khoản của An | — |
| **Câu lạc bộ** | Signaling server: giữ sổ hội viên, phát vé | `server/…/auth/AuthController.java`, `server/…/auth/AuthService.java` |
| **Sổ hội viên** | Database MySQL: bảng `users` và `sessions` | `signaling-server/src/main/resources/schema.sql` |
| **Phòng sinh hoạt** | Phòng chat | `server/…/ws/SignalingWebSocketHandler.java`, `server/…/room/RoomRegistry.java` |

---

## Chuỗi thiết kế

**Cách đọc.** Mỗi bước có cùng khuôn: **Làm gì** (ví dụ đời thường, không dùng thuật ngữ kỹ thuật) → **Về kỹ thuật** và **Trong code** → **Đáp ứng yêu cầu** của bước trước → **Lỗ hổng** → **Phân tích vấn đề** → **Để giải quyết thì cần gì** (R1, R2…) → sinh ra bước tiếp theo. Bước đánh dấu *(không dùng trong code)* là cách "dễ nghĩ ra", được nêu để thấy vì sao phải bỏ. Bước 7 là **đề xuất, chưa làm**.

---

### Bước 0 — Không có tài khoản, ai cũng tự xưng tên

**Làm gì:** An bước vào phòng sinh hoạt và tự giới thiệu "Tôi là An". Không ai hỏi giấy tờ.

**Về kỹ thuật:** đây là chế độ khách: client gửi `JOIN` kèm tên hiển thị tự đặt. Production hiện chạy `CHAT_P2P_AUTH_REQUIRED=false` nên khách vào phòng được.

**Trong code:** `server/…/ws/SignalingWebSocketHandler.java:200` trở đi (`handleJoin`): không có vé thì chỉ kiểm tra các trường bắt buộc rồi cho vào.

**Lỗ hổng:** Minh cũng bước vào và giới thiệu "Tôi là An" được.

**Phân tích vấn đề:**
- Cái tên chỉ là một câu tự khai. Trong phòng có hai người cùng tên "An", Bình không biết ai là An thật.
- Minh có thể nhận những tin Bình định gửi cho An, hoặc nói những điều An không hề nói.
- Mã hoá đầu-cuối không giúp gì ở đây: nó chỉ bảo vệ đường truyền, không chứng minh được *cái tên* là của ai.

**Để giải quyết thì cần gì:**
- **R1:** mỗi tên thuộc về **đúng một người**; ai đăng ký trước thì giữ tên đó.
- **R2:** mỗi lần dùng tên, người đó phải **chứng minh** mình là chủ tên.
- **R3:** cách chứng minh không bị người khác học lại hay lấy cắp được.

**Các phương án:**
- Chứng minh bằng **thứ mình biết**: mật khẩu. Đơn giản, dùng được trên mọi máy.
- Chứng minh bằng **thứ mình có**: khoá danh tính trên máy, điện thoại nhận mã. An toàn hơn, nhưng mất máy là mất tài khoản và cần thêm hạ tầng.
- Nhờ **bên thứ ba** xác nhận (đăng nhập bằng Google, Facebook): phụ thuộc dịch vụ ngoài, không hợp với một hệ thống chạy độc lập.

Dự án chọn **mật khẩu** làm cách chứng minh chính. **→ Sinh ra Bước 1.**

---

### Bước 1 — Câu lạc bộ ghi tên và mật khẩu vào sổ *(không dùng trong code)*

**Làm gì:** An đến câu lạc bộ đăng ký tên "an_nguyen" và một mật khẩu. Câu lạc bộ chép cả hai vào sổ hội viên. Lần sau An đọc mật khẩu, câu lạc bộ so với sổ.

**Về kỹ thuật:** lưu mật khẩu dạng rõ trong database.

**Đáp ứng yêu cầu của Bước 0:** R1 (tên là duy nhất trong sổ), R2 (phải đọc đúng mật khẩu).

**Lỗ hổng:** nếu cuốn sổ bị lộ, **toàn bộ mật khẩu bị lộ**.

**Phân tích vấn đề:**
- Sổ có thể bị lộ theo nhiều cách: bị trộm, bản sao để quên ở đâu đó, người giữ sổ tò mò.
- Thiệt hại không dừng ở câu lạc bộ này: người ta hay **dùng lại một mật khẩu** cho email, ngân hàng.
- Câu lạc bộ thực ra không cần *biết* mật khẩu. Nó chỉ cần *kiểm tra* được "mật khẩu An vừa đọc có đúng không".

**Để giải quyết thì cần gì:**
- **R1:** sổ chỉ lưu một thứ dùng để **kiểm tra** mật khẩu, nhưng **không lần ngược ra được** mật khẩu.
- **R2:** hai người trùng mật khẩu thì trong sổ vẫn phải trông khác nhau, để lộ một người không kéo theo người kia.
- **R3:** kẻ có cuốn sổ muốn dò mật khẩu bằng cách thử hàng loạt thì phải **rất chậm**.

**Các phương án:**
- **Khoá mật khẩu lại rồi lưu:** câu lạc bộ phải giữ chìa để mở, mà chìa thường bị lộ cùng với sổ.
- **Biến mật khẩu thành một dãy ký tự bằng một phép tính nhanh, không đảo ngược được:** không lần ngược được, nhưng tính quá nhanh. Kẻ gian thử được hàng tỷ mật khẩu mỗi giây và có sẵn bảng tra cho các mật khẩu phổ biến. Hai người trùng mật khẩu ra trùng kết quả.
- **Dùng một phép tính chuyên cho mật khẩu, cố ý chậm và trộn thêm một gia vị riêng cho từng người:** đáp ứng cả ba yêu cầu.

**→ Sinh ra Bước 2.**

---

### Bước 2 — Lưu mật khẩu bằng BCrypt

**Làm gì:** câu lạc bộ không chép mật khẩu vào sổ. Nó chỉ chép một "dấu vân" của mật khẩu, tạo bằng một cỗ máy cố ý chạy chậm; mỗi hội viên có thêm một gia vị riêng trộn vào trước khi cho qua máy. Khi An đọc mật khẩu, câu lạc bộ cho mật khẩu đó chạy qua đúng cỗ máy với đúng gia vị của An rồi so dấu vân.

**Về kỹ thuật:** hàm băm mật khẩu BCrypt, cường độ 10, salt ngẫu nhiên riêng cho từng người (salt nằm luôn trong chuỗi được lưu).

**Trong code:**
- `server/…/auth/AuthService.java:27`: `new BCryptPasswordEncoder()`.
- Dòng 47: `passwordEncoder.encode(password)` khi đăng ký; dòng 73: `passwordEncoder.matches(...)` khi đăng nhập.
- Dòng 142 (`validateCredentials`): tên phải gồm 3–64 ký tự chữ, số hoặc `_`; mật khẩu dài 8–200 ký tự.
- Cột `password_hash` của bảng `users` trong `schema.sql`.

**Đáp ứng yêu cầu của Bước 1:**
- **R1:** chỉ lưu dấu vân, không lần ngược ra mật khẩu được.
- **R2:** mỗi người một gia vị (salt), nên trùng mật khẩu vẫn ra dấu vân khác nhau.
- **R3:** mỗi lần thử tốn cỡ hàng chục mili giây, nên dò hàng tỷ mật khẩu là không khả thi.

**Lỗ hổng:** nếu mọi việc (vào phòng, xem danh sách phòng) đều phải đọc lại mật khẩu thì sao?

**Phân tích vấn đề:**
- Mật khẩu sẽ đi qua đường truyền **rất nhiều lần**; mỗi lần là một cơ hội bị nghe lén.
- Ứng dụng phải **giữ mật khẩu** suốt buổi, hoặc tệ hơn là ghi xuống máy.
- Mỗi lần kiểm tra lại tốn một lần chạy cỗ máy chậm, làm câu lạc bộ quá tải.

**Để giải quyết thì cần gì:**
- **R1:** chỉ đọc mật khẩu **một lần** khi đăng nhập, sau đó dùng một thứ khác thay thế tạm.
- **R2:** thứ thay thế đó **không đoán được** và **không tự làm giả được**.
- **R3:** nó **tự hết hạn**, và chủ tài khoản **huỷ được** khi cần (đăng xuất).
- **R4:** nếu sổ bị lộ, những thứ thay thế đang còn hạn cũng không dùng được.

**→ Sinh ra Bước 3.**

---

### Bước 3 — Vé phiên

**Làm gì:** An đọc đúng mật khẩu thì câu lạc bộ phát cho An một **tấm vé** in một dãy số rất dài, ngẫu nhiên, có hạn 12 giờ. Trong sổ, câu lạc bộ không chép dãy số trên vé mà chỉ chép dấu vân của nó. Từ đó, mỗi lần cần, An chỉ đưa vé, không đọc lại mật khẩu. Câu lạc bộ có quầy nhận trả vé: vé trả rồi thì bị gạch đi. Ứng dụng của An chỉ nhớ **tên tài khoản** để lần sau điền sẵn, không nhớ mật khẩu hay vé.

**Về kỹ thuật:** session token = `v1_` + 32 byte ngẫu nhiên (SecureRandom), mã hoá base64url. Database chỉ lưu SHA-256 của token, cùng thời điểm hết hạn (`expires_at`) và thời điểm thu hồi (`revoked_at`). Token gửi kèm header `Authorization: Bearer …` khi gọi REST, và kèm bản tin `JOIN` khi vào phòng.

**Trong code:**
- `server/…/auth/AuthService.java:127` (`newToken`), `:133` (`hash`, SHA-256), `:95` (ghi vào bảng `sessions`), hạn 12 giờ ở dòng 23 (`SESSION_LIFETIME`).
- Dòng 110 (`authenticate`): chỉ chấp nhận token chưa thu hồi và chưa hết hạn.
- Dòng 104 (`logout`): đặt `revoked_at`.
- Phía client: token chỉ nằm trong bộ nhớ; ứng dụng chỉ nhớ tên tài khoản gần nhất (`client/…/util/UserSettings.java:92`, `lastAccount`).

**Đáp ứng yêu cầu của Bước 2:**
- **R1:** mật khẩu chỉ gửi đúng một lần lúc đăng nhập.
- **R2:** 256 bit ngẫu nhiên nên không đoán được; vé không chứa thông tin gì để tự làm giả.
- **R3:** ✅ (nút đăng xuất thêm ngày 22/11). Vé hết hạn sau 12 giờ, và An trả vé được bằng nút "Đăng xuất" ở thẻ tên trên màn hình sảnh: ứng dụng bỏ vé trên máy, quay về màn hình đăng nhập, và báo câu lạc bộ thu hồi vé (`client/…/view/HomeController.java`, `onLogout` → `AuthClient.logout` → `POST /auth/logout`). Mất mạng lúc đăng xuất thì vé vẫn tự hết hạn sau 12 giờ. Riêng việc đóng ứng dụng thì không trả vé.
- **R4:** sổ chỉ có dấu vân của vé, nên lộ sổ cũng không dùng được vé.

*Vì sao với vé thì dùng phép tính nhanh (SHA-256) là đủ, còn mật khẩu phải dùng BCrypt chậm?* Vì vé là một dãy 256 bit hoàn toàn ngẫu nhiên: không có "vé phổ biến" nào để dò như mật khẩu, nên thử hết các khả năng là bất khả thi dù phép tính có nhanh.

**Lỗ hổng:** mật khẩu (lúc đăng nhập) và vé (mỗi lần dùng) đều đi qua đường truyền.

**Phân tích vấn đề:**
- Minh ngồi cùng quán cà phê, nghe lén được đường truyền, sẽ chép được vé. Trong 12 giờ đó, Minh dùng vé như chính An: vào phòng dưới tên An, xem danh sách phòng, thậm chí đăng xuất An.
- Lấy được mật khẩu thì còn tệ hơn: Minh đăng nhập lại bất cứ lúc nào.
- Ngoài nghe lén, Minh còn có thể dựng một **câu lạc bộ giả** ở giữa để An đưa mật khẩu cho Minh.

**Để giải quyết thì cần gì:**
- **R1:** đường từ ứng dụng tới câu lạc bộ phải **được mã hoá**.
- **R2:** ứng dụng phải **chắc chắn đang nói chuyện với đúng câu lạc bộ**, không phải câu lạc bộ giả.

**→ Sinh ra Bước 4.**

---

### Bước 4 — Mã hoá đường tới server (TLS)

**Làm gì:** mọi lần An nói chuyện với câu lạc bộ đều đi trong một chiếc xe chở tiền bọc thép. Câu lạc bộ có giấy chứng nhận do một cơ quan uy tín cấp, nên ứng dụng của An nhận ra đúng câu lạc bộ thật.

**Về kỹ thuật:** TLS: signaling qua `wss://chat.zingme.fun`, REST qua `https://chat.zingme.fun`. Caddy trên VPS giữ chứng chỉ Let's Encrypt và chuyển tiếp vào signaling server, vốn chỉ nghe ở `127.0.0.1:8080`.

**Trong code:** `client/…/session/AuthClient.java:150` (`httpBase`): đổi `wss://` thành `https://` cho `/auth/*`, `/rooms`, `/turn-credentials`. Địa chỉ mặc định `wss://chat.zingme.fun` nằm trong `AuthController` và `HomeController`.

**Đáp ứng yêu cầu của Bước 3:** R1 (TLS mã hoá đường truyền), R2 (Java kiểm tra chứng chỉ của server).

**Lỗ hổng:** đường đi đã an toàn, nhưng **vé vẫn có thể bị đưa nhầm chỗ**.

**Phân tích vấn đề:** đợt kiểm định ngày 22/11 tìm ra hai chỗ:
- **Đưa nhầm câu lạc bộ:** An đăng nhập ở câu lạc bộ A, nhưng màn hình sảnh tự đặt lại về câu lạc bộ mặc định rồi đưa vé của A cho câu lạc bộ đó. Người dùng câu lạc bộ tự dựng vì vậy bị lộ vé cho chủ câu lạc bộ mặc định.
- **Câu lạc bộ chuyển nguyên vé cho người khác:** khi chuyển "thư hỏi đường" của An tới Bình, câu lạc bộ để nguyên tấm vé của An trong thư. Bình (nếu có ý xấu) lấy được vé của An.

Ngoài ra, ứng dụng **vẫn cho tự gõ địa chỉ `ws://`** (không mã hoá). Khi đó mật khẩu và vé lại đi dạng rõ.

**Để giải quyết thì cần gì:**
- **R1:** vé chỉ được đưa cho **đúng câu lạc bộ đã phát** nó.
- **R2:** câu lạc bộ dùng vé để kiểm tra rồi **giữ lại**, không chuyển cho ai.
- **R3:** chỉ cho phép đường không mã hoá khi chạy thử trên chính máy mình.

**→ Sinh ra Bước 5.**

---

### Bước 5 — Vé chỉ dùng đúng chỗ

**Làm gì:** vé ghi rõ "chỉ dùng tại câu lạc bộ A", và ứng dụng không cho đổi sang câu lạc bộ khác khi đang cầm vé; muốn đổi thì bấm "Đăng xuất" rồi đăng nhập lại ở câu lạc bộ mới. Câu lạc bộ xem vé xong thì cất đi, không bao giờ chuyển cho ai. Khi An vào phòng sinh hoạt bằng vé, câu lạc bộ tự ghi tên An theo đúng tên trong sổ, An không tự khai được tên khác.

**Về kỹ thuật:** token gắn với đúng server đã cấp (ô server bị khoá ở chế độ tài khoản); server xoá `authToken` trước khi chuyển tiếp bản tin; khi `JOIN` kèm token hợp lệ, tên hiển thị được thay bằng tên tài khoản.

**Trong code:**
- `client/…/view/AuthController.java:174, 179`: nhớ server đăng nhập (`loginServer`) và truyền sang màn hình sảnh.
- `client/…/view/HomeController.java:166` (`setAuthenticatedUser`), dòng 171 (`serverField.setEditable(false)`).
- `server/…/ws/SignalingWebSocketHandler.java:345`: `signal.setAuthToken(null)` trước khi chuyển tiếp.
- `server/…/ws/SignalingWebSocketHandler.java:237`: tên hiển thị = tên tài khoản. Mỗi lần chuyển tiếp, vé được kiểm tra lại (dòng 371).

**Đáp ứng yêu cầu của Bước 4:** R1 ✅ và R2 ✅ (sửa ngày 22/11). R3 ❌ chưa làm.

**Lỗ hổng:** vẫn còn hai chỗ hở.

**Phân tích vấn đề thứ nhất — dò mật khẩu trực tiếp:**
- Trước ngày 22/11, Minh gửi yêu cầu đăng nhập liên tục, bao nhiêu lần cũng được, để thử mật khẩu của "an_nguyen". Cỗ máy chậm ở Bước 2 làm mỗi lần thử chậm, nhưng không giới hạn số lần thử.
- Mỗi lần đăng nhập thành công lại thêm một dòng vào bảng phiên, và không có gì dọn đi, nên sổ phình mãi.

**Phân tích vấn đề thứ hai — khách và hội viên lẫn vào nhau:**
- Câu lạc bộ đang **cho khách vào phòng không cần đăng nhập**. Khách tự khai tên, và câu lạc bộ chỉ ghi đè tên cho người *có vé*.
- Một khách hoàn toàn có thể tự xưng **"an_nguyen"**, trùng tên một hội viên.
- Danh sách người trong phòng mà câu lạc bộ gửi cho mọi người chỉ có tên, **không có dấu nào cho biết ai là hội viên, ai là khách** (`common/…/signal/SignalMessage.java`, lớp `PeerInfo` chỉ có `peerId` và `userName`).
- Vậy với người dùng cho phép khách, vấn đề ở Bước 0 vẫn còn: Bình không phân biệt được "an_nguyen" thật với một khách mượn tên.

**Để giải quyết thì cần gì:**

Với vấn đề thứ nhất:
- **R1:** giới hạn số lần thử đăng nhập của mỗi người gửi trong một khoảng thời gian.
- **R2:** dọn những vé đã hết hạn hoặc đã trả.

Với vấn đề thứ hai:
- **R3:** trong phòng phải **nhìn thấy được** ai là hội viên đã đăng nhập và ai là khách, hoặc không cho khách dùng tên trùng tên hội viên.

**→ Sinh ra Bước 6.**

---

### Bước 6 — Giới hạn số lần thử, dọn vé cũ

**Làm gì:** mỗi người gửi chỉ được thử đăng nhập 10 lần mỗi phút; quá thì phải chờ. Mỗi khi An đăng nhập, câu lạc bộ gạch luôn những tấm vé cũ đã hết hạn hoặc đã trả của An.

**Về kỹ thuật:** giới hạn tốc độ 10 yêu cầu/phút theo địa chỉ IP (IPv6 gộp theo dải /64), vượt thì trả mã HTTP 429. Khi đăng nhập, xoá các phiên đã thu hồi hoặc hết hạn của tài khoản đó.

**Trong code (sửa ngày 22/11):**
- `server/…/auth/AuthController.java:15` (`MAX_AUTH_REQUESTS_PER_MINUTE = 10`), dòng 40 (`requireWithinLimit`), dùng `server/…/limit/RequestRateLimiter.java`.
- `server/…/auth/AuthService.java:92`: `DELETE FROM sessions … WHERE revoked_at IS NOT NULL OR expires_at <= CURRENT_TIMESTAMP`.

**Đáp ứng yêu cầu của Bước 5:** R1 ✅, R2 ✅. R3 ✅ (thêm ngày 22/11):
- Khi báo người mới vào phòng (`PEER_LIST`, `PEER_JOINED`), câu lạc bộ ghi kèm người đó là hội viên đã đăng nhập hay khách, cùng khoá danh tính đã đăng ký của hội viên (`common/…/signal/SignalMessage.java`, trường `authenticated` và `identityKey`).
- Danh sách người trong phòng hiện nhãn "Tài khoản" hoặc "Khách". Khách đặt trùng tên một tài khoản đang có trong phòng thì hiện "Khách trùng tên tài khoản" màu đỏ, kèm cảnh báo.
- Máy người nhận còn tự đối chiếu khoá danh tính của hội viên với khoá đã đăng ký. Chi tiết ở [Co-che-xac-thuc-danh-tinh.md](Co-che-xac-thuc-danh-tinh.md), Bước 7.

**Lỗ hổng:** nếu **mật khẩu của An bị lộ** (An dùng lại mật khẩu ở trang khác, hoặc bị lừa nhập vào trang giả), Minh đăng nhập được từ **máy của Minh**.

**Phân tích vấn đề:**
- Dự án có làm một cơ chế: lần đăng nhập đầu tiên, tài khoản được **gắn với khoá danh tính của máy**; về sau, đăng nhập từ máy có khoá khác thì bị từ chối (`server/…/auth/AuthService.java:78–90`).
- Nhưng đợt kiểm định cho thấy cơ chế này **không phải lớp bảo vệ thật**:
  - Thứ được đem ra so là **khoá công khai**, do chính ứng dụng tự khai lên. Khoá công khai của An thì ai trong phòng cũng thấy, vì nó được gửi cho mọi người khi xác thực danh tính. Minh chỉ cần khai đúng khoá công khai đó.
  - Bỏ trống trường khoá thì câu lạc bộ **bỏ qua luôn** việc so khoá.
  - Câu lạc bộ chưa bao giờ yêu cầu An **chứng minh đang giữ khoá riêng** tương ứng với khoá công khai.
- Vậy hiện tại, **chỉ cần biết mật khẩu là đủ** để đăng nhập vào tài khoản của An.

**Để giải quyết thì cần gì:**
- **R1:** ngoài mật khẩu, cần thêm một **thứ chỉ máy của An có**.
- **R2:** chứng minh có thứ đó mà **không gửi nó đi**.
- **R3:** lời chứng minh **không dùng lại được** cho lần sau (Minh chép được cũng vô ích).
- **R4:** vẫn có cách lấy lại tài khoản khi An **đổi máy**.

**Các phương án:**
- **Mã gửi qua email hoặc tin nhắn điện thoại:** cần thêm dịch vụ gửi thư, gửi tin.
- **Ứng dụng tạo mã 6 số đổi liên tục:** người dùng phải cài thêm ứng dụng và quét mã cài đặt.
- **Ký bằng khoá danh tính đã có sẵn trên máy:** không cần thêm gì, tận dụng đúng thứ dự án đã có; việc đổi máy đã có sẵn chức năng xuất / nhập bản sao lưu danh tính.

**→ Sinh ra Bước 7.**

---

### Bước 7 — *(đề xuất, chưa làm)* Chứng minh đang giữ khoá danh tính khi đăng nhập

**Làm gì:** khi An đăng nhập, câu lạc bộ đưa ra một **câu đố mới tinh**, chỉ dùng một lần. An ký tên lên câu đố bằng chữ ký riêng chỉ máy mình có. Câu lạc bộ so chữ ký với mẫu chữ ký đã lưu từ lần đầu. Minh biết mật khẩu nhưng không có máy của An, nên không ký được; chép lại chữ ký của lần trước cũng vô ích vì lần này câu đố đã khác.

**Về kỹ thuật:** xác thực hai yếu tố bằng thử thách – phản hồi (challenge–response): server sinh một chuỗi ngẫu nhiên dùng một lần, client ký bằng khoá danh tính ECDSA, server kiểm tra chữ ký bằng `public_key` đã lưu.

**Trong code (dự kiến):** thêm `/auth/challenge` trả về chuỗi ngẫu nhiên có hạn ngắn; `AuthService.login` nhận thêm chữ ký và kiểm tra bằng `IdentitySignatureService.verify` với `public_key` đã lưu; bỏ việc "trống khoá thì bỏ qua". Đổi máy thì dùng chức năng nhập bản sao lưu danh tính đã có (`crypto/…/IdentityBackup.java`).

**Đáp ứng yêu cầu của Bước 6:**
- **R1:** khoá riêng chỉ nằm trên máy An (được mã hoá bằng DPAPI trên Windows).
- **R2:** chỉ gửi chữ ký, không gửi khoá riêng.
- **R3:** câu đố mới mỗi lần, nên chữ ký cũ không dùng lại được.
- **R4:** đổi máy bằng bản sao lưu danh tính (có mật khẩu bảo vệ).

---

### Tóm tắt chuỗi

```
B0 tự xưng tên ──ai cũng giả tên được──► B1 sổ ghi mật khẩu ──lộ sổ là lộ mật khẩu──► B2 BCrypt
──phải đọc mật khẩu mỗi lần──► B3 vé phiên ──mật khẩu / vé bị nghe lén──► B4 TLS
──vé bị đưa nhầm chỗ──► B5 vé chỉ dùng đúng chỗ ──dò mật khẩu / khách trùng tên hội viên──► B6 giới hạn thử, dọn vé
──lộ mật khẩu là mất tài khoản──► B7 ký câu đố bằng khoá danh tính (đề xuất)
```

| Lỗ hổng | Được vá ở | Trạng thái |
|---|---|---|
| Ai cũng tự xưng tên | Bước 1–3, với người dùng có tài khoản | ✅ |
| Lộ sổ là lộ mật khẩu | Bước 2 | ✅ |
| Phải gửi mật khẩu mỗi lần | Bước 3 | ✅ |
| Không trả vé được: thiếu nút đăng xuất ở chế độ tài khoản, vé sống đủ 12 giờ | Bước 3 (R3) | ✅ sửa ngày 22/11: nút "Đăng xuất" gọi `AuthClient.logout` rồi về màn hình đăng nhập |
| Nghe lén mật khẩu và vé | Bước 4 | ✅ (trừ khi người dùng tự gõ `ws://`) |
| Vé đưa nhầm server / bị server chuyển cho người khác | Bước 5 | ✅ sửa ngày 22/11 |
| Dò mật khẩu trực tiếp, bảng phiên phình mãi | Bước 6 | ✅ sửa ngày 22/11 |
| Khách đặt tên trùng tên hội viên, trong phòng không phân biệt được | Bước 6 (R3) | ✅ sửa ngày 22/11: nhãn "Tài khoản" / "Khách", cảnh báo khách trùng tên, đối chiếu khoá đã đăng ký |
| Lộ mật khẩu là mất tài khoản | Bước 7 | ❌ đề xuất |

---

## Hành trình của một lần đăng nhập — từ đầu đến cuối

| # | Ví dụ đời thường | Code thực hiện |
|---|---|---|
| 1 | An mở ứng dụng; ô tên đã điền sẵn tên lần trước | `client/…/view/AuthController.java` đọc `UserSettings.lastAccount()` |
| 2 | An nhập mật khẩu, bấm Đăng nhập | `AuthController.login` → `AuthClient.login(server, tên, mật khẩu, khoá công khai)` |
| 3 | Lời đề nghị đi trong xe bọc thép tới câu lạc bộ | `AuthClient.httpBase` đổi sang `https://` → `POST /auth/login` qua Caddy (TLS) |
| 4 | Câu lạc bộ đếm số lần thử của người gửi | `server/…/auth/AuthController.java:40` (`requireWithinLimit`) |
| 5 | Câu lạc bộ tra sổ, cho mật khẩu qua cỗ máy chậm, so dấu vân | `AuthService.login`: tra `users`, `passwordEncoder.matches` (dòng 73) |
| 6 | Gạch vé cũ, phát vé mới, chép dấu vân của vé vào sổ | `AuthService.java:92` (xoá phiên cũ), `newToken` (127), `INSERT INTO sessions` (95) |
| 7 | An nhận vé (chỉ giữ trong túi, không ghi ra giấy) | `AuthResponse(token, username, expiresAt)`; client giữ token trong bộ nhớ |
| 8 | Ứng dụng nhớ vé dùng cho đúng câu lạc bộ này | `Navigator.showHome(..., loginServer)` → `HomeController.setAuthenticatedUser` khoá ô server |
| 9 | An xem danh sách phòng bằng vé | `AuthClient.rooms` gửi `Authorization: Bearer <vé>` → `RoomController` gọi `AuthService.authenticate` |
| 10 | An vào phòng bằng vé; câu lạc bộ tự ghi tên theo sổ | `JOIN` kèm token → `SignalingWebSocketHandler.java:200` kiểm tra, dòng 237 đặt tên = tên tài khoản |
| 11 | Mỗi lần nhờ chuyển thư, câu lạc bộ xem lại vé rồi cất đi | `isAuthorized` (dòng 356–371) kiểm tra lại token; dòng 345 xoá token trước khi chuyển |
| 12 | Ra về, An bấm "Đăng xuất" để trả vé | `HomeController.onLogout` → `AuthClient.logout` → `POST /auth/logout` → `AuthService.logout` đặt `revoked_at`; ứng dụng về màn hình đăng nhập |
