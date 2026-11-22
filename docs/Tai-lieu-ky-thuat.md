Tài liệu kỹ thuật thống nhất — chat-p2p-java

# TÀI LIỆU KỸ THUẬT: CHAT-P2P-JAVA

**Ngày:** 22/11/2026 · **Phiên bản code:** nhánh `main`, commit `a0ee278` · **Signaling server trên VPS:** đã deploy bản này ngày 22/11/2026.

*Đây là tài liệu kỹ thuật **duy nhất** của dự án. Ngày 22/11/2026, ba tài liệu trước đây được gộp vào đây: `Tai-lieu-ky-thuat.md` (bản cũ), `Bao-cao-chuc-nang-da-lam.md` (nay là Phần I) và `Bao-cao-bao-mat-13-van-de.md` (nay là mục J.3). Các phần thiết kế viết từ giai đoạn đầu đã được viết lại theo đúng code hiện tại. Đọc cùng [De-cuong-Chat-P2P-Java.md](De-cuong-Chat-P2P-Java.md) (mục tiêu đồ án, nộp GVHD), [Phan-cong-cong-viec.md](Phan-cong-cong-viec.md) (chia việc 2 thành viên) và [README.md](../README.md) (lệnh build/chạy nhanh).*

**Cấu trúc tài liệu:**

| Phần | Nội dung |
|---|---|
| A | Giới thiệu tài liệu |
| B | Tổng quan hệ thống: bối cảnh, yêu cầu chức năng / phi chức năng, phạm vi |
| C | Kiến trúc: logic, module Maven, triển khai thực tế |
| D | So sánh với **chitchatter** (bản tham chiếu) |
| E | Thiết kế chi tiết theo code hiện tại: giao thức, phiên phòng, mã hoá, xác thực, ICE, từng chức năng |
| F | Yêu cầu phi chức năng, số đo và kiểm thử |
| G | Cấu hình, build, triển khai và vận hành |
| H | Xử lý lỗi và logging |
| I | **Chức năng đã hoàn thành**: luồng hoạt động, lỗi có thể xảy ra, kiểm thử (16 chức năng) |
| J | Rủi ro, giới hạn đã biết, và **kiểm định bảo mật 22/11/2026** (13 vấn đề đã sửa) |
| K | Phụ lục: thuật ngữ, tài liệu tham khảo, lịch sử thay đổi |

---

# PHẦN A — GIỚI THIỆU

## A.1 Mục đích tài liệu

1. Mô tả **đúng hiện trạng** của hệ thống: kiến trúc, giao thức, thiết kế từng thành phần, cấu hình và vận hành.
2. Ghi lại **từng chức năng đã làm**: hệ thống xử lý thế nào khi mọi thứ bình thường và khi có lỗi, kèm test chứng minh (Phần I).
3. Liệt kê **rủi ro và giới hạn** một cách trung thực, kể cả các vấn đề bảo mật đã tìm ra và đã sửa (Phần J), để đưa thẳng vào chương "Đánh giá" và "Hướng phát triển" của báo cáo đồ án.

## A.2 Phạm vi tài liệu

Bao gồm: kiến trúc, giao thức signaling và P2P, mã hoá và xác thực, thiết kế và hành vi của từng chức năng, số đo hiệu năng, kiểm thử, cấu hình, triển khai lên VPS, xử lý lỗi, rủi ro và kết quả kiểm định bảo mật.

**Không bao gồm:** lý do chọn đề tài và mục tiêu học thuật (xem đề cương), lịch làm việc và phân công (xem phân công công việc).

## A.3 Đối tượng đọc

Hai thành viên thực hiện đồ án, giảng viên hướng dẫn và hội đồng muốn hiểu sâu về kỹ thuật. Giả định người đọc biết Java, mạng máy tính (TCP/UDP/NAT) và mật mã học ứng dụng ở mức nhập môn. Thuật ngữ chuyên sâu (ICE, STUN, TURN, ECDH, AES-GCM, HKDF…) được tổng hợp ở mục K.1.

## A.4 Quy ước trong tài liệu

- Tên lớp / phương thức viết dạng `code` là tên **thật** trong repo.
- Tham chiếu chéo dùng dạng "mục X.Y" (ví dụ "mục E.6.1"), luôn trỏ nội bộ trong tài liệu này. Chú thích trong mã nguồn cũng dùng các số hiệu mục này.
- Ký hiệu cột trạng thái ở Phần I: ✅ đã xử lý · ⚠️ xử lý một phần · ❌ chưa xử lý (có hướng khắc phục).
- Đánh dấu *(sửa 22/11, vấn đề N)* nghĩa là hành vi đó được thêm hoặc sửa trong đợt kiểm định bảo mật ngày 22/11/2026; vấn đề N được mô tả chi tiết ở mục J.3.

---

# PHẦN B — TỔNG QUAN HỆ THỐNG

## B.1 Bối cảnh và bài toán

Ứng dụng chat desktop viết bằng Java, cho phép nhiều người trò chuyện **ngang hàng (P2P)**, **mã hoá đầu-cuối**, không lưu nội dung trên máy chủ trung gian. Dự án lấy `chitchatter` (ứng dụng web P2P mã nguồn mở, dùng WebRTC) làm tham chiếu về kiến trúc mesh và tập tính năng, nhưng **không port code**, vì toàn bộ stack khác nhau: JavaFX thay React, Spring Boot + ice4j thay Trystero/WebRTC. So sánh chi tiết ở Phần D.

## B.2 Yêu cầu chức năng và trạng thái

| # | Chức năng | Trạng thái | Mô tả hành vi | Thiết kế |
|---|---|---|---|---|
| 1 | Tài khoản (đăng ký / đăng nhập) và chế độ khách | ✅ | I.1 | G.3 |
| 2 | Sảnh chờ: tạo phòng, danh sách phòng, vào phòng | ✅ | I.2 | E.4 |
| 3 | Kết nối P2P trực tiếp (ICE/STUN, TURN dự phòng) | ✅ | I.3 | E.6 |
| 4 | Trao khoá và mã hoá đầu-cuối | ✅ | I.4 | E.3.4 |
| 5 | Xác thực danh tính người trong phòng | ✅ | I.5 | E.5 |
| 6 | Chat nhóm (mesh nhiều người) | ✅ | I.6 | E.7, E.8.1 |
| 7 | Chat riêng 1-1 trong phòng | ✅ | I.7 | E.8.2 |
| 8 | Trạng thái "đang gõ…" | ✅ | I.8 | E.8.3 |
| 9 | Định dạng tin nhắn (Markdown) và link | ✅ | I.9 | E.8.1 |
| 10 | Âm báo, thông báo màn hình, cài đặt, giao diện | ✅ | I.10 | E.8.6 |
| 11 | Lịch sử chat cho người mới vào phòng | ✅ | I.11 | E.4 |
| 12 | Phòng có mật khẩu | ✅ | I.12 | E.5.4 |
| 13 | Rời phòng / mất kết nối | ✅ | I.13 | E.4 |
| 14 | Truyền file 1-1 | ✅ | I.14 | E.8.4 |
| 15 | Log theo dõi (terminal và file) | ✅ | I.15 | H.2 |
| 16 | Bản đóng gói cho người dùng (Windows) | ✅ | I.16 | G.2 |
| — | Gọi thoại, gọi video, chia sẻ màn hình | ❌ chưa làm | — | E.8.5 |

## B.3 Yêu cầu phi chức năng (tóm tắt; số đo ở Phần F)

| Nhóm | Yêu cầu |
|---|---|
| Bảo mật | Mọi nội dung qua kênh P2P mã hoá AES-256-GCM, khoá phiên từ ECDH + HKDF; danh tính peer xác thực bằng chữ ký ECDSA gắn với phiên ECDH; signaling server không đọc và không lưu nội dung; kết nối tới server đi qua TLS |
| Hiệu năng | Thiết lập kết nối trong vài giây; tin nhắn đi thẳng giữa các máy; truyền file không sụp tốc độ khi mạng mất gói |
| Độ tin cậy | Ephemeral theo thiết kế: rời phòng là mất tin nhắn (trừ khi người dùng bật lưu lịch sử trên máy). Tin nhắn, lịch sử và file có ACK + gửi lại trên nền UDP |
| Khả năng mở rộng | Mesh đầy đủ, chi phí tăng theo N²; tối đa 8 người mỗi phòng |
| Vận hành | Một signaling server nhẹ (Spring Boot + MySQL cho tài khoản) và một TURN server (coturn) dự phòng |

## B.4 Ngoài phạm vi

Danh bạ / bạn bè lâu dài; tin nhắn offline (người nhận phải đang ở trong phòng); đồng bộ nhiều thiết bị cho một người; nhúng web/iframe (không có ý nghĩa với ứng dụng desktop).

---

# PHẦN C — KIẾN TRÚC HỆ THỐNG

## C.1 Kiến trúc logic tổng thể

```
                     ┌──────────────────────────────┐
                     │      signaling-server         │  Spring Boot + WebSocket (/ws), REST (/auth, /rooms,
                     │  RoomRegistry, relay, auth     │  /turn-credentials), MySQL (tài khoản). KHÔNG đọc nội dung
                     └──────────────▲───────────────┘
                     wss:// (SignalMessage JSON) / https:// (REST) qua Caddy
                                    │
        ┌───────────────────────────┴────────────────────────────┐
        │                                                         │
┌───────▼─────────┐   UDP trực tiếp (ice4j), hoặc qua TURN   ┌────────▼────────┐
│ client-javafx A  │◄──────── Envelope mã hoá AES-GCM ───────►│ client-javafx B  │
└──────────────────┘                                          └──────────────────┘
```

Hai tầng giao thức tách biệt hoàn toàn:
- **Tầng signaling** (JSON qua WebSocket): chỉ dùng để vào / rời phòng và trao đổi thông tin ICE lúc thiết lập kết nối.
- **Tầng dữ liệu P2P**: sau khi kết nối mở, mọi thứ (tin nhắn, lịch sử, file, trạng thái) đóng thành `Envelope` mã hoá, đi thẳng giữa các máy, không qua signaling server.

## C.2 Module Maven

| Module | Gói gốc | Lớp chính | Vai trò |
|---|---|---|---|
| `common` | `com.datn.chatp2p.common` | `DataChannel`, `ChatMessage`, `Peer`, `Envelope`, `EnvelopeType`, các `*Payload`, `SignalMessage`, `SignalType`, `JoinRejectReason`, `SecretLogging` | Model và giao thức dùng chung |
| `crypto` | `com.datn.chatp2p.crypto` | `KeyExchangeService` (ECDH + HKDF), `AesGcmCipher`, `Hkdf`, `IdentitySignatureService` (ECDSA), `IdentityKeyStore`, `IdentityBackup`, `RoomSecret`, `Fingerprint` | Mật mã |
| `p2p-core` | `com.datn.chatp2p.p2p` | `RoomSession`, `PeerConnection`, `EnvelopeCodec`, `ReplayWindow`, `IceP2pConnectionEstablisher`, `P2pDataChannel`, `WebSocketSignalingClient`, `FileSender` / `FileReceiver` / `ReliableChunkSender` | Phiên phòng, kết nối P2P, truyền file |
| `signaling-server` | `com.datn.chatp2p.signaling` | `SignalingWebSocketHandler`, `RoomRegistry`, `WrongPasswordLimiter`, `AuthController` / `AuthService`, `RoomController`, `TurnController` / `TurnCredentialService`, `RequestRateLimiter` | Giới thiệu peer, tài khoản, cấp TURN tạm thời |
| `client-javafx` | `com.datn.chatp2p.client` | `P2pChatSession`, `FileTransferManager`, `GroupHistory`, `MessageDeliveryTracker`, `AuthController`, `HomeController`, `RoomController`, `MarkdownParser`, `TrustedPeers`, `LocalHistoryStore` | Giao diện và logic phiên chat |

**Phụ thuộc:** `common ← crypto`, `common ← p2p-core ← client-javafx`, `crypto ← client-javafx`, `common ← signaling-server`. `signaling-server` **không** phụ thuộc `crypto` hay `p2p-core`, đúng nguyên tắc "server chỉ giới thiệu, không xử lý nội dung".

## C.3 Kiến trúc triển khai thực tế

- **Client:** mỗi người chạy `ChatP2P.exe` (bản đóng gói jpackage kèm Java runtime, mục I.16) hoặc `mvn -pl client-javafx javafx:run`.
- **VPS** (Ubuntu 20.04):
  - **Caddy** nghe cổng 443 với chứng chỉ Let's Encrypt cho `chat.zingme.fun`, chuyển tiếp (kể cả nâng cấp WebSocket) vào `127.0.0.1:8080`.
  - **Signaling server** chạy bằng service systemd `chat-p2p` (`java -jar .../signaling-server/target/signaling-server.jar`), chỉ nghe `127.0.0.1:8080`.
  - **MySQL 8.4** chạy trong Docker, bind `127.0.0.1`; chỉ lưu tài khoản và bản băm token phiên.
  - **coturn** nghe UDP 3478, xác thực bằng khoá bí mật dùng chung với server (TURN REST API).
- **Không lưu nội dung chat ở đâu trên server.**

## C.4 Nguyên tắc kiến trúc xuyên suốt

1. Mọi dữ liệu qua kênh P2P phải tự mã hoá, vì không có DTLS như WebRTC (mục E.3.4).
2. Một `PeerConnection` = một kết nối 1-1; phòng N người = N-1 kết nối song song trên mỗi máy (mesh).
3. Đa kênh logic tự thiết kế bằng `Envelope` + `EnvelopeType` + namespace (nhóm / riêng).
4. Signaling server chỉ giới thiệu và chuyển tiếp, không bao giờ đọc payload.
5. Trạng thái tin cậy (đã qua mật khẩu phòng, đã xác thực danh tính) gắn với **đúng phiên ECDH** đã chứng minh nó (mục E.4, E.5).
6. Ephemeral: không lưu tin nhắn, trừ tuỳ chọn lưu lịch sử trên máy (mặc định tắt, mã hoá DPAPI).

---

# PHẦN D — SO SÁNH VỚI CHITCHATTER (BẢN THAM CHIẾU)

*Rút gọn từ phần phân tích mã nguồn `chitchatter-develop` ở bản tài liệu đầu (tháng 8/2026). Mục đích: chỉ ra chính xác chỗ nào giống, chỗ nào khác, và vì sao.*

## D.1 Kiến trúc chitchatter

Chitchatter là ứng dụng web thuần client (React + Vite), không có API server. Signaling đi qua **BitTorrent tracker công khai** (thư viện `trystero`); kết nối giữa các trình duyệt là `RTCPeerConnection` của WebRTC, có **DTLS do trình duyệt tự lo**; TURN công khai làm dự phòng. Chitchatter không tự mã hoá nội dung tin nhắn ở tầng ứng dụng: bảo mật nội dung dựa vào DTLS. Riêng file được mã hoá trước khi chia sẻ qua WebTorrent.

## D.2 Stack công nghệ

| Thành phần | chitchatter | chat-p2p-java |
|---|---|---|
| Giao diện | React + TypeScript | JavaFX 21 + AtlantaFX |
| Signaling | BitTorrent tracker (`trystero`) | `signaling-server` tự viết (Spring Boot + WebSocket) |
| Kết nối P2P | WebRTC `RTCPeerConnection` | ice4j (ICE/STUN/TURN) + `P2pDataChannel` tự viết trên UDP |
| Mã hoá kênh | DTLS (trình duyệt) | ECDH P-256 + HKDF + AES-256-GCM tự cài (`crypto`) |
| Danh tính | RSASSA-PKCS1-v1_5, ký `roomId_userId` | ECDSA P-256, ký cả hai khoá ECDH của phiên |
| Truyền file | `secure-file-transfer` + WebTorrent | Giao thức chunk tin cậy riêng qua kênh P2P (mục E.8.4) |
| Lưu cục bộ | `localforage` (cài đặt + khoá) | File trong `~/.chat-p2p-java/` (khoá mã hoá DPAPI, cài đặt, vân tay tin cậy, lịch sử tuỳ chọn) |
| Tài khoản | Không có | Tuỳ chọn (MySQL + BCrypt), song song chế độ khách |

## D.3 Phòng công khai và phòng riêng tư

- **chitchatter:** phòng riêng dùng `secret = base64(SHA-256(roomId_password))` làm khoá swarm của Trystero.
- **chat-p2p-java:** mạnh hơn ở hai điểm (mục E.5.4):
  1. Dẫn xuất bằng **PBKDF2 120.000 vòng**, tách thành *thẻ phòng* (gửi server dạng `tên~thẻ`) và *khoá chứng minh*.
  2. Sau khi trao khoá, hai bên gửi **`ROOM_PROOF`** (HMAC gắn với khoá ECDH của phiên). Vì vậy kể cả server bị chiếm cũng không ghép được người lạ vào phòng.

## D.4 Ánh xạ `PeerAction` của chitchatter sang `EnvelopeType`

| `PeerAction` (chitchatter) | `EnvelopeType` (chat-p2p-java) | Ghi chú |
|---|---|---|
| `MESSAGE` | `MESSAGE` | Kèm `MESSAGE_ACK`, gửi lại tối đa 6 lần |
| `MESSAGE_TRANSCRIPT` | `MESSAGE_TRANSCRIPT` | Chia gói, có ACK; chỉ nhận từ peer đã xác thực |
| `PEER_METADATA` | `PEER_IDENTITY` | Chữ ký gắn với phiên ECDH |
| `TYPING_STATUS_CHANGE` | `TYPING_STATUS_CHANGE` | |
| `FILE_OFFER` | `FILE_OFFER`, `FILE_ANSWER`, `FILE_CHUNK`, `FILE_ACK`, `FILE_CANCEL` | Giao thức tin cậy riêng thay cho WebTorrent |
| `AUDIO_CHANGE`, `VIDEO_CHANGE`, `SCREEN_SHARE` | `AUDIO_CHANGE`, `VIDEO_CHANGE`, `SCREEN_SHARE_CHANGE`, `MEDIA_FRAME` | Mới khai báo, **chưa cài đặt** |
| `MEDIA_MESSAGE` | — | Ảnh gửi dạng file, xem trước trong khung chat |
| *(không có)* | `ROOM_PROOF`, `MESSAGE_ACK`, `MESSAGE_READ`, `MESSAGE_REACTION`, `MESSAGE_EDIT` | Bổ sung: chứng minh mật khẩu, xác nhận, đã xem, cảm xúc, sửa / xoá |

Namespace: `GROUP` và `DIRECT_MESSAGE`, giống `ActionNamespace` của chitchatter. Tin riêng đi trên cùng kết nối mesh, chỉ khác namespace và chỉ gửi cho đúng một peer.

## D.5 Mức độ giống bản gốc

| Mức độ | Khối |
|---|---|
| Giữ ý tưởng, tự viết lại cơ chế | Signaling / tìm nhau, đa kênh logic bằng `Envelope` |
| Mạnh hơn bản gốc | Phòng có mật khẩu (PBKDF2 + `ROOM_PROOF`), chữ ký danh tính gắn phiên ECDH, ghi nhớ vân tay tin cậy |
| Thêm mới hoàn toàn (bản gốc có DTLS nên không cần) | Mã hoá kênh truyền, chống replay, tầng truyền UDP qua ice4j (chia mảnh, gửi lại) |
| Đơn giản hoá có chủ đích | Truyền file 1-1 thay vì swarm WebTorrent |
| Chưa làm | Gọi thoại / video / chia sẻ màn hình |
| Loại bỏ | Nhúng iframe / SDK (không áp dụng cho desktop) |

---

# PHẦN E — THIẾT KẾ CHI TIẾT (THEO CODE HIỆN TẠI)

## E.1 Nguyên tắc thiết kế

Xem mục C.4. Thêm: mọi lỗi cục bộ (một gói hỏng, một peer lỗi, một handler ném ngoại lệ) không được làm hỏng phần còn lại của phiên (mục H.1).

## E.2 Luồng dữ liệu tổng thể

```
JOIN (wss) ─► PEER_LIST ─► với mỗi peer có sẵn: OFFER(ICE) ─► ANSWER(ICE)
  └► ice4j chọn cặp địa chỉ (host / srflx / relay) ─► P2pDataChannel (UDP)
       └► PeerConnection: gói đầu = public key ECDH (rõ) ─► HKDF ─► 2 khoá AES-256 (mỗi chiều 1 khoá)
            └► Envelope mã hoá: [ROOM_PROOF] ─► PEER_IDENTITY ─► MESSAGE / TRANSCRIPT / FILE_* / ...
```

