Tài liệu kỹ thuật đầy đủ — chat-p2p-java

# TÀI LIỆU KỸ THUẬT: CHAT-P2P-JAVA

*Đây là tài liệu kỹ thuật đầy đủ của dự án — không chỉ mô tả chức năng, mà bao quát toàn bộ: kiến trúc, module hiện có trong code, yêu cầu phi chức năng, chiến lược kiểm thử, cấu hình/vận hành, xử lý lỗi, và rủi ro kỹ thuật. Đọc cùng [De-cuong-Chat-P2P-Java.md](De-cuong-Chat-P2P-Java.md) (mục tiêu đồ án, nộp GVHD) và [Phan-cong-cong-viec.md](Phan-cong-cong-viec.md) (chia việc 2 thành viên).*

**Cấu trúc tài liệu** (11 phần):

| Phần | Nội dung |
|---|---|
| A | Giới thiệu tài liệu |
| B | Tổng quan hệ thống (bối cảnh, yêu cầu chức năng/phi chức năng, phạm vi) |
| C | Kiến trúc hệ thống (logic, module hiện có trong repo, triển khai) |
| D | Phân tích kiến trúc và chức năng thật của **chitchatter** (bản gốc tham chiếu) |
| E | Thiết kế thi công chi tiết cho **chat-p2p-java** (blueprint code) |
| F | Yêu cầu phi chức năng & chiến lược kiểm thử |
| G | Cấu hình, build & vận hành |
| H | Xử lý lỗi & logging |
| J | Rủi ro kỹ thuật & giới hạn đã biết |
| K | Phụ lục (thuật ngữ, tài liệu tham khảo, lịch sử thay đổi) |

---

# PHẦN A — GIỚI THIỆU

## A.1 Mục đích tài liệu

Tài liệu này phục vụ 2 mục đích:
1. **Ghi lại hiểu biết kỹ thuật** về cách `chitchatter` (dự án tham chiếu) thực sự hoạt động, dựa trên đọc mã nguồn thật — không suy đoán từ README hay tài liệu marketing (Phần D).
2. **Làm bản thiết kế thi công** (implementation spec) cho `chat-p2p-java` — đủ chi tiết để cầm tài liệu này code thẳng, không cần thiết kế lại từ đầu khi bắt tay vào từng module (Phần E trở đi).

## A.2 Phạm vi tài liệu

Bao gồm: kiến trúc hệ thống, giao thức mạng (signaling + P2P), mô hình dữ liệu, thiết kế từng chức năng trong số 12 chức năng mục tiêu, yêu cầu phi chức năng, chiến lược kiểm thử, hướng dẫn build/vận hành, chiến lược xử lý lỗi, và rủi ro kỹ thuật.

**Không bao gồm**: quy trình quản lý dự án, lịch làm việc theo tuần (xem [Phan-cong-cong-viec.md](Phan-cong-cong-viec.md)), lý do chọn đề tài / mục tiêu học thuật (xem [De-cuong-Chat-P2P-Java.md](De-cuong-Chat-P2P-Java.md)).

## A.3 Đối tượng đọc

Chính là 2 thành viên thực hiện đồ án (vừa là tác giả vừa là người thi công) và giảng viên hướng dẫn muốn hiểu sâu về mặt kỹ thuật. Giả định người đọc đã biết Java cơ bản, khái niệm mạng máy tính (TCP/UDP/NAT) và mật mã học ứng dụng ở mức nhập môn — các khái niệm chuyên sâu hơn (ICE/STUN/TURN, ECDH, AES-GCM) được giải thích khi xuất hiện lần đầu và tổng hợp lại ở Phụ lục K.1.

## A.4 Tài liệu liên quan

- [De-cuong-Chat-P2P-Java.md](De-cuong-Chat-P2P-Java.md) — đề cương đồ án tốt nghiệp.
- [Phan-cong-cong-viec.md](Phan-cong-cong-viec.md) — phân công công việc, interface chung `DataChannel`, lịch chạy song song.
- Mã nguồn tham chiếu: `chitchatter-develop/` (kèm trong workspace).
- Mã nguồn đang thi công: `chat-p2p-java/{common,crypto,p2p-core,signaling-server,client-javafx}`.

## A.5 Quy ước trong tài liệu

- **In đậm** đánh dấu quyết định thiết kế hoặc lưu ý quan trọng cần nhớ khi code.
- Khối code có comment `// TODO` là phần **chưa cài đặt thật**, đang là stub trong repo.
- Tham chiếu chéo dùng dạng "Phần X, mục Y" (ví dụ "Phần E, mục 6.3") — luôn trỏ nội bộ trong chính tài liệu này.
- Tên lớp/phương thức viết dạng `code` là tên thật đã tồn tại trong repo, hoặc tên đề xuất sẽ tạo mới — được phân biệt rõ bằng chú thích "(đã có)" / "(chưa có, cần tạo)" ở lần nhắc đầu tiên.

---

# PHẦN B — TỔNG QUAN HỆ THỐNG

## B.1 Bối cảnh & bài toán

Ứng dụng chat desktop viết bằng Java, cho phép nhiều người dùng trò chuyện **ngang hàng (P2P)**, **mã hoá đầu-cuối**, không lưu trữ nội dung trên máy chủ trung gian. Lấy `chitchatter` (ứng dụng web P2P mã nguồn mở, dùng WebRTC) làm tham chiếu về kiến trúc mesh, mô hình dữ liệu và tập tính năng — không port code, vì toàn bộ stack khác nhau (xem Phần D, mục 1 và Phần E, mục 12 để biết chính xác chỗ nào giống/khác).

## B.2 Yêu cầu chức năng (12 mục tiêu, chi tiết thiết kế ở Phần E)

| # | Chức năng | Thiết kế chi tiết |
|---|---|---|
| 1 | Nhiều peer trong 1 phòng (mesh) | Phần E, mục 7 |
| 2 | Phòng công khai / phòng riêng tư | Phần E, mục 12.3 |
| 3 | Nhắn tin nhóm, đa dòng, Markdown | Phần E, mục 8.1 |
| 4 | Direct message (chat riêng 1-1 trong phòng đông người) | Phần E, mục 8.2 |
| 5 | Trạng thái đang gõ | Phần E, mục 8.3 |
| 6 | Conversation backfilling (lịch sử chat cho peer mới) | Phần E, mục 4 bước 6 |
| 7 | Xác thực danh tính peer tự động (chữ ký số) | Phần E, mục 5 |
| 8 | Truyền file mã hoá | Phần E, mục 8.4 |
| 9 | Video call | Phần E, mục 8.5 |
| 10 | Audio call | Phần E, mục 8.5 |
| 11 | Screen share | Phần E, mục 8.5 |
| 12 | Cài đặt cá nhân (theme sáng/tối, âm thanh, thông báo) | Phần E, mục 8.6 |

*(Nhúng ứng dụng qua iframe — chức năng thứ 13 của bản gốc — không áp dụng cho app desktop, xem Phần E, mục 8.7 và Phần D, mục 13.)*

## B.3 Yêu cầu phi chức năng (tóm tắt — chi tiết & số đo cụ thể ở Phần F)

| Nhóm | Yêu cầu tóm tắt |
|---|---|
| Bảo mật | Toàn bộ nội dung (tin nhắn, file, media) mã hoá đầu-cuối AES-256-GCM; khoá phiên trao bằng ECDH; danh tính peer xác thực bằng chữ ký ECDSA; signaling server không bao giờ đọc/lưu nội dung |
| Hiệu năng | Thiết lập kết nối P2P nên hoàn tất trong vài giây trên mạng thường; độ trễ tin nhắn tối thiểu hoá (không qua trung gian nội dung) |
| Độ tin cậy | Ephemeral theo thiết kế — mất tin nhắn khi rời phòng là hành vi **đúng**, không phải lỗi; signaling server không giữ trạng thái bền |
| Khả năng mở rộng | Mesh đầy đủ giới hạn quy mô phòng bởi băng thông/CPU từng máy client (tăng theo N²) — chấp nhận được ở quy mô phòng nhỏ (đồ án nhắm ~2-8 peer) |
| Khả năng vận hành | Không cần API server phức tạp — chỉ 1 signaling server nhẹ + (khuyến nghị) 1 TURN server dự phòng |

## B.4 Ngoài phạm vi

Không làm: tài khoản người dùng bền vững/danh bạ liên hệ lâu dài (theo đề cương); nhúng web/iframe (không có ý nghĩa với app desktop, Phần E mục 8.7); đồng bộ đa thiết bị cho 1 người dùng.

---

# PHẦN C — KIẾN TRÚC HỆ THỐNG

## C.1 Kiến trúc logic tổng thể

```
                         ┌─────────────────────┐
                         │   signaling-server   │  (Spring Boot + WebSocket)
                         │  RoomRegistry, relay  │  KHÔNG đọc nội dung
                         └──────────▲───────────┘
                        JSON/WebSocket (SignalMessage)
                                    │
        ┌───────────────────────────┴───────────────────────────┐
        │                                                        │
┌───────▼────────┐                                       ┌───────▼────────┐
│  client-javafx   │◄─────────── DataChannel (N) ───────►│  client-javafx   │
│  (Peer A)         │   ice4j P2P, moi byte da AES-GCM     │  (Peer B)         │
└───────────────────┘                                       └───────────────────┘
```

2 tầng giao thức tách biệt hoàn toàn — chi tiết đầy đủ ở Phần E, mục 2:
- **Tầng signaling**: JSON qua WebSocket, chỉ để JOIN/LEAVE phòng và trao đổi SDP/ICE lúc thiết lập kết nối.
- **Tầng dữ liệu P2P**: sau khi kết nối trực tiếp mở xong, mọi thứ (chat, file, media) đi qua `Envelope` mã hoá, không còn liên quan tới signaling-server.

## C.2 Kiến trúc module Maven hiện có trong repo

*(Đây là mô tả **trạng thái thật hiện tại của code**, không phải chỉ là kế hoạch — xem README.md ở gốc repo để biết lệnh build/chạy.)*

| Module | Gói Java gốc | Lớp chính đã có | Test đã có | Trạng thái |
|---|---|---|---|---|
| `common` | `com.datn.chatp2p.common` | `DataChannel` (interface), `ChatMessage`, `Peer`, `PeerVerificationState`, `SignalMessage`, `SignalType` | — | Hoàn chỉnh cho scope hiện tại |
| `crypto` | `com.datn.chatp2p.crypto` | `KeyExchangeService` (ECDH secp256r1), `AesGcmCipher`, `Fingerprint` | `KeyExchangeServiceTest`, `AesGcmCipherTest` (5 test) | Hoàn chỉnh, đã test pass; cần thêm `IdentitySignatureService` (Phần E, mục 5.1) |
| `p2p-core` | `com.datn.chatp2p.p2p` | `LoopbackDataChannel` (dùng thật cho demo), `P2pDataChannel` (stub), `SignalingClient`/`WebSocketSignalingClient` (stub) | `LoopbackDataChannelTest` | Chỉ phần loopback là thật; `P2pDataChannel`/`WebSocketSignalingClient` cần hoàn thiện theo Phần E, mục 6 |
| `signaling-server` | `com.datn.chatp2p.signaling` | `SignalingServerApplication`, `SignalingWebSocketHandler`, `RoomRegistry`, `PeerSession`, `WebSocketConfig` | `SignalingWebSocketHandlerTest` (integration test WebSocket thật) | Hoàn chỉnh cho vai trò relay JOIN/PEER_LIST/PEER_JOINED/PEER_LEFT/OFFER/ANSWER/ICE_CANDIDATE |
| `client-javafx` | `com.datn.chatp2p.client` | `ChatApplication`, `HomeController`, `RoomController`, `PeerListCell`, `MessageListCell`, `DemoPeerSimulator`, `RoomNameGenerator` | `RoomNameGeneratorTest` | UI hoàn chỉnh cho demo loopback; cần đấu nối `RoomSession` thật (Phần E, mục 10) |

**Phụ thuộc giữa các module** (Maven `dependencyManagement` ở pom cha):

```
common  ←──  crypto
common  ←──  p2p-core
common, crypto, p2p-core  ←──  client-javafx
common  ←──  signaling-server
```

`signaling-server` **không** phụ thuộc `crypto`/`p2p-core` — đúng nguyên tắc "server chỉ relay, không xử lý nội dung/mã hoá".

## C.3 Kiến trúc triển khai (deployment view)

- **Mỗi người dùng** chạy 1 tiến trình `client-javafx` trên máy mình (Windows/macOS/Linux — JavaFX đa nền tảng).
- **1 `signaling-server`** chạy ở một địa chỉ mà mọi client tiếp cận được — có thể là máy chủ LAN (demo nội bộ) hoặc VPS/dịch vụ cloud có địa chỉ public (dùng thật qua Internet). Không cần HTTPS bắt buộc cho đồ án (WebSocket thường `ws://`), nhưng nên dùng `wss://` nếu triển khai qua Internet công khai để tránh bị chặn bởi proxy/firewall chỉ cho phép traffic mã hoá.
- **1 TURN server** dự phòng khi ICE không tìm được đường truyền trực tiếp (mạng chặn NAT symmetric, nhiều tầng NAT lồng nhau...) — xem Phần G.6 về lựa chọn triển khai TURN.
- **Không có database, không có storage server** — đúng tinh thần ephemeral.

## C.4 Nguyên tắc kiến trúc xuyên suốt

