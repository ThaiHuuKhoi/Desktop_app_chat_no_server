# Cơ chế kiểm thử và đo đạc

Tài liệu này trả lời câu hỏi: **làm sao biết các cơ chế trong 14 tài liệu kia thật sự chạy đúng, kể cả khi mạng xấu, khi có người tấn công, và sau mỗi lần sửa code?**

Cách trình bày giống các tài liệu cơ chế khác: đi từ cách ngây thơ, tìm lỗ hổng, phân tích cần gì, rồi sinh ra bước tiếp theo. Ở mỗi chỗ phải chọn có so sánh các phương án.

Số liệu trong tài liệu này lấy từ lần chạy ngày 22/11/2026 sau commit `36ccc20`.

| Module | Số test | Bỏ qua |
|---|---|---|
| `crypto` | 42 | 0 |
| `signaling-server` | 36 | 0 |
| `p2p-core` | 121 | 1 (mesh 8 người, xem Bước 5) |
| `client-javafx` | 92 | 1 (benchmark, chỉ chạy khi bật cờ) |
| **Tổng** | **291** | **2**, không có lỗi |

---

## Bước 0 — Kiểm thử bằng tay

**Làm gì:** sửa code xong, Khôi mở hai cửa sổ ứng dụng, vào cùng một phòng, gõ vài tin, gửi một file. Thấy tin hiện ra là coi như xong.

**Đáp ứng yêu cầu:** nhanh, thấy được kết quả ngay trên giao diện.

**→ Lỗ hổng:** cách này chỉ thử được **trường hợp suôn sẻ**, và **không lặp lại được**.

**Phân tích vấn đề:**
- **Mạng xấu không thử được:** hai cửa sổ trên cùng một máy gần như không bao giờ mất gói, nên cơ chế gửi lại, chống trùng, chia mảnh không hề được chạy tới.
- **Tấn công không thử được:** không ai ngồi tay phát lại một gói đã mã hoá, gửi `OFFER` trùng mã người, hay dán chuỗi 600 nghìn ký tự.
- **Sửa chỗ này hỏng chỗ khác:** mỗi lần sửa lại phải thử tay lại mọi thứ. Thực tế không ai làm vậy, và lỗi cũ quay lại mà không ai biết.
- **Không chứng minh được với người khác:** "tôi đã thử rồi" không phải là bằng chứng.

**Để giải quyết thì cần gì:**
- **R1:** mỗi hành vi quan trọng có một **bài kiểm tra tự động**, chạy lại được bất cứ lúc nào và cho cùng kết quả.
- **R2:** với thuật toán mật mã, kết quả phải được so với **giá trị chuẩn** do bên thứ ba công bố, không phải so với chính code mình.

---

## Bước 1 — Unit test cho từng phần, so với vector chuẩn

**Làm gì:**
- Mỗi phần nhỏ được kiểm tra riêng: mã hoá rồi giải mã có ra lại đúng không, sửa một byte thì có bị phát hiện không, hai bên làm chìa chung có ra cùng một chìa không.
- Hàm dẫn xuất khoá và hàm HMAC được so với các bộ giá trị mẫu trong tài liệu chuẩn quốc tế. Code ra khác giá trị mẫu là sai, dù hai máy của mình vẫn "nói chuyện được với nhau".
- Những thứ phụ thuộc thời gian, như "5 lần sai trong 1 phút" hay "3 giây không gõ thì ngừng", dùng một chiếc đồng hồ giả để tua nhanh, không phải chờ thật.
- Phần tính toán được tách khỏi phần giao diện, để kiểm tra mà không cần mở cửa sổ.

**Về kỹ thuật:**
- JUnit 5.
- HKDF so với vector của RFC 5869. HMAC-SHA1 của tài khoản TURN tạm so với vector của RFC 2202.
- Thời gian được truyền vào: `WrongPasswordLimiter` và `RequestRateLimiter` nhận `Clock`; `TypingNotifier` và `TypingTracker` nhận `LongSupplier`.
- Logic giao diện được tách thành lớp thuần: `DeliveryStatuses`, `LobbyRules`, `LoginRules`, `SafetyCode`, `MarkdownParser`, `ReactionStore`.

