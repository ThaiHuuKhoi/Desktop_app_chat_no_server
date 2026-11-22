# Cơ chế gửi tin tin cậy trên UDP

Tài liệu này trả lời câu hỏi: **đường truyền P2P là UDP, gửi đi không có gì bảo đảm tới nơi, vậy làm sao tin nhắn vẫn tới đủ, không trùng, và người gửi biết được tin đã tới hay chưa?**

Cách trình bày giống các tài liệu cơ chế khác: đi từ cách ngây thơ, tìm lỗ hổng, phân tích cần gì, rồi sinh ra bước tiếp theo. Ở mỗi chỗ phải chọn có so sánh các phương án.

Nhân vật:
- **An** gửi thư.
- **Bình** nhận thư.
- **Người đưa thư** chạy giữa hai nhà. Trong code là đường UDP mà ICE đã chọn. Người đưa thư này không ký nhận, không hứa gì: thư có thể rơi dọc đường, đến muộn, hoặc đến lộn thứ tự.

---

## Bước 0 — Gửi thẳng, hy vọng tới nơi

**Làm gì:** An viết thư, đưa cho người đưa thư rồi quên luôn. Thư có tới hay không, An không biết.

**Về kỹ thuật:** mỗi tin nhắn được đóng thành một Envelope, mã hoá AES-256-GCM rồi gửi thành một gói UDP qua `DatagramSocket` mà ICE đã chọn.