Xem đầy đủ ở Phần E, mục 1. Tóm tắt: (1) mọi dữ liệu qua `DataChannel` phải tự mã hoá vì không có DTLS như WebRTC; (2) 1 `DataChannel` = 1 kết nối 1-1, phòng N peer = N kết nối song song; (3) đa kênh logic tự thiết kế bằng `Envelope`; (4) signaling server chỉ relay; (5) ephemeral tuyệt đối trừ cấu hình cá nhân.

---

# PHẦN D — PHÂN TÍCH KIẾN TRÚC VÀ CHỨC NĂNG CỦA CHITCHATTER

*Phân tích mã nguồn thật của `chitchatter-develop` (không suy đoán từ README) để hiểu đúng cơ chế của từng chức năng trước khi thiết kế tương đương ở Phần E.*

## D.1 Tổng quan kiến trúc

Chitchatter là ứng dụng web **thuần client** (React + Vite), **không có API server bắt buộc**. Toàn bộ logic nghiệp vụ chạy trong trình duyệt của từng người dùng; các máy chủ bên ngoài chỉ đóng 3 vai trò hạ tầng dùng chung, không phụ thuộc riêng vào chitchatter:

1. **BitTorrent tracker công khai** (qua thư viện `trystero`) — dùng làm kênh "signaling" để hai peer cùng phòng tìm thấy nhau và trao đổi SDP/ICE.
2. **TURN relay server công khai** (cấu hình trong `rtcConfig`) — dự phòng khi không thể kết nối trực tiếp.
3. **GitHub Pages** — chỉ host static asset (HTML/JS/CSS), không có vai trò runtime.

Không có máy chủ nào nhìn thấy nội dung chat: sau khi 2 peer tìm thấy nhau qua tracker, họ mở thẳng một `RTCPeerConnection` (WebRTC) — kênh dữ liệu (data channel) này được **mã hoá bằng DTLS ở tầng giao thức**, trình duyệt tự lo, ứng dụng không cần tự viết thêm mã hoá cho nội dung.

```
Peer A (trình duyệt)                                   Peer B (trình duyệt)
      |                                                       |
      |  (1) joinRoom(roomId) -> Trystero băm room key,       |
      |      quảng cáo trên BitTorrent tracker công khai      |
      |------------------------> Tracker <--------------------|
      |                                                       |
      |  (2) Tracker giúp trao đổi SDP offer/answer + ICE      |
      |      candidate (không thấy nội dung, chỉ metadata kết  |
      |      nối); TURN relay dự phòng nếu NAT chặn trực tiếp  |
      |<===================== (qua tracker) ==================>|
      |                                                       |
      |  (3) RTCPeerConnection trực tiếp, DataChannel mã hoá   |
      |      bằng DTLS (trình duyệt tự làm)                    |
      |<======================================================>|
      |   - PEER_METADATA (public key + chữ ký danh tính)      |
      |   - MESSAGE / MEDIA_MESSAGE / MESSAGE_TRANSCRIPT        |
      |   - TYPING_STATUS_CHANGE, FILE_OFFER                   |
      |   - AUDIO_CHANGE / VIDEO_CHANGE / SCREEN_SHARE          |
      |     (kèm MediaStream qua addStream, kênh riêng)         |
```

## D.2 Stack công nghệ thật

