Báo cáo thực hiện — Nhiệm vụ A (Mạng & Kết nối)

# BÁO CÁO THỰC HIỆN — NHIỆM VỤ A: MẠNG & KẾT NỐI

*Tài liệu này ghi lại **từng bước đã thực hiện** cho các phần việc thuộc Thành viên A (xem [Phan-cong-cong-viec.md](Phan-cong-cong-viec.md) mục 2), dùng làm bản nháp cho Chương 4 — Cài đặt của báo cáo đồ án. Chỉ ghi phần đã code xong và chạy được thật; các mục còn lại của nhiệm vụ A (P2P core/ice4j, mesh, đo hiệu năng — xem Tai-lieu-ky-thuat.md Phần C.2) sẽ được bổ sung tiếp vào tài liệu này khi hoàn thành.*

**Trạng thái tại thời điểm viết:** đã hoàn thành toàn bộ chuỗi mạng cốt lõi — signaling server + client (Tầng 1, **coi như đã hoàn thiện cả chịu lỗi, khả năng mở rộng lẫn bảo mật, đã rà soát lại lần 2 từ đầu và vá thêm 3 bug**), ICE thật (`ice4j`, có đo hiệu năng, **đã rà soát khả năng chịu lỗi, mở rộng lẫn bảo mật, vá 4 bug và xác nhận chịu được candidate hỏng/giả mạo**), kênh dữ liệu P2P thật (**đã rà soát khả năng chịu lỗi, vá 3 bug trong đó có 1 bug gây ~50% tỉ lệ thất bại handshake**), đa kênh logic + trao khoá phiên, và `RoomSession` quản lý nhiều peer (đã xác nhận mesh **≥3 peer** hoạt động đúng, không chỉ 2 peer) — đã xác nhận chạy đúng **cả qua signaling server thật**, kể cả **100 người dùng kết nối đồng thời** (không chỉ giả lập/tuần tự), 31 test liên quan đã tự chạy bằng IntelliJ và **PASS** (cộng 1 test đã `@Disabled` có ghi rõ lý do và kết quả thật trong javadoc, không tính vào số PASS). Trong quá trình đó phát hiện và sửa **15 bug thật**: 1 race condition trong `RoomRegistry`, 3 lỗ hổng chịu lỗi trong `SignalingWebSocketHandler` (JSON hỏng làm crash kết nối, 1 peer lỗi chặn thông báo cho các peer khác, và 1 bug thread-safety khi nhiều peer join đồng thời làm Tomcat ném `IllegalStateException`), 1 lỗ hổng đối xứng ở `RoomSession` (1 peer lỗi trong `PEER_LIST` chặn kết nối tới các peer khác), 3 bug phát hiện ở lượt rà soát Tầng 1 lần 2 (NPE khi JOIN thiếu trường bắt buộc, kết nối của người gửi bị Tomcat tự đóng khi gửi bản tin vượt 8KB, entry "mồ côi" trong `RoomRegistry` khi JOIN 2 lần không LEAVE), 2 bug ở Tầng 2 (ICE) về chịu lỗi: `RoomSession` không `dispose()` (giải phóng UDP socket) khi ICE thất bại thật — cả ở nhánh ICE tự báo `FAILED` lẫn nhánh `sendOffer()` lỗi giữa chừng, 1 bug về khả năng mở rộng ở Tầng 2: mọi `IceP2pConnectionEstablisher` dùng chung 1 điểm bắt đầu dò cổng UDP cố định, khiến dải cổng cạn kiệt hiệu quả chỉ sau ~50 kết nối bất kể khai báo dải rộng bao nhiêu — đã sửa bằng điểm bắt đầu xoay vòng, và **1 lỗ hổng DoS thật ở Tầng 2**: `RoomSession` ghi đè establisher đang chờ mà không dispose khi 1 peer gửi lặp lại OFFER tới cùng 1 nạn nhân trước khi lần đầu kịp hoàn tất — kẻ tấn công có thể cạn kiệt dải cổng UDP hữu hạn của nạn nhân chỉ bằng cách gửi đủ nhiều OFFER, không cần kết nối thành công. Ở Tầng 3 (kênh dữ liệu P2P), phát hiện và sửa **3 bug thật**: `P2pDataChannel` chết vĩnh viễn thread nhận nếu handler ném lỗi với 1 gói tin hỏng/giả mạo, `RoomSession.broadcast()` không cô lập lỗi giữa các peer, và đặc biệt **1 bug gây ~50% tỉ lệ thất bại thật**: handshake ECDH chỉ gửi public key đúng 1 lần qua UDP không ACK/retry, có thể mất gói vĩnh viễn — đã sửa bằng cơ chế gửi lại tự động, xác nhận hiệu quả bằng cách chạy lại nhiều lần liên tiếp (từ ~50% thất bại xuống 0/7-8 lần). Còn thiếu: xác thực danh tính tự động (`PEER_IDENTITY`, cần `IdentitySignatureService` của B), TURN dự phòng, đo hiệu năng tổng hợp qua mạng thật, và kiểm thử qua 2 máy thật khác NAT.

---

## Tóm tắt chức năng & cách xử lý từng thành phần (tính đến hiện tại)

*Phần này mô tả **hệ thống đang có** theo đúng luồng xử lý thật (từ lúc 2 peer "tìm thấy nhau" tới lúc gửi được dữ liệu) — đọc trước các "Giai đoạn" bên dưới nếu muốn hiểu kiến trúc trước khi đọc lịch sử từng bước code.*

### Tầng 1 — Signaling (tìm nhau, trao đổi thông tin kết nối)

**`signaling-server`** — máy chủ trung gian tối giản. Chức năng: giúp các peer trong cùng 1 phòng biết về nhau và chuyển tiếp (relay) các gói tin cần thiết để 2 bên tự thiết lập kết nối trực tiếp — **không bao giờ đọc/lưu nội dung chat**.

- [RoomRegistry.java](../signaling-server/src/main/java/com/datn/chatp2p/signaling/room/RoomRegistry.java): giữ `Map<roomId, Map<peerId, PeerSession>>` hoàn toàn trong bộ nhớ (không database) — mất khi restart server, đúng thiết kế "ephemeral".
- [SignalingWebSocketHandler.java](../signaling-server/src/main/java/com/datn/chatp2p/signaling/ws/SignalingWebSocketHandler.java) xử lý từng loại bản tin (`SignalType`):
  - `JOIN` → thêm peer vào registry, trả `PEER_LIST` (ai đã có sẵn) cho người mới, báo `PEER_JOINED` cho người cũ.
  - `LEAVE` (hoặc WebSocket đóng đột ngột) → xoá khỏi registry, báo `PEER_LEFT`.
  - `OFFER`/`ANSWER`/`ICE_CANDIDATE` → **chỉ chuyển tiếp nguyên văn** tới đúng `toPeerId`, không parse bên trong `payload`.
- Chạy qua WebSocket endpoint `/ws`, đóng gói fat jar chạy bằng `java -jar` (đã né bug path tiếng Việt làm hỏng `spring-boot:run`).

**Chịu lỗi ở tầng signaling** (Tai-lieu-ky-thuat.md Phần H.1 — rà soát và vá lại, không phải viết mới):
- **Phía server** (`SignalingWebSocketHandler`): `handleTextMessage` bắt riêng lỗi JSON sai định dạng (log WARN, bỏ qua đúng bản tin đó, không để Spring tự đóng kết nối của client); `send()` bắt riêng `IOException` cho **từng session**, không để lỗi 1 người làm dừng cả vòng lặp `broadcastToOthers` — các peer còn lại vẫn nhận được `PEER_JOINED`/`PEER_LEFT` bình thường. Đây chính là nguyên nhân của cảnh báo "Unhandled exception after connection closed" từng thấy trong log lúc test `RoomSessionRealSignalingServerTest` ở phiên trước.
- **Phía client** (`RoomSession`, đối xứng với fix phía server): `handlePeerList` lặp qua từng peer trong `PEER_LIST` gọi `connectAsOfferer` — bọc `try/catch` cho **từng peer**, báo lỗi qua `onConnectionFailed(peerId, error)` đã có sẵn, dọn dẹp `IceP2pConnectionEstablisher` dở dang (`cleanupFailedEstablisher`) — 1 peer lỗi (hết cổng UDP, gửi OFFER thất bại...) không còn chặn việc kết nối tới các peer khác trong cùng danh sách. `handleOffer`/`handleAnswer` cũng được bọc tương tự (JSON/candidate sai định dạng không làm crash luồng xử lý bản tin).

**Bug thứ 2 phát hiện qua load test thật** (30 peer join đồng thời — xem mục "Khả năng chịu tải" bên dưới): Tomcat's `WebSocketSession.sendMessage()` **không an toàn khi 2 thread cùng ghi đồng thời trên CÙNG 1 session** — ném `IllegalStateException: state [TEXT_PARTIAL_WRITING]`, khác hẳn `IOException` nên fix ở trên **không hề bắt được**, exception vẫn lọt ra ngoài làm Spring tự đóng session, gây cascade lỗi tiếp theo. **Đã sửa:** bọc `send()` bằng `synchronized(session)` — khoá trên chính đối tượng session (không phải khoá toàn cục), chỉ serialize ghi trên cùng 1 session, các session khác vẫn gửi song song bình thường; bắt thêm cả `IllegalStateException`.

### Khả năng chịu tải của Tầng 1 (Signaling) — tách biệt khỏi giới hạn N² của mesh

Câu hỏi "tầng 1 chịu được bao nhiêu người dùng cùng lúc" **khác với** câu hỏi "1 phòng chat P2P chứa được bao nhiêu người" (giới hạn N² ở mục "Khả năng mở rộng" của Tầng 5, chỉ áp dụng cho mesh/ICE) — vì signaling chỉ là relay JSON nhẹ qua WebSocket, không cần cổng UDP/ICE Agent nên chịu tải được nhiều hơn hẳn.

`WebSocketSignalingClientCapacityTest`: mô phỏng **N người dùng thật kết nối đồng thời** (mỗi người 1 thread riêng, gọi `connect()` gần như cùng lúc) vào 1 `signaling-server` thật — xác nhận tất cả kết nối thành công **và** không ai bị "thất lạc" (mỗi người biết đúng N-1 người còn lại, không thiếu/dư do race condition). Đã đo 2 mốc trên cùng 1 máy phát triển:

| Số peer đồng thời | Thời gian join | Kết quả |
|---|---|---|
| 30 | 761ms | PASS (sau khi sửa bug thread-safety — lần chạy đầu **thất bại**, nhiều session bị đóng do lỗi ghi đồng thời) |
| 100 | 951ms | PASS (không còn lỗi `TEXT_PARTIAL_WRITING` nào trong lúc JOIN) |

**Kết luận trung thực:** tầng Signaling đã xác nhận xử lý đúng **≥100 người dùng đồng thời** trên 1 máy phát triển, thời gian join gần như không tăng đáng kể từ 30→100 (761ms → 951ms), cho thấy còn nhiều dư địa. Đây **vẫn không phải** giới hạn tối đa thật của hệ thống — chưa thử số lượng lớn hơn nữa (500+, 1000+) hay môi trường production/mạng thật (chỉ localhost). Muốn có con số giới hạn thật, cần tiếp tục tăng `peerCount` trong chính test này tới khi thấy dấu hiệu quá tải thật (timeout, lỗi, hoặc thời gian join tăng phi tuyến) thay vì đoán.

