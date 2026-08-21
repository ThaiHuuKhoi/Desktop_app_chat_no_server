Tài liệu kỹ thuật — phân tích chitchatter

# TÀI LIỆU KỸ THUẬT: KIẾN TRÚC VÀ CHỨC NĂNG CỦA CHITCHATTER

*Tài liệu này phân tích mã nguồn thật của `chitchatter-develop` (không suy đoán từ README) để làm nền tảng thiết kế chi tiết cho `chat-p2p-java`. Mục tiêu: hiểu đúng cơ chế của từng chức năng trước khi quyết định sẽ port/tương đương hoá thế nào sang Java. Đọc cùng với [De-cuong-Chat-P2P-Java.md](De-cuong-Chat-P2P-Java.md) và [Phan-cong-cong-viec.md](Phan-cong-cong-viec.md).*

---

## 1. Tổng quan kiến trúc

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

## 2. Stack công nghệ thật

| Thành phần | Công nghệ | Ghi chú |
|---|---|---|
| UI framework | React + TypeScript, Vite | SPA, routing bằng `react-router-dom` |
| P2P / signaling | [`trystero`](https://github.com/dmotz/trystero) (chiến lược mặc định: BitTorrent tracker) | Bọc `RTCPeerConnection`, tự động ICE, expose API `joinRoom` |
| Truyền file | [`secure-file-transfer`](https://github.com/jeremyckahn/secure-file-transfer) (nền WebTorrent) | File mã hoá thành torrent, chia sẻ qua `magnetURI` |
| Mã hoá/ký danh tính | Web Crypto API (`window.crypto.subtle`), thuật toán `RSASSA-PKCS1-v1_5` + SHA-256 | Chỉ dùng để **ký/xác thực danh tính**, không mã hoá nội dung tin nhắn (xem mục 6) |
| Lưu trữ cục bộ | `localforage` (wrapper IndexedDB) | Chỉ lưu **cài đặt người dùng** (kể cả cặp khoá), **không lưu tin nhắn** |
| Markdown | `react-markdown` (+ syntax highlight) | Render nội dung tin nhắn |
| Deploy | GitHub Pages (tĩnh) | Không có backend runtime bắt buộc |

## 3. Phòng chat (Room)

- **Room ID**: chuỗi bất kỳ do người dùng đặt hoặc UUID sinh ngẫu nhiên (`pages/Home`). Route: `/public/:roomId` hoặc `/private/:roomId` (`config/routes.ts`).
- **Phòng công khai** (`PublicRoom`): `PeerRoom` khởi tạo với `password: roomId` — tức Trystero băm room key trực tiếp từ tên phòng. Ai biết tên phòng là vào được.
- **Phòng riêng tư** (`PrivateRoom`): người dùng nhập thêm **mật khẩu**. Mật khẩu **không bao giờ được gửi qua mạng ở dạng thô** — được băm cục bộ:
  ```
  secret = base64(SHA-256(`${roomId}_${password}`))
  ```
  `secret` này mới là giá trị được dùng làm `password` thật cho Trystero (`services/Encryption.encodePassword`), và có thể nhúng vào URL dạng `#secret=...` để chia sẻ (giữ trong URL *hash*, không gửi lên server nào vì hash fragment không nằm trong HTTP request). Ai không biết password gốc thì không tính ra được `secret`, và không đoán được room key thật của Trystero → không tham gia được swarm.
- **Nhiều peer trong 1 phòng**: Trystero thiết lập **mesh đầy đủ** — mỗi peer mở `RTCPeerConnection` riêng với *từng* peer khác trong phòng (không qua trung gian). `PeerRoom.getPeers()` trả danh sách toàn bộ kết nối hiện có.

## 4. Lớp kết nối P2P — `PeerRoom` (`lib/PeerRoom/PeerRoom.ts`)

`PeerRoom` là lớp bọc mỏng quanh `Room` của Trystero, chuẩn hoá thành các API mà UI dùng:

- `onPeerJoin(hookType, fn)` / `onPeerLeave(hookType, fn)` — nhiều "consumer" (nhắn tin, video, file...) cùng đăng ký nhận sự kiện peer vào/ra qua một `Map<PeerHookType, handler>`, tránhghi đè lẫn nhau.
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

## 5. Xác thực danh tính peer (không phải mã hoá nội dung)

Khi có peer mới vào phòng (`onPeerJoin`), **tự động** (không cần người dùng bấm gì):

1. Bên A gửi `PEER_METADATA`: `{ userId, customUsername, publicKeyString, identitySignatureBase64 }`.
   - `identitySignatureBase64` = ký chuỗi thách thức `"${roomId}_${userId}"` bằng **private key RSASSA-PKCS1-v1_5** của A (khoá này được sinh 1 lần và lưu bền trong `localforage`, không đổi giữa các phiên).
2. Bên B nhận được, `parseCryptoKeyString` để lấy lại `CryptoKey` từ chuỗi base64, rồi `verifySignature(publicKey, signature, "${roomId}_${userId}")`.
3. Khớp → `PeerVerificationState.VERIFIED`; không khớp → `UNVERIFIED`. Không có bước người dùng tự so khớp fingerprint bằng mắt.

**Quan trọng**: cơ chế này **chứng minh** "người đang nói chuyện với bạn nắm giữ đúng private key ứng với public key đã công bố trong phiên trước" — chống mạo danh lặp lại danh tính giữa các lần join. Nó **không** chống được nghe lén nội dung (đã có DTLS lo) và **không** tự nó chống MITM ở lần gặp đầu tiên tuyệt đối (nếu kẻ tấn công chèn được vào ngay từ đầu và tự xưng danh tính mới, verify vẫn "khớp" vì nó tự ký bằng khoá của chính nó) — đây là hạn chế "trust on first use" cố hữu của mọi hệ không có PKI tập trung, kể cả Signal/PGP.

## 6. Mã hoá nội dung — đính chính quan trọng

Chitchatter **không tự mã hoá nội dung tin nhắn ở tầng ứng dụng**. Bảo mật nội dung đến từ:
- **DTLS** của WebRTC data channel (bắt buộc theo chuẩn WebRTC, trình duyệt tự thương lượng khi thiết lập `RTCPeerConnection`).
- Với **file**: `secure-file-transfer` có mã hoá riêng (dùng khoá suy ra từ tên phòng) trước khi biến file thành torrent — vì file được lan truyền qua giao thức BitTorrent/WebTorrent công khai (không riêng tư như data channel), nên **bắt buộc phải mã hoá ở tầng ứng dụng** cho phần này.

→ Java không có WebRTC/DTLS có sẵn, nên chat-p2p-java **phải tự làm phần mà chitchatter được trình duyệt lo miễn phí** — đây là lý do đề cương chọn tự cài ECDH + AES-GCM cho toàn bộ kênh dữ liệu (không chỉ riêng file), rộng hơn phạm vi mã hoá thật của chitchatter.

## 7. Nhắn tin & trạng thái gõ

- Gửi tin: tạo `UnsentMessage{id, authorId, text, timeSent}` → hiển thị optimistic ngay → gửi qua action `MESSAGE` → tự gắn `timeReceived` cho bản của mình.
- Nhận tin: gắn `timeReceived = now()`, phát âm thanh / notification desktop nếu tab không active (`services/Notification`, `services/Audio`), tắt cờ "đang gõ" của người gửi.
- Đa dòng: Shift+Enter trong `MessageForm` (không có gì đặc biệt ở tầng mạng).
- Markdown: chỉ là render phía nhận (`react-markdown`), dữ liệu gửi đi vẫn là text thô.
- Typing status: debounce 2s (`useDebounce`), gửi qua action `TYPING_STATUS_CHANGE` với payload `{ isTyping }`, có thể nhắm riêng 1 peer (direct message) hoặc broadcast (group).
- **Conversation backfilling**: chỉ áp dụng phòng **công khai**. Khi peer mới vào, nếu `messageLog` hiện có, gửi luôn qua action `MESSAGE_TRANSCRIPT` cho peer đó — nhưng **chỉ khi `messageLog.length === 0`** ở phía nhận (tránh ghi đè nếu đã có sẵn dữ liệu).

## 8. Direct message

Không phải kết nối riêng — dùng chung `RTCPeerConnection` mesh sẵn có, chỉ khác:
- `namespace = ActionNamespace.DIRECT_MESSAGE` thay vì `GROUP`.
- Gửi kèm `{ target: targetPeerId }` để Trystero chỉ gửi tới 1 peer thay vì broadcast.
- `messageLog` được tách riêng theo từng `targetPeerId` (`ShellMessageLog.directMessageLog: Record<peerId, MessageLog>`), không lẫn với chat nhóm.

## 9. Video / Audio call & Screen share

Cả 3 dùng chung cơ chế `PeerRoom.addStream(mediaStream, { metadata })`:
- **Webcam**: `getUserMedia({ video: {...} })`, gắn `metadata: { type: StreamType.WEBCAM }`.
- **Mic**: tương tự nhưng audio, quản lý qua `AudioChannelState` riêng (`useRoomAudio`).
- **Screen share**: `getDisplayMedia`, `metadata: { type: StreamType.SCREEN_SHARE }`.

Khi 1 stream được add, tất cả peer trong phòng nhận `room.onPeerStream(stream, peerId, metadata)` — phân biệt loại stream bằng `metadata.type` để hiển thị đúng chỗ (`peerVideoStreams` vs `peerScreenStreams`). Có action riêng (`AUDIO_CHANGE`/`VIDEO_CHANGE`/`SCREEN_SHARE`) chỉ để đồng bộ **trạng thái hiển thị** (icon bật/tắt trên peer list), không mang media — media đi qua track/stream thật của WebRTC, không qua kênh action.

**Đây là phần phụ thuộc nặng nhất vào hạ tầng có sẵn của trình duyệt** (codec H.264/VP8/Opus, `RTCPeerConnection` transceiver, `getUserMedia`/`getDisplayMedia`) — Java không có tương đương built-in, phải dùng thư viện ngoài (vd. JavaCV/FFmpeg cho codec, thư viện capture riêng cho webcam/screen) nếu muốn làm.

## 10. Chia sẻ file

Khác hẳn cơ chế message ở trên — **không đi qua WebRTC DataChannel của Trystero** mà qua **WebTorrent** (giao thức BitTorrent chạy được trong trình duyệt qua WebRTC data channel *của WebTorrent*, độc lập với data channel của Trystero):

1. `fileTransfer.offer(files, roomId)` (gói `secure-file-transfer`) — mã hoá file, biến thành torrent, trả về `magnetURI`.
2. Gửi `magnetURI` cho các peer qua action `FILE_OFFER` (đây mới là thứ đi qua kênh Trystero — chỉ là con trỏ, không phải nội dung file).
3. Bên nhận dùng `magnetURI` để tải torrent qua WebTorrent client riêng, rồi giải mã (khoá suy ra từ tên phòng, tương tự cơ chế password ở mục 3).
4. File ảnh/audio/video nhỏ có thể hiển thị **inline** trong khung chat (`isAllInlineMedia`), khác với file thường chỉ hiện nút tải.
5. Rời phòng / đổi file → gửi `FILE_OFFER` với `magnetURI = null` để thu hồi (`fileTransfer.rescind`).

→ Vì Java không có WebTorrent, chat-p2p-java sẽ cần tự thiết kế cơ chế truyền file (chia chunk qua chính kênh P2P đã có, mã hoá từng chunk bằng AES-GCM) — **đơn giản hơn** kiến trúc 2 tầng (Trystero + WebTorrent) của bản gốc, phù hợp vì không cần chia sẻ file kiểu "swarm" công khai.

## 11. Cài đặt cá nhân & lưu trữ cục bộ

`localforage` (IndexedDB) chỉ lưu **đúng 1 key**: `userSettings` (`models/storage.ts`), gồm: `colorMode`, `userId`, `customUsername`, `publicKey`/`privateKey` (cặp khoá ký danh tính — sinh 1 lần, tồn tại lâu dài qua các phiên), `playSoundOnNewMessage`, `showNotificationOnNewMessage`, `showActiveTypingStatus`, `isEnhancedConnectivityEnabled`, `selectedSound`.

**Không có gì khác được lưu** — đặc biệt **tin nhắn và metadata phòng không bao giờ persist**, đúng tinh thần "ephemeral" (rời phòng/đóng tab là mất sạch lịch sử chat, chỉ cấu hình cá nhân còn lại).

## 12. Chẩn đoán kết nối (Enhanced Connectivity)

`lib/ConnectionTest` kiểm tra 2 việc trước/trong khi vào phòng:
- **Có kết nối được tới tracker hay không** (`TrackerConnection: SEARCHING/SUCCESS/FAILURE`) — phát hiện sớm nếu mạng chặn WebSocket tới tracker.
- **Có TURN server khả dụng hay không** (`hasTURNServer`) — thử một `RTCPeerConnection` với `iceServers` cấu hình, xem có sinh được `relay` candidate không.

Kết quả hiển thị ở UI (`EnhancedConnectivityControl`) giúp người dùng tự chẩn đoán vì sao không kết nối được — **đây chính là ý tưởng của "đo tỉ lệ thiết lập kết nối P2P thành công"** trong mục 5 (Kế hoạch đánh giá) của đề cương, chitchatter làm ở phía client, còn đề cương định làm ở tầng đo hiệu năng riêng.

## 13. Nhúng ứng dụng (SDK / iframe)

`models/sdk.ts` định nghĩa giao thức `postMessage` giữa trang cha và `<iframe>` nhúng chitchatter: trang cha gửi `CONFIG` (màu theme, tên phòng, user id/name...) qua `window.postMessage`, chitchatter lắng nghe và tự cấu hình theo — có kiểm tra `origin` khớp domain cha để tránh giả mạo. **Không liên quan tới core P2P/chat**, thuộc nhóm tính năng UI/tích hợp, ngoài phạm vi đề cương.

## 14. Bảng ánh xạ sang kiến trúc Java (tổng hợp, dùng khi code)

| Thành phần chitchatter | Cơ chế thật | Tương đương cần xây ở chat-p2p-java |
|---|---|---|
| Trystero (`joinRoom`, tracker) | BitTorrent tracker làm signaling, tự động ICE | `signaling-server` (Spring Boot/WebSocket) đã xây — vai trò tương đương, tự viết vì Java không có tracker công khai kiểu này |
| `RTCPeerConnection` + DataChannel (DTLS) | WebRTC built-in trình duyệt | `p2p-core.P2pDataChannel` (ice4j + socket, **chưa xây**) + `crypto` (ECDH/AES-GCM tự viết vì không có DTLS) |
| `PeerRoom.makeAction` (đa kênh logic trên 1 data channel) | Đặt tên action, phân namespace | Cần thiết kế 1 lớp tương đương (chưa có) — có thể làm 1 `MessageType` enum + framing đơn giản trên `DataChannel.send/onReceive` hiện tại |
| `PEER_METADATA` + chữ ký RSASSA | Xác thực danh tính tự động | Đề cương đang chọn xác thực **thủ công** qua fingerprint (khác cách, đã cài) — nếu muốn "y hệt" cần đổi sang ký/verify tự động bằng `crypto.KeyExchangeService` (thêm chữ ký, hiện chỉ có ECDH trao khoá) |
| `secure-file-transfer` + WebTorrent | Mã hoá + phân phối file qua BitTorrent | Tự thiết kế: chia chunk qua `DataChannel` sẵn có, mã hoá từng chunk bằng `AesGcmCipher` |
| `getUserMedia`/`getDisplayMedia` + media track WebRTC | Video/audio call, screen share | Cần thư viện ngoài (capture + codec), độ phức tạp cao — quyết định mức độ làm tuỳ khả năng (xem trao đổi mở rộng phạm vi đề cương) |
| `localforage` (IndexedDB) | Lưu cặp khoá + cài đặt | File cấu hình cục bộ (vd. Java Preferences API hoặc file JSON trong thư mục người dùng) |
| `lib/ConnectionTest` | Kiểm tra tracker/TURN khả dụng | Tương đương ở tầng đo hiệu năng của đề cương (mục 9: tỉ lệ kết nối P2P thành công) |

## 15. Ghi chú áp dụng cho chat-p2p-java

Theo trao đổi trong quá trình làm việc: mục tiêu là làm **đủ 12 chức năng** của chitchatter (mục 7 của [phần trả lời trước](#) — không lặp lại ở đây), **triển khai từng chức năng một** theo thứ tự phụ thuộc kỹ thuật hợp lý (transport P2P thật → mã hoá nội dung → nhắn tin nhóm/DM → xác thực tự động kiểu chitchatter → file → nâng cao/media), và **xác thực peer sẽ đổi sang kiểu tự động (chữ ký số)** giống chitchatter thay vì thủ công như đề cương gốc.

Đề cương (`De-cuong-Chat-P2P-Java.md`) và phân công (`Phan-cong-cong-viec.md`) cần cập nhật lại theo hướng này ở bước tiếp theo.
