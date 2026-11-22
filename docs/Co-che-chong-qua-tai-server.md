# Cơ chế chống quá tải ở server

Tài liệu này trả lời câu hỏi: **signaling server chạy trên một VPS nhỏ, mở cổng ra Internet cho bất kỳ ai kết nối. Làm sao một người, hay một vài máy, không làm nó cạn bộ nhớ, cạn kết nối, cạn CPU, khiến những người dùng khác không vào được?**

Server ở dự án này không chở tin nhắn. Nó chỉ xếp phòng, chuyển tín hiệu kết nối, cấp tài khoản TURN tạm và xử lý đăng nhập. Nhưng chừng đó việc cũng đủ để bị lợi dụng.

Cách trình bày giống các tài liệu cơ chế khác: đi từ cách ngây thơ, tìm lỗ hổng, phân tích cần gì, rồi sinh ra bước tiếp theo. Ở mỗi chỗ phải chọn có so sánh các phương án.

Nhân vật:
- **Bưu điện** là signaling server, chỉ có một quầy và một kho nhỏ.
- **An**, **Bình** là khách bình thường.
- **Minh** là kẻ phá: không cần đọc trộm gì, chỉ muốn bưu điện tê liệt.

---

## Bước 0 — Bưu điện phục vụ tất cả, không giới hạn gì

**Làm gì:** bưu điện mở cửa cho bất kỳ ai. Ai vào cũng được ở lại bao lâu tuỳ thích, gửi thư to bao nhiêu cũng nhận, xin bao nhiêu thẻ đi nhờ xe cũng cấp.

**Về kỹ thuật:** Spring Boot nhận kết nối WebSocket ở `/ws`, cùng các API REST `/auth/*`, `/rooms` và `/turn-credentials`. Mỗi kết nối và mỗi phòng được giữ trong bộ nhớ (`RoomRegistry`).

**→ Lỗ hổng:** mọi tài nguyên của server đều có hạn, và Minh có thể lần lượt vét sạch từng thứ.

**Phân tích vấn đề:** liệt kê những gì Minh có thể vét:

| Tài nguyên | Cách Minh vét |
|---|---|
| Bộ nhớ | Gửi bản tin thật to; đặt tên phòng dài hàng chục KB; tạo thật nhiều phòng |
| Kết nối | Mở hàng nghìn kết nối WebSocket rồi để im, không làm gì |
| Luồng xử lý | Kết nối rồi không đọc dữ liệu, để server bị kẹt khi gửi cho Minh |
| CPU | Gọi `/auth/login` liên tục; mỗi lần là một phép BCrypt tốn khoảng 100 ms |
| Băng thông TURN | Xin tài khoản TURN rồi dùng server relay để chuyển dữ liệu của mình |
| Mật khẩu phòng | Dùng server để thử mật khẩu hàng loạt (xem [Co-che-phong-mat-khau.md](Co-che-phong-mat-khau.md)) |

**Để giải quyết thì cần gì:**
- **R1:** mỗi loại tài nguyên phải có **giới hạn trên** rõ ràng.
- **R2:** giới hạn phải tính **theo từng nguồn**, như mỗi địa chỉ hay mỗi kết nối. Một người phá không được làm hết suất của mọi người.
- **R3:** chính bộ đếm dùng để giới hạn **cũng không được phình vô hạn**.

**Xét các phương án:**
- **Đặt server sau một dịch vụ chống tấn công bên ngoài**, như Cloudflare hay tường lửa của nhà cung cấp: chặn tốt các đợt tấn công lớn ở tầng mạng. Nhưng nó không hiểu "phòng", "JOIN" hay "thử sai mật khẩu" là gì, và phụ thuộc vào bên thứ ba.
- **Bắt buộc đăng nhập cho mọi thao tác:** giảm số người lạ. Nhưng chính API đăng nhập vẫn phải mở, và một tài khoản thật vẫn có thể phá.
- **Đặt giới hạn ngay trong ứng dụng, cho từng loại tài nguyên:** hiểu đúng ngữ cảnh, không phụ thuộc bên ngoài, kiểm thử được bằng unit test. Đây là hướng được chọn. Nó không loại trừ việc đặt thêm lớp bên ngoài khi triển khai.