### Bảo mật của Tầng 1 (Signaling) — xác nhận bằng test thật, không chỉ đọc code

Tai-lieu-ky-thuat.md Phần F.2 khẳng định sẵn 1 claim bảo mật cốt lõi: *"Signaling server không bao giờ đọc/giải mã nội dung — chỉ relay `SignalMessage`... đã kiểm chứng **bằng code review**"*. Đây là claim quan trọng (nền tảng của toàn bộ kiến trúc "server không nhìn thấy nội dung chat") nhưng trước đó mới chỉ được xác nhận bằng đọc code, chưa có test thật — đúng tinh thần "kiểm chứng bằng chạy code" đã theo xuyên suốt tài liệu này.

`SignalingServerContentOpacityTest` (3 test, dùng `FakeWebSocketSession` + `RoomRegistry` thật, không Mockito):
- **Payload không phải JSON hợp lệ** (cố tình chèn thẻ `<script>`, ngoặc chưa đóng, ký tự Unicode) → server không hề văng lỗi (chứng minh thật sự không bao giờ thử parse `payload`) **và** relay nguyên văn từng ký tự tới đích, không sửa/diễn giải gì.
- **Không log nội dung payload**: gắn trực tiếp 1 Logback `ListAppender` bắt log THẬT trong lúc xử lý (không đoán mò), gửi 1 payload chứa chuỗi đánh dấu "bí mật", xác nhận không dòng log nào chứa chuỗi đó — kiểm chứng thật cho nguyên tắc H.2.
- **Payload rất lớn** (200.000 ký tự) → không bị cắt bớt hay lỗi.

### Tầng 2 — Thiết lập kết nối trực tiếp (ICE, xuyên NAT) — mới viết trong phiên gần đây, dùng `ice4j`

**`IceOfferPayload`/`IceAnswerPayload`** (trong `common`): "gói tin mời kết nối" — chứa `ufrag`, `password` (2 giá trị bí mật ngắn để xác thực đúng đối phương lúc bắt tay ICE) và `candidates` (danh sách địa chỉ IP:port khả dụng của mình). Chỉ là 2 `record` Java thuần, serialize JSON rồi nhét vào `SignalMessage.payload` có sẵn — server không cần biết cấu trúc bên trong.

**`IceCandidateCodec`**: dịch qua lại giữa candidate của `ice4j` và 1 dòng text để gửi qua signaling.
- `encode()`: gọi thẳng `candidate.toString()` của `ice4j` — tự in đúng chuẩn RFC 5245 (vd `candidate:1 1 udp 2130706431 192.168.1.5 54321 typ host`).
- `decode()`: tách dòng text ra từng phần (foundation, transport, priority, địa chỉ, port, loại candidate), dựng lại thành `RemoteCandidate`.

**`IceP2pConnectionEstablisher`** — "bộ điều phối" trung tâm, đại diện cho 1 phiên thiết lập kết nối với đúng 1 peer khác, bọc 1 `Agent` của `ice4j`. Xử lý đúng thứ tự:
1. Khởi tạo: tạo `Agent`, thêm STUN server, tạo `IceMediaStream` + `Component` — tự động gom (harvest) candidate cục bộ.
2. Bên chủ động (peer vào sau, thấy người khác đã có sẵn) gọi `createOffer()` → đánh dấu "controlling", trả `IceOfferPayload` chứa candidate vừa gom, gửi qua signaling.
3. Bên nhận offer gọi `createAnswer(offer)` → áp thông tin người kia, tạo answer, **tự bắt đầu ngay** `startConnectivityEstablishment()` (đã đủ thông tin 2 phía).
4. Bên chủ động nhận answer, gọi `acceptAnswer(answer)` → áp thông tin người kia rồi mới bắt đầu `startConnectivityEstablishment()` phía mình.
5. `ice4j` tự chạy ngầm: ghép từng cặp candidate, gửi STUN kiểm tra, chọn cặp "thông" nhất — phần xuyên NAT khó nhất do thư viện lo, không tự viết.
6. Khi xong, `ice4j` bắn sự kiện `IceProcessingState.COMPLETED` — lớp này lắng nghe (`PropertyChangeListener`), lấy socket UDP + địa chỉ đối phương đã chọn, **tự tạo `P2pDataChannel`**, gọi callback `onConnected(channel)`. Nếu `FAILED` → gọi callback `onFailed`.

**Đo hiệu năng kết nối** (`IceConnectionStats`, Tai-lieu-ky-thuat.md Phần F.1): ngay lúc `COMPLETED`, tính `establishmentMillis` (từ lúc tạo `IceP2pConnectionEstablisher` tới lúc xong) và `usingRelay` (xem loại candidate của candidate pair đã chọn có phải `RELAYED_CANDIDATE` không — nếu có thì dữ liệu đang đi qua TURN thay vì trực tiếp). Đọc qua `getStats()` (trả `Optional`, rỗng nếu ICE chưa xong).

### Chịu lỗi ở Tầng 2 (ICE) — rà soát và vá 2 bug rò rỉ tài nguyên thật

Rà soát riêng khía cạnh "khả năng chịu lỗi" của Tầng 2 (câu hỏi: khi ICE thật sự thất bại — không phải lỗi parse OFFER/ANSWER đã xử lý từ trước — hệ thống có dọn dẹp sạch không, hay để lại tài nguyên "mồ côi"?) — phát hiện 2 chỗ `RoomSession` tạo xong 1 `IceP2pConnectionEstablisher` (chiếm 1 UDP socket trong dải cổng **rất hẹp 10000-10100, chỉ 101 cổng**) rồi sau đó thất bại mà **không gọi `dispose()`** — khác với các nhánh lỗi khác (lỗi parse OFFER/ANSWER) vốn đã dọn dẹp đúng từ trước:

1. **`RoomSession.handleIceFailed`** (gọi khi `Agent` của ice4j thật sự báo `IceProcessingState.FAILED` sau khi đã bắt đầu connectivity establishment thật — ví dụ NAT đối xứng không xuyên qua được, mạng thật sự không thông) trước đây chỉ `pendingEstablishers.remove(peerId)` mà không gọi `dispose()` — rò rỉ vĩnh viễn 1 UDP socket mỗi lần ICE thất bại thật. Vì dải cổng chỉ có 101 cổng, sau khoảng 100 lần thất bại thật (hoàn toàn có thể xảy ra trên mạng xấu hoặc phòng có nhiều peer), **mọi `IceP2pConnectionEstablisher` mới sẽ không còn cổng nào để bind**, sập toàn bộ khả năng kết nối P2P mới trong ứng dụng — một lỗi chịu lỗi nghiêm trọng vì hậu quả tích luỹ âm thầm, không lộ ra ngay ở lần thất bại đầu tiên.
2. **`RoomSession.handlePeerList`**: nếu `connectAsOfferer` thất bại **sau khi** establisher đã được tạo (ví dụ `establisher.createOffer()` hoặc `signalingClient.sendOffer()` ném lỗi) — catch block cũ chỉ gọi `notifyConnectionFailed` mà không dọn dẹp establisher vừa tạo, cùng loại rò rỉ như trên.

**Đã sửa** cả 2 bằng cách dùng lại `cleanupFailedEstablisher` (remove + `dispose()`) đã có sẵn — đúng tinh thần tái sử dụng logic thay vì viết lại. Xác nhận bằng 2 test thật:
- `RoomSessionErrorHandlingTest` — thêm `RoomSession#hasPendingEstablisherFor(peerId)` (package-private, chỉ dùng cho test) để xác nhận establisher lỗi của `existing1` không còn "mồ côi" trong `pendingEstablishers` sau khi lỗi đã được xử lý xong (chỉ kiểm tra đúng 1 peerId, không phải toàn bộ map, vì các peer khác vẫn có thể đang ICE thật chưa xong tại cùng thời điểm — không phải rò rỉ).
- `IceP2pConnectionEstablisherTest#reportsOnFailedAndFreesTheUdpPortWhenRemoteCredentialsAreWrong` (test mới) — ép ICE thất bại THẬT một cách nhanh và xác định (không cần chờ timeout mạng ~vài chục giây): cố ý đặt SAI mật khẩu ICE trong offer trước khi đưa cho answerer — địa chỉ candidate vẫn THẬT và còn lắng nghe, nên answerer gửi được STUN Binding Request tới nơi, nhưng offerer từ chối ngay bằng STUN error response (401, do sai `MESSAGE-INTEGRITY`) thay vì im lặng — ICE xử lý phản hồi lỗi rõ ràng nhanh hơn nhiều so với chờ hết giờ vì không có phản hồi gì cả. Xác nhận `onFailed` báo đúng **và** cổng UDP được giải phóng thật sau `dispose()` (bind lại được chính xác cổng đó bằng 1 `DatagramSocket` mới).

Cả 2 test đều đã chạy PASS trên máy thật qua IntelliJ.

**Rà soát tiếp: ICE candidate hỏng/giả mạo trong OFFER (không phải rò rỉ tài nguyên, mà là câu hỏi "có crash không").** Tầng 1 đã xác nhận signaling server **không bao giờ** validate nội dung `payload` — nghĩa là 1 dòng ICE candidate sai định dạng (bug ở peer gửi, hoặc giả mạo có ý) hoàn toàn có thể tới `RoomSession.handleOffer` nguyên vẹn. Code review cho thấy điều này *nên* được `try/catch(RuntimeException)` có sẵn bắt, nhưng **chưa từng có test thật nào chứng minh** — viết 2 bộ test mới để xác nhận, cả 2 đều **PASS ngay, không cần sửa code sản xuất**:
- `IceCandidateCodecTest` (8 test, mới) — `IceCandidateCodec.decode()` từ chối đúng cách nhiều dạng sai định dạng (dòng rỗng, thiếu token, sai vị trí marker `typ`, priority/port không phải số, candidate type không tồn tại) — không âm thầm tạo ra dữ liệu rác.
- `RoomSessionMalformedIceCandidateTest` (mới) — giả mạo 1 dòng candidate bên trong 1 OFFER **thật** (dùng `LoopbackSignalingClient.Hub`) gửi tới 1 peer, xác nhận `RoomSession.handleOffer` không crash/treo **và** 1 peer khác trong cùng `PEER_LIST` vẫn kết nối bình thường (đúng nguyên tắc H.1).

**Giới hạn thiết kế mới phát hiện trong lúc rà soát (chưa sửa, ghi rõ để không nhận vơ):** bên **gửi** OFFER không có cơ chế timeout/NACK nếu bên nhận từ chối/thất bại khi xử lý offer — giao thức hiện tại không có loại tín hiệu "OFFER bị từ chối", nên `IceP2pConnectionEstablisher` của bên gửi sẽ chờ ANSWER **vô thời hạn** (chỉ được dọn dẹp khi peer đó rời phòng qua `PEER_LEFT`, hoặc khi `leave()` toàn phòng được gọi). Muốn xử lý triệt để cần thay đổi giao thức (thêm tín hiệu từ chối, hoặc cơ chế timeout ở tầng `RoomSession`) — chưa làm trong đợt rà soát này.

### Khả năng mở rộng ở Tầng 2 (ICE) — phát hiện và vá 1 bug thật về dải cổng UDP