**Trong code:**
- Mã hoá rồi gửi: [PeerConnection.java:229-231](../p2p-core/src/main/java/com/datn/chatp2p/p2p/PeerConnection.java#L229-L231).
- Đẩy gói UDP ra socket: [P2pDataChannel.java:97-115](../p2p-core/src/main/java/com/datn/chatp2p/p2p/channel/P2pDataChannel.java#L97-L115).

**Đáp ứng yêu cầu:** nhanh, đơn giản. Thông thường thư tới được.

**→ Lỗ hổng:** thư rơi là mất hẳn, và không ai biết là đã mất.

**Phân tích vấn đề:** UDP không bảo đảm gì cả.
- **Gói bị mất trên mạng:** gặp nhiều ở Wi-Fi yếu và mạng di động. Đo thực tế qua TURN thấy mất khoảng 1,5% gói (xem [Co-che-ket-noi-p2p.md](Co-che-ket-noi-p2p.md)).
- **Gói bị vứt ngay trên máy nhận:** đáng ngạc nhiên hơn. Bài kiểm thử gửi liên tiếp 1000 Envelope ngay trên localhost, lần đầu chỉ **358/1000** gói tới nơi. Nguyên nhân là bộ đệm nhận mặc định của hệ điều hành chỉ vài chục KB. Vòng nhận còn đang giải mã gói trước thì bộ đệm đã đầy, và hệ điều hành lặng lẽ vứt các gói đến sau ([P2pDataChannel.java:66-85](../p2p-core/src/main/java/com/datn/chatp2p/p2p/channel/P2pDataChannel.java#L66-L85)).
- **Hậu quả:** An tưởng Bình đã đọc, Bình không hề biết có thư. Đây là kiểu lỗi tệ nhất cho một ứng dụng chat.

**Để giải quyết thì cần gì:**
- **R1:** thư bị mất thì phải được **gửi lại** cho tới khi tới nơi.
- **R2:** muốn biết mà gửi lại thì người gửi phải **biết thư đã tới hay chưa**.

**Xét các phương án:**
- **Dùng TCP thay cho UDP:** TCP tự gửi lại và giữ thứ tự. Nhưng việc mở lối xuyên qua NAT của ICE chủ yếu hoạt động với UDP. Mở lối bằng TCP giữa hai máy sau NAT kém ổn định hơn nhiều. Phần TURN của dự án cũng mới chạy trên UDP.
- **Dùng một thư viện giao thức tin cậy có sẵn trên UDP**, như SCTP của WebRTC hay QUIC: chuẩn và đầy đủ. Nhưng trong Java không có bản nào gắn sẵn với ice4j. Ghép vào thì nặng, và khó giải thích từng phần khi bảo vệ.
- **Tự làm xác nhận và gửi lại ở tầng ứng dụng, chỉ cho những bản tin cần:** nhẹ, kiểm soát được, và chỉ áp dụng cho loại bản tin thật sự cần tới nơi. Đây là hướng được chọn.

Trước khi làm việc đó, có một chỗ rẻ cần vá trước, đó là bộ đệm nhận.

---

## Bước 1 — Nới rộng hộp thư nhận

**Làm gì:** hộp thư trước cửa nhà Bình quá nhỏ: thư đến dồn dập là tràn và rơi ra ngoài. Bình thay một hộp thư to hơn hẳn.

**Về kỹ thuật:** tăng bộ đệm nhận và gửi của socket lên 1 MB. Nếu hệ điều hành từ chối thì vẫn chạy tiếp với kích thước mặc định.

**Trong code:** [P2pDataChannel.java:37](../p2p-core/src/main/java/com/datn/chatp2p/p2p/channel/P2pDataChannel.java#L37), [86-95](../p2p-core/src/main/java/com/datn/chatp2p/p2p/channel/P2pDataChannel.java#L86-L95).

**Đáp ứng yêu cầu:** hết cảnh mất gói hàng loạt ngay trên máy nhận khi tin đến dồn dập.

**→ Lỗ hổng:** đây chỉ là giảm mất mát, không phải hết mất mát. Gói rơi trên mạng thì hộp thư to mấy cũng không cứu được. R1 và R2 vẫn chưa đạt.

---

## Bước 2 — Ký nhận và gửi lại

**Làm gì:**
- Mỗi lá thư có một **mã số riêng**.
- Bình nhận được thư thì gửi lại một tờ giấy nhỏ: "đã nhận thư mã số này".
- An giữ một danh sách thư đang chờ ký nhận. Cứ nửa giây chưa thấy giấy ký nhận thì An gửi lại thư đó, tối đa 6 lần. Có giấy ký nhận thì gạch khỏi danh sách.
- An ghi thư vào danh sách **trước** khi đưa cho người đưa thư. Nếu Bình ký nhận quá nhanh thì tờ giấy ký nhận vẫn khớp được với danh sách.

**Về kỹ thuật:**
- Mỗi tin có `messageId` là một UUID.
- Bên nhận trả về `MESSAGE_ACK(messageId)`.
- Bên gửi theo dõi các tin đang chờ ACK theo cặp `(peerId, messageId)`. Mỗi 500 ms gửi lại một lần, tối đa 6 lần.
- Mỗi lần gửi lại, tin được **mã hoá lại với số thứ tự và nonce mới**, nên không bị cửa sổ chống phát lại ở bên nhận chặn nhầm. Cửa sổ này chặn đúng những gói giống hệt nhau về mặt byte (xem [Co-che-ma-hoa-tin-nhan.md](Co-che-ma-hoa-tin-nhan.md)).

**Trong code:**
- Số lần và khoảng cách gửi lại: [P2pChatSession.java:68-69](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L68-L69).
- Bộ theo dõi tin chờ ACK: [MessageDeliveryTracker.java:53-93](../client-javafx/src/main/java/com/datn/chatp2p/client/session/MessageDeliveryTracker.java#L53-L93).
- Ghi vào danh sách trước rồi mới gửi: [P2pChatSession.java:326-333](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L326-L333).
- Nhận ACK: [P2pChatSession.java:513-526](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L513-L526).
- Cơ chế này dùng chung cho tin nhắn, sửa/xoá tin và các gói lịch sử. Tất cả đều đi qua `sendReliably`.

**Đáp ứng yêu cầu:**
- **R1:** thư mất thì được gửi lại.
- **R2:** giấy ký nhận cho An biết thư đã tới.

**→ Lỗ hổng:** chính tờ giấy ký nhận cũng có thể rơi dọc đường.

**Phân tích vấn đề:**
1. Bình nhận thư và gửi giấy ký nhận, nhưng giấy rơi mất.
2. An không thấy giấy nên gửi lại thư.
3. Bình nhận thư lần hai, và màn hình hiện **hai tin giống hệt nhau**.

Vấn đề cũng có thể xảy ra mà không có gói nào bị mất: giấy ký nhận chỉ cần đến chậm hơn nửa giây là An đã gửi lại rồi.

**Để giải quyết thì cần gì:**
- **R3:** bên nhận phải **nhận ra thư đã có** và không hiện lại.
- **R4:** gặp thư trùng thì bên nhận vẫn phải **ký nhận lại**. Nếu không, An sẽ tiếp tục gửi lại cho tới hết lượt, và cuối cùng báo lỗi dù thư đã tới.
- **R5:** sổ ghi mã số thư đã nhận không được phình mãi theo thời gian.

---

## Bước 3 — Loại thư trùng theo mã số

**Làm gì:**
- Bình có một cuốn sổ ghi mã số của 512 lá thư gần nhất nhận từ mỗi người.
- Thư đến mà mã số đã có trong sổ thì Bình **vẫn gửi giấy ký nhận**, nhưng không đưa lên màn hình nữa.
- Sổ đầy thì xoá dòng cũ nhất.

**Về kỹ thuật:**
- Bên nhận **luôn** gửi ACK trước, kể cả với bản trùng.
- Sau đó mới kiểm tra `markReceived(peerId, messageId)`: mã số đã có thì bỏ qua.
- Sổ là một `LinkedHashMap` giữ 512 mã số gần nhất cho mỗi người. Người đó rời phòng thì xoá cả sổ.

**Trong code:**
- ACK trước, kiểm tra trùng sau: [P2pChatSession.java:477-489](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L477-L489).
- Sổ 512 mã số: [MessageDeliveryTracker.java:28](../client-javafx/src/main/java/com/datn/chatp2p/client/session/MessageDeliveryTracker.java#L28), [96-105](../client-javafx/src/main/java/com/datn/chatp2p/client/session/MessageDeliveryTracker.java#L96-L105).
- Người rời phòng thì huỷ các tin đang chờ và xoá sổ: [MessageDeliveryTracker.java:108-117](../client-javafx/src/main/java/com/datn/chatp2p/client/session/MessageDeliveryTracker.java#L108-L117).

**Đáp ứng yêu cầu:**
- **R3:** không hiện trùng.
- **R4:** luôn ký nhận lại.
- **R5:** sổ có giới hạn.

**→ Lỗ hổng:** mọi thứ đã chạy đúng bên dưới, nhưng **người dùng không thấy gì cả**.

**Phân tích vấn đề:**
- An không biết tin đang trên đường, đã tới, hay đã hết 6 lần gửi lại mà vẫn không tới.
- Trong nhóm nhiều người, có thể Bình đã nhận còn Chi thì chưa.
- Nếu tin cuối cùng không tới mà không ai báo, An vẫn nghĩ mọi người đã biết.

**Để giải quyết thì cần gì:**
- **R6:** dưới mỗi tin mình gửi phải hiện **trạng thái giao**, tính riêng cho từng người nhận.
- **R7:** hết lượt gửi lại thì phải **báo rõ** là tin chưa tới.

---

## Bước 4 — Hiện trạng thái giao dưới mỗi tin

**Làm gì:** dưới mỗi lá thư An gửi có một dòng nhỏ:
- "Đang gửi".
- "Đã nhận 2/3" khi mới có hai trong ba người ký nhận.
- "Tất cả đã nhận".
- "Chưa tới nơi" khi hết lượt gửi lại mà vẫn không ai ký nhận. Lúc đó màn hình cũng báo kết nối với người kia có vấn đề.

**Về kỹ thuật:**
- Mỗi tin ghi nhận ba con số: số người nhận, số đã ACK, số đã hết lượt.
- Từ ba con số đó suy ra trạng thái: `SENDING`, `PARTIAL`, `DELIVERED` hoặc `FAILED`.
- Tin nhóm được gửi riêng cho từng người qua từng kết nối của cặp, nên mỗi người có ACK riêng.

**Trong code:**
- Báo danh sách người nhận ngay khi gửi: [P2pChatSession.java:209](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L209), [237-242](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L237-L242).
- Hết lượt gửi lại thì báo `onMessageDeliveryFailed` và cảnh báo kết nối: [P2pChatSession.java:95-105](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L95-L105).
- Tính trạng thái và chữ hiển thị: [DeliveryStatuses.java:16-52](../client-javafx/src/main/java/com/datn/chatp2p/client/view/DeliveryStatuses.java#L16-L52).
- Một người gặp lỗi không chặn việc gửi cho những người còn lại: [P2pChatSession.java:210-218](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L210-L218).

**Đáp ứng yêu cầu:**
- **R6:** trạng thái tính riêng cho từng người nhận.
- **R7:** có trạng thái "Chưa tới nơi" kèm cảnh báo kết nối.

**→ Lỗ hổng:** "Đã nhận" chỉ có nghĩa là **máy** của Bình đã nhận, chưa chắc **Bình** đã đọc.

**Phân tích vấn đề:**
- Bình có thể đang thu nhỏ cửa sổ, hoặc đang mở cuộc trò chuyện với người khác.
- An muốn biết Bình đã thật sự nhìn thấy tin hay chưa.
- Nhưng thông tin "đã xem" chỉ là thông tin thêm. Mất một lần cũng không sao, vì tin vẫn hiện "Đã nhận". Không đáng tốn công gửi lại như với tin nhắn.

**Để giải quyết thì cần gì:**
- **R8:** chỉ báo "đã xem" khi người nhận **thật sự đang nhìn** đúng cuộc trò chuyện chứa tin đó.
- **R9:** bản tin "đã xem" phải **nhẹ**: gom nhiều tin vào một gói, không gửi lại, không bắt ký nhận.

---

## Bước 5 — Báo đã xem

**Làm gì:**
- Khi Bình đang mở ứng dụng và đang xem đúng cuộc trò chuyện với An, máy Bình gom mã số các thư vừa hiện ra gửi cho An: "đã xem các thư này". Mỗi tờ ghi tối đa 20 mã số.
- Nếu lúc thư đến Bình đang thu nhỏ cửa sổ hay đang xem cuộc trò chuyện khác, máy chờ tới khi Bình quay lại mới báo.
- Tờ "đã xem" rơi mất thì thôi, thư của An vẫn hiện "Đã nhận".

**Về kỹ thuật:** bản tin `MESSAGE_READ` mang tối đa 20 `messageId`, gửi kiểu cố gắng hết sức: không ACK, không gửi lại. Trạng thái hiển thị bên gửi thêm hai mức `PARTIALLY_READ` ("Đã xem 1/3") và `READ` ("Đã xem" hoặc "Tất cả đã xem").

**Trong code:**
- Chỉ báo khi cửa sổ đang được chọn và đang mở đúng tab: [RoomController.java:570-595](../client-javafx/src/main/java/com/datn/chatp2p/client/view/RoomController.java#L570-L595).
- Gửi theo lô 20 mã số: [P2pChatSession.java:531-544](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L531-L544).
- Nhận và giới hạn 20 mã số mỗi gói: [P2pChatSession.java:564-582](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L564-L582).

**Đáp ứng yêu cầu:**
- **R8:** chỉ báo khi thật sự đang xem.
- **R9:** gom theo lô, không gửi lại.

**→ Lỗ hổng:** tin dài hơn khoảng 1500 byte **không bao giờ tới nơi**, gửi lại bao nhiêu lần cũng vậy.

**Phân tích vấn đề:**
- Thư viện ice4j nhận mỗi gói UDP vào một bộ đệm cố định 1500 byte. Gói lớn hơn bị cắt cụt, giải mã AES-GCM thất bại, và gói bị vứt.
- Điều này được đo thực tế khi truyền file ([P2pDataChannel.java:232-236](../p2p-core/src/main/java/com/datn/chatp2p/p2p/channel/P2pDataChannel.java#L232-L236)).
- Một tin nhắn dài, hoặc một gói lịch sử, sau khi mã hoá và mã hoá Base64 dễ dàng vượt quá 1500 byte. Kết quả là gửi lại đủ 6 lần rồi báo "Chưa tới nơi", dù mạng hoàn toàn bình thường.
- Ngoài ra, đường đi qua mạng còn có giới hạn kích thước gói (MTU). Với IPv6, mức tối thiểu được bảo đảm chỉ là 1280 byte.

**Để giải quyết thì cần gì:**
- **R10:** bản tin lớn phải được **chia thành nhiều gói nhỏ**, mỗi gói nằm dưới giới hạn, rồi **ghép lại** ở bên nhận.
- **R11:** kẻ gian không được lợi dụng việc ghép gói để làm **cạn bộ nhớ** bên nhận, chẳng hạn gửi thật nhiều mảnh dở dang.

---

## Bước 6 — Chia mảnh và ghép lại

**Làm gì:**
- Thư dài thì An cắt ra thành nhiều tờ nhỏ. Mỗi tờ ghi "thư số 7, tờ 2 trên 5".
- Bình gom đủ 5 tờ của thư số 7 rồi mới ghép lại đọc.
- Thiếu một tờ quá 5 giây thì Bình vứt cả thư. An không thấy giấy ký nhận nên sẽ gửi lại cả thư.
- Bình chỉ ôm tối đa 64 thư dở dang cùng lúc, và một thư ghép lại không quá 1 MB. Kẻ gian gửi hàng nghìn tờ lẻ cũng không làm đầy nhà Bình được.

**Về kỹ thuật:**
- Byte đầu mỗi gói UDP cho biết loại gói: `1` là bản tin nguyên, `2` là một mảnh.
- Mảnh có đầu 9 byte: loại (1 byte), id bản tin (4 byte), số thứ tự mảnh (2 byte), tổng số mảnh (2 byte).
- Mỗi mảnh chứa tối đa 1150 byte dữ liệu, để cả gói nằm dưới khoảng 1170 byte, còn chỗ cho đầu gói UDP, IP và TURN, và dưới mức MTU 1280 của IPv6.
- Việc chia mảnh làm **sau khi mã hoá**. Từng mảnh không được xác thực riêng, nhưng cả bản tin sau khi ghép vẫn phải qua kiểm tra tag GCM. Mảnh bị sửa thì cả bản tin bị vứt.

**Trong code:**
- Các hằng số giới hạn: [P2pDataChannel.java:237-242](../p2p-core/src/main/java/com/datn/chatp2p/p2p/channel/P2pDataChannel.java#L237-L242).
- Chia mảnh: [P2pDataChannel.java:253-272](../p2p-core/src/main/java/com/datn/chatp2p/p2p/channel/P2pDataChannel.java#L253-L272).
- Ghép mảnh, kiểm tra thông số, hết hạn 5 giây, tối đa 64 bản tin dở dang: [P2pDataChannel.java:275-343](../p2p-core/src/main/java/com/datn/chatp2p/p2p/channel/P2pDataChannel.java#L275-L343).
- Gói đến từ địa chỉ khác địa chỉ ICE đã chọn thì bị bỏ qua: [P2pDataChannel.java:166](../p2p-core/src/main/java/com/datn/chatp2p/p2p/channel/P2pDataChannel.java#L166).
- Gói lỗi không làm chết vòng nhận: [P2pDataChannel.java:183-201](../p2p-core/src/main/java/com/datn/chatp2p/p2p/channel/P2pDataChannel.java#L183-L201).
- Ở tầng trên, một tin nhắn tối đa 12.000 ký tự. Tin dài hơn vẫn được ACK để bên gửi khỏi gửi lại mãi, nhưng không hiển thị ([GroupHistory.java:25](../client-javafx/src/main/java/com/datn/chatp2p/client/session/GroupHistory.java#L25), [P2pChatSession.java:481-485](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L481-L485)).

**Đáp ứng yêu cầu:**
- **R10:** bản tin tới 1 MB đi được qua đường giới hạn 1500 byte.
- **R11:** có giới hạn số bản tin dở dang, thời gian chờ mảnh và tổng kích thước.

**→ Lỗ hổng còn lại:** tin tới đủ và không trùng, nhưng **chưa chắc đúng thứ tự**.

**Phân tích vấn đề:**
1. An gửi liên tiếp "Mai họp nhé" rồi "7 giờ sáng".
2. Gói đầu bị mất, gói sau tới ngay.
3. Nửa giây sau "Mai họp nhé" mới được gửi lại và tới.
4. Màn hình của Bình hiện "7 giờ sáng" ở **trên** "Mai họp nhé".

Tin trực tiếp được thêm vào cuối danh sách theo thứ tự **đến** ([ChatTranscriptManager.java:118-120](../client-javafx/src/main/java/com/datn/chatp2p/client/view/ChatTranscriptManager.java#L118-L120)). Chỉ phần lịch sử chia sẻ mới được chèn theo thời gian gửi ([ChatTranscriptManager.java:127-151](../client-javafx/src/main/java/com/datn/chatp2p/client/view/ChatTranscriptManager.java#L127-L151)).

Một chỗ hở nhỏ hơn: hết lượt gửi lại thì tin bị đánh dấu "Chưa tới nơi" là xong. Không có nút để gửi lại tin đó.

**Để giải quyết thì cần gì:**
- **R12:** các tin của **cùng một người gửi** phải hiện đúng thứ tự người đó đã gửi.
- **R13:** tin "Chưa tới nơi" phải **gửi lại được** bằng một thao tác, không phải gõ lại.

---

## Bước 7 — *(đề xuất, chưa làm)* Giữ thứ tự theo người gửi và nút gửi lại

**Làm gì:**
- Mỗi lá thư của An mang thêm một **số thứ tự riêng của An**: 1, 2, 3…
- Bình đặt thư vào đúng chỗ theo số đó: thư số 1 đến muộn vẫn được xếp lên trên thư số 2.
- Thư "Chưa tới nơi" có một nút "Gửi lại". Bấm vào là thư được gửi lại với đúng mã số cũ, nên bên nhận vẫn loại trùng được.

**Về kỹ thuật:**
- Thêm trường `senderSeq` vào `MessagePayload`. Bên nhận chèn tin vào vị trí theo cặp `(authorId, senderSeq)`, giống cách `mergeGroupHistory` đang chèn theo `timeSent`.
- Không chặn hiển thị để chờ tin còn thiếu. Chặn như vậy sẽ làm tin sau phải chờ tin trước, mà một ứng dụng chat không nên để như thế.
- Nút "Gửi lại" gọi lại `sendMessageReliably` với cùng `messageId`.

**Trong code (dự kiến):**
- `MessagePayload` thêm trường; `P2pChatSession.sendGroupMessage` và `sendDirectMessage` tăng bộ đếm theo phiên.
- `ChatTranscriptManager.addGroup` và `addDirect` chèn theo thứ tự thay vì thêm vào cuối.
- `TranscriptCell` hiện nút "Gửi lại" khi trạng thái là `FAILED`.

**Đáp ứng yêu cầu:**
- **R12:** tin của một người luôn hiện đúng thứ tự.
- **R13:** gửi lại bằng một nút bấm.

---

## Bản tin nào đi đường nào

Không phải bản tin nào cũng cần được bảo đảm tới nơi. Gửi lại những bản tin không quan trọng chỉ làm nặng đường truyền.

| Bản tin | ACK và gửi lại | Lý do |
|---|---|---|
| `MESSAGE` (tin nhắn) | Có, 6 lần × 500 ms | Mất là mất nội dung |
| `MESSAGE_EDIT` (sửa/xoá) | Có, theo `editId` riêng | Mất thì hai bên thấy nội dung khác nhau |
| `MESSAGE_TRANSCRIPT` (lịch sử) | Có, theo `batchId` | Người mới vào thiếu lịch sử |
| `ROOM_PROOF`, `PEER_IDENTITY` | Không ACK, chỉ gửi lặp lại sau 400, 1200 và 2500 ms | Chỉ cần tới một lần. Bên nhận tự bỏ qua bản lặp |
| `MESSAGE_READ`, `TYPING_STATUS_CHANGE`, `MESSAGE_REACTION` | Không | Chỉ là thông tin thêm. Riêng "đang gõ" được gửi nhắc lại định kỳ và tự hết hạn |
| `FILE_CHUNK` | Có cơ chế riêng | Xem [Tra-loi-giao-thuc-truyen-file.md](Tra-loi-giao-thuc-truyen-file.md) |

---

## Tóm tắt chuỗi thiết kế

| Bước | Làm gì | Vá được gì | Sinh ra vấn đề gì |
|---|---|---|---|
| 0 | Gửi UDP thẳng | — | Mất gói, không ai biết |
| 1 | Bộ đệm socket 1 MB | Hết mất gói hàng loạt trên máy nhận | Mất trên mạng vẫn không cứu được |
| 2 | ACK + gửi lại 6 × 500 ms, mã hoá lại mỗi lần | Tin mất được gửi lại | ACK mất thì hiện trùng |
| 3 | Loại trùng theo `messageId`, sổ 512 mã số, luôn ACK lại | Không hiện trùng | Người dùng không thấy trạng thái |
| 4 | Trạng thái Đang gửi / Đã nhận k/n / Chưa tới nơi | Biết tin đã tới ai | Đã nhận chưa phải đã đọc |
| 5 | `MESSAGE_READ`, tối đa 20 mã số mỗi gói, chỉ khi đang xem | Biết ai đã xem | Tin lớn hơn 1500 byte không bao giờ tới |
| 6 | Chia mảnh 1150 byte, ghép lại, có giới hạn | Bản tin tới 1 MB đi được | Chưa giữ thứ tự, không gửi lại thủ công được |
| 7 *(đề xuất)* | Số thứ tự theo người gửi + nút "Gửi lại" | Đúng thứ tự, gửi lại dễ | — |

```
B0 gửi thẳng
 ──mất gói hàng loạt trên máy──► B1 bộ đệm 1 MB
 ──mất trên mạng───────────────► B2 ACK + gửi lại
 ──ACK mất thì trùng───────────► B3 loại trùng theo mã số
 ──người dùng không thấy gì────► B4 trạng thái giao
 ──đã nhận chưa phải đã đọc────► B5 báo đã xem
 ──tin lớn bị cắt cụt──────────► B6 chia mảnh + ghép
 ──lộn thứ tự──────────────────► B7 số thứ tự + nút gửi lại (đề xuất)
```