## E.3 Giao thức tầng dữ liệu — `Envelope`

### E.3.1 `EnvelopeType`

`MESSAGE`, `MESSAGE_ACK`, `MESSAGE_READ`, `MESSAGE_EDIT`, `MESSAGE_REACTION`, `MESSAGE_TRANSCRIPT`, `TYPING_STATUS_CHANGE`, `PEER_IDENTITY`, `ROOM_PROOF`, `FILE_OFFER`, `FILE_ANSWER`, `FILE_CHUNK`, `FILE_ACK`, `FILE_CANCEL`. Bốn loại `AUDIO_CHANGE`, `VIDEO_CHANGE`, `SCREEN_SHARE_CHANGE`, `MEDIA_FRAME` mới khai báo, chưa dùng. Ánh xạ với chitchatter ở mục D.4.

### E.3.2 Cấu trúc `Envelope`

`record Envelope(EnvelopeType type, String namespace, long timestamp, byte[] payload)`, tuần tự hoá bằng Jackson. Không dùng kiểu đa hình (`@JsonTypeInfo`), nên không có rủi ro giải tuần tự hoá lớp tuỳ ý. `payload` là JSON của từng `*Payload` (`MessagePayload`, `FileOfferPayload`…).

### E.3.3 Payload theo từng loại

Mỗi `EnvelopeType` có một record payload trong `common/.../protocol`. Bên nhận luôn coi payload là **dữ liệu không tin cậy**: tác giả tin nhắn lấy từ `peerId` của kết nối, không lấy từ payload; các trường được kiểm tra giới hạn (độ dài text ≤ 12.000 ký tự, cảm xúc trong bộ cố định, offer file kiểm tra kích thước / số chunk / SHA-256…).

### E.3.4 `EnvelopeCodec` — mã hoá và chống replay

- **Khoá:** `KeyExchangeService.deriveSessionKeys` lấy bí mật chung ECDH (P-256), rồi dùng **HKDF-SHA256** (RFC 5869) với salt = SHA-256(hai public key đã sắp xếp) và info = nhãn + SHA-256(public key bên gửi). Kết quả là **hai khoá AES-256 khác nhau cho hai chiều**, nên chặn được kiểu tấn công reflection.
- **Nonce 12 byte:** 8 byte số thứ tự tăng dần + 4 byte ngẫu nhiên. Nonce là đầu vào của GCM, nên sửa số thứ tự thì giải mã thất bại.
- **Chống replay:** `ReplayWindow` là cửa sổ trượt 1024 số thứ tự. Gói được kiểm tra "còn mới không" **trước** khi giải mã, nhưng chỉ đánh dấu đã thấy **sau** khi giải mã thành công, nên gói giả mang số thứ tự lớn không đẩy được cửa sổ.
- **Gói đầu tiên** của mỗi kết nối là public key ECDH dạng rõ, được gửi lại 5 lần cách nhau 300 ms. Từ gói thứ hai, mọi thứ đều mã hoá; không có đường lùi về dạng rõ.

## E.4 `RoomSession` — quản lý nhiều peer trong một phòng

1. `join()` kết nối `WebSocketSignalingClient`, gửi `JOIN`. Khi nhận `PEER_LIST`, người mới vào làm **bên gửi OFFER** cho từng peer có sẵn. Mỗi peer có một `IceP2pConnectionEstablisher` riêng.
2. Khi ICE xong (`onIceConnected`), tạo `PeerConnection` với cặp khoá ECDH mới và bắt đầu trao khoá. Khi ECDH xong, gọi `onPeerJoined`.
3. **Thay kết nối dưới cùng `peerId`** (một OFFER thứ hai hoàn tất ICE): kết nối cũ và establisher cũ được đóng / giải phóng để không rò cổng UDP. *(sửa 22/11, vấn đề 1)* `RoomSession` đồng thời phát **`onPeerLeft` cho kết nối cũ**, để tầng trên xoá trạng thái tin cậy. Kết nối mới phải làm lại `ROOM_PROOF` và `PEER_IDENTITY` từ đầu.
4. `dispatchEnvelope` gọi các handler đã đăng ký theo `EnvelopeType`. Handler này lỗi không chặn handler khác.
5. Ở client, `P2pChatSession` bọc mọi handler (trừ `ROOM_PROOF`) bằng `admittedOnly`: peer chưa được nhận vào phòng thì bản tin bị bỏ qua. Phòng công khai nhận vào ngay khi ECDH xong; phòng có mật khẩu chỉ nhận sau `ROOM_PROOF` hợp lệ.
6. **Lịch sử cho người mới vào:** khi xác thực xong danh tính một peer, mỗi bên gửi `GroupHistory` (tối đa 200 tin nhóm, không gồm tin riêng) qua `MESSAGE_TRANSCRIPT`, chia gói theo ngân sách 6.000 ký tự, có ACK. Bên nhận chỉ nhận từ peer đã xác thực, tối đa 200 tin mỗi peer, và *(sửa 22/11, vấn đề 5)* bỏ các mục ghi tác giả là chính người nhận.
7. **Rời phòng:** `leave()` đóng mọi kết nối, giải phóng establisher, gửi `LEAVE`. Server phát `PEER_LEFT` cho người còn lại.

## E.5 Xác thực danh tính

### E.5.1 Thuật toán

ECDSA trên P-256 (`SHA256withECDSA`), cùng đường cong với ECDH. Khoá danh tính sinh một lần cho mỗi máy, lưu ở `~/.chat-p2p-java/identity.properties`. Trên Windows, private key được mã hoá bằng DPAPI (định dạng version 2; file cũ dạng rõ tự được chuyển). Có thể xuất / nhập sang máy khác (`IdentityBackup`: PBKDF2 310.000 vòng + AES-GCM, mật khẩu ≥ 8 ký tự).

### E.5.2 Thông điệp thách thức và luồng

Mỗi bên gửi `PEER_IDENTITY` gồm public key danh tính và chữ ký trên chuỗi `phòng | peerId bên ký | SHA-256(khoá ECDH bên ký) | SHA-256(khoá ECDH bên nhận)` (`IdentitySignatureService.handshakeChallengeFor`). Bên nhận kiểm tra bằng **chính các khoá ECDH mà nó thấy trong phiên**. Nếu kẻ đứng giữa tráo khoá ECDH thì chữ ký không khớp. Gói được gửi lại sau 0,4 s, 1,2 s và 2,5 s.

### E.5.3 Lưu ý bảo mật

- Chữ ký hợp lệ chứng minh người kia **giữ private key ứng với public key họ trình ra**. Lần gặp đầu vẫn phải tin khoá nhận được (TOFU), nên người dùng cần so vân tay qua kênh khác. Hộp so vân tay hiện mã an toàn 60 chữ số và mã QR.
- Vân tay đã tin cậy được ghi nhớ theo tên hiển thị (`TrustedPeers`). Khoá đổi thì hiện "Khoá đã đổi". *(sửa 22/11, vấn đề 7)* Chọn "Không khớp" khi khoá đã đổi sẽ **giữ** vân tay cũ.
- Trạng thái "đã xác thực" gắn với phiên ECDH: kết nối thay thế phải xác thực lại (mục E.4 bước 3).

### E.5.4 Phòng có mật khẩu (`RoomSecret`)

PBKDF2-HMAC-SHA256 120.000 vòng trên (tên phòng, mật khẩu) cho ra một khoá chính. Từ khoá chính tách ra **thẻ phòng** (24 ký tự hex, gửi server dạng `tên~thẻ`) và **khoá chứng minh**. `ROOM_PROOF` = HMAC(khoá chứng minh, `phòng | peerId bên gửi | SHA-256(các khoá ECDH)`), so sánh thời gian hằng. Server từ chối JOIN `tên~thẻ-khác` khi đang có phòng cùng tên khác thẻ (`WRONG_PASSWORD`), và giới hạn 5 lần sai mỗi phút mỗi IP. Mật khẩu không bao giờ rời khỏi máy.

## E.6 Kết nối P2P bằng ice4j

### E.6.1 Signaling mang thông tin ICE

`OFFER` / `ANSWER` mang `IceOfferPayload` / `IceAnswerPayload` (ufrag, mật khẩu ICE, danh sách candidate) trong trường `payload` của `SignalMessage`; server chuyển tiếp nguyên văn. Các loại `SignalType`: `JOIN`, `LEAVE`, `PEER_LIST`, `PEER_JOINED`, `PEER_LEFT`, `OFFER`, `ANSWER`, `ICE_CANDIDATE`, `JOIN_REJECTED`. *(sửa 22/11, vấn đề 2)* Server xoá `authToken` của người gửi trước khi chuyển tiếp.

### E.6.2 Thiết lập kết nối

`IceP2pConnectionEstablisher` tạo ICE agent với cổng UDP trong dải **10000–11000**. Candidate gồm: địa chỉ IPv4 nội bộ có thể định tuyến (loại link-local, loopback, multicast), địa chỉ public qua STUN `stun.l.google.com:19302`, và relay qua TURN (tài khoản tạm thời từ `/turn-credentials`). ice4j làm connectivity check và chọn cặp địa chỉ. `IceConnectionStats.usingRelay()` cho biết kết nối có đi qua TURN không.

### E.6.3 Kênh dữ liệu `P2pDataChannel`

Gửi / nhận datagram trên socket ice4j đã chọn; chỉ nhận gói từ đúng địa chỉ của cặp đã chọn. ice4j cắt gói lớn hơn 1500 byte, nên bản tin được **chia mảnh ≤ 1150 byte** (header 9 byte: id, chỉ số, tổng số mảnh) và ghép lại ở bên nhận. Giới hạn: bản tin ≤ 1 MB, tối đa 64 bản tin đang ghép, mảnh hết hạn sau 5 s. Lỗi xử lý một gói không làm dừng vòng nhận.

### E.6.4 `WebSocketSignalingClient`

Kết nối `serverUri + "/ws"`. Mất kết nối thì tự kết nối lại với thời gian chờ tăng dần (1 s … 30 s) và gửi lại `JOIN`; các kết nối P2P đã có vẫn chạy. Token phiên được gắn vào `JOIN` (và các bản tin khác để server kiểm tra lại). Client chỉ gửi token tới đúng server đã đăng nhập (*sửa 22/11, vấn đề 3*, mục G.4).

## E.7 Mesh nhiều người

N người → N(N-1)/2 kết nối; mỗi máy duy trì N-1 kết nối. Server giới hạn **8 người mỗi phòng**. Tin nhóm được gửi riêng tới từng người; một người lỗi không ảnh hưởng người khác. Test tự động có mesh 3 người và 8 người.

## E.8 Thiết kế từng chức năng

### E.8.1 Tin nhắn nhóm và Markdown

`MESSAGE {id, text, timeSent, replyToId}`. Bên nhận trả `MESSAGE_ACK`; bên gửi gửi lại tối đa 6 lần, mỗi lần cách 500 ms. Bên nhận loại bản trùng theo (peer, id) với cửa sổ 512 id mỗi peer. *(sửa 22/11, vấn đề 8)* Tin dài hơn 12.000 ký tự bị bỏ (vẫn ACK). Sửa / xoá tin (`MESSAGE_EDIT`) chỉ áp dụng khi người gửi là tác giả, *(sửa 22/11, vấn đề 6)* xác định theo cặp (id, tác giả). Markdown do `MarkdownParser` tự viết phân tích (đậm, nghiêng, gạch, code, khối code, danh sách, trích dẫn, link `http(s)` thuần), vẽ bằng JavaFX `Text` / `Hyperlink`, không dùng HTML / WebView. *(sửa 22/11, vấn đề 8)* Thời gian phân tích tuyến tính theo độ dài dòng. `LinkOpener` chỉ mở `http` / `https`.

### E.8.2 Tin riêng (DM)

Cùng `MESSAGE` nhưng namespace `DIRECT_MESSAGE`, chỉ gửi tới một peer; mỗi người có một tab riêng. Tin riêng không vào lịch sử nhóm.

### E.8.3 Trạng thái đang gõ

`TYPING_STATUS_CHANGE {isTyping}` (nhóm hoặc riêng). Bên nhận tự hết hạn sau 6 s nếu không nhận được tín hiệu tiếp theo.

### E.8.4 Truyền file

`FILE_OFFER` (tên, kích thước ≤ 100 MB, SHA-256, kích thước chunk, số chunk) → người nhận bấm Nhận → `FILE_ANSWER` → `FILE_CHUNK` (chunk **4 KB**, cửa sổ trượt **8 chunk**) ↔ `FILE_ACK` (`contiguousUpTo` + danh sách chunk nhận lẻ, ưu tiên chunk mới nhất). Bên gửi **gửi lại nhanh** kiểu TCP RACK: một chunk được coi là mất khi đã có 3 chunk gửi sau nó tới nơi, không phải chờ hết hạn 500 ms. Bên nhận ghi vào file tạm `.part` bằng ghi theo vị trí, kiểm SHA-256 cả file rồi đổi tên. Tên file được làm sạch (`FileNames`): bỏ đường dẫn, ký tự cấm, tên thiết bị Windows; không ghi đè file sẵn có. File lưu vào `~/Downloads`; chỉ ảnh mới mở được bằng một cú bấm.

### E.8.5 Gọi thoại / video / chia sẻ màn hình

**Chưa cài đặt.** Phương án dự kiến: tận dụng `Envelope` / `DataChannel` sẵn có, gửi khung hình JPEG và âm thanh PCM qua `MEDIA_FRAME`. Đây là đánh đổi chất lượng để khỏi phụ thuộc thư viện native.

### E.8.6 Cài đặt và lưu trữ cục bộ

Thư mục `~/.chat-p2p-java/`:

| File | Nội dung |
|---|---|
| `identity.properties` | Khoá danh tính (mã hoá DPAPI trên Windows) |
| `settings.properties` | Giao diện (Primer, Nord, Cupertino sáng/tối, Dracula), âm báo, thông báo, lưu lịch sử, tài khoản gần nhất |
| `trusted-peers.properties` | Tên → vân tay đã tin cậy |
| `history/*.dat` | Lịch sử nhóm (tuỳ chọn, mặc định tắt, mã hoá DPAPI, tối đa 500 tin) |
| `client.properties` | Cấu hình TURN cố định (tuỳ chọn) |
| `logs/client-*.log` | Log, xoay vòng |

---

# PHẦN F — YÊU CẦU PHI CHỨC NĂNG, SỐ ĐO VÀ KIỂM THỬ

## F.1 Hiệu năng (số đo thực tế)

**Truyền file** (4 MB qua ICE thật trên localhost, trung vị 3 lần, mất gói giả lập theo từng datagram):

| Mất gói | Trước (chunk 512 B, cửa sổ 64) | Sau (chunk 4 KB, cửa sổ 8, gửi lại nhanh) |
|---|---|---|
| 0% | 6,4 MB/s | 17,9 MB/s |
| 1% | 0,20 MB/s | 18,1 MB/s |
| 3% | 0,12 MB/s | 18,0 MB/s (lần chậm nhất 4,7) |
| 5% | 0,09 MB/s | 5,5 MB/s (lần chậm nhất 2,2) |

Qua Internet, tốc độ bị giới hạn bởi cửa sổ cố định 32 KB mỗi RTT (khoảng 0,37 MB/s qua TURN với RTT ~86 ms). Chưa đo trên Internet thật.

**Kết nối qua TURN thật** (20/11/2026, mạng 4G): thiết lập ICE 354 ms; cấp cổng relay 221 ms; RTT trung vị 86 ms (p95 191 ms); 200 tin × 1 KB mất 1,5% tính cả hai chiều. Chi tiết và bảng "khi nào đi thẳng, khi nào qua TURN" ở mục I.3.

**Mục tiêu chưa đo:** thời gian thiết lập kết nối giữa hai mạng khác nhau (Wi-Fi ↔ Wi-Fi, 4G ↔ Wi-Fi); tỉ lệ kết nối trực tiếp / qua relay / thất bại trên nhiều kịch bản mạng (mục F.5.3).

## F.2 Bảo mật

- **Mã hoá nội dung:** AES-256-GCM cho mọi `Envelope` (mục E.3.4). Gói duy nhất không mã hoá là public key ECDH ban đầu.
- **Trao khoá:** ECDH P-256 + HKDF-SHA256, khoá riêng mỗi chiều, cặp khoá ECDH sinh mới cho mỗi kết nối.
- **Xác thực danh tính:** ECDSA gắn với phiên ECDH (mục E.5). Giới hạn: TOFU ở lần gặp đầu (rủi ro J-1).
- **Signaling server:** không đọc payload. Các biện pháp gia cố sau đợt kiểm định 22/11/2026 (mục J.3):
  - Xoá `authToken` trước khi relay.
  - Chỉ relay từ kết nối đang thực sự ở trong phòng.
  - Gửi không chặn luồng (`ConcurrentWebSocketSessionDecorator`, 10 s / 512 KB).
  - Phải `JOIN` trong 30 s; tối đa 32 kết nối mỗi IP.
  - Giới hạn độ dài trường `JOIN`; tối đa 5.000 phòng.
  - `RequestRateLimiter` cho `/auth/*` (10 lần/phút) và `/turn-credentials` (20 lần/phút).
- **Vận chuyển tới server:** TLS qua Caddy (`wss://`, `https://`); server chỉ nghe `127.0.0.1`.
- **Tài khoản:** BCrypt; token 32 byte ngẫu nhiên, chỉ lưu SHA-256, hạn 12 giờ; nút "Đăng xuất" ở sảnh thu hồi token trên server rồi về màn hình đăng nhập; SQL tham số hoá.
- **Lưu cục bộ:** khoá danh tính và lịch sử mã hoá DPAPI trên Windows; log không chứa bí mật theo mặc định.

## F.3 Khả năng mở rộng và giới hạn quy mô

Mesh đầy đủ: băng thông và CPU mỗi máy tăng tuyến tính theo số người còn lại trong phòng. Server giới hạn 8 người mỗi phòng và 5.000 phòng. Khuyến nghị demo 2–6 người mỗi phòng.

## F.4 Độ tin cậy và khả năng phục hồi

- Ephemeral là hành vi đúng: server restart hoặc rời phòng làm mất trạng thái phòng.
- Server tự phát hiện WebSocket đóng đột ngột và báo `PEER_LEFT`.
- Client tự kết nối lại signaling; các kết nối P2P đã có vẫn chạy khi server tạm mất.
- UDP không đảm bảo: tin nhắn, lịch sử và file có ACK + gửi lại ở tầng ứng dụng; thứ tự tin chữ chưa được đảm bảo tuyệt đối.

## F.5 Kiểm thử

### F.5.1 Test tự động

Lần chạy gần nhất: **22/11/2026, commit `a0ee278`, 283 test, 0 lỗi, 3 bỏ qua**. Chạy trong **Docker cô lập** (image `maven:3.9.9-eclipse-temurin-17`, mạng nội bộ không ra được Internet, Maven offline, kho thư viện gắn chỉ-đọc).

| Module | Số test | Nội dung chính |
|---|---|---|
| `crypto` | 42 | Mã hoá khoá danh tính khi lưu (version 2, tự chuyển file cũ, không ghi đè khi sai khoá), sao lưu danh tính, ECDH, AES-GCM, HKDF (vector RFC 5869), khoá riêng mỗi chiều, chữ ký danh tính gắn ECDH (kịch bản MITM), khoá phòng có mật khẩu |
| `signaling-server` | 32 | Cấp TURN tạm thời (HMAC-SHA1 theo vector RFC 2202, giới hạn theo IP), chuyển tiếp không đọc nội dung, xử lý lỗi, danh sách phòng không lộ thẻ, `JOIN_REJECTED` từng lý do, giới hạn thử sai mật khẩu. **Mới 22/11:** không chuyển tiếp token; người đã rời phòng không chuyển tiếp được; từ chối trường `JOIN` quá dài; bộ giới hạn tốc độ tự dọn và gộp IPv6 /64 |
| `p2p-core` | 121 (1 bỏ qua: mesh 8 người, bật bằng cờ) | Mesh 3 người, mất gói, chống replay (gói cũ sau 2000 gói, gói giả, reflection), rò rỉ cổng, kết nối lại, TURN dự phòng, chia / ghép mảnh UDP, truyền file tin cậy và gửi lại nhanh, 1 MB qua kênh mã hoá trên mạng giả lập xấu. **Mới 22/11:** thay kết nối cùng `peerId` phải phát `onPeerLeft` |
| `client-javafx` | 88 (2 bỏ qua: benchmark; DPAPI chỉ chạy trên Windows) | Gửi lại tin, trạng thái giao tin và đã xem, cảm xúc, trả lời, sửa / xoá, nhận diện ảnh, DPAPI, vân tay tin cậy, đang gõ, Markdown, cài đặt, lịch sử, quy tắc sảnh chờ, thông báo khi bị từ chối, điều phối truyền file (16 test), 2 test tích hợp nhiều client thật. **Mới 22/11:** Markdown với chuỗi xấu 600.000 ký tự xong dưới 5 giây |