Rà soát khả năng mở rộng của Tầng 2 tại **đúng biên trên đã công bố** (2-8 peer/phòng, Tai-lieu-ky-thuat.md Phần B.3/F.3) — trước đó `RoomSessionThreePeerMeshTest` mới chỉ thử tới 3 peer (2 kết nối/peer). Viết `RoomSessionEightPeerMeshScalabilityTest` (8 peer, 28 cặp kết nối, 56 `Agent` ice4j tối đa) và phát hiện 1 bug thật quan trọng:

**Bug:** mọi `IceP2pConnectionEstablisher` trước đây đều dùng **cùng 1 hằng số `PREFERRED_PORT = 10_000` làm điểm bắt đầu dò cổng UDP**. Lần chạy đầu thất bại thật với `IOException: Failed to bind even a single host candidate... preferredPort=10000 minPort=10000 maxPort=10100` sau ~64 giây. Nghi ngờ ban đầu là "dải 101 cổng quá hẹp" nên đã thử tăng `MAX_PORT` lên 11000 (gấp 10 lần) — nhưng **kết quả/thời gian thất bại gần như không đổi** (63917ms so với 64179ms)! Log thật cho thấy nguyên nhân sâu hơn: `HostCandidateHarvester` của `ice4j` chỉ thử tối đa **~50 cổng liên tiếp tính từ `preferredPort`** cho mỗi địa chỉ mạng rồi bỏ cuộc với địa chỉ đó — **hoàn toàn không liên quan đến `MAX_PORT`** đã khai báo. Vì mọi Agent đều bắt đầu dò từ đúng 1 điểm cố định, ngay khi ~50 cổng đầu tiên bị chiếm bởi các kết nối trước đó (giữ cổng suốt vòng đời kết nối, không bao giờ trả lại giữa chừng), **mọi Agent mới tạo ra sau đó đều chắc chắn thất bại**, bất kể còn bao nhiêu cổng trống ở xa hơn trong dải.

**Đã sửa đúng gốc:** cho điểm bắt đầu dò cổng **xoay vòng qua từng instance** (dùng 1 `AtomicInteger` dùng chung, tăng dần mỗi lần tạo Agent mới) thay vì luôn cố định ở `MIN_PORT`. Sau khi sửa: chạy lại `RoomSessionEightPeerMeshScalabilityTest` — **không còn crash/`BindException` nào nữa**, xác nhận đúng nguyên nhân gốc đã được xử lý. Vẫn giữ dải cổng rộng hơn (10000-11000, 1001 cổng) để có thêm biên độ an toàn.

**Phát hiện còn lại (đã dừng điều tra theo quyết định của người dùng, không phải bug sản xuất):** sau khi hết crash, mô phỏng 56 `Agent` ice4j chạy đồng thời **trong cùng 1 JVM** (dùng `LoopbackSignalingClient` để né việc cần 8 máy thật) không hoàn tất hết trong 60 giây — đây là giới hạn **thông lượng/CPU** khi quá nhiều Agent tranh chấp tài nguyên trên 1 máy vật lý, khác hẳn bug rò rỉ cổng ở trên. Quan trọng: đây là giới hạn của **cách test** (mô phỏng nhiều peer chung 1 tiến trình), **không phải** giới hạn của 1 peer thật trong triển khai sản xuất — mỗi peer thật chạy trên máy riêng, chỉ cần tự xử lý tối đa 7 kết nối của chính mình, không tranh chấp CPU với Agent của 7 người còn lại (mỗi người có máy riêng). Đã đánh dấu `RoomSessionEightPeerMeshScalabilityTest` là `@Disabled` (giữ nguyên code, ghi rõ lý do và toàn bộ kết quả thật trong javadoc của chính test) thay vì để lại 1 test đỏ vĩnh viễn trong bộ test — muốn xác nhận triệt để "8 peer thật" cần kiểm thử qua nhiều máy vật lý riêng biệt (cùng loại với mục "Test qua 2 máy thật khác NAT" đã ghi ở phần "Chưa làm").

Đã chạy lại toàn bộ test liên quan đến `IceP2pConnectionEstablisher` sau khi sửa (`IceP2pConnectionEstablisherTest`, `IceCandidateCodecTest`, `RoomSessionTest`, `RoomSessionThreePeerMeshTest`, `RoomSessionErrorHandlingTest`, `RoomSessionMalformedIceCandidateTest`, `RoomSessionRealSignalingServerTest`) và xác nhận **PASS**.

### Bảo mật ở Tầng 2 (ICE) — phát hiện và vá 1 lỗ hổng DoS thật

Rà soát bảo mật Tầng 2 với câu hỏi: **1 peer trong phòng (bug hoặc cố ý) có thể khai thác luồng xử lý OFFER để gây từ chối dịch vụ (DoS) cho peer khác không?** Tầng 1 đã xác nhận signaling server **không giới hạn tốc độ, không xác thực** bản tin (H.2) — nghĩa là không có gì ngăn 1 peer gửi lặp lại OFFER liên tục tới cùng 1 nạn nhân.

**Bug:** `RoomSession.createEstablisherFor` — dùng chung bởi cả `connectAsOfferer` (bên chủ động) lẫn `handleOffer` (bên trả lời) — ghi `pendingEstablishers.put(peerId, establisher)` **không kiểm tra giá trị cũ**. Nếu 1 peer gửi OFFER lần 2 tới cùng 1 peerId **trước khi** lần đầu kịp hoàn tất ICE (do bug gửi trùng lặp ở phía họ, hoặc **cố ý tấn công**), establisher lần đầu (đã chiếm 1 UDP socket) bị ghi đè trong map mà **không được `dispose()`** — "mồ côi" vĩnh viễn. Kẻ tấn công **không cần thành công kết nối thật sự** — chỉ cần gửi đủ nhiều OFFER hợp lệ về định dạng (có thể lấy candidate của chính mình, không quan trọng) tới cùng 1 nạn nhân để cạn kiệt toàn bộ dải cổng UDP hữu hạn (1001 cổng sau khi sửa ở mục trên) của nạn nhân — khiến nạn nhân không còn thiết lập được kết nối P2P mới nào nữa, kể cả với các peer khác hoàn toàn vô hại trong phòng.

**Đã sửa:** dùng giá trị trả về của `Map.put()` (chính là giá trị **cũ** bị ghi đè, nếu có) để `dispose()` đúng establisher bị thay thế, thay vì bỏ qua nó. Xác nhận bằng test mới `RoomSessionDuplicateOfferSecurityTest`: mô phỏng đúng kịch bản tấn công — gửi 2 OFFER hợp lệ liên tiếp (không đợi ANSWER của offer đầu) tới cùng 1 nạn nhân, xác nhận cổng UDP của establisher đầu tiên được giải phóng thật (bind lại được bằng 1 `DatagramSocket` mới) thay vì bị "mồ côi". Đã chạy lại toàn bộ test liên quan (`RoomSessionTest`, `RoomSessionThreePeerMeshTest`, `RoomSessionErrorHandlingTest`, `RoomSessionMalformedIceCandidateTest`, `RoomSessionRealSignalingServerTest`) và xác nhận **PASS**.

### Tầng 3 — Kênh dữ liệu thật sau khi đã "bắt tay" xong

**`P2pDataChannel`**: kênh gửi/nhận byte thô giữa 2 peer sau khi ICE đã tìm ra đường truyền — implement đúng interface `DataChannel` chung với B.
- `send(data)`: thêm 4 byte length-prefix ở đầu, gửi qua `DatagramSocket.send()` tới đúng địa chỉ đối phương.
- 1 thread nền riêng chạy vòng lặp `socket.receive()` liên tục — mỗi gói tới, bóc length-prefix, lấy đúng phần dữ liệu, gọi `receiveHandler` (đăng ký qua `onReceive`).
- `close()`: đóng socket, dừng thread nền.

### Chịu lỗi ở Tầng 3 (kênh dữ liệu P2P) — phát hiện và vá 3 bug thật, trong đó có 1 bug gây ~50% tỉ lệ thất bại

Rà soát khả năng chịu lỗi của Tầng 3 — câu hỏi: 1 gói tin hỏng/hết hạn/mất trên đường truyền UDP có làm sập cả kênh không?

1. **`P2pDataChannel.runReceiveLoop()` — bug rất nghiêm trọng, thread nhận chết vĩnh viễn.** Dòng `handler.accept(data)` (gọi tới `PeerConnection.handleIncoming`, giải mã AES-GCM) **không được bọc try/catch**. Nếu 1 gói tin đã qua được kiểm tra framing nhưng nội dung bên trong hỏng/giả mạo (sai khoá, dữ liệu bị sửa trên đường truyền, hoặc 1 gói UDP lạ rơi vào đúng cổng), ngoại lệ đó thoát khỏi vòng lặp `while`, **làm chết vĩnh viễn thread nhận của chính kênh đó** — kết nối P2P trở thành "xác sống": vẫn tưởng đang mở nhưng không bao giờ nhận được gì nữa, không có cảnh báo gì cả. **Đã sửa:** bọc `try/catch(RuntimeException)`, bỏ qua đúng gói tin đó, tiếp tục vòng lặp — xác nhận bằng test mới `receiveLoopSurvivesAHandlerThatThrowsOnAMalformedOrTamperedPacket`.
2. **`RoomSession.broadcast()` không cô lập lỗi giữa các peer.** Nếu `PeerConnection.send()` của 1 peer ném lỗi (channel của nó vừa đóng đúng lúc đang broadcast, hoặc lỗi mạng thật), vòng lặp `for` dừng ngay — **các peer SAU peer lỗi trong danh sách không bao giờ nhận được broadcast**, dù bản thân họ hoàn toàn khoẻ mạnh. Vi phạm đúng nguyên tắc H.1 đã áp dụng ở mọi nơi khác trong dự án. **Đã sửa:** bọc try/catch cho từng peer, báo lỗi qua `onConnectionFailed` rồi tiếp tục gửi cho các peer còn lại. Thêm `RoomSession#getPeerConnection(peerId)` (package-private, chỉ dùng cho test) để mô phỏng chính xác lỗi cục bộ mà không cần dàn dựng cả kịch bản mạng thật — xác nhận bằng test mới `RoomSessionBroadcastErrorHandlingTest`.
3. **Bug thật gây ~50% tỉ lệ thất bại: handshake ECDH mất gói UDP.** Trong lúc viết test #2, phát hiện mesh 3 peer **thỉnh thoảng** "ICE báo `Completed` đúng nhưng handshake ECDH không bao giờ hoàn tất" — xác nhận thật qua nhiều lần chạy liên tiếp: **tỉ lệ thất bại ~50% (2/4 lần)**. Nguyên nhân: `PeerConnection.sendEcdhPublicKey()` trước đây chỉ gửi **đúng 1 lần** qua `P2pDataChannel` (UDP thuần, không ACK/retry — giới hạn đã biết từ trước, ghi trong Tai-lieu-ky-thuat.md Phần H.3) — nếu gói tin DUY NHẤT này bị mất trên đường truyền (nghi ngờ do cạnh tranh `receive()` với chính `ice4j` trên cùng 1 socket vừa được `COMPLETED`, hoặc mất gói UDP thông thường), handshake sẽ **không bao giờ** hoàn tất cho kết nối đó — `onPeerJoined` không bao giờ được gọi, dù ICE đã xong hoàn toàn. **Đã sửa:** `sendEcdhPublicKey()` giờ tự động **gửi lại tối đa 5 lần** (cách nhau 300ms) bằng 1 `ScheduledExecutorService` dùng chung cho mọi `PeerConnection` — an toàn nhờ đúng fix #1 ở trên: nếu 1 bản sao gửi lại tới sau khi đối phương đã hoàn tất handshake từ bản sao trước, nó bị hiểu nhầm là dữ liệu mã hoá, giải mã AES-GCM thất bại, nhưng giờ chỉ bị bỏ qua an toàn thay vì làm sập kết nối.
   - **Phát hiện phụ trong lúc xác nhận fix #3**: `PeerConnectionTest` (dùng `LoopbackDataChannel` — bản giả lập do A viết, không phải `P2pDataChannel` thật) in ra stack trace "Giải mã AES-GCM thất bại" từ thread `loopback-datachannel-delivery` — lộ ra **cùng 1 lỗ hổng** (handler không được bọc try/catch) cũng tồn tại ở `LoopbackDataChannel` (nhẹ hơn vì `ExecutorService` tự thay worker thread chết nên kênh không "chết hẳn" như `P2pDataChannel`, nhưng vẫn in stack trace gây nhầm lẫn cho 1 tình huống hoàn toàn vô hại) — đã sửa tương tự.

