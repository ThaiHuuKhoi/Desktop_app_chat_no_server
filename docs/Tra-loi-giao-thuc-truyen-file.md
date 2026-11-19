# TRẢ LỜI: THỐNG NHẤT GIAO THỨC TRUYỀN FILE

**Người gửi:** A (`KOTa2k4`) · **Người nhận:** B (`Quanghuy009`) · **Trả lời cho:** bản đề xuất ngày 15/11/2026

**Tóm tắt:** A đồng ý hướng đi chung. Có **3 lỗ hổng phải bổ sung** (mục 1) và **vài thay đổi nhỏ ở API D6** (mục 3). Phần của A **đã code xong và có test** (mục 4). Code của A dùng thẳng các payload và `EnvelopeType` trong nhánh `feature/file-transfer-protocol` của B; A chỉ thêm `FileTransferReason`.

---

## Bảng trả lời

| # | A | Ý kiến |
|---|---|---|
| D1 | ✏️ | Đồng ý 5 bản tin. Cần thêm quy tắc gửi lại cho OFFER/ANSWER/CANCEL (lỗ hổng 1). |
| D2 | ✅ | Payload trong nhánh `feature/file-transfer-protocol` của B khớp D1, A dùng luôn. Lưu ý: PR `fix/security-hardening` của A cũng thêm `MESSAGE_ACK` vào cuối enum, nên PR nào merge sau sẽ gặp xung đột 1 dòng; xếp `MESSAGE_ACK` sau `FILE_CANCEL` là xong. |
| D3 | ✅ | |
| D4 | ✏️ | Đồng ý `contiguousUpTo` + `receivedAbove` + `verified`. Bổ sung cách xử lý khi mất ACK cuối (lỗ hổng 2). Với cửa sổ 32 thì `receivedAbove` không vượt 32. |
| D5 | ✏️ | Đồng ý các giá trị, nhưng **trần chunk hạ xuống 16 KB** (giải thích bên dưới). |
| D6 | ✏️ | Đổi nhỏ: `start()` tự gửi offer, thêm `onAnswer` / `onCancel` / scheduler (mục 3). |
| D7 | ✅ | Code nằm ở `p2p-core/.../p2p/filetransfer/`. |
| D8 | ✏️ | Thêm mã `BUSY`. Huỷ cả khi `PeerConnection` bị thay thế (ICE kết nối lại), không chỉ khi nhận `PEER_LEFT`. |
| D9 | ✏️ | Thêm `BUSY` (đã có trong `FileTransferReason`). |
| D10 | ✅ | Đã có sẵn `FileTransferLimits.validate()` và `expectedChunkLength()` để B dùng. |
| D11 | ✅ | |

**Về D5, vì sao trần chunk là 16 KB:** chunk 4 KB sau khi mã hoá thành khoảng 7,3 KB, vẫn lớn hơn MTU 1500 nên vẫn bị chia mảnh IP. Lý do thực sự để chọn chunk nhỏ là **ít mảnh hơn thì xác suất mất cả gói thấp hơn**. Chunk 32 KB thì gói vào khoảng 58,5 KB, sát giới hạn 65 507 byte của `P2pDataChannel`, mà khi qua TURN còn bị cộng thêm header. Chunk 16 KB cho gói khoảng 29 KB, an toàn.

---

## 1. Ba lỗ hổng cần bổ sung

**Lỗ hổng 1: các bản tin điều khiển cũng có thể mất.** `P2pDataChannel` là UDP thuần, nên OFFER, ANSWER và CANCEL đều có thể mất. Thống nhất như sau:
- **Bên gửi** gửi lại `FILE_OFFER` mỗi 1 s cho tới khi nhận được `FILE_ANSWER`. Quá 60 s thì báo `TIMEOUT`. *(A đã làm.)*
- **Bên nhận** khi thấy một `FILE_OFFER` có `fileId` đã biết thì **gửi lại đúng câu trả lời cũ**, không hỏi lại người dùng. Nếu người dùng chưa bấm gì thì im lặng. *(B làm.)*
- **Bên nhận** khi nhận chunk của một `fileId` không biết hoặc đã huỷ thì trả `FILE_CANCEL` với lý do tương ứng. Như vậy dù bản CANCEL trước đó có mất, bên gửi vẫn dừng được. *(B làm.)*

**Lỗ hổng 2: mất bản `FILE_ACK verified=true` cuối cùng thì giao dịch bị treo.**
- **Bên nhận** giữ trạng thái "đã xong" của `fileId` khoảng 30 s. Nếu nhận thêm **bất kỳ chunk nào** của file đó thì **gửi lại ACK `verified`**. *(B làm.)*
- **Bên gửi**, sau khi đủ chunk mà chưa có `verified`, gửi lại chunk cuối theo backoff (500 ms … 4 s), tối đa 10 lần. Quãng chờ này đủ để bên nhận băm SHA-256 file 100 MB. Hết lượt thì báo `TIMEOUT`. *(A đã làm.)*

**Lỗ hổng 3: trạng thái đang bận.** Nếu đang truyền một file với peer này mà nhận được offer thứ hai, bên nhận trả `FILE_ANSWER(accepted=false, reason="BUSY")`.

---

## 2. Bên nhận phải làm gì (checklist cho `FileReceiver` của B)

Code tham khảo: `p2p-core/src/test/.../filetransfer/TestFileReceiver.java`. Đây là bên nhận tối thiểu, giữ dữ liệu trong bộ nhớ, đã chạy được với sender của A.

