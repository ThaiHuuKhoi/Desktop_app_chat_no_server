# Cơ chế phòng có mật khẩu

Tài liệu này trả lời câu hỏi: **làm sao chỉ những người biết mật khẩu mới vào được phòng, trong khi server, nơi xếp mọi người vào phòng, không bao giờ biết mật khẩu đó?**

Cách trình bày giống các tài liệu cơ chế khác: đi từ cách ngây thơ, tìm lỗ hổng, phân tích cần gì, rồi sinh ra bước tiếp theo. Ở mỗi chỗ phải chọn có so sánh các phương án.

Nhân vật:
- **An** lập một nhóm kín và muốn chỉ bạn bè mình vào được.
- **Bình** là bạn của An, được An báo mật khẩu.
- **Minh** là người ngoài, muốn chen vào nhóm.
- **Bưu điện** là chỗ xếp mọi người vào đúng nhóm. Trong code là signaling server.

---

## Bước 0 — Phòng không có mật khẩu

**Làm gì:** An dán ở bưu điện một tờ thông báo "Nhóm của An đang họp". Ai đọc được tên nhóm thì đi vào được.

**Về kỹ thuật:** client gửi `roomId` lên signaling server, và server xếp những ai cùng `roomId` vào một phòng. Màn hình sảnh lấy danh sách phòng đang có người từ server.