**Xác nhận hiệu quả thật:** chạy `RoomSessionThreePeerMeshTest` và `RoomSessionBroadcastErrorHandlingTest` liên tiếp 7-8 lần **sau** fix #3 — không còn lần nào thất bại (trước đó ~50%). Đã chạy lại toàn bộ test liên quan (`P2pDataChannelTest`, `PeerConnectionTest`, `RoomSessionTest`, `RoomSessionErrorHandlingTest`, `RoomSessionMalformedIceCandidateTest`, `RoomSessionDuplicateOfferSecurityTest`, `RoomSessionRealSignalingServerTest`, `EnvelopeCodecTest`) và xác nhận **PASS**.

### Tầng 4 — Đa kênh logic + trao khoá phiên (`Envelope`/`EnvelopeCodec`/`PeerConnection`)

**`EnvelopeType`/`Envelope`/`EnvelopeNamespace`** (`common`): "phong bì" chung cho MỌI thứ đi qua `DataChannel` sau khi đã kết nối P2P — thay vì chỉ gửi được 1 loại tin nhắn duy nhất như bản demo ban đầu. `EnvelopeType` liệt kê 10 loại (tin nhắn, gõ phím, xác thực danh tính, file, media...), `Envelope` gói `type + namespace (nhóm/DM) + timestamp + payload (JSON của loại tương ứng)`. `MessagePayload` là payload cụ thể đầu tiên (tin nhắn văn bản).

**`EnvelopeCodec`** (`p2p-core`): nối `AesGcmCipher` (đã có sẵn của B, module `crypto`) với Jackson (`common`) — **không viết lại logic mã hoá**, chỉ dùng lại.
- `encode(type, namespace, payload)`: `payload` → JSON → gói vào `Envelope` → JSON → mã hoá AES-GCM → `byte[]` sẵn sàng cho `DataChannel.send()`.
- `decode(raw)`: giải mã AES-GCM → JSON → `Envelope` (phần `payload` bên trong vẫn là JSON thô, chưa parse).
- `parsePayload(envelope, Class<T>)`: parse tiếp phần payload thô đó thành đúng record cụ thể (vd `MessagePayload`).

**`PeerConnection`** (`p2p-core`): bọc 1 `DataChannel` đã mở với đúng 1 peer, tự lo việc trao khoá phiên rồi mới cho gửi dữ liệu.
- Ngay khi tạo xong (2 phía đối xứng, không ai "hỏi trước"), mỗi bên tự gọi `sendEcdhPublicKey()` — gửi public key ECDH của mình, đây là **gói tin duy nhất không mã hoá** (bản thân public key không cần giữ bí mật).
- Gói tin **đầu tiên nhận được** ở mỗi bên được coi là public key ECDH của đối phương → tự tính `KeyExchangeService.deriveSharedSecret(...)` (có sẵn của B) → dựng `EnvelopeCodec` cho riêng peer này.
- Từ gói tin thứ 2 trở đi, mọi thứ đều đi qua `EnvelopeCodec` (đã mã hoá). Gọi `send()` trước khi trao khoá xong bị chặn bằng `IllegalStateException` rõ ràng, không gửi ngầm dữ liệu chưa mã hoá.
- Mỗi peer trong phòng có 1 `PeerConnection` + 1 khoá AES riêng (không dùng chung khoá giữa các cặp peer khác nhau).

### Tầng 5 — Quản lý mesh nhiều peer (`RoomSession`)

**`RoomSession`** (`p2p-core`): lớp duy nhất biết cả 3 mảnh ở trên (`SignalingClient`, `IceP2pConnectionEstablisher`, `PeerConnection`) — mảnh ghép cuối cùng nối tất cả lại thành 1 phòng chat hoạt động được.

- **Quy tắc ai chủ động, ai trả lời** (bắt buộc để 2 bên không cùng lúc gửi OFFER hoặc cùng chờ nhau): nhận `PEER_LIST` lúc vừa vào phòng (nghĩa là những peer này đã vào TRƯỚC mình) → **mình chủ động** gửi OFFER cho từng người. Nhận `PEER_JOINED` (ai đó vào SAU mình) → chỉ ghi nhớ tên hiển thị, **không tự gửi gì** — vì họ sẽ tự thấy mình qua `PEER_LIST` của họ và chủ động gửi OFFER tới mình.
- Nhận được OFFER → tạo 1 `IceP2pConnectionEstablisher` mới, trả lời bằng ANSWER. Nhận được ANSWER → tìm đúng phiên ICE đang chờ (theo `peerId`), gọi tiếp `acceptAnswer()`.
- Khi ICE báo xong (`onConnected`) → sinh 1 cặp khoá ECDH **riêng cho peer này**, tạo `PeerConnection`, tự gọi `sendEcdhPublicKey()` ngay. Chỉ khi `PeerConnection` báo trao khoá xong mới bắn sự kiện `onPeerJoined` ra ngoài (UI/lớp gọi chỉ nhận peer khi nó đã thật sự dùng được).
- `broadcast(type, payload)` gửi cho mọi peer đang có; `sendTo(peerId, type, payload)` gửi 1 peer; `onEnvelope(type, handler)` đăng ký nhận theo đúng loại `EnvelopeType`, không quan tâm gửi từ peer nào.
- `leave()` đóng hết `PeerConnection`, huỷ mọi phiên ICE đang dở, rồi mới ngắt signaling.

**Khả năng mở rộng** (Tai-lieu-ky-thuat.md Phần B.3/F.3): kiến trúc mesh đầy đủ (N peer → N×(N-1)/2 kết nối) — băng thông/CPU tăng theo N², **chấp nhận được và đã ghi rõ là giới hạn thiết kế** (không phải bug), khuyến nghị demo ổn định ở 2-8 peer/phòng, giống hệt kiến trúc gốc `chitchatter`. Đến trước phiên này mọi test chỉ dùng 2 peer — đã bổ sung `RoomSessionThreePeerMeshTest` dựng **mesh 3 peer thật** (P3 vào sau cùng, thấy 2 peer qua `PEER_LIST`, tự kết nối với cả hai) để xác nhận `handlePeerList` xử lý đúng khi danh sách có nhiều hơn 1 peer, và broadcast từ 1 người tới đúng tất cả người còn lại. Signaling server chạy đơn instance, không scale ngang (state trong bộ nhớ) — chấp nhận được, đúng tinh thần ephemeral, ngoài phạm vi đồ án.

### Interface/giao ước chung (nền tảng để A/B code song song)

- **`DataChannel`** (`common`): 3 method `send/onReceive/close` — cả `LoopbackDataChannel` (giả lập của B) lẫn `P2pDataChannel` (thật của A) đều implement đúng interface này, B không cần đổi code khi ghép kênh thật vào.
- **`SignalingClient`** (`p2p-core`): hợp đồng cho việc kết nối/tham gia phòng qua signaling — `WebSocketSignalingClient` là cài đặt thật, dùng `java.net.http.HttpClient` (có sẵn JDK) mở WebSocket tới `signaling-server`, serialize/deserialize `SignalMessage` bằng Jackson, dispatch theo `SignalType` tới đúng handler đã đăng ký (`onOffer`, `onAnswer`,...).

**Tự động kết nối lại** (`WebSocketSignalingClient`, Tai-lieu-ky-thuat.md Phần H.3): nếu WebSocket đóng **bất thường** (server sập, mất mạng — khác với tự gọi `disconnect()`), tự lên lịch thử lại với backoff tăng dần (1s → 2s → 4s... tối đa 30s); kết nối lại thành công thì **tự gửi lại JOIN** để vào lại đúng phòng, không cần lớp gọi (`RoomSession`) tự xử lý gì. Có `onConnectionStateChanged(handler)` riêng (không thuộc interface `SignalingClient` chung, vì không phải cài đặt nào — ví dụ bản giả lập dùng để test — cũng cần khái niệm reconnect) để lớp trên biết lúc nào đang "mất kết nối, đang thử lại" (dùng cho UI sau này).

### Bug thật phát hiện khi test qua signaling-server thật (không phải giả lập)

`RoomSessionTest` (dùng `LoopbackSignalingClient` giả lập) chạy đúng, nhưng khi đổi sang test với `WebSocketSignalingClient` + 1 `signaling-server` thật (`RoomSessionRealSignalingServerTest`, tự boot server thật trên cổng ngẫu nhiên bằng `SpringApplicationBuilder`) thì **treo, không kết nối được**.

Nguyên nhân: `RoomRegistry.join()` (module `signaling-server`, tưởng đã "xong" và có test pass từ Giai đoạn 1) đọc danh sách peer hiện có rồi mới thêm mình vào — **2 bước này không nguyên tử**. Khi 2 `RoomSession` join gần như đồng thời (2 thread Tomcat khác nhau xử lý 2 WebSocket session), cả 2 có thể cùng đọc được danh sách rỗng trước khi bên kia kịp thêm mình vào registry → cả 2 đều nghĩ mình là người đầu tiên → **không ai chủ động gửi OFFER** → kết nối treo vĩnh viễn. Lỗi này không xuất hiện khi test bằng `LoopbackSignalingClient` (chạy đơn luồng, đồng bộ) — chỉ lộ ra khi có 2 thread thật xử lý đồng thời, đúng giá trị của việc test qua server thật thay vì chỉ tin vào bản giả lập.

**Đã sửa:** thêm `synchronized (room)` bọc quanh cả `join()` lẫn `leave()` trong [RoomRegistry.java](../signaling-server/src/main/java/com/datn/chatp2p/signaling/room/RoomRegistry.java) — khoá trên chính object Map của từng phòng (không khoá cả `RoomRegistry`), nên các phòng khác nhau không tranh chấp nhau, chỉ join/leave *trong cùng 1 phòng* mới bị serialize.

**Bug phụ khác cũng gặp:** `SpringApplicationBuilder.properties("server.port=0")` không có tác dụng ép cổng ngẫu nhiên — độ ưu tiên của nó thấp hơn `server.port: 8080` đã hardcode trong `application.yml`, nên bị đè ngược lại. Phải dùng `.run("--server.port=0")` (tương đương tham số dòng lệnh, ưu tiên cao nhất) mới ghi đè đúng.

