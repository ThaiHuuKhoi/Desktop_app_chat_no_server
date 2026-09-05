Phân công công việc — nhóm 2 thành viên

# PHÂN CÔNG CÔNG VIỆC

**Đồ án:** Ứng dụng chat ngang hàng (P2P) mã hoá đầu-cuối trên nền tảng Java
**Thành viên:** *[Tên A]* — *[MSSV]* &nbsp;|&nbsp; *[Tên B]* — *[MSSV]*

*Bảng phân công này bám theo thiết kế thi công trong [Tai-lieu-ky-thuat.md](Tai-lieu-ky-thuat.md) (Phần E) — không phải phạm vi tối giản ban đầu của [De-cuong-Chat-P2P-Java.md](De-cuong-Chat-P2P-Java.md). Hai tài liệu đang lệch nhau ở một điểm cần thống nhất với GVHD trước khi chốt lịch (xem ghi chú ⚠️ ở mục 4) — Tai-lieu-ky-thuat.md Phần B.2 đưa **video call, audio call, screen share** vào 3 trong 12 chức năng mục tiêu, trong khi đề cương mục 3 liệt kê "gọi thoại/video call" là **ngoài phạm vi đề tài**. Bảng dưới đây tạm coi 3 chức năng này là *stretch goal làm sau cùng* (đúng khuyến nghị Phần E.8.5/E.9), không phải cam kết cứng.*

---

## 1. Nguyên tắc chia việc

Chia theo **tầng kỹ thuật**, không chia theo tính năng lắt nhắt, để mỗi người sở hữu trọn vẹn một mảng, chịu trách nhiệm rõ ràng và có thể làm song song:

- **Thành viên A** phụ trách **Mạng & Kết nối** (signaling, xuyên NAT, truyền dữ liệu tầng thấp, quản lý phiên nhiều peer).
- **Thành viên B** phụ trách **Bảo mật & Ứng dụng** (mã hoá, xác thực danh tính, giao diện JavaFX với đầy đủ tính năng chat, truyền file, media).

Để hai người không phải chờ nhau, cả hai thống nhất **hai interface chung** ngay từ đầu (mục 3):
1. `DataChannel` — như cũ, tách tầng vận chuyển byte thô khỏi phần còn lại.
2. `Envelope` / `EnvelopeCodec` — mới, bổ sung theo Tai-lieu-ky-thuat.md Phần E.3, vì thiết kế mới cần **đa kênh logic trên 1 `DataChannel`** (tương đương `PeerRoom.makeAction` của chitchatter — xem Phần D.4) để tải được 10 loại `EnvelopeType` (chat, DM, typing, xác thực, file, media...) thay vì chỉ 1 loại tin nhắn như bản demo ban đầu.

`Envelope`/`EnvelopeCodec` là **điểm giao thoa** giữa 2 mảng — không thuộc hẳn về A hay B — nên được liệt kê riêng ở mục 2 thay vì gán cứng cho một người, và phải chốt cùng đợt với `DataChannel` (tuần 3–4).

## 2. Bảng phân công theo module

