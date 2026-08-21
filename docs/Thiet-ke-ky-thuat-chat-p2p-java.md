Thiet ke ky thuat chi tiet — chat-p2p-java

# THIẾT KẾ KỸ THUẬT CHI TIẾT: CHAT-P2P-JAVA

*Tài liệu này là bản thiết kế thi công (implementation spec), viết để code thẳng theo — không còn ở mức "ý tưởng" như đề cương. Mọi tên lớp, tên phương thức, định dạng dữ liệu nêu ở đây là thứ sẽ gõ ra trong IDE. Đọc trước [Tai-lieu-ky-thuat-Chitchatter.md](Tai-lieu-ky-thuat-Chitchatter.md) (cơ chế thật của bản gốc) và [De-cuong-Chat-P2P-Java.md](De-cuong-Chat-P2P-Java.md) (mục tiêu đồ án). Đối chiếu code hiện có trong repo: `common/`, `crypto/`, `p2p-core/`, `signaling-server/`, `client-javafx/`.*

**Phạm vi**: làm đủ 12 chức năng của chitchatter, xác thực peer theo kiểu **tự động bằng chữ ký số** (giống bản gốc, không phải fingerprint thủ công như đề cương gốc), làm dần từng chức năng theo thứ tự phụ thuộc kỹ thuật ở mục 9.

---

## 1. Nguyên tắc thiết kế xuyên suốt

1. **Không có DTLS như WebRTC** → mọi byte đi qua `DataChannel.send()` (trừ bản thân handshake khoá) đều phải được mã hoá AES-GCM ở tầng ứng dụng trước khi gửi. Đây là khác biệt cốt lõi so với chitchatter (mục 6 của tài liệu phân tích).
2. **Một `DataChannel` = một kết nối 1-1 với một peer.** Phòng có N peer → N kết nối `DataChannel` song song (mesh đầy đủ, giống Trystero), quản lý tập trung bởi một lớp mới: `RoomSession` (thiết kế ở mục 4).
3. **Không có `PeerRoom.makeAction` đa kênh của Trystero** → tự đóng gói bằng một **envelope chung** duy nhất trên mỗi `DataChannel`, phân loại bằng trường `type` (tương đương `PeerAction`). Thiết kế ở mục 3.
4. **Signaling server chỉ relay, không đọc nội dung** — giữ nguyên nguyên tắc đã có, không đổi.
5. **Ephemeral**: chỉ có đúng 1 thứ được lưu bền trên đĩa — cặp khoá danh tính + cài đặt người dùng (mục 8.6). Tin nhắn/danh sách phòng không bao giờ ghi ra đĩa.

## 2. Sơ đồ module & luồng dữ liệu tổng thể

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

## 3. Giao thức tầng dữ liệu P2P — `Envelope`