**Trong code:** tên phòng được dùng thẳng làm `roomId` khi không có mật khẩu ([P2pChatSession.java:119-121](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L119-L121)). Danh sách phòng ở sảnh lấy từ [RoomRegistry.java:157-170](../signaling-server/src/main/java/com/datn/chatp2p/signaling/room/RoomRegistry.java#L157-L170).

**Đáp ứng yêu cầu:** vào phòng nhanh, không phải nhập gì thêm. Phù hợp cho phòng công khai.

**→ Lỗ hổng:** tên phòng hiện ngay ở sảnh, nên ai cũng vào được.

**Phân tích vấn đề:**
- Tên phòng không phải bí mật: nó được liệt kê ở sảnh, và còn dễ đoán.
- Tin nhắn vẫn được mã hoá đầu cuối, nhưng mã hoá *với tất cả những ai đang ở trong phòng*. Minh vào được phòng thì Minh cũng nhận được chìa chung như mọi người và đọc được hết.

**Để giải quyết thì cần gì:**
- **R1:** phải có một bí mật mà chỉ người được mời mới biết. Không biết bí mật thì không vào được.
- **R2:** **server không được biết bí mật đó.** Server biết thì một người quản trị server, một file log hay một lần server bị chiếm đều làm lộ mật khẩu của mọi phòng.

**Xét các phương án:**
- **Gửi mật khẩu lên server để server kiểm tra:** server nhìn thấy mật khẩu ở mỗi lần kiểm tra. Không đạt R2.
- **Server lưu bản băm của mật khẩu:** người vào vẫn phải gửi mật khẩu lên để server băm rồi so, nên server vẫn thấy mật khẩu. Mặt khác, việc kiểm tra vẫn nằm hoàn toàn trong tay server. Không đạt R2.
- **Biến mật khẩu thành một giá trị một chiều ngay trên máy người dùng, server chỉ dùng giá trị đó để xếp phòng:** server không bao giờ thấy mật khẩu. Đây là hướng được chọn, và sinh ra Bước 1.

---

## Bước 1 — Thẻ phòng: người sai mật khẩu rơi vào phòng khác

**Làm gì:**
- An và Bình, từ tên nhóm và mật khẩu, cùng tính theo một công thức ra một **mã số dài**. Công thức chỉ tính xuôi được, không tính ngược từ mã số ra mật khẩu được.
- Ra bưu điện, mỗi người chỉ nói "cho tôi vào nhóm của An, mã số này". Bưu điện xếp những người cùng tên nhóm và cùng mã số vào chung một phòng.
- Minh không biết mật khẩu nên tính ra mã số khác, và bị xếp vào một phòng khác. Minh không thấy An và Bình.
- Bưu điện chỉ thấy mã số, không thấy mật khẩu.

**Về kỹ thuật:**
1. Từ `(tên phòng, mật khẩu)`, chạy PBKDF2-HMAC-SHA256 với **120.000 vòng**, salt là `"chat-p2p-room-salt-v1|" + tên phòng`. Kết quả là một khoá chính 256 bit.
2. Từ khoá chính, dùng HMAC với hai nhãn khác nhau để tách ra hai khoá con: khoá làm thẻ phòng và khoá làm bằng chứng (dùng ở Bước 3).
3. Thẻ phòng là 24 ký tự hex đầu tiên của khoá con thứ nhất, tức 96 bit.
4. `roomId` gửi lên server có dạng `tênphòng~thẻ`.

**Trong code:**
- Tính khoá: [RoomSecret.java:31](../crypto/src/main/java/com/datn/chatp2p/crypto/RoomSecret.java#L31), [39-64](../crypto/src/main/java/com/datn/chatp2p/crypto/RoomSecret.java#L39-L64). Mật khẩu và khoá chính bị xoá khỏi bộ nhớ ngay sau khi dùng.
- Thẻ phòng: [RoomSecret.java:67-69](../crypto/src/main/java/com/datn/chatp2p/crypto/RoomSecret.java#L67-L69).
- Ghép `roomId`: [P2pChatSession.java:115-121](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L115-L121).
- Mật khẩu không bao giờ nằm trong bản tin gửi lên server. Khi in cấu hình ra log, chỉ in "có mật khẩu hay không" ([RoomConfig.java:27-32](../client-javafx/src/main/java/com/datn/chatp2p/client/session/RoomConfig.java#L27-L32)). Những chỗ log khác in mật khẩu dưới dạng `***`, trừ khi người phát triển bật chế độ gỡ lỗi ([SecretLogging.java:33-38](../common/src/main/java/com/datn/chatp2p/common/log/SecretLogging.java#L33-L38)).
- Khi lập phòng, mật khẩu phải có ít nhất 6 ký tự ([LobbyRules.java:25](../client-javafx/src/main/java/com/datn/chatp2p/client/view/LobbyRules.java#L25), [70-72](../client-javafx/src/main/java/com/datn/chatp2p/client/view/LobbyRules.java#L70-L72)).
- Sảnh vẫn liệt kê phòng có mật khẩu để người dùng chọn rồi nhập mật khẩu, nhưng **cắt bỏ phần thẻ**, chỉ trả về tên phòng và cờ `passwordProtected` ([RoomRegistry.java:157-170](../signaling-server/src/main/java/com/datn/chatp2p/signaling/room/RoomRegistry.java#L157-L170)).

**Đáp ứng yêu cầu:**
- **R1:** sai mật khẩu thì sai thẻ, và rơi vào phòng khác.
- **R2:** server chỉ thấy thẻ, không thấy mật khẩu.

**→ Lỗ hổng:** người nhập sai mật khẩu **không biết mình sai**.

**Phân tích vấn đề:**
- Bình gõ nhầm một chữ. Bình bị xếp vào một phòng trống mang đúng tên "nhóm của An", và ngồi chờ mãi mà không thấy ai.
- Tệ hơn, có hai người cùng gõ nhầm theo cùng một kiểu thì họ vào chung một phòng giả mang tên của An mà không hề biết.
- Hai người cùng lúc lập phòng trùng tên với hai mật khẩu khác nhau cũng tạo ra hai phòng trùng tên.

**Để giải quyết thì cần gì:**
- **R3:** sai mật khẩu thì phải được **báo ngay**.
- **R4:** việc báo "sai mật khẩu" lại mở ra một cách dò mật khẩu: Minh có thể thử hàng loạt mật khẩu và chờ câu trả lời. Vì vậy phải **giới hạn số lần thử sai**.

---

## Bước 2 — Báo sai mật khẩu và giới hạn số lần thử

**Làm gì:**
- Khi có người xin vào "nhóm của An" với một mã số khác mã số của nhóm đang họp, bưu điện trả lời ngay: "sai mật khẩu". Bưu điện vốn đã biết mã số của nhóm đang họp nên không cần biết thêm gì để so.
- Ai nói sai mã số 5 lần trong một phút thì bị chặn, phải chờ.
- Việc chặn tính theo địa chỉ nhà người gửi chứ không theo từng lần ghé, nên đi ra rồi vào lại cũng không thoát được.

**Về kỹ thuật:**
- Server nhận `tên~thẻ`. Nếu đang có phòng `tên~thẻ khác` còn người, server trả `JOIN_REJECTED` với lý do `WRONG_PASSWORD`.
- Việc kiểm tra và tạo phòng diễn ra dưới cùng một khoá đồng bộ. Nhờ vậy hai người cùng lúc lập `X~T1` và `X~T2` không thể cùng thành công.
- Mỗi lần sai, server ghi nhận theo địa chỉ IP (IPv6 gộp theo dải /64). Từ lần thứ 5 trong 60 giây, server trả `RATE_LIMITED`.

**Trong code:**
- So thẻ dưới `privateRoomLock`: [RoomRegistry.java:66-82](../signaling-server/src/main/java/com/datn/chatp2p/signaling/room/RoomRegistry.java#L66-L82).
- Chặn trước khi xét phòng: [SignalingWebSocketHandler.java:230-235](../signaling-server/src/main/java/com/datn/chatp2p/signaling/ws/SignalingWebSocketHandler.java#L230-L235).
- Ghi nhận lần sai: [SignalingWebSocketHandler.java:255-258](../signaling-server/src/main/java/com/datn/chatp2p/signaling/ws/SignalingWebSocketHandler.java#L255-L258).
- Khoá đếm theo IP: [SignalingWebSocketHandler.java:384-389](../signaling-server/src/main/java/com/datn/chatp2p/signaling/ws/SignalingWebSocketHandler.java#L384-L389).
- Bộ đếm theo cửa sổ thời gian, 5 lần trong 60 giây: [WrongPasswordLimiter.java:18-19](../signaling-server/src/main/java/com/datn/chatp2p/signaling/room/WrongPasswordLimiter.java#L18-L19), [37-66](../signaling-server/src/main/java/com/datn/chatp2p/signaling/room/WrongPasswordLimiter.java#L37-L66).

**Đáp ứng yêu cầu:**
- **R3:** sai mật khẩu là có thông báo ngay.
- **R4:** tối đa 5 lần thử sai mỗi phút cho mỗi địa chỉ.

**→ Lỗ hổng:** cả Bước 1 và Bước 2 đều **dựa hoàn toàn vào việc bưu điện làm đúng**.

**Phân tích vấn đề:**
- Ai được vào phòng nào là do server quyết định.
- Nếu server bị chiếm, hoặc người quản trị server có ý xấu, họ có thể ghép Minh, người không biết mật khẩu, vào đúng phòng `tên~thẻ` của An.
- Trên máy An, Minh hiện ra như một thành viên bình thường. Bước trao chìa chung diễn ra như với mọi người khác, và Minh đọc được toàn bộ tin nhắn.
- Mật khẩu phòng sinh ra chính là để không phải tin ai ngoài những người biết mật khẩu. Vậy mà đến đây lại phải tin server.

**Để giải quyết thì cần gì:**
- **R5:** mỗi thành viên phải **tự kiểm tra** người mới có biết mật khẩu hay không, không dựa vào server.
- **R6:** khi kiểm tra **không được để lộ mật khẩu** trên đường truyền, kể cả cho chính người đang bị kiểm tra.
- **R7:** bằng chứng "biết mật khẩu" của lần này không được **mang ra dùng lại** ở lần khác, hay chuyển tiếp từ người khác. Minh đứng giữa mà chép được bằng chứng của Bình thì cũng vô ích.

**Xét các phương án:**
- **Hai bên đọc mật khẩu cho nhau:** lộ mật khẩu cho chính người đang bị kiểm tra, mà người đó có thể là kẻ gian. Không đạt R6.
- **Gửi bản băm của mật khẩu:** bản băm lần nào cũng giống nhau, nên ai thấy một lần là dùng lại được mãi. Không đạt R7.
- **Dùng mật khẩu làm khoá để ký một nội dung gắn với buổi trao chìa đang diễn ra:** không lộ mật khẩu, và mỗi buổi ra một bằng chứng khác nhau. Đây là hướng được chọn.

---

## Bước 3 — Thành viên tự kiểm tra nhau: bằng chứng gắn với phiên

**Làm gì:**
- Sau khi An và người mới làm xong chìa chung, mỗi bên viết một tờ giấy: "Tôi là người này, phần nguyên liệu tôi gửi đi có số hiệu thế này, phần tôi nhận được có số hiệu thế kia". Rồi mỗi bên đóng lên tờ giấy một dấu được tạo từ mật khẩu nhóm.
- Chỉ ai biết mật khẩu mới đóng được dấu đó. Bên nhận cũng tự đóng thử một dấu với nội dung mình đang thấy để so, nên không cần ai nói ra mật khẩu.
- Dấu không khớp thì không cho người mới vào nhóm. Trước khi có dấu hợp lệ, mọi thư của người mới đều bị bỏ qua.
- Minh được bưu điện đưa vào phòng cũng không đóng được dấu, vì không biết mật khẩu. Minh chép được tờ giấy của Bình cũng vô ích, vì số hiệu nguyên liệu trên đó là của buổi trao chìa giữa Bình và An, không khớp với buổi giữa Minh và An.

**Về kỹ thuật:**
- Bằng chứng là HMAC-SHA256 tính bằng khoá con thứ hai ở Bước 1, trên nội dung:

  ```
  chat-p2p-room-proof-v1 | tên phòng | peerId bên gửi | SHA-256(khoá ECDH bên gửi) | SHA-256(khoá ECDH bên nhận)
  ```

- Bản tin chứa bằng chứng là `ROOM_PROOF`, được gửi ngay sau khi trao khoá ECDH xong.
- Bên nhận tự tính lại HMAC từ khoá ECDH mà máy mình thấy, rồi so sánh theo kiểu không để lộ thông tin qua thời gian so (constant-time).
- Cách gắn với khoá ECDH giống hệt chữ ký danh tính ở [Co-che-xac-thuc-danh-tinh.md](Co-che-xac-thuc-danh-tinh.md), Bước 2.

**Trong code:**
- Tính và kiểm tra bằng chứng: [RoomSecret.java:77-90](../crypto/src/main/java/com/datn/chatp2p/crypto/RoomSecret.java#L77-L90).
- Phòng công khai thì cho vào ngay. Phòng có mật khẩu thì gửi `ROOM_PROOF`, gửi lại sau 400, 1200 và 2500 ms phòng khi mất gói UDP ([P2pChatSession.java:351-371](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L351-L371)).
- Kiểm tra `ROOM_PROOF`: [P2pChatSession.java:373-398](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L373-L398).
  - Hợp lệ thì gọi `admit`, người đó hiện lên giao diện.
  - Sai thì đưa vào `rejectedPeers` và báo "người này không có đúng mật khẩu phòng".
- Mọi loại bản tin khác (tin nhắn, ACK, lịch sử, đang gõ, danh tính, reaction, sửa tin, file) chỉ được xử lý khi người gửi đã có trong `admittedPeers` ([P2pChatSession.java:170-182](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L170-L182), [455-463](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L455-L463)).
- Tin của mình cũng chỉ gửi cho người đã có trong `admittedPeers` ([P2pChatSession.java:344-346](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L344-L346)).

**Đáp ứng yêu cầu:**
- **R5:** mỗi máy tự kiểm tra, server có ghép sai thì người lạ cũng không được nhận vào.
- **R6:** trên đường truyền chỉ có HMAC, không có mật khẩu.
- **R7:** bằng chứng gắn với khoá ECDH của đúng phiên, chép sang phiên khác là hỏng.

**→ Lỗ hổng:** thẻ phòng ở Bước 1 là **một giá trị tính ra từ mật khẩu**, và server nhìn thấy nó.

**Phân tích vấn đề:**
- Server, hoặc ai đọc được log hay bộ nhớ của server, có `tên phòng` và `thẻ`. Họ có thể ngồi ở nhà thử từng mật khẩu một: chạy PBKDF2 rồi so với thẻ. Giới hạn 5 lần mỗi phút ở Bước 2 không ngăn được việc này, vì việc thử diễn ra trên máy của họ, không cần hỏi server.
- Mỗi lần thử tốn 120.000 vòng PBKDF2. Nhưng mật khẩu tối thiểu chỉ 6 ký tự, và người dùng hay đặt mật khẩu dễ nhớ, nên vẫn có thể đoán ra.
- Salt là tên phòng, nên những tên phổ biến như "test" hay "nhom1" có thể được tính sẵn bảng tra từ trước.
- Hậu quả: biết mật khẩu thì vượt qua được cả Bước 3.

**Để giải quyết thì cần gì:**
- **R8:** mỗi lần đoán thử thẻ phòng phải **đắt hơn nhiều**, cả về thời gian lẫn bộ nhớ.
- **R9:** mật khẩu phải **đủ mạnh**, hoặc tốt hơn nữa là do máy sinh ngẫu nhiên thay vì người tự đặt.

---

## Bước 4 — *(đề xuất, chưa làm)* Làm cho việc đoán mật khẩu đắt hơn

**Làm gì:**
- Công thức tính mã số được làm chậm hơn và cần nhiều chỗ làm việc hơn. An tính một lần chỉ mất chưa tới một giây, nhưng Minh muốn thử hàng triệu mật khẩu thì phải tốn rất nhiều thời gian và máy móc.
- Khi lập nhóm, máy gợi ý sẵn một mật khẩu dài ngẫu nhiên. An chỉ việc gửi nguyên đường dẫn mời cho Bình.

**Về kỹ thuật:**
- Đổi PBKDF2 sang **Argon2id**, một hàm vừa tốn thời gian vừa tốn bộ nhớ. Kiểu tấn công thử hàng loạt bằng card đồ hoạ trở nên kém hiệu quả hơn hẳn.
- Hoặc ít nhất tăng số vòng PBKDF2 lên khoảng 600.000, mức OWASP khuyến nghị cho HMAC-SHA256.
- Tăng độ dài mật khẩu tối thiểu, có thanh báo độ mạnh, và có nút "tạo mật khẩu ngẫu nhiên".

**Trong code (dự kiến):**
- Đổi hàm tính khoá trong `RoomSecret.derive` và nâng nhãn phiên bản lên `chat-p2p-room-salt-v2`. Hai phiên bản cho ra hai thẻ khác nhau, nên client cũ và client mới không vào nhầm phòng của nhau.
- Thêm kiểm tra độ mạnh ở `LobbyRules.validateCreate`.

**Đáp ứng yêu cầu:**
- **R8:** mỗi lần đoán tốn nhiều thời gian và bộ nhớ hơn.
- **R9:** mật khẩu ngẫu nhiên dài thì gần như không đoán được.

---

## Những giới hạn còn lại cần nêu khi bảo vệ

| Giới hạn | Giải thích |
|---|---|
| Mật khẩu chỉ dùng để **cho vào phòng**, không trộn vào khoá mã hoá tin nhắn | Không cần: khoá tin nhắn đã đến từ ECDH riêng cho từng cặp, và `ROOM_PROOF` gắn với chính khoá ECDH đó. Người đứng giữa không qua được bước kiểm tra mật khẩu. |
| Không đuổi được người đã biết mật khẩu | Ai biết mật khẩu thì luôn vào lại được. Muốn loại một người thì phải lập phòng mới với mật khẩu mới. |
| Tên phòng vẫn hiện ở sảnh | Có chủ đích, để người được mời tìm thấy phòng. Chỉ phần thẻ bị giấu đi. |
| Server vẫn biết ai đang ở trong phòng nào | Server cần biết điều này để xếp phòng và chuyển tín hiệu kết nối. Nội dung tin nhắn thì server không bao giờ thấy. |

---

## Tóm tắt chuỗi thiết kế

| Bước | Làm gì | Vá được gì | Sinh ra vấn đề gì |
|---|---|---|---|
| 0 | Phòng không mật khẩu | — | Ai biết tên phòng cũng vào được |
| 1 | Thẻ phòng từ PBKDF2, `roomId = tên~thẻ` | Sai mật khẩu rơi vào phòng khác; server không thấy mật khẩu | Người sai không biết mình sai |
| 2 | Báo `WRONG_PASSWORD`, giới hạn 5 lần mỗi phút theo IP | Báo ngay khi sai, chặn dò mật khẩu qua server | Tất cả vẫn phải tin server |
| 3 | `ROOM_PROOF`: HMAC gắn với khoá ECDH, cổng `admittedOnly` | Thành viên tự kiểm tra, server ghép sai cũng không lọt | Thẻ phòng vẫn cho phép đoán mật khẩu ngoại tuyến |
| 4 *(đề xuất)* | Argon2id, mật khẩu ngẫu nhiên | Đoán thử đắt hơn nhiều | — |

```
B0 không mật khẩu
 ──ai cũng vào được──────────► B1 thẻ phòng, server không thấy mật khẩu
 ──sai mà không biết─────────► B2 báo sai + giới hạn thử
 ──vẫn phải tin server───────► B3 ROOM_PROOF gắn với phiên
 ──đoán ngoại tuyến từ thẻ───► B4 Argon2id + mật khẩu ngẫu nhiên (đề xuất)
```