### Ghi chú môi trường: Mockito không chạy được trên JDK 26

Lúc viết test cho phần chịu lỗi ở trên, thử dùng Mockito trước — thất bại thật với lỗi `Java 26 (70) is not supported by the current version of Byte Buddy which officially supports Java 23 (67)`. Đây là giới hạn của JDK 26 (bản rất mới) với engine bytecode mà Mockito's "inline mock maker" dùng để giả lập class/interface, **không phải bug trong code**. Cách xử lý: bỏ hẳn Mockito cho test này, tự viết `FakeWebSocketSession` (implement `WebSocketSession` thủ công, chỉ cài đặt đúng các method handler thực sự dùng tới) và dùng `RoomRegistry` **thật** thay vì mock nó — vừa né được vấn đề JDK, vừa nhất quán với phong cách "tự viết fake thay vì dùng framework mock nặng" đã có sẵn trong dự án (`LoopbackDataChannel`, `LoopbackSignalingClient`). Nếu sau này cần dùng Mockito cho việc khác, nhớ kiểm tra lại phiên bản JDK đang chạy trước.

### Đã kiểm chứng thật (không chỉ "viết xong")

31 test đã tự chạy bằng IntelliJ và **PASS**:
- `LoopbackDataChannelTest` — kênh dữ liệu giả lập.
- `P2pDataChannelTest` (3 test) — kênh dữ liệu UDP thật; test thứ 3 (`receiveLoopSurvivesAHandlerThatThrowsOnAMalformedOrTamperedPacket`) xác nhận vòng lặp nhận sống sót sau khi handler ném lỗi ở 1 gói tin, vẫn nhận đúng gói tin tiếp theo.
- `RoomSessionBroadcastErrorHandlingTest` — 1 peer gửi thất bại giữa lúc broadcast không chặn các peer khác trong cùng lần gửi.
- `IceP2pConnectionEstablisherTest` (2 test) — 2 `Agent` ice4j thật trên localhost, ICE chạy đúng RFC 8445, gửi/nhận dữ liệu thành công qua kênh vừa thiết lập, xác nhận `IceConnectionStats` báo đúng `usingRelay=false` trên localhost (không có TURN); test thứ 2 (`reportsOnFailedAndFreesTheUdpPortWhenRemoteCredentialsAreWrong`) cố ý làm sai mật khẩu ICE để ép ICE thất bại thật nhanh, xác nhận `onFailed` báo đúng **và** cổng UDP được giải phóng thật sau `dispose()`.
- `IceCandidateCodecTest` (8 test) — `decode()` từ chối đúng cách nhiều dạng dòng candidate sai định dạng (rỗng, thiếu token, sai vị trí marker `typ`, priority/port không phải số, candidate type không tồn tại).
- `RoomSessionMalformedIceCandidateTest` — 1 dòng ICE candidate hỏng/giả mạo bên trong 1 OFFER thật không làm crash/treo `RoomSession`, và không chặn peer khác trong cùng `PEER_LIST` kết nối bình thường.
- `EnvelopeCodecTest` — mã hoá/giải mã đúng, sai khoá hoặc dữ liệu bị sửa đều thất bại đúng cách.
- `PeerConnectionTest` — 2 `PeerConnection` tự trao khoá ECDH xong rồi gửi/nhận đúng 1 `Envelope` mã hoá.
- `RoomSessionTest` — 2 `RoomSession` (Alice vào trước, Bob vào sau) tự nhận đúng vai trò chủ động/trả lời, chạy ICE thật + trao khoá ECDH thật + gửi/nhận `Envelope` mã hoá thật, dùng `LoopbackSignalingClient` giả lập.
- `RoomSessionRealSignalingServerTest` — **cùng kịch bản trên nhưng qua `WebSocketSignalingClient` + 1 `signaling-server` thật** (tự boot bằng `SpringApplicationBuilder`, không phải giả lập) — sau khi sửa 2 bug ở trên, chạy đúng end-to-end.
- `WebSocketSignalingClientReconnectTest` — server "sập" thật (đóng thẳng context, không phải client tự ngắt) → xác nhận client tự chuyển `RECONNECTING` → server sống lại trên đúng cổng cũ → xác nhận client tự kết nối lại + tự JOIN lại (kiểm chứng bằng cách cho 1 peer khác vào phòng sau đó và xác nhận client cũ thấy được peer mới).
- `SignalingWebSocketHandlerErrorHandlingTest` (5 test) — JSON hỏng/thiếu `type` không làm throw; JOIN thiếu trường bắt buộc không làm nem NPE; JOIN 2 lần trên cùng session không LEAVE trước tự dọn dẹp entry cũ; 1 session đang "lỗi" đứng trước 1 session khoẻ mạnh trong danh sách broadcast không chặn được thông báo tới session khoẻ mạnh.
- `SignalingWebSocketHandlerTest` (2 test) — mở kết nối WebSocket **thật** qua Tomcat: JOIN trả đúng `PEER_LIST`/`PEER_JOINED`; gửi OFFER với payload 20.000 ký tự qua kết nối thật **không làm người gửi bị văng khỏi phòng** (sau khi tăng giới hạn buffer Tomcat lên 64KB ở cả 2 phía server/client).
- `RoomSessionErrorHandlingTest` — đối xứng phía client: giả lập gửi OFFER tới 1 peer thất bại, xác nhận `onConnectionFailed` báo đúng lỗi cho peer đó, establisher lỗi không bị "mồ côi" trong `pendingEstablishers` (đã dispose đúng), **và** vẫn kết nối thành công với peer còn lại trong cùng `PEER_LIST`.
- `RoomSessionThreePeerMeshTest` — mesh 3 peer thật (P1→P2→P3), xác nhận cả 3 cặp tự kết nối đúng (mỗi peer 2 kết nối) và broadcast từ 1 người tới đúng cả 2 người còn lại.
- `WebSocketSignalingClientCapacityTest` — 100 người dùng thật kết nối **đồng thời** (mỗi người 1 thread riêng) qua 1 `signaling-server` thật, xác nhận tất cả kết nối thành công và không ai bị thất lạc do race condition (đã phát hiện + sửa bug thread-safety `IllegalStateException` khi viết test này, ban đầu chạy 30 peer, đã tăng lên 100 và vẫn pass).
- `SignalingServerContentOpacityTest` — payload không phải JSON hợp lệ vẫn được relay nguyên văn, không làm server văng lỗi hay tự sửa; log thật (bắt bằng Logback `ListAppender`) không chứa nội dung payload; payload 200.000 ký tự không bị cắt bớt.

**Tầng 1 (Signaling) coi như đã hoàn thiện cả 3 mặt: chịu lỗi, khả năng mở rộng, và bảo mật** — cả 2 phía (server relay, client phản ứng với bản tin signaling) đều cô lập lỗi đúng theo nguyên tắc H.1 (1 lỗi cục bộ không lan sang các peer/bản tin khác), đã xác nhận chịu tải ≥100 người dùng đồng thời, và đã xác nhận thật (không chỉ đọc code) rằng server không bao giờ đọc/log nội dung payload.

### Chưa làm (mảnh còn thiếu để hoàn chỉnh nhiệm vụ A)

- `PEER_IDENTITY` tự động (cần `IdentitySignatureService` bằng ECDSA ở module `crypto` — chưa có, thuộc phần B).
- TURN dự phòng (mới có STUN — `IceConnectionStats.usingRelay()` đã sẵn sàng đo khi có TURN, chỉ chưa có harvester TURN để thử).
- Đo hiệu năng kết nối **tổng hợp qua nhiều kịch bản mạng thật** (đã có hạ tầng đo `IceConnectionStats` cho từng phiên; còn thiếu chạy thật qua LAN/NAT khác nhau và tổng hợp bảng số liệu như Tai-lieu-ky-thuat.md Phần F.5.3 yêu cầu).
- Test qua 2 máy thật khác NAT (mới test được trên 1 máy — theo tìm hiểu, cách duy nhất giả lập "2 máy khác NAT" chỉ bằng 1 laptop là dùng 2 máy ảo với chế độ mạng NAT riêng biệt, chưa dựng vì không cấp thiết).
- Nối `RoomSession` vào `RoomController`/UI thật của `client-javafx` (hiện UI vẫn đang dùng `LoopbackDataChannel`/`DemoPeerSimulator`, thuộc phần B).

### Rà soát SOLID/design pattern và 2 refactor gọn code (không đổi hành vi)

Sau khi toàn bộ chuỗi mạng cốt lõi đã chạy đúng và có test bao phủ, rà soát lại code theo góc nhìn SOLID/design pattern (không phải viết mới) để tìm chỗ vi phạm DRY/SRP còn sót — phát hiện đúng 2 chỗ trùng lặp logic, cả 2 đều đã refactor và **xác nhận lại toàn bộ test liên quan vẫn PASS** sau khi sửa (không đổi hành vi, chỉ gọn code lại):

1. **`SignalMessageDispatcher`** (mới, [SignalMessageDispatcher.java](../p2p-core/src/main/java/com/datn/chatp2p/p2p/signaling/SignalMessageDispatcher.java)) — trước đây `WebSocketSignalingClient` (cài đặt thật) và `LoopbackSignalingClient` (fake dùng cho test) mỗi lớp tự viết lại **y hệt** logic đăng ký/dispatch handler theo `SignalType` (`EnumMap<SignalType, List<Consumer<SignalMessage>>>` + `register()`/`dispatch()`) — vi phạm DRY, sửa 1 nơi dễ quên nơi kia. Tách ra 1 lớp `public` dùng chung, cả 2 lớp trên giờ chỉ giữ 1 field `SignalMessageDispatcher` và uỷ quyền toàn bộ việc đăng ký/dispatch cho nó.
2. **`RoomSession.createEstablisherFor(peerId, userName)`** — `connectAsOfferer` và `handleOffer` trước đây tự lặp lại y hệt đoạn tạo `IceP2pConnectionEstablisher` mới, đăng ký vào `pendingEstablishers`, gán `onConnected`/`onFailed`. Trích thành 1 helper `private` dùng chung, mỗi nơi gọi nó chỉ còn giữ lại phần logic riêng của mình (gửi OFFER, hoặc tạo+gửi ANSWER).

Đã chạy lại toàn bộ test bị ảnh hưởng sau refactor và **PASS**: `RoomSessionTest`, `RoomSessionThreePeerMeshTest`, `RoomSessionErrorHandlingTest`, `RoomSessionRealSignalingServerTest`, `WebSocketSignalingClientReconnectTest`, `WebSocketSignalingClientCapacityTest`.

### Rà soát SOLID/design pattern lần 2 (sau khi hoàn tất Tầng 2)

Sau khi Tầng 2 (ICE) đã được rà soát đầy đủ cả chịu lỗi/mở rộng/bảo mật, rà soát lại toàn bộ code production 1 lượt nữa theo góc nhìn SOLID/design pattern — đọc lại `SignalingWebSocketHandler`, `RoomRegistry`, `WebSocketConfig`, `WebSocketSignalingClient`/`SignalMessageDispatcher`, `IceP2pConnectionEstablisher`, `IceCandidateCodec`, `RoomSession`, `PeerConnection`, `EnvelopeCodec`. Đa số đã ổn (`PeerConnection`/`EnvelopeCodec`/`IceCandidateCodec` SRP tốt, không trùng lặp; tầng signaling đã được dọn ở lượt review trước) — tìm thấy 2 chỗ đáng sửa, cả 2 đều ở `RoomSession.java`:

