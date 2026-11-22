# BÁO CÁO KIỂM ĐỊNH BẢO MẬT — 13 VẤN ĐỀ VÀ CÁCH KHẮC PHỤC — CHAT P2P JAVA

**Ngày:** 22/11/2026 · **Phiên bản code được kiểm định:** nhánh `main`, commit `ca51592` · **Phương pháp:** review mã nguồn theo quy trình 6 bước (trinh sát → tìm lỗi theo từng ranh giới tin cậy → kiểm chứng độc lập → ghi kết quả có cấu trúc → kiểm chứng lần 2 → báo cáo). Mỗi vấn đề dưới đây được **hai lượt kiểm tra độc lập** xác nhận là đoạn code gây lỗi có thật.

**Lưu ý về mức độ chắc chắn:** các vấn đề được phát hiện bằng đọc mã nguồn. Sau khi sửa, toàn bộ bộ test (gồm test hồi quy mới) đã chạy qua trong Docker cô lập, không có mạng ra ngoài (xem mục 4). Các test hồi quy chứng minh hành vi **sau khi sửa** là đúng; chúng chưa được chạy trên code cũ để tái hiện lỗi trước khi sửa.

Ký hiệu trạng thái: ✅ đã sửa trong code · 🧪 có test hồi quy riêng · ✔️ toàn bộ test đã chạy qua trong Docker (22/11/2026).

---

## 1. Tổng quan

| # | Vấn đề | Thành phần | Ai có thể khai thác | Trạng thái |
|---|---|---|---|---|
| 1 | Kết nối thay thế cho cùng peerId kế thừa trạng thái "đã vào phòng" và "đã xác thực" | p2p-core + client | Signaling server bị chiếm / kẻ đứng giữa đường WebSocket | ✅ 🧪 ✔️ |
| 2 | Server chuyển nguyên token đăng nhập của người gửi sang peer nhận | signaling-server | Bất kỳ người cùng phòng (client sửa đổi) | ✅ 🧪 ✔️ |
| 3 | Token đăng nhập bị gửi tới server khác server đã cấp | client | Chủ server mặc định / server bất kỳ người dùng gõ vào | ✅ ✔️ |
| 4 | Sau khi rời phòng, kết nối vẫn relay được tin vào phòng cũ | signaling-server | Người từng ở trong phòng | ✅ 🧪 ✔️ |
| 5 | Giả mạo tác giả trong lịch sử chia sẻ, bị lưu thành "tin của tôi" | client | Peer đã xác thực trong phòng | ✅ ✔️ |
| 6 | Peer gửi trùng id tin nhắn có thể sửa / xoá tin của người khác | client | Peer trong phòng | ✅ ✔️ |
| 7 | Chọn "Không khớp" ở hộp thoại vân tay lại xoá mất vân tay hợp lệ đã lưu | client | Kẻ mạo danh dùng trùng tên | ✅ ✔️ |
| 8 | Một tin nhắn dài đặc biệt làm treo giao diện người nhận | client | Peer trong phòng | ✅ 🧪 ✔️ |
| 9 | Một peer ngừng đọc socket làm nghẽn luồng xử lý của server | signaling-server | Tài khoản bất kỳ | ✅ ✔️ |
| 10 | Kết nối WebSocket chưa đăng nhập được giữ vô thời hạn | signaling-server | Bất kỳ ai trên Internet | ✅ ✔️ |
| 11 | `/auth/login`, `/auth/register` không giới hạn tốc độ; bảng phiên không được dọn | signaling-server | Bất kỳ ai trên Internet | ✅ 🧪 ✔️ |
| 12 | Không giới hạn số phòng và độ dài tên phòng / peerId | signaling-server | Tài khoản bất kỳ | ✅ 🧪 ✔️ |
| 13 | Bộ giới hạn theo IP của `/turn-credentials` không bao giờ dọn bộ nhớ | signaling-server | Bất kỳ ai trên Internet | ✅ 🧪 ✔️ |