---

## Bước 1 — Giới hạn kích thước thư

**Làm gì:**
- Bưu điện chỉ nhận thư tối đa 64 KB.
- Phiếu đăng ký vào phòng thì giới hạn từng ô: tên phòng tối đa 200 ký tự, mã người tối đa 100, tên hiển thị tối đa 64.
- Thư viết sai định dạng thì bỏ riêng lá thư đó, không đuổi người gửi. Có thể đó chỉ là lỗi của ứng dụng chứ không phải tấn công.

**Về kỹ thuật:**
- Bộ đệm nhận bản tin của container WebSocket là 64 KB. Mặc định của Tomcat chỉ là 8 KB, mà mức đó đã từng làm đứt kết nối của người dùng có nhiều card mạng ảo, vì bản tin ICE offer của họ lớn hơn 8 KB.
- Giới hạn độ dài các trường JOIN.
- JSON lỗi chỉ ghi log rồi bỏ qua.

**Trong code:**
- Bộ đệm 64 KB và lý do chọn: [WebSocketConfig.java:21-35](../signaling-server/src/main/java/com/datn/chatp2p/signaling/config/WebSocketConfig.java#L21-L35), [50-56](../signaling-server/src/main/java/com/datn/chatp2p/signaling/config/WebSocketConfig.java#L50-L56).
- Độ dài các trường JOIN: [SignalingWebSocketHandler.java:72-75](../signaling-server/src/main/java/com/datn/chatp2p/signaling/ws/SignalingWebSocketHandler.java#L72-L75), [221-229](../signaling-server/src/main/java/com/datn/chatp2p/signaling/ws/SignalingWebSocketHandler.java#L221-L229).
- JSON lỗi thì bỏ qua bản tin, giữ kết nối: [SignalingWebSocketHandler.java:168-185](../signaling-server/src/main/java/com/datn/chatp2p/signaling/ws/SignalingWebSocketHandler.java#L168-L185).

**Đáp ứng yêu cầu:**
- **R1** cho bộ nhớ trên từng bản tin.

**→ Lỗ hổng:** thư nhỏ nhưng **người đến chiếm chỗ** thì sao?

**Phân tích vấn đề:**
- Minh mở 10.000 kết nối WebSocket rồi để im, không JOIN phòng nào. Trước đây những kết nối này được giữ mãi, và một máy có thể chiếm hết suất kết nối của server.
- Minh cũng có thể tạo hàng nghìn phòng, mỗi phòng một người, hoặc nhét thật đông người vào một phòng.

**Để giải quyết thì cần gì:**
- **R4:** giới hạn **số kết nối đồng thời từ mỗi địa chỉ**.
- **R5:** kết nối mà **không làm gì** thì phải bị đóng sau một thời gian.
- **R6:** giới hạn **tổng số phòng** và **số người mỗi phòng**.

---

## Bước 2 — Giới hạn số kết nối, hạn chót JOIN, số phòng

**Làm gì:**
- Mỗi địa chỉ nhà chỉ được giữ tối đa 32 chỗ ngồi trong bưu điện cùng lúc.
- Vào mà 30 giây không đăng ký phòng nào thì bị mời ra.
- Bưu điện có tối đa 5.000 phòng, mỗi phòng tối đa 8 người. Hết phòng thì báo "bưu điện đang bận".

**Về kỹ thuật:**
- Đếm số kết nối theo địa chỉ IP. Địa chỉ IPv6 được gộp theo dải /64, vì một máy thường được cấp nguyên một dải /64, khoá theo từng địa chỉ /128 thì giới hạn mất tác dụng.
- Kết nối thứ 33 bị đóng ngay với mã `POLICY_VIOLATION`.
- Mỗi kết nối có một hẹn giờ 30 giây. Chưa JOIN thì đóng.
- `RoomRegistry` từ chối với lý do `SERVER_BUSY` khi đủ 5.000 phòng, `ROOM_FULL` khi phòng đủ 8 người.
- Khi đặt sau reverse proxy (Caddy), server đọc địa chỉ thật của người dùng từ `X-Forwarded-For`. Tomcat chỉ tin header này khi yêu cầu đến từ địa chỉ nội bộ.

**Trong code:**
- 32 kết nối mỗi địa chỉ, hạn chót JOIN 30 giây: [SignalingWebSocketHandler.java:76-83](../signaling-server/src/main/java/com/datn/chatp2p/signaling/ws/SignalingWebSocketHandler.java#L76-L83), [122-161](../signaling-server/src/main/java/com/datn/chatp2p/signaling/ws/SignalingWebSocketHandler.java#L122-L161).
- Giảm bộ đếm khi kết nối đóng: [SignalingWebSocketHandler.java:293-303](../signaling-server/src/main/java/com/datn/chatp2p/signaling/ws/SignalingWebSocketHandler.java#L293-L303).
- Gộp IPv6 theo /64: [RequestRateLimiter.java:95-121](../signaling-server/src/main/java/com/datn/chatp2p/signaling/limit/RequestRateLimiter.java#L95-L121).
- 5.000 phòng, 8 người mỗi phòng: [RoomRegistry.java:25](../signaling-server/src/main/java/com/datn/chatp2p/signaling/room/RoomRegistry.java#L25), [30](../signaling-server/src/main/java/com/datn/chatp2p/signaling/room/RoomRegistry.java#L30), [84-97](../signaling-server/src/main/java/com/datn/chatp2p/signaling/room/RoomRegistry.java#L84-L97).
- Đọc IP thật sau proxy: [application.yml:5-7](../signaling-server/src/main/resources/application.yml#L5-L7).

**Đáp ứng yêu cầu:**
- **R4:** 32 kết nối mỗi địa chỉ.
- **R5:** 30 giây phải JOIN.
- **R6:** 5.000 phòng, 8 người mỗi phòng.

**→ Lỗ hổng:** một người **nhận chậm** có thể làm kẹt việc gửi cho tất cả mọi người.

**Phân tích vấn đề:**
- Khi An vào phòng, server phải báo cho mọi người trong phòng, và chuyển tín hiệu ICE qua lại.
- Ghi vào một kết nối WebSocket là thao tác **chặn**: nếu phía bên kia không đọc, luồng đang ghi phải đứng chờ.
- Minh vào phòng rồi cố tình không đọc gì. Luồng nào đang gửi cho Minh cũng bị kẹt, trong khi đó lại là luồng đang phục vụ An và Bình.
- Thêm nữa, Tomcat không cho hai luồng cùng ghi vào một kết nối. Lỗi này đã được phát hiện qua bài kiểm thử tải.

**Để giải quyết thì cần gì:**
- **R7:** gửi cho một người **không được chặn** luồng đang phục vụ người khác.
- **R8:** người nhận quá chậm thì phải bị **cắt**, để hàng đợi gửi cho họ không phình mãi.

---

## Bước 3 — Hàng đợi gửi có giới hạn cho từng người

**Làm gì:**
- Mỗi khách có một khay thư chờ riêng. Nhân viên chỉ bỏ thư vào khay rồi quay sang phục vụ người khác ngay. Một nhân viên khác chuyển thư từ khay đến tay khách.
- Khay đầy quá 512 KB, hoặc thư nằm trong khay quá 10 giây, thì khách đó bị mời ra.

**Về kỹ thuật:**
- Mỗi session được bọc trong `ConcurrentWebSocketSessionDecorator(session, 10 s, 512 KB)`.
- Các luồng khác chỉ xếp bản tin vào hàng đợi rồi trả về ngay. Việc ghi thật diễn ra lần lượt, không bao giờ có hai luồng cùng ghi vào một kết nối.
- Vượt giới hạn thì Spring ném `SessionLimitExceededException` và đóng session chậm. Những người khác không bị ảnh hưởng.

**Trong code:**
- Giới hạn 10 giây và 512 KB: [SignalingWebSocketHandler.java:84-86](../signaling-server/src/main/java/com/datn/chatp2p/signaling/ws/SignalingWebSocketHandler.java#L84-L86).
- Gửi qua lớp bọc, bắt lỗi theo từng session: [SignalingWebSocketHandler.java:453-476](../signaling-server/src/main/java/com/datn/chatp2p/signaling/ws/SignalingWebSocketHandler.java#L453-L476).

**Đáp ứng yêu cầu:**
- **R7:** gửi không chặn.
- **R8:** người nhận chậm bị cắt.

**→ Lỗ hổng:** các **API REST** mỗi lần gọi tốn kém, và chưa có gì giới hạn số lần gọi.

**Phân tích vấn đề:**
- **`/auth/login` và `/auth/register`:** mỗi yêu cầu là một phép BCrypt, được thiết kế cố ý chậm khoảng 100 ms CPU. Minh gọi 100 lần mỗi giây là chiếm trọn một nhân CPU. Đồng thời Minh còn dò được mật khẩu tài khoản người khác.
- **`/turn-credentials`:** mỗi lần gọi cấp một tài khoản TURN dùng được 6 giờ. Có tài khoản TURN là đi nhờ được băng thông của server relay.
- **JOIN phòng có mật khẩu:** server trả lời "sai mật khẩu" ngay, nên dò được mật khẩu hàng loạt.
- Nếu đếm số lần gọi theo địa chỉ bằng một bảng không bao giờ xoá, thì Minh chỉ cần xoay vòng thật nhiều địa chỉ là làm bảng đếm phình ra, và chính bộ đếm lại trở thành chỗ làm cạn bộ nhớ.

**Để giải quyết thì cần gì:**
- **R9:** giới hạn **số lần gọi mỗi phút theo địa chỉ** cho từng API tốn kém.
- **R10:** bộ đếm phải **tự dọn** những địa chỉ đã lâu không gọi, và có **trần tổng số địa chỉ** được theo dõi.
- **R11:** khi bộ đếm đã đầy mà vẫn còn địa chỉ đang hoạt động thì **từ chối địa chỉ mới**, không xoá bừa bộ đếm của người khác để nhường chỗ.

---

## Bước 4 — Giới hạn tần suất gọi theo địa chỉ

**Làm gì:**
- Mỗi địa chỉ chỉ được:
  - đăng nhập hoặc đăng ký 10 lần mỗi phút;
  - xin tài khoản TURN 20 lần mỗi phút;
  - nhập sai mật khẩu phòng 5 lần mỗi phút.
- Sổ đếm chỉ ghi các lần gọi trong một phút gần nhất. Địa chỉ nào cả phút không gọi thì bị gạch khỏi sổ.
- Sổ chứa tối đa 10.000 địa chỉ. Sổ đầy mà vẫn toàn người đang hoạt động thì người mới phải chờ.

**Về kỹ thuật:**
- `RequestRateLimiter` dùng cửa sổ trượt: mỗi khoá giữ một hàng đợi các mốc thời gian, mốc nào cũ hơn 60 giây thì bị xoá.
- `maxKeys = 10.000`. Khi đầy thì dọn các khoá rỗng. Vẫn đầy thì từ chối, tức là chọn an toàn khi lỗi (fail closed).
- Vượt giới hạn thì trả HTTP 429.
- Phòng có mật khẩu dùng một bộ đếm riêng là `WrongPasswordLimiter`.
- Bảng `sessions` trong MySQL được dọn các phiên đã hết hạn hoặc đã đăng xuất ở mỗi lần đăng nhập. Nhờ vậy cơ sở dữ liệu không phình theo số lần đăng nhập.

**Trong code:**
- Bộ giới hạn dùng chung: [RequestRateLimiter.java:22-89](../signaling-server/src/main/java/com/datn/chatp2p/signaling/limit/RequestRateLimiter.java#L22-L89).
- Đăng nhập và đăng ký 10 lần mỗi phút: [AuthController.java:15-20](../signaling-server/src/main/java/com/datn/chatp2p/signaling/auth/AuthController.java#L15-L20), [40-45](../signaling-server/src/main/java/com/datn/chatp2p/signaling/auth/AuthController.java#L40-L45).
- TURN 20 lần mỗi phút: [TurnController.java:20-28](../signaling-server/src/main/java/com/datn/chatp2p/signaling/turn/TurnController.java#L20-L28), [34-47](../signaling-server/src/main/java/com/datn/chatp2p/signaling/turn/TurnController.java#L34-L47).
- Sai mật khẩu phòng 5 lần mỗi phút: [WrongPasswordLimiter.java:18-19](../signaling-server/src/main/java/com/datn/chatp2p/signaling/room/WrongPasswordLimiter.java#L18-L19).
- Dọn bảng `sessions`: [AuthService.java:90-93](../signaling-server/src/main/java/com/datn/chatp2p/signaling/auth/AuthService.java#L90-L93).

**Đáp ứng yêu cầu:**
- **R9:** mỗi API tốn kém có hạn mức riêng.
- **R10:** tự dọn khoá rỗng, trần 10.000 khoá.
- **R11:** đầy thì từ chối khoá mới.

**→ Lỗ hổng còn lại:** các giới hạn hiện có đều ở **cửa vào**. Bên trong thì còn ba chỗ hở.

**Phân tích vấn đề:**
- **Chưa giới hạn tốc độ bản tin trên WebSocket đã JOIN:**
  - Sau khi vào phòng, Minh gửi được không giới hạn số bản tin `OFFER` và `ICE_CANDIDATE` tới một người, mỗi bản tin tới 64 KB.
  - Server chuyển tiếp hết ([SignalingWebSocketHandler.java:326-354](../signaling-server/src/main/java/com/datn/chatp2p/signaling/ws/SignalingWebSocketHandler.java#L326-L354)).
  - Hàng đợi 512 KB của **người nhận** đầy rất nhanh, và theo Bước 3 thì **người nhận bị cắt kết nối**, chứ không phải Minh. Cơ chế bảo vệ ở Bước 3 bị lật ngược thành công cụ để đá người khác ra khỏi phòng.
- **Giới hạn số phòng là chung cho cả server:**
  - Mỗi kết nối tạo được một phòng, mỗi địa chỉ có 32 kết nối, nên khoảng 157 địa chỉ là lấp đầy 5.000 phòng.
  - Sau đó mọi người khác đều nhận `SERVER_BUSY`.
- **Tài khoản TURN cấp cho cả người chưa đăng nhập:**
  - `/turn-credentials` không kiểm tra token ([TurnController.java:34-43](../signaling-server/src/main/java/com/datn/chatp2p/signaling/turn/TurnController.java#L34-L43)).
  - Với 20 lần mỗi phút, mỗi lần một tài khoản dùng được 6 giờ, một người lạ không có tài khoản vẫn đi nhờ được băng thông relay.
  - Giới hạn băng thông cho từng tài khoản TURN phải đặt trong cấu hình coturn trên VPS, mà cấu hình mẫu trong [README.md](../README.md) hiện chưa có.
- **Bộ đếm nằm trong bộ nhớ của một tiến trình:** khởi động lại server là xoá hết. Chạy nhiều bản server thì mỗi bản đếm riêng. Với quy mô một VPS thì chấp nhận được.

**Để giải quyết thì cần gì:**
- **R12:** giới hạn **tốc độ bản tin** của từng kết nối. Vượt thì cắt **người gửi**.
- **R13:** giới hạn **số phòng mỗi địa chỉ hoặc mỗi tài khoản**, để không ai một mình chiếm phần lớn bảng phòng.
- **R14:** chỉ cấp TURN cho **người đã đăng nhập**, và có **hạn mức băng thông** trên coturn.

---

## Bước 5 — *(đề xuất, chưa làm)* Giới hạn tốc độ trên WebSocket, số phòng mỗi địa chỉ, TURN cần đăng nhập

**Làm gì:**
- Mỗi khách trong bưu điện được phát một xấp phiếu, mỗi giây được thêm vài phiếu, tối đa một xấp nhất định. Gửi một thư tốn một phiếu. Hết phiếu mà vẫn cố gửi thì chính khách đó bị mời ra.
- Mỗi địa chỉ nhà chỉ được giữ vài phòng cùng lúc.
- Thẻ đi nhờ xe chỉ cấp cho người đã có tài khoản. Xe trung chuyển có giới hạn tốc độ cho từng thẻ.

**Về kỹ thuật:**
- Thuật toán xô token (token bucket) cho từng session, ví dụ 20 bản tin mỗi giây, xô tối đa 50.
- Đếm số phòng đang có người theo `addressKey` hoặc theo tài khoản, chặn khi vượt.
- `TurnController.credentials` đọc header `Authorization` như `/rooms` đang làm ([RoomController.java:22-30](../signaling-server/src/main/java/com/datn/chatp2p/signaling/room/RoomController.java#L22-L30)).
- Thêm vào `turnserver.conf` các dòng `user-quota`, `total-quota`, `max-bps`.

**Trong code (dự kiến):**
- `SignalingWebSocketHandler.handleTextMessage` kiểm tra xô token trước khi `switch`.
- `RoomRegistry.tryJoin` nhận thêm khoá địa chỉ để đếm số phòng.
- `TurnController` thêm tham số `@RequestHeader("Authorization")`; client đã có token sẵn khi gọi.
- Cập nhật mục cấu hình coturn trong README.

**Đáp ứng yêu cầu:**
- **R12:** người spam bị cắt, người nhận không bị vạ lây.
- **R13:** không ai chiếm hết bảng phòng.
- **R14:** TURN chỉ cho người đã đăng nhập, có hạn mức băng thông.

---

## Bảng tổng hợp các giới hạn hiện có

| Tài nguyên | Giới hạn | Vị trí |
|---|---|---|
| Kích thước một bản tin WebSocket | 64 KB | `WebSocketConfig` |
| Độ dài tên phòng / peerId / tên hiển thị | 200 / 100 / 64 ký tự | `SignalingWebSocketHandler` |
| Kết nối đồng thời mỗi địa chỉ | 32 (IPv6 gộp /64) | `SignalingWebSocketHandler` |
| Thời gian được kết nối mà chưa JOIN | 30 giây | `SignalingWebSocketHandler` |
| Hàng đợi gửi cho mỗi người | 512 KB hoặc 10 giây | `SignalingWebSocketHandler` |
| Tổng số phòng / người mỗi phòng | 5.000 / 8 | `RoomRegistry` |
| Đăng nhập, đăng ký | 10 lần mỗi phút mỗi địa chỉ | `AuthController` |
| Cấp TURN | 20 lần mỗi phút mỗi địa chỉ | `TurnController` |
| Sai mật khẩu phòng | 5 lần mỗi phút mỗi địa chỉ | `WrongPasswordLimiter` |
| Số địa chỉ được theo dõi | 10.000, đầy thì từ chối | `RequestRateLimiter` |
| Phiên đăng nhập | 12 giờ, dọn phiên cũ khi đăng nhập | `AuthService` |

---

## Tóm tắt chuỗi thiết kế

| Bước | Làm gì | Vá được gì | Sinh ra vấn đề gì |
|---|---|---|---|
| 0 | Không giới hạn gì | — | Mọi tài nguyên đều vét được |
| 1 | Bản tin 64 KB, giới hạn độ dài trường, JSON lỗi chỉ bỏ qua | Không làm cạn bộ nhớ bằng bản tin to | Chiếm kết nối và phòng |
| 2 | 32 kết nối mỗi IP, hạn JOIN 30 giây, 5.000 phòng, 8 người | Không giữ kết nối mãi được | Người nhận chậm làm kẹt luồng |
| 3 | Hàng đợi gửi 512 KB hoặc 10 giây cho mỗi người | Không ai làm kẹt người khác | API tốn kém bị gọi liên tục |
| 4 | Giới hạn tần suất theo IP: đăng nhập 10, TURN 20, sai mật khẩu 5 mỗi phút; trần 10.000 khoá | Chống dò mật khẩu, chống vét CPU | Spam trên WebSocket đá người nhận; phòng toàn cục; TURN không cần đăng nhập |
| 5 *(đề xuất)* | Xô token mỗi session, giới hạn phòng mỗi IP, TURN cần đăng nhập và có hạn mức | Cắt đúng người phá | — |

```
B0 không giới hạn
 ──bản tin khổng lồ──────────────► B1 64 KB + giới hạn trường
 ──chiếm kết nối, phòng──────────► B2 32 kết nối/IP, hạn JOIN, trần phòng
 ──người nhận chậm làm kẹt───────► B3 hàng đợi gửi có giới hạn
 ──API tốn kém bị gọi liên tục───► B4 giới hạn tần suất theo IP
 ──spam đá người nhận, TURN mở───► B5 xô token + phòng/IP + TURN cần đăng nhập (đề xuất)
```