1. Kiểm tra offer bằng `FileTransferLimits.validate(offer)` (sai thì trả `INVALID_OFFER`) và `isTooLarge(offer)` (quá lớn thì trả `FILE_TOO_LARGE`).
2. Offer trùng thì trả lại câu trả lời cũ (lỗ hổng 1).
3. Với mỗi chunk, kiểm tra `0 ≤ index < totalChunks` và `data.length == FileTransferLimits.expectedChunkLength(offer, index)`. Chunk trùng thì bỏ qua.
4. Gửi `FILE_ACK` sau mỗi **8 chunk mới**, hoặc sau **50 ms** không có chunk mới.
5. Khi đủ chunk thì kiểm tra SHA-256. Khớp thì gửi `FILE_ACK(contiguousUpTo=totalChunks, receivedAbove=[], verified=true)`. Không khớp thì xoá file `.part` và gửi `FILE_CANCEL(CHECKSUM_MISMATCH)`.
6. Đã xong mà vẫn nhận chunk của file đó thì gửi lại ACK `verified` (lỗ hổng 2).
7. File rỗng: nhận offer xong thì tạo file rỗng và gửi ngay ACK `verified`.

---

## 3. API đã code (thay đổi so với D6)

```java
public interface ChunkSource {
    FileOfferPayload offer();
    FileChunkPayload readChunk(int index) throws IOException;   // thêm throws IOException -> sender báo IO_ERROR
}

public final class ReliableChunkSender {
    public ReliableChunkSender(ChunkSource source,
                               Consumer<FileOfferPayload> sendOffer,   // MỚI: sender tự gửi lại offer
                               Consumer<FileChunkPayload> sendChunk,
                               ReliableTransferListener listener,
                               ScheduledExecutorService scheduler,     // MỚI: dùng chung, sender không tự shutdown
                               ReliableTransferConfig config);         // có thể bỏ, mặc định là DEFAULT = D5
    public void start();                          // ĐỔI: gửi FILE_OFFER và chờ trả lời (không phải gọi sau khi có ANSWER)
    public void onAnswer(FileAnswerPayload a);    // MỚI: chuyển mọi FILE_ANSWER vào đây
    public void onAck(FileAckPayload ack);
    public void onCancel(FileCancelPayload c);    // MỚI: chuyển FILE_CANCEL nhận được vào đây -> onFailed(reason)
    public void cancel();                         // huỷ phía mình, KHÔNG gọi listener
    public String fileId();
    public boolean isFinished();
}

public interface ReliableTransferListener {      // giữ nguyên như D6
    void onProgress(int ackedChunks, int totalChunks);
    void onCompleted();
    void onFailed(String reason);
}
```

Quy ước khi nối vào `P2pChatSession` (phần B):
- Các callback chạy trên thread mạng hoặc thread hẹn giờ, **không phải JavaFX thread**. Muốn cập nhật UI thì dùng `Platform.runLater`.
- `sendOffer` và `sendChunk` được gọi **trong khoá** của sender, nên phải nhanh và không chặn. Dùng `roomSession.sendTo(...)` là được.
- **Ai gửi `FILE_CANCEL`:** sender **không** tự gửi. Khi `onFailed` báo `TIMEOUT` hoặc `IO_ERROR`, B gửi `FILE_CANCEL(reason)`. Khi người dùng bấm Huỷ, B gọi `cancel()` rồi gửi `FILE_CANCEL(CANCELLED_BY_USER)`. Nếu lỗi là do bên kia huỷ hoặc từ chối thì không cần gửi lại.
- Khi nhận `PEER_LEFT` hoặc kết nối bị thay thế, gọi `cancel()`.
- Tham số `namespace` của Envelope để `null`: dùng `roomSession.sendTo(peerId, type, payload)`.

Các file trong `p2p-core/.../filetransfer/`: `ChunkSource`, `ReliableChunkSender`, `ReliableTransferListener`, `ReliableTransferConfig`, `FileTransferLimits`. Trong `common/.../protocol/`: `FileTransferReason` (5 payload là của B).

---

## 4. Test đã có (A)

| Test | Kịch bản |
|---|---|
| `ReliableChunkSenderTest` (10 test) | Mạng hoàn hảo; mất 20% + nhân đôi 10% + đảo thứ tự; mất ACK `verified` cuối; bị từ chối; không có trả lời (gửi lại offer rồi TIMEOUT); chunk không bao giờ tới (đúng 1 cửa sổ rồi TIMEOUT); file rỗng; CHECKSUM_MISMATCH; huỷ giữa chừng; offer sai quy ước |
| `FileTransferOverUnreliablePeerConnectionTest` | 1 MB qua `PeerConnection` thật (ECDH + AES-GCM + chống replay) trên kênh mất 15%, nhân đôi 5%: khoảng 1,7 s trên máy local, dữ liệu đúng từng byte |
| `NetworkImpairment`, `UnreliableDataChannel` | Kênh giả lập mạng xấu (D7), B dùng lại được cho test phía nhận |

Chạy: `mvn test` từ thư mục gốc (test của p2p-core cần module signaling-server).

---

## 5. Lưu ý khác

- **PR `fix/security-hardening` (A)** gồm: `MESSAGE` có `MESSAGE_ACK` và gửi lại tối đa 6 lần; chữ ký `PEER_IDENTITY` gắn với khoá ECDH (chống MITM); TURN credential chuyển ra khỏi jar; signaling server chỉ relay cho session đã JOIN, tối đa 8 peer mỗi phòng. **Client cũ và client mới không chat được với nhau.**
- PR này (`feature/file-transfer-reliable-sender`) dựa trên nhánh của B, nên cần merge sau PR của B.