Thứ tự trên là thứ tự ưu tiên. **Vấn đề 1 và 2 quan trọng nhất**, vì chúng phá vỡ đúng điều đồ án cam kết: *signaling server chỉ giới thiệu peer, kể cả khi bị chiếm cũng không đọc được tin nhắn*. Vấn đề 3–8 là lỗi logic ở client. Vấn đề 9–13 liên quan đến chống quá tải; mức độ thực tế của chúng còn phụ thuộc cấu hình triển khai (proxy, Tomcat, bộ nhớ JVM).

**Điểm làm tốt** (đã kiểm tra, không có lỗi): lõi mật mã gồm ECDH P-256 + HKDF với khoá riêng mỗi chiều, AES-GCM có chống replay, chữ ký `PEER_IDENTITY` gắn với khoá ECDH của phiên, và `ROOM_PROOF` dùng HMAC so sánh thời gian hằng. Server dùng BCrypt, lưu token dưới dạng hash và tham số hoá SQL. File nhận được lưu an toàn, không thể ghi ra ngoài thư mục `Downloads`. MySQL chỉ mở trên localhost. Không có secret nào bị commit.

---

## 2. Chi tiết từng vấn đề

### Vấn đề 1 — Kết nối thay thế kế thừa trạng thái tin cậy của kết nối cũ

**Hiện tượng.** Khi một OFFER thứ hai tới với peerId của một peer đã vào phòng và đã xác thực, `RoomSession` dựng kết nối ICE/ECDH mới rồi âm thầm thay kết nối cũ (`peers.put`). Phía client (`P2pChatSession`) lưu "đã qua `ROOM_PROOF`" và "đã xác thực `PEER_IDENTITY`" **theo peerId**, và chỉ xoá khi nhận PEER_LEFT. Kết nối mới vì vậy bỏ qua cả hai bước kiểm tra, giao diện vẫn hiện VERIFIED với vân tay cũ, còn mọi tin nhắn sau đó được mã hoá tới một khoá ECDH chưa ai xác thực.

**Kịch bản khai thác.** Signaling server bị chiếm (hoặc kẻ đứng giữa khi dùng `ws://`) gửi một OFFER giả mang peerId của Bình. Người lạ trả lời OFFER đó, dù không có mật khẩu phòng và không có khoá danh tính của Bình, rồi đọc được tin nhắn gửi cho Bình và gửi tin dưới danh nghĩa Bình.

**Vị trí.** `p2p-core/.../RoomSession.java` (`onIceConnected`, đoạn `peers.put`); `client-javafx/.../P2pChatSession.java` (`handleRoomProof`, `admit`, `admittedOnly`, `handleTranscript`).

**Cách sửa.** Khi kết nối của một peerId bị thay, `RoomSession` gọi `onPeerLeft` cho kết nối cũ trước khi kết nối mới hoạt động. Client nhờ đó xoá sạch trạng thái tin cậy của peerId đó, và kết nối mới phải làm lại `ROOM_PROOF` + `PEER_IDENTITY` từ đầu.

### Vấn đề 2 — Server chuyển nguyên token đăng nhập sang peer khác

**Hiện tượng.** Client gắn `authToken` vào mọi bản tin OFFER/ANSWER/ICE_CANDIDATE để server kiểm tra lại. Server relay **nguyên đối tượng** `SignalMessage` cho peer đích mà không xoá trường này, nên peer đích nhận được token còn hạn 12 giờ của người gửi.

**Kịch bản khai thác.** Người cùng phòng dùng client đã sửa để đọc token, rồi dùng nó trong 12 giờ để vào phòng dưới tên tài khoản nạn nhân, xem danh sách phòng, hoặc đăng xuất nạn nhân.

**Vị trí.** `p2p-core/.../WebSocketSignalingClient.java` (`sendMessage`); `signaling-server/.../SignalingWebSocketHandler.java` (`relayToTargetPeer`).

**Cách sửa.** Server gọi `signal.setAuthToken(null)` trước khi relay. Test: `SignalingSecurityFixesTest.relayedSignalDoesNotCarryTheSendersAuthToken`.

