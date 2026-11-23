# Cơ chế duy trì kết nối và xử lý khi mất kết nối

Tài liệu này trả lời câu hỏi: **đang chat thì rớt mạng, đổi Wi-Fi, hay server khởi động lại, thì chuyện gì xảy ra, và ứng dụng tự hồi phục tới đâu?**

Phần lập kết nối lần đầu đã có trong [Co-che-ket-noi-p2p.md](Co-che-ket-noi-p2p.md). Tài liệu này bàn chuyện **giữ** kết nối và **dựng lại** kết nối.

Cách trình bày giống các tài liệu cơ chế khác: đi từ cách ngây thơ, tìm lỗ hổng, phân tích cần gì, rồi sinh ra bước tiếp theo. Ở mỗi chỗ phải chọn có so sánh các phương án.

Nhân vật:
- **An**, **Bình**: hai người trong phòng.
- **Bưu điện**: signaling server, nơi An và Bình đăng ký có mặt và trao nhau địa chỉ.
- **Đường riêng**: kết nối P2P đã mã hoá giữa An và Bình, chở tin nhắn thật.

Một điều cần nhớ xuyên suốt: mỗi người có **hai đường kết nối**. Đường tới bưu điện dùng để biết ai đang ở trong phòng. Đường riêng tới từng người dùng để chở tin. Hai đường này có thể đứt riêng rẽ.

---

## Bước 0 — Kết nối một lần, đứt là thôi

**Làm gì:** An đăng ký với bưu điện là "đang ở phòng X", rồi mở đường riêng tới Bình. Một lúc sau mạng nhà An chập chờn, đường tới bưu điện đứt. An không làm gì cả.

**Về kỹ thuật:** client mở WebSocket tới `/ws`, gửi `JOIN`. Nếu WebSocket bị đóng thì không có gì mở lại.

**→ Lỗ hổng:** chỉ một lần rớt mạng vài giây là An bị "bốc hơi" khỏi phòng.

**Phân tích vấn đề:**
- Bưu điện không còn thấy An, nên những người vào sau không biết An có mặt và không mở đường riêng tới An.
- Muốn quay lại, An phải tự thoát phòng rồi vào lại.
- Mạng di động và Wi-Fi công cộng rớt vài giây là chuyện thường. Server cũng phải khởi động lại mỗi lần deploy.

**Để giải quyết thì cần gì:**
- **R1:** đường tới bưu điện đứt **bất ngờ** thì phải **tự nối lại** và tự đăng ký lại đúng phòng cũ.
- **R2:** nối lại phải **không dồn dập**. Server vừa khởi động lại mà cả trăm máy cùng thử liên tục từng mili giây thì server lại sập.
- **R3:** người dùng **chủ động** rời phòng thì không được tự nối lại.
- **R4:** lần kết nối **đầu tiên** mà thất bại thì phải báo ngay, không âm thầm thử mãi. Rất có thể người dùng gõ sai địa chỉ server.

**Xét các phương án nối lại:**
- **Thử lại ngay, liên tục:** hồi phục nhanh nhất nếu chỉ chập chờn một chút. Nhưng khi server sập thì mỗi client gửi hàng nghìn yêu cầu, và server vừa lên đã bị dội ngập. Không đạt R2.
- **Thử lại theo chu kỳ cố định, ví dụ mỗi 5 giây:** đơn giản. Nhưng chập chờn ngắn thì phải chờ trọn 5 giây; server sập lâu thì vẫn bị gọi đều đặn mãi.
- **Thử lại với thời gian chờ tăng gấp đôi, có trần** (exponential backoff): lần đầu thử rất nhanh, càng thất bại càng chờ lâu. Đây là hướng được chọn.

---

## Bước 1 — Tự nối lại tới bưu điện, chờ tăng dần