**Trong code (ví dụ):**
- Mật mã: [HkdfAndSessionKeysTest.java](../crypto/src/test/java/com/datn/chatp2p/crypto/HkdfAndSessionKeysTest.java), [AesGcmCipherTest.java](../crypto/src/test/java/com/datn/chatp2p/crypto/AesGcmCipherTest.java), [KeyExchangeServiceTest.java](../crypto/src/test/java/com/datn/chatp2p/crypto/KeyExchangeServiceTest.java), [IdentitySignatureServiceTest.java](../crypto/src/test/java/com/datn/chatp2p/crypto/IdentitySignatureServiceTest.java), [RoomSecretTest.java](../crypto/src/test/java/com/datn/chatp2p/crypto/RoomSecretTest.java).
- Lưu khoá: [IdentityKeyStoreProtectionTest.java](../crypto/src/test/java/com/datn/chatp2p/crypto/IdentityKeyStoreProtectionTest.java), [IdentityBackupTest.java](../crypto/src/test/java/com/datn/chatp2p/crypto/IdentityBackupTest.java).
- Server: [TurnCredentialServiceTest.java](../signaling-server/src/test/java/com/datn/chatp2p/signaling/turn/TurnCredentialServiceTest.java), [RequestRateLimiterTest.java](../signaling-server/src/test/java/com/datn/chatp2p/signaling/limit/RequestRateLimiterTest.java).
- Client: [DeliveryStatusesTest.java](../client-javafx/src/test/java/com/datn/chatp2p/client/view/DeliveryStatusesTest.java), [TypingTest.java](../client-javafx/src/test/java/com/datn/chatp2p/client/view/TypingTest.java), [SafetyCodeTest.java](../client-javafx/src/test/java/com/datn/chatp2p/client/view/SafetyCodeTest.java), [MessageDeliveryTrackerTest.java](../client-javafx/src/test/java/com/datn/chatp2p/client/session/MessageDeliveryTrackerTest.java).

**Đáp ứng yêu cầu:**
- **R1:** chạy lại được, kết quả ổn định nhờ đồng hồ giả.
- **R2:** mật mã được so với vector RFC.

**→ Lỗ hổng:** từng phần đúng, nhưng **ghép lại chưa chắc đúng**.

**Phân tích vấn đề:**
- Unit test dùng kênh giả trong bộ nhớ. Ngoài thực tế, tin đi qua: JSON qua WebSocket thật → server Spring Boot thật → ice4j mở cổng UDP thật → chia mảnh → giải mã.
- Lỗi thường nằm ở **chỗ nối** giữa các phần. Ví dụ đã gặp thật:
  - Tomcat chỉ nhận bản tin WebSocket tối đa 8 KB, nên bản tin ICE của máy có nhiều card mạng ảo làm đứt kết nối.
  - ice4j cắt mọi gói UDP lớn hơn 1500 byte.
- Unit test với kênh giả không bao giờ thấy những lỗi này.

**Để giải quyết thì cần gì:**
- **R3:** có test chạy qua **các thành phần thật**: ICE thật, UDP thật, signaling server thật, nhiều client thật.

---

## Bước 2 — Test tích hợp với thành phần thật

**Làm gì:**
- Bật một server thật ngay trong lúc test, trên một cổng ngẫu nhiên. Cho hai, ba "người dùng" thật kết nối vào, mở đường riêng bằng ICE thật trên máy, trao chìa, gửi tin, rồi kiểm tra tin tới đúng người.
- Thử cả trường hợp đường thẳng không đi được để chắc là có chuyển sang đường vòng qua TURN.

**Về kỹ thuật:**
- `RoomSessionRealSignalingServerTest`: khởi động `signaling-server` bằng `SpringApplicationBuilder` trên cổng ngẫu nhiên, dùng cơ sở dữ liệu H2 trong bộ nhớ, rồi dùng `WebSocketSignalingClient` thật. Đây là phụ thuộc chỉ dùng cho test của `p2p-core`, không vào jar chính.
- `IceP2pConnectionEstablisherTest`: hai ice4j Agent thật trên localhost.
- `RoomSessionThreePeerMeshTest`: ba người, mesh đầy đủ, tin phát tới mọi người.
- `P2pChatSessionIntegrationTest`: nhiều `P2pChatSession` thật.
- `WebSocketSignalingClientCapacityTest`: 100 kết nối cùng lúc. Chính test này phát hiện lỗi hai luồng cùng ghi vào một WebSocket.