> Sau khi triển khai bản sửa, nên coi như token của những người từng ở chung phòng với người lạ đã bị lộ (token tự hết hạn sau 12 giờ).

### Vấn đề 3 — Token bị gửi tới server không phải nơi đã cấp

**Hiện tượng.** Người dùng đăng nhập vào server A (ô server ở màn hình đăng nhập). Màn hình Home lại tự đặt ô server về mặc định `wss://chat.zingme.fun` và gửi ngay `GET /rooms` kèm token tới đó. Việc vào phòng (JOIN), lấy TURN và tải lại danh sách phòng cũng dùng ô server này, và người dùng sửa được ô đó.

**Hậu quả.** Người dùng server tự host bị lộ token cho chủ server mặc định, hoặc cho bất kỳ server nào gõ vào sau đó.

**Vị trí.** `client-javafx/.../AuthController.java` (`login`), `Navigator.java` (`showHome`, `showHomeAfterLeaving`), `HomeController.java` (`initialize`, `setAuthenticatedUser`, `loadRooms`).

**Cách sửa.** Truyền địa chỉ server đăng nhập sang Home. Ở chế độ tài khoản, ô server bị khoá vào đúng server đó; muốn đổi server phải đăng xuất rồi đăng nhập lại.

### Vấn đề 4 — Kết nối đã rời phòng vẫn relay được

**Hiện tượng.** Sau LEAVE (hoặc khi JOIN sang phòng khác thất bại), server xoá peer khỏi danh sách phòng nhưng **không xoá** các thuộc tính `roomId`/`peerId`/`authToken` trên session. Hàm kiểm tra quyền relay lại chỉ so với các thuộc tính đó.

**Hậu quả.** Một session đã rời phòng (kể cả khi phòng đã đủ người) vẫn gửi được OFFER/ICE tới các thành viên hiện tại, dưới peerId cũ.

**Vị trí.** `signaling-server/.../SignalingWebSocketHandler.java` (`removeAndNotify`, `isAuthorized`).

**Cách sửa.** Xoá các thuộc tính ngay khi rời phòng. `isAuthorized` kiểm tra thêm với sổ phòng thật (`RoomRegistry.isMember`). Test: `sessionCannotRelayAfterLeavingTheRoom`, `failedRejoinAlsoDropsTheOldBinding`.

### Vấn đề 5 — Lịch sử chia sẻ giả mạo bị lưu thành "tin của tôi"

**Hiện tượng.** `MESSAGE_TRANSCRIPT` (lịch sử chia sẻ cho người mới vào) lấy `authorId` và `authorName` từ payload mà không kiểm tra. Lúc đang trong phòng, giao diện có ghi nhãn "lịch sử · X chia sẻ, không xác minh được người viết". Nhưng nếu `authorId` trùng peerId của chính người nhận, tin hiện ra như bong bóng của chính mình. Khi bật "lưu lịch sử", hàm `saveLocalHistory` còn lưu tin đó thành `mine = true`. Lần sau mở lại, tin giả hiện thành tin chính mình viết và **mất hẳn nhãn cảnh báo**.

**Vị trí.** `client-javafx/.../P2pChatSession.java` (`handleTranscript`); `RoomController.java` (`saveLocalHistory`, `loadLocalHistory`).

**Cách sửa.** Bỏ qua mục lịch sử có `authorId` trùng peerId của chính người nhận. Khi lưu, tin đến từ lịch sử do người khác chia sẻ không bao giờ được đánh dấu là "của tôi".

### Vấn đề 6 — Trùng id tin nhắn cho phép sửa / xoá tin của người khác

**Hiện tượng.** Id tin nhắn do người gửi tự đặt. Phía giao diện, việc tìm tin để sửa dùng "tin đầu tiên có id X", việc áp dụng sửa đổi thay "mọi tin có id X", và trạng thái đã xoá cũng lưu theo id. Nếu tin của M với id X tới trước tin thật của B cùng id X, M có thể sửa hoặc xoá tin của B trên máy người nhận, còn các lần sửa thật của B lại bị từ chối.