**Làm gì:**
- Đường tới bưu điện đứt bất ngờ thì máy An chờ 1 giây rồi thử nối lại. Không được thì chờ 2 giây, rồi 4 giây, 8 giây…, tối đa 30 giây mỗi lần, và cứ thế thử tiếp.
- Nối lại được thì máy An tự đăng ký lại "An đang ở phòng X" với **đúng mã người cũ**, và thời gian chờ quay về 1 giây.
- An bấm rời phòng thì máy đánh dấu "chủ động rời" trước khi đóng đường, nên không tự nối lại.
- Lần kết nối đầu tiên mà thất bại thì báo lỗi ngay cho người dùng.

**Về kỹ thuật:** `WebSocketSignalingClient` có ba trạng thái: `CONNECTED`, `RECONNECTING`, `DISCONNECTED`.
- `onClose` và `onError` gọi `handleUnexpectedDisconnect`. Nếu không phải do chính mình gọi `disconnect()`, nó lên lịch thử lại.
- Mỗi lần nối được, `establishConnection` gửi lại `JOIN` cùng `roomId`, `peerId`, tên và token.

**Trong code:**
- Mô tả và hằng số 1 giây / 30 giây: [WebSocketSignalingClient.java:33-49](../p2p-core/src/main/java/com/datn/chatp2p/p2p/signaling/WebSocketSignalingClient.java#L33-L49).
- Lần đầu thất bại thì ném lỗi ngay: [WebSocketSignalingClient.java:85-98](../p2p-core/src/main/java/com/datn/chatp2p/p2p/signaling/WebSocketSignalingClient.java#L85-L98).
- Rời phòng chủ động: đánh dấu, huỷ lịch thử lại, gửi `LEAVE`: [WebSocketSignalingClient.java:159-178](../p2p-core/src/main/java/com/datn/chatp2p/p2p/signaling/WebSocketSignalingClient.java#L159-L178).
- Mở kết nối và gửi lại `JOIN`, đặt lại thời gian chờ: [WebSocketSignalingClient.java:181-205](../p2p-core/src/main/java/com/datn/chatp2p/p2p/signaling/WebSocketSignalingClient.java#L181-L205).
- Thời gian chờ gấp đôi, có trần: [WebSocketSignalingClient.java:208-237](../p2p-core/src/main/java/com/datn/chatp2p/p2p/signaling/WebSocketSignalingClient.java#L208-L237).
- Phát hiện đường bị đóng: [WebSocketSignalingClient.java:308-319](../p2p-core/src/main/java/com/datn/chatp2p/p2p/signaling/WebSocketSignalingClient.java#L308-L319).

**Đáp ứng yêu cầu:**
- **R1:** tự nối lại và tự `JOIN` lại đúng phòng.
- **R2:** chờ 1, 2, 4… tối đa 30 giây.
- **R3:** chủ động rời thì không nối lại.
- **R4:** lần đầu thất bại thì báo ngay.

**→ Lỗ hổng:** trong lúc An mất tích, **những người khác vẫn tưởng An còn đó**.

**Phân tích vấn đề:**
- Bình vẫn thấy An trong danh sách, vẫn gửi tin cho An. Tin không tới, gửi lại 6 lần rồi báo "Chưa tới nơi".
- Các phần việc dở dang với An vẫn treo: file đang truyền, dòng "An đang gõ…", cảm xúc của An trên các tin.
- Bình cần một tín hiệu rõ ràng: "An đã rời đi". Chỉ bưu điện biết được điều đó, vì chỉ bưu điện thấy đường của An bị đóng.

**Để giải quyết thì cần gì:**
- **R5:** khi đường của An tới bưu điện bị đóng, bưu điện phải **báo cho mọi người** trong phòng.
- **R6:** nhận được tin báo, mỗi máy phải **dọn sạch mọi thứ** liên quan tới An: đóng đường riêng, giải phóng cổng mạng, huỷ tin đang chờ ký nhận, huỷ file đang truyền, xoá trạng thái tin cậy.
- **R7:** tin An chưa kịp ký nhận thì phải hiện **"Chưa tới nơi"**, không treo "Đang gửi" mãi.

---

## Bước 2 — Bưu điện báo người rời đi, mọi máy dọn dẹp

**Làm gì:**
- Bưu điện thấy đường của An đóng lại, dù An chủ động rời hay mạng đứt, thì xoá An khỏi danh sách phòng và báo cho từng người còn lại: "An đã rời phòng".
- Bình nhận tin báo thì đóng đường riêng tới An và giải phóng cổng mạng đã dùng cho An. Bình cũng:
  - thôi gửi lại các thư đang chờ An ký nhận;
  - huỷ file đang truyền với An;
  - xoá dòng "An đang gõ…" và cảm xúc của An;
  - quên những gì đã xác minh về An trong buổi đó.
- Thư Bình gửi mà An chưa ký nhận thì chuyển sang "Chưa tới nơi".

**Về kỹ thuật:**
- `afterConnectionClosed` gọi `removeAndNotify`: xoá ràng buộc phòng của session, xoá peer khỏi `RoomRegistry`, gửi `PEER_LEFT` cho những người còn lại.
- `RoomSession.handlePeerLeftNotice` đóng `PeerConnection` và `dispose()` cả establisher ICE. Chỉ đóng `PeerConnection` thì chưa nhả hẳn cổng UDP.
- `P2pChatSession.notifyPeerLeft` dọn trạng thái của tầng ứng dụng.

**Trong code:**
- Server gửi `PEER_LEFT` khi kết nối đóng: [SignalingWebSocketHandler.java:293-324](../signaling-server/src/main/java/com/datn/chatp2p/signaling/ws/SignalingWebSocketHandler.java#L293-L324).
- Client đóng kết nối, nhả cổng: [RoomSession.java:357-380](../p2p-core/src/main/java/com/datn/chatp2p/p2p/RoomSession.java#L357-L380).
- Dọn tin chờ ACK, file, danh sách tin cậy, lịch sử đã gửi: [P2pChatSession.java:872-890](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L872-L890).
- Huỷ file đang truyền: [FileTransferManager.java:667](../client-javafx/src/main/java/com/datn/chatp2p/client/session/FileTransferManager.java#L667).
- Tin chưa ký nhận chuyển sang "Chưa tới nơi": [DeliveryStatuses.java:108-117](../client-javafx/src/main/java/com/datn/chatp2p/client/view/DeliveryStatuses.java#L108-L117).
- Giao diện xoá người đó, xoá "đang gõ", cảm xúc, thông báo "đã rời phòng": [RoomController.java:404-418](../client-javafx/src/main/java/com/datn/chatp2p/client/view/RoomController.java#L404-L418).

**Đáp ứng yêu cầu:**
- **R5:** server gửi `PEER_LEFT`.
- **R6:** đóng kết nối, nhả cổng, dọn sạch trạng thái.
- **R7:** "Chưa tới nơi" thay vì treo "Đang gửi".

**→ Lỗ hổng:** khi An nối lại, An quay lại với **đúng mã người cũ**. Đường riêng mới mang cùng mã đó có được thừa hưởng sự tin cậy cũ không?

**Phân tích vấn đề:**
- Sau khi nối lại, An và Bình mở một đường riêng mới: làm chìa chung mới, trao địa chỉ mới.
- Nếu Bình chỉ nhìn mã người và thấy "mã này đã qua kiểm tra mật khẩu phòng, đã xác thực danh tính rồi", Bình sẽ bỏ qua bước kiểm tra cho đường mới.
- Nguy hiểm ở chỗ: đường mới cũng có thể do **kẻ khác** dựng lên dưới mã của An, chẳng hạn một bưu điện bị chiếm gửi `OFFER` giả. Bình sẽ mã hoá tin bằng một chìa chung chưa ai chứng minh, trong khi giao diện vẫn hiện "đã xác thực".
- Còn một trường hợp tương tự: kết nối mới tới **trước khi** Bình nhận được tin báo An rời đi.

**Để giải quyết thì cần gì:**
- **R8:** đường riêng mới, dù cùng mã người, phải **chứng minh lại từ đầu**: chìa chung mới, bằng chứng mật khẩu phòng mới, chữ ký danh tính mới.
- **R9:** đường cũ phải được **đóng hẳn**, để không có hai đường cùng mã chạy song song và tin không bị chuyển âm thầm sang đường lạ.
- **R10:** những tin nhóm An bỏ lỡ trong lúc vắng mặt phải được **bù lại**.

---

## Bước 3 — Đường mới phải chứng minh lại từ đầu

**Làm gì:**
- Khi một đường riêng mới mang mã của An được dựng xong, Bình đóng hẳn đường cũ và nhả cổng mạng của nó.
- Bình coi như "An cũ đã rời đi", xoá mọi sự tin cậy dành cho An, rồi bắt đường mới đi lại đủ các bước: làm chìa chung mới, đưa bằng chứng biết mật khẩu phòng, ký danh tính.
- Xong các bước đó, Bình và An gửi lại sổ lịch sử nhóm cho nhau. Tin nào đã có thì bỏ qua, nên An nhận được đúng những tin đã bỏ lỡ.

**Về kỹ thuật:**
- `RoomSession.onIceConnected`: `peers.put` trả về kết nối cũ thì `close()` nó và `dispose()` establisher cũ, rồi gọi `onPeerLeftHandler` cho `peerId` đó, để `P2pChatSession` xoá `admittedPeers`, `verifiedRemoteFingerprints` và `historySentTo`.
- Sau đó gửi khoá ECDH mới. Các bước `ROOM_PROOF`, `PEER_IDENTITY` và `MESSAGE_TRANSCRIPT` diễn ra lại như lần đầu.

**Trong code:**
- Thay kết nối cũ và báo "đã rời" để xoá trạng thái tin cậy: [RoomSession.java:534-555](../p2p-core/src/main/java/com/datn/chatp2p/p2p/RoomSession.java#L534-L555). Đây là lỗi bảo mật đã sửa trong đợt rà soát; test là `RoomSessionEstablishedConnectionOverwriteSecurityTest`.
- Gửi lịch sử lại sau khi xác thực, chống trùng theo mã tin: xem [Co-che-lich-su-tin-nhan.md](Co-che-lich-su-tin-nhan.md), Bước 1.

**Đáp ứng yêu cầu:**
- **R8:** đường mới chứng minh lại toàn bộ.
- **R9:** đường cũ bị đóng hẳn, cổng được nhả.
- **R10:** tin nhóm bỏ lỡ được bù qua lịch sử. Riêng tin nhắn riêng thì không được bù, vì tin riêng không vào lịch sử nhóm.

**→ Lỗ hổng còn lại:** cả chuỗi trên chỉ chạy khi **có người nhận ra** đường đã đứt. Hiện nay việc nhận ra vừa chậm vừa không đầy đủ.

**Phân tích vấn đề:**
- **Không có tín hiệu "còn sống" trên đường riêng:**
  - An và Bình không gửi gì cho nhau khi im lặng. Nếu chỉ đường riêng đứt, chẳng hạn An đổi từ Wi-Fi sang 4G, địa chỉ cũ chết, thì không ai biết.
  - Bình chỉ phát hiện khi gửi một tin rồi sau 6 lần gửi lại báo "Chưa tới nơi". Nhưng giao diện vẫn hiện An đang "Trực tiếp", vì đường riêng chưa bị đóng.
  - Không có cơ chế mở lại đường riêng (ICE restart) khi đường tới bưu điện vẫn sống.
  - Chính code cũng ghi chú hạn chế này ([P2pDataChannel.java:28-31](../p2p-core/src/main/java/com/datn/chatp2p/p2p/channel/P2pDataChannel.java#L28-L31)).
- **Không có tín hiệu "còn sống" trên đường tới bưu điện:**
  - Cả client lẫn server đều không gửi WebSocket ping. Nếu mạng đứt mà không ai gửi gói đóng kết nối, hai bên chỉ phát hiện khi TCP tự hết hạn, có thể mất vài phút.
  - Trong lúc đó, bưu điện vẫn giữ An trong phòng.
  - Nếu An nối lại trước khi bưu điện nhận ra kết nối cũ đã chết, `JOIN` với cùng mã người sẽ bị từ chối với lý do `DUPLICATE_PEER` ([RoomRegistry.java:91](../signaling-server/src/main/java/com/datn/chatp2p/signaling/room/RoomRegistry.java#L91)), và An bị đưa về sảnh ([RoomController.java:479-489](../client-javafx/src/main/java/com/datn/chatp2p/client/view/RoomController.java#L479-L489)).
- **Người dùng không thấy đang nối lại:**
  - `WebSocketSignalingClient` có sẵn `onConnectionStateChanged` để báo trạng thái `RECONNECTING` ([WebSocketSignalingClient.java:104](../p2p-core/src/main/java/com/datn/chatp2p/p2p/signaling/WebSocketSignalingClient.java#L104)), nhưng **không chỗ nào trong client đăng ký nghe**.
  - Lúc đang nối lại, màn hình không hiện gì khác thường.
- **Mọi máy nối lại cùng nhịp:** server khởi động lại thì tất cả client cùng thử ở giây thứ 1, 3, 7… Thời gian chờ chưa có phần ngẫu nhiên (jitter) để giãn các lần thử ra.

**Để giải quyết thì cần gì:**
- **R11:** phát hiện đường riêng chết **trong vài giây**, kể cả khi không ai nhắn tin.
- **R12:** đường riêng chết mà bưu điện vẫn sống thì **tự dựng lại** đường riêng.
- **R13:** phát hiện đường tới bưu điện chết sớm, và bưu điện phải **nhận ra An quay lại** thay vì từ chối vì trùng mã.
- **R14:** người dùng phải **thấy** trạng thái "Mất kết nối, đang thử lại…".
- **R15:** các máy nối lại **không cùng một nhịp**.

---

## Bước 4 — *(đề xuất, chưa làm)* Tín hiệu còn sống, tự dựng lại đường riêng, hiện trạng thái

**Làm gì:**
- Khi im lặng, An và Bình cứ khoảng 5 giây lại gửi nhau một gói nhỏ "còn đây". Ba lần liền không nghe thấy gì thì coi đường riêng đã chết: hiện "Mất kết nối với Bình", rồi nhờ bưu điện dựng lại đường riêng mới, lại chứng minh từ đầu như Bước 3.
- Đường tới bưu điện cũng gửi tín hiệu "còn đây" định kỳ, để cả hai phía sớm biết đường đã đứt.
- Bưu điện nhận đăng ký lại từ đúng người cũ, cùng mã và cùng vé, thì thay chỗ cũ bằng chỗ mới thay vì từ chối.
- Trên đầu khung chat hiện dải "Mất kết nối tới server, đang thử lại…" khi đang nối lại.
- Thời gian chờ nối lại cộng thêm một khoảng ngẫu nhiên nhỏ.

**Về kỹ thuật:**
- Thêm `EnvelopeType.PING` và `PONG`, đi trong kênh AES-GCM như mọi bản tin khác, có `peerId` và số thứ tự. Gửi mỗi 5 giây khi không có lưu lượng; 15 giây không nhận được gì thì đánh dấu mất.
- Mất thì đóng `PeerConnection` và tạo lại kết nối ICE qua signaling, cũng chính là luồng `connectAsOfferer` đang có.
- WebSocket ping/pong: dùng `WebSocket.sendPing` ở client, và cấu hình thời gian chờ phiên ở server.
- `RoomRegistry.tryJoin`: nếu `peerId` đã có trong phòng và session mới mang cùng token, hoặc cùng khoá danh tính, thì thay session cũ và đóng nó, thay vì trả `DUPLICATE_PEER`.
- `P2pChatSession` đăng ký `signalingClient.onConnectionStateChanged` và báo lên `ChatSessionListener` để `RoomController` hiện dải trạng thái.
- Thời gian chờ = `min(30 s, 2^n giây) × ngẫu nhiên(0,5…1,0)`.

**Đáp ứng yêu cầu:**
- **R11:** phát hiện trong khoảng 15 giây.
- **R12:** tự dựng lại đường riêng.
- **R13:** ping WebSocket cộng với việc thay session cũ.
- **R14:** có dải trạng thái.
- **R15:** có jitter.

**Cái giá phải trả:** mỗi cặp người tốn thêm vài chục byte mỗi 5 giây. Với phòng tối đa 8 người, mỗi máy giữ 7 đường riêng, tức khoảng 1,4 gói mỗi giây, không đáng kể. Phía TURN cũng tính băng thông cho các gói này khi đi qua relay.

---

## Bảng: chuyện gì xảy ra trong từng tình huống (hiện tại)

| Tình huống | Ai phát hiện, bằng cách nào | Hồi phục |
|---|---|---|
| Người dùng bấm rời phòng | Client gửi `LEAVE` và đóng WebSocket; server gửi `PEER_LEFT` ngay | Những người khác dọn dẹp ngay |
| Đóng cửa sổ hay tắt ứng dụng bình thường | Như trên | Như trên |
| Server khởi động lại | Mọi client thấy WebSocket đóng | Tự nối lại sau 1, 2, 4… giây, `JOIN` lại; người vào sau gửi `OFFER`, các đường riêng được dựng lại và chứng minh lại; lịch sử nhóm được bù |
| Mạng chập chờn vài giây, kết nối TCP vẫn còn | Không ai, vì không cần | Không mất gì |
| Mạng đứt hẳn, không có gói đóng kết nối | Chỉ khi TCP tự hết hạn, có thể vài phút | Nếu nối lại trước khi server nhận ra thì có thể bị `DUPLICATE_PEER` và về sảnh |
| Chỉ đường riêng chết (đổi Wi-Fi, NAT xoá ánh xạ) | Chỉ khi gửi tin: 6 lần gửi lại rồi "Chưa tới nơi" | Không tự dựng lại; đường riêng chỉ được dựng lại nếu đường tới server cũng đứt rồi nối lại |
| Một người tắt máy đột ngột | Server, khi TCP hết hạn | Khi đó mới gửi `PEER_LEFT` |

---

## Tóm tắt chuỗi thiết kế

| Bước | Làm gì | Vá được gì | Sinh ra vấn đề gì |
|---|---|---|---|
| 0 | Kết nối một lần | — | Rớt mạng là biến mất khỏi phòng |
| 1 | Tự nối lại WebSocket, chờ 1 tới 30 giây, `JOIN` lại cùng `peerId` | Tự quay lại phòng, không dội server | Người khác vẫn tưởng mình còn đó |
| 2 | Server gửi `PEER_LEFT` khi kết nối đóng; client dọn kết nối, cổng, tin chờ, file, "Chưa tới nơi" | Không còn trạng thái treo | Đường mới cùng `peerId` có thể thừa hưởng tin cậy cũ |
| 3 | Đường thay thế đóng đường cũ, xoá tin cậy, chứng minh lại; lịch sử bù tin nhóm | Không ai lợi dụng việc nối lại để chen vào | Phát hiện mất kết nối chậm hoặc không có; người dùng không thấy gì |
| 4 *(đề xuất)* | Ping trên đường riêng và WebSocket, tự dựng lại đường riêng, thay session cũ, dải trạng thái, jitter | Phát hiện trong khoảng 15 giây, tự hồi phục | — |

```
B0 kết nối một lần
 ──rớt mạng là biến mất──────────► B1 tự nối lại, chờ tăng dần
 ──người khác vẫn tưởng còn──────► B2 PEER_LEFT + dọn dẹp
 ──đường mới thừa hưởng tin cậy──► B3 chứng minh lại từ đầu
 ──phát hiện chậm, không thấy────► B4 ping + tự dựng lại + trạng thái (đề xuất)
```