| Module / chức năng | Phụ trách | Nội dung | Tham chiếu Tai-lieu-ky-thuat.md |
|---|---|---|---|
| Signaling server | **A** | Spring Boot + WebSocket, quản lý phòng, chuyển tiếp SDP/ICE candidate | Phần C.2, E.6.1, E.6.4 |
| P2P core (NAT traversal) | **A** | Tích hợp ice4j, thiết lập kết nối trực tiếp, fallback TURN relay, mở `P2pDataChannel` trên candidate pair đã chọn | Phần E.6.2–E.6.3 |
| Quản lý phiên nhiều peer (mesh) | **A** | `RoomSession`/`PeerConnection` — vòng đời kết nối với từng peer mới, `Map<peerId, PeerConnection>`, phát sự kiện `onPeerJoined`/`onPeerLeft` | Phần E.4, E.7 |
| Giao thức đóng gói dữ liệu (framing tầng transport) | **A** | Length-prefix framing trên UDP, xử lý mất gói/mất kết nối, retry | Phần E.6.3, H.3 |
| **Giao thức `Envelope`/`EnvelopeCodec`** | **A + B (chốt chung)** | A cài phần đóng gói/gửi qua `PeerConnection`; B cung cấp `AesGcmCipher`/`KeyExchangeService` để mã hoá — 2 bên ráp lại thành `EnvelopeCodec` | Phần E.3 |
| Đo hiệu năng kết nối | **A** | Tỉ lệ kết nối P2P thành công, độ trễ thiết lập, qua nhiều kịch bản mạng | Phần F.1, F.5.3 |
| Module mã hoá | **B** | ECDH trao khoá phiên (secp256r1), AES-GCM mã hoá/giải mã nội dung | Phần E.12.1, đã xong (`crypto`) |
| **Xác thực danh tính peer (tự động)** | **B** | `IdentitySignatureService` (ECDSA secp256r1) ký/verify chuỗi thách thức `"${roomId}_${userId}"`, cập nhật `verificationState` tự động khi nhận `PEER_IDENTITY` — **không còn** UI "so khớp fingerprint" thủ công như thiết kế ban đầu | Phần E.5 (thay thế thiết kế cũ ở đề cương mục 5) |
| Phòng công khai / phòng riêng tư | **B** | `RoomSecret.derive(roomId, password)` (SHA-256), `PasswordField` ẩn/hiện trong `HomeView` | Phần E.12.3 |
| Giao diện JavaFX — khung chat nhóm, đa dòng, Markdown | **B** | `TextArea` đa dòng, render Markdown, `ObservableList<ChatMessage>` | Phần E.8.1 |
| Direct message (DM) | **B** | Tab riêng theo từng peer, gọi `roomSession.sendTo(...)` (API do A cung cấp qua `RoomSession`) | Phần E.8.2 |
| Trạng thái đang gõ | **B** | Debounce bằng `PauseTransition`, gửi `Envelope(TYPING_STATUS_CHANGE, ...)` | Phần E.8.3 |
| Conversation backfilling | **B** | Gửi `MESSAGE_TRANSCRIPT` cho peer mới vào phòng công khai | Phần E.4 bước 6 |
| Truyền file (mã hoá chunk) | **B** | `FileSender`/`FileReceiver`, chia chunk 16KB, mã hoá từng chunk, UI tiến trình | Phần E.8.4 |
| Cài đặt cá nhân, theme sáng/tối | **B** | `UserSettingsService` (Preferences hoặc JSON cục bộ), 2 stylesheet | Phần E.8.6 |
| ⚠️ Video call / Audio call / Screen share | **B** (stretch, làm sau cùng) | Motion-JPEG qua webcam-capture, PCM qua `TargetDataLine`, screen capture qua `Robot` — xem ghi chú phạm vi ở đầu file | Phần E.8.5, ⚠️ mâu thuẫn với đề cương mục 3 |
| Đánh giá bảo mật | **B** | Test chống nghe lén trung gian (Wireshark), test giả mạo danh tính, kiểm tra tính đúng đắn mã hoá | Phần F.2, F.5.4 |
| Kiểm thử tích hợp, viết báo cáo | **A + B** | Cả hai cùng làm ở giai đoạn cuối | Phần F.5.2 |

*(Nhúng ứng dụng qua iframe/SDK — chức năng thứ 13 của chitchatter — không áp dụng cho app desktop, không cần phân công, xem Phần E.8.7/E.12.10.)*

## 3. Interface chung — điểm tích hợp giữa hai phần việc

### 3.1 `DataChannel` — chốt trước khi tách ra làm song song (tuần 3–4)

```java
public interface DataChannel {
    void send(byte[] data);
    void onReceive(Consumer<byte[]> handler);
    void close();
}
```

- **A** cài đặt bản thật `P2pDataChannel` — chạy trên kết nối ICE/socket thật.
- **B** cài đặt tạm bản giả lập `LoopbackDataChannel` — chạy nội bộ trong cùng máy, đủ để dựng UI và mã hoá mà **không cần chờ A xong phần mạng**.
- Đến tuần tích hợp, chỉ cần thay `LoopbackDataChannel` bằng `P2pDataChannel` thật của A — B không phải sửa lại logic mã hoá/UI.

### 3.2 `Envelope` / `EnvelopeCodec` — chốt cùng đợt, trước tuần 10–11

Vì thiết kế mới cần nhiều loại tin nhắn (`EnvelopeType`: `MESSAGE`, `MESSAGE_TRANSCRIPT`, `TYPING_STATUS_CHANGE`, `PEER_IDENTITY`, `FILE_OFFER`, `FILE_CHUNK`, `AUDIO_CHANGE`, `VIDEO_CHANGE`, `SCREEN_SHARE_CHANGE`, `MEDIA_FRAME` — Phần E.3.1) đi qua **cùng một** `DataChannel`, hai người phải thống nhất trước:

```java
public final class Envelope {
    private EnvelopeType type;
    private String namespace;    // "g" (nhóm) hoặc "dm" (direct message)
    private long timestamp;
    private byte[] payload;      // JSON của record cụ thể theo `type`, TRƯỚC KHI mã hoá
}
```

- **A** cài phần "khung" (`RoomSession`/`PeerConnection` gọi `dataChannel.send(codec.encode(...))`, dispatch theo `type` khi nhận).
- **B** cung cấp `EnvelopeCodec.encode/decode` (dựa trên `AesGcmCipher` + `ObjectMapper` đã có ở `crypto`), và định nghĩa đúng record payload cho từng `EnvelopeType` mình cần (`MessagePayload`, `TypingPayload`, `IdentityPayload`, `FileOfferPayload`, `FileChunkPayload`...).
- Mọi thay đổi thêm/bớt `EnvelopeType` hoặc đổi định dạng payload phải báo ngay cho người còn lại — tránh 2 bên serialize lệch nhau.

## 4. Lịch chạy song song

*(Theo thứ tự phụ thuộc kỹ thuật ở Tai-lieu-ky-thuat.md Phần E.9, ánh xạ vào các tuần của đề cương. ⚠️ Bước 10 là stretch goal — nếu hết thời gian, dừng lại và ghi rõ trong báo cáo là "đã cắt theo kế hoạch", không phải thiếu sót.)*