**Vị trí.** `client-javafx/.../ChatTranscriptManager.java` (`findMessage`, `replaceMessage`); `RoomController.java` (`onMessageEdited`, `applyEdit`); `MessageContext.java`; `TranscriptCell.java`.

**Cách sửa.** Mọi thao tác sửa / xoá đều theo **cặp (id, tác giả)**. Thêm `findMessage(id, authorId)`; `replaceMessage` chỉ thay tin cùng id và cùng tác giả; trạng thái sửa/xoá lưu theo khoá `tác giả + id`.

### Vấn đề 7 — "Không khớp" xoá mất vân tay hợp lệ

**Hiện tượng.** Khi một khoá lạ xuất hiện dưới tên đã được ghi nhớ, ứng dụng cảnh báo "khoá đã đổi". Nếu người dùng chọn đúng "Không khớp", code lại gọi `TrustedPeers.forget(tên)`, tức là **xoá vân tay thật** đã lưu. Từ lần sau, kẻ mạo danh dùng cùng tên không còn bị cảnh báo nữa.

**Vị trí.** `client-javafx/.../RoomController.java` (`verifyFingerprint`); `TrustedPeers.java` (`forget`).

**Cách sửa.** Khi hộp thoại mở vì khoá đã đổi, chọn "Không khớp" sẽ **giữ nguyên** vân tay đã lưu. Chỉ xoá khi người dùng phủ nhận chính khoá đang được tin cậy.

### Vấn đề 8 — Một tin nhắn làm treo giao diện

**Hiện tượng.** Tin nhắn trực tiếp không bị giới hạn độ dài; giới hạn duy nhất là 1 MiB của kênh UDP, tương đương khoảng 750.000 ký tự. Trong khi đó, sửa tin và lịch sử đều bị giới hạn 12.000 ký tự. Bộ đọc Markdown, với mỗi dấu `*` mở không có dấu đóng, lại quét tới cuối dòng, nên độ phức tạp là O(n²). Bộ đọc chạy trên luồng JavaFX, vì vậy một tin kiểu `*a *a *a …` làm treo cửa sổ người nhận, và treo lại mỗi lần cuộn qua tin đó.

**Vị trí.** `client-javafx/.../P2pChatSession.java` (`handleMessage`); `MarkdownParser.java` (`parseInline`, `findClosing`).

**Cách sửa.** (1) Bỏ qua tin dài hơn `GroupHistory.MAX_TEXT_CHARS` (12.000 ký tự), vẫn gửi ACK để bên gửi không gửi lại. (2) `findClosing` ghi nhớ vị trí "từ đây không còn dấu đóng" cho từng loại dấu, nên mỗi dòng chỉ còn quét tuyến tính. Test: `MarkdownParserComplexityTest` (chuỗi 600.000 ký tự phải phân tích xong dưới 5 giây và cho kết quả không đổi).

### Vấn đề 9 — Peer ngừng đọc socket làm nghẽn server

**Hiện tượng.** Hàm `send` của server ghi **đồng bộ** trong khi giữ khoá `synchronized(session)`, trên chính luồng đang xử lý bản tin của người khác. Một peer cố ý ngừng đọc socket làm mọi luồng gửi tới nó bị treo (relay, PEER_JOINED, PEER_LEFT) và xếp hàng trên khoá. Lặp lại ở nhiều phòng thì có thể làm cạn luồng xử lý của Tomcat.

**Vị trí.** `signaling-server/.../SignalingWebSocketHandler.java` (`send`, `broadcastToOthers`).

**Cách sửa.** Bọc mỗi session bằng `ConcurrentWebSocketSessionDecorator` với giới hạn 10 giây và 512 KB. Các luồng khác chỉ đưa bản tin vào hàng đợi rồi trả về ngay. Session nào nhận quá chậm sẽ bị đóng, thay vì làm nghẽn người khác.

### Vấn đề 10 — Kết nối chưa đăng nhập được giữ vô thời hạn