**Trong code:**
- [RoomSessionRealSignalingServerTest.java](../p2p-core/src/test/java/com/datn/chatp2p/p2p/RoomSessionRealSignalingServerTest.java), [IceP2pConnectionEstablisherTest.java](../p2p-core/src/test/java/com/datn/chatp2p/p2p/ice/IceP2pConnectionEstablisherTest.java), [IceP2pConnectionEstablisherTurnFallbackTest.java](../p2p-core/src/test/java/com/datn/chatp2p/p2p/ice/IceP2pConnectionEstablisherTurnFallbackTest.java), [RoomSessionThreePeerMeshTest.java](../p2p-core/src/test/java/com/datn/chatp2p/p2p/RoomSessionThreePeerMeshTest.java), [P2pChatSessionIntegrationTest.java](../client-javafx/src/test/java/com/datn/chatp2p/client/session/P2pChatSessionIntegrationTest.java), [WebSocketSignalingClientCapacityTest.java](../p2p-core/src/test/java/com/datn/chatp2p/p2p/signaling/WebSocketSignalingClientCapacityTest.java), [WebSocketSignalingClientReconnectTest.java](../p2p-core/src/test/java/com/datn/chatp2p/p2p/signaling/WebSocketSignalingClientReconnectTest.java).

**Đáp ứng yêu cầu:**
- **R3:** đi qua WebSocket, Spring Boot, ice4j và UDP thật.

**→ Lỗ hổng:** chạy trên localhost thì mạng **quá tốt**, và không ai **cố tình phá**.

**Phân tích vấn đề:**
- Localhost gần như không mất gói, không đảo thứ tự, không nhân đôi gói. Cơ chế gửi lại và chống trùng vẫn gần như không được chạy tới.
- Không có test nào đóng vai **kẻ tấn công**, như phát lại gói, giả `OFFER`, gửi dữ liệu hỏng hay dữ liệu cố tình chậm.
- Với một ứng dụng bảo mật, chứng minh nó **từ chối đúng** quan trọng không kém chứng minh nó **chấp nhận đúng**.

**Để giải quyết thì cần gì:**
- **R4:** có **kênh truyền giả lập xấu**: mất gói, nhân đôi gói, theo tỉ lệ đặt trước.
- **R5:** mỗi lỗ hổng bảo mật, khi sửa, phải có một test **tái hiện đúng cách tấn công**. Test này phải hỏng trước khi sửa và qua sau khi sửa.

---

## Bước 3 — Mạng giả lập xấu và test đóng vai kẻ tấn công

**Làm gì:**
- Đặt giữa hai máy một "người đưa thư cẩu thả" giả: làm rơi một phần thư, đưa trùng một số thư. Rồi kiểm tra tin và file vẫn tới đủ, đúng từng byte, không bị hiện trùng.
- Viết các bài test trong đó test **đóng vai Minh**:
  - chép một gói đã mã hoá rồi gửi lại;
  - gửi `OFFER` liên tục dưới mã của người khác;
  - dựng một kết nối mới đè lên kết nối cũ;
  - gửi địa chỉ ICE hỏng;
  - dán chuỗi Markdown độc;
  - tự nhận là tài khoản trong bản tin chuyển tiếp;
  - và giả làm client cũ để chắc server mới không làm nó hỏng.
- Có một bài đo "gửi dồn 1000 tin liền" để xem thực tế bao nhiêu tin tới nơi.

**Về kỹ thuật:**
- `FileTransferOverUnreliablePeerConnectionTest`: `PeerConnection` thật (ECDH, AES-GCM, chống replay) trên kênh giả lập mất gói và nhân đôi cả hai chiều, truyền 1 MB.
- `PeerConnectionReplayAttackTest`: phát lại đúng ciphertext đã gửi, gói cũ sau 2000 gói, gói giả.
- Các test bảo mật ở tầng phòng: `RoomSessionDuplicateOfferSecurityTest`, `RoomSessionEstablishedConnectionOverwriteSecurityTest`, `RoomSessionMalformedIceCandidateTest`, `RoomSessionLeaveReleasesEstablishedIcePortsTest`.
- Các test bảo mật ở server: `SignalingSecurityFixesTest` (không chuyển tiếp token, người đã rời phòng không chuyển tiếp được, trường `JOIN` quá dài), `SignalingServerContentOpacityTest` (server không đọc nội dung), `PeerAccountInfoTest` (khách không tự nhận là tài khoản, client cũ không nhận trường lạ).
- `MarkdownParserComplexityTest`: chuỗi `"*a "` lặp 200.000 lần phải xong dưới 5 giây.
- `PeerConnectionBurstThroughputTest`: lần đầu chạy chỉ **358/1000** tin tới nơi ngay trên localhost. Từ đó mới phát hiện bộ đệm nhận của hệ điều hành quá nhỏ (xem [Co-che-gui-tin-tin-cay.md](Co-che-gui-tin-tin-cay.md), Bước 0–1).