### 3.1 `EnvelopeType` (tương đương `PeerAction` của chitchatter, xem mục 4 tài liệu phân tích)

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
    MEDIA_FRAME            // 1 khung hình video/screen-share (xem mục 8.5)
}
```

### 3.2 Cấu trúc `Envelope` (đóng gói mọi thứ gửi qua `DataChannel`)

```java
// common/src/main/java/com/datn/chatp2p/common/protocol/Envelope.java
public final class Envelope {
    private EnvelopeType type;
    private String namespace;   // "g" (group) hoặc "dm" (direct message) - xem 3.3
    private String targetPeerId; // null nếu broadcast trong action gửi; 
                                  // ở envelope THẬT thì luôn null vì DataChannel đã là 1-1
    private long timestamp;
    private byte[] payload;     // JSON đã serialize của object cụ thể theo `type`, TRƯỚC KHI mã hoá
    // getters/setters...
}
```

- `Envelope` không tự chứa payload đã mã hoá — **lớp `EnvelopeCodec` (mục 3.4) lo việc mã hoá toàn bộ envelope đã serialize thành 1 khối `byte[]`** rồi mới gọi `DataChannel.send()`. Bên nhận giải mã trước, parse `Envelope` sau.
- Vì mỗi `DataChannel` đã là kết nối 1-1 với đúng 1 peer, **không cần `target` như Trystero** (Trystero broadcast trên 1 kết nối chung nên cần lọc theo target; ở đây gửi tới ai thì gọi đúng `DataChannel` của peer đó — đơn giản hơn bản gốc).
- `namespace` giữ lại để phân biệt tin nhắn **nhóm** hiển thị ở khung chat chung, và tin nhắn **DM** hiển thị ở tab riêng với 1 peer — dù về mặt transport chúng đi qua cùng 1 `DataChannel` (vì đằng nào cũng là kết nối 1-1), namespace chỉ ảnh hưởng UI hiển thị ở đâu, không ảnh hưởng routing mạng.

### 3.3 Định dạng payload cụ thể theo từng `EnvelopeType`

| EnvelopeType | Payload (Java record, serialize bằng Jackson) |
|---|---|
| `MESSAGE` | `record MessagePayload(String id, String authorId, String text, long timeSent)` |
| `MESSAGE_TRANSCRIPT` | `record TranscriptPayload(List<MessagePayload> messages)` |
| `TYPING_STATUS_CHANGE` | `record TypingPayload(boolean isTyping)` |
| `PEER_IDENTITY` | `record IdentityPayload(String userId, String customUsername, String publicKeyBase64, String signatureBase64)` |
| `FILE_OFFER` | `record FileOfferPayload(String fileId, String fileName, long fileSize, int totalChunks, boolean isInlineMedia)` — `fileId == null` nghĩa là thu hồi offer trước đó |
| `FILE_CHUNK` | `record FileChunkPayload(String fileId, int chunkIndex, byte[] ciphertextChunk)` — bản thân chunk **đã được mã hoá 1 lớp nữa bên trong** payload (xem mục 8.4) trước khi cả `Envelope` bị mã hoá lần nữa bởi `EnvelopeCodec` — chấp nhận double-encrypt để đơn giản hoá code, chi phí CPU không đáng kể |
| `AUDIO_CHANGE` / `VIDEO_CHANGE` / `SCREEN_SHARE_CHANGE` | `record MediaStatePayload(boolean isActive)` |
| `MEDIA_FRAME` | `record MediaFramePayload(String streamId, MediaStreamType streamType, byte[] jpegFrame)` (xem mục 8.5) |

### 3.4 `EnvelopeCodec` — lớp trung tâm nối `crypto` với `p2p-core`

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

Đây chính là điểm mà `crypto` (đã có `AesGcmCipher`) và `common` (model + Jackson) gặp nhau ở tầng `p2p-core`, thay thế cho việc `RoomController` tự gọi `AesGcmCipher.encrypt/decrypt` trực tiếp như bản demo hiện tại (`RoomController.onSendMessage`/`onEncryptedMessageReceived` sẽ được đơn giản hoá đi rất nhiều khi có lớp này — xem mục 10).

## 4. `RoomSession` — quản lý nhiều peer trong 1 phòng (thay thế `PeerRoom` của Trystero)

```java
// p2p-core/src/main/java/com/datn/chatp2p/p2p/RoomSession.java
public final class RoomSession {
    private final String roomId;
    private final String selfPeerId;
    private final KeyPair identityKeyPair;      // ECDSA, ký danh tính - xem mục 5
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

1. `RoomSession` tạo `KeyPair` ECDH tạm thời cho phiên này (KHÔNG dùng chung với `identityKeyPair` ở mục 5 — ECDH để trao khoá AES, ECDSA/RSA để ký danh tính, 2 mục đích khác nhau, 2 cặp khoá khác nhau).
2. Gửi public key ECDH của mình qua kênh signaling (payload trong `SignalMessage.OFFER`, hoặc gộp vào bước ICE — xem mục 6) tới peer mới.
3. Khi `P2pDataChannel` giữa 2 bên mở xong (ICE thành công), 2 bên trao public key ECDH qua chính `DataChannel` vừa mở (gói tin đầu tiên, **không mã hoá** vì đây chính là bước tạo ra khoá mã hoá — xem mục 5.2 để biết cách tránh MITM ở bước này).
4. `KeyExchangeService.deriveSharedSecret(...)` → `SecretKey` → tạo `EnvelopeCodec` cho `PeerConnection` này.
5. Gửi `Envelope(PEER_IDENTITY, ...)` đã mã hoá bằng khoá vừa có — tương đương `PEER_METADATA` của chitchatter.
6. Nếu phòng công khai và đã có lịch sử chat: gửi `Envelope(MESSAGE_TRANSCRIPT, ...)`.

## 5. Xác thực danh tính tự động (thay thế fingerprint thủ công hiện tại)

### 5.1 Đổi thuật toán trong `crypto`

Chitchatter dùng RSASSA-PKCS1-v1_5 (RSA-2048) để **ký**, tách biệt với ECDH để **trao khoá**. Ở Java, dùng **ECDSA (cùng họ đường cong secp256r1 đã dùng cho ECDH)** cho gọn — không cần thêm thuật toán RSA riêng:

```java
// crypto/src/main/java/com/datn/chatp2p/crypto/IdentitySignatureService.java
public final class IdentitySignatureService {
    // Sinh 1 lần, lưu bền (mục 8.6) - đây là "danh tính" lâu dài của người dùng,
    // KHÁC với KeyPair ECDH tạm thời sinh mỗi phiên kết nối ở mục 4 bước 1.
    public static KeyPair generateIdentityKeyPair(); // EC, secp256r1, dùng cho "SHA256withECDSA"

    public static byte[] sign(PrivateKey identityPrivateKey, String message); // message = "${roomId}_${userId}"
    public static boolean verify(PublicKey identityPublicKey, byte[] signature, String message);
}
```

### 5.2 Thông điệp thách thức & luồng xác thực

Giữ nguyên công thức của chitchatter: `challenge = roomId + "_" + userId` (mục 5, tài liệu phân tích). Khi B nhận `PEER_IDENTITY` từ A:

```java
boolean verified = IdentitySignatureService.verify(
    decodedPublicKeyOfA, signatureFromA, roomId + "_" + userIdOfA);

peer.setVerificationState(verified ? PeerVerificationState.VERIFIED : PeerVerificationState.UNVERIFIED);
```

Không cần dialog "So sánh fingerprint" nữa — `PeerListCell` (đã có) chỉ cần **bỏ nút "Xác thực"**, hiển thị badge theo `verificationState` do `RoomSession` tự cập nhật khi `PEER_IDENTITY` tới. **Giữ nguyên `Fingerprint.of(...)`** trong `crypto` — vẫn hữu ích để hiển thị public key dạng rút gọn cho người dùng tò mò xem (giống `components/PublicKey` của chitchatter), chỉ không dùng nó làm cơ chế xác thực chính nữa.

### 5.3 Lưu ý bảo mật cần ghi vào báo cáo

Đúng như mục 5 tài liệu phân tích: cơ chế này chống **mạo danh lặp lại** (ai đó tự nhận là "Khôi" ở lần join thứ 2 mà không có đúng private key sẽ bị phát hiện), **không** chống tuyệt đối MITM ở lần gặp đầu tiên. Đây là hạn chế đã biết, cần nêu rõ trong báo cáo (mục "Đánh giá bảo mật") thay vì giấu đi.

## 6. Kết nối P2P thật — hoàn thiện `P2pDataChannel` bằng ice4j

*(Việc lớn nhất còn thiếu, mục 14 của tài liệu phân tích — chi tiết hoá thành các bước code được ở đây)*

### 6.1 Mở rộng giao thức signaling để mang thêm ICE credentials

`SignalMessage.payload` (đã có, kiểu `String`) sẽ mang **JSON** của 1 trong các cấu trúc sau tuỳ `SignalType`:

```java
// common/model, dùng làm nội dung của SignalMessage.payload (Jackson serialize -> String)
record IceOfferPayload(String ufrag, String password, List<String> candidates) {}
record IceAnswerPayload(String ufrag, String password, List<String> candidates) {}
record IceCandidatePayload(String candidate) {} // 1 candidate mới phát hiện sau khi đã gửi offer/answer (trickle ICE)
```

### 6.2 Luồng thiết lập kết nối bằng ice4j (thư viện `org.jitsi:ice4j`)

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
  | 9. Mở kênh dữ liệu THẬT trên candidate pair đó (mục 6.3)               |
```

### 6.3 Kênh dữ liệu thật sau khi ICE xong

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

**Lưu ý quan trọng về bảo mật tầng transport**: vì không dùng DTLS như WebRTC, `P2pDataChannel` **truyền UDP thô** — đây chính là lý do mục 1.1 (mọi payload phải tự mã hoá AES-GCM ở tầng `Envelope` trước khi gọi `send()`) là **bắt buộc**, không phải tuỳ chọn. Ghi rõ điều này trong báo cáo ở phần so sánh với WebRTC.

### 6.4 `WebSocketSignalingClient` — hoàn thiện thay vì stub

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

## 7. Nhiều peer trong phòng (mesh)

Không cần thiết kế thêm — hệ quả tự nhiên của `RoomSession` (mục 4) quản lý `Map<peerId, PeerConnection>`. Khi `SignalingClient.onPeerJoined` báo có peer mới, `RoomSession` tự chạy lại toàn bộ luồng mục 6.2 với peer đó, **độc lập** với các `PeerConnection` đang có — đúng kiểu mesh đầy đủ N×(N-1)/2 kết nối như Trystero.

`RoomController` (client-javafx) chỉ cần lắng nghe `RoomSession.onPeerJoined/onPeerLeft` để cập nhật `ObservableList<Peer>` — logic UI (PeerListCell, MessageListCell) **giữ nguyên không đổi** so với bản demo hiện tại.

## 8. Chi tiết từng chức năng còn lại

### 8.1 Nhắn tin nhóm + đa dòng + Markdown

- Gửi: `roomSession.broadcast(EnvelopeType.MESSAGE, new MessagePayload(...))`.
- Nhận: `RoomSession.onEnvelope(MESSAGE, (peerId, env) -> ...)`, parse `MessagePayload`, thêm vào `ObservableList<ChatMessage>` qua `Platform.runLater` (y hệt cơ chế demo hiện tại, chỉ đổi nguồn từ `LoopbackDataChannel` sang `RoomSession`).
- Đa dòng: `TextArea` thay cho `TextField` trong `room.fxml`, bắt Shift+Enter để xuống dòng, Enter thường để gửi (`TextArea` không có `onAction`, phải tự bắt `KeyEvent` bằng `setOnKeyPressed`).
- Markdown: dùng thư viện `com.vladsch.flexmark:flexmark-all` (Java Markdown → HTML), hiển thị bằng `javafx.scene.web.WebView` thay vì `Label` cho nội dung tin nhắn (hoặc `Label` với `-fx-font-family` monospace cho code block nếu muốn tránh phụ thuộc `WebView`/JCEF nặng — cân nhắc theo thời gian còn lại).

### 8.2 Direct message (DM)

- UI: click vào 1 peer trong `PeerListCell` → mở 1 tab/panel chat riêng (bổ sung `TabPane` trong `room.fxml`, 1 tab "Nhóm" + N tab theo từng peer đang chat riêng).
- Gửi: `roomSession.sendTo(peerId, EnvelopeType.MESSAGE, payload)` với `namespace = "dm"`.
- Lưu trữ: `RoomController` giữ `Map<String peerId, ObservableList<ChatMessage>> directMessageLogs` tách biệt khỏi `ObservableList<ChatMessage> groupMessages` — tương đương `ShellMessageLog.directMessageLog` của chitchatter.

### 8.3 Trạng thái đang gõ

- `messageField`/`TextArea` gắn `textProperty().addListener(...)`, debounce 2 giây bằng `java.util.Timer`/`PauseTransition` (JavaFX có sẵn `javafx.animation.PauseTransition`, gọn hơn tự viết debounce).
- Gửi `Envelope(TYPING_STATUS_CHANGE, new TypingPayload(isTyping))` tới peer đang chat cùng (DM) hoặc broadcast (nhóm).
- Nhận: cập nhật 1 `Label` nhỏ trong `PeerListCell` hoặc phía trên khung nhập ("Khôi đang nhập...") — tương đương `TypingStatusBar.tsx`.

### 8.4 Truyền file mã hoá qua chunk

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
        // Chờ FILE_OFFER_ACCEPTED (bổ sung thêm 1 EnvelopeType nếu muốn có bước "đồng ý nhận" tường minh,
        // hoặc đơn giản hoá: cứ gửi luôn toàn bộ chunk ngay sau OFFER, bên nhận tự quyết định lưu hay bỏ)
        sendChunks(file, fileId, session, targetPeerIds);
    }

    private void sendChunks(File file, String fileId, RoomSession session, Set<String> targetPeerIds) {
        try (var in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buf = new byte[CHUNK_SIZE];
            int index = 0, read;
            while ((read = in.read(buf)) != -1) {
                byte[] chunk = Arrays.copyOf(buf, read);
                // Mã hoá chunk bằng CHÍNH khoá phiên AES-GCM đã có với từng peer -
                // KHÔNG cần khoá riêng theo "tên phòng" như secure-file-transfer của bản gốc,
                // vì ở đây file đi thẳng qua kênh 1-1 đã mã hoá sẵn (không qua mạng công khai như WebTorrent).
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
        // cập nhật progress bar = receivedCount / totalChunks
        if (buffer.isComplete()) assembleAndSave(buffer);
    }

    private void assembleAndSave(ChunkBuffer buffer) {
        // ghi ra thư mục tải về của người dùng (vd. System.getProperty("user.home") + "/Downloads"),
        // nối các chunk theo đúng thứ tự index -> FileOutputStream
    }
}
```

- File ảnh/audio nhỏ hiển thị inline: sau khi `assembleAndSave`, nếu `isInlineMedia`, tạo `ChatMessage`-like entry đặc biệt (thêm 1 subclass hoặc field `attachedFilePath` vào `ChatMessage`) để `MessageListCell` render `ImageView`/`MediaView` thay vì `Label`.
- UI: nút 📎 cạnh ô nhập, `FileChooser` của JavaFX; hiển thị `ProgressBar` cho cả gửi lẫn nhận (tương đương `RoomFileUploadControls.tsx`).

### 8.5 Video call / Audio call / Screen share — phương án khả thi trong Java thuần

Java không có WebRTC/codec built-in. Đề xuất phương án **đơn giản hoá có chủ đích** (ghi rõ lý do trong báo cáo — đánh đổi hiệu năng lấy khả năng triển khai trong thời gian đồ án):

| Nhu cầu | Bản gốc (WebRTC) | Đề xuất cho chat-p2p-java |
|---|---|---|
| Video call | Codec H.264/VP8 qua `RTCPeerConnection` transceiver | **Motion-JPEG tự chế**: chụp webcam định kỳ (~10-15 fps) bằng thư viện [`webcam-capture`](https://github.com/sarxos/webcam-capture) (`com.github.sarxos:webcam-capture`) → mỗi khung hình nén JPEG (`javax.imageio.ImageIO`, quality ~0.5) → gửi qua `Envelope(MEDIA_FRAME, ...)` như 1 tin nhắn nhị phân bình thường (đã mã hoá AES-GCM sẵn theo cơ chế chung) |
| Audio call | Codec Opus qua `RTCPeerConnection` | Capture PCM bằng `javax.sound.sampled.TargetDataLine` (built-in JDK, không cần thư viện ngoài), chia chunk ~20ms, gửi thẳng PCM thô (hoặc nén nhẹ bằng μ-law 8-bit nếu cần giảm băng thông) qua `Envelope(MEDIA_FRAME, streamType=AUDIO, ...)`, phát lại bằng `SourceDataLine` |
| Screen share | `getDisplayMedia` | `java.awt.Robot.createScreenCapture(...)` (built-in JDK) định kỳ (~5-8 fps đủ cho demo/thuyết trình) → JPEG → cùng cơ chế `MEDIA_FRAME` như video |

- **Vì sao chọn cách này thay vì JavaCV/FFmpeg**: JavaCV kéo theo native binding nặng (OpenCV/FFmpeg build cho từng OS), rủi ro build fail cao trên máy chấm đồ án, không đáng đánh đổi khi mục tiêu là chứng minh khái niệm ("proof of concept") chứ không phải chất lượng video sản xuất. Motion-JPEG qua chính kênh dữ liệu đã có sẵn (`Envelope`/`DataChannel`) **tái dùng toàn bộ hạ tầng mã hoá + P2P đã xây**, không cần mở thêm kênh media riêng như WebRTC — đơn giản hơn nhiều so với bản gốc.
- UI phía nhận: `ImageView` cập nhật theo từng `MEDIA_FRAME` nhận được (giống hiển thị 1 GIF thủ công), đặt trong `PeerVideoDisplay` (tương đương `PeerVideo.tsx`).
- `AUDIO_CHANGE`/`VIDEO_CHANGE`/`SCREEN_SHARE_CHANGE` chỉ đồng bộ trạng thái icon bật/tắt trên `PeerListCell`, không mang dữ liệu media (giống bản gốc, xem mục 9 tài liệu phân tích).
- **Ghi chú khối lượng công việc**: đây là nhóm chức năng nặng nhất trong 12 chức năng — nên làm **sau cùng**, sau khi đã có kênh P2P + mã hoá + chat/file ổn định (xem thứ tự ở mục 9).

### 8.6 Cài đặt cá nhân, theme sáng/tối, lưu trữ cục bộ

- Thay `localforage`/IndexedDB bằng **`java.util.prefs.Preferences`** (built-in JDK, lưu vào registry trên Windows / file trên Linux/macOS — không cần thư viện ngoài) *hoặc* đơn giản hơn: 1 file JSON tại `System.getProperty("user.home") + "/.chat-p2p-java/settings.json"` (dễ debug, dễ demo, dễ giải thích trong báo cáo hơn Preferences API "ẩn" trong registry).
- `UserSettingsService` (module `client-javafx` hoặc `common`): load lúc khởi động app (trước khi hiện `HomeView`), save khi đổi cài đặt hoặc lúc thoát app.
- Nội dung lưu, đối chiếu `UserSettings` của chitchatter (mục 11 tài liệu phân tích): `colorMode`, `userId` (UUID cố định), `customUsername`, `identityKeyPair` (Base64 của `PublicKey`/`PrivateKey` — encode bằng `getEncoded()`, decode bằng `X509EncodedKeySpec`/`PKCS8EncodedKeySpec`), `playSoundOnNewMessage`, `showNotificationOnNewMessage`, `showActiveTypingStatus`.
- Theme sáng/tối: 2 file CSS (`app-light.css` hiện có đổi tên, thêm `app-dark.css`), `ChatApplication`/`RoomController` chọn `scene.getStylesheets()` theo `UserSettings.colorMode`.

### 8.7 Nhúng ứng dụng (SDK/iframe)

**Không áp dụng cho desktop app** — đây là khái niệm web-only (iframe). Bỏ khỏi phạm vi thi công (không phải "chưa làm" mà là "không có ý nghĩa với app desktop"), khác với 11 chức năng còn lại.

## 9. Thứ tự triển khai đề xuất (theo phụ thuộc kỹ thuật)

```
1. Hoàn thiện WebSocketSignalingClient (mục 6.4)         ─┐
2. Hoàn thiện P2pDataChannel bằng ice4j (mục 6.2-6.3)     ─┤ Nền tảng bắt buộc trước,
3. EnvelopeCodec + RoomSession + PeerConnection (mục 3-4) ─┤ mọi chức năng khác phụ thuộc vào đây
4. Xác thực danh tính tự động (mục 5)                     ─┘
   -> Mốc: 2 máy thật qua Internet chat text được với nhau, có xác thực tự động
5. Nhiều peer / mesh (mục 7) - hệ quả tự nhiên của bước 3, test với ≥3 máy
6. DM (mục 8.2), Typing status (mục 8.3) - UI nhỏ, rủi ro thấp
7. Markdown + đa dòng (mục 8.1) - UI thuần, không đụng mạng
8. Truyền file (mục 8.4) - phức tạp vừa, không phụ thuộc UI ở bước 6-7
9. Cài đặt cá nhân + theme (mục 8.6) - độc lập, làm lúc nào cũng được, nên chen vào lúc rảnh
10. Video call -> Audio call -> Screen share (mục 8.5) - nặng nhất, làm SAU CÙNG,
    dừng ở bước nào cũng được nếu hết thời gian (đề cương đã cho phép "hoàn thiện nếu đủ khả năng")
```

## 10. Việc phải sửa trong code hiện có khi bắt tay vào bước 1-4

- `RoomController.start(...)`: thay đoạn tạo `LoopbackDataChannel.Pair` + `DemoPeerSimulator` bằng tạo `RoomSession` thật (`new RoomSession(roomId, selfPeerId, identityKeyPair, webSocketSignalingClient)`), gọi `roomSession.join()`.
- `RoomController.onSendMessage()`: đổi `myChannel.send(AesGcmCipher.encrypt(...))` thành `roomSession.broadcast(EnvelopeType.MESSAGE, new MessagePayload(...))` — bỏ hẳn việc gọi `AesGcmCipher` trực tiếp ở tầng UI (chuyển xuống `EnvelopeCodec`, đúng nguyên tắc tách lớp).
- `PeerListCell`: bỏ nút "Xác thực" thủ công + `RoomController.onVerifyRequested(...)`, thay bằng lắng nghe `verificationState` cập nhật tự động từ `RoomSession`.
- `P2pDataChannel`, `WebSocketSignalingClient`: xoá `UnsupportedOperationException`, cài đặt thật theo mục 6.
- `pom.xml` của `p2p-core`: thêm dependency `org.jitsi:ice4j` (kiểm tra version mới nhất trên Maven Central trước khi thêm).
- `pom.xml` của `client-javafx`: thêm `com.github.sarxos:webcam-capture` (khi tới bước 10), `com.fasterxml.jackson.core:jackson-databind` (để `EnvelopeCodec`/`Envelope` serialize — hiện `client-javafx` chưa có Jackson).

## 11. Những gì KHÔNG đổi so với code hiện tại

Để tránh hiểu nhầm "phải viết lại từ đầu" — các phần sau **giữ nguyên**, chỉ đấu nối lại nguồn dữ liệu:

- `crypto/KeyExchangeService`, `AesGcmCipher`, `Fingerprint` — dùng y nguyên bên trong `EnvelopeCodec`.
- `common/model/ChatMessage`, `Peer`, `PeerVerificationState` — dùng y nguyên cho UI.
- `signaling-server` toàn bộ — chỉ mở rộng `SignalMessage.payload` mang thêm dữ liệu ICE (mục 6.1), không đổi kiến trúc `RoomRegistry`/`SignalingWebSocketHandler`.
- `MessageListCell`, `home.fxml`, `RoomNameGenerator`, CSS — giữ nguyên, chỉ `room.fxml` cần bổ sung `TabPane` (DM), `TextArea` (đa dòng), nút đính kèm file, khu vực hiển thị video.

## 12. Đối chiếu toàn diện — bản gốc chitchatter vs giải pháp thay thế Java

*Mục này gộp lại và mở rộng toàn bộ các quyết định "giữ theo bản gốc" / "phải thiết kế lại" đã rải rác ở các mục trên, trình bày theo từng khối kỹ thuật để dễ tra cứu khi code. Với mỗi khối: **(a)** cơ chế thật của chitchatter, **(b)** vì sao không mang thẳng sang Java được, **(c)** giải pháp thay thế cụ thể (trỏ lại mục đã thiết kế ở trên, hoặc thiết kế mới nếu chưa có).*

### 12.1 Mã hoá kênh truyền

- **(a) Bản gốc**: không tự làm gì — `RTCPeerConnection` bắt buộc DTLS theo chuẩn WebRTC, trình duyệt tự thương lượng khoá lúc thiết lập kết nối. `services/Encryption` của chitchatter **không** tham gia vào việc này.
- **(b) Vì sao không port thẳng**: Java không có `RTCPeerConnection`; kênh UDP tự mở qua ice4j (mục 6.3) hoàn toàn trần trụi, ai chặn được gói tin là đọc được nội dung nếu không tự mã hoá.
- **(c) Giải pháp Java**: bắt buộc mã hoá **mọi** `Envelope` bằng AES-GCM trước khi `DataChannel.send()` (mục 1 nguyên tắc #1, mục 3.4 `EnvelopeCodec`). Khoá phiên lấy từ ECDH giữa đúng 2 peer, sinh 1 lần lúc mở `PeerConnection` (mục 4, bước 1-4). Đây là phần **thêm hẳn ra** so với bản gốc, không phải rút gọn.

### 12.2 Tìm nhau (peer discovery) & signaling

- **(a) Bản gốc**: `trystero.joinRoom(roomId)` — thư viện băm `appId+roomId+password` thành 1 khoá phòng, tự quảng cáo lên **BitTorrent tracker công khai** (hoặc chiến lược khác nếu cấu hình), các peer cùng khoá phòng tự tìm thấy nhau qua đó, tự trao đổi SDP/ICE candidate luôn trong quá trình này — code ứng dụng không thấy bước này.
- **(b) Vì sao không port thẳng**: không có tracker BitTorrent nào "biết" về chat-p2p-java; cũng không có API browser nào lo việc trao đổi SDP hộ.
- **(c) Giải pháp Java**: `signaling-server` tự viết (đã xong, không đổi kiến trúc) đóng đúng vai trò tracker — nhưng **tường minh hơn**: code ứng dụng (`WebSocketSignalingClient`, mục 6.4) trực tiếp gửi/nhận `SignalMessage` chứa OFFER/ANSWER/ICE_CANDIDATE, không có gì "ẩn" trong thư viện như Trystero.

### 12.3 Phòng công khai / phòng riêng tư

- **(a) Bản gốc** (mục 3 tài liệu phân tích): phòng công khai dùng `password = roomId` (ai biết tên phòng là vào được). Phòng riêng tư: `secret = base64(SHA-256("${roomId}_${password}"))`, `secret` này mới là "khoá phòng" thật đưa cho Trystero — nếu không đúng password gốc thì không tính ra đúng `secret`, không đoán được khoá phòng thật, không vào được swarm dù có biết tên phòng hiển thị.
- **(b) Vì sao không port thẳng**: khái niệm này **không phụ thuộc WebRTC/trình duyệt** — hoàn toàn có thể tái tạo y hệt trong Java, chỉ cần đổi chỗ áp dụng (Trystero swarm key → `roomId` gửi cho `signaling-server`).
- **(c) Giải pháp Java** (thiết kế mới, bổ sung cho mục 6): giữ 2 khái niệm tách biệt —
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

### 12.4 Kết nối P2P & xuyên NAT

- **(a) Bản gốc**: `RTCPeerConnection` tự làm ICE gathering, connectivity check, chọn candidate pair — toàn bộ nằm trong engine WebRTC của trình duyệt, lộ ra ngoài đúng 2 việc: SDP offer/answer và ICE candidate (đi qua Trystero).
- **(b) Vì sao không port thẳng**: JVM không có engine WebRTC.
- **(c) Giải pháp Java**: thư viện `ice4j` (dự án Jitsi) làm đúng phần ICE gathering/connectivity check (mục 6.2); phần "mở kênh dữ liệu trên candidate pair đã chọn" mà WebRTC tự cho miễn phí thì Java phải **tự viết** (`P2pDataChannel`, mục 6.3) — đây là phần không có sẵn thư viện nào thay thế trọn gói.

### 12.5 Đa kênh logic trên 1 kết nối (multiplexing)

- **(a) Bản gốc**: `PeerRoom.makeAction(actionName)` của Trystero — mỗi `actionName` là 1 luồng gửi/nhận độc lập trên cùng 1 `RTCDataChannel`, thư viện lo việc gắn nhãn + tách gói.
- **(b) Vì sao không port thẳng**: đây là tính năng riêng của thư viện `trystero`, không tồn tại ngoài hệ sinh thái WebRTC/JS.
- **(c) Giải pháp Java**: tự thiết kế `Envelope { type, namespace, payload }` + `EnvelopeCodec` (mục 3) — mỗi `DataChannel.onReceive` nhận **1 loại byte[] duy nhất**, tự parse `type` để biết đây là tin nhắn/gõ phím/chunk file gì, rồi dispatch — bản chất là tự viết lại đúng cơ chế mà `makeAction` làm, chỉ khác là tường minh trong code thay vì ẩn trong thư viện.

### 12.6 Xác thực danh tính peer

- **(a) Bản gốc** (mục 5 tài liệu phân tích): tự động, ký bằng RSASSA-PKCS1-v1_5 (RSA-2048) trên chuỗi `"${roomId}_${userId}"`, verify bằng public key nhận qua `PEER_METADATA`.
- **(b) Vì sao không port y nguyên**: RSA-2048 vẫn dùng được trong JCA (`KeyPairGenerator.getInstance("RSA")`), **có thể port y hệt nếu muốn** — đây là 1 trong số ít chỗ có thể copy gần như nguyên xi thuật toán. Lý do đổi sang ECDSA (mục 5.1) là lựa chọn thiết kế (gọn hơn, tái dùng cùng đường cong `secp256r1` đã có sẵn cho ECDH), **không phải bắt buộc kỹ thuật**.
- **(c) Giải pháp Java**: `IdentitySignatureService` dùng `SHA256withECDSA` thay vì `SHA256withRSA`, giữ nguyên 100% công thức chuỗi thách thức và luồng gửi/verify qua `PEER_IDENTITY` (mục 5.2). Nếu muốn bám sát tuyệt đối bản gốc, chỉ cần đổi `KeyPairGenerator.getInstance("EC", ...)` thành `getInstance("RSA")` với `keySize=2048` và đổi `Signature.getInstance("SHA256withECDSA")` thành `"SHA256withRSA"` — phần còn lại của luồng không đổi gì.

### 12.7 Truyền file

- **(a) Bản gốc** (mục 10 tài liệu phân tích): 2 tầng — `secure-file-transfer` mã hoá file thành torrent (khoá suy từ tên phòng), phân phối qua **WebTorrent** (giao thức BitTorrent chạy trong trình duyệt), chỉ có `magnetURI` (con trỏ) đi qua kênh Trystero.
- **(b) Vì sao không port thẳng**: không có WebTorrent client thuần Java trưởng thành/dễ tích hợp tương đương; và bản chất WebTorrent tồn tại để **phân phối tới nhiều người xem chưa chắc đã có kết nối trực tiếp với nhau** (swarm) — trong khi chat-p2p-java gửi file trực tiếp 1-1 qua kênh đã có sẵn, không cần mô hình swarm.
- **(c) Giải pháp Java**: bỏ hẳn tầng "torrent hoá", gửi file trực tiếp qua chunk trên `DataChannel` đã mã hoá sẵn (mục 8.4, `FileSender`/`FileReceiver`) — **kiến trúc đơn giản hơn bản gốc**, đánh đổi là không tận dụng được cơ chế phân phối song song kiểu swarm (không cần thiết với quy mô phòng nhỏ của đồ án).

### 12.8 Video call, audio call, screen share

- **(a) Bản gốc** (mục 9 tài liệu phân tích): `getUserMedia`/`getDisplayMedia` lấy `MediaStream`, gắn thẳng vào `RTCPeerConnection` qua `addStream` — mã hoá + nén (H.264/VP8/Opus) + truyền đều do WebRTC engine của trình duyệt lo, tách biệt hoàn toàn khỏi data channel.
- **(b) Vì sao không port thẳng**: JVM không có encoder/decoder codec video/audio chuẩn built-in, không có khái niệm "media track" tách biệt khỏi data channel như WebRTC.
- **(c) Giải pháp Java** (mục 8.5): tận dụng lại chính hạ tầng `Envelope`/`DataChannel` đã xây cho chat/file (không có kênh media riêng như bản gốc) — capture bằng `webcam-capture`/`Robot`/`TargetDataLine` (đều là API JVM hoặc thư viện Java thuần, không cần native binding nặng), nén JPEG cho hình, PCM thô cho tiếng, gửi như `Envelope(MEDIA_FRAME, ...)` bình thường. **Đánh đổi tường minh**: chất lượng/độ trễ kém hơn hẳn codec chuyên dụng, nhưng khả thi trong thời gian đồ án và không phụ thuộc build native.

### 12.9 Lưu trữ cục bộ

- **(a) Bản gốc**: `localforage` (wrapper IndexedDB), chỉ lưu 1 object `userSettings` (mục 11 tài liệu phân tích) — bao gồm cả cặp khoá danh tính.
- **(b) Vì sao không port thẳng**: IndexedDB là API trình duyệt, không tồn tại trong JVM.
- **(c) Giải pháp Java** (mục 8.6): `Preferences` API hoặc file JSON cục bộ — vai trò và nội dung lưu **giống hệt** bản gốc (cùng lưu đúng 1 "gói cài đặt", cùng nguyên tắc không lưu tin nhắn), chỉ khác cơ chế lưu trữ vật lý.

### 12.10 Nhúng ứng dụng (SDK/iframe)

- **(a) Bản gốc**: `postMessage` giữa trang cha và `<iframe>` nhúng chitchatter (mục 13 tài liệu phân tích).
- **(b) Vì sao không port**: `<iframe>` là khái niệm trình duyệt, ứng dụng desktop JavaFX không có "trang cha" nào để nhúng vào.
- **(c) Giải pháp Java**: **không có giải pháp thay thế** — loại hẳn khỏi phạm vi thi công (không tính là 1 trong 12 chức năng cần làm ở app desktop), khác về bản chất với các mục 12.1-12.9 (những mục đó đều có giải pháp thay thế, mục này thì không áp dụng được).

### 12.11 Tổng kết mức độ "giống bản gốc"

| Mức độ | Các khối |
|---|---|
| **Copy gần như nguyên xi** (chỉ khác cú pháp Java) | Công thức chữ ký danh tính (12.6), công thức phòng riêng tư (12.3) |
| **Giữ nguyên ý tưởng, tự viết lại cơ chế** | Signaling/peer discovery (12.2), đa kênh logic (12.5) |
| **Đơn giản hoá có chủ đích** (bớt phức tạp hơn bản gốc) | Truyền file (12.7) |
| **Hạ cấp có chủ đích** (chấp nhận kém hơn để khả thi) | Video/audio/screen share (12.8) |
| **Thêm mới hoàn toàn** (bản gốc không cần vì có DTLS) | Mã hoá kênh truyền (12.1), tự viết tầng transport qua ice4j (12.4) |
| **Loại bỏ hẳn** (không áp dụng cho desktop) | Nhúng iframe/SDK (12.10) |