**Hiện tượng.** `/ws` chấp nhận mọi kết nối và chỉ kiểm tra token khi nhận bản tin JOIN. Không có idle timeout, không có hạn chót phải JOIN, không giới hạn số kết nối mỗi IP. Một máy có thể mở hàng nghìn kết nối im lặng và chiếm hết slot kết nối của server.

**Vị trí.** `signaling-server/.../WebSocketConfig.java`; `SignalingWebSocketHandler.java`.

**Cách sửa.** Kết nối phải JOIN trong 30 giây, nếu không sẽ bị đóng. Mỗi địa chỉ được tối đa 32 kết nối đồng thời (IPv6 gộp theo dải /64). Không đặt idle timeout chung, vì thành viên trong phòng có thể im lặng lâu một cách hợp lệ.

### Vấn đề 11 — `/auth` không giới hạn tốc độ

**Hiện tượng.** Mỗi lần gọi `/auth/login` (với username có thật) hoặc `/auth/register` tốn một lần tính BCrypt, đăng ký còn ghi thêm một dòng `users`. Mỗi lần đăng nhập thành công ghi thêm một dòng `sessions`, và không có chỗ nào xoá các dòng đã hết hạn hoặc đã đăng xuất.

**Vị trí.** `signaling-server/.../AuthController.java`, `AuthService.java`.

**Cách sửa.** Tối đa 10 lần đăng nhập/đăng ký mỗi phút cho một địa chỉ (trả mã 429). Khi đăng nhập, xoá các phiên đã hết hạn hoặc đã đăng xuất của tài khoản đó. Đồng thời sửa câu UPDATE ghi khoá lần đầu thành `… AND public_key IS NULL` để tránh race. Test: `RequestRateLimiterTest`.

### Vấn đề 12 — Không giới hạn số phòng và độ dài tên

**Hiện tượng.** Server chỉ giới hạn 8 người mỗi phòng. Không có giới hạn tổng số phòng, `roomId`/`peerId` dài tuỳ ý (tới 64 KB), `GET /rooms` trả về mọi tên phòng, và mỗi lần đóng kết nối phải quét toàn bộ các phòng.

**Vị trí.** `signaling-server/.../RoomRegistry.java`; `SignalingWebSocketHandler.java` (`handleJoin`).

**Cách sửa.** Giới hạn `roomId` ≤ 200 ký tự, `peerId` ≤ 100, tên khách ≤ 64 (vượt quá thì trả `INVALID_JOIN`). Giới hạn tối đa 5.000 phòng (vượt quá thì trả mã mới `SERVER_BUSY`; client đã có thông báo tiếng Việt). Thêm chỉ mục theo session để việc rời phòng có độ phức tạp O(1). Test: `oversizedJoinFieldsAreRejected`, `closingTheConnectionStillRemovesThePeer`.

### Vấn đề 13 — Bộ giới hạn TURN không dọn bộ nhớ

**Hiện tượng.** `TurnController` giữ một map theo IP mà không bao giờ xoá key. Mỗi địa chỉ mới (ví dụ xoay vòng trong cả dải IPv6) để lại một key vĩnh viễn và lại được hưởng hạn mức mới.

**Vị trí.** `signaling-server/.../TurnController.java`.

**Cách sửa.** Dùng lớp chung mới `RequestRateLimiter`: tự xoá key đã hết hạn, giới hạn tổng số key ở 10.000, và gộp IPv6 theo dải /64. Lớp này cũng được dùng cho `/auth`. Test: `RequestRateLimiterTest`.

---

## 3. Các ứng viên đã bị bác bỏ (không phải lỗi)

| Ứng viên | Lý do bác bỏ |
|---|---|
| Đăng nhập bỏ qua kiểm tra khoá danh tính khi không gửi `publicKey` | Khoá công khai vốn được gửi cho mọi người trong phòng, nên việc so khớp không bảo vệ được gì |
| Peer gửi hàng loạt FILE_OFFER | Peer trong phòng có thể gây tác động tương đương bằng tin nhắn thường. Đã ghi lại thành đề xuất cải thiện |
| `/turn-credentials` không yêu cầu đăng nhập | Ai cũng tự đăng ký được tài khoản, nên thiếu kiểm tra token không tạo khác biệt về mức tin cậy. Rủi ro lạm dụng TURN nằm ở cấu hình coturn |