**Test cũ được chỉnh theo hành vi mới (22/11):**
- `SignalingServerContentOpacityTest`: người gửi phải thật sự `JOIN` phòng.
- `WebSocketSignalingClientCapacityTest`: 100 kết nối từ cùng localhost, nên nới giới hạn kết nối mỗi IP.

**Chạy test trong Docker:**
```bash
docker network create --internal chatp2p-isolated   # mạng nội bộ, không ra Internet
docker run --rm --network chatp2p-isolated -v <bản sao mã nguồn>:/work -v <~/.m2/repository>:/m2:ro -w /work \
  maven:3.9.9-eclipse-temurin-17 mvn -o -B -Dmaven.repo.local=/m2 -Djavafx.platform=win \
  -pl common,crypto,signaling-server,p2p-core,client-javafx test
```
Không dùng `--network none`: khi đó chỉ còn địa chỉ loopback, mà ice4j bỏ qua loopback nên test ICE không kết nối được. `-Djavafx.platform=win` vì kho Maven trên máy phát triển chỉ có jar JavaFX bản Windows.

**Lưu ý build:** `mvn install` (khác `mvn test`) lỗi biên dịch ở `P2pChatSessionIntegrationTest`, vì test cần lớp của `signaling-server` mà jar Spring Boot đã đóng gói lại không dùng làm thư viện được. Dùng `mvn test` cho kiểm thử, `mvn -Dmaven.test.skip=true install` khi cần cài.

Một lần chạy toàn bộ, `PeerConnectionBurstThroughputTest` fail đúng lúc máy đang tải nặng; chạy lại thì pass. Test này nhạy với thời gian chờ 5 s.

### F.5.2 Test tích hợp

- Đã có: 2 `RoomSession` thật qua ICE thật và signaling-server thật (`RoomSessionRealSignalingServerTest`); mesh 3 người; 2 test tích hợp nhiều `P2pChatSession`.
- Còn thiếu: test tự động cho các luồng giao diện đã sửa ngày 22/11 (khoá ô server theo tài khoản, sửa / xoá theo tác giả, lịch sử mạo danh chính mình, giữ vân tay khi chọn "Không khớp"). Hiện kiểm tra bằng tay.

### F.5.3 Kiểm thử thủ công và đo mạng

- **20/11/2026, server thật `wss://chat.zingme.fun`:** đạt. Đã thử chat nhóm / riêng, phòng có mật khẩu (kể cả nhập sai), gửi / nhận / từ chối / huỷ file, trạng thái giao tin, so vân tay, kết nối qua TLS. Giao diện còn được kiểm tra bằng cách dựng màn hình với dữ liệu giả và chụp ảnh.
- **20/11/2026, đo xuyên NAT và TURN thật:** xem mục I.3.
- **22/11/2026, sau khi deploy server mới:** service khởi động bình thường, `/turn-credentials` trả 200 qua Caddy. **Chưa thử lại bằng tay với hai client bản mới.**
- **Cần đo thêm:** hai máy khác mạng (Wi-Fi ↔ Wi-Fi, 4G ↔ Wi-Fi, 4G ↔ 4G), mạng chặn UDP; ghi tỉ lệ đi thẳng / relay / thất bại để đưa vào chương "Kiểm thử và đánh giá".

### F.5.4 Kiểm thử bảo mật

- Test tự động: chống replay, reflection, MITM tráo khoá ECDH, OFFER trùng / ghi đè kết nối, candidate ICE hỏng, server không đọc payload, và các test hồi quy của đợt kiểm định 22/11.
- **Kiểm định mã nguồn 22/11/2026:** rà soát theo từng ranh giới tin cậy, mỗi vấn đề được hai lượt kiểm chứng độc lập; 13 vấn đề đã sửa (mục J.3).
- Nên làm thêm: bắt gói bằng Wireshark giữa 2 client để xác nhận payload UDP là ciphertext; xác nhận log server không chứa nội dung.

---

# PHẦN G — CẤU HÌNH, BUILD, TRIỂN KHAI VÀ VẬN HÀNH

## G.1 Yêu cầu môi trường

- JDK 17+ (code biên dịch `--release 17`; VPS chạy OpenJDK 21).
- Maven 3.9+ (máy phát triển dùng Maven kèm IntelliJ; VPS có Maven 3.6.3, vẫn build được server).
- Docker (MySQL trên VPS; chạy test cô lập trên máy phát triển).

## G.2 Build và lệnh

```bash
mvn -q -DskipTests package                                   # build toàn bộ
mvn test                                                     # toàn bộ test (chạy từ gốc repo)
mvn -q -pl signaling-server -am clean package -DskipTests    # chỉ build jar server
java -jar signaling-server/target/signaling-server.jar       # chạy server (cần biến môi trường DB)
mvn -pl client-javafx javafx:run                             # chạy client
powershell -ExecutionPolicy Bypass -File packaging\build-windows.ps1   # đóng gói ChatP2P.exe + zip (mục I.16)
```

## G.3 Cấu hình `signaling-server`

`signaling-server/src/main/resources/application.yml` đọc cấu hình từ biến môi trường:

| Biến môi trường | Mặc định | Ý nghĩa |
|---|---|---|
| `CHAT_P2P_SERVER_ADDRESS` | `0.0.0.0` | Địa chỉ nghe. Trên VPS đặt `127.0.0.1` để chỉ Caddy gọi vào |
| `CHAT_P2P_DB_URL`, `CHAT_P2P_DB_USERNAME`, `CHAT_P2P_DB_PASSWORD` | *(bắt buộc)* | Kết nối MySQL, không có giá trị mặc định |
| `CHAT_P2P_AUTH_REQUIRED` | `true` | `false` cho phép khách vào phòng không cần đăng nhập. Production đặt `false` để giữ chế độ "Khách tự do" |
| `CHAT_P2P_TURN_SECRET`, `CHAT_P2P_TURN_HOST`, `CHAT_P2P_TURN_PORT` (3478), `CHAT_P2P_TURN_TTL_SECONDS` (21600) | trống | Cấp TURN tạm thời (coturn `use-auth-secret`). Để trống secret / host thì không cấp |
| `CHAT_P2P_WS_MAX_CONNECTIONS_PER_ADDRESS` | `32` | Số kết nối WebSocket đồng thời tối đa mỗi IP (IPv6 gộp /64) |

- `server.forward-headers-strategy: native`: đọc IP thật của client từ `X-Forwarded-For` khi request đến từ địa chỉ nội bộ. **Bắt buộc đúng** thì mọi giới hạn theo IP mới hoạt động đúng (thử sai mật khẩu, 32 kết nối, 10 lần `/auth`, 20 lần `/turn-credentials`). Caddy gửi header này mặc định.
- Giới hạn cố định trong code: `JOIN` trong 30 s; tên phòng ≤ 200 ký tự, `peerId` ≤ 100, tên khách ≤ 64; tối đa 5.000 phòng, 8 người mỗi phòng; hàng đợi gửi mỗi kết nối 10 s / 512 KB.
- Endpoint WebSocket `/ws` cho phép mọi origin. Chấp nhận được vì xác thực dùng token trong bản tin `JOIN`, không dùng cookie.
- MySQL bằng Docker: `docker-compose.yml` chỉ bind `127.0.0.1` và bắt buộc đặt `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD` (sao chép từ `.env.example`).

## G.4 Cấu hình `client-javafx`

- **Server:** mặc định `wss://chat.zingme.fun`, sửa được ở "tuỳ chọn kết nối" của màn hình đăng nhập và ở sảnh (chế độ khách). *(sửa 22/11, vấn đề 3)* Ở chế độ tài khoản, ô server ở sảnh bị **khoá vào server đã đăng nhập**; muốn đổi server thì bấm "Đăng xuất" (thêm ngày 22/11: gọi `AuthClient.logout` để server thu hồi token) rồi đăng nhập lại. `AuthClient` tự đổi `wss://` thành `https://` cho `/auth/*`, `/rooms`, `/turn-credentials`.
- **STUN:** `stun.l.google.com:19302`, cố định trong `P2pChatSession`.
- **TURN:** mặc định tự xin tài khoản tạm thời từ server. Muốn dùng TURN cố định (để test), đặt đủ 4 giá trị `CHAT_P2P_TURN_HOST/PORT/USERNAME/PASSWORD`, qua system property, biến môi trường hoặc `~/.chat-p2p-java/client.properties` (`chat.p2p.turn.*`).
- **Log bí mật** (chỉ để test): `CHAT_P2P_LOG_SECRETS=true` (mục I.15).

## G.5 Vấn đề đã gặp và cách khắc phục

- **Đường dẫn có dấu tiếng Việt** (`...\Máy tính\...`) làm `mvn spring-boot:run` báo `Could not find or load main class` trên Windows: plugin ghi classpath ra file tạm bị lệch encoding. **Cách khắc phục:** `mvn package` rồi `java -jar`.
- **`mvn install` lỗi** ở một test tích hợp: xem mục F.5.1.
- **OneDrive / antivirus khoá file** khi nén bản đóng gói: script nén bằng `tar.exe` và thử lại vài lần (mục I.16).

## G.6 Triển khai lên VPS

**Hạ tầng hiện có** (mục C.3): Caddy (`chat.zingme.fun` → `127.0.0.1:8080`), service systemd `chat-p2p`, MySQL trong Docker, coturn UDP 3478. Biến môi trường của server đặt trong `/etc/systemd/system/chat-p2p.service.d/override.conf`.

**Quy trình deploy bản mới** (đã dùng ngày 22/11/2026):
```bash
cd /root/Desktop_app_chat_no_server
cp signaling-server/target/signaling-server.jar /root/signaling-server.jar.bak-$(date +%F)   # sao lưu
git pull --ff-only
mvn -q -pl signaling-server -am clean package -DskipTests
systemctl restart chat-p2p
systemctl status chat-p2p --no-pager | head -12             # active (running)
journalctl -u chat-p2p -n 30 --no-pager                      # "Started SignalingServerApplication"
curl -s -o /dev/null -w "%{http_code}\n" https://chat.zingme.fun/turn-credentials   # 200
```
**Quay lại bản cũ:** chép file `.bak` về `signaling-server/target/signaling-server.jar` rồi `systemctl restart chat-p2p`. Khi restart, log có dòng `status=143` của tiến trình cũ; đó là mã thoát bình thường khi nhận SIGTERM.

**Mạng:** mở UDP 3478 (TURN) và UDP 10000–11000 (ICE) trên firewall VPS và máy client. Cổng 8080 không mở ra Internet.

---

# PHẦN H — XỬ LÝ LỖI VÀ LOGGING

## H.1 Nguyên tắc xử lý lỗi

1. Lỗi mạng không bao giờ làm sập ứng dụng JavaFX. Mọi thao tác mạng được bọc xử lý lỗi; người dùng thấy thông báo tiếng Việt, không phải stack trace.
2. Lỗi cục bộ không lan: một gói hỏng, một peer lỗi hay một handler ném ngoại lệ không được làm hỏng phần còn lại. Ví dụ: `broadcastToOthers` ở server, `RoomSession.dispatchEnvelope`, vòng nhận của `P2pDataChannel`.
3. Lỗi giải mã (sai khoá, dữ liệu bị sửa) là tín hiệu bất thường: gói bị bỏ, kết nối không đóng.
4. Ở signaling server, bản tin JSON hỏng, thiếu trường hay không tìm thấy peer đích chỉ được log `WARN` rồi bỏ qua, không đóng kết nối của người gửi và không ảnh hưởng người khác.

## H.2 Logging

- **`signaling-server`:** SLF4J/Logback của Spring Boot. `INFO` khi peer vào / rời phòng, `WARN` khi từ chối hoặc relay thất bại. **Không log payload.** Mật khẩu và token chỉ hiện khi bật `CHAT_P2P_LOG_SECRETS`.
- **`client-javafx`:** `java.util.logging` (`ConsoleLogging`), ghi ra terminal và `~/.chat-p2p-java/logs/client-0.log` (xoay vòng 5 file × 5 MB). Có 3 mức: bình thường / chi tiết / bí mật (mục I.15). Nội dung tin nhắn, mật khẩu, token, khoá phiên **chỉ** hiện ở mức bí mật, kèm cảnh báo ở dòng đầu log.

## H.3 Các kịch bản lỗi đã xử lý

| Kịch bản | Xử lý |
|---|---|
| Mất WebSocket tới signaling server | Tự kết nối lại (1 s … 30 s), gửi lại `JOIN`; kết nối P2P vẫn chạy |
| ICE thất bại (kể cả qua TURN) | Hết thời gian chờ thì báo "Không kết nối được với X"; không thử lại vô hạn |
| Mất gói UDP | ACK + gửi lại cho tin nhắn (6 lần × 500 ms), lịch sử, file (cửa sổ trượt, gửi lại nhanh) |
| Peer rời phòng khi đang gửi file | Huỷ truyền, xoá file tạm `.part`, thẻ file chuyển sang thất bại |
| Giải mã `Envelope` thất bại | Bỏ gói, không đóng kết nối |
| Server từ chối `JOIN` | `JOIN_REJECTED` kèm lý do, client về sảnh và hiện lý do (mục I.2) |
| Người nhận quá chậm, không đọc socket *(sửa 22/11, vấn đề 9)* | Hàng đợi gửi riêng mỗi kết nối; quá 10 s / 512 KB thì đóng kết nối đó |

---

# PHẦN I — CHỨC NĂNG ĐÃ HOÀN THÀNH

*Chuyển từ `Bao-cao-chuc-nang-da-lam.md` (cập nhật lần 5, 22/11/2026). Mỗi chức năng gồm: **luồng hoạt động chính**; **lỗi có thể xảy ra và cách xử lý** (cột TT: ✅ đã xử lý · ⚠️ một phần · ❌ chưa); **kiểm thử**. Kiến trúc chung ở Phần C, thiết kế ở Phần E, tổng hợp test ở mục F.5.*

## I.1 Tài khoản và chế độ khách

**Mô tả:** người dùng chọn **Khách tự do** (không cần tài khoản) hoặc **Đăng nhập / Đăng ký**. Tài khoản lưu trong MySQL trên server; mật khẩu băm bằng BCrypt; server chỉ lưu bản băm SHA-256 của token phiên, không lưu token gốc.

**Luồng hoạt động chính**
1. Người dùng nhập tên tài khoản và mật khẩu, bấm Đăng ký hoặc Đăng nhập.
2. Client gọi REST `/auth/register` hoặc `/auth/login`, gửi kèm public key danh tính của máy.
3. Server kiểm tra dữ liệu và mật khẩu, tạo token phiên ngẫu nhiên 32 byte (hiệu lực 12 giờ), trả token về client.
4. Khi vào phòng, client gửi token trong bản tin `JOIN`. Server kiểm tra token và dùng tên tài khoản làm tên hiển thị.
5. Người đã đăng nhập xem được danh sách phòng đang hoạt động (`/rooms`).

**Lỗi có thể xảy ra và cách xử lý**

| Tình huống | Hệ thống xử lý | TT | Hướng cải thiện |
|---|---|---|---|
| Sai tên đăng nhập hoặc mật khẩu | Server trả lỗi, giao diện hiện "Tên tài khoản hoặc mật khẩu không đúng" (không tiết lộ tài khoản có tồn tại hay không) | ✅ | |
| Tên tài khoản đã tồn tại | Hiện "Tên tài khoản đã tồn tại" | ✅ | |
| Tên hoặc mật khẩu không hợp lệ | Hiện yêu cầu: tên 3–64 ký tự (chữ, số, `_`), mật khẩu 8–200 ký tự | ✅ | |
| Đăng nhập từ máy có khoá danh tính khác với khoá đã gắn với tài khoản | Từ chối: "Khóa identity không khớp tài khoản này" | ✅ | Thêm chức năng chuyển khoá sang máy mới |
| Không kết nối được tới server | Hiện "Không thể kết nối auth server" | ✅ | |
| Người ngoài truy cập thẳng cơ sở dữ liệu tài khoản | MySQL trên VPS chỉ mở ở `127.0.0.1` (không ra Internet); server không còn mật khẩu DB mặc định, bắt buộc đặt qua biến môi trường | ✅ | |
| Token hết hạn (sau 12 giờ) hoặc không hợp lệ khi vào phòng | Server từ chối và gửi `JOIN_REJECTED(AUTH_EXPIRED / AUTH_REQUIRED)`; client báo "Phiên đăng nhập đã hết hạn" và chuyển sang màn hình đăng nhập | ✅ | |
| Token và mật khẩu bị nghe lén trên đường tới server | Mọi kết nối tới server đi qua **TLS**: `wss://chat.zingme.fun` (signaling) và `https://chat.zingme.fun` (đăng nhập, danh sách phòng). Caddy trên VPS giữ chứng chỉ Let's Encrypt (tự gia hạn) và chuyển tiếp vào server ở `127.0.0.1:8080`; cổng 8080 không mở ra Internet | ✅ | Client vẫn cho phép gõ `ws://` (không mã hoá); nên chặn trừ khi là localhost |
| Người cùng phòng lấy được token đăng nhập của mình | *(sửa 22/11, vấn đề 2)* Trước đây server chuyển nguyên bản tin OFFER/ANSWER/ICE kèm `authToken` của người gửi sang người nhận. Nay server **xoá token trước khi chuyển tiếp** | ✅ | |
| Token gửi nhầm tới server khác server đã đăng nhập | *(sửa 22/11, vấn đề 3)* Trước đây màn hình sảnh tự đặt lại ô server về mặc định và gửi token tới đó. Nay token **gắn với đúng server đã đăng nhập**: ô server ở sảnh bị khoá ở chế độ tài khoản; muốn đổi server phải đăng xuất rồi đăng nhập lại | ✅ | |
| Dò mật khẩu hoặc tạo tài khoản hàng loạt | *(sửa 22/11, vấn đề 11)* Tối đa **10 lần đăng nhập/đăng ký mỗi phút** cho một địa chỉ IP (IPv6 gộp theo dải /64); vượt quá thì trả mã 429 | ✅ | |
| Bảng phiên đăng nhập trong database phình mãi | *(sửa 22/11, vấn đề 11)* Mỗi lần đăng nhập, server xoá các phiên đã hết hạn hoặc đã đăng xuất của tài khoản đó | ✅ | |
| Không đăng xuất được ở chế độ tài khoản | *(thêm 22/11)* Nút "Đăng xuất" ở thẻ tên trên màn hình sảnh: bỏ token trên máy, về màn hình đăng nhập, gọi `POST /auth/logout` để server thu hồi token (mất mạng thì token vẫn tự hết hạn sau 12 giờ) | ✅ | |
| Khách đặt trùng tên một tài khoản, người trong phòng không phân biệt được | *(thêm 22/11)* `PEER_LIST` / `PEER_JOINED` mang thêm `authenticated` và `identityKey` (khoá đã đăng ký của tài khoản); server xoá hai trường này khỏi bản tin relay. Danh sách người hiện nhãn "Tài khoản" / "Khách", khách trùng tên tài khoản có nhãn đỏ và cảnh báo. Client tự đối chiếu khoá trong `PEER_IDENTITY` với khoá đã đăng ký, không khớp thì không coi là đã xác thực | ✅ | Tương thích client cũ: client mới gửi `protocolVersion = 2` trong `JOIN`, server chỉ gửi trường mới cho client đó và không ghi trường `null`; client cũ vẫn nhận bản tin như trước (nhưng không có nhãn) |

---

## I.2 Sảnh chờ: tạo phòng, danh sách phòng, vào phòng

**Mô tả:** phòng được xác định bằng tên; mỗi phòng tối đa 8 người, vì kiến trúc mesh có chi phí tăng theo bình phương số người. Server không có bước "tạo phòng" riêng: phòng tự sinh khi có người đầu tiên vào, và tự mất khi người cuối cùng rời đi. Ở sảnh chờ có 3 cách vào phòng:

| Cách | Dành cho | Các bước |
|---|---|---|
| **Tạo phòng** | Người mở phòng mới | Bấm **Tạo phòng** → hộp **"Thông tin phòng"**: tên phòng (có sẵn tên ngẫu nhiên, nút đổi tên, nút sao chép), ô **"Đặt mật khẩu cho phòng"** (hiện thêm ô mật khẩu và ô nhập lại) → **Tạo phòng** |
| **Chọn trong danh sách** | Người đã đăng nhập | Danh sách "Phòng đang hoạt động" hiện tên phòng, số người, biểu tượng 🔒 (có mật khẩu) hoặc `#` (công khai). Bấm một phòng → **hộp xác nhận** → nếu phòng có mật khẩu thì **hộp nhập mật khẩu** → vào |
| **Vào phòng bằng tên** | Khách (không có danh sách), hoặc phòng bạn bè gửi tên qua kênh khác | Hộp nhập tên phòng và mật khẩu (nếu có) → vào |

**Luồng hoạt động chính (sau khi chọn xong phòng)**
1. Client kiểm tra tên hiển thị, địa chỉ server, trạng thái đăng nhập (ở chế độ tài khoản).
2. Client tạo phiên trên **luồng nền**, vì phòng có mật khẩu cần chạy PBKDF2 khoảng 0,1–0,3 s; giao diện hiện "Đang vào phòng …", không bị đứng.
3. Client mở WebSocket tới signaling server và gửi `JOIN {roomId, peerId, userName}`. `peerId` là một UUID mới cho mỗi lần vào phòng; `roomId` là tên phòng, hoặc `tên~thẻ` với phòng có mật khẩu (mục I.12).
4. Server trả về `PEER_LIST`, là danh sách những người đã ở trong phòng, đồng thời gửi `PEER_JOINED` cho những người đó.
5. Người mới vào chủ động bắt đầu kết nối P2P với từng người trong `PEER_LIST` (mục I.3).

**Khi server từ chối (thay cho bước 4):** server gửi `JOIN_REJECTED` kèm lý do. Client rời màn hình phòng, quay về sảnh và hiện lý do bằng tiếng Việt:

| Lý do | Người dùng thấy | Bước tiếp theo |
|---|---|---|
| `WRONG_PASSWORD` | "Sai mật khẩu phòng …" | Hộp nhập mật khẩu của đúng phòng đó tự mở lại |
| `RATE_LIMITED` | "Bạn đã nhập sai mật khẩu quá nhiều lần…" | Thử lại sau 1 phút |
| `ROOM_FULL` | "Phòng … đã đủ người (tối đa 8 người)" | Chọn phòng khác |
| `AUTH_EXPIRED`, `AUTH_REQUIRED` | "Phiên đăng nhập đã hết hạn…" | Chế độ tài khoản: chuyển sang màn hình đăng nhập |
| `DUPLICATE_PEER` | "Không vào được phòng … (trùng mã phiên)" | Thử vào lại |
| `INVALID_JOIN` | "Tên phòng hoặc tên hiển thị không hợp lệ (có thể quá dài)" | Đặt tên ngắn hơn |
| `SERVER_BUSY` *(mới, 22/11)* | "Server đang quá tải, vui lòng thử lại sau ít phút" | Thử lại sau |

**Danh sách phòng (`/rooms`, chỉ cho người đã đăng nhập):** server trả về tên phòng, số người và cờ `passwordProtected`. Với phòng có mật khẩu, server **cắt bỏ thẻ** (`tên~thẻ` → `tên`), nên người xem danh sách không có gì để dò mật khẩu. Client vẫn hiểu định dạng cũ `tên~thẻ` của server chưa cập nhật.

**Lỗi có thể xảy ra và cách xử lý**

| Tình huống | Hệ thống xử lý | TT | Hướng cải thiện |
|---|---|---|---|
| Nhập sai trong hộp thoại (tên rỗng, tên có `~`, mật khẩu dưới 6 ký tự, nhập lại không khớp, chưa nhập mật khẩu) | Hộp thoại **giữ nguyên** và hiện lỗi ngay bên dưới, không đóng mất dữ liệu đã nhập | ✅ | |
| Gõ thừa dấu cách ở đầu/cuối mật khẩu hoặc tên phòng | Tự cắt khoảng trắng hai đầu, ở mọi phía, nên vẫn khớp nhau | ✅ | |
| Tạo phòng trùng tên phòng công khai đang có người | Hộp "Thông tin phòng" cảnh báo "bạn sẽ vào chung phòng với những người đang ở đó" | ✅ | |
| Tạo phòng có mật khẩu trùng tên phòng có mật khẩu đang có | Cảnh báo "nếu mật khẩu của bạn khác, bạn sẽ bị báo sai mật khẩu — hãy chọn tên khác"; nếu vẫn vào thì server trả `WRONG_PASSWORD` | ✅ | |
| Nội dung hộp thoại cao thêm (hiện ô mật khẩu, hiện dòng lỗi) | Cửa sổ hộp thoại tự giãn theo, hàng nút luôn hiện | ✅ | |
| Ở một mình trong phòng quá lâu (sai tên phòng, hoặc bạn bè chưa vào) | Sau 20 s hiện gợi ý kiểm tra lại tên phòng (phân biệt hoa thường). Sai mật khẩu thì đã được báo ngay | ⚠️ | |
| Tên phòng phân biệt hoa thường (`Phong-A` khác `phong-a`) | Ghi rõ trong gợi ý ở sảnh và trong hộp thoại | ⚠️ | Chuẩn hoá tên phòng về chữ thường |
| Không kết nối được tới server khi vào phòng | Báo lỗi lên giao diện: "Không thể kết nối tới signaling server", mở lại các nút ở sảnh | ✅ | |
| Mất kết nối với server khi đang trong phòng | Client tự kết nối lại với thời gian chờ tăng dần (1 s, 2 s, 4 s… tối đa 30 s) và tự gửi lại `JOIN`. Các kết nối P2P đã có **vẫn hoạt động** vì không đi qua server | ✅ | Hiện trạng thái "Đang kết nối lại…" trên giao diện |
| Phòng đã đủ 8 người, hoặc trùng `peerId` | Server gửi `JOIN_REJECTED(ROOM_FULL / DUPLICATE_PEER)`; client quay về sảnh và hiện lý do | ✅ | |
| Bản tin JSON hỏng hoặc thiếu trường | Server bỏ qua bản tin đó, không ảnh hưởng người khác | ✅ | |
| Một client cố giả mạo người khác khi gửi OFFER/ANSWER | Server chỉ chuyển tiếp nếu người gửi đã `JOIN` đúng phòng và đúng `peerId`, và **hiện vẫn đang ở trong phòng** (kiểm tra với danh sách phòng của server) | ✅ | |
| Người đã rời phòng (hoặc JOIN sang phòng khác bị từ chối) vẫn gửi OFFER/ICE vào phòng cũ | *(sửa 22/11, vấn đề 4)* Trước đây server quên xoá thông tin phòng gắn với kết nối nên vẫn chuyển tiếp. Nay xoá ngay khi rời phòng | ✅ | |
| Mở rất nhiều kết nối WebSocket rồi để im, chiếm hết chỗ của server | *(sửa 22/11, vấn đề 10)* Kết nối phải gửi `JOIN` trong **30 giây**, nếu không bị đóng; mỗi địa chỉ IP tối đa **32 kết nối** đồng thời (đổi được bằng `CHAT_P2P_WS_MAX_CONNECTIONS_PER_ADDRESS`) | ✅ | |
| Tạo hàng nghìn phòng với tên rất dài để làm đầy bộ nhớ server | *(sửa 22/11, vấn đề 12)* Tên phòng tối đa 200 ký tự, `peerId` 100, tên khách 64 (vượt thì `INVALID_JOIN`); tối đa **5.000 phòng** cùng lúc (vượt thì `SERVER_BUSY`) | ✅ | |
| Một người cố ý không đọc dữ liệu từ server làm chậm người khác | *(sửa 22/11, vấn đề 9)* Mỗi kết nối có hàng đợi gửi riêng (giới hạn 10 giây và 512 KB); kết nối nhận quá chậm bị đóng thay vì làm nghẽn luồng xử lý của người khác | ✅ | |
| Server sập | Không ai vào phòng mới được; các cuộc chat đang diễn ra vẫn tiếp tục | ⚠️ | Chấp nhận ở quy mô đồ án (rủi ro J-5) |

---

## I.3 Kết nối P2P trực tiếp (ICE/STUN, TURN dự phòng)

**Mô tả:** hai máy tìm đường kết nối UDP trực tiếp xuyên qua NAT/router bằng thư viện ice4j. STUN dùng để biết địa chỉ public; TURN dùng làm đường relay dự phòng khi không đi thẳng được.

**Luồng hoạt động chính**
1. Bên vào sau tạo **OFFER**, gồm danh sách địa chỉ có thể kết nối (ICE candidate), và gửi qua server tới bên kia.
2. Bên kia tạo **ANSWER**, gồm candidate của mình, và gửi ngược lại.
3. Hai bên thử ghép từng cặp địa chỉ (connectivity check) và chọn cặp kết nối được tốt nhất: trực tiếp trước, TURN sau.
4. Khi thành công, mỗi bên có một kênh UDP riêng tới bên kia (`P2pDataChannel`). Từ đây server không còn tham gia.

**Lỗi có thể xảy ra và cách xử lý**

| Tình huống | Hệ thống xử lý | TT | Hướng cải thiện |
|---|---|---|---|
| Hai mạng có NAT chặt (symmetric NAT), không đi thẳng được | Tự chuyển sang relay qua TURN nếu đã cấu hình TURN | ✅ | |
| Chưa cấu hình TURN mà đi thẳng cũng thất bại | Báo trên giao diện: "Không kết nối được với X: …". **Người dùng không phải cấu hình TURN**: khi vào phòng, client tự xin tài khoản TURN **tạm thời** (hết hạn sau 6 giờ) từ `https://chat.zingme.fun/turn-credentials` | ⚠️ | Hướng dẫn người dùng cấu hình TURN ngay trong thông báo lỗi |
| Người lạ lấy tài khoản TURN để dùng ké | Tài khoản chỉ có hạn 6 giờ (coturn kiểm tra bằng HMAC với khoá bí mật chỉ server và coturn biết, theo cơ chế "TURN REST API"); mỗi IP xin tối đa 20 lần/phút; không còn mật khẩu TURN cố định nào để lộ. *(sửa 22/11, vấn đề 13)* Bộ đếm theo IP nay tự dọn bộ nhớ, giới hạn tổng số IP theo dõi và gộp IPv6 theo dải /64, nên xoay vòng địa chỉ không làm phình bộ nhớ server và không được thêm lượt. Việc không bắt buộc đăng nhập để lấy tài khoản TURN là có chủ đích (ai cũng tự đăng ký được tài khoản); giới hạn lạm dụng nằm ở cấu hình quota của coturn | ✅ | Đặt `user-quota` / `denied-peer-ip` cho coturn |
| Tường lửa Windows chặn UDP | Lần đầu chạy, app xin quyền tạo luật tường lửa cho dải cổng UDP 10000–11000 | ✅ | |
| OFFER/ANSWER hỏng hoặc candidate sai định dạng | Chỉ huỷ kết nối với đúng người đó, những người khác không ảnh hưởng | ✅ | |
| Một người gửi OFFER liên tục (cố ý làm cạn cổng UDP) | Mỗi OFFER mới huỷ phiên ICE cũ của cùng người đó, không rò rỉ cổng | ✅ | Giới hạn tốc độ OFFER ở server |
| ICE hoàn tất **sau khi** người dùng đã rời phòng | Phát hiện và đóng ngay, không "hồi sinh" kết nối | ✅ | |
| Rời phòng mà cổng UDP không được giải phóng | Khi rời phòng, giải phóng toàn bộ phiên ICE, kể cả những phiên đã kết nối | ✅ | |
| NAT xoá ánh xạ cổng khi chat im lặng lâu | ice4j tự gửi gói STUN giữ kết nối (keepalive) trên cặp địa chỉ đã chọn | ✅ | |
| Hai bên **đều chỉ** đi được qua TURN (relay ↔ relay) | Đo thực tế: ICE thất bại (xem kết quả đo bên dưới). Thường không ảnh hưởng vì ICE vẫn còn cặp relay ↔ địa chỉ public của bên kia | ⚠️ | Kiểm tra log coturn (`403 Forbidden IP` khi chuyển tiếp giữa hai cổng relay của chính nó), mở `allowed-peer-ip` cho IP của VPS |
| Mạng chặn UDP ra ngoài (Wi-Fi trường học, công ty) | TURN hiện chỉ chạy UDP cổng 3478 nên **không kết nối được** | ❌ | Bật TURN qua TCP/TLS cổng 443 ở coturn và khai báo ở client |
| Giao diện không cho biết kết nối đi thẳng hay qua relay | Luôn hiện "trực tiếp" (`ConnectionKind.DIRECT` gán cứng trong `P2pChatSession`), dù `IceConnectionStats.usingRelay()` đã tính đúng | ❌ | Đưa `usingRelay` lên danh sách người trong phòng (biểu tượng "qua relay" đã có sẵn ở giao diện) |

**Kết quả đo xuyên NAT thực tế (20/11/2026)**

Đo trên máy phát triển dùng mạng 4G (điểm phát sóng iPhone, IP nội bộ `172.20.10.x`), với STUN công cộng và TURN thật của dự án (`165.101.47.83:3478`).

1. **Loại NAT: NAT đối xứng (symmetric NAT).** Từ **cùng một socket**, hỏi đồng thời 2 STUN server (`stun.l.google.com`, `stun.cloudflare.com`) nhận về **2 địa chỉ public khác nhau cả IP lẫn cổng** (vd `113.185.52.138:17210` và `113.185.47.243:36596`); lặp lại 3 lần đều như vậy. Đây là NAT cấp nhà mạng (CGNAT) điển hình của 4G: địa chỉ public phụ thuộc đích đến, nên hai máy cùng sau NAT loại này **không thể đục lỗ UDP cho nhau**.
2. **STUN:** lấy được địa chỉ public (candidate `srflx`).
3. **TURN:** `GET /turn-credentials` trả tài khoản tạm thời; cấp cổng relay thành công sau **221 ms**.
4. **Truyền dữ liệu qua relay, đầu-cuối:** một bên chỉ dùng candidate relay, bên kia chỉ dùng địa chỉ public, qua đúng các lớp như app thật (`IceP2pConnectionEstablisher` → `P2pDataChannel` → `PeerConnection` với ECDH + AES-256-GCM):

| Chỉ số | Kết quả |
|---|---|
| Thiết lập ICE | 354 ms, cả hai bên báo `usingRelay = true` |
| Trao khoá ECDH qua relay | Thành công |
| 200 tin × 1 KB, gửi đi rồi nhận lại (50 tin/giây) | 197/200 về đủ, **mất 1,5%** tính cả 2 chiều |
| Thời gian khứ hồi (RTT) | nhỏ nhất 58 ms · trung vị **86 ms** · p95 191 ms · lớn nhất 247 ms |

5. **Relay ↔ relay** (cả hai bên chỉ quảng bá candidate relay): ICE **thất bại** (xem bảng lỗi ở trên).

Trước lần đo này, test TURN tự động chỉ kiểm tra rằng một TURN **không tới được** không làm hỏng ICE; chưa có gì chứng minh dữ liệu thật sự đi qua relay. Nay đã chứng minh được với TURN thật.

**Khi nào đi thẳng, khi nào phải qua TURN** (ICE luôn thử theo thứ tự: IP nội bộ → địa chỉ public qua STUN → relay qua TURN):

| Bên A | Bên B | Kết quả | Căn cứ |
|---|---|---|---|
| Cùng LAN / Wi-Fi | Cùng LAN / Wi-Fi | Đi thẳng qua IP nội bộ | Test tự động: 2 đầu ICE trên **cùng một máy**, cùng cơ chế candidate `host`. **Chưa thử 2 máy thật** |
| Wi-Fi nhà | Wi-Fi nhà khác | Thường đi thẳng (đa số router gia đình là NAT "dễ") | Lý thuyết, **chưa thử** |
| 4G (NAT đối xứng) | Wi-Fi nhà | Đi thẳng nếu router bên kia dễ tính, nếu không thì qua TURN | Lý thuyết, **chưa thử** |
| 4G | 4G | **Bắt buộc qua TURN** | **Suy ra** từ loại NAT đã đo (bước đo 1 ở trên); **chưa thử với 2 máy 4G** |
| Bất kỳ | Mạng chặn UDP | **Không kết nối được** | **Suy ra** từ cấu hình (TURN chỉ có UDP); chưa thử trên mạng chặn UDP |

Đi qua TURN **vẫn là mã hoá đầu-cuối**: TURN chỉ chuyển tiếp gói UDP đã mã hoá, khoá AES chỉ hai máy biết, sửa gói thì AES-GCM phát hiện, và chữ ký `PEER_IDENTITY` gắn với khoá ECDH nên TURN không đóng vai kẻ đứng giữa được. TURN chỉ thấy **siêu dữ liệu**: IP hai bên, thời điểm, kích thước và số lượng gói. Qua TURN vẫn là mesh: mỗi cặp người có kết nối và khoá riêng, TURN không nhân bản tin như máy chủ trung gian.

**Cách kiểm tra một kết nối cụ thể:** trong `~/.chat-p2p-java/logs/client-0.log`, dòng `ICE selected pair` ghi loại candidate được chọn: `typ host` / `srflx` / `prflx` là đi thẳng (xuyên NAT thành công), `typ relay` là qua TURN.

**Chưa thử:** mọi kết nối giữa **hai máy thật** ở hai mạng khác nhau (Wi-Fi ↔ Wi-Fi, 4G ↔ Wi-Fi, 4G ↔ 4G). Các phép đo ở trên chạy trên một máy; các dòng "lý thuyết" / "suy ra" ở bảng là dự đoán, cần kiểm chứng bằng cách cho 2 máy vào cùng phòng và xem dòng `ICE selected pair` trong log.

---

## I.4 Trao khoá và mã hoá đầu-cuối

**Mô tả:** mọi dữ liệu qua kênh P2P đều được mã hoá bằng **AES-256-GCM**, với khoá phiên sinh bằng **ECDH** (đường cong secp256r1). Mỗi kết nối có cặp khoá ECDH riêng, sinh mới mỗi lần.

**Luồng hoạt động chính**
1. Ngay khi kênh UDP mở, mỗi bên gửi public key ECDH của mình. Đây là gói duy nhất không mã hoá, vì public key không cần giữ bí mật.
2. Mỗi bên tự tính bí mật chung ECDH, rồi dùng **HKDF-SHA256** (RFC 5869) tách ra **hai khoá AES-256 riêng cho hai chiều gửi**. Salt của HKDF gắn với đúng hai public key của phiên; khoá chiều A→B khác khoá chiều B→A.
3. Từ gói thứ hai, mọi dữ liệu đóng thành `Envelope` (JSON) và mã hoá AES-256-GCM. Nonce 12 byte gồm **số thứ tự gói 8 byte** (tăng dần từ 0) và 4 byte ngẫu nhiên, nên cặp (khoá, nonce) không bao giờ lặp lại, và số thứ tự được GCM xác thực luôn (sửa số thứ tự là giải mã thất bại). Kích thước gói không đổi so với trước.
4. Bên nhận giữ một **cửa sổ trượt 1024 số thứ tự** (cùng cách làm với DTLS / IPsec): gói trùng hoặc cũ hơn cửa sổ bị bỏ; gói đến sai thứ tự nhưng còn trong cửa sổ vẫn được nhận.

**Lỗi có thể xảy ra và cách xử lý**