**Trong code:**
- [FileTransferOverUnreliablePeerConnectionTest.java](../p2p-core/src/test/java/com/datn/chatp2p/p2p/filetransfer/FileTransferOverUnreliablePeerConnectionTest.java), [PeerConnectionReplayAttackTest.java](../p2p-core/src/test/java/com/datn/chatp2p/p2p/PeerConnectionReplayAttackTest.java), [RoomSessionEstablishedConnectionOverwriteSecurityTest.java](../p2p-core/src/test/java/com/datn/chatp2p/p2p/RoomSessionEstablishedConnectionOverwriteSecurityTest.java), [RoomSessionDuplicateOfferSecurityTest.java](../p2p-core/src/test/java/com/datn/chatp2p/p2p/RoomSessionDuplicateOfferSecurityTest.java), [SignalingSecurityFixesTest.java](../signaling-server/src/test/java/com/datn/chatp2p/signaling/ws/SignalingSecurityFixesTest.java), [PeerAccountInfoTest.java](../signaling-server/src/test/java/com/datn/chatp2p/signaling/ws/PeerAccountInfoTest.java), [MarkdownParserComplexityTest.java](../client-javafx/src/test/java/com/datn/chatp2p/client/view/MarkdownParserComplexityTest.java), [PeerConnectionBurstThroughputTest.java](../p2p-core/src/test/java/com/datn/chatp2p/p2p/PeerConnectionBurstThroughputTest.java).

**Đáp ứng yêu cầu:**
- **R4:** có kênh mất gói, nhân đôi gói.
- **R5:** các lỗi bảo mật đã sửa trong đợt rà soát đều có test tái hiện.

**→ Lỗ hổng:** các test này chạy **trên máy phát triển**, nên vừa phụ thuộc vào máy đó, vừa có thể **chạm ra Internet**.

**Phân tích vấn đề:**
- Một số test ICE gửi yêu cầu STUN tới `stun.l.google.com`. Kết quả phụ thuộc mạng lúc đó, và máy test liên lạc với dịch vụ bên ngoài.
- Thư viện Maven có thể bị tải thêm trong lúc chạy, nên bản đã kiểm tra không còn đúng là bản sẽ chạy.
- Máy phát triển có nhiều card mạng ảo (VPN, Docker, WSL, Hyper-V). Cùng một bộ test chạy ở máy khác có thể ra kết quả khác.
- Muốn tin kết quả, phải chạy được trong một môi trường **sạch và cô lập**.

**Để giải quyết thì cần gì:**
- **R6:** chạy toàn bộ test trong một môi trường **không ra được Internet**, với **đúng bộ thư viện cố định**, mà vẫn có IP riêng để ICE hoạt động.

---

## Bước 4 — Chạy test trong Docker cô lập

**Làm gì:**
- Chép mã nguồn sang một thư mục riêng. Chạy test trong một container có mạng nội bộ riêng nhưng không có đường ra Internet.
- Kho thư viện được gắn vào ở chế độ chỉ đọc, và Maven chạy ở chế độ offline, không được tải thêm gì.

**Về kỹ thuật:**

```bash
docker network create --internal chatp2p-isolated
docker run --rm --network chatp2p-isolated \
  -v <bản sao mã nguồn>:/work -v <~/.m2/repository>:/m2:ro -w /work \
  maven:3.9.9-eclipse-temurin-17 \
  mvn -o -B -Dmaven.repo.local=/m2 -Djavafx.platform=win \
  -pl common,crypto,signaling-server,p2p-core,client-javafx test
```