| Tuần | Thành viên A (Mạng) | Thành viên B (Bảo mật & UI) |
|---|---|---|
| 1–2 | Nghiên cứu ICE/STUN/TURN | Nghiên cứu ECDH/AES-GCM/ECDSA |
| 3–4 | Thiết kế giao thức mạng + chốt `DataChannel` với B | Thiết kế UI + chốt `DataChannel` với A |
| 5–6 | Cài signaling server (đã xong) | Cài module mã hoá (ECDH/AES-GCM, đã xong), test trên kênh giả lập |
| 7–9 | Hoàn thiện `WebSocketSignalingClient` + `P2pDataChannel` bằng ice4j | Xây giao diện JavaFX cơ bản, gắn kênh giả lập `LoopbackDataChannel` |
| 10–11 | **Tích hợp**: `EnvelopeCodec` + `RoomSession`/`PeerConnection`, cắm kênh P2P thật vào `Envelope` chung với B; mesh nhiều peer (≥3 máy) | **Tích hợp**: `IdentitySignatureService` (thay UI xác thực thủ công), cắm `EnvelopeCodec` thật vào UI |
| 12–13 | Hỗ trợ A phần vận chuyển cho DM/typing/backfill nếu cần đổi giao thức | DM, trạng thái đang gõ, Markdown/đa dòng, phòng công khai/riêng tư |
| 14–15 | Truyền file ở tầng mạng (chunk, framing, xử lý mất gói) | Mã hoá từng chunk + UI hiển thị tiến trình, cài đặt cá nhân/theme |
| 16 | ⚠️ (stretch, nếu còn thời gian) Hỗ trợ băng thông/độ trễ cho media | ⚠️ (stretch) Video call → Audio call → Screen share (Motion-JPEG/PCM), dừng ở bước nào cũng được nếu hết giờ |
| 17 | Xử lý lỗi kết nối, tối ưu, đo hiệu năng | Đánh giá bảo mật (Wireshark, test giả mạo danh tính) |
| 18–19 | Kiểm thử tích hợp toàn diện, viết báo cáo phần mình phụ trách (cả hai) | Kiểm thử tích hợp toàn diện, viết báo cáo phần mình phụ trách (cả hai) |

## 5. Quy tắc phối hợp

- **Git**: mỗi người làm trên nhánh riêng (`feature/network-*` cho A, `feature/security-ui-*` cho B), merge vào `main` qua pull request, review chéo trước khi merge.
- **Họp nhóm**: định kỳ 1 buổi/tuần để đồng bộ tiến độ và rà lại 2 interface chung (`DataChannel`, `Envelope`) có cần đổi không.
- **Mốc tích hợp** (tuần 10–11) là mốc quan trọng nhất — nên demo thử sớm hơn 1 tuần nếu có thể, để còn thời gian xử lý phát sinh.
- **Tài liệu hoá interface**: mọi thay đổi với `DataChannel`, `Envelope`/`EnvelopeType` (hoặc interface chung khác phát sinh) phải thông báo cho người còn lại ngay, tránh code lệch nhau.
- **Trước khi bắt tay vào bước 10 (video/audio/screen share)**: xác nhận lại với GVHD rằng phạm vi đã mở rộng so với đề cương ban đầu (xem ghi chú ⚠️ đầu file) — tránh làm xong mà không được tính vì "ngoài phạm vi đã duyệt".

## 6. Đóng góp cá nhân trong báo cáo (để ghi rõ khi nộp)

| Chương / Phần | Phụ trách |
|---|---|
| Chương 1 — Tổng quan | A + B (viết chung) |
| Chương 2 — Cơ sở lý thuyết: mục NAT/STUN/TURN/ICE | A |
| Chương 2 — Cơ sở lý thuyết: mục mã hoá ECDH/AES-GCM/ECDSA | B |
| Chương 3 — Thiết kế hệ thống (kiến trúc `RoomSession`/`Envelope`: viết chung phần giao nhau) | A + B (mỗi người phần module của mình) |
| Chương 4 — Cài đặt: phần mạng, signaling, mesh | A |
| Chương 4 — Cài đặt: phần mã hoá, xác thực danh tính, giao diện, file, media | B |
| Chương 5 — Kiểm thử & đánh giá (bao gồm hạn chế bảo mật đã biết — Phần J của Tai-lieu-ky-thuat.md) | A + B (mỗi người phần đo đạc của mình) |
| Chương 6 — Kết luận (nêu rõ nếu bước 10 bị cắt do thời gian) | A + B (viết chung) |

---

*Ghi chú: điền tên/MSSV ở đầu file. Nếu vai trò công việc thực tế thay đổi trong quá trình làm, cập nhật lại bảng ở mục 2 và 6 để báo cáo cuối kỳ phản ánh đúng đóng góp thực tế của từng người. Nếu GVHD không duyệt mở rộng phạm vi sang video/audio/screen share, xoá dòng ⚠️ tương ứng ở mục 2 và 4, quay lại đúng phạm vi đề cương gốc.*