| Tình huống | Hệ thống xử lý | TT | Hướng cải thiện |
|---|---|---|---|
| Gói public key ECDH bị mất (UDP không đảm bảo) | Tự gửi lại tối đa 5 lần, mỗi lần cách 300 ms | ✅ | |
| Dữ liệu bị sửa trên đường truyền, hoặc giải mã sai khoá | GCM phát hiện, gói bị bỏ qua, kết nối không bị sập | ✅ | |
| Kẻ tấn công bắt gói rồi gửi lại (replay), dù gói cũ đến đâu | Số thứ tự gói + cửa sổ trượt: gói đã nhận hoặc quá cũ bị bỏ | ✅ | |
| Kẻ tấn công gửi gói giả mang số thứ tự rất lớn để "đẩy" cửa sổ | Cửa sổ chỉ cập nhật **sau khi** gói giải mã và xác thực thành công, nên gói giả không làm hỏng cửa sổ | ✅ | |
| Bắt gói A gửi rồi gửi ngược lại cho chính A (reflection) | Hai chiều dùng hai khoá khác nhau nên giải mã thất bại | ✅ | |
| Kẻ đứng giữa tráo khoá ECDH khi trao khoá | Phát hiện ở bước xác thực danh tính (mục I.5), vì chữ ký gắn với khoá ECDH của phiên | ✅ | |
| Signaling server (bị chiếm) gửi OFFER thứ hai mang `peerId` của người đã xác thực, để thay kết nối bằng kết nối tới người lạ | *(sửa 22/11, vấn đề 1)* Trước đây kết nối mới **thừa hưởng** trạng thái "đã qua mật khẩu phòng" và "đã xác thực" của kết nối cũ, giao diện vẫn hiện "Đã xác thực". Nay khi kết nối bị thay, client coi như người đó đã rời phòng: kết nối mới phải làm lại `ROOM_PROOF` và `PEER_IDENTITY` từ đầu | ✅ | |

---

## I.5 Xác thực danh tính người trong phòng

**Mô tả:** mỗi máy có một cặp khoá danh tính **ECDSA** lâu dài, lưu ở `~/.chat-p2p-java/identity.properties`; trên Windows private key trong file được mã hoá bằng DPAPI. Sau khi trao khoá, hai bên chứng minh danh tính với nhau; giao diện hiện dấu "đã xác thực" và fingerprint để người dùng đối chiếu.

**Luồng hoạt động chính**
1. Mỗi bên gửi `PEER_IDENTITY`, gồm public key danh tính và chữ ký trên chuỗi `phòng | peerId | SHA-256(khoá ECDH của mình) | SHA-256(khoá ECDH của bên kia)`.
2. Bên nhận kiểm tra chữ ký bằng chính các khoá ECDH mà nó thấy trong phiên.
3. Hợp lệ thì đánh dấu người đó là **Đã xác thực** và hiện fingerprint.
4. **So vân tay và đánh dấu tin cậy:** bấm vào biểu tượng cạnh tên một người để mở hộp so vân tay (hai dãy 4×4 nhóm, đọc cho nhau qua điện thoại hoặc gặp trực tiếp). Bấm **Khớp** thì app ghi nhớ "tên → vân tay" trong `~/.chat-p2p-java/trusted-peers.properties`.
5. Lần sau gặp lại người cùng tên: vân tay khớp thì hiện biểu tượng **khiên** (đã tin cậy); vân tay **khác** thì hiện huy hiệu đỏ **"Khoá đã đổi"** và khung chat cảnh báo đừng gửi thông tin nhạy cảm cho tới khi so lại. App **không tự ghi nhớ** người gặp lần đầu, vì tên ở chế độ khách là tự đặt và hay trùng; tự ghi nhớ sẽ sinh cảnh báo giả.

**Lỗi có thể xảy ra và cách xử lý**

| Tình huống | Hệ thống xử lý | TT | Hướng cải thiện |
|---|---|---|---|
| Gói `PEER_IDENTITY` bị mất | Gửi lại sau 0,4 s, 1,2 s và 2,5 s | ✅ | |
| Chữ ký sai (giả mạo, hoặc bị tráo khoá ECDH) | Không đánh dấu xác thực; giao diện cảnh báo "Identity peer không hợp lệ"; người đó vẫn hiện "chưa xác thực" | ✅ | Tự động ngắt kết nối với người có chữ ký sai |
| Lần gặp đầu tiên với kẻ mạo danh có khoá riêng (TOFU) | Không phát hiện tự động được, vì không có cơ quan cấp chứng chỉ; người dùng so vân tay bằng hộp "Xác thực vân tay" | ⚠️ | |
| Kẻ mạo danh dùng tên của người đã được tin cậy | Vân tay khác nên hiện "Khoá đã đổi" và cảnh báo trong khung chat | ✅ | |
| So vân tay thấy không khớp | Đánh dấu người đó "Không khớp" và cảnh báo có thể có người xen giữa. *(sửa 22/11, vấn đề 7)* Nếu hộp so vân tay mở vì **khoá đã đổi**, vân tay cũ đã lưu được **giữ nguyên** (trước đây bị xoá, làm kẻ mạo danh lần sau không còn bị cảnh báo); chỉ xoá khi người dùng phủ nhận chính vân tay đang được tin cậy | ✅ | Tự động ngắt kết nối |
| File khoá danh tính bị hỏng, hoặc bị chép sang máy / tài khoản Windows khác | App dừng và báo rõ đường dẫn file, **không tự ghi đè** để tránh mất danh tính | ✅ | Thêm nút "tạo lại danh tính" có xác nhận |
| Kẻ khác đọc được file khoá danh tính trên máy | Trên Windows, private key được **mã hoá bằng DPAPI** (gắn với tài khoản Windows của người dùng; định dạng version 2). File cũ dạng thường tự được chuyển sang dạng mã hoá, danh tính giữ nguyên | ✅ | |

---

## I.6 Chat nhóm

**Mô tả:** tin nhắn gửi tới mọi người trong phòng. Mỗi tin đi thẳng tới từng người qua kênh P2P đã mã hoá (mesh), không qua server.

**Luồng hoạt động chính**
1. Người dùng gõ tin (Enter để gửi, Shift+Enter để xuống dòng); tin hiện ngay ở khung chat của mình.
2. Client gửi `MESSAGE {id, text, timeSent}` tới từng người trong phòng.
3. Người nhận hiển thị tin, trả `MESSAGE_ACK {id}`, và lưu tin vào lịch sử nhóm (phục vụ mục I.11).
4. Người gửi nhận ACK thì ngừng gửi lại tin đó.
5. **Trạng thái giao tin** hiện ngay dưới bong bóng tin của mình: biểu tượng đồng hồ "Đang gửi" → "Đã nhận" (tin riêng) hoặc "Đã nhận 1/3" → "Tất cả đã nhận" (tin nhóm). Hết lượt gửi lại, hoặc người nhận rời phòng trước khi xác nhận, thì hiện "Chưa tới nơi" màu đỏ. Phòng chưa có ai thì chỉ hiện giờ gửi.
6. **Đã xem:** khi người nhận đang mở đúng tab chứa tin **và** cửa sổ app đang được chọn, máy họ gửi `MESSAGE_READ`; tin của bạn chuyển sang biểu tượng con mắt "Đã xem" (tin riêng) hoặc "Đã xem 1/3" → "Tất cả đã xem" (tin nhóm). Tin đến lúc app bị thu nhỏ hoặc đang ở tab khác thì chỉ tính là đã xem khi người dùng quay lại.
7. **Trả lời:** chuột phải vào một tin → **Trả lời**; phía trên ô nhập hiện "Đang trả lời X: …" (bấm × để huỷ). Tin gửi đi kèm `replyToId`; bong bóng hiện khung trích dẫn tên và đoạn đầu của tin gốc.
8. **Thả cảm xúc:** chuột phải → chọn 👍 ❤️ 😂 😮 😢. Dưới bong bóng hiện các "chip" như "👍 2"; bấm vào chip để thả hoặc bỏ cảm xúc của mình. Chỉ nhận cảm xúc trong bộ cố định này, nên người khác không chèn được chữ tuỳ ý vào giao diện.
9. Chuột phải còn có **Sao chép** nội dung tin.

**Lỗi có thể xảy ra và cách xử lý**

| Tình huống | Hệ thống xử lý | TT | Hướng cải thiện |
|---|---|---|---|
| Tin bị mất trên đường truyền | Không nhận ACK thì gửi lại, tối đa 6 lần, mỗi lần cách 0,5 s | ✅ | |
| ACK bị mất nên người nhận nhận cùng một tin 2 lần | Người nhận loại bản trùng theo `id` của tin, vẫn trả ACK lại | ✅ | |
| Gửi lại 6 lần vẫn không có ACK | Giao diện cảnh báo "tin nhắn chưa tới nơi" cho đúng người đó, và tin đó hiện "Chưa tới nơi" | ✅ | |
| Gửi lỗi với một người trong phòng | Vẫn gửi bình thường cho những người còn lại | ✅ | |
| Người nhận rời phòng giữa lúc đang gửi | Huỷ việc gửi lại tới người đó, không báo lỗi thừa | ✅ | |
| Tin đến sai thứ tự (UDP có thể đảo gói) | Hiển thị theo thứ tự nhận | ⚠️ | Sắp xếp theo thời điểm gửi khi chênh lệch nhỏ |
| Tin quá dài | Kênh UDP tự chia mảnh tin tới 1 MB. *(sửa 22/11, vấn đề 8)* Người nhận **bỏ qua tin dài hơn 12.000 ký tự** (vẫn trả ACK để bên gửi không gửi lại); trước đây một tin rất dài được chế tạo đặc biệt có thể làm treo giao diện người nhận | ⚠️ | Giới hạn độ dài ô nhập ở bên gửi và báo rõ cho người dùng |
| Đồng hồ máy người gửi bị lệch về tương lai | Thời gian tin bị giới hạn không vượt quá thời điểm nhận | ✅ | |
| Một người gửi tin trùng `id` với tin của người khác để sửa / xoá tin đó | *(sửa 22/11, vấn đề 6)* Sửa và xoá tin giờ xác định theo **cặp (id, tác giả)**: chỉ tác giả thật sửa / xoá được đúng tin của mình, tin trùng id của người khác không bị ảnh hưởng | ✅ | |

---

## I.7 Chat riêng 1-1 trong phòng

**Mô tả:** trong phòng đông người, bấm biểu tượng chat cạnh tên một người để mở tab chat riêng với người đó. Tin chỉ gửi tới đúng người đó.

**Luồng hoạt động chính**
1. Người dùng bấm biểu tượng chat cạnh tên người nhận, một tab "DM: Tên" được mở.
2. Tin gửi trong tab này đi kèm đánh dấu tin riêng (namespace `dm`), **chỉ** tới kênh P2P của người nhận.
3. Người nhận thấy tin trong tab DM tương ứng. Nếu tab chưa có thì tự tạo; nếu người nhận đang gõ dở ở tab khác thì app **không** tự chuyển tab.
4. Tin riêng có ACK và gửi lại giống chat nhóm, và **không bao giờ** được đưa vào lịch sử gửi cho người mới vào phòng.
5. **Gửi file trong tab DM** (nút gửi tệp hoặc kéo thả) thì file chỉ gửi cho đúng người đó; thẻ file hiện trong tab DM của cả hai bên (mục I.14).

**Lỗi có thể xảy ra và cách xử lý**

| Tình huống | Hệ thống xử lý | TT | Hướng cải thiện |
|---|---|---|---|
| Người nhận đã rời phòng | Báo "Gửi tin thất bại: Người này chưa vào phòng hoặc đã rời phòng" | ✅ | Khoá ô nhập của tab DM khi người đó rời phòng |
| Mất gói, trùng gói, hết lượt gửi lại | Như mục I.6 | ✅ | |
| Người thứ ba muốn đọc tin riêng | Không thể: tin chỉ đi trên kênh mã hoá giữa hai người, server không chuyển tiếp nội dung | ✅ | |
| Muốn nhắn riêng cho người không ở cùng phòng | Chưa hỗ trợ (DM chỉ trong phạm vi phòng) | ❌ | Danh bạ và chat 1-1 theo tài khoản (ngoài phạm vi đề cương) |

---

## I.8 Trạng thái "đang gõ…"

**Luồng hoạt động chính**
1. Khi người dùng bắt đầu gõ, client gửi `TYPING_STATUS_CHANGE {typing: true}` tới khung chat đang mở (nhóm, hoặc người trong tab DM).
2. Nếu vẫn gõ tiếp, cứ 2,5 s nhắc lại một lần. Khi ngừng gõ 3 s, xoá hết chữ, gửi tin hoặc đổi tab thì gửi `typing: false`.
3. Bên nhận hiện "An đang gõ…", "An và Bình đang gõ…" hoặc "An, Bình và 2 người khác đang gõ…" ngay trên ô nhập.

**Lỗi có thể xảy ra và cách xử lý**

| Tình huống | Hệ thống xử lý | TT |
|---|---|---|
| Gói "ngừng gõ" bị mất, chữ "đang gõ" bị treo mãi | Bên nhận tự tắt sau 6 s nếu không được nhắc lại | ✅ |
| Gửi tín hiệu theo từng phím bấm gây tốn băng thông | Chỉ gửi khi bắt đầu gõ và nhắc lại định kỳ | ✅ |
| Người đang gõ rời phòng | Xoá trạng thái của người đó ngay | ✅ |
| Gửi tín hiệu lỗi | Bỏ qua, không làm phiền người dùng (tín hiệu không quan trọng) | ✅ |

---

## I.9 Định dạng tin nhắn (Markdown) và link

**Mô tả:** hỗ trợ `**đậm**`, `*nghiêng*`, `~~gạch ngang~~`, `` `code` ``, khối code ```` ``` ````, danh sách `- mục`, trích dẫn `> …`; link `http(s)://` tự động bấm được.

**Luồng hoạt động chính:** khi hiển thị, nội dung tin được phân tích thành các khối và đoạn chữ có kiểu, rồi vẽ bằng JavaFX `TextFlow`. Bấm vào link sẽ mở trình duyệt mặc định.

**Lỗi có thể xảy ra và cách xử lý**

| Tình huống | Hệ thống xử lý | TT |
|---|---|---|
| Ký hiệu chưa đóng (`**abc`) hoặc tên biến kiểu `my_var_name` | Hiện nguyên văn, không định dạng sai | ✅ |
| Muốn gõ ký tự `*` thật | Dùng `\*` | ✅ |
| Link lừa đảo dạng `[Ngân hàng](http://trang-gia)` | Cố ý **không hỗ trợ** cú pháp này, nên địa chỉ thật của link luôn hiện ra | ✅ |
| Link nguy hiểm (`file:`, `javascript:`) | Chỉ mở link `http`/`https` | ✅ |
| Khung chat không cuộn hết xuống tin dài | Cuộn lại sau khi giao diện tính xong chiều cao các tin | ✅ |
| Tin có rất nhiều dấu `*` / `_` không đóng (vd `*a *a *a …` dài) làm treo giao diện | *(sửa 22/11, vấn đề 8)* Bộ phân tích ghi nhớ chỗ "không còn dấu đóng" nên mỗi dòng chỉ quét một lần (trước đây quét lại cho từng dấu, thời gian tăng theo bình phương độ dài). Test: chuỗi 600.000 ký tự phân tích xong dưới 5 giây | ✅ |

---

## I.10 Âm báo, thông báo màn hình, cài đặt, chọn giao diện

**Luồng hoạt động chính**
1. Có tin mới mà người dùng không đang nhìn đúng khung chat đó thì phát âm báo ngắn. Âm được tạo bằng code, không cần file âm thanh.
2. Nếu cửa sổ app không được chọn thì hiện thông báo của Windows: "An đã gửi tin nhắn trong phòng …". Bấm vào thông báo sẽ mở lại cửa sổ.
3. Nút **Cài đặt** (bánh răng) cho phép chọn giao diện (Primer, Nord, Cupertino sáng/tối, Dracula), bật/tắt âm báo và thông báo. Lựa chọn được lưu ở `~/.chat-p2p-java/settings.properties`.

**Lỗi có thể xảy ra và cách xử lý**

| Tình huống | Hệ thống xử lý | TT |
|---|---|---|
| Thông báo làm lộ nội dung tin trên màn hình khoá | Thông báo **chỉ hiện tên người gửi**, không hiện nội dung | ✅ |
| Máy không có loa / thiết bị âm thanh | Bỏ qua, không lỗi | ✅ |
| Hệ điều hành không hỗ trợ khay hệ thống | Bỏ qua thông báo, app vẫn chạy | ✅ |
| Nhiều tin đến cùng lúc gây âm báo dồn dập | Đang phát thì bỏ qua âm mới | ✅ |
| File cài đặt hỏng | Dùng giá trị mặc định, app không bị sập | ✅ |

---

## I.11 Lịch sử chat cho người mới vào phòng

**Mô tả:** người vào sau thấy được các tin nhóm trước đó, do những người đang ở trong phòng gửi lại. Lịch sử chỉ nằm trong RAM (tối đa 200 tin), mất khi rời phòng; server không lưu.

**Luồng hoạt động chính**
1. Khi xác thực xong danh tính một người, client gửi cho người đó lịch sử nhóm qua `MESSAGE_TRANSCRIPT`, chia thành nhiều gói vừa một gói UDP.
2. Mỗi gói có ACK và được gửi lại nếu mất.
3. Người nhận gộp lịch sử, bỏ các tin đã có (theo `id`), rồi chèn vào khung chat đúng thứ tự thời gian, kèm nhãn **"lịch sử · X chia sẻ"** và dòng thông báo "Đã nhận N tin nhắn trước đó do X chia sẻ".
4. Cả hai bên đều gửi lịch sử cho nhau. Người mới vào (lịch sử rỗng) chỉ nhận; hai người cũ kết nối lại thì bù cho nhau phần còn thiếu.

**Lỗi có thể xảy ra và cách xử lý**

| Tình huống | Hệ thống xử lý | TT | Hướng cải thiện |
|---|---|---|---|
| Gói lịch sử bị mất | Gửi lại tối đa 6 lần theo cơ chế ACK | ✅ | |
| Nhiều người cùng gửi lịch sử nên trùng tin | Loại trùng theo `id` | ✅ | |
| Lịch sử quá dài vượt giới hạn gói UDP | Chia nhiều gói; tin dài trên 12.000 ký tự không đưa vào lịch sử | ✅ | |
| Người chia sẻ chưa được xác thực danh tính | Không nhận và không ACK; bên gửi sẽ gửi lại sau khi xác thực xong | ✅ | |
| Người trong phòng gửi lịch sử giả (bịa tin của người khác) | Giới hạn 200 tin mỗi người chia sẻ; giao diện ghi rõ "do X chia sẻ, không xác minh được người viết" | ⚠️ | Ký từng tin bằng khoá danh tính của người viết |
| Lịch sử giả mạo **chính người nhận** là tác giả, để tin giả hiện như tin mình viết | *(sửa 22/11, vấn đề 5)* Bỏ qua mọi mục lịch sử ghi tác giả là chính người nhận. Khi bật "lưu lịch sử trên máy", tin đến từ lịch sử người khác chia sẻ **không bao giờ** được lưu thành "tin của tôi" (trước đây có thể bị lưu như vậy và mất nhãn "không xác minh" ở lần mở sau) | ✅ | |
| Tin nhắn riêng bị lộ qua lịch sử | Lịch sử **chỉ** gồm tin nhóm | ✅ | |
| Đồng hồ các máy lệch nhau | Không cho thời gian ở tương lai; sắp xếp theo thời điểm gửi | ⚠️ | |

---

## I.12 Phòng có mật khẩu

**Mô tả:** người tạo phòng tích "Đặt mật khẩu cho phòng" trong hộp **Thông tin phòng** (tối thiểu 6 ký tự, có ô nhập lại). Người vào phòng được hỏi mật khẩu trong hộp nhập mật khẩu sau khi chọn phòng có biểu tượng 🔒, hoặc nhập trong hộp "Vào phòng bằng tên". **Mật khẩu không bao giờ rời khỏi máy**, kể cả server cũng không biết.

**Luồng hoạt động chính**
1. Từ (tên phòng, mật khẩu), client dùng PBKDF2 (120.000 vòng) tạo ra một khoá chính, rồi tách thành "thẻ phòng" và "khoá chứng minh". Bước này chạy trên luồng nền.
2. Client gửi lên server tên phòng dạng `tên~thẻ`. Người nhập mật khẩu khác sẽ ra thẻ khác, rơi vào phòng khác trên server, nên không nhìn thấy nhau.
3. Sau khi trao khoá ECDH, hai bên gửi `ROOM_PROOF`, một mã HMAC gắn với khoá ECDH của cả hai trong phiên, chứng minh mình biết mật khẩu.
4. Chỉ người có bằng chứng hợp lệ mới hiện trên giao diện và được trao đổi tin.
5. Trong danh sách phòng ở sảnh, phòng hiện với biểu tượng 🔒, tên và số người; **thẻ không bao giờ được gửi cho người xem danh sách**.