| Thành phần | Công nghệ | Ghi chú |
|---|---|---|
| UI framework | React + TypeScript, Vite | SPA, routing bằng `react-router-dom` |
| P2P / signaling | [`trystero`](https://github.com/dmotz/trystero) (chiến lược mặc định: BitTorrent tracker) | Bọc `RTCPeerConnection`, tự động ICE, expose API `joinRoom` |
| Truyền file | [`secure-file-transfer`](https://github.com/jeremyckahn/secure-file-transfer) (nền WebTorrent) | File mã hoá thành torrent, chia sẻ qua `magnetURI` |
| Mã hoá/ký danh tính | Web Crypto API (`window.crypto.subtle`), thuật toán `RSASSA-PKCS1-v1_5` + SHA-256 | Chỉ dùng để **ký/xác thực danh tính**, không mã hoá nội dung tin nhắn (xem mục D.6) |
| Lưu trữ cục bộ | `localforage` (wrapper IndexedDB) | Chỉ lưu **cài đặt người dùng** (kể cả cặp khoá), **không lưu tin nhắn** |
| Markdown | `react-markdown` (+ syntax highlight) | Render nội dung tin nhắn |
| Deploy | GitHub Pages (tĩnh) | Không có backend runtime bắt buộc |

## D.3 Phòng chat (Room)

- **Room ID**: chuỗi bất kỳ do người dùng đặt hoặc UUID sinh ngẫu nhiên (`pages/Home`). Route: `/public/:roomId` hoặc `/private/:roomId` (`config/routes.ts`).
- **Phòng công khai** (`PublicRoom`): `PeerRoom` khởi tạo với `password: roomId` — tức Trystero băm room key trực tiếp từ tên phòng. Ai biết tên phòng là vào được.
- **Phòng riêng tư** (`PrivateRoom`): người dùng nhập thêm **mật khẩu**. Mật khẩu **không bao giờ được gửi qua mạng ở dạng thô** — được băm cục bộ:
  ```
  secret = base64(SHA-256(`${roomId}_${password}`))
  ```
  `secret` này mới là giá trị được dùng làm `password` thật cho Trystero (`services/Encryption.encodePassword`), và có thể nhúng vào URL dạng `#secret=...` để chia sẻ (giữ trong URL *hash*, không gửi lên server nào vì hash fragment không nằm trong HTTP request). Ai không biết password gốc thì không tính ra được `secret`, và không đoán được room key thật của Trystero → không tham gia được swarm.
- **Nhiều peer trong 1 phòng**: Trystero thiết lập **mesh đầy đủ** — mỗi peer mở `RTCPeerConnection` riêng với *từng* peer khác trong phòng (không qua trung gian). `PeerRoom.getPeers()` trả danh sách toàn bộ kết nối hiện có.

## D.4 Lớp kết nối P2P — `PeerRoom` (`lib/PeerRoom/PeerRoom.ts`)

`PeerRoom` là lớp bọc mỏng quanh `Room` của Trystero, chuẩn hoá thành các API mà UI dùng:

- `onPeerJoin(hookType, fn)` / `onPeerLeave(hookType, fn)` — nhiều "consumer" (nhắn tin, video, file...) cùng đăng ký nhận sự kiện peer vào/ra qua một `Map<PeerHookType, handler>`, tránh ghi đè lẫn nhau.
- `makeAction<T>(peerAction, namespace)` — tạo một **kênh hành động** kiểu `[sender, receiver, progress, detach]` trên nền `room.makeAction(actionName)` của Trystero. Đây là cơ chế **đa kênh logic trên cùng 1 data channel WebRTC**: mỗi `actionName = "${namespace}.${PeerAction}"` (vd. `"g.0"` cho MESSAGE trong nhóm, `"dm.0"` cho MESSAGE trong direct message) là một luồng gửi/nhận độc lập.
- `addStream` / `removeStream` — gắn/gỡ `MediaStream` (webcam, mic, screen share) vào kết nối, tách biệt hoàn toàn khỏi các "action" dữ liệu ở trên (WebRTC xử lý media track khác cơ chế với data channel message).
- `getPeerConnectionTypes()` — gọi `RTCPeerConnection.getStats()`, soi `candidate-pair` đã thành công để suy ra kết nối là **DIRECT** hay **RELAY** (qua TURN) — dùng cho UI chẩn đoán kết nối.

### `PeerAction` — danh sách đầy đủ 9 loại hành động (`models/network.ts`)

| PeerAction | Namespace dùng | Mục đích |
|---|---|---|
| `MESSAGE` | group hoặc dm | Tin nhắn văn bản |
| `MEDIA_MESSAGE` | group | Tin nhắn media nhúng trực tiếp (ảnh/audio/video nhỏ) |
| `MESSAGE_TRANSCRIPT` | group | Gửi lại toàn bộ lịch sử chat cho peer mới (backfilling) |
| `PEER_METADATA` | group | userId, tên hiển thị, public key, chữ ký danh tính |
| `AUDIO_CHANGE` | group | Thông báo bật/tắt mic |
| `VIDEO_CHANGE` | group | Thông báo bật/tắt webcam |
| `SCREEN_SHARE` | group | Thông báo bật/tắt chia sẻ màn hình |
| `FILE_OFFER` | group | Gửi `magnetURI` của file đang chia sẻ (hoặc `null` để thu hồi) |
| `TYPING_STATUS_CHANGE` | group hoặc dm | Trạng thái đang gõ |

`ActionNamespace` chỉ có 2 giá trị: `GROUP` ("g") và `DIRECT_MESSAGE` ("dm") — direct message thực chất là **cùng cơ chế action, khác namespace và lọc theo `targetPeerId`**, không phải kết nối riêng.

## D.5 Xác thực danh tính peer (không phải mã hoá nội dung)

Khi có peer mới vào phòng (`onPeerJoin`), **tự động** (không cần người dùng bấm gì):

1. Bên A gửi `PEER_METADATA`: `{ userId, customUsername, publicKeyString, identitySignatureBase64 }`.
   - `identitySignatureBase64` = ký chuỗi thách thức `"${roomId}_${userId}"` bằng **private key RSASSA-PKCS1-v1_5** của A (khoá này được sinh 1 lần và lưu bền trong `localforage`, không đổi giữa các phiên).
2. Bên B nhận được, `parseCryptoKeyString` để lấy lại `CryptoKey` từ chuỗi base64, rồi `verifySignature(publicKey, signature, "${roomId}_${userId}")`.
3. Khớp → `PeerVerificationState.VERIFIED`; không khớp → `UNVERIFIED`. Không có bước người dùng tự so khớp fingerprint bằng mắt.

**Quan trọng**: cơ chế này **chứng minh** "người đang nói chuyện với bạn nắm giữ đúng private key ứng với public key đã công bố trong phiên trước" — chống mạo danh lặp lại danh tính giữa các lần join. Nó **không** chống được nghe lén nội dung (đã có DTLS lo) và **không** tự nó chống MITM ở lần gặp đầu tiên tuyệt đối (nếu kẻ tấn công chèn được vào ngay từ đầu và tự xưng danh tính mới, verify vẫn "khớp" vì nó tự ký bằng khoá của chính nó) — đây là hạn chế "trust on first use" cố hữu của mọi hệ không có PKI tập trung, kể cả Signal/PGP.

## D.6 Mã hoá nội dung — đính chính quan trọng

Chitchatter **không tự mã hoá nội dung tin nhắn ở tầng ứng dụng**. Bảo mật nội dung đến từ:
- **DTLS** của WebRTC data channel (bắt buộc theo chuẩn WebRTC, trình duyệt tự thương lượng khi thiết lập `RTCPeerConnection`).
- Với **file**: `secure-file-transfer` có mã hoá riêng (dùng khoá suy ra từ tên phòng) trước khi biến file thành torrent — vì file được lan truyền qua giao thức BitTorrent/WebTorrent công khai (không riêng tư như data channel), nên **bắt buộc phải mã hoá ở tầng ứng dụng** cho phần này.

→ Java không có WebRTC/DTLS có sẵn, nên chat-p2p-java **phải tự làm phần mà chitchatter được trình duyệt lo miễn phí** — đây là lý do chọn tự cài ECDH + AES-GCM cho toàn bộ kênh dữ liệu (không chỉ riêng file), rộng hơn phạm vi mã hoá thật của chitchatter.

## D.7 Nhắn tin & trạng thái gõ

- Gửi tin: tạo `UnsentMessage{id, authorId, text, timeSent}` → hiển thị optimistic ngay → gửi qua action `MESSAGE` → tự gắn `timeReceived` cho bản của mình.
- Nhận tin: gắn `timeReceived = now()`, phát âm thanh / notification desktop nếu tab không active (`services/Notification`, `services/Audio`), tắt cờ "đang gõ" của người gửi.
- Đa dòng: Shift+Enter trong `MessageForm` (không có gì đặc biệt ở tầng mạng).
- Markdown: chỉ là render phía nhận (`react-markdown`), dữ liệu gửi đi vẫn là text thô.
- Typing status: debounce 2s (`useDebounce`), gửi qua action `TYPING_STATUS_CHANGE` với payload `{ isTyping }`, có thể nhắm riêng 1 peer (direct message) hoặc broadcast (group).
- **Conversation backfilling**: chỉ áp dụng phòng **công khai**. Khi peer mới vào, nếu `messageLog` hiện có, gửi luôn qua action `MESSAGE_TRANSCRIPT` cho peer đó — nhưng **chỉ khi `messageLog.length === 0`** ở phía nhận (tránh ghi đè nếu đã có sẵn dữ liệu).

## D.8 Direct message

Không phải kết nối riêng — dùng chung `RTCPeerConnection` mesh sẵn có, chỉ khác:
- `namespace = ActionNamespace.DIRECT_MESSAGE` thay vì `GROUP`.
- Gửi kèm `{ target: targetPeerId }` để Trystero chỉ gửi tới 1 peer thay vì broadcast.
- `messageLog` được tách riêng theo từng `targetPeerId` (`ShellMessageLog.directMessageLog: Record<peerId, MessageLog>`), không lẫn với chat nhóm.

## D.9 Video / Audio call & Screen share

Cả 3 dùng chung cơ chế `PeerRoom.addStream(mediaStream, { metadata })`:
- **Webcam**: `getUserMedia({ video: {...} })`, gắn `metadata: { type: StreamType.WEBCAM }`.
- **Mic**: tương tự nhưng audio, quản lý qua `AudioChannelState` riêng (`useRoomAudio`).
- **Screen share**: `getDisplayMedia`, `metadata: { type: StreamType.SCREEN_SHARE }`.

Khi 1 stream được add, tất cả peer trong phòng nhận `room.onPeerStream(stream, peerId, metadata)` — phân biệt loại stream bằng `metadata.type` để hiển thị đúng chỗ (`peerVideoStreams` vs `peerScreenStreams`). Có action riêng (`AUDIO_CHANGE`/`VIDEO_CHANGE`/`SCREEN_SHARE`) chỉ để đồng bộ **trạng thái hiển thị** (icon bật/tắt trên peer list), không mang media — media đi qua track/stream thật của WebRTC, không qua kênh action.

**Đây là phần phụ thuộc nặng nhất vào hạ tầng có sẵn của trình duyệt** (codec H.264/VP8/Opus, `RTCPeerConnection` transceiver, `getUserMedia`/`getDisplayMedia`) — Java không có tương đương built-in, phải dùng thư viện ngoài hoặc phương án đơn giản hoá (Phần E, mục 8.5).

## D.10 Chia sẻ file

Khác hẳn cơ chế message ở trên — **không đi qua WebRTC DataChannel của Trystero** mà qua **WebTorrent** (giao thức BitTorrent chạy được trong trình duyệt qua WebRTC data channel *của WebTorrent*, độc lập với data channel của Trystero):

1. `fileTransfer.offer(files, roomId)` (gói `secure-file-transfer`) — mã hoá file, biến thành torrent, trả về `magnetURI`.
2. Gửi `magnetURI` cho các peer qua action `FILE_OFFER` (đây mới là thứ đi qua kênh Trystero — chỉ là con trỏ, không phải nội dung file).
3. Bên nhận dùng `magnetURI` để tải torrent qua WebTorrent client riêng, rồi giải mã (khoá suy ra từ tên phòng, tương tự cơ chế password ở mục D.3).
4. File ảnh/audio/video nhỏ có thể hiển thị **inline** trong khung chat (`isAllInlineMedia`), khác với file thường chỉ hiện nút tải.
5. Rời phòng / đổi file → gửi `FILE_OFFER` với `magnetURI = null` để thu hồi (`fileTransfer.rescind`).

→ Vì Java không có WebTorrent, chat-p2p-java sẽ cần tự thiết kế cơ chế truyền file (chia chunk qua chính kênh P2P đã có, mã hoá từng chunk bằng AES-GCM) — **đơn giản hơn** kiến trúc 2 tầng (Trystero + WebTorrent) của bản gốc, phù hợp vì không cần chia sẻ file kiểu "swarm" công khai.

## D.11 Cài đặt cá nhân & lưu trữ cục bộ

`localforage` (IndexedDB) chỉ lưu **đúng 1 key**: `userSettings` (`models/storage.ts`), gồm: `colorMode`, `userId`, `customUsername`, `publicKey`/`privateKey` (cặp khoá ký danh tính — sinh 1 lần, tồn tại lâu dài qua các phiên), `playSoundOnNewMessage`, `showNotificationOnNewMessage`, `showActiveTypingStatus`, `isEnhancedConnectivityEnabled`, `selectedSound`.

**Không có gì khác được lưu** — đặc biệt **tin nhắn và metadata phòng không bao giờ persist**, đúng tinh thần "ephemeral" (rời phòng/đóng tab là mất sạch lịch sử chat, chỉ cấu hình cá nhân còn lại).

## D.12 Chẩn đoán kết nối (Enhanced Connectivity)

`lib/ConnectionTest` kiểm tra 2 việc trước/trong khi vào phòng:
- **Có kết nối được tới tracker hay không** (`TrackerConnection: SEARCHING/SUCCESS/FAILURE`) — phát hiện sớm nếu mạng chặn WebSocket tới tracker.
- **Có TURN server khả dụng hay không** (`hasTURNServer`) — thử một `RTCPeerConnection` với `iceServers` cấu hình, xem có sinh được `relay` candidate không.

Kết quả hiển thị ở UI (`EnhancedConnectivityControl`) giúp người dùng tự chẩn đoán vì sao không kết nối được — **đây chính là ý tưởng của "đo tỉ lệ thiết lập kết nối P2P thành công"** trong kế hoạch đánh giá của đề cương; chitchatter làm ở phía client, còn chat-p2p-java sẽ đo ở tầng riêng (Phần F.5).

## D.13 Nhúng ứng dụng (SDK / iframe)

`models/sdk.ts` định nghĩa giao thức `postMessage` giữa trang cha và `<iframe>` nhúng chitchatter: trang cha gửi `CONFIG` (màu theme, tên phòng, user id/name...) qua `window.postMessage`, chitchatter lắng nghe và tự cấu hình theo — có kiểm tra `origin` khớp domain cha để tránh giả mạo. **Không liên quan tới core P2P/chat**, thuộc nhóm tính năng UI/tích hợp, không áp dụng cho app desktop.

## D.14 Bảng ánh xạ sang kiến trúc Java (tổng hợp nhanh — đối chiếu đầy đủ ở Phần E, mục 12)

| Thành phần chitchatter | Cơ chế thật | Tương đương ở chat-p2p-java |
|---|---|---|
| Trystero (`joinRoom`, tracker) | BitTorrent tracker làm signaling, tự động ICE | `signaling-server` (đã xây — Phần C.2) |
| `RTCPeerConnection` + DataChannel (DTLS) | WebRTC built-in trình duyệt | `P2pDataChannel` (ice4j + socket, Phần E mục 6) + `crypto` (ECDH/AES-GCM) |
| `PeerRoom.makeAction` | Đa kênh logic trên 1 data channel | `Envelope`/`EnvelopeCodec` (Phần E, mục 3) |
| `PEER_METADATA` + chữ ký RSASSA | Xác thực danh tính tự động | `IdentitySignatureService` bằng ECDSA (Phần E, mục 5) |
| `secure-file-transfer` + WebTorrent | Mã hoá + phân phối file qua BitTorrent | Chunk tự thiết kế qua `DataChannel` (Phần E, mục 8.4) |
| `getUserMedia`/`getDisplayMedia` + media track WebRTC | Video/audio call, screen share | Motion-JPEG + PCM thô qua kênh dữ liệu sẵn có (Phần E, mục 8.5) |
| `localforage` (IndexedDB) | Lưu cặp khoá + cài đặt | `Preferences` API hoặc file JSON cục bộ (Phần E, mục 8.6) |
| `lib/ConnectionTest` | Kiểm tra tracker/TURN khả dụng | Tầng đo hiệu năng riêng (Phần F.5) |

## D.15 Ghi chú áp dụng cho chat-p2p-java

Mục tiêu là làm **đủ 12 chức năng** của chitchatter (Phần B.2), **triển khai từng chức năng một** theo thứ tự phụ thuộc kỹ thuật hợp lý (Phần E, mục 9), và **xác thực peer theo kiểu tự động (chữ ký số)** giống chitchatter thay vì thủ công như đề cương gốc ban đầu.

---

# PHẦN E — THIẾT KẾ THI CÔNG CHI TIẾT: CHAT-P2P-JAVA

*Bản thiết kế thi công (implementation spec), viết để code thẳng theo. Mọi tên lớp, tên phương thức, định dạng dữ liệu nêu ở đây là thứ sẽ gõ ra trong IDE.*

## E.1 Nguyên tắc thiết kế xuyên suốt

1. **Không có DTLS như WebRTC** → mọi byte đi qua `DataChannel.send()` (trừ bản thân handshake khoá) đều phải được mã hoá AES-GCM ở tầng ứng dụng trước khi gửi. Đây là khác biệt cốt lõi so với chitchatter (Phần D, mục D.6).
2. **Một `DataChannel` = một kết nối 1-1 với một peer.** Phòng có N peer → N kết nối `DataChannel` song song (mesh đầy đủ, giống Trystero), quản lý tập trung bởi một lớp mới: `RoomSession` (thiết kế ở mục E.4).
3. **Không có `PeerRoom.makeAction` đa kênh của Trystero** → tự đóng gói bằng một **envelope chung** duy nhất trên mỗi `DataChannel`, phân loại bằng trường `type` (tương đương `PeerAction`). Thiết kế ở mục E.3.
4. **Signaling server chỉ relay, không đọc nội dung** — giữ nguyên nguyên tắc đã có, không đổi.
5. **Ephemeral**: chỉ có đúng 1 thứ được lưu bền trên đĩa — cặp khoá danh tính + cài đặt người dùng (mục E.8.6). Tin nhắn/danh sách phòng không bao giờ ghi ra đĩa.

## E.2 Sơ đồ module & luồng dữ liệu tổng thể

```
                         ┌─────────────────────┐
                         │   signaling-server   │  (Spring Boot + WebSocket)
                         │  RoomRegistry, relay  │  KHÔNG đọc nội dung
                         └──────────▲───────────┘
                        JSON/WebSocket (SignalMessage)
                                    │
        ┌───────────────────────────┴───────────────────────────┐
        │                                                        │
┌───────▼────────┐                                       ┌───────▼────────┐
│  client-javafx   │◄─────────── DataChannel (N) ───────►│  client-javafx   │
│  (Peer A)         │   ice4j P2P, moi byte da AES-GCM     │  (Peer B)         │
│                   │                                       │                   │
│  RoomSession      │                                       │  RoomSession      │
│   ├─ PeerConnection[peerId] → DataChannel + SecretKey      │   ├─ ...          │
│   ├─ EnvelopeCodec (encrypt/decrypt + (de)serialize)        │                   │
│   └─ ActionRouter (dispatch theo EnvelopeType)              │                   │
└───────────────────┘                                       └───────────────────┘
```

**2 tầng giao thức tách biệt hoàn toàn**:
- **Tầng signaling** (đã có, không đổi nhiều): JSON qua WebSocket, model `SignalMessage`/`SignalType` trong `common`. Chỉ dùng để JOIN/LEAVE phòng và relay OFFER/ANSWER/ICE_CANDIDATE lúc thiết lập kết nối.
- **Tầng dữ liệu P2P** (thiết kế mới ở tài liệu này): sau khi `P2pDataChannel` giữa 2 peer đã mở, mọi thứ (chat, gõ phím, file, media...) đi qua **envelope nhị phân riêng**, không liên quan gì tới `SignalMessage` nữa.

## E.3 Giao thức tầng dữ liệu P2P — `Envelope`

### E.3.1 `EnvelopeType` (tương đương `PeerAction` của chitchatter, xem Phần D, mục D.4)

Thêm vào `common/model` (file mới `EnvelopeType.java`):

```java
public enum EnvelopeType {
    MESSAGE,               // tin nhắn văn bản (nhóm hoặc DM)
    MESSAGE_TRANSCRIPT,    // backfill lịch sử chat cho peer mới
    TYPING_STATUS_CHANGE,  // trạng thái đang gõ
    PEER_IDENTITY,         // public key + chữ ký danh tính (xác thực tự động)
    FILE_OFFER,            // rao 1 file đang chia sẻ (hoặc thu hồi)
    FILE_CHUNK,            // 1 chunk dữ liệu file đã mã hoá
    AUDIO_CHANGE,          // bật/tắt mic
    VIDEO_CHANGE,          // bật/tắt webcam
    SCREEN_SHARE_CHANGE,   // bật/tắt chia sẻ màn hình
    MEDIA_FRAME            // 1 khung hình video/screen-share (xem mục E.8.5)
}
```

### E.3.2 Cấu trúc `Envelope` (đóng gói mọi thứ gửi qua `DataChannel`)

```java
// common/src/main/java/com/datn/chatp2p/common/protocol/Envelope.java
public final class Envelope {
    private EnvelopeType type;
    private String namespace;   // "g" (group) hoặc "dm" (direct message) - xem E.3.3
    private String targetPeerId; // null nếu broadcast trong action gửi; 
                                  // ở envelope THẬT thì luôn null vì DataChannel đã là 1-1
    private long timestamp;
    private byte[] payload;     // JSON đã serialize của object cụ thể theo `type`, TRƯỚC KHI mã hoá
    // getters/setters...
}
```

- `Envelope` không tự chứa payload đã mã hoá — **lớp `EnvelopeCodec` (mục E.3.4) lo việc mã hoá toàn bộ envelope đã serialize thành 1 khối `byte[]`** rồi mới gọi `DataChannel.send()`. Bên nhận giải mã trước, parse `Envelope` sau.
- Vì mỗi `DataChannel` đã là kết nối 1-1 với đúng 1 peer, **không cần `target` như Trystero** (Trystero broadcast trên 1 kết nối chung nên cần lọc theo target; ở đây gửi tới ai thì gọi đúng `DataChannel` của peer đó — đơn giản hơn bản gốc).
- `namespace` giữ lại để phân biệt tin nhắn **nhóm** hiển thị ở khung chat chung, và tin nhắn **DM** hiển thị ở tab riêng với 1 peer — dù về mặt transport chúng đi qua cùng 1 `DataChannel` (vì đằng nào cũng là kết nối 1-1), namespace chỉ ảnh hưởng UI hiển thị ở đâu, không ảnh hưởng routing mạng.

### E.3.3 Định dạng payload cụ thể theo từng `EnvelopeType`

| EnvelopeType | Payload (Java record, serialize bằng Jackson) |
|---|---|
| `MESSAGE` | `record MessagePayload(String id, String authorId, String text, long timeSent)` |
| `MESSAGE_TRANSCRIPT` | `record TranscriptPayload(List<MessagePayload> messages)` |
| `TYPING_STATUS_CHANGE` | `record TypingPayload(boolean isTyping)` |
| `PEER_IDENTITY` | `record IdentityPayload(String userId, String customUsername, String publicKeyBase64, String signatureBase64)` |
| `FILE_OFFER` | `record FileOfferPayload(String fileId, String fileName, long fileSize, int totalChunks, boolean isInlineMedia)` — `fileId == null` nghĩa là thu hồi offer trước đó |
| `FILE_CHUNK` | `record FileChunkPayload(String fileId, int chunkIndex, byte[] ciphertextChunk)` — bản thân chunk **đã được mã hoá 1 lớp nữa bên trong** payload (xem mục E.8.4) trước khi cả `Envelope` bị mã hoá lần nữa bởi `EnvelopeCodec` — chấp nhận double-encrypt để đơn giản hoá code, chi phí CPU không đáng kể |
| `AUDIO_CHANGE` / `VIDEO_CHANGE` / `SCREEN_SHARE_CHANGE` | `record MediaStatePayload(boolean isActive)` |
| `MEDIA_FRAME` | `record MediaFramePayload(String streamId, MediaStreamType streamType, byte[] jpegFrame)` (xem mục E.8.5) |

### E.3.4 `EnvelopeCodec` — lớp trung tâm nối `crypto` với `p2p-core`

```java
// p2p-core/src/main/java/com/datn/chatp2p/p2p/protocol/EnvelopeCodec.java
public final class EnvelopeCodec {
    private final ObjectMapper objectMapper; // dùng chung 1 instance, cấu hình 1 lần
    private final SecretKey sessionKey;      // khoá AES phiên với đúng 1 peer (từ ECDH)

    public EnvelopeCodec(SecretKey sessionKey) { ... }

    /** payload -> JSON -> Envelope -> JSON -> AES-GCM -> byte[] sẵn sàng cho DataChannel.send() */
    public <T> byte[] encode(EnvelopeType type, String namespace, T payload) { ... }

    /** byte[] nhận từ DataChannel.onReceive -> AES-GCM decrypt -> JSON -> Envelope */
    public Envelope decode(byte[] raw) { ... }

    /** Envelope.payload (byte[] JSON đã decode nhưng CHƯA parse) -> T cụ thể */
    public <T> T parsePayload(Envelope envelope, Class<T> payloadType) { ... }
}
```

Đây chính là điểm mà `crypto` (đã có `AesGcmCipher`) và `common` (model + Jackson) gặp nhau ở tầng `p2p-core`, thay thế cho việc `RoomController` tự gọi `AesGcmCipher.encrypt/decrypt` trực tiếp như bản demo hiện tại (`RoomController.onSendMessage`/`onEncryptedMessageReceived` sẽ được đơn giản hoá đi rất nhiều khi có lớp này — xem mục E.10).

## E.4 `RoomSession` — quản lý nhiều peer trong 1 phòng (thay thế `PeerRoom` của Trystero)

```java
// p2p-core/src/main/java/com/datn/chatp2p/p2p/RoomSession.java
public final class RoomSession {
    private final String roomId;
    private final String selfPeerId;
    private final KeyPair identityKeyPair;      // ECDSA, ký danh tính - xem mục E.5
    private final SignalingClient signalingClient;
    private final Map<String, PeerConnection> peers = new ConcurrentHashMap<>(); // key = peerId

    // Đăng ký listener theo kiểu tương tự PeerRoom.onPeerJoin/onPeerLeave của chitchatter,
    // nhưng đơn giản hơn (không cần Map<HookType,handler> vì không có nhiều "consumer"
    // độc lập tranh nhau đăng ký - client-javafx chỉ có đúng 1 RoomController lắng nghe).
    public void onPeerJoined(Consumer<PeerConnection> handler);
    public void onPeerLeft(Consumer<String /* peerId */> handler);
    public void onEnvelope(EnvelopeType type, BiConsumer<String /* peerId */, Envelope> handler);

    public void join(); // gửi SignalType.JOIN, bắt đầu vòng đời thiết lập kết nối với peer có sẵn
    public void leave(); // đóng toàn bộ PeerConnection, gửi SignalType.LEAVE

    public void broadcast(EnvelopeType type, Object payload);              // gửi cho TẤT CẢ peer (group)
    public void sendTo(String peerId, EnvelopeType type, Object payload);  // gửi 1 peer (DM hoặc điều khiển)
    public Collection<String> getPeerIds();
}
```

```java
// p2p-core/src/main/java/com/datn/chatp2p/p2p/PeerConnection.java
public final class PeerConnection {
    private final String peerId;
    private final DataChannel dataChannel;   // P2pDataChannel thật, hoặc LoopbackDataChannel khi demo
    private final EnvelopeCodec codec;       // khoá AES riêng cho peer này (mỗi cặp peer có session key riêng)
    private PeerVerificationState verificationState;
    private String customUsername;

    public void send(EnvelopeType type, Object payload) {
        dataChannel.send(codec.encode(type, ..., payload));
    }
    // dataChannel.onReceive(...) được RoomSession gắn 1 lần lúc tạo PeerConnection,
    // decode Envelope rồi dispatch theo type tới handler đã đăng ký ở RoomSession.onEnvelope
}
```

**Vòng đời thiết lập kết nối với 1 peer mới** (khi `SignalingClient` báo `PEER_JOINED` hoặc nhận `PEER_LIST` lúc mới vào phòng):

1. `RoomSession` tạo `KeyPair` ECDH tạm thời cho phiên này (KHÔNG dùng chung với `identityKeyPair` ở mục E.5 — ECDH để trao khoá AES, ECDSA để ký danh tính, 2 mục đích khác nhau, 2 cặp khoá khác nhau).
2. Gửi public key ECDH của mình qua kênh signaling (payload trong `SignalMessage.OFFER`, hoặc gộp vào bước ICE — xem mục E.6) tới peer mới.
3. Khi `P2pDataChannel` giữa 2 bên mở xong (ICE thành công), 2 bên trao public key ECDH qua chính `DataChannel` vừa mở (gói tin đầu tiên, **không mã hoá** vì đây chính là bước tạo ra khoá mã hoá — xem mục E.5.2 để biết cách tránh MITM ở bước này).
4. `KeyExchangeService.deriveSharedSecret(...)` → `SecretKey` → tạo `EnvelopeCodec` cho `PeerConnection` này.
5. Gửi `Envelope(PEER_IDENTITY, ...)` đã mã hoá bằng khoá vừa có — tương đương `PEER_METADATA` của chitchatter.
6. Nếu phòng công khai và đã có lịch sử chat: gửi `Envelope(MESSAGE_TRANSCRIPT, ...)`.

## E.5 Xác thực danh tính tự động (thay thế fingerprint thủ công hiện tại)

### E.5.1 Đổi thuật toán trong `crypto`

Chitchatter dùng RSASSA-PKCS1-v1_5 (RSA-2048) để **ký**, tách biệt với ECDH để **trao khoá**. Ở Java, dùng **ECDSA (cùng họ đường cong secp256r1 đã dùng cho ECDH)** cho gọn — không cần thêm thuật toán RSA riêng:

```java
// crypto/src/main/java/com/datn/chatp2p/crypto/IdentitySignatureService.java
public final class IdentitySignatureService {
    // Sinh 1 lần, lưu bền (mục E.8.6) - đây là "danh tính" lâu dài của người dùng,
    // KHÁC với KeyPair ECDH tạm thời sinh mỗi phiên kết nối ở mục E.4 bước 1.
    public static KeyPair generateIdentityKeyPair(); // EC, secp256r1, dùng cho "SHA256withECDSA"

    public static byte[] sign(PrivateKey identityPrivateKey, String message); // message = "${roomId}_${userId}"
    public static boolean verify(PublicKey identityPublicKey, byte[] signature, String message);
}
```

### E.5.2 Thông điệp thách thức & luồng xác thực

Giữ nguyên công thức của chitchatter: `challenge = roomId + "_" + userId` (Phần D, mục D.5). Khi B nhận `PEER_IDENTITY` từ A:

```java
boolean verified = IdentitySignatureService.verify(
    decodedPublicKeyOfA, signatureFromA, roomId + "_" + userIdOfA);

peer.setVerificationState(verified ? PeerVerificationState.VERIFIED : PeerVerificationState.UNVERIFIED);
```

Không cần dialog "So sánh fingerprint" nữa — `PeerListCell` (đã có) chỉ cần **bỏ nút "Xác thực"**, hiển thị badge theo `verificationState` do `RoomSession` tự cập nhật khi `PEER_IDENTITY` tới. **Giữ nguyên `Fingerprint.of(...)`** trong `crypto` — vẫn hữu ích để hiển thị public key dạng rút gọn cho người dùng tò mò xem (giống `components/PublicKey` của chitchatter), chỉ không dùng nó làm cơ chế xác thực chính nữa.

### E.5.3 Lưu ý bảo mật cần ghi vào báo cáo

Đúng như Phần D, mục D.5: cơ chế này chống **mạo danh lặp lại** (ai đó tự nhận là "Khôi" ở lần join thứ 2 mà không có đúng private key sẽ bị phát hiện), **không** chống tuyệt đối MITM ở lần gặp đầu tiên. Đây là hạn chế đã biết, cần nêu rõ trong báo cáo (mục "Đánh giá bảo mật") thay vì giấu đi. Tổng hợp đầy đủ các rủi ro ở Phần J.

## E.6 Kết nối P2P thật — hoàn thiện `P2pDataChannel` bằng ice4j

*(Việc lớn nhất còn thiếu, Phần D mục D.14 — chi tiết hoá thành các bước code được ở đây)*

### E.6.1 Mở rộng giao thức signaling để mang thêm ICE credentials

`SignalMessage.payload` (đã có, kiểu `String`) sẽ mang **JSON** của 1 trong các cấu trúc sau tuỳ `SignalType`:

```java
// common/model, dùng làm nội dung của SignalMessage.payload (Jackson serialize -> String)
record IceOfferPayload(String ufrag, String password, List<String> candidates) {}
record IceAnswerPayload(String ufrag, String password, List<String> candidates) {}
record IceCandidatePayload(String candidate) {} // 1 candidate mới phát hiện sau khi đã gửi offer/answer (trickle ICE)
```

### E.6.2 Luồng thiết lập kết nối bằng ice4j (thư viện `org.jitsi:ice4j`)

```
Peer A (chủ động, ví dụ peer đến sau thấy peer có sẵn qua PEER_LIST)     Peer B
  |                                                                        |
  | 1. tạo Agent (ice4j), addComponent, startCandidateHarvest             |
  | 2. localUfrag/localPassword + list candidate cục bộ (host/srflx/relay)|
  | 3. gửi SignalType.OFFER, payload = IceOfferPayload(...)                |
  |------------------------> qua signaling-server ------------------------>|
  |                                                        4. nhận offer, tạo Agent riêng, set remote ufrag/password/candidates
  |                                                        5. gửi SignalType.ANSWER, payload = IceAnswerPayload(...)
  |<------------------------ qua signaling-server -------------------------|
  | 6. set remote ufrag/password/candidates từ answer                     |
  | 7. agent.startConnectivityEstablishment() cả 2 bên                    |
  |    -> ice4j tự chạy connectivity checks, chọn candidate pair thắng     |
  | 8. IceProcessingListener báo COMPLETED -> lấy CandidatePair đã chọn    |
  | 9. Mở kênh dữ liệu THẬT trên candidate pair đó (mục E.6.3)             |
```

### E.6.3 Kênh dữ liệu thật sau khi ICE xong

ice4j chỉ lo tới bước có được 1 cặp (localSocket, remoteAddress) đã "thông" (connectivity check thành công) — **không tự cho DataChannel như WebRTC**. Cần tự mở kênh dữ liệu trên đó:

```java
// p2p-core/src/main/java/com/datn/chatp2p/p2p/channel/P2pDataChannel.java (thay stub hiện tại)
public class P2pDataChannel implements DataChannel {
    private final DatagramSocket socket; // lấy từ IceMediaStream.getComponent().getSocket() sau khi ICE xong
    private final InetSocketAddress remoteAddress; // CandidatePair đã chọn
    private volatile Consumer<byte[]> receiveHandler;
    private final ExecutorService receiveLoop = Executors.newSingleThreadExecutor();

    public P2pDataChannel(DatagramSocket iceSocket, InetSocketAddress remote) {
        this.socket = iceSocket;
        this.remoteAddress = remote;
        receiveLoop.submit(this::receiveLoop);
    }

    @Override
    public void send(byte[] data) {
        // Framing đơn giản: 4-byte length prefix + data, vì UDP giữ ranh giới gói tin
        // nên thực ra length-prefix KHÔNG bắt buộc cho UDP đơn thuần, nhưng vẫn thêm
        // để dự phòng chuyển sang TCP/DTLS-over-UDP sau này mà không đổi framing.
        byte[] framed = frame(data);
        socket.send(new DatagramPacket(framed, framed.length, remoteAddress));
    }

    private void receiveLoop() {
        byte[] buf = new byte[65536];
        while (!Thread.currentThread().isInterrupted()) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet); // blocking
            byte[] data = unframe(packet.getData(), packet.getLength());
            if (receiveHandler != null) receiveHandler.accept(data);
        }
    }
    // onReceive(), close() ...
}
```

**Lưu ý quan trọng về bảo mật tầng transport**: vì không dùng DTLS như WebRTC, `P2pDataChannel` **truyền UDP thô** — đây chính là lý do mục E.1 nguyên tắc #1 (mọi payload phải tự mã hoá AES-GCM ở tầng `Envelope` trước khi gọi `send()`) là **bắt buộc**, không phải tuỳ chọn. Ghi rõ điều này trong báo cáo ở phần so sánh với WebRTC.

### E.6.4 `WebSocketSignalingClient` — hoàn thiện thay vì stub

```java
// p2p-core/.../signaling/WebSocketSignalingClient.java
public class WebSocketSignalingClient implements SignalingClient {
    private WebSocket webSocket; // java.net.http.HttpClient.newWebSocketBuilder()...buildAsync(...)
    private final ObjectMapper objectMapper;
    private final Map<SignalType, List<Consumer<SignalMessage>>> handlers = new ConcurrentHashMap<>();

    @Override
    public void connect(String serverUri, String roomId, String peerId, String userName) {
        this.webSocket = HttpClient.newHttpClient()
            .newWebSocketBuilder()
            .buildAsync(URI.create(serverUri + "/ws"), new Listener() { ... onText(...) parse + dispatch ... })
            .join();
        send(SignalMessage.join(roomId, peerId, userName));
    }
    // send(SignalMessage) dùng chung cho onOffer/onAnswer/onIceCandidate/sendOffer/sendAnswer/sendIceCandidate
}
```

## E.7 Nhiều peer trong phòng (mesh)

Không cần thiết kế thêm — hệ quả tự nhiên của `RoomSession` (mục E.4) quản lý `Map<peerId, PeerConnection>`. Khi `SignalingClient.onPeerJoined` báo có peer mới, `RoomSession` tự chạy lại toàn bộ luồng mục E.6.2 với peer đó, **độc lập** với các `PeerConnection` đang có — đúng kiểu mesh đầy đủ N×(N-1)/2 kết nối như Trystero.

`RoomController` (client-javafx) chỉ cần lắng nghe `RoomSession.onPeerJoined/onPeerLeft` để cập nhật `ObservableList<Peer>` — logic UI (PeerListCell, MessageListCell) **giữ nguyên không đổi** so với bản demo hiện tại.

## E.8 Chi tiết từng chức năng còn lại

### E.8.1 Nhắn tin nhóm + đa dòng + Markdown

- Gửi: `roomSession.broadcast(EnvelopeType.MESSAGE, new MessagePayload(...))`.
- Nhận: `RoomSession.onEnvelope(MESSAGE, (peerId, env) -> ...)`, parse `MessagePayload`, thêm vào `ObservableList<ChatMessage>` qua `Platform.runLater` (y hệt cơ chế demo hiện tại, chỉ đổi nguồn từ `LoopbackDataChannel` sang `RoomSession`).
- Đa dòng: `TextArea` thay cho `TextField` trong `room.fxml`, bắt Shift+Enter để xuống dòng, Enter thường để gửi (`TextArea` không có `onAction`, phải tự bắt `KeyEvent` bằng `setOnKeyPressed`).
- Markdown: dùng thư viện `com.vladsch.flexmark:flexmark-all` (Java Markdown → HTML), hiển thị bằng `javafx.scene.web.WebView` thay vì `Label` cho nội dung tin nhắn (hoặc `Label` với `-fx-font-family` monospace cho code block nếu muốn tránh phụ thuộc `WebView`/JCEF nặng — cân nhắc theo thời gian còn lại).

### E.8.2 Direct message (DM)

- UI: click vào 1 peer trong `PeerListCell` → mở 1 tab/panel chat riêng (bổ sung `TabPane` trong `room.fxml`, 1 tab "Nhóm" + N tab theo từng peer đang chat riêng).
- Gửi: `roomSession.sendTo(peerId, EnvelopeType.MESSAGE, payload)` với `namespace = "dm"`.
- Lưu trữ: `RoomController` giữ `Map<String peerId, ObservableList<ChatMessage>> directMessageLogs` tách biệt khỏi `ObservableList<ChatMessage> groupMessages` — tương đương `ShellMessageLog.directMessageLog` của chitchatter.

### E.8.3 Trạng thái đang gõ

- `messageField`/`TextArea` gắn `textProperty().addListener(...)`, debounce 2 giây bằng `javafx.animation.PauseTransition` (có sẵn trong JavaFX, gọn hơn tự viết debounce).
- Gửi `Envelope(TYPING_STATUS_CHANGE, new TypingPayload(isTyping))` tới peer đang chat cùng (DM) hoặc broadcast (nhóm).
- Nhận: cập nhật 1 `Label` nhỏ trong `PeerListCell` hoặc phía trên khung nhập ("Khôi đang nhập...") — tương đương `TypingStatusBar.tsx`.

### E.8.4 Truyền file mã hoá qua chunk

Vì không có WebTorrent, thiết kế tự làm, đơn giản hơn nhưng đủ dùng:

```java
// client-javafx/.../filetransfer/FileSender.java (hoặc đưa xuống p2p-core nếu muốn tái dùng)
public final class FileSender {
    private static final int CHUNK_SIZE = 16 * 1024; // 16KB/chunk, an toàn dưới MTU thường gặp

    public void offer(File file, RoomSession session, Set<String> targetPeerIds) {
        String fileId = UUID.randomUUID().toString();
        int totalChunks = (int) Math.ceil(file.length() / (double) CHUNK_SIZE);
        var offer = new FileOfferPayload(fileId, file.getName(), file.length(), totalChunks, isInlineMedia(file));
        for (String peerId : targetPeerIds) session.sendTo(peerId, EnvelopeType.FILE_OFFER, offer);
        sendChunks(file, fileId, session, targetPeerIds);
    }

    private void sendChunks(File file, String fileId, RoomSession session, Set<String> targetPeerIds) {
        try (var in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buf = new byte[CHUNK_SIZE];
            int index = 0, read;
            while ((read = in.read(buf)) != -1) {
                byte[] chunk = Arrays.copyOf(buf, read);
                for (String peerId : targetPeerIds) {
                    session.sendTo(peerId, EnvelopeType.FILE_CHUNK,
                        new FileChunkPayload(fileId, index, chunk));
                }
                index++;
            }
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
}
```

```java
// FileReceiver.java — ráp file từ các chunk nhận được
public final class FileReceiver {
    private final Map<String, ChunkBuffer> incoming = new ConcurrentHashMap<>(); // key = fileId

    public void onFileOffer(FileOfferPayload offer) {
        incoming.put(offer.fileId(), new ChunkBuffer(offer, new byte[offer.totalChunks()][]));
        // báo UI: hiện progress bar 0%, tên file, kích thước
    }

    public void onFileChunk(FileChunkPayload chunk) {
        ChunkBuffer buffer = incoming.get(chunk.fileId());
        if (buffer == null) return; // chưa có offer hoặc đã huỷ
        buffer.chunks()[chunk.chunkIndex()] = chunk.ciphertextChunk();
        buffer.receivedCount().incrementAndGet();
        if (buffer.isComplete()) assembleAndSave(buffer);
    }

    private void assembleAndSave(ChunkBuffer buffer) {
        // ghi ra thư mục tải về của người dùng, nối các chunk theo đúng thứ tự index
    }
}
```

- File ảnh/audio nhỏ hiển thị inline: sau khi `assembleAndSave`, nếu `isInlineMedia`, tạo `ChatMessage`-like entry đặc biệt để `MessageListCell` render `ImageView`/`MediaView` thay vì `Label`.
- UI: nút 📎 cạnh ô nhập, `FileChooser` của JavaFX; hiển thị `ProgressBar` cho cả gửi lẫn nhận (tương đương `RoomFileUploadControls.tsx`).

### E.8.5 Video call / Audio call / Screen share — phương án khả thi trong Java thuần

Java không có WebRTC/codec built-in. Đề xuất phương án **đơn giản hoá có chủ đích** (ghi rõ lý do trong báo cáo — đánh đổi hiệu năng lấy khả năng triển khai trong thời gian đồ án):

| Nhu cầu | Bản gốc (WebRTC) | Đề xuất cho chat-p2p-java |
|---|---|---|
| Video call | Codec H.264/VP8 qua `RTCPeerConnection` transceiver | **Motion-JPEG tự chế**: chụp webcam định kỳ (~10-15 fps) bằng thư viện [`webcam-capture`](https://github.com/sarxos/webcam-capture) (`com.github.sarxos:webcam-capture`) → mỗi khung hình nén JPEG (`javax.imageio.ImageIO`, quality ~0.5) → gửi qua `Envelope(MEDIA_FRAME, ...)` như 1 tin nhắn nhị phân bình thường (đã mã hoá AES-GCM sẵn theo cơ chế chung) |
| Audio call | Codec Opus qua `RTCPeerConnection` | Capture PCM bằng `javax.sound.sampled.TargetDataLine` (built-in JDK, không cần thư viện ngoài), chia chunk ~20ms, gửi thẳng PCM thô (hoặc nén nhẹ bằng μ-law 8-bit nếu cần giảm băng thông) qua `Envelope(MEDIA_FRAME, streamType=AUDIO, ...)`, phát lại bằng `SourceDataLine` |
| Screen share | `getDisplayMedia` | `java.awt.Robot.createScreenCapture(...)` (built-in JDK) định kỳ (~5-8 fps đủ cho demo/thuyết trình) → JPEG → cùng cơ chế `MEDIA_FRAME` như video |

- **Vì sao chọn cách này thay vì JavaCV/FFmpeg**: JavaCV kéo theo native binding nặng (OpenCV/FFmpeg build cho từng OS), rủi ro build fail cao trên máy chấm đồ án, không đáng đánh đổi khi mục tiêu là chứng minh khái niệm ("proof of concept") chứ không phải chất lượng video sản xuất. Motion-JPEG qua chính kênh dữ liệu đã có sẵn (`Envelope`/`DataChannel`) **tái dùng toàn bộ hạ tầng mã hoá + P2P đã xây**, không cần mở thêm kênh media riêng như WebRTC — đơn giản hơn nhiều so với bản gốc.
- UI phía nhận: `ImageView` cập nhật theo từng `MEDIA_FRAME` nhận được (giống hiển thị 1 GIF thủ công), đặt trong `PeerVideoDisplay` (tương đương `PeerVideo.tsx`).
- `AUDIO_CHANGE`/`VIDEO_CHANGE`/`SCREEN_SHARE_CHANGE` chỉ đồng bộ trạng thái icon bật/tắt trên `PeerListCell`, không mang dữ liệu media (giống bản gốc, xem Phần D, mục D.9).
- **Ghi chú khối lượng công việc**: đây là nhóm chức năng nặng nhất trong 12 chức năng — nên làm **sau cùng**, sau khi đã có kênh P2P + mã hoá + chat/file ổn định (xem thứ tự ở mục E.9).

### E.8.6 Cài đặt cá nhân, theme sáng/tối, lưu trữ cục bộ

- Thay `localforage`/IndexedDB bằng **`java.util.prefs.Preferences`** (built-in JDK, lưu vào registry trên Windows / file trên Linux/macOS — không cần thư viện ngoài) *hoặc* đơn giản hơn: 1 file JSON tại `System.getProperty("user.home") + "/.chat-p2p-java/settings.json"` (dễ debug, dễ demo, dễ giải thích trong báo cáo hơn Preferences API "ẩn" trong registry).
- `UserSettingsService` (module `client-javafx` hoặc `common`): load lúc khởi động app (trước khi hiện `HomeView`), save khi đổi cài đặt hoặc lúc thoát app.
- Nội dung lưu, đối chiếu `UserSettings` của chitchatter (Phần D, mục D.11): `colorMode`, `userId` (UUID cố định), `customUsername`, `identityKeyPair` (Base64 của `PublicKey`/`PrivateKey` — encode bằng `getEncoded()`, decode bằng `X509EncodedKeySpec`/`PKCS8EncodedKeySpec`), `playSoundOnNewMessage`, `showNotificationOnNewMessage`, `showActiveTypingStatus`.
- Theme sáng/tối: 2 file CSS (`app-light.css` hiện có đổi tên, thêm `app-dark.css`), `ChatApplication`/`RoomController` chọn `scene.getStylesheets()` theo `UserSettings.colorMode`.

### E.8.7 Nhúng ứng dụng (SDK/iframe)

**Không áp dụng cho desktop app** — đây là khái niệm web-only (iframe). Bỏ khỏi phạm vi thi công (không phải "chưa làm" mà là "không có ý nghĩa với app desktop"), khác với 11 chức năng còn lại.

## E.9 Thứ tự triển khai đề xuất (theo phụ thuộc kỹ thuật)

```
1. Hoàn thiện WebSocketSignalingClient (mục E.6.4)         ─┐
2. Hoàn thiện P2pDataChannel bằng ice4j (mục E.6.2-6.3)     ─┤ Nền tảng bắt buộc trước,
3. EnvelopeCodec + RoomSession + PeerConnection (mục E.3-4) ─┤ mọi chức năng khác phụ thuộc vào đây
4. Xác thực danh tính tự động (mục E.5)                     ─┘
   -> Mốc: 2 máy thật qua Internet chat text được với nhau, có xác thực tự động
5. Nhiều peer / mesh (mục E.7) - hệ quả tự nhiên của bước 3, test với ≥3 máy
6. DM (mục E.8.2), Typing status (mục E.8.3) - UI nhỏ, rủi ro thấp
7. Markdown + đa dòng (mục E.8.1) - UI thuần, không đụng mạng
8. Truyền file (mục E.8.4) - phức tạp vừa, không phụ thuộc UI ở bước 6-7
9. Cài đặt cá nhân + theme (mục E.8.6) - độc lập, làm lúc nào cũng được, nên chen vào lúc rảnh
10. Video call -> Audio call -> Screen share (mục E.8.5) - nặng nhất, làm SAU CÙNG,
    dừng ở bước nào cũng được nếu hết thời gian
```

## E.10 Việc phải sửa trong code hiện có khi bắt tay vào bước 1-4

- `RoomController.start(...)`: thay đoạn tạo `LoopbackDataChannel.Pair` + `DemoPeerSimulator` bằng tạo `RoomSession` thật (`new RoomSession(roomId, selfPeerId, identityKeyPair, webSocketSignalingClient)`), gọi `roomSession.join()`.
- `RoomController.onSendMessage()`: đổi `myChannel.send(AesGcmCipher.encrypt(...))` thành `roomSession.broadcast(EnvelopeType.MESSAGE, new MessagePayload(...))` — bỏ hẳn việc gọi `AesGcmCipher` trực tiếp ở tầng UI (chuyển xuống `EnvelopeCodec`, đúng nguyên tắc tách lớp).
- `PeerListCell`: bỏ nút "Xác thực" thủ công + `RoomController.onVerifyRequested(...)`, thay bằng lắng nghe `verificationState` cập nhật tự động từ `RoomSession`.
- `P2pDataChannel`, `WebSocketSignalingClient`: xoá `UnsupportedOperationException`, cài đặt thật theo mục E.6.
- `pom.xml` của `p2p-core`: thêm dependency `org.jitsi:ice4j` (kiểm tra version mới nhất trên Maven Central trước khi thêm).
- `pom.xml` của `client-javafx`: thêm `com.github.sarxos:webcam-capture` (khi tới bước 10), `com.fasterxml.jackson.core:jackson-databind` (để `EnvelopeCodec`/`Envelope` serialize — hiện `client-javafx` chưa có Jackson).

## E.11 Những gì KHÔNG đổi so với code hiện tại

- `crypto/KeyExchangeService`, `AesGcmCipher`, `Fingerprint` — dùng y nguyên bên trong `EnvelopeCodec`.
- `common/model/ChatMessage`, `Peer`, `PeerVerificationState` — dùng y nguyên cho UI.
- `signaling-server` toàn bộ — chỉ mở rộng `SignalMessage.payload` mang thêm dữ liệu ICE (mục E.6.1), không đổi kiến trúc `RoomRegistry`/`SignalingWebSocketHandler`.
- `MessageListCell`, `home.fxml`, `RoomNameGenerator`, CSS — giữ nguyên, chỉ `room.fxml` cần bổ sung `TabPane` (DM), `TextArea` (đa dòng), nút đính kèm file, khu vực hiển thị video.

## E.12 Đối chiếu toàn diện — bản gốc chitchatter vs giải pháp thay thế Java

*Với mỗi khối kỹ thuật: **(a)** cơ chế thật của chitchatter, **(b)** vì sao không mang thẳng sang Java được, **(c)** giải pháp thay thế cụ thể.*

### E.12.1 Mã hoá kênh truyền

- **(a) Bản gốc**: không tự làm gì — `RTCPeerConnection` bắt buộc DTLS theo chuẩn WebRTC, trình duyệt tự thương lượng khoá lúc thiết lập kết nối. `services/Encryption` của chitchatter **không** tham gia vào việc này.
- **(b) Vì sao không port thẳng**: Java không có `RTCPeerConnection`; kênh UDP tự mở qua ice4j (mục E.6.3) hoàn toàn trần trụi, ai chặn được gói tin là đọc được nội dung nếu không tự mã hoá.
- **(c) Giải pháp Java**: bắt buộc mã hoá **mọi** `Envelope` bằng AES-GCM trước khi `DataChannel.send()` (mục E.1 nguyên tắc #1, mục E.3.4 `EnvelopeCodec`). Khoá phiên lấy từ ECDH giữa đúng 2 peer, sinh 1 lần lúc mở `PeerConnection` (mục E.4, bước 1-4). Đây là phần **thêm hẳn ra** so với bản gốc, không phải rút gọn.

### E.12.2 Tìm nhau (peer discovery) & signaling

- **(a) Bản gốc**: `trystero.joinRoom(roomId)` — thư viện băm `appId+roomId+password` thành 1 khoá phòng, tự quảng cáo lên **BitTorrent tracker công khai** (hoặc chiến lược khác nếu cấu hình), các peer cùng khoá phòng tự tìm thấy nhau qua đó, tự trao đổi SDP/ICE candidate luôn trong quá trình này — code ứng dụng không thấy bước này.
- **(b) Vì sao không port thẳng**: không có tracker BitTorrent nào "biết" về chat-p2p-java; cũng không có API browser nào lo việc trao đổi SDP hộ.
- **(c) Giải pháp Java**: `signaling-server` tự viết (đã xong, không đổi kiến trúc) đóng đúng vai trò tracker — nhưng **tường minh hơn**: code ứng dụng (`WebSocketSignalingClient`, mục E.6.4) trực tiếp gửi/nhận `SignalMessage` chứa OFFER/ANSWER/ICE_CANDIDATE, không có gì "ẩn" trong thư viện như Trystero.

### E.12.3 Phòng công khai / phòng riêng tư

- **(a) Bản gốc** (Phần D, mục D.3): phòng công khai dùng `password = roomId` (ai biết tên phòng là vào được). Phòng riêng tư: `secret = base64(SHA-256("${roomId}_${password}"))`, `secret` này mới là "khoá phòng" thật đưa cho Trystero — nếu không đúng password gốc thì không tính ra đúng `secret`, không đoán được khoá phòng thật, không vào được swarm dù có biết tên phòng hiển thị.
- **(b) Vì sao không port thẳng**: khái niệm này **không phụ thuộc WebRTC/trình duyệt** — hoàn toàn có thể tái tạo y hệt trong Java, chỉ cần đổi chỗ áp dụng (Trystero swarm key → `roomId` gửi cho `signaling-server`).
- **(c) Giải pháp Java** (thiết kế mới, bổ sung cho mục E.6): giữ 2 khái niệm tách biệt —
  - `displayRoomId`: tên phòng người dùng gõ/thấy trên UI (`home.fxml`).
  - `effectiveRoomId`: chuỗi thật sự gửi trong `SignalMessage.roomId` khi JOIN — với phòng công khai thì `effectiveRoomId = displayRoomId`; với phòng riêng tư thì:
    ```java
    String effectiveRoomId = Base64.getUrlEncoder().withoutPadding().encodeToString(
        MessageDigest.getInstance("SHA-256")
            .digest((displayRoomId + "_" + password).getBytes(StandardCharsets.UTF_8)));
    ```
    (thêm hàm tiện ích này vào `crypto` hoặc `common`, ví dụ `RoomSecret.derive(roomId, password)`).
  - `signaling-server` **không cần biết** phòng nào là "riêng tư" — nó chỉ thấy 1 chuỗi `roomId` bất kỳ, đúng tinh thần "server không đọc/không quan tâm nội dung" đã có. Toàn bộ tính riêng tư nằm ở việc `effectiveRoomId` không đoán được nếu không có password gốc.
  - HomeView cần thêm 1 `PasswordField` ẩn/hiện tuỳ người dùng chọn "Phòng riêng tư" (tương đương `PasswordPrompt.tsx` + `PrivateRoom.tsx`).

### E.12.4 Kết nối P2P & xuyên NAT

- **(a) Bản gốc**: `RTCPeerConnection` tự làm ICE gathering, connectivity check, chọn candidate pair — toàn bộ nằm trong engine WebRTC của trình duyệt, lộ ra ngoài đúng 2 việc: SDP offer/answer và ICE candidate (đi qua Trystero).
- **(b) Vì sao không port thẳng**: JVM không có engine WebRTC.
- **(c) Giải pháp Java**: thư viện `ice4j` (dự án Jitsi) làm đúng phần ICE gathering/connectivity check (mục E.6.2); phần "mở kênh dữ liệu trên candidate pair đã chọn" mà WebRTC tự cho miễn phí thì Java phải **tự viết** (`P2pDataChannel`, mục E.6.3) — đây là phần không có sẵn thư viện nào thay thế trọn gói.

### E.12.5 Đa kênh logic trên 1 kết nối (multiplexing)

- **(a) Bản gốc**: `PeerRoom.makeAction(actionName)` của Trystero — mỗi `actionName` là 1 luồng gửi/nhận độc lập trên cùng 1 `RTCDataChannel`, thư viện lo việc gắn nhãn + tách gói.
- **(b) Vì sao không port thẳng**: đây là tính năng riêng của thư viện `trystero`, không tồn tại ngoài hệ sinh thái WebRTC/JS.
- **(c) Giải pháp Java**: tự thiết kế `Envelope { type, namespace, payload }` + `EnvelopeCodec` (mục E.3) — mỗi `DataChannel.onReceive` nhận **1 loại byte[] duy nhất**, tự parse `type` để biết đây là tin nhắn/gõ phím/chunk file gì, rồi dispatch — bản chất là tự viết lại đúng cơ chế mà `makeAction` làm, chỉ khác là tường minh trong code thay vì ẩn trong thư viện.

### E.12.6 Xác thực danh tính peer

- **(a) Bản gốc** (Phần D, mục D.5): tự động, ký bằng RSASSA-PKCS1-v1_5 (RSA-2048) trên chuỗi `"${roomId}_${userId}"`, verify bằng public key nhận qua `PEER_METADATA`.
- **(b) Vì sao không port y nguyên**: RSA-2048 vẫn dùng được trong JCA (`KeyPairGenerator.getInstance("RSA")`), **có thể port y hệt nếu muốn** — đây là 1 trong số ít chỗ có thể copy gần như nguyên xi thuật toán. Lý do đổi sang ECDSA (mục E.5.1) là lựa chọn thiết kế (gọn hơn, tái dùng cùng đường cong `secp256r1` đã có sẵn cho ECDH), **không phải bắt buộc kỹ thuật**.
- **(c) Giải pháp Java**: `IdentitySignatureService` dùng `SHA256withECDSA` thay vì `SHA256withRSA`, giữ nguyên 100% công thức chuỗi thách thức và luồng gửi/verify qua `PEER_IDENTITY` (mục E.5.2). Nếu muốn bám sát tuyệt đối bản gốc, chỉ cần đổi `KeyPairGenerator.getInstance("EC", ...)` thành `getInstance("RSA")` với `keySize=2048` và đổi `Signature.getInstance("SHA256withECDSA")` thành `"SHA256withRSA"` — phần còn lại của luồng không đổi gì.

### E.12.7 Truyền file

- **(a) Bản gốc** (Phần D, mục D.10): 2 tầng — `secure-file-transfer` mã hoá file thành torrent (khoá suy từ tên phòng), phân phối qua **WebTorrent** (giao thức BitTorrent chạy trong trình duyệt), chỉ có `magnetURI` (con trỏ) đi qua kênh Trystero.
- **(b) Vì sao không port thẳng**: không có WebTorrent client thuần Java trưởng thành/dễ tích hợp tương đương; và bản chất WebTorrent tồn tại để **phân phối tới nhiều người xem chưa chắc đã có kết nối trực tiếp với nhau** (swarm) — trong khi chat-p2p-java gửi file trực tiếp 1-1 qua kênh đã có sẵn, không cần mô hình swarm.
- **(c) Giải pháp Java**: bỏ hẳn tầng "torrent hoá", gửi file trực tiếp qua chunk trên `DataChannel` đã mã hoá sẵn (mục E.8.4, `FileSender`/`FileReceiver`) — **kiến trúc đơn giản hơn bản gốc**, đánh đổi là không tận dụng được cơ chế phân phối song song kiểu swarm (không cần thiết với quy mô phòng nhỏ của đồ án).

### E.12.8 Video call, audio call, screen share

- **(a) Bản gốc** (Phần D, mục D.9): `getUserMedia`/`getDisplayMedia` lấy `MediaStream`, gắn thẳng vào `RTCPeerConnection` qua `addStream` — mã hoá + nén (H.264/VP8/Opus) + truyền đều do WebRTC engine của trình duyệt lo, tách biệt hoàn toàn khỏi data channel.
- **(b) Vì sao không port thẳng**: JVM không có encoder/decoder codec video/audio chuẩn built-in, không có khái niệm "media track" tách biệt khỏi data channel như WebRTC.
- **(c) Giải pháp Java** (mục E.8.5): tận dụng lại chính hạ tầng `Envelope`/`DataChannel` đã xây cho chat/file (không có kênh media riêng như bản gốc) — capture bằng `webcam-capture`/`Robot`/`TargetDataLine` (đều là API JVM hoặc thư viện Java thuần, không cần native binding nặng), nén JPEG cho hình, PCM thô cho tiếng, gửi như `Envelope(MEDIA_FRAME, ...)` bình thường. **Đánh đổi tường minh**: chất lượng/độ trễ kém hơn hẳn codec chuyên dụng, nhưng khả thi trong thời gian đồ án và không phụ thuộc build native.

### E.12.9 Lưu trữ cục bộ

- **(a) Bản gốc**: `localforage` (wrapper IndexedDB), chỉ lưu 1 object `userSettings` (Phần D, mục D.11) — bao gồm cả cặp khoá danh tính.
- **(b) Vì sao không port thẳng**: IndexedDB là API trình duyệt, không tồn tại trong JVM.
- **(c) Giải pháp Java** (mục E.8.6): `Preferences` API hoặc file JSON cục bộ — vai trò và nội dung lưu **giống hệt** bản gốc (cùng lưu đúng 1 "gói cài đặt", cùng nguyên tắc không lưu tin nhắn), chỉ khác cơ chế lưu trữ vật lý.

### E.12.10 Nhúng ứng dụng (SDK/iframe)

- **(a) Bản gốc**: `postMessage` giữa trang cha và `<iframe>` nhúng chitchatter (Phần D, mục D.13).
- **(b) Vì sao không port**: `<iframe>` là khái niệm trình duyệt, ứng dụng desktop JavaFX không có "trang cha" nào để nhúng vào.
- **(c) Giải pháp Java**: **không có giải pháp thay thế** — loại hẳn khỏi phạm vi thi công, khác về bản chất với các mục E.12.1-E.12.9 (những mục đó đều có giải pháp thay thế, mục này thì không áp dụng được).

### E.12.11 Tổng kết mức độ "giống bản gốc"

| Mức độ | Các khối |
|---|---|
| **Copy gần như nguyên xi** (chỉ khác cú pháp Java) | Công thức chữ ký danh tính (E.12.6), công thức phòng riêng tư (E.12.3) |
| **Giữ nguyên ý tưởng, tự viết lại cơ chế** | Signaling/peer discovery (E.12.2), đa kênh logic (E.12.5) |
| **Đơn giản hoá có chủ đích** (bớt phức tạp hơn bản gốc) | Truyền file (E.12.7) |
| **Hạ cấp có chủ đích** (chấp nhận kém hơn để khả thi) | Video/audio/screen share (E.12.8) |
| **Thêm mới hoàn toàn** (bản gốc không cần vì có DTLS) | Mã hoá kênh truyền (E.12.1), tự viết tầng transport qua ice4j (E.12.4) |
| **Loại bỏ hẳn** (không áp dụng cho desktop) | Nhúng iframe/SDK (E.12.10) |

---

# PHẦN F — YÊU CẦU PHI CHỨC NĂNG & CHIẾN LƯỢC KIỂM THỬ

## F.1 Hiệu năng

| Chỉ tiêu | Mục tiêu đo đạc | Cách đo |
|---|---|---|
| Thời gian thiết lập kết nối P2P (cùng LAN) | < 2 giây | Log timestamp lúc gửi JOIN đến lúc `P2pDataChannel` báo sẵn sàng |
| Thời gian thiết lập kết nối P2P (khác NAT, cần TURN) | < 8 giây | Tương tự, phân biệt case phải relay qua TURN |
| Tỉ lệ thiết lập kết nối P2P thành công | Ghi nhận % kết nối trực tiếp thành công / phải relay / thất bại hoàn toàn, qua ≥3 kịch bản mạng khác nhau | Bảng thống kê thủ công khi test (Phần F.5) |
| Độ trễ tin nhắn văn bản (đã kết nối) | Không đo được tuyệt đối vì không có server trung gian mốc thời gian chung — đo tương đối bằng round-trip: gửi + echo, đo thời gian client-side | Test thủ công với đồng hồ hệ thống đồng bộ NTP |
| Thông lượng truyền file | Ghi nhận tốc độ theo chunk 16KB, so sánh cùng LAN vs qua Internet | Đo bằng `System.currentTimeMillis()` trước/sau `assembleAndSave` |

## F.2 Bảo mật

- **Mã hoá nội dung**: AES-256-GCM cho mọi `Envelope` (mục E.12.1) — không có nội dung nào rời máy gửi ở dạng plaintext qua mạng, trừ chính gói tin trao đổi public key ECDH ban đầu (bản chất public key không cần giữ bí mật).
- **Trao khoá**: ECDH đường cong secp256r1 (NIST P-256), khoá phiên theo từng cặp peer, không tái sử dụng giữa các phòng/phiên khác nhau.
- **Xác thực danh tính**: ECDSA cùng đường cong, chống mạo danh lặp lại (mục E.5.3) — **giới hạn đã biết**: không chống MITM tuyệt đối ở lần gặp đầu (ghi vào Phần J).
- **Signaling server**: không bao giờ đọc/giải mã nội dung — chỉ relay `SignalMessage` (đã kiểm chứng bằng code review: `SignalingWebSocketHandler` chỉ đọc `type`/`roomId`/`toPeerId`, không đụng vào `payload`).
- **Kiểm thử bảo mật cần làm** (xem thêm F.5): bắt gói tin bằng Wireshark giữa 2 client thật, xác nhận nội dung UDP payload là ciphertext ngẫu nhiên (không đọc được), không phải JSON/text thô.

## F.3 Khả năng mở rộng & giới hạn quy mô

- Kiến trúc **mesh đầy đủ** (N peer → N×(N-1)/2 kết nối, mỗi peer duy trì N-1 kết nối song song) — giống bản gốc chitchatter, có cùng giới hạn: băng thông/CPU của máy client tăng tuyến tính theo số peer còn lại trong phòng.
- **Khuyến nghị cho đồ án**: test và trình diễn ổn định ở quy mô 2-6 peer/phòng; không đặt mục tiêu hỗ trợ phòng hàng chục người (ngoài phạm vi thiết kế mesh).
- Video/audio/screen share (Motion-JPEG, mục E.8.5) tốn băng thông hơn hẳn codec nén thật — nên giới hạn số peer đồng thời bật video trong 1 phòng khi demo (khuyến nghị ≤ 3) để tránh giật/lag do quá tải CPU nén JPEG liên tục.

## F.4 Độ tin cậy & khả năng phục hồi

- **Ephemeral là hành vi đúng theo thiết kế** — signaling server restart hoặc client rời phòng làm mất toàn bộ trạng thái phòng đó, không phải lỗi cần khắc phục.
- `signaling-server` cần xử lý đúng khi WebSocket đóng đột ngột (mất mạng, tắt app không gọi LEAVE) — đã có: `afterConnectionClosed` gọi `removeAndNotify` (xem `SignalingWebSocketHandler`, đã code, đã test).
- `P2pDataChannel` (khi hoàn thiện) cần tự phát hiện mất kết nối (timeout không nhận được gói tin nào trong X giây) và báo lên `RoomSession`/`RoomController` để cập nhật UI (peer chuyển sang "mất kết nối") thay vì treo im lặng — **chưa thiết kế chi tiết, cần bổ sung khi cài đặt mục E.6.3**.

## F.5 Chiến lược kiểm thử

### F.5.1 Unit test (đã có, chạy tự động bằng `mvn test`)

| Module | Test class | Số test | Nội dung kiểm chứng |
|---|---|---|---|
| `crypto` | `KeyExchangeServiceTest` | 2 | ECDH 2 bên ra cùng shared secret; roundtrip encode/decode public key |
| `crypto` | `AesGcmCipherTest` | 3 | Encrypt/decrypt đúng dữ liệu gốc; ciphertext ≠ plaintext; sai khoá thì thất bại |
| `p2p-core` | `LoopbackDataChannelTest` | 1 | 2 đầu kênh gửi/nhận đúng dữ liệu 2 chiều |
| `signaling-server` | `SignalingWebSocketHandlerTest` | 1 | Server thật (Spring context + Tomcat), WebSocket thật: JOIN → PEER_LIST đúng, peer thứ 2 vào → peer đầu nhận PEER_JOINED |
| `client-javafx` | `RoomNameGeneratorTest` | 1 | Định dạng tên phòng sinh ra đúng quy tắc |

**Tổng: 8 test, tất cả pass** (đã verify thật bằng `mvn clean test`, không chỉ dựa vào biên dịch — xem lịch sử làm việc).

### F.5.2 Integration test cần bổ sung khi hoàn thiện Phần E

- Test 2 instance `RoomSession` thật (không qua `LoopbackDataChannel`) trên **cùng máy, khác cổng UDP** — kiểm chứng toàn bộ luồng ICE + Envelope + xác thực danh tính hoạt động đúng mà không cần 2 máy vật lý.
- Test mesh 3 `RoomSession` cùng vào 1 phòng — kiểm chứng mọi người thấy đủ nhau, tin nhắn broadcast tới đúng tất cả.

### F.5.3 Kiểm thử thủ công qua nhiều môi trường mạng (đúng mục tiêu "đo tỉ lệ kết nối P2P thành công" của đề cương)

| Kịch bản | Mục tiêu kiểm tra |
|---|---|
| 2 máy cùng LAN | Kết nối trực tiếp, không cần TURN, độ trễ thấp nhất |
| 2 máy khác mạng, NAT loại Cone (nhà mạng phổ thông) | ICE nên tìm được đường trực tiếp qua STUN (server-reflexive candidate) |
| 2 máy sau NAT symmetric hoặc firewall doanh nghiệp chặt | Kỳ vọng phải relay qua TURN — xác nhận cơ chế dự phòng hoạt động |
| 1 máy qua mạng di động (4G/5G) | Kịch bản NAT khó đoán nhất, dễ lộ ra giới hạn thật của ice4j/TURN |

Ghi nhận kết quả thành bảng số liệu (tỉ lệ % theo từng kịch bản) để đưa vào báo cáo chương "Kiểm thử và đánh giá".

### F.5.4 Kiểm thử bảo mật thủ công

- Dùng Wireshark bắt gói UDP giữa 2 client đang chat, xác nhận payload không phải plaintext.
- Thử kịch bản giả lập kẻ tấn công: chèn 1 tiến trình thứ 3 tự xưng danh tính của peer đã có (không có đúng private key) — xác nhận `verificationState` chuyển thành `UNVERIFIED`, không phải `VERIFIED`.
- Xác nhận signaling server logs (mục H.2) không chứa bất kỳ nội dung tin nhắn/payload đã mã hoá nào — chỉ chứa `roomId`/`peerId`/loại sự kiện.

---

# PHẦN G — CẤU HÌNH, BUILD & VẬN HÀNH

## G.1 Yêu cầu môi trường

- JDK 17+ (build/test đã verify thật với JDK 17.0.12 và biên dịch target `--release 17`).
- Maven 3.9+ (đã verify thật với Apache Maven 3.9.9).
- Không cần cài Node.js/npm — dự án Java thuần, không liên quan tới toolchain của chitchatter.

## G.2 Cấu trúc build & lệnh (xem thêm README.md ở gốc repo)

```bash
mvn -q -DskipTests package        # build toàn bộ 5 module
mvn -pl crypto test                # chạy 5 unit test mã hoá
mvn -pl signaling-server test      # chạy integration test WebSocket thật
mvn -pl signaling-server -DskipTests package
java -jar signaling-server/target/signaling-server.jar   # chạy server thật, cổng 8080
mvn -pl client-javafx javafx:run   # chạy demo UI (loopback + mã hoá thật)
```

## G.3 Cấu hình `signaling-server`

`signaling-server/src/main/resources/application.yml`:

```yaml
server:
  port: 8080
spring:
  application:
    name: chat-p2p-signaling-server
logging:
  level:
    com.datn.chatp2p: INFO
```

- Đổi cổng: sửa `server.port`, hoặc override lúc chạy bằng `--server.port=xxxx` hoặc biến môi trường `SERVER_PORT` (Spring Boot tự map).
- WebSocket endpoint cố định tại `/ws` (`WebSocketConfig`), cho phép mọi origin (`setAllowedOriginPatterns("*")`) — chấp nhận được vì server không xử lý dữ liệu nhạy cảm, chỉ relay.

## G.4 Cấu hình `client-javafx` (khi nối mạng thật, thay thế demo)

Hiện tại (bản demo) không cần cấu hình gì — dùng `LoopbackDataChannel` nội bộ. Khi hoàn thiện Phần E, cần thêm màn hình/tham số cấu hình:
- Địa chỉ `signaling-server` (host:port hoặc URL đầy đủ `ws://.../ws`) — nên cho phép người dùng nhập ở màn hình cài đặt hoặc đọc từ file cấu hình cục bộ (mục E.8.6), không hardcode.
- Danh sách STUN/TURN server cho ice4j (mục E.6.2) — cùng cơ chế cấu hình như trên.

## G.5 Vấn đề đã phát hiện thực tế & cách khắc phục

**Đường dẫn dự án chứa dấu tiếng Việt (`...\Máy tính\...`) làm `mvn spring-boot:run` báo lỗi trên Windows.**

- Hiện tượng: `mvn -pl signaling-server spring-boot:run` báo `Error: Could not find or load main class ...` dù `mvn package` build thành công và class file tồn tại đúng chỗ trên đĩa. Đã thử cả git-bash lẫn PowerShell, cùng lỗi — không phải do shell.
- Nguyên nhân: `spring-boot-maven-plugin`'s goal `run` ghi classpath ra file tạm (`@argfile`) để né giới hạn độ dài dòng lệnh Windows; khi đường dẫn chứa ký tự có dấu, việc ghi/đọc file này bị lệch encoding, làm JVM không tìm thấy đúng thư mục class.
- **Cách khắc phục đã verify hoạt động**: không dùng `spring-boot:run`. Thay bằng đóng gói fat jar (`mvn package`, cần khai báo tường minh goal `repackage` trong `pom.xml` vì module không kế thừa `spring-boot-starter-parent` — xem `signaling-server/pom.xml`) rồi chạy trực tiếp `java -jar signaling-server/target/signaling-server.jar`. Đã test thật: Tomcat khởi động đúng, WebSocket endpoint `/ws` phản hồi HTTP 400 khi GET thường (đúng hành vi kỳ vọng cho endpoint chỉ nhận WebSocket upgrade).

## G.6 Yêu cầu hạ tầng khi triển khai qua Internet thật (khác demo cục bộ)

- **`signaling-server`** cần chạy ở địa chỉ mọi client tiếp cận được (VPS, dịch vụ cloud có cổng public, hoặc port-forward nếu tự host tại nhà).
- **TURN server dự phòng**: bắt buộc có ít nhất 1 TURN server khả dụng để đạt tỉ lệ kết nối thành công cao (giống `rtcConfig.iceServers` của chitchatter, mục D.2). Lựa chọn:
  - Tự host [`coturn`](https://github.com/coturn/coturn) (mã nguồn mở, phổ biến nhất) — kiểm soát được, không giới hạn băng thông/thời gian, phù hợp để đo hiệu năng thật cho báo cáo.
  - Dùng TURN free công khai (như chitchatter đang dùng) — nhanh để demo nhưng thường giới hạn băng thông/hết hạn, **không nên dựa vào cho số liệu đánh giá chính thức trong báo cáo**.
- Mở cổng UDP cần thiết cho STUN/TURN và luồng dữ liệu ICE trên firewall của máy chạy `signaling-server`/TURN (không cần mở gì phía client thông thường — ICE tự dò từ phía sau NAT).

---

# PHẦN H — XỬ LÝ LỖI & LOGGING

## H.1 Nguyên tắc xử lý lỗi

1. **Không bao giờ để lỗi mạng làm crash toàn bộ ứng dụng JavaFX** — mọi thao tác mạng (kết nối signaling, ICE, gửi/nhận `Envelope`) phải được bọc try/catch, lỗi hiển thị cho người dùng qua `Alert` hoặc banner trong `room.fxml`, không phải stack trace văng ra console.
2. **Lỗi mã hoá/giải mã** (`AesGcmCipher.decrypt` ném `IllegalStateException` khi sai khoá hoặc dữ liệu bị thay đổi) coi là **tín hiệu bất thường cần cảnh báo**, không chỉ log — vì đây có thể là dấu hiệu tấn công hoặc lỗi đồng bộ khoá, khác với lỗi mạng thông thường.
3. **Lỗi ở tầng signaling-server** (thiếu `roomId`/`toPeerId`, không tìm thấy peer đích) chỉ log `WARN` và bỏ qua bản tin đó — không được làm crash cả kết nối WebSocket của các peer khác (đã đúng theo code hiện tại trong `SignalingWebSocketHandler`).

## H.2 Logging

- **`signaling-server`**: dùng SLF4J/Logback có sẵn qua Spring Boot. Mức `INFO` khi peer join/leave phòng (đã code: `log.info("Peer {} ({}) da vao phong {}", ...)`), mức `WARN` khi relay thất bại. **Không log nội dung `payload`** (dù đã là ciphertext, tránh log rác không cần thiết và giữ nguyên tắc "server không đụng nội dung" cả ở log).
- **`client-javafx`**: hiện chưa có logging framework — khi hoàn thiện Phần E nên thêm SLF4J + Logback đơn giản (ghi ra file `~/.chat-p2p-java/logs/app.log`), mức `INFO` cho vòng đời kết nối (join/leave phòng, peer connect/disconnect), `DEBUG` cho chi tiết ICE candidate/ candidate pair đã chọn (hữu ích khi debug tỉ lệ kết nối ở Phần F.5.3), **tuyệt đối không log nội dung tin nhắn plaintext** dù chỉ ở máy cục bộ — giữ đúng tinh thần ephemeral/không lưu trữ.

## H.3 Các kịch bản lỗi cụ thể cần xử lý khi hoàn thiện Phần E

| Kịch bản | Xử lý đề xuất |
|---|---|
| Mất kết nối WebSocket tới `signaling-server` giữa chừng | `WebSocketSignalingClient` tự thử reconnect (backoff tăng dần), báo UI "Mất kết nối máy chủ, đang thử lại..." |
| ICE connectivity establishment thất bại (không tìm được candidate pair nào, kể cả qua TURN) | Sau timeout hợp lý (vd. 15s), báo UI rõ ràng "Không thể kết nối trực tiếp với [tên peer]", không lặp vô hạn |
| `P2pDataChannel` mất gói tin giữa chừng (UDP không đảm bảo) | Bản đầu chấp nhận mất gói (tin nhắn thất lạc hiếm khi xảy ra trên mạng ổn định); nếu còn thời gian, cân nhắc thêm ACK + retry đơn giản cho riêng `FILE_CHUNK` (file cần toàn vẹn tuyệt đối hơn tin nhắn) |
| Người dùng gửi file khi đối tác vừa rời phòng | `FileSender` cần kiểm tra `targetPeerIds` còn hợp lệ trước mỗi lần gửi chunk, dừng và dọn dẹp nếu peer đã rời |
| `AesGcmCipher.decrypt` ném lỗi khi nhận `Envelope` | Bắt riêng, log `WARN`/cảnh báo bảo mật (H.1 mục 2), bỏ qua gói tin đó, không đóng cả kết nối |

---

# PHẦN J — RỦI RO KỸ THUẬT & GIỚI HẠN ĐÃ BIẾT

*Tổng hợp toàn bộ rủi ro đã nêu rải rác ở các phần trên vào một bảng duy nhất — nên đưa thẳng vào chương "Kết luận và hướng phát triển" hoặc "Đánh giá" của báo cáo, thay vì che giấu.*

| # | Rủi ro / giới hạn | Mức độ | Biện pháp giảm thiểu / ghi chú |
|---|---|---|---|
| 1 | Không chống MITM tuyệt đối ở lần gặp đầu tiên (xác thực chữ ký chỉ chống mạo danh lặp lại) | Trung bình — hạn chế cố hữu của mọi hệ không có PKI tập trung | Nêu rõ trong báo cáo (mục E.5.3); có thể bổ sung xác thực fingerprint thủ công như một lớp tuỳ chọn nếu còn thời gian |
| 2 | Đường dẫn dự án chứa dấu tiếng Việt gây lỗi `spring-boot:run` trên Windows | Thấp — đã có cách khắc phục | Dùng `java -jar` thay vì `spring-boot:run` (Phần G.5) |
| 3 | Video/audio/screen share dùng Motion-JPEG/PCM thô thay vì codec chuẩn | Trung bình — chất lượng/độ trễ kém hơn WebRTC thật | Đánh đổi có chủ đích để tránh phụ thuộc native binding (Phần E.8.5); nêu rõ trong báo cáo là giới hạn đã biết, không phải thiếu sót |
| 4 | ICE qua NAT symmetric có thể thất bại ngay cả khi có TURN nếu TURN cấu hình sai/quá tải | Trung bình | Test kỹ theo Phần F.5.3, tự host coturn thay vì phụ thuộc TURN free công khai khi đo số liệu chính thức (Phần G.6) |
| 5 | `signaling-server` là điểm chịu lỗi duy nhất tạm thời (không phải nội dung, nhưng sập thì không ai vào phòng mới được) | Thấp với quy mô đồ án | Ephemeral by design — chấp nhận được; nếu cần độ sẵn sàng cao hơn, cân nhắc chạy nhiều instance sau `RoomRegistry` dùng bộ nhớ chia sẻ (ngoài phạm vi đồ án) |
| 6 | Kiến trúc mesh N² giới hạn quy mô phòng | Thấp với quy mô đồ án | Giới hạn khuyến nghị 2-6 peer/phòng khi demo/đánh giá (Phần F.3) |
| 7 | UDP không đảm bảo thứ tự/không mất gói — `P2pDataChannel` bản đầu chưa có retry | Trung bình cho truyền file, thấp cho chat text | Cân nhắc thêm ACK/retry cho `FILE_CHUNK` nếu còn thời gian (Phần H.3) |
| 8 | Double-encrypt cho `FILE_CHUNK` (mã hoá chunk rồi mã hoá cả Envelope) tốn CPU thừa | Rất thấp | Chấp nhận được, ưu tiên đơn giản hoá code hơn tối ưu vi mô (đã ghi chú ở mục E.3.3) |

---

# PHẦN K — PHỤ LỤC

## K.1 Bảng thuật ngữ

| Thuật ngữ | Giải thích ngắn |
|---|---|
| **P2P (Peer-to-Peer)** | Mô hình mạng ngang hàng — các máy trao đổi trực tiếp, không qua máy chủ trung tâm lưu nội dung |
| **NAT (Network Address Translation)** | Cơ chế router "che" địa chỉ IP nội bộ sau 1 địa chỉ public — nguyên nhân chính khiến 2 máy không tự kết nối trực tiếp được nếu không có kỹ thuật xuyên NAT |
| **STUN (Session Traversal Utilities for NAT)** | Giao thức giúp 1 máy biết được địa chỉ/cổng public của mình sau NAT (RFC 8489) |
| **TURN (Traversal Using Relays around NAT)** | Máy chủ trung gian relay dữ liệu khi 2 máy không thể kết nối trực tiếp dù đã dùng STUN (RFC 8656) |
| **ICE (Interactive Connectivity Establishment)** | Thuật toán tổng hợp STUN + TURN + so khớp candidate để tìm đường kết nối tốt nhất giữa 2 peer (RFC 8445) |
| **SDP (Session Description Protocol)** | Định dạng mô tả thông số phiên kết nối (codec, địa chỉ...) trao đổi giữa 2 bên trước khi kết nối — trong WebRTC gọi là "offer"/"answer" |
| **DTLS (Datagram TLS)** | TLS chạy trên UDP — WebRTC dùng để mã hoá data channel tự động |
| **ECDH (Elliptic Curve Diffie-Hellman)** | Thuật toán trao đổi khoá bí mật chung qua kênh công khai, dựa trên đường cong elliptic |
| **ECDSA (Elliptic Curve Digital Signature Algorithm)** | Thuật toán ký số dựa trên đường cong elliptic — dùng để xác thực danh tính (mục E.5) |
| **AES-GCM** | Chế độ mã hoá đối xứng AES kèm xác thực toàn vẹn dữ liệu (authenticated encryption) |
| **Mesh network** | Kiến trúc mọi peer kết nối trực tiếp với mọi peer khác trong cùng nhóm (không qua trung tâm) |
| **Ephemeral** | Tính chất "không lưu trữ lâu dài" — dữ liệu biến mất khi phiên kết thúc |
| **Signaling** | Giai đoạn trao đổi thông tin để 2 peer "tìm thấy nhau" và thiết lập kết nối, trước khi có kênh dữ liệu trực tiếp |
| **Fat jar** | File `.jar` đóng gói cả ứng dụng lẫn toàn bộ thư viện phụ thuộc, chạy được bằng `java -jar` mà không cần classpath ngoài |

## K.2 Tài liệu tham khảo

1. RFC 8445, *Interactive Connectivity Establishment (ICE)*, IETF, 2018.
2. RFC 8489, *Session Traversal Utilities for NAT (STUN)*, IETF, 2020.
3. RFC 8656, *Traversal Using Relays around NAT (TURN)*, IETF, 2020.
4. RFC 5869, *HMAC-based Extract-and-Expand Key Derivation Function (HKDF)* — tham khảo cho hướng nâng cấp KDF (Phần E, mục 5.1 crypto gốc).
5. Jitsi, *ice4j* — <https://github.com/jitsi/ice4j>.
6. NIST Special Publication 800-38D, *Recommendation for Block Cipher Modes of Operation: Galois/Counter Mode (GCM)*.
7. Oracle, *Java Cryptography Architecture (JCA) Reference Guide*.
8. Oracle, *JavaFX Documentation*.
9. jeremyckahn, *chitchatter* (mã nguồn tham chiếu) — <https://github.com/jeremyckahn/chitchatter>.
10. dmotz, *Trystero* — <https://github.com/dmotz/trystero>.
11. jeremyckahn, *secure-file-transfer* — <https://github.com/jeremyckahn/secure-file-transfer>.
12. sarxos, *webcam-capture* — <https://github.com/sarxos/webcam-capture>.

## K.3 Lịch sử thay đổi tài liệu

| Ngày | Thay đổi |
|---|---|
| 2026-08-21 | Tạo `Tai-lieu-ky-thuat-Chitchatter.md` (phân tích bản gốc) và `Thiet-ke-ky-thuat-chat-p2p-java.md` (thiết kế thi công) riêng biệt. |
| 2026-08-21 | Gộp 2 tài liệu thành `Tai-lieu-ky-thuat.md` (Phần I / Phần II). |
| 2026-08-21 | Viết lại toàn diện thành tài liệu kỹ thuật đầy đủ (Phần A-K): bổ sung tổng quan hệ thống, kiến trúc module hiện có trong repo, yêu cầu phi chức năng, chiến lược kiểm thử, cấu hình/vận hành, xử lý lỗi/logging, rủi ro kỹ thuật, phụ lục thuật ngữ — không còn chỉ giới hạn ở thiết kế chức năng. |