- Dùng `--internal` chứ không dùng `--network none`. Khi không có mạng, container chỉ còn địa chỉ loopback, mà ice4j bỏ qua loopback nên test ICE không tạo được kết nối nào.
- `-Djavafx.platform=win`: kho Maven trên máy phát triển chỉ có jar JavaFX bản Windows.
- Test phải chạy trong reactor cả năm module. Module `p2p-core` cần các lớp của `signaling-server` cho test tích hợp, mà jar Spring Boot đã đóng gói lại thì không dùng làm thư viện được.

**Trong tài liệu:** mục "Chạy test trong Docker" trong [README.md](../README.md) và mục F.5 trong [Tai-lieu-ky-thuat.md](Tai-lieu-ky-thuat.md).

**Đáp ứng yêu cầu:**
- **R6:** không ra Internet, thư viện cố định và chỉ đọc, ICE vẫn có IP để chạy.

**→ Lỗ hổng:** test qua chỉ nói **đúng hay sai**, không nói **nhanh hay chậm**. Nhiều quyết định thiết kế lại là quyết định về **hiệu năng**.

**Phân tích vấn đề:**
- Khi truyền file, mỗi phần nên to bao nhiêu? 512 byte, 4 KB hay 8 KB? Cửa sổ 8 hay 64? Có cần gửi lại nhanh không?
- Không đo thì chỉ chọn theo cảm tính. Cảm tính đã sai: phiên bản đầu với phần 512 byte, cửa sổ 64, khi mất 1% gói chỉ còn 0,20 MB/s.

**Để giải quyết thì cần gì:**
- **R7:** có bài **đo tốc độ** trên đường truyền thật của ứng dụng, giả lập mất gói **theo từng datagram**, chạy nhiều lần và lấy trung vị.
- **R8:** bài đo **không chạy mặc định**, vì lâu và phụ thuộc máy, nhưng ai cũng chạy lại được bằng một lệnh.

---

## Bước 5 — Benchmark để chọn thông số

**Làm gì:**
- Một bài đo gửi file 4 MB qua đúng đường mà ứng dụng dùng: ICE thật, chia mảnh, mã hoá, giao thức truyền file.
- Mất gói được giả lập ở từng gói UDP nhỏ: một tin chia thành n mảnh thì mất cả tin với xác suất `1 − (1 − p)^n`, đúng như ngoài đời.
- Mỗi cách kết hợp chạy 3 lần, lấy kết quả giữa.

**Về kỹ thuật:** `FileTransferBenchmarkTest`. Mặc định bị bỏ qua bằng `assumeTrue`. Chạy bằng:

```
mvn -pl client-javafx test -Dtest=FileTransferBenchmarkTest -Dchatp2p.benchmark=true
```

Có thể chọn thông số qua `-Dchatp2p.benchmark.cases=4096/8`, `.losses=0,1,3`, `.repeats=3`.

**Kết quả đã dùng để chọn thông số** (xem [Co-che-truyen-file.md](Co-che-truyen-file.md), Bước 3):

| Mất gói | Trước: phần 512 B, cửa sổ 64 | Sau: phần 4 KB, cửa sổ 8, gửi lại nhanh |
|---|---|---|
| 0% | 6,4 MB/s | 17,9 MB/s |
| 1% | 0,20 MB/s | 18,1 MB/s |
| 3% | 0,12 MB/s | 18,0 MB/s |
| 5% | 0,09 MB/s | 5,5 MB/s |

Ngoài benchmark, độ trễ qua TURN đã được đo thực tế giữa các máy: 221 ms, 354 ms, 86 ms, và mất khoảng 1,5% gói (xem [Co-che-ket-noi-p2p.md](Co-che-ket-noi-p2p.md), Bước 3).

**Trong code:** [FileTransferBenchmarkTest.java](../client-javafx/src/test/java/com/datn/chatp2p/client/session/FileTransferBenchmarkTest.java).

**Đáp ứng yêu cầu:**
- **R7:** đo trên đường thật, mất gói theo datagram, lấy trung vị 3 lần.
- **R8:** mặc định bỏ qua, chạy lại bằng một lệnh.

**→ Lỗ hổng còn lại:** vẫn có những khẳng định trong tài liệu **chưa có test tự động nào chứng minh**.