**Khi nhập sai mật khẩu (mục J.2)**
1. Client tạo ra thẻ khác thẻ của phòng thật, nên gửi JOIN `tên~thẻ-khác`.
2. Server thấy đang có phòng có mật khẩu **cùng tên nhưng khác thẻ**, nên **từ chối** và gửi lại `JOIN_REJECTED(WRONG_PASSWORD)`. Server vốn đã biết các thẻ đang dùng, nên không lộ thêm thông tin gì; thẻ đúng không bao giờ được gửi cho client.
3. Client rời màn hình phòng, quay về sảnh, hiện "Sai mật khẩu phòng …" và **mở lại hộp nhập mật khẩu** của đúng phòng đó.
4. **Giới hạn số lần thử:** quá 5 lần sai trong 1 phút từ cùng một địa chỉ IP thì server trả `RATE_LIMITED` cho mọi lần JOIN phòng có mật khẩu (kể cả mật khẩu đúng) đến khi hết 1 phút. Người ngoài chỉ thử được từng lần, không dò hàng loạt được.
5. **Hệ quả:** khi tên phòng đang được một nhóm dùng với mật khẩu khác, nhóm vào sau sẽ bị báo sai mật khẩu và phải chọn tên khác. Khi mọi người đã rời phòng, tên đó lại dùng được với mật khẩu mới.

**Lỗi có thể xảy ra và cách xử lý**

| Tình huống | Hệ thống xử lý | TT | Hướng cải thiện |
|---|---|---|---|
| Nhập sai mật khẩu | Server từ chối ngay (`WRONG_PASSWORD`); client quay về sảnh, báo "Sai mật khẩu phòng" và mở lại hộp nhập mật khẩu | ✅ | |
| Thử sai mật khẩu nhiều lần liên tiếp | Quá 5 lần/phút từ cùng IP thì bị chặn (`RATE_LIMITED`) trong 1 phút | ✅ | |
| Mật khẩu quá ngắn khi tạo phòng | Không cho tạo, yêu cầu tối thiểu 6 ký tự; phải nhập lại trùng khớp | ✅ | |
| Server bị chiếm và cố ghép người lạ vào phòng | Người lạ không có bằng chứng hợp lệ nên bị bỏ qua; giao diện cảnh báo "người này không có đúng mật khẩu phòng" | ✅ | |
| Người sai mật khẩu vẫn giữ kết nối P2P | Mọi bản tin của họ bị lọc, nhưng kết nối chưa bị chủ động ngắt | ⚠️ | Thêm chức năng ngắt kết nối một người trong `RoomSession` |
| Dùng lại bằng chứng của phiên khác | Không được, vì bằng chứng gắn với khoá ECDH riêng của từng phiên | ✅ | |
| Người quản trị server (hoặc kẻ chiếm server) đoán mật khẩu từ thẻ phòng | Phải dò PBKDF2 120.000 vòng cho mỗi lần đoán; mật khẩu tối thiểu 6 ký tự | ⚠️ | Người dùng nên đặt mật khẩu dài |
| Người xem danh sách phòng muốn dò mật khẩu | Không được: danh sách chỉ có tên, số người và cờ có mật khẩu, không có thẻ | ✅ | |
| Gói `ROOM_PROOF` bị mất | Gửi lại sau 0,4 s, 1,2 s và 2,5 s | ✅ | |
| Tên phòng chứa ký tự `~` (trùng ký tự ngăn cách) | Không cho phép, báo lỗi ngay trong hộp thoại | ✅ | |

---

## I.13 Rời phòng / mất kết nối

**Luồng hoạt động chính**
1. Người dùng bấm **Rời phòng**, xác nhận cảnh báo "tin nhắn sẽ bị xoá khỏi máy", hoặc đóng cửa sổ.
2. Client **huỷ mọi file đang gửi / nhận** (gửi `FILE_CANCEL` cho bên kia, từ chối các lời mời nhận file chưa trả lời, xoá file tạm `.part`), gửi tín hiệu ngừng gõ, rồi đóng mọi kết nối P2P, giải phóng cổng UDP, ngắt WebSocket.
3. Server gửi `PEER_LEFT` cho những người còn lại; giao diện của họ hiện "X đã rời phòng", và thẻ file đang truyền với người đó chuyển sang trạng thái thất bại, kèm lý do người kia đã rời phòng.

**Lỗi có thể xảy ra và cách xử lý**

| Tình huống | Hệ thống xử lý | TT |
|---|---|---|
| Tắt app đột ngột, không kịp gửi `LEAVE` | Server phát hiện WebSocket đóng và tự báo `PEER_LEFT` | ✅ |
| Lỗi khi đóng một kết nối | Vẫn tiếp tục đóng các kết nối còn lại, không rò rỉ | ✅ |
| Callback mạng đến sau khi đã rời phòng | Bị bỏ qua, không cập nhật giao diện cũ | ✅ |
| Đã rời phòng nhưng vẫn gửi được OFFER/ICE vào phòng cũ qua server | *(sửa 22/11, vấn đề 4)* Server xoá thông tin phòng gắn với kết nối ngay khi rời phòng; sau đó mọi bản tin chuyển tiếp từ kết nối đó bị từ chối | ✅ |

---

## I.14 Truyền file 1-1

**Mô tả:** gửi file tới người trong phòng, đi thẳng qua kênh P2P đã mã hoá (server không thấy nội dung). Mỗi lần truyền là **1-1**: gửi trong tab DM thì tới đúng người đó; gửi trong khung nhóm thì app tạo **một lần truyền riêng cho từng người** đang ở trong phòng (mỗi người tự bấm Nhận hoặc Từ chối). Giao thức do hai thành viên thống nhất (`docs/Thong-nhat-giao-thuc-truyen-file.md`, `docs/Tra-loi-giao-thuc-truyen-file.md`): A làm tầng gửi tin cậy (`ReliableChunkSender`), B làm đọc/ghi file (`FileSender`, `FileReceiver`, `FileNames`), điều phối (`FileTransferManager`) và giao diện. Phạm vi: 1-1, tối đa 100 MB, mỗi người một file đang nhận tại một thời điểm, mất kết nối thì huỷ (không gửi tiếp).

**Cách dùng:** bấm nút gửi tệp (hoặc **kéo thả 1 file** vào khung chat) trong khung nhóm hoặc tab chat riêng. Trong khung chat hiện **thẻ file** với tên, dung lượng, trạng thái và thanh tiến độ. Người nhận bấm **Nhận** hoặc **Từ chối**; hai bên đều có nút **Huỷ** khi đang truyền. File nhận được lưu vào `~/Downloads`, có nút mở thư mục chứa file. **File ảnh** (png, jpg, gif, bmp) hiện **ảnh xem trước** ngay trên thẻ file (người gửi thấy từ file gốc, người nhận thấy sau khi file đã được kiểm SHA-256); bấm vào ảnh để mở bằng trình xem ảnh của Windows. App không bao giờ tự mở file nhận được.

**Luồng hoạt động chính**
1. Bên gửi tính SHA-256 cả file (trên luồng nền), gửi `FILE_OFFER` (tên, kích thước, số chunk, SHA-256), gửi lại mỗi giây cho tới khi có trả lời.
2. Bên nhận kiểm tra offer (kích thước, số chunk, định dạng SHA-256), hiện thẻ file; người dùng bấm Nhận hoặc Từ chối → `FILE_ANSWER`.
3. Bên gửi chia file thành chunk **4 KB** và gửi theo cửa sổ trượt (tối đa **8 chunk** chờ xác nhận, tức 32 KB, khoảng 60 datagram, dưới giới hạn hàng đợi nhận 100 gói của ice4j). Thư viện ICE cắt mọi gói UDP lớn hơn 1500 byte, nên `P2pDataChannel` tự **chia mỗi bản tin thành mảnh ≤ 1150 byte** và ghép lại ở bên nhận; một chunk 4 KB (~7,3 KB sau mã hoá) đi thành ~7 mảnh.
4. Bên nhận ghi vào file tạm `.part`, trả `FILE_ACK` (sau mỗi 4 KB mới theo thứ tự, ngay khi có lỗ hổng, hoặc sau 50 ms không có chunk mới). ACK gồm `contiguousUpTo` và danh sách chunk nhận lẻ: chunk **vừa tới** cộng các chunk **mới nhất** phía trên lỗ hổng.
5. **Gửi lại nhanh** (kiểu TCP RACK): bên gửi coi một chunk là mất ngay khi đã có 3 chunk **gửi sau nó** được xác nhận, và gửi lại ngay, không chờ hết hạn 500 ms. Hết hạn chờ vẫn là lưới an toàn khi không còn ACK nào về.
6. Đủ chunk thì bên nhận kiểm tra SHA-256. Khớp thì đổi `.part` thành tên thật và trả `verified = true`; chỉ khi đó bên gửi mới báo "Đã gửi xong".

**Lỗi có thể xảy ra và cách xử lý**

| Tình huống | Hệ thống xử lý | TT |
|---|---|---|
| Mất, trùng hoặc đảo thứ tự chunk | Gửi lại nhanh khi 3 chunk gửi sau đã tới (chịu được đảo thứ tự nhẹ); nếu không còn ACK nào thì gửi lại theo thời gian chờ tăng dần (0,5 → 4 s), tối đa 10 lần hết hạn liên tiếp mỗi chunk; bên nhận bỏ chunk trùng | ✅ |
| Mất offer hoặc câu trả lời | Gửi lại offer; quá 60 s không trả lời thì báo `TIMEOUT` | ✅ |
| Mất bản xác nhận cuối cùng | Bên gửi gửi lại chunk cuối để hỏi; bên nhận đã xong thì trả lại xác nhận | ✅ |
| File nhận được bị sai (SHA-256 không khớp) | Bên nhận huỷ với lý do `CHECKSUM_MISMATCH`, bên gửi báo lỗi | ✅ |
| Người nhận từ chối, hoặc một bên bấm Huỷ | Dừng ngay, báo lý do (`REJECTED`, `CANCELLED_BY_USER`) | ✅ |
| File quá 100 MB hoặc offer sai quy ước | Từ chối (`FILE_TOO_LARGE`, `INVALID_OFFER`) | ✅ |
| Tên file độc hại (`../`, ký tự cấm, `CON`…) hoặc ghi ra ngoài thư mục | Bên nhận làm sạch tên (`FileNames`), chỉ ghi trong `~/Downloads`; trùng tên thì thêm ` (1)`, ` (2)`… Không tự mở file | ✅ |
| Đang nhận một file khác từ cùng người đó | Tự trả lời `BUSY`, bên gửi báo bận | ✅ |
| Một bên rời phòng hoặc mất kết nối giữa chừng | Huỷ cả hai phía, xoá file tạm `.part` | ✅ |
| Lỗi đọc / ghi đĩa (đầy ổ, không có quyền) | Huỷ với lý do `IO_ERROR`, báo bên kia | ✅ |
| Người chưa nhập đúng mật khẩu phòng gửi file vào | Bản tin `FILE_*` của người chưa qua bước xác nhận mật khẩu bị bỏ qua; cũng không gửi file được cho người đó | ✅ |
| Tốc độ sụp khi mạng mất gói | Đã sửa (xem "Tốc độ" bên dưới): gửi lại nhanh, ACK báo chunk mới nhất, chunk 4 KB | ✅ |
| Đường truyền chậm (RTT lớn, vd qua TURN) | Tốc độ tối đa bị giới hạn bởi cửa sổ: 32 KB mỗi RTT | ⚠️ — hướng: cửa sổ tự điều chỉnh theo RTT, đo lại qua TURN thật |

**Tốc độ**

Ban đầu tốc độ rất thấp khi mạng mất gói, và nguyên nhân **không phải kích thước chunk** mà là cách khôi phục khi mất gói:
1. Bên gửi chờ hết hạn 500 ms mới gửi lại chunk mất, trong lúc đó cả cửa sổ đứng yên.
2. ACK chỉ liệt kê 64 chunk **thấp nhất** phía trên lỗ hổng. Khi lỗ hổng tồn tại lâu, các chunk mới tới không bao giờ được báo, bên gửi tưởng mất và gửi lại hàng loạt, có lúc cạn lượt và báo `TIMEOUT`.
3. ACK sau mỗi 8 chunk cố định: với chunk lớn và cửa sổ nhỏ, bên gửi luôn phải chờ ACK trễ 50 ms.

Đã sửa cả ba (bước 4 và 5 ở luồng trên) và tăng chunk lên 4 KB. Đo bằng `FileTransferBenchmarkTest`: truyền 4 MB qua **ICE thật trên localhost** (đi qua đủ `P2pDataChannel` chia mảnh, ECDH + AES-GCM, `FileTransferManager`), mất gói giả lập **theo từng datagram**, trung vị 3 lần với 3 kiểu mất gói khác nhau:

| Mất gói | Trước (chunk 512 B, cửa sổ 64) | Sau (chunk 4 KB, cửa sổ 8, gửi lại nhanh) | Tăng |
|---|---|---|---|
| 0% | 6,4 MB/s | 17,9 MB/s | ×2,8 |
| 1% | 0,20 MB/s | 18,1 MB/s | ×90 |
| 3% | 0,12 MB/s | 18,0 MB/s (lần chậm nhất 4,7) | ×150 |
| 5% | 0,09 MB/s | 5,5 MB/s (lần chậm nhất 2,2) | ×60 |

Một số nhận xét từ lần đo:
- **Chỉ tăng chunk mà không sửa cách gửi lại thì gần như vô ích:** code cũ với chunk 4 KB ở mức mất 1% chỉ đạt 0,37 MB/s.
- **Chunk lớn hơn không tốt hơn:** chunk 8–16 KB nhanh ngang 4 KB khi mạng sạch, nhưng sụp mạnh khi mất gói (một chunk 16 KB là ~26 mảnh UDP, mất 1 mảnh là mất cả chunk); 16 KB ở mức mất 3% đã có lần `TIMEOUT`.
- **Giới hạn trên Internet thật:** số trên đo trên localhost, nơi RTT gần 0. Qua mạng thật, tốc độ tối đa xấp xỉ *dữ liệu đang trên đường ÷ RTT*: với 32 KB và RTT 86 ms đo được qua TURN (mục I.3) là **khoảng 0,37 MB/s**. Cấu hình cũ (64 × 512 B) cũng là 32 KB nên **không chậm đi**, nhưng muốn nhanh hơn trên Internet phải cho cửa sổ tự lớn lên theo RTT, rồi đo lại qua TURN thật.

**Kiểm thử:**
- `ReliableChunkSenderTest` (11 kịch bản): mạng hoàn hảo; mất 20% + nhân đôi 10% + đảo thứ tự; mất ACK cuối; bị từ chối; không trả lời; file rỗng; sai checksum; huỷ giữa chừng; **gửi lại nhanh** (chưa gửi lại khi mới có 2 chunk sau tới, gửi lại ngay khi đủ 3, không gửi lặp bản vừa gửi lại).
- `FileTransferOverUnreliablePeerConnectionTest`: 1 MB qua kênh mã hoá thật, mất 15% và nhân đôi 5% gói, khoảng 1 s (trước khi có gửi lại nhanh: 1,7 s), đúng từng byte.
- `FileSenderTest`, `FileReceiverTest` (17 test, có kịch bản ACK ưu tiên chunk mới nhất), `FileNamesTest`, `FileTransferLimitsTest`: đọc/ghi file thật, làm sạch tên, kiểm tra offer.
- `FileTransferManagerTest` (16 test): hai bên nối với nhau trong bộ nhớ; nhận, từ chối, huỷ, `BUSY`, người rời phòng.
- `P2pDataChannelFragmentationTest`, `PeerConnectionMaxPayloadSizeTest`: chia và ghép mảnh; bản tin 200 000 ký tự (~235 mảnh) tới nguyên vẹn qua UDP thật; bản tin vượt 1 MB bị từ chối với lỗi rõ ràng.
- `FileTransferBenchmarkTest` (không chạy mặc định): đo tốc độ ở trên. Chạy bằng `mvn -pl common,crypto,signaling-server,p2p-core,client-javafx test -Dtest=FileTransferBenchmarkTest -Dsurefire.failIfNoSpecifiedTests=false -Dchatp2p.benchmark=true`.

**Tương thích:** định dạng datagram của `P2pDataChannel` đã đổi (byte đầu cho biết gói nguyên hay mảnh, thay cho tiền tố độ dài 4 byte), nên **client mới và client cũ không kết nối được với nhau**: mọi máy phải dùng cùng bản. Thay đổi về ACK thì tương thích ngược (định dạng `FILE_ACK` giữ nguyên).

---

## I.15 Log theo dõi (terminal và file)

**Mô tả:** mỗi bước chính in một dòng lên terminal, dạng `17:02:56.060 INFO   [P2pChatSession] ...`, để kiểm tra khi test. Mỗi người được ghi kèm 8 ký tự đầu của `peerId`, để phân biệt nhiều client chạy trên cùng một máy.

| Chế độ | Cách bật | In ra |
|---|---|---|
| **Bình thường** | Mặc định | Khởi động/thoát; đăng nhập/đăng ký/đăng xuất; vào phòng (công khai hay có mật khẩu, `roomId` trên server); trao khoá ECDH xong; `ROOM_PROOF` hợp lệ/bị từ chối; người mới vào phòng; xác thực danh tính (fingerprint rút gọn); gửi/nhận tin (id + độ dài); gửi/nhận lịch sử; hết lượt gửi lại; người rời phòng; đổi cài đặt; **server từ chối cho vào phòng kèm lý do** (`WRONG_PASSWORD`, `ROOM_FULL`…); **lỗi khi truyền file** (không hoàn tất được file, payload file hỏng, không gửi được bản tin file). Server: kết quả đăng nhập/đăng ký, lý do từ chối JOIN (kể cả sai mật khẩu và bị giới hạn số lần thử) |
| **Chi tiết** | `JAVA_TOOL_OPTIONS=-Dchat.debug=true` | Thêm: từng lần gửi lại, ACK, tín hiệu đang gõ, gói UDP bị bỏ qua, gói replay bị loại, log của thư viện ICE |
| **Bí mật** (chỉ để test) | `CHAT_P2P_LOG_SECRETS=true` | Thêm: **nội dung tin nhắn**, mật khẩu tài khoản và mật khẩu phòng, token, khoá phiên AES-256 của từng kết nối, public key ECDH. Dòng đầu log có cảnh báo `!!! CHE DO LOG BI MAT DANG BAT !!!` |

**Lỗi có thể xảy ra và cách xử lý**

| Tình huống | Hệ thống xử lý | TT |
|---|---|---|
| Lộ nội dung chat / mật khẩu qua log | Mặc định **che** (mật khẩu, token hiện `***`; tin nhắn chỉ hiện độ dài). Chế độ bí mật phải bật chủ động và có cảnh báo | ✅ |
| Lộ khoá danh tính dài hạn | Private key ECDSA **không bao giờ** được in, kể cả ở chế độ bí mật | ✅ |
| Kẻ xấu gửi gói UDP rác làm ngập terminal | Log gói bị bỏ qua chỉ hiện ở chế độ chi tiết, tối đa 20 dòng mỗi kết nối | ✅ |
| Chạy app không có cửa sổ terminal (bản đóng gói `ChatP2P.exe`, bấm đúp) | Log ghi thêm ra file `~/.chat-p2p-java/logs/client-0.log` (mới nhất), xoay vòng 5 file × 5 MB, cùng quy tắc che bí mật như terminal | ✅ |
| Theo dõi một lần truyền file thành công | Truyền file chỉ ghi log khi **lỗi**; các bước bình thường (gửi offer, nhận, tiến độ, hoàn tất) chỉ hiện trên thẻ file trong giao diện | ⚠️ — hướng: thêm log INFO cho từng bước (fileId rút gọn, tên file, dung lượng, thời gian truyền) |

---

## I.16 Bản đóng gói cho người dùng (Windows)

**Mô tả:** người dùng không cần cài Java hay Maven. Script `packaging/build-windows.ps1` dùng `jpackage` (có sẵn trong JDK) tạo thư mục `ChatP2P` gồm `ChatP2P.exe` và Java runtime đi kèm, rồi nén thành `ChatP2P-windows.zip` (khoảng 72 MB). Người dùng giải nén và bấm đúp `ChatP2P.exe`; app tự kết nối `wss://chat.zingme.fun` và tự xin tài khoản TURN tạm thời, nên **không phải cấu hình gì**.

| Tình huống | Hệ thống xử lý | TT |
|---|---|---|
| JavaFX báo thiếu thư viện khi chạy từ classpath | Lớp khởi động `Launcher` (không kế thừa `Application`) | ✅ |
| Muốn file cài đặt `.msi` có shortcut, menu Start | Cài WiX Toolset 3 rồi chạy script với `-Type msi` | ⚠️ Máy build hiện chưa có WiX |
| OneDrive / antivirus khoá tạm file khi nén | Script nén bằng `tar.exe` và thử lại vài lần | ✅ |