1. **Trùng lặp DRY (4 lần)**: chuỗi `cleanupFailedEstablisher(peerId); notifyConnectionFailed(peerId, error);` bị lặp y hệt ở 4 nơi (catch của `handlePeerList`/`handleOffer`/`handleAnswer`, và `handleIceFailed`) — gộp thành 1 helper `failConnection(peerId, error)` dùng chung.
2. **Dùng tên lớp đầy đủ (FQN) thay vì import**: `handleOffer`/`handleAnswer` viết `com.datn.chatp2p.common.signal.ice.IceOfferPayload.class`/`IceAnswerPayload.class` trực tiếp trong thân hàm thay vì import ở đầu file — đã thêm import, bỏ FQN.

**Ghi nhận nhưng không sửa (DIP)**: `RoomSession` phụ thuộc trực tiếp vào class cụ thể `IceP2pConnectionEstablisher`/`PeerConnection` (không qua interface) — chấp nhận được vì dự án đã chọn chủ đích "dùng object thật thay vì mock/interface-cho-test" (xem mục Mockito ở trên), và hiện chưa có nhu cầu thay thế implementation nào khác cho 2 lớp này.

Không đổi hành vi, chỉ gọn code. Đã chạy lại toàn bộ test liên quan (`RoomSessionTest`, `RoomSessionThreePeerMeshTest`, `RoomSessionErrorHandlingTest`, `RoomSessionMalformedIceCandidateTest`, `RoomSessionDuplicateOfferSecurityTest`, `RoomSessionRealSignalingServerTest`) và xác nhận **PASS**.

### Rà soát lại Tầng 1 từ đầu (lượt 2) — phát hiện thêm 3 bug thật

Sau khi coi Tầng 1 "đã hoàn thiện", chủ động đọc lại toàn bộ `SignalingWebSocketHandler`/`RoomRegistry`/`WebSocketConfig` một lượt nữa với tinh thần hoài nghi (không tin claim cũ, tự hỏi "còn góc nào chưa test thật") — tìm ra đúng 3 chỗ hổng thật, mỗi chỗ đều viết test chứng minh TRƯỚC/SAU khi sửa, không chỉ đọc code suông:

1. **`NullPointerException` khi JOIN thiếu trường bắt buộc.** `PeerSession` dùng `Objects.requireNonNull` cho cả `roomId`/`peerId`/`userName`, nhưng `handleJoin` trước đó gọi thẳng `new PeerSession(...)` mà không tự validate trước — 1 bản tin JOIN hợp lệ về JSON nhưng thiếu `fromPeerId`/`userName` (bug client, hoặc cố ý) sẽ ném NPE **không ai bắt**, khác hẳn cách xử lý cẩn thận đã áp dụng cho JSON hỏng (mục "Chịu lỗi ở tầng signaling" ở trên). **Đã sửa:** thêm validate đầu `handleJoin`, log `WARN` + bỏ qua đúng bản tin đó thay vì ném lỗi — xác nhận bằng test `joinMissingRequiredFieldsIsIgnoredWithoutThrowingOrClosingTheSession`.

2. **Kết nối của người GỬI bị Tomcat tự đóng khi gửi bản tin vượt 8KB — không chỉ "bị cắt bớt" như tưởng.** Test cũ (`relaysVeryLargePayloadWithoutTruncationOrError`, 200.000 ký tự) gọi thẳng `handler.handleTextMessage()` với `FakeWebSocketSession` — bỏ qua **hoàn toàn** giới hạn buffer thật của Tomcat (`maxTextMessageBufferSize` mặc định **8192 byte**). Viết test mới `relaysALargeIceOfferPayloadOverARealWebSocketConnection` dùng kết nối WebSocket **thật** (`StandardWebSocketClient` + Tomcat random port, gửi 20.000 ký tự) — kết quả thật sự khác hẳn dự đoán: không phải payload bị cắt bớt, mà **toàn bộ kết nối của người gửi (Alice) bị server đóng ngay lập tức**, Alice bị văng khỏi phòng hoàn toàn (log xác nhận: `"Peer alice da roi phong"` xảy ra ngay sau khi gửi, phía nhận chỉ thấy `PEER_LEFT` thay vì OFFER). Đây là kịch bản có thật (máy nhiều adapter mạng ảo — VPN/Docker/WSL/Hyper-V — có thể sinh đủ nhiều ICE candidate để vượt 8KB). **Đã sửa:** thêm bean `ServletServerContainerFactoryBean` trong `WebSocketConfig` tăng giới hạn buffer server lên 65536 byte (64KB); phát hiện thêm giới hạn này tồn tại **độc lập ở cả 2 phía** (client nhận dùng `ContainerProvider.getWebSocketContainer()` riêng, không chung container với server) nên test cũng phải tự cấu hình container phía client mới relay xuôi hết toàn trình.

3. **Entry "mồ côi" trong `RoomRegistry` khi 1 session JOIN 2 lần không LEAVE.** Không có gì ngăn 1 client gửi JOIN lần 2 trên cùng kết nối (đổi phòng/đổi peerId) mà không gửi LEAVE trước — `RoomRegistry` khi đó giữ **2 entry** cho cùng 1 session, nhưng `leaveBySession()` (gọi lúc kết nối đóng thật sự) chỉ tìm và xoá đúng **entry đầu tiên** khớp `session.getId()` nó gặp — entry còn lại "mồ côi" vĩnh viễn, phòng cũ không bao giờ nhận được `PEER_LEFT`. **Đã sửa:** `handleJoin` tự gọi lại `removeAndNotify` cho entry cũ (nếu session đã JOIN trước đó) trước khi thêm entry mới — xác nhận bằng test `joiningTwiceOnTheSameSessionWithoutLeavingCleansUpThePreviousRoomEntry`.

Đã chạy lại **toàn bộ** test có đụng tới `SignalingWebSocketHandler`/`WebSocketConfig` sau khi sửa cả 3 bug và xác nhận **PASS**: `SignalingWebSocketHandlerErrorHandlingTest` (5 test), `SignalingWebSocketHandlerTest` (2 test), `SignalingServerContentOpacityTest`, `RoomSessionRealSignalingServerTest`, `WebSocketSignalingClientReconnectTest`, `WebSocketSignalingClientCapacityTest`.

---

## Giai đoạn 1: Dựng khung dự án đa module

**Mục tiêu:** tách hệ thống thành các module Maven độc lập để 2 thành viên code song song không giẫm chân nhau.

**Đã làm:**
1. Tạo `pom.xml` cha (parent POM) khai báo `packaging=pom`, quản lý version chung (Java 17, Spring Boot, JUnit 5) qua `dependencyManagement`.
2. Tách 5 module con: `common` (model + giao ước dùng chung), `crypto` (thuộc B), `p2p-core` (thuộc A — interface + cài đặt tầng P2P), `signaling-server` (thuộc A), `client-javafx` (thuộc B).
3. Khai báo phụ thuộc giữa các module: `signaling-server` chỉ phụ thuộc `common` — **không** phụ thuộc `crypto`/`p2p-core`, đúng nguyên tắc "server chỉ relay, không xử lý nội dung/mã hoá" (Tai-lieu-ky-thuat.md Phần C.2).

**Kết quả:** `mvn -q -DskipTests package` build được cả 5 module từ gốc repo.

## Giai đoạn 2: Định nghĩa giao thức signaling tối giản (`common`)

**Mục tiêu:** có một định dạng bản tin chung, độc lập framework, để cả server lẫn client (sau này) cùng (de)serialize.

**Đã làm:**
1. Viết `SignalType` ([SignalType.java](../common/src/main/java/com/datn/chatp2p/common/signal/SignalType.java)) — enum 8 giá trị: `JOIN, LEAVE` (client→server), `PEER_JOINED, PEER_LEFT, PEER_LIST` (server→client), `OFFER, ANSWER, ICE_CANDIDATE` (client→server→client đích, server chỉ relay).
2. Viết `SignalMessage` ([SignalMessage.java](../common/src/main/java/com/datn/chatp2p/common/signal/SignalMessage.java)) — POJO thuần (không annotation Jackson) gồm `type, roomId, fromPeerId, toPeerId, userName, payload, peers[]`; cố tình để `payload` là `String` cơ hội — server không bao giờ parse bên trong nó, chỉ có client (sau này) mới hiểu SDP/ICE candidate chứa gì.
3. Thêm factory method tiện dụng `SignalMessage.join(roomId, peerId, userName)` cho thao tác JOIN — dùng lại được cả ở test lẫn ở client thật sau này.

**Quyết định thiết kế đáng chú ý:** để `SignalMessage` không phụ thuộc Jackson annotation (chỉ dùng constructor rỗng + getter/setter chuẩn) — nhờ vậy module `common` không phải kéo theo Jackson như một dependency bắt buộc, những module không cần serialize (ví dụ nếu sau này có module khác chỉ dùng model thuần) không bị ảnh hưởng.

## Giai đoạn 3: Chốt 2 interface giao ước với Thành viên B

**Mục tiêu:** để B dựng UI + mã hoá song song mà không phải chờ A code xong phần mạng thật (Phan-cong-cong-viec.md mục 3).

**Đã làm:**
1. Viết `DataChannel` ([DataChannel.java](../common/src/main/java/com/datn/chatp2p/common/channel/DataChannel.java)) trong `common` — interface 3 method: `send(byte[])`, `onReceive(Consumer<byte[]>)`, `close()`. Đặt trong `common` (không phải `p2p-core`) vì cả A lẫn B đều cần thấy được nó.
2. Viết `SignalingClient` ([SignalingClient.java](../p2p-core/src/main/java/com/datn/chatp2p/p2p/signaling/SignalingClient.java)) trong `p2p-core` — interface 10 method (`connect`, `onPeerJoined/onPeerLeft/onPeerList`, `onOffer/onAnswer/onIceCandidate`, `sendOffer/sendAnswer/sendIceCandidate`, `disconnect`) mô tả đầy đủ vòng đời một client tham gia phòng — chốt trước để `WebSocketSignalingClient` (bước sau) và `RoomSession` (việc sau này) đều dựa vào cùng một hợp đồng.
3. Cài đặt tạm `LoopbackDataChannel` ([LoopbackDataChannel.java](../p2p-core/src/main/java/com/datn/chatp2p/p2p/LoopbackDataChannel.java)) — 2 đầu kênh nối thẳng với nhau trong bộ nhớ, gửi bất đồng bộ qua 1 `ExecutorService` riêng (mô phỏng đúng hành vi bất đồng bộ của mạng thật thay vì gọi callback đồng bộ ngay tại chỗ). Đây là việc B cần để dựng UI ngay, không phải chờ A.
4. Viết khung `P2pDataChannel`/`WebSocketSignalingClient` — cài đặt thật của A cho 2 interface trên — nhưng **để trống**, mọi method `throw UnsupportedOperationException` kèm Javadoc TODO trỏ đúng tuần cần hoàn thiện, để 2 module vẫn biên dịch được và các module khác (`client-javafx`) có thể phụ thuộc vào lớp này mà không bị chặn bởi lỗi biên dịch.