**Phân tích vấn đề:**
- **Phòng 8 người:**
  - Tài liệu nói phòng tối đa 8 người. Test `RoomSessionEightPeerMeshScalabilityTest` đang bị `@Disabled`.
  - Khi chạy 8 "người" trong cùng một JVM, tức 56 ice4j Agent tranh nhau CPU trên một máy, test không hoàn tất trong 60 giây.
  - Lần chạy đầu của test này còn tìm ra một lỗi thật: mọi Agent cùng dò cổng từ 10000, nên từ Agent thứ khoảng 50 trở đi không lấy được cổng nào. Lỗi đã sửa bằng cách xoay vòng điểm bắt đầu dò cổng.
  - Nhưng việc 8 người trên 8 máy riêng chạy ổn thì **chưa được kiểm chứng tự động**. Test 3 người thì đang chạy và qua.
  - Mục F.5 của [Tai-lieu-ky-thuat.md](Tai-lieu-ky-thuat.md) ghi test này "bật bằng cờ". Thực tế nó bị `@Disabled`, phải sửa code mới bật được.
- **Giao diện:** không có test nào bấm nút thật trên cửa sổ JavaFX. Logic được tách ra lớp thuần để test, nhưng các lớp `RoomController`, `HomeController`, `PeerListCell` thì không có test, ví dụ nút "Đăng xuất" và nhãn "Tài khoản/Khách" vừa thêm.
- **Internet thật:** tốc độ truyền file mới đo trên localhost. Qua Internet và TURN, tính toán cho thấy tối đa khoảng 0,37 MB/s do cửa sổ cố định, nhưng con số này chưa được đo.
- **Không có CI:** test chạy tay trên máy phát triển hoặc trong Docker. Mỗi lần push không tự động chạy test. Bản deploy lên VPS được build với `-DskipTests`.
- **Tải server:** chưa có bài thử tải cho signaling server với hàng trăm phòng hay hàng nghìn kết nối. Các giới hạn ở [Co-che-chong-qua-tai-server.md](Co-che-chong-qua-tai-server.md) mới được kiểm bằng unit test.
- **DPAPI:** test DPAPI chỉ chạy trên Windows. Trong Docker Linux, test này không được chạy tới.

**Để giải quyết thì cần gì:**
- **R9:** chứng minh **8 người trên 8 máy riêng** chạy ổn, có giả lập độ trễ và mất gói của mạng thật.
- **R10:** có **test giao diện** cho các luồng chính.
- **R11:** test **tự chạy mỗi lần push**, trên cả Windows lẫn Linux, và deploy chỉ dùng bản đã qua test.
- **R12:** có số đo trên **Internet thật** và **thử tải** server.

---

## Bước 6 — *(đề xuất, chưa làm)* CI, mesh nhiều máy, test giao diện, thử tải

**Làm gì:**
- Mỗi lần đẩy mã lên GitHub, hệ thống tự chạy toàn bộ test trên một máy Windows và một máy Linux. Test hỏng thì không được deploy.
- Dựng 8 container, mỗi container là một người dùng với mạng riêng. Đặt độ trễ 50–200 ms và mất 1–3% gói giữa các container, rồi cho cả 8 người vào cùng một phòng.
- Có vài bài test tự bấm nút trên giao diện: đăng nhập, vào phòng, gửi tin, đăng xuất.
- Đo truyền file thật giữa hai máy ở hai mạng khác nhau, qua TURN. Thử tải server với vài nghìn kết nối giả.

**Về kỹ thuật:**
- GitHub Actions với ma trận `windows-latest` và `ubuntu-latest`. Chạy `mvn -pl common,crypto,signaling-server,p2p-core,client-javafx test`, rồi đính kèm jar đã build vào bản phát hành. VPS chỉ tải jar này về.
- Docker Compose với 8 dịch vụ client chạy chế độ không giao diện, cộng một signaling server và một coturn. Dùng `tc qdisc add dev eth0 root netem delay 100ms loss 2%` trên từng container.
- TestFX cho JavaFX, chạy với Monocle ở chế độ không màn hình trên CI.
- k6 hoặc Gatling cho WebSocket: mở N kết nối, `JOIN`, trao `OFFER` giả, và đo độ trễ chuyển tiếp cùng mức dùng bộ nhớ.

**Đáp ứng yêu cầu:**
- **R9** đến **R12**: mỗi đề xuất ứng với một yêu cầu ở trên.