## 4. Kiểm thử

**Môi trường:** container `maven:3.9.9-eclipse-temurin-17` trên Docker network `--internal` (có địa chỉ IP riêng để ICE chạy được, nhưng không ra được Internet). Giới hạn 6 GB RAM và 4 CPU; Maven chạy offline với kho thư viện gắn chỉ-đọc.

**Kết quả (22/11/2026): BUILD SUCCESS, 283 test, 0 lỗi.**

| Module | Số test | Kết quả |
|---|---|---|
| crypto | 42 | ✔️ |
| signaling-server | 32 | ✔️ |
| p2p-core | 121 | ✔️ (bỏ qua 1: `RoomSessionEightPeerMeshScalabilityTest`, chỉ chạy khi bật cờ) |
| client-javafx | 88 | ✔️ (bỏ qua 2: benchmark truyền file chỉ chạy khi bật cờ; DPAPI chỉ có trên Windows) |

**Test hồi quy mới hoặc bổ sung:**

| Test | Vấn đề |
|---|---|
| `p2p-core/.../RoomSessionEstablishedConnectionOverwriteSecurityTest` (thêm kiểm tra `onPeerLeft` khi thay kết nối) | 1 |
| `signaling-server/.../ws/SignalingSecurityFixesTest` | 2, 4, 12 |
| `signaling-server/.../limit/RequestRateLimiterTest` | 11, 13 |
| `client-javafx/.../view/MarkdownParserComplexityTest` | 8 |

**Test cũ đã được điều chỉnh** (hành vi mới là đúng, test cũ giả định hành vi cũ):
- `SignalingServerContentOpacityTest`: test cũ giả lập người gửi "đã JOIN" chỉ bằng thuộc tính session. Sau bản sửa vấn đề 4, người gửi phải thật sự có trong sổ phòng, nên test được sửa để JOIN thật.
- `WebSocketSignalingClientCapacityTest`: test mở 100 kết nối từ cùng localhost, nên được nới giới hạn bằng `--chat-p2p.ws.max-connections-per-address=1000`.

**Chưa có test tự động:** vấn đề 3, 5, 6, 7 (luồng giao diện JavaFX). Nên kiểm tra thủ công khi demo.

**Lưu ý khi triển khai lên VPS:** giới hạn 32 kết nối mỗi địa chỉ (cấu hình được bằng `chat-p2p.ws.max-connections-per-address`) dựa vào IP thật của client. Reverse proxy **phải** gửi header `X-Forwarded-For`. Nếu không, mọi người dùng sẽ bị coi là cùng một địa chỉ (IP của proxy) và server chỉ nhận tổng cộng 32 kết nối.

## 5. Đề xuất cải thiện thêm (chưa sửa)

- Từ chối kết nối `ws://` hoặc `http://` tới server không phải localhost, để mật khẩu và token không đi dạng rõ.
- Khi `PEER_IDENTITY` sai chữ ký, ngắt kết nối với peer đó thay vì chỉ hiện thông báo.
- Ghi nhớ vân tay theo tài khoản (khi bật đăng nhập) thay vì theo tên hiển thị, và chuẩn hoá Unicode tên trước khi so sánh.
- Giới hạn số offer file đang chờ của mỗi peer và cho chúng hết hạn; giới hạn độ dài khung chat.
- Nâng Spring Boot lên bản mới hơn 3.3.4 để có bản vá BCrypt (CVE-2025-22228).
- `WindowsIceFirewallPermission`: gọi `powershell.exe` bằng đường dẫn tuyệt đối, và giới hạn rule firewall cho đúng chương trình Java của ứng dụng, bỏ profile Public.
- Trên VPS: đặt `CHAT_P2P_SERVER_ADDRESS=127.0.0.1` sau reverse proxy có TLS; cấu hình `denied-peer-ip` và quota cho coturn.