**Kết quả kiểm chứng:** `LoopbackDataChannelTest` (1 test) xác nhận 2 đầu kênh gửi/nhận đúng dữ liệu 2 chiều — chứng minh interface `DataChannel` đủ dùng cho cả 2 phía trước khi có kết nối thật.

## Giai đoạn 4: Cài đặt lõi quản lý phòng (`signaling-server`)

**Mục tiêu:** biết "ai đang ở phòng nào" trong bộ nhớ, không đọc/lưu nội dung chat.

**Đã làm:**
1. Viết `PeerSession` ([PeerSession.java](../signaling-server/src/main/java/com/datn/chatp2p/signaling/room/PeerSession.java)) — gắn 1 `WebSocketSession` của Spring với `roomId/peerId/userName`; chỉ giữ đúng 3 trường metadata kết nối, không có trường nào chứa nội dung chat.
2. Viết `RoomRegistry` ([RoomRegistry.java](../signaling-server/src/main/java/com/datn/chatp2p/signaling/room/RoomRegistry.java)) — `Map<roomId, Map<peerId, PeerSession>>` bằng `ConcurrentHashMap` (an toàn khi nhiều WebSocket session xử lý đồng thời trên các thread khác nhau của Spring). Cung cấp `join()` (trả về danh sách peer đã có sẵn trước khi peer mới được thêm vào — dùng để trả lời `PEER_LIST`), `leave()`, `peersInRoom()`, `find()`, và `leaveBySession()` (duyệt toàn bộ để tìm đúng peer gắn với 1 `WebSocketSession` bị đóng, dùng khi client tắt đột ngột không kịp gửi LEAVE).
3. Cố tình giữ toàn bộ trong bộ nhớ (`ConcurrentHashMap`, không có database) — đúng nguyên tắc ephemeral: server restart là mất hết phòng, chấp nhận được vì đây là hành vi thiết kế, không phải thiếu sót (Tai-lieu-ky-thuat.md Phần F.4).

## Giai đoạn 5: Cài đặt handler WebSocket + đăng ký endpoint

**Mục tiêu:** nhận/gửi các `SignalMessage` qua WebSocket thật, đúng vai trò "chỉ relay, không đọc nội dung".

**Đã làm:**
1. Viết `SignalingWebSocketHandler` ([SignalingWebSocketHandler.java](../signaling-server/src/main/java/com/datn/chatp2p/signaling/ws/SignalingWebSocketHandler.java)) kế thừa `TextWebSocketHandler` của Spring:
   - `handleTextMessage`: parse JSON thành `SignalMessage` bằng `ObjectMapper`, `switch` theo `type` — `JOIN` gọi `handleJoin`, `LEAVE` gọi `handleLeave`, `OFFER/ANSWER/ICE_CANDIDATE` gọi `relayToTargetPeer` (chuyển tiếp **nguyên văn** `payload`, không parse bên trong), các loại chỉ-server-mới-phát (`PEER_JOINED/PEER_LEFT/PEER_LIST`) bị bỏ qua nếu client lỡ gửi lên.
   - `handleJoin`: tạo `PeerSession`, lưu `roomId`/`peerId` vào `session.getAttributes()` (để tra cứu lại khi cần, ví dụ lúc đóng kết nối), gọi `RoomRegistry.join()`, trả `PEER_LIST` cho chính peer vừa vào, rồi broadcast `PEER_JOINED` cho các peer còn lại trong phòng (trừ chính nó).
   - `afterConnectionClosed` (override từ Spring, tự gọi khi WebSocket đóng vì bất kỳ lý do gì — kể cả mất mạng đột ngột) và `handleLeave` đều gọi chung `removeAndNotify` — đảm bảo dù client rời phòng "lịch sự" (gửi LEAVE) hay rớt mạng đột ngột, các peer còn lại đều nhận được `PEER_LEFT`.
   - `relayToTargetPeer`: validate có đủ `roomId`/`toPeerId` không, tìm đúng `WebSocketSession` đích qua `RoomRegistry.find()`, nếu không thấy thì log `WARN` và bỏ qua bản tin đó — **không** làm sập cả kết nối của các peer khác (đúng nguyên tắc xử lý lỗi ở Tai-lieu-ky-thuat.md Phần H.1).
2. Viết `WebSocketConfig` ([WebSocketConfig.java](../signaling-server/src/main/java/com/datn/chatp2p/signaling/config/WebSocketConfig.java)) — đăng ký `SignalingWebSocketHandler` vào endpoint cố định `/ws`, cho phép mọi origin (`setAllowedOriginPatterns("*")`) vì server không xử lý dữ liệu nhạy cảm.
3. Viết `SignalingServerApplication` ([SignalingServerApplication.java](../signaling-server/src/main/java/com/datn/chatp2p/signaling/SignalingServerApplication.java)) — entry point `@SpringBootApplication` tối giản.
4. Cấu hình `pom.xml` của `signaling-server`: thêm `spring-boot-starter-websocket` + khai báo riêng `jackson-databind` (starter-websocket không tự kéo Jackson như starter-web, phải thêm tay để có bean `ObjectMapper`), và khai báo tường minh goal `repackage` của `spring-boot-maven-plugin` (module không kế thừa `spring-boot-starter-parent` nên goal này không tự gắn vào phase `package`).

## Giai đoạn 6: Viết integration test thật (không chỉ test biên dịch)

**Mục tiêu:** chứng minh server hoạt động đúng bằng WebSocket thật, không chỉ unit test cô lập.

**Đã làm:** viết `SignalingWebSocketHandlerTest` ([SignalingWebSocketHandlerTest.java](../signaling-server/src/test/java/com/datn/chatp2p/signaling/SignalingWebSocketHandlerTest.java)) dùng `@SpringBootTest(webEnvironment = RANDOM_PORT)` — khởi động Tomcat thật trên cổng ngẫu nhiên, dùng `StandardWebSocketClient` của Spring mở 2 kết nối WebSocket thật (mô phỏng "Alice" và "Bob"):
1. Alice JOIN phòng mới → xác nhận nhận lại `PEER_LIST` rỗng (phòng chưa có ai khác).
2. Bob JOIN cùng phòng → xác nhận Bob nhận `PEER_LIST` có đúng 1 phần tử là Alice.
3. Xác nhận Alice nhận được `PEER_JOINED` đúng lúc Bob vào, với `fromPeerId = "bob"`.

Kết quả: **1 test, pass** — đây là integration test thật (có Spring context + Tomcat + WebSocket client thật), không phải mock, nên xác nhận được cả cấu hình (`WebSocketConfig`, bean `ObjectMapper`) lẫn logic (`RoomRegistry`, `SignalingWebSocketHandler`) hoạt động đúng cùng nhau.

## Giai đoạn 7: Xử lý vấn đề triển khai thực tế trên Windows

**Vấn đề phát hiện:** `mvn -pl signaling-server spring-boot:run` báo `Could not find or load main class` dù `mvn package` build thành công — xảy ra vì đường dẫn project chứa dấu tiếng Việt (`...\Máy tính\...`), làm goal `spring-boot:run` ghi/đọc sai encoding file classpath tạm (`@argfile`).

**Cách khắc phục đã áp dụng và xác nhận hoạt động:** không dùng `spring-boot:run`; đóng gói fat jar bằng `mvn package` (nhờ đã khai báo goal `repackage` ở Giai đoạn 5) rồi chạy trực tiếp:
```bash
java -jar signaling-server/target/signaling-server.jar
```
Đã xác nhận: Tomcat khởi động đúng, endpoint `/ws` phản hồi HTTP 400 khi gọi bằng GET thường (đúng hành vi kỳ vọng cho endpoint chỉ nhận WebSocket upgrade).

---

## Tổng kết Giai đoạn 1 (đã hoàn thành)

| Hạng mục | Trạng thái |
|---|---|
| Khung đa module Maven (5 module) | ✅ Build được |
| Giao thức `SignalMessage`/`SignalType` | ✅ |
| Interface `DataChannel`, `SignalingClient` | ✅ Đã chốt, dùng chung với B |
| `LoopbackDataChannel` (tạm, để B không phải chờ) | ✅ Có test, pass |
| `RoomRegistry`/`PeerSession` | ✅ |
| `SignalingWebSocketHandler`/`WebSocketConfig`/`SignalingServerApplication` | ✅ |
| Integration test WebSocket thật | ✅ 1 test, pass |
| Chạy được bằng `java -jar` (đã né bug path tiếng Việt) | ✅ |

## Giai đoạn 8: Thêm dependency `ice4j` + cài đặt thật `WebSocketSignalingClient`

**Mục tiêu:** thay bản stub `WebSocketSignalingClient` (ném `UnsupportedOperationException`) bằng cài đặt thật, dùng đúng `java.net.http.HttpClient` như Tai-lieu-ky-thuat.md Phần E.6.4 đề xuất — không cần thêm thư viện WebSocket client ngoài.

**Đã làm:**
1. Thêm property `ice4j.version` + entry `dependencyManagement` cho `org.jitsi:ice4j` vào `pom.xml` gốc (version xác nhận qua Maven Central Search API tại thời điểm viết: `3.2-8-gfa5f931` — ice4j dùng kiểu versioning "git describe", không theo semver, nên **kiểm tra lại phiên bản mới nhất** trước khi build nếu đã lâu).
2. Thêm dependency `jackson-databind` + `ice4j` vào `p2p-core/pom.xml`.
3. Viết lại hoàn chỉnh `WebSocketSignalingClient` ([WebSocketSignalingClient.java](../p2p-core/src/main/java/com/datn/chatp2p/p2p/signaling/WebSocketSignalingClient.java)): `connect()` mở kết nối qua `HttpClient.newWebSocketBuilder().buildAsync(...)`, gửi `JOIN` ngay sau khi kết nối; mọi `send*` gói thành `SignalMessage` rồi `sendText` dạng JSON (Jackson); `Listener.onText` gộp các frame bị chia nhỏ (`last=false`) trước khi parse, dispatch theo `SignalType` tới đúng danh sách handler đã đăng ký qua `on*`.

## Giai đoạn 9: Cài đặt thật `P2pDataChannel`

**Đã làm:** viết lại `P2pDataChannel` ([P2pDataChannel.java](../p2p-core/src/main/java/com/datn/chatp2p/p2p/channel/P2pDataChannel.java)) nhận sẵn 1 `DatagramSocket` + địa chỉ đích đã được ICE chọn — không tự làm ICE, chỉ lo gửi/nhận byte: `send()` thêm 4-byte length-prefix rồi gửi qua `DatagramSocket.send`, 1 thread nền `receive()` liên tục rồi gọi `receiveHandler`. Viết `P2pDataChannelTest` dùng 2 `DatagramSocket` thật trên `localhost` (khác cổng UDP) để kiểm chứng logic framing/gửi/nhận — **không cần ice4j**, nên có thể chạy độc lập.

## Giai đoạn 10: Điều phối ICE thật bằng ice4j (`IceP2pConnectionEstablisher`)

**Mục tiêu:** lớp mới đứng giữa `SignalingClient` và `P2pDataChannel` — chạy `Agent` của ice4j để 2 phía thật sự "bắt tay" qua UDP.