---

# PHẦN J — RỦI RO, GIỚI HẠN ĐÃ BIẾT VÀ KIỂM ĐỊNH BẢO MẬT

*Tổng hợp toàn bộ rủi ro đã nêu rải rác ở các phần trên vào một bảng duy nhất — nên đưa thẳng vào chương "Kết luận và hướng phát triển" hoặc "Đánh giá" của báo cáo, thay vì che giấu.*

| # | Rủi ro / giới hạn | Mức độ | Biện pháp giảm thiểu / ghi chú |
|---|---|---|---|
| 1 | Không chống MITM tuyệt đối ở lần gặp đầu tiên (xác thực chữ ký chỉ chống mạo danh lặp lại) | Trung bình — hạn chế cố hữu của mọi hệ không có PKI tập trung | Chữ ký `PEER_IDENTITY` đã gắn với khoá ECDH của phiên (`IdentitySignatureService.handshakeChallengeFor`), nên kẻ đứng giữa không thể chuyển tiếp chữ ký thật; nhưng lần gặp đầu vẫn phải tin khoá danh tính nhận được (TOFU) — người dùng nên đối chiếu fingerprint qua kênh khác (mục E.5.3) |
| 2 | Đường dẫn dự án chứa dấu tiếng Việt gây lỗi `spring-boot:run` trên Windows | Thấp — đã có cách khắc phục | Dùng `java -jar` thay vì `spring-boot:run` (mục G.5) |
| 3 | Chưa có gọi thoại / video / chia sẻ màn hình; phương án dự kiến dùng Motion-JPEG / PCM thô thay vì codec chuẩn | Trung bình | Đánh đổi có chủ đích để tránh phụ thuộc thư viện native (mục E.8.5); nêu rõ là giới hạn đã biết |
| 4 | ICE qua NAT symmetric có thể thất bại ngay cả khi có TURN nếu TURN cấu hình sai/quá tải | Trung bình | Test kỹ theo Phần F.5.3, tự host coturn thay vì phụ thuộc TURN free công khai khi đo số liệu chính thức (mục G.6) |
| 5 | `signaling-server` là điểm chịu lỗi duy nhất tạm thời (không phải nội dung, nhưng sập thì không ai vào phòng mới được) | Thấp với quy mô đồ án | Ephemeral by design — chấp nhận được; nếu cần độ sẵn sàng cao hơn, cân nhắc chạy nhiều instance sau `RoomRegistry` dùng bộ nhớ chia sẻ (ngoài phạm vi đồ án) |
| 6 | Kiến trúc mesh N² giới hạn quy mô phòng | Thấp với quy mô đồ án | Giới hạn khuyến nghị 2-6 peer/phòng khi demo/đánh giá (mục F.3) |
| 7 | UDP không đảm bảo thứ tự/không mất gói — `P2pDataChannel` không có retry ở tầng dưới | Thấp (đã giảm thiểu) | Đã thêm ACK/gửi lại ở tầng ứng dụng: `MESSAGE` và `MESSAGE_TRANSCRIPT` có `MESSAGE_ACK` (gửi lại tối đa 6 lần), truyền file có `ReliableChunkSender` (cửa sổ trượt + backoff). Thứ tự tin nhắn chữ vẫn chưa được đảm bảo tuyệt đối (mục H.3) |
| 8 | Double-encrypt cho `FILE_CHUNK` (mã hoá chunk rồi mã hoá cả Envelope) tốn CPU thừa | Rất thấp | Chấp nhận được, ưu tiên đơn giản hoá code hơn tối ưu vi mô (đã ghi chú ở mục E.3.3) |
| 9 | **Signaling dùng `ws://` không mã hoá** — token đăng nhập (JOIN), mật khẩu khi gọi REST `/auth/*`, tên phòng và ICE candidate (địa chỉ IP) đi dạng rõ giữa client và signaling server | **Cao** khi chạy qua Internet công khai | Nội dung chat **không** bị ảnh hưởng (đã mã hoá đầu-cuối trên kênh P2P), nhưng kẻ nghe lén đường truyền có thể lấy token để mạo danh tài khoản và biết ai đang vào phòng nào. **Đã giảm thiểu (2026-11-20):** signaling và REST đi qua TLS tại `chat.zingme.fun` (Caddy + Let's Encrypt), server chỉ nghe `127.0.0.1:8080` (mục J.1) |
| 10 | Lịch sử chat cho người mới vào (`MESSAGE_TRANSCRIPT`) không xác minh được người viết từng tin | Trung bình | Chỉ nhận lịch sử từ peer đã xác thực danh tính, tối đa 200 tin/peer; giao diện gắn nhãn "lịch sử · X chia sẻ". Muốn xác minh tuyệt đối phải ký từng tin bằng khoá danh tính của người viết (hướng phát triển) |
| 11 | Phòng có mật khẩu: peer gửi bằng chứng sai chỉ bị **bỏ qua**, kết nối P2P với họ vẫn mở tới khi họ rời phòng | Thấp | Họ không thấy/không gửi được gì (mọi bản tin bị lọc). Nên bổ sung API chủ động ngắt 1 peer trong `RoomSession` |
| 12 | Mật khẩu TURN cũ từng nằm trong lịch sử git (commit `fa173d1`) | Đã xử lý | Đã chuyển credential ra khỏi jar (biến môi trường / `~/.chat-p2p-java/client.properties`); **coturn chuyển sang tài khoản tạm thời do server cấp (TURN REST API, 2026-11-20)**: không còn mật khẩu cố định, mật khẩu cũ vô hiệu |
| 13 | **Nhập sai mật khẩu phòng không được báo ngay**: client JOIN vào `tên~thẻ-khác`, tức một phòng trống khác; chỉ có gợi ý sau 20 s ở một mình. Server cũng chưa báo lý do khi từ chối JOIN (token hết hạn, phòng đầy) | Trung bình (trải nghiệm, không phải lỗ hổng: mật khẩu sai không lọt vào phòng thật) | **Đã giảm thiểu (2026-11-20):** server kiểm tra lúc JOIN và gửi `JOIN_REJECTED` kèm lý do, giới hạn 5 lần sai/phút/IP; thẻ phòng vẫn không bao giờ gửi về client (mục J.2) |
| 14 | **13 vấn đề bảo mật tìm được khi kiểm định mã nguồn**: lộ token qua relay, kết nối thay thế thừa hưởng trạng thái xác thực, token gửi nhầm server, relay sau khi rời phòng, giả mạo lịch sử, sửa / xoá tin người khác, vân tay bị xoá khi chọn "Không khớp", treo giao diện vì Markdown, và các điểm chống quá tải ở server | Cao nhất ở 2 vấn đề đầu (phá vỡ cam kết "server bị chiếm cũng không đọc được tin") | **Đã xử lý (2026-11-22)**, đã deploy server, có test hồi quy (mục J.3) |
| 15 | Client vẫn chấp nhận `ws://` / `http://` không mã hoá nếu người dùng tự gõ | Thấp (mặc định là `wss://`) | Từ chối scheme không mã hoá trừ `localhost` |
| 16 | Peer có chữ ký `PEER_IDENTITY` sai chỉ bị cảnh báo, vẫn ở lại phòng và nhận tin nhóm | Trung bình | Ngắt kết nối hoặc ngừng gửi tin cho peer đó (cùng hướng với rủi ro 11) |
| 17 | Vân tay tin cậy được ghi nhớ theo **tên hiển thị** (chế độ khách tự đặt được) | Thấp | Ghi nhớ theo tài khoản khi bật đăng nhập; chuẩn hoá Unicode tên trước khi so |
| 18 | Spring Boot 3.3.4 kéo theo `spring-security-crypto` chưa có bản vá BCrypt cho mật khẩu dài hơn 72 byte (CVE-2025-22228), trong khi đăng ký cho phép tới 200 ký tự | Thấp | Nâng Spring Boot BOM |
| 19 | Các giới hạn theo IP phụ thuộc reverse proxy gửi đúng `X-Forwarded-For` | Trung bình nếu cấu hình proxy sai | Caddy hiện tại gửi mặc định; khi đổi proxy phải kiểm tra lại (mục G.3) |
| 20 | Tên phòng phân biệt hoa thường (`Phong-A` khác `phong-a`) | Thấp (trải nghiệm) | Đã ghi rõ trong gợi ý ở sảnh; nên chuẩn hoá tên phòng về chữ thường |
| 21 | Truyền file: tốc độ trên Internet bị giới hạn bởi cửa sổ cố định 32 KB mỗi RTT (~0,37 MB/s qua TURN); gửi cho nhóm là gửi riêng từng người; chưa gửi tiếp được sau khi mất kết nối | Trung bình | Cửa sổ tự điều chỉnh theo RTT; gửi tiếp theo `contiguousUpTo` (mục I.14) |
| 22 | `mvn install` lỗi ở `P2pChatSessionIntegrationTest` (jar Spring Boot đã đóng gói lại không dùng làm thư viện được) | Thấp | Dùng `mvn test`; sửa cấu hình build bằng `classifier` cho jar Spring Boot (mục F.5.1) |
| 23 | Chưa có forward secrecy theo từng tin (như Double Ratchet): mỗi kết nối dùng một cặp khoá cho cả phiên | Thấp | Khoá ECDH sinh mới mỗi kết nối, nên lộ khoá phiên này không ảnh hưởng phiên khác |
| 24 | Không có tin nhắn offline: người nhận phải đang ở trong phòng | Thấp (đánh đổi của thiết kế không lưu nội dung trên server) | Chấp nhận theo thiết kế |
| 25 | Xuyên NAT trên 4G phụ thuộc TURN (4G đo được là NAT đối xứng); TURN chỉ có UDP nên mạng chặn UDP không kết nối được; relay ↔ relay thất bại với cấu hình coturn hiện tại | Trung bình | Bật TURN qua TCP/TLS cổng 443; kiểm tra cấu hình coturn (mục I.3) |
| 26 | Giao diện luôn hiện kết nối "trực tiếp", không cho biết đang đi qua TURN | Thấp | Hiển thị `IceConnectionStats.usingRelay()` đã có sẵn |
| 27 | Chưa đo kết nối giữa hai mạng khác nhau và chưa đo truyền file qua Internet thật | Trung bình (thiếu số liệu đánh giá) | Đo theo mục F.5.3 |

## J.1 Kế hoạch chuyển signaling sang `wss://` (đã triển khai 2026-11-20)

**Đã triển khai:** tên miền con `chat.zingme.fun` (bản ghi A trên Hostinger) trỏ về VPS; **Caddy** nghe 80/443, tự xin và gia hạn chứng chỉ Let's Encrypt, chuyển tiếp (kể cả nâng cấp WebSocket) vào `127.0.0.1:8080`. Server đặt `CHAT_P2P_SERVER_ADDRESS=127.0.0.1` để cổng 8080 không mở ra Internet, và `server.forward-headers-strategy: native` để đọc IP thật của client từ `X-Forwarded-For` (cần cho giới hạn thử sai mật khẩu). Client mặc định `wss://chat.zingme.fun`; `AuthClient` tự đổi sang `https://` cho `/auth/*` và `/rooms`.

*Ghi nhận ngày 2026-11-19, triển khai ở giai đoạn sau. Không cần thay đổi giao thức signaling — chỉ thay lớp vận chuyển.*

1. **Tên miền + chứng chỉ:** trỏ 1 tên miền (vd `chat.ten-mien.vn`) về IP VPS; xin chứng chỉ miễn phí Let's Encrypt (certbot), tự gia hạn.
2. **Reverse proxy (khuyến nghị):** đặt Nginx/Caddy trước signaling server, nhận `443` (TLS) và chuyển tiếp nội bộ tới `127.0.0.1:8080`, nhớ bật nâng cấp WebSocket (`Upgrade`/`Connection`). Signaling server chỉ bind `127.0.0.1`, đóng cổng `8080` ra Internet. *(Phương án thay thế: bật TLS thẳng trong Spring Boot bằng `server.ssl.*` + keystore PKCS12.)*
3. **Client:** đổi địa chỉ mặc định trong `HomeController` từ `ws://IP:8080` sang `wss://ten-mien`; `AuthClient` dùng `https://` cho `/auth/*` và `/rooms`. Có thể từ chối `ws://`/`http://` với địa chỉ không phải `localhost` để tránh cấu hình nhầm.
4. **Kiểm thử:** chạy lại bộ test hiện có (server test dùng cổng cục bộ, không cần TLS); thêm 1 bước kiểm tra thủ công kết nối `wss://` qua Internet và xác nhận bằng Wireshark rằng token không còn đọc được.
5. **Báo cáo:** sau khi xong, cập nhật dòng 9 ở bảng trên thành "đã giảm thiểu".

Ảnh hưởng: không đổi gì ở kênh P2P (ICE/UDP, mã hoá đầu-cuối), không ảnh hưởng TURN.

## J.2 Báo "sai mật khẩu" ngay và bản tin `JOIN_REJECTED` (đã triển khai 2026-11-20)

*Ghi nhận ngày 2026-11-19, triển khai ngày 2026-11-20. Giải quyết rủi ro 13; cần deploy lại server cùng lúc với client mới.*

**Đã triển khai:** `SignalType.JOIN_REJECTED` + `JoinRejectReason` (module `common`); `RoomRegistry.tryJoin` trả về lý do, kiểm tra cùng tên khác thẻ dưới một khoá riêng để hai nhóm tạo phòng cùng lúc không cùng được tạo; `WrongPasswordLimiter` (5 lần sai/phút/IP); `SignalingWebSocketHandler` gửi `JOIN_REJECTED` cho mọi trường hợp từ chối. Client: `RoomSession.onJoinRejected` → `ChatSessionListener.onJoinRejected` → quay về sảnh, hiện lý do; sai mật khẩu thì mở lại hộp nhập mật khẩu; token hết hạn ở chế độ tài khoản thì chuyển sang màn hình đăng nhập. Test: `JoinRejectedTest` (server), `LobbyRulesTest` (thông báo phía client).

**Hiện trạng:** phòng có mật khẩu có `roomId = tên~thẻ`, trong đó thẻ suy ra từ (tên phòng, mật khẩu) bằng PBKDF2. Nhập sai mật khẩu sinh ra thẻ khác, nên client vào một phòng trống khác mà không được báo. Server từ chối JOIN (token hết hạn, phòng đầy) cũng chỉ ghi log.

**Vì sao không cho client tự kiểm tra:** muốn client so được mật khẩu thì server phải gửi thẻ về. Khi đó bất kỳ ai đã đăng nhập cũng có thể lấy thẻ về máy và dò mật khẩu **offline**, thử không giới hạn.

**Thiết kế:**
1. **Server kiểm tra khi JOIN:** nếu đang có phòng có mật khẩu tên `X` với thẻ `T1` mà nhận được JOIN `X~T2` (`T2 ≠ T1`), server **không cho vào** và gửi lại bản tin mới `JOIN_REJECTED {reason}`. Server vốn đã biết các thẻ đang dùng, nên không lộ thêm thông tin nào.
2. **Giới hạn số lần thử:** tối đa khoảng 5 lần `WRONG_PASSWORD` mỗi phút cho mỗi kết nối hoặc địa chỉ IP. Người ngoài chỉ thử được từng lần (online), không dò hàng loạt được.
3. **Bản tin dùng chung cho mọi lý do từ chối:** `WRONG_PASSWORD`, `ROOM_FULL` (đủ 8 người), `AUTH_REQUIRED` / `AUTH_EXPIRED` (thiếu hoặc hết hạn token), `DUPLICATE_PEER`, `RATE_LIMITED`.
4. **Client:** nhận `JOIN_REJECTED` thì quay về sảnh và hiện thông báo tương ứng. Với `WRONG_PASSWORD`, mở lại hộp nhập mật khẩu của đúng phòng đó; với `AUTH_EXPIRED`, chuyển sang màn hình đăng nhập.
5. **Hệ quả cần chấp nhận:** không còn chuyện hai nhóm dùng cùng tên phòng với hai mật khẩu khác nhau. Nhóm vào sau bị báo sai mật khẩu và phải chọn tên khác.
6. **Tương thích:** client cũ không hiểu `JOIN_REJECTED`, nên mọi máy cần cập nhật cùng lúc với server.
7. **Kiểm thử:** test server cho từng lý do từ chối và cho giới hạn số lần thử; test tích hợp "sai mật khẩu → nhận `WRONG_PASSWORD` → nhập đúng → vào được phòng".

Sau khi xong, cập nhật rủi ro 13 thành "đã giảm thiểu", và bỏ gợi ý 20 s cho trường hợp sai mật khẩu (vẫn giữ cho trường hợp "chưa ai vào").

## J.3 Kiểm định bảo mật mã nguồn và các bản sửa (2026-11-22)

**Phương pháp:**
1. Trinh sát kiến trúc và các ranh giới tin cậy.
2. Chia mã nguồn thành 21 đơn vị (bề mặt nhận dữ liệu × ranh giới × loại tấn công); mỗi đơn vị do một người tìm lỗi riêng rà soát.
3. Coverage critic kiểm tra những chỗ còn bỏ sót, qua 3 vòng.
4. Mỗi lỗi ứng viên được một người khác **cố bác bỏ**, sau đó kiểm chứng lần 2 bởi một người độc lập nữa.

Có 16 ứng viên: 13 được giữ lại, 3 bị bác bỏ kèm lý do.

| # | Vấn đề | Lớp bị ảnh hưởng | Cách sửa |
|---|---|---|---|
| 1 | Kết nối thay thế cùng `peerId` thừa hưởng trạng thái `ROOM_PROOF` / `PEER_IDENTITY` | `RoomSession`, `P2pChatSession` | Phát `onPeerLeft` cho kết nối cũ khi bị thay |
| 2 | Server relay nguyên `authToken` của người gửi | `SignalingWebSocketHandler` | `signal.setAuthToken(null)` trước khi relay |
| 3 | Token gửi tới server không phải nơi cấp | `AuthController`, `Navigator`, `HomeController` | Truyền server đăng nhập sang sảnh, khoá ô server ở chế độ tài khoản |
| 4 | Thuộc tính phòng của session còn sau `LEAVE` | `SignalingWebSocketHandler`, `RoomRegistry` | Xoá thuộc tính khi rời phòng; kiểm tra thêm `isMember` |
| 5 | `MESSAGE_TRANSCRIPT` mạo danh người nhận, bị lưu thành "tin của tôi" | `P2pChatSession`, `RoomController` | Bỏ mục có `authorId` = chính mình; tin từ lịch sử chia sẻ không bao giờ là "của tôi" khi lưu |
| 6 | Sửa / xoá tin theo id do người gửi tự đặt | `ChatTranscriptManager`, `RoomController`, `MessageContext` | Xác định tin theo cặp (id, tác giả) |
| 7 | Chọn "Không khớp" xoá vân tay hợp lệ đã lưu | `RoomController` | Giữ vân tay cũ khi hộp thoại mở vì khoá đã đổi |
| 8 | Tin dài + Markdown O(n²) treo luồng JavaFX | `P2pChatSession`, `MarkdownParser` | Bỏ tin > 12.000 ký tự; ghi nhớ vị trí "không còn dấu đóng" |
| 9 | Gửi đồng bộ trong `synchronized(session)` | `SignalingWebSocketHandler` | `ConcurrentWebSocketSessionDecorator` |
| 10 | Kết nối chưa `JOIN` được giữ mãi, không giới hạn mỗi IP | `SignalingWebSocketHandler` | Hạn chót `JOIN` 30 s, 32 kết nối / IP |
| 11 | `/auth/*` không giới hạn tốc độ, bảng `sessions` không dọn | `AuthController`, `AuthService` | 10 lần/phút/IP; xoá phiên hết hạn khi đăng nhập |
| 12 | Không giới hạn số phòng, độ dài tên | `RoomRegistry`, `SignalingWebSocketHandler` | Giới hạn độ dài, tối đa 5.000 phòng, chỉ mục theo session |
| 13 | Map giới hạn theo IP của TURN không bao giờ dọn | `TurnController` | `RequestRateLimiter` dùng chung |

**Tương thích:** không đổi định dạng giao thức. Client cũ vẫn dùng được server mới, chỉ thấy thông báo chung với mã mới `SERVER_BUSY`. Server đã deploy ngày 22/11/2026; client cần build lại để nhận các bản sửa phía client (1, 3, 5, 6, 7, 8).

**Điểm làm tốt** (đã kiểm tra, không có lỗi): lõi mật mã gồm ECDH P-256 + HKDF với khoá riêng mỗi chiều, AES-GCM có chống replay, chữ ký `PEER_IDENTITY` gắn với khoá ECDH của phiên, và `ROOM_PROOF` dùng HMAC so sánh thời gian hằng. Server dùng BCrypt, lưu token dưới dạng hash và tham số hoá SQL. File nhận được lưu an toàn, không thể ghi ra ngoài thư mục `Downloads`. MySQL chỉ mở trên localhost. Không có secret nào bị commit.

Mỗi vấn đề dưới đây đã được **hai lượt kiểm chứng độc lập** xác nhận đoạn code gây lỗi là có thật. Sau khi sửa, toàn bộ test (gồm test hồi quy mới) đã chạy qua trong Docker cô lập (mục F.5.1). Các test hồi quy chứng minh hành vi **sau khi sửa** là đúng; chúng chưa được chạy trên code cũ để tái hiện lỗi.

### J.3.1 Chi tiết từng vấn đề

#### Vấn đề 1 — Kết nối thay thế kế thừa trạng thái tin cậy của kết nối cũ

**Hiện tượng.** Khi một OFFER thứ hai tới với peerId của một peer đã vào phòng và đã xác thực, `RoomSession` dựng kết nối ICE/ECDH mới rồi âm thầm thay kết nối cũ (`peers.put`). Phía client (`P2pChatSession`) lưu "đã qua `ROOM_PROOF`" và "đã xác thực `PEER_IDENTITY`" **theo peerId**, và chỉ xoá khi nhận PEER_LEFT. Kết nối mới vì vậy bỏ qua cả hai bước kiểm tra, giao diện vẫn hiện VERIFIED với vân tay cũ, còn mọi tin nhắn sau đó được mã hoá tới một khoá ECDH chưa ai xác thực.

**Kịch bản khai thác.** Signaling server bị chiếm (hoặc kẻ đứng giữa khi dùng `ws://`) gửi một OFFER giả mang peerId của Bình. Người lạ trả lời OFFER đó, dù không có mật khẩu phòng và không có khoá danh tính của Bình, rồi đọc được tin nhắn gửi cho Bình và gửi tin dưới danh nghĩa Bình.

**Vị trí.** `p2p-core/.../RoomSession.java` (`onIceConnected`, đoạn `peers.put`); `client-javafx/.../P2pChatSession.java` (`handleRoomProof`, `admit`, `admittedOnly`, `handleTranscript`).

**Cách sửa.** Khi kết nối của một peerId bị thay, `RoomSession` gọi `onPeerLeft` cho kết nối cũ trước khi kết nối mới hoạt động. Client nhờ đó xoá sạch trạng thái tin cậy của peerId đó, và kết nối mới phải làm lại `ROOM_PROOF` + `PEER_IDENTITY` từ đầu.

#### Vấn đề 2 — Server chuyển nguyên token đăng nhập sang peer khác

**Hiện tượng.** Client gắn `authToken` vào mọi bản tin OFFER/ANSWER/ICE_CANDIDATE để server kiểm tra lại. Server relay **nguyên đối tượng** `SignalMessage` cho peer đích mà không xoá trường này, nên peer đích nhận được token còn hạn 12 giờ của người gửi.

**Kịch bản khai thác.** Người cùng phòng dùng client đã sửa để đọc token, rồi dùng nó trong 12 giờ để vào phòng dưới tên tài khoản nạn nhân, xem danh sách phòng, hoặc đăng xuất nạn nhân.

**Vị trí.** `p2p-core/.../WebSocketSignalingClient.java` (`sendMessage`); `signaling-server/.../SignalingWebSocketHandler.java` (`relayToTargetPeer`).

**Cách sửa.** Server gọi `signal.setAuthToken(null)` trước khi relay. Test: `SignalingSecurityFixesTest.relayedSignalDoesNotCarryTheSendersAuthToken`.

> Sau khi triển khai bản sửa, nên coi như token của những người từng ở chung phòng với người lạ đã bị lộ (token tự hết hạn sau 12 giờ).

#### Vấn đề 3 — Token bị gửi tới server không phải nơi đã cấp

**Hiện tượng.** Người dùng đăng nhập vào server A (ô server ở màn hình đăng nhập). Màn hình Home lại tự đặt ô server về mặc định `wss://chat.zingme.fun` và gửi ngay `GET /rooms` kèm token tới đó. Việc vào phòng (JOIN), lấy TURN và tải lại danh sách phòng cũng dùng ô server này, và người dùng sửa được ô đó.

**Hậu quả.** Người dùng server tự host bị lộ token cho chủ server mặc định, hoặc cho bất kỳ server nào gõ vào sau đó.

**Vị trí.** `client-javafx/.../AuthController.java` (`login`), `Navigator.java` (`showHome`, `showHomeAfterLeaving`), `HomeController.java` (`initialize`, `setAuthenticatedUser`, `loadRooms`).

**Cách sửa.** Truyền địa chỉ server đăng nhập sang Home. Ở chế độ tài khoản, ô server bị khoá vào đúng server đó; muốn đổi server phải thoát ứng dụng rồi đăng nhập lại (chế độ tài khoản **chưa có nút đăng xuất**: `AuthClient.logout` có sẵn nhưng giao diện chưa gọi, nên token chỉ hết hiệu lực sau 12 giờ).

#### Vấn đề 4 — Kết nối đã rời phòng vẫn relay được

**Hiện tượng.** Sau LEAVE (hoặc khi JOIN sang phòng khác thất bại), server xoá peer khỏi danh sách phòng nhưng **không xoá** các thuộc tính `roomId`/`peerId`/`authToken` trên session. Hàm kiểm tra quyền relay lại chỉ so với các thuộc tính đó.

**Hậu quả.** Một session đã rời phòng (kể cả khi phòng đã đủ người) vẫn gửi được OFFER/ICE tới các thành viên hiện tại, dưới peerId cũ.

**Vị trí.** `signaling-server/.../SignalingWebSocketHandler.java` (`removeAndNotify`, `isAuthorized`).

**Cách sửa.** Xoá các thuộc tính ngay khi rời phòng. `isAuthorized` kiểm tra thêm với sổ phòng thật (`RoomRegistry.isMember`). Test: `sessionCannotRelayAfterLeavingTheRoom`, `failedRejoinAlsoDropsTheOldBinding`.

#### Vấn đề 5 — Lịch sử chia sẻ giả mạo bị lưu thành "tin của tôi"

**Hiện tượng.** `MESSAGE_TRANSCRIPT` (lịch sử chia sẻ cho người mới vào) lấy `authorId` và `authorName` từ payload mà không kiểm tra. Lúc đang trong phòng, giao diện có ghi nhãn "lịch sử · X chia sẻ, không xác minh được người viết". Nhưng nếu `authorId` trùng peerId của chính người nhận, tin hiện ra như bong bóng của chính mình. Khi bật "lưu lịch sử", hàm `saveLocalHistory` còn lưu tin đó thành `mine = true`. Lần sau mở lại, tin giả hiện thành tin chính mình viết và **mất hẳn nhãn cảnh báo**.

**Vị trí.** `client-javafx/.../P2pChatSession.java` (`handleTranscript`); `RoomController.java` (`saveLocalHistory`, `loadLocalHistory`).

**Cách sửa.** Bỏ qua mục lịch sử có `authorId` trùng peerId của chính người nhận. Khi lưu, tin đến từ lịch sử do người khác chia sẻ không bao giờ được đánh dấu là "của tôi".

#### Vấn đề 6 — Trùng id tin nhắn cho phép sửa / xoá tin của người khác

**Hiện tượng.** Id tin nhắn do người gửi tự đặt. Phía giao diện, việc tìm tin để sửa dùng "tin đầu tiên có id X", việc áp dụng sửa đổi thay "mọi tin có id X", và trạng thái đã xoá cũng lưu theo id. Nếu tin của M với id X tới trước tin thật của B cùng id X, M có thể sửa hoặc xoá tin của B trên máy người nhận, còn các lần sửa thật của B lại bị từ chối.

**Vị trí.** `client-javafx/.../ChatTranscriptManager.java` (`findMessage`, `replaceMessage`); `RoomController.java` (`onMessageEdited`, `applyEdit`); `MessageContext.java`; `TranscriptCell.java`.

**Cách sửa.** Mọi thao tác sửa / xoá đều theo **cặp (id, tác giả)**. Thêm `findMessage(id, authorId)`; `replaceMessage` chỉ thay tin cùng id và cùng tác giả; trạng thái sửa/xoá lưu theo khoá `tác giả + id`.

#### Vấn đề 7 — "Không khớp" xoá mất vân tay hợp lệ

**Hiện tượng.** Khi một khoá lạ xuất hiện dưới tên đã được ghi nhớ, ứng dụng cảnh báo "khoá đã đổi". Nếu người dùng chọn đúng "Không khớp", code lại gọi `TrustedPeers.forget(tên)`, tức là **xoá vân tay thật** đã lưu. Từ lần sau, kẻ mạo danh dùng cùng tên không còn bị cảnh báo nữa.

**Vị trí.** `client-javafx/.../RoomController.java` (`verifyFingerprint`); `TrustedPeers.java` (`forget`).

**Cách sửa.** Khi hộp thoại mở vì khoá đã đổi, chọn "Không khớp" sẽ **giữ nguyên** vân tay đã lưu. Chỉ xoá khi người dùng phủ nhận chính khoá đang được tin cậy.

#### Vấn đề 8 — Một tin nhắn làm treo giao diện

**Hiện tượng.** Tin nhắn trực tiếp không bị giới hạn độ dài; giới hạn duy nhất là 1 MiB của kênh UDP, tương đương khoảng 750.000 ký tự. Trong khi đó, sửa tin và lịch sử đều bị giới hạn 12.000 ký tự. Bộ đọc Markdown, với mỗi dấu `*` mở không có dấu đóng, lại quét tới cuối dòng, nên độ phức tạp là O(n²). Bộ đọc chạy trên luồng JavaFX, vì vậy một tin kiểu `*a *a *a …` làm treo cửa sổ người nhận, và treo lại mỗi lần cuộn qua tin đó.

**Vị trí.** `client-javafx/.../P2pChatSession.java` (`handleMessage`); `MarkdownParser.java` (`parseInline`, `findClosing`).

**Cách sửa.** (1) Bỏ qua tin dài hơn `GroupHistory.MAX_TEXT_CHARS` (12.000 ký tự), vẫn gửi ACK để bên gửi không gửi lại. (2) `findClosing` ghi nhớ vị trí "từ đây không còn dấu đóng" cho từng loại dấu, nên mỗi dòng chỉ còn quét tuyến tính. Test: `MarkdownParserComplexityTest` (chuỗi 600.000 ký tự phải phân tích xong dưới 5 giây và cho kết quả không đổi).

#### Vấn đề 9 — Peer ngừng đọc socket làm nghẽn server

**Hiện tượng.** Hàm `send` của server ghi **đồng bộ** trong khi giữ khoá `synchronized(session)`, trên chính luồng đang xử lý bản tin của người khác. Một peer cố ý ngừng đọc socket làm mọi luồng gửi tới nó bị treo (relay, PEER_JOINED, PEER_LEFT) và xếp hàng trên khoá. Lặp lại ở nhiều phòng thì có thể làm cạn luồng xử lý của Tomcat.

**Vị trí.** `signaling-server/.../SignalingWebSocketHandler.java` (`send`, `broadcastToOthers`).

**Cách sửa.** Bọc mỗi session bằng `ConcurrentWebSocketSessionDecorator` với giới hạn 10 giây và 512 KB. Các luồng khác chỉ đưa bản tin vào hàng đợi rồi trả về ngay. Session nào nhận quá chậm sẽ bị đóng, thay vì làm nghẽn người khác.

#### Vấn đề 10 — Kết nối chưa đăng nhập được giữ vô thời hạn

**Hiện tượng.** `/ws` chấp nhận mọi kết nối và chỉ kiểm tra token khi nhận bản tin JOIN. Không có idle timeout, không có hạn chót phải JOIN, không giới hạn số kết nối mỗi IP. Một máy có thể mở hàng nghìn kết nối im lặng và chiếm hết slot kết nối của server.

**Vị trí.** `signaling-server/.../WebSocketConfig.java`; `SignalingWebSocketHandler.java`.

**Cách sửa.** Kết nối phải JOIN trong 30 giây, nếu không sẽ bị đóng. Mỗi địa chỉ được tối đa 32 kết nối đồng thời (IPv6 gộp theo dải /64). Không đặt idle timeout chung, vì thành viên trong phòng có thể im lặng lâu một cách hợp lệ.

#### Vấn đề 11 — `/auth` không giới hạn tốc độ

**Hiện tượng.** Mỗi lần gọi `/auth/login` (với username có thật) hoặc `/auth/register` tốn một lần tính BCrypt, đăng ký còn ghi thêm một dòng `users`. Mỗi lần đăng nhập thành công ghi thêm một dòng `sessions`, và không có chỗ nào xoá các dòng đã hết hạn hoặc đã đăng xuất.

**Vị trí.** `signaling-server/.../AuthController.java`, `AuthService.java`.

**Cách sửa.** Tối đa 10 lần đăng nhập/đăng ký mỗi phút cho một địa chỉ (trả mã 429). Khi đăng nhập, xoá các phiên đã hết hạn hoặc đã đăng xuất của tài khoản đó. Đồng thời sửa câu UPDATE ghi khoá lần đầu thành `… AND public_key IS NULL` để tránh race. Test: `RequestRateLimiterTest`.

#### Vấn đề 12 — Không giới hạn số phòng và độ dài tên

**Hiện tượng.** Server chỉ giới hạn 8 người mỗi phòng. Không có giới hạn tổng số phòng, `roomId`/`peerId` dài tuỳ ý (tới 64 KB), `GET /rooms` trả về mọi tên phòng, và mỗi lần đóng kết nối phải quét toàn bộ các phòng.

**Vị trí.** `signaling-server/.../RoomRegistry.java`; `SignalingWebSocketHandler.java` (`handleJoin`).

**Cách sửa.** Giới hạn `roomId` ≤ 200 ký tự, `peerId` ≤ 100, tên khách ≤ 64 (vượt quá thì trả `INVALID_JOIN`). Giới hạn tối đa 5.000 phòng (vượt quá thì trả mã mới `SERVER_BUSY`; client đã có thông báo tiếng Việt). Thêm chỉ mục theo session để việc rời phòng có độ phức tạp O(1). Test: `oversizedJoinFieldsAreRejected`, `closingTheConnectionStillRemovesThePeer`.

#### Vấn đề 13 — Bộ giới hạn TURN không dọn bộ nhớ

**Hiện tượng.** `TurnController` giữ một map theo IP mà không bao giờ xoá key. Mỗi địa chỉ mới (ví dụ xoay vòng trong cả dải IPv6) để lại một key vĩnh viễn và lại được hưởng hạn mức mới.

**Vị trí.** `signaling-server/.../TurnController.java`.

**Cách sửa.** Dùng lớp chung mới `RequestRateLimiter`: tự xoá key đã hết hạn, giới hạn tổng số key ở 10.000, và gộp IPv6 theo dải /64. Lớp này cũng được dùng cho `/auth`. Test: `RequestRateLimiterTest`.

### J.3.2 Các ứng viên đã bị bác bỏ (không phải lỗi)

| Ứng viên | Lý do bác bỏ |
|---|---|
| Đăng nhập bỏ qua kiểm tra khoá danh tính khi không gửi `publicKey` | Khoá công khai vốn được gửi cho mọi người trong phòng, nên việc so khớp không bảo vệ được gì |
| Peer gửi hàng loạt FILE_OFFER | Peer trong phòng có thể gây tác động tương đương bằng tin nhắn thường. Đã ghi lại thành đề xuất cải thiện |
| `/turn-credentials` không yêu cầu đăng nhập | Ai cũng tự đăng ký được tài khoản, nên thiếu kiểm tra token không tạo khác biệt về mức tin cậy. Rủi ro lạm dụng TURN nằm ở cấu hình coturn |

### J.3.3 Test hồi quy

| Test | Vấn đề |
|---|---|
| `p2p-core/.../RoomSessionEstablishedConnectionOverwriteSecurityTest` (thêm kiểm tra `onPeerLeft` khi thay kết nối) | 1 |
| `signaling-server/.../ws/SignalingSecurityFixesTest` | 2, 4, 12 |
| `signaling-server/.../limit/RequestRateLimiterTest` | 11, 13 |
| `client-javafx/.../view/MarkdownParserComplexityTest` | 8 |

### J.3.4 Đề xuất cải thiện thêm (chưa sửa)

- Từ chối kết nối `ws://` hoặc `http://` tới server không phải localhost, để mật khẩu và token không đi dạng rõ.
- Khi `PEER_IDENTITY` sai chữ ký, ngắt kết nối với peer đó thay vì chỉ hiện thông báo.
- Ghi nhớ vân tay theo tài khoản (khi bật đăng nhập) thay vì theo tên hiển thị, và chuẩn hoá Unicode tên trước khi so sánh.
- Giới hạn số offer file đang chờ của mỗi peer và cho chúng hết hạn; giới hạn độ dài khung chat.
- Nâng Spring Boot lên bản mới hơn 3.3.4 để có bản vá BCrypt (CVE-2025-22228).
- `WindowsIceFirewallPermission`: gọi `powershell.exe` bằng đường dẫn tuyệt đối, và giới hạn rule firewall cho đúng chương trình Java của ứng dụng, bỏ profile Public.
- Trên VPS: đặt `CHAT_P2P_SERVER_ADDRESS=127.0.0.1` sau reverse proxy có TLS; cấu hình `denied-peer-ip` và quota cho coturn.

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
4. RFC 5869, *HMAC-based Extract-and-Expand Key Derivation Function (HKDF)* — dùng cho `Hkdf` (mục E.3.4).
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
| 2026-11-22 | **Gộp tài liệu:** `Bao-cao-chuc-nang-da-lam.md` thành Phần I, `Bao-cao-bao-mat-13-van-de.md` thành mục J.3; viết lại Phần A–H theo code hiện tại (Phần D rút gọn thành so sánh với chitchatter; Phần E mô tả thiết kế đã cài đặt thay cho bản thi công giai đoạn đầu; thêm quy trình deploy ở mục G.6); gộp mục "Hạn chế" vào bảng rủi ro (rủi ro 20–27). |
| 2026-11-22 | Kiểm định bảo mật mã nguồn: thêm mục J.3 và rủi ro 14–19; cập nhật F.2 (gia cố signaling), F.5.1 (283 test thay cho bảng 8 test cũ), F.5.2, F.5.4, G.3 (bảng biến môi trường), G.4 (client đã nối mạng thật, ô server khoá theo tài khoản), H.2 (client đã có log). |
| 2026-08-21 | Tạo `Tai-lieu-ky-thuat-Chitchatter.md` (phân tích bản gốc) và `Thiet-ke-ky-thuat-chat-p2p-java.md` (thiết kế thi công) riêng biệt. |
| 2026-08-21 | Gộp 2 tài liệu thành `Tai-lieu-ky-thuat.md` (Phần I / Phần II). |
| 2026-08-21 | Viết lại toàn diện thành tài liệu kỹ thuật đầy đủ (Phần A-K): bổ sung tổng quan hệ thống, kiến trúc module hiện có trong repo, yêu cầu phi chức năng, chiến lược kiểm thử, cấu hình/vận hành, xử lý lỗi/logging, rủi ro kỹ thuật, phụ lục thuật ngữ — không còn chỉ giới hạn ở thiết kế chức năng. |
| 2026-11-20 | Triển khai mục J.2 (`JOIN_REJECTED`, báo sai mật khẩu ngay, giới hạn số lần thử); rủi ro 13 chuyển thành "đã giảm thiểu". |
| 2026-11-19 | Phần J: thêm rủi ro 13 (nhập sai mật khẩu phòng không được báo ngay, server chưa báo lý do từ chối JOIN) và mục J.2 kế hoạch kiểm tra mật khẩu phía server + bản tin `JOIN_REJECTED`. |
| 2026-11-19 | Phần J: cập nhật rủi ro 1 và 7 theo code hiện tại; thêm rủi ro 9-12 (signaling `ws://` chưa mã hoá, lịch sử không xác minh người viết, peer sai mật khẩu chưa bị ngắt, mật khẩu TURN trong lịch sử git) và mục J.1 kế hoạch chuyển sang `wss://`. |