---

## Bảng: cơ chế nào được kiểm chứng bằng gì

| Cơ chế | Kiểm chứng tự động hiện có | Chưa có |
|---|---|---|
| Kết nối P2P | ICE thật trên localhost, TURN dự phòng, mesh 3 người, server thật | Mesh 8 người, NAT thật |
| Mã hoá tin nhắn | Vector RFC, replay, sửa byte, khoá mỗi chiều | — |
| Đăng nhập | Giới hạn tốc độ, không chuyển tiếp token | Test REST đầu-cuối với MySQL thật |
| Xác thực danh tính | Chữ ký gắn ECDH (kịch bản MITM), sổ tay tin cậy, mã an toàn, đối chiếu khoá đăng ký | Hộp thoại so vân tay trên giao diện |
| Phòng có mật khẩu | Thẻ phòng, `ROOM_PROOF`, `WRONG_PASSWORD`, giới hạn thử sai | — |
| Gửi tin tin cậy | Gửi lại, chống trùng, trạng thái giao, chia mảnh, gửi dồn 1000 tin | — |
| Chat nhóm, riêng | Sửa/xoá chỉ tác giả, lịch sử nhóm | Giao diện tab riêng |
| Lịch sử | Gộp, giới hạn, không gán cho mình, lưu DPAPI | DPAPI trên Linux (không áp dụng) |
| Truyền file | Mạng xấu 1 MB, gửi lại nhanh, SHA-256, tên file, benchmark | Tốc độ qua Internet |
| Chống quá tải | Giới hạn tốc độ, kết nối, độ dài trường | Thử tải thật |
| Duy trì kết nối | Tự nối lại WebSocket, thay kết nối cùng `peerId`, nhả cổng khi rời | Mất đường riêng khi đổi mạng |
| Ghi log | — | Test bảo đảm không dòng log nào có bí mật |
| Hiển thị an toàn | Markdown độc 600 nghìn ký tự, link chỉ `http(s)` | Ký tự đảo chiều chữ, ảnh bom giải nén |
| Triển khai | Kiểm tra tay sau deploy (`/rooms` trả 401, `/auth/logout` trả 204) | CI, health check |

---

## Tóm tắt chuỗi thiết kế

| Bước | Làm gì | Vá được gì | Sinh ra vấn đề gì |
|---|---|---|---|
| 0 | Thử tay hai cửa sổ | — | Chỉ thử trường hợp suôn sẻ, không lặp lại được |
| 1 | Unit test, vector RFC, đồng hồ giả, tách logic khỏi giao diện | Từng phần đúng, chạy lại được | Ghép lại chưa chắc đúng |
| 2 | Test tích hợp: server thật, ICE thật, 3 người, 100 kết nối | Bắt được lỗi ở chỗ nối | Localhost quá tốt, không ai tấn công |
| 3 | Kênh mất gói, nhân đôi; test đóng vai kẻ tấn công cho mỗi lỗi bảo mật | Chứng minh từ chối đúng; phát hiện lần đầu chỉ 358/1000 tin tới nơi | Phụ thuộc máy, chạm Internet |
| 4 | Docker mạng `--internal`, Maven offline, kho chỉ đọc | Kết quả tin được, không ra ngoài | Đúng chưa chắc đủ nhanh |
| 5 | Benchmark mất gói theo datagram, trung vị 3 lần | Chọn phần 4 KB, cửa sổ 8, gửi lại nhanh bằng số đo | 8 người, giao diện, Internet, CI chưa kiểm chứng |
| 6 *(đề xuất)* | CI hai hệ điều hành, mesh 8 container có netem, TestFX, thử tải | Kiểm chứng điều tài liệu đang khẳng định | — |

```
B0 thử tay
 ──không lặp lại, chỉ suôn sẻ────► B1 unit test + vector RFC
 ──ghép lại chưa chắc đúng───────► B2 tích hợp thành phần thật
 ──mạng quá tốt, không tấn công──► B3 mạng giả lập xấu + test tấn công
 ──phụ thuộc máy, chạm Internet──► B4 Docker cô lập
 ──đúng chưa chắc nhanh──────────► B5 benchmark
 ──khẳng định chưa kiểm chứng────► B6 CI + mesh 8 máy + UI + tải (đề xuất)
```