**Đã làm:**
1. Thêm 2 record `IceOfferPayload`/`IceAnswerPayload` (`ufrag, password, candidates[]`) vào `common` — nội dung JSON sẽ nằm trong `SignalMessage.payload` (Tai-lieu-ky-thuat.md Phần E.6.1).
2. Viết `IceCandidateCodec` — encode 1 `LocalCandidate` thành dòng văn bản chuẩn RFC 5245/8839 (dùng thẳng `Candidate.toString()` có sẵn của ice4j, không tự bịa định dạng), và decode ngược lại thành `RemoteCandidate` để `component.addRemoteCandidate(...)`.
3. Viết `IceP2pConnectionEstablisher` — bọc 1 `Agent` + 1 `IceMediaStream` + 1 `Component`: bên chủ động gọi `createOffer()` (gather candidate, đánh dấu `controlling=true`); bên nhận gọi `createAnswer(offer)` (áp thông tin đối phương, tạo answer, tự gọi `startConnectivityEstablishment()` ngay); bên chủ động gọi `acceptAnswer(answer)` sau khi nhận answer để bắt đầu xử lý. Lắng nghe `Agent.PROPERTY_ICE_PROCESSING_STATE`: khi `COMPLETED` thì lấy `component.getSocket()` + địa chỉ đối phương từ `component.getSelectedPair()`, dựng `P2pDataChannel` và gọi callback `onConnected`; khi `FAILED` gọi callback `onFailed`.
4. Viết `IceP2pConnectionEstablisherTest` — 2 `Agent` thật chạy trên cùng máy (khác cổng UDP), không dùng STUN server (localhost chỉ cần host candidate), tự trao offer/answer trong test rồi xác nhận cả 2 bên báo `COMPLETED` và gửi/nhận dữ liệu qua kênh vừa thiết lập được.

**Quyết định thiết kế đáng chú ý:**
- Tắt tường minh trickle ICE (`agent.setTrickling(false)`) thay vì dựa vào giá trị mặc định của ice4j — gather toàn bộ candidate xong mới gửi 1 lần, đúng thiết kế offer/answer đơn giản ở Phần E.6.1 (không làm trickle ICE trong bản đầu).
- Constructor `IceP2pConnectionEstablisher` bọc `IOException`/`BindException` từ `Agent.createComponent(...)` thành `IllegalStateException` có thông điệp rõ ràng (không còn port UDP trống trong khoảng cấu hình) — đồng bộ với cách xử lý lỗi ở các lớp khác trong module.

**Chưa làm (ghi rõ để không nhận vơ):**
- TURN relay dự phòng — mới có STUN harvester, chưa có `TurnCandidateHarvester`.
- Xử lý mất kết nối `WebSocketSignalingClient` (tự động reconnect) — vẫn còn TODO như Phần H.3 đã nêu.
- Chưa nối `IceP2pConnectionEstablisher` vào `RoomSession`/`RoomController` thật (vẫn cần `RoomSession` — Giai đoạn kế tiếp).

**Giới hạn của việc kiểm chứng khi viết code:** môi trường làm việc của trợ lý AI không có `mvn` trên PATH nên không tự chạy được `mvn test` để xác nhận — chỉ dùng được chẩn đoán biên dịch thời gian thực của IDE (đã phát hiện và sửa 2 lỗi thật lúc viết: thiếu try/catch cho `IOException`/`BindException` của `Agent.createComponent`, và 1 lần tự làm hỏng dòng `import` do dùng `replace_all` không cẩn thận). Các hằng số/API của `ice4j` đã được tra cứu trực tiếp từ mã nguồn thật trên GitHub (nhánh `master` của `jitsi/ice4j`) thay vì suy đoán — xem Giai đoạn 11 để biết kết quả chạy thật.

## Giai đoạn 11: Chạy thật bằng IntelliJ trên máy — phát hiện và sửa 1 bug thật

**Đã làm:** tự chạy 3 test bằng IntelliJ (không qua dòng lệnh `mvn`, dùng Run trực tiếp trong IDE):

1. Lần chạy đầu tiên `IceP2pConnectionEstablisherTest` → **lỗi thật**:
   ```
   java.lang.IllegalArgumentException: preferredPort (0) must be between minPort (10000) and maxPort (10100)
       at org.ice4j.ice.harvest.HostCandidateHarvester.checkPorts(...)
       at ... Agent.createComponent(Agent.java:533)
       at IceP2pConnectionEstablisher.<init>(IceP2pConnectionEstablisher.java:98)
   ```
   Nguyên nhân: khác với `java.net.ServerSocket`, ice4j **không** chấp nhận `preferredPort=0` nghĩa là "cổng bất kỳ" — bắt buộc phải nằm trong khoảng `[minPort, maxPort]`. Đã tra lại mã nguồn `HostCandidateHarvester.createDatagramSocket` để xác nhận: nếu bind thất bại ở `preferredPort`, nó tự tăng dần cổng, quay vòng trong khoảng `[minPort, maxPort]` cho tới khi tìm được cổng trống — vậy chỉ cần sửa `PREFERRED_PORT` từ `0` thành `10_000` (bằng `MIN_PORT`) là đủ, không cần đổi gì khác.
2. Sửa 1 dòng trong `IceP2pConnectionEstablisher.java`, chạy lại → **PASS**: `establishesDirectConnectionAndExchangesDataOnLocalhost` — ✔ **1 test passed, 2s 47ms** (xác nhận bằng ảnh chụp panel kết quả test của IntelliJ, không chỉ dựa vào log console).

**Log thật xác nhận ICE hoạt động đúng** (không phải suy đoán): cả 2 `Agent` (offerer/answerer) đều báo `ICE state changed from Running to Completed`, có `Nomination confirmed for pair`, `Selected pair for stream data.RTP`, `Harvester used for selected pair: host` — đúng trình tự RFC 8445 thật, chạy qua candidate host thật trên máy (không phải mock). 2 dòng `Closing.`/`Failed to receive: Socket closed` xuất hiện sau đó là log dọn dẹp bình thường của `agent.free()` gọi trong `@AfterEach` sau khi test đã gửi/nhận dữ liệu xong — đã đối chiếu với mã nguồn `Agent.java` (`terminate()`/`free()`) để xác nhận đây không phải lỗi.

3. Chạy tiếp `LoopbackDataChannelTest` và `P2pDataChannelTest` bằng IntelliJ (Run riêng từng file) → **cả 2 đều pass**. Vậy cả 3 test trong `p2p-core` (`LoopbackDataChannelTest`, `P2pDataChannelTest`, `IceP2pConnectionEstablisherTest`) đã được tự tay chạy và xác nhận PASS trên máy thật qua IntelliJ trong phiên này — không còn dựa vào chẩn đoán tĩnh của IDE nữa.

**Còn thiếu để coi là "xong hẳn":**
- Chưa chạy `mvn -pl common,p2p-core test` qua dòng lệnh (chỉ mới chạy từng test đơn lẻ qua IntelliJ) — nên chạy 1 lần đầy đủ cả module để chắc chắn không sót lỗi ở test khác (chạy qua IntelliJ thường chỉ compile lại phần đã đổi, không chắc bằng 1 lần `mvn clean test` sạch từ đầu).
- Chưa test `IceP2pConnectionEstablisher` giữa 2 máy thật khác NAT (mới có localhost, chưa cần STUN/TURN thật).
- Chưa có test tự động riêng cho `WebSocketSignalingClient` (khó viết vì cần `signaling-server` thật chạy song song).

## Tổng kết trạng thái hiện tại

| Hạng mục | Trạng thái |
|---|---|
| Khung đa module Maven (5 module) | ✅ Build được |
| Giao thức `SignalMessage`/`SignalType` | ✅ |
| Interface `DataChannel`, `SignalingClient` | ✅ Đã chốt, dùng chung với B |
| `LoopbackDataChannel` (tạm, để B không phải chờ) | ✅ Có test, **đã chạy lại bằng IntelliJ trong phiên này, pass** |
| `RoomRegistry`/`PeerSession` | ✅ |
| `SignalingWebSocketHandler`/`WebSocketConfig`/`SignalingServerApplication` | ✅ |
| Integration test WebSocket thật (signaling-server) | ✅ 1 test, pass |
| Chạy được bằng `java -jar` (đã né bug path tiếng Việt) | ✅ |
| `WebSocketSignalingClient` (thật, HttpClient) | ✅ Viết xong, **chưa có test tự động riêng** (đã dùng gián tiếp qua các test khác, nhưng chưa test chính lớp này) |
| `P2pDataChannel` (thật, UDP + framing) | ✅ **Đã chạy thật bằng IntelliJ, PASS** (`P2pDataChannelTest`) |
| `IceCandidateCodec`, `IceP2pConnectionEstablisher` (ice4j) | ✅ **Đã chạy thật bằng IntelliJ, PASS** (1 test, 2s47ms) — xem Giai đoạn 11 |
| `RoomSession`/`PeerConnection` (mesh nhiều peer) | ❌ Chưa làm |
| Đo hiệu năng kết nối | ❌ Chưa làm |

**Tất cả 3 test trong `p2p-core` đã được tự tay chạy và xác nhận PASS bằng IntelliJ trong phiên làm việc này** (`LoopbackDataChannelTest`, `P2pDataChannelTest`, `IceP2pConnectionEstablisherTest`).

## Việc tiếp theo của nhiệm vụ A

1. (Tuỳ chọn, nên làm trước khi coi là "chốt") Chạy 1 lần `mvn -pl common,p2p-core -am clean test` qua dòng lệnh để có 1 lần build sạch từ đầu, đối chiếu với kết quả chạy rời rạc qua IntelliJ ở trên.
2. Thử nghiệm `IceP2pConnectionEstablisher` giữa 2 máy thật khác NAT (không chỉ localhost) — xem có cần STUN server thật, TURN dự phòng hay không.
3. Viết `RoomSession`/`PeerConnection` để quản lý mesh nhiều peer, nối `IceP2pConnectionEstablisher` + `WebSocketSignalingClient` + `EnvelopeCodec` (chưa có) lại với nhau.
4. Đo hiệu năng kết nối (tỉ lệ P2P thành công, độ trễ thiết lập).

*(Xem Tai-lieu-ky-thuat.md Phần E.4/E.6/E.7 để biết chi tiết thiết kế cho các mục còn lại.)*

---

*Ghi chú: các Giai đoạn 1–7 đã được xác nhận qua `mvn test` thật ở phiên làm việc trước (xem lịch sử trong chính file này). Giai đoạn 8–10 (ice4j) được viết trong 1 phiên không có Maven trên PATH nên lúc viết chỉ kiểm tra được bằng chẩn đoán tĩnh của IDE; Giai đoạn 11 sau đó đã tự chạy thật cả 3 test của `p2p-core` bằng IntelliJ trên máy, phát hiện và sửa đúng 1 bug thật (`preferredPort=0` không hợp lệ với ice4j), và xác nhận cả `LoopbackDataChannelTest`, `P2pDataChannelTest`, `IceP2pConnectionEstablisherTest` đều PASS. Việc còn lại chỉ là chạy 1 lần `mvn` dòng lệnh cho chắc và thử qua 2 máy thật khác NAT trước khi coi phần ICE là "chốt hẳn". Cập nhật tiếp file này thành các giai đoạn mới khi hoàn thành từng phần.*
