# Cơ chế xác thực danh tính và vân tay

Tài liệu này trả lời câu hỏi: **khi màn hình hiện "Bình", làm sao An biết đó thật sự là Bình?**

Cách trình bày giống các tài liệu cơ chế khác. Bắt đầu từ cách làm ngây thơ nhất, tìm ra chỗ hỏng, phân tích cần gì để vá, rồi từ yêu cầu đó sinh ra bước tiếp theo. Ở mỗi chỗ phải chọn, so sánh các phương án khác trước khi chọn.

Nhân vật:
- **An** và **Bình**: hai người muốn nói chuyện với nhau.
- **Minh**: kẻ gian, muốn giả làm Bình hoặc đứng giữa đọc trộm.
- **Bưu điện**: chỗ trung chuyển thư. Trong code là signaling server.

Tài liệu mã hoá tin nhắn ([Co-che-ma-hoa-tin-nhan.md](Co-che-ma-hoa-tin-nhan.md)) đã cho An và Bình một chìa chung để khoá thư. Tài liệu này giải quyết phần còn thiếu: **chìa chung đó đang được dùng chung với ai.**

---

## Bước 0 — Tin vào cái tên tự xưng

**Làm gì:** Bình gửi thư đầu tiên ghi "Tôi là Bình". An thấy chữ "Bình" thì tin luôn.

**Về kỹ thuật:** khi vào phòng, mỗi client gửi một `userName` lên signaling server. Server chuyển tên đó cho những người khác trong phòng. Màn hình hiện tên này cạnh mỗi người.

**Trong code:** tên lấy từ `connection.getCustomUsername()` ([P2pChatSession.java:412-415](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L412-L415)). Ở chế độ khách, tên do người dùng tự gõ, tối đa 64 ký tự ([SignalingWebSocketHandler.java:75](../signaling-server/src/main/java/com/datn/chatp2p/signaling/ws/SignalingWebSocketHandler.java#L75)).

**Đáp ứng yêu cầu:** có tên để hiển thị và phân biệt mọi người trong phòng.

**→ Lỗ hổng:** ai cũng viết được chữ "Bình".

**Phân tích vấn đề:**
- **Mạo danh thẳng:** Minh vào phòng với tên "Bình". An không có cách nào phân biệt.
- **Đứng giữa:** nguy hiểm hơn. Bước trao chìa chung đi qua bưu điện. Nếu Minh chiếm được bưu điện, Minh có thể làm chìa chung riêng với An và một chìa chung khác với Bình. Thư của An đến tay Minh, Minh mở ra đọc, khoá lại bằng chìa thứ hai rồi chuyển cho Bình. Cả hai vẫn thấy thư được khoá cẩn thận và không ai nhận ra.

Gốc của cả hai vấn đề là một: **cái tên không chứng minh được gì**, vì ai cũng tạo ra được nó.

**Để giải quyết thì cần gì:**
- **R1:** Bình phải đưa ra được một bằng chứng mà **chỉ Bình làm ra được**. Minh không tự tạo được, kể cả khi Minh đọc được hết thư qua lại.
- **R2:** An phải **tự kiểm tra** được bằng chứng đó mà không cần hỏi lại Bình hay nhờ bưu điện.

**Xét các phương án:**
- **An và Bình hẹn trước một câu mật khẩu riêng:** phải gặp nhau từ trước, và mỗi cặp bạn lại cần một câu riêng. Muốn kiểm tra thì phải đưa câu đó ra, mà đưa ra rồi thì Minh nghe được. Không đạt R1.
- **Nhờ bưu điện xác nhận "người này đúng là Bình":** nếu chính bưu điện bị Minh chiếm thì lời xác nhận cũng là của Minh. Khách không có tài khoản thì bưu điện cũng chẳng có gì để xác nhận. Không đạt R2.
- **Mỗi người có một con dấu riêng, ai cũng kiểm tra được hình in nhưng không ai làm giả được:** đạt cả R1 và R2. Đây là chữ ký số và là hướng được chọn.

---

## Bước 1 — Mỗi người có một con dấu riêng

**Làm gì:** Bình khắc một con dấu riêng và giữ cho mình. Ai nhìn hình in cũng so được, nhưng chỉ người cầm con dấu mới đóng được hình in đó. Bình gửi cho An hình in của con dấu, kèm một tờ giấy ghi "Tôi là Bình, trong phòng này" có đóng dấu. An so hình in trên tờ giấy với hình in Bình gửi. Khớp thì tờ giấy đúng là do người cầm con dấu đóng.

**Về kỹ thuật:** đây là chữ ký số ECDSA trên đường cong P-256, băm SHA-256. Mỗi máy có một **cặp khoá danh tính** dùng lâu dài:
- **Private key** là con dấu, chỉ nằm trên máy.
- **Public key** là hình in, gửi cho người khác.

Cặp khoá này **khác** với cặp khoá ECDH:
- Khoá ECDH sinh mới cho mỗi lần kết nối và chỉ dùng để làm chìa chung.
- Khoá danh tính sinh một lần và dùng mãi, chính là "danh tính" của người dùng.

**Trong code:**
- Sinh khoá, ký, kiểm tra: [IdentitySignatureService.java:42-50](../crypto/src/main/java/com/datn/chatp2p/crypto/IdentitySignatureService.java#L42-L50), [93-127](../crypto/src/main/java/com/datn/chatp2p/crypto/IdentitySignatureService.java#L93-L127).
- Nội dung được ký ở phiên bản đầu tiên chỉ là `phòng + "_" + tên` ([IdentitySignatureService.java:56-58](../crypto/src/main/java/com/datn/chatp2p/crypto/IdentitySignatureService.java#L56-L58)).
- Bản tin mang chữ ký là `PEER_IDENTITY`, gồm public key và chữ ký.

**Đáp ứng yêu cầu:**
- **R1:** Minh không có private key của Bình nên không ký được.
- **R2:** An kiểm tra bằng public key, không cần hỏi ai.

**→ Lỗ hổng:** tờ giấy "Tôi là Bình, trong phòng này" **lần nào cũng giống hệt nhau**.

**Phân tích vấn đề:** quay lại tình huống đứng giữa:
1. Minh đứng giữa, làm hai chìa chung riêng, một với An và một với Bình.
2. Bình gửi tờ giấy có dấu thật. Tờ giấy đi qua tay Minh.
3. Minh **chuyển nguyên** tờ giấy đó cho An.
4. Dấu là thật, nội dung đúng, nên An tin. An không biết chìa chung mình đang dùng là chìa làm với Minh.

Chữ ký chứng minh được "Bình có mặt trong phòng", nhưng **không gắn với buổi trao chìa cụ thể** nào. Vì vậy Minh chép lại được, dùng lại được.

**Để giải quyết thì cần gì:**
- **R3:** nội dung được ký phải **gắn với chính lần trao chìa chung đang diễn ra**. Nếu có người đứng giữa thì nội dung Bình ký và nội dung An kiểm tra phải khác nhau, và chữ ký phải hỏng.
- **R4:** cả hai bên tự tính ra nội dung cần kiểm tra từ những gì **máy mình thực sự thấy**, không tin vào nội dung đối phương gửi kèm.

---

## Bước 2 — Ký kèm những gì hai bên vừa trao nhau

**Làm gì:** khi làm chìa chung, An và Bình gửi cho nhau mỗi người một phần nguyên liệu. Trên tờ giấy đóng dấu, Bình không chỉ ghi "Tôi là Bình" nữa mà ghi thêm: "phần nguyên liệu tôi gửi đi có số hiệu thế này, phần tôi nhận được có số hiệu thế kia". An đọc tờ giấy và so hai số hiệu với **đúng hai phần nguyên liệu mà An đang cầm**: phần An gửi đi và phần An nhận về.

Nếu Minh đứng giữa:
- Phần nguyên liệu Bình nhận được thực ra là của Minh.
- Phần nguyên liệu An nhận được cũng là của Minh.

Số hiệu trên tờ giấy của Bình sẽ không khớp với những gì An đang cầm. Minh muốn sửa số hiệu thì phải đóng lại dấu, mà Minh không có con dấu của Bình.

**Về kỹ thuật:** nội dung được ký là

```
chat-p2p-identity-v2 | phòng | peerId của bên ký | SHA-256(public key ECDH của bên ký) | SHA-256(public key ECDH của bên nhận)
```

Bên nhận ghép lại chuỗi này bằng **public key ECDH mà chính máy mình nhận được** trong phiên. Bên ký là đối phương, nên thứ tự hai khoá phải đảo ngược so với lúc ký.

**Trong code:**
- Định dạng chuỗi: [IdentitySignatureService.java:76-81](../crypto/src/main/java/com/datn/chatp2p/crypto/IdentitySignatureService.java#L76-L81).
- Ký theo thứ tự (khoá ECDH của mình, khoá ECDH của đối phương): [PeerIdentityService.java:52-60](../client-javafx/src/main/java/com/datn/chatp2p/client/session/PeerIdentityService.java#L52-L60).
- Kiểm tra theo thứ tự ngược lại: [PeerIdentityService.java:66-76](../client-javafx/src/main/java/com/datn/chatp2p/client/session/PeerIdentityService.java#L66-L76).
- Gửi `PEER_IDENTITY` ngay khi người kia được nhận vào phòng. Vì UDP có thể mất gói, bản tin này được gửi lại sau 400, 1200 và 2500 ms ([P2pChatSession.java:71](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L71), [427-429](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L427-L429)).
- Xử lý khi nhận ([P2pChatSession.java:805-860](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L805-L860)):
  - Chữ ký sai thì ghi cảnh báo "nghi ngờ MITM" và báo lỗi kết nối.
  - Chữ ký đúng thì lưu vân tay vào `verifiedRemoteFingerprints`, trạng thái người đó chuyển từ "Đang xác minh…" sang đã xác thực.
- Lịch sử chat do người khác chia sẻ chỉ được nhận **sau khi** người đó đã xác thực danh tính ([P2pChatSession.java:711-715](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L711-L715)).

**Đáp ứng yêu cầu:**
- **R3:** chữ ký gắn với khoá ECDH của đúng phiên này. Minh chuyển nguyên chữ ký thật thì chữ ký hỏng.
- **R4:** bên kiểm tra dùng khoá ECDH máy mình thấy, không dùng thứ gì đối phương tự khai.

**→ Lỗ hổng:** Minh không cần giả con dấu của Bình. **Minh tự khắc một con dấu của chính mình** rồi xưng là Bình.

**Phân tích vấn đề:**
1. Minh có con dấu và hình in của riêng Minh.
2. Minh gửi cho An hình in của Minh và một tờ giấy ghi đúng số hiệu nguyên liệu, đóng bằng dấu của Minh.
3. An kiểm tra thì thấy dấu khớp với hình in đi kèm, số hiệu cũng khớp. Mọi thứ đều đúng.

Chữ ký chỉ chứng minh: **"người gửi đang cầm con dấu có hình in này"**. Nó không chứng minh **"hình in này là của Bình"**. Lần đầu gặp, An chưa từng thấy hình in thật của Bình nên không có gì để so.

**Để giải quyết thì cần gì:**
- **R5:** phải nối được hình in với **con người thật** là Bình, qua một đường mà Minh không kiểm soát được.
- **R6:** việc so phải **đủ dễ** để người dùng chịu làm, chứ không phải đọc cho nhau nghe một chuỗi 64 ký tự.

**Xét các phương án:**
- **Một bên thứ ba cấp giấy chứng nhận "hình in này là của Bình"**, giống chứng chỉ của các trang web: cần dựng cả một hệ thống cấp và thu hồi chứng nhận. Nếu bên cấp chính là server của dự án thì quay về vấn đề cũ: chiếm được server là cấp giấy giả được.
- **Tự động tin hình in ở lần gặp đầu, chỉ cảnh báo khi lần sau thấy khác:** ở chế độ khách, tên do người dùng tự đặt và rất hay trùng, nên sẽ sinh ra cảnh báo giả liên tục. Lần đầu tiên cũng không được bảo vệ. Code cố ý **không** làm cách này ([TrustedPeers.java:24-25](../client-javafx/src/main/java/com/datn/chatp2p/client/util/TrustedPeers.java#L24-L25)).
- **Hai người tự so hình in qua một đường khác:** gặp mặt, gọi điện hoặc nhắn qua ứng dụng khác. Minh có chiếm được bưu điện cũng không chen vào cuộc gọi được. Signal, WhatsApp và Zalo đều làm như vậy, và đây là hướng được chọn.

---

## Bước 3 — Vân tay và mã an toàn chung

**Làm gì:**
- Từ hình in con dấu, mỗi người tính ra một **số hiệu ngắn**, gọi là vân tay.
- Từ vân tay của hai người, cả hai cùng tính ra **một dãy số chung**: 12 nhóm, mỗi nhóm 5 chữ số, kèm một mã QR. Dãy số giống hệt nhau trên cả hai máy, dù ai tính.
- An gọi cho Bình, hai người đọc dãy số cho nhau nghe, hoặc đặt hai màn hình cạnh nhau để so mã QR. Khớp thì không có ai đứng giữa.
- Minh mà đứng giữa thì An đang thấy vân tay của Minh chứ không phải của Bình, nên dãy số trên hai máy sẽ khác nhau.

**Về kỹ thuật:**
- **Vân tay:** SHA-256 của public key danh tính, viết thành hex chia nhóm 4 ký tự.
- **Mã an toàn chung:**
  1. Chuẩn hoá hai vân tay rồi sắp xếp theo thứ tự cố định, để đổi chỗ hai người vẫn ra cùng một chuỗi.
  2. Lấy SHA-512 của chuỗi đó, cắt thành 12 khối 5 byte.
  3. Mỗi khối lấy phần dư khi chia cho 100000, được một nhóm 5 chữ số.
  4. Tổng cộng 60 chữ số, khoảng 199 bit.

**Trong code:**
- Vân tay: [Fingerprint.java:24-32](../crypto/src/main/java/com/datn/chatp2p/crypto/Fingerprint.java#L24-L32).
- Ghép hai vân tay theo thứ tự: [SafetyCode.java:24-28](../client-javafx/src/main/java/com/datn/chatp2p/client/view/SafetyCode.java#L24-L28).
- 12 nhóm 5 chữ số: [SafetyCode.java:37-55](../client-javafx/src/main/java/com/datn/chatp2p/client/view/SafetyCode.java#L37-L55).
- Mã QR: [SafetyCode.java:58-72](../client-javafx/src/main/java/com/datn/chatp2p/client/view/SafetyCode.java#L58-L72).
- Hộp thoại so vân tay có hai nút "Khớp – đánh dấu đã xác minh" và "Không khớp" ([FingerprintDialog.java:51-77](../client-javafx/src/main/java/com/datn/chatp2p/client/view/FingerprintDialog.java#L51-L77)).
- Màn hình đăng nhập cũng hiện vân tay rút gọn của máy mình ([AuthController.java:105](../client-javafx/src/main/java/com/datn/chatp2p/client/view/AuthController.java#L105)).

**Đáp ứng yêu cầu:**
- **R5:** cuộc gọi hoặc việc gặp mặt là đường mà Minh không kiểm soát.
- **R6:** so một dãy số chung, hoặc nhìn hai mã QR, thay vì đọc hai chuỗi hex dài.

**→ Lỗ hổng:** mỗi lần gặp lại phải so lại từ đầu.

**Phân tích vấn đề:** việc so tốn công: phải gọi điện, đọc 60 chữ số. Nếu buổi nào cũng phải làm, người dùng sẽ bỏ qua, và khi đó Bước 3 vô tác dụng. Mặt khác, cái cần biết thật ra chỉ là "**hình in của Bình có còn là hình in hôm trước mình đã so không**". Câu này máy tự trả lời được nếu nó nhớ.

**Để giải quyết thì cần gì:**
- **R7:** so một lần rồi **ghi nhớ** kết quả. Những lần sau máy tự đối chiếu.
- **R8:** nếu lần sau hình in **khác** cái đã ghi nhớ thì phải **cảnh báo rõ**, vì đó là dấu hiệu có người mạo danh.

---

## Bước 4 — Sổ tay ghi nhớ người đã so

**Làm gì:** sau khi so khớp, An ghi vào sổ tay: "Bình, hình in con dấu có số hiệu này". Lần sau gặp người tên Bình:
- **Số hiệu giống trong sổ:** máy hiện "Đã tin cậy", không cần so lại.
- **Số hiệu khác trong sổ:** máy hiện cảnh báo đỏ. Có thể là người khác cùng tên, hoặc Bình đã đổi máy. Cần so lại trước khi nói chuyện gì quan trọng.
- **Chưa có trong sổ:** máy hiện "Chưa xác minh" và mời so vân tay.

Nếu An bấm "Không khớp" thì máy đánh dấu người này là đáng ngờ trong suốt buổi đó.

**Về kỹ thuật:** sổ tay là file `~/.chat-p2p-java/trusted-peers.properties`, mỗi dòng ghi tên hiển thị kèm vân tay. File chỉ được ghi khi người dùng **chủ động** bấm "Khớp". Có ba trạng thái: `UNKNOWN`, `TRUSTED`, `CHANGED`.

**Trong code:**
- Tra cứu, ghi nhớ, xoá: [TrustedPeers.java:56-79](../client-javafx/src/main/java/com/datn/chatp2p/client/util/TrustedPeers.java#L56-L79). File được ghi ra file tạm rồi mới đổi tên, nên không bị hỏng giữa chừng.
- Cảnh báo "Khoá danh tính của … KHÁC với lần bạn đã xác minh", mỗi người chỉ cảnh báo một lần: [RoomController.java:329-340](../client-javafx/src/main/java/com/datn/chatp2p/client/view/RoomController.java#L329-L340).
- Xử lý hai nút Khớp / Không khớp: [RoomController.java:343-373](../client-javafx/src/main/java/com/datn/chatp2p/client/view/RoomController.java#L343-L373).
- Danh sách người trong phòng hiện các nhãn "Đang xác minh…", "Đã tin cậy", "Khoá đã đổi", "Không khớp – có thể có người xen giữa", và nút "So vân tay" hoặc "So lại vân tay" ([PeerListCell.java:74-141](../client-javafx/src/main/java/com/datn/chatp2p/client/view/PeerListCell.java#L74-L141)).

**Một chi tiết đã sửa trong đợt rà soát bảo mật** ([RoomController.java:360-365](../client-javafx/src/main/java/com/datn/chatp2p/client/view/RoomController.java#L360-L365)):
- Trước đây, bấm "Không khớp" luôn xoá tên đó khỏi sổ tay.
- Nhưng khi hộp thoại được mở vì **khoá đã đổi**, thứ có trong sổ mới là khoá thật, còn khoá đang hiện ra là khoá đáng ngờ.
- Xoá sổ trong trường hợp này là xoá mất bằng chứng. Lần sau kẻ mạo danh quay lại sẽ không còn bị cảnh báo nữa.
- Bây giờ, trường hợp khoá đã đổi thì **giữ nguyên** dòng cũ trong sổ.

**Đáp ứng yêu cầu:**
- **R7:** so một lần, những lần sau máy tự đối chiếu.
- **R8:** khoá khác trong sổ thì có cảnh báo, viền đỏ và nút "So lại vân tay".

**→ Lỗ hổng:** sổ tay chỉ có ý nghĩa nếu **Bình luôn dùng đúng một con dấu**.

**Phân tích vấn đề:**
- Nếu mỗi lần mở ứng dụng máy lại sinh khoá danh tính mới, thì lần nào gặp Bình cũng thấy "Khoá đã đổi". Cảnh báo kêu liên tục, người dùng sẽ quen và bỏ qua, kể cả lúc Minh thật sự mạo danh.
- Vậy khoá danh tính phải được **cất trên máy** để dùng lại. Nhưng con dấu cất trên máy thì có thể bị lấy mất: một phần mềm độc hại, người mượn máy hay bản sao lưu ổ đĩa đều có thể chép file đi. Ai cầm được file đó thì **giả làm Bình mãi mãi**, và sổ tay của bạn bè Bình vẫn hiện "Đã tin cậy".

**Để giải quyết thì cần gì:**
- **R9:** khoá danh tính được lưu lại và **dùng đúng khoá cũ** ở mọi lần mở ứng dụng. File không được hỏng, và không bao giờ bị âm thầm thay bằng khoá mới.
- **R10:** file khoá bị chép sang máy khác hoặc tài khoản khác thì **không dùng được**.
- **R11:** không bắt người dùng nhập thêm mật khẩu mỗi lần mở ứng dụng. Nếu bắt, người ta sẽ đặt mật khẩu yếu hoặc bỏ dùng.

**Xét các phương án:**
- **Lưu khoá dạng thường, chỉ hạn chế quyền đọc file:** đạt R9 và R11 nhưng không đạt R10. Một chương trình chạy dưới tài khoản của người dùng vẫn đọc và chép được file.
- **Mã hoá khoá bằng mật khẩu do người dùng nhập:** đạt R10 nhưng không đạt R11.
- **Dùng chip bảo mật hoặc thẻ thông minh:** an toàn nhất nhưng phức tạp và phụ thuộc phần cứng, quá tầm một đồ án.
- **Nhờ hệ điều hành mã hoá khoá bằng bí mật gắn với tài khoản đăng nhập máy:** đạt R10, vì file chỉ giải mã được trên đúng máy và đúng tài khoản đó. Cũng đạt R11, vì người dùng đã đăng nhập Windows rồi nên không phải nhập gì thêm. Đây là hướng được chọn.

---

## Bước 5 — Cất khoá danh tính an toàn trên máy

**Làm gì:**
- An cất con dấu trong một ngăn tủ chỉ mở được khi chính An đang ở trong chính căn nhà của mình. Ai bê cả ngăn tủ sang nhà khác cũng không mở được.
- Khi cất, An làm cẩn thận: cất vào một ngăn phụ trước, chắc chắn xong mới chuyển sang ngăn chính. Nhờ vậy không bao giờ có lúc con dấu bị dở dang.
- Nếu một ngày ngăn tủ có dấu hiệu bị hỏng, An **không** tự khắc con dấu mới. Làm vậy là mất danh tính cũ, và bạn bè sẽ thấy "Khoá đã đổi". An dừng lại và báo cho người dùng biết.

**Về kỹ thuật:**
- Khoá danh tính lưu ở `~/.chat-p2p-java/identity.properties`.
- Trên Windows, private key được mã hoá bằng **DPAPI** (`CryptProtectData`), gắn với tài khoản Windows đang đăng nhập. File có `version=2`, `protection=dpapi`, `privateKeyProtected=…`.
- File cũ `version=1` lưu private key dạng thường sẽ được **tự động chuyển** sang dạng mã hoá ở lần mở kế tiếp.

**Trong code:**
- Đọc hoặc tạo khoá: [IdentityKeyStore.java:74-93](../crypto/src/main/java/com/datn/chatp2p/crypto/IdentityKeyStore.java#L74-L93).
  - Hai cửa sổ ứng dụng cùng tạo khoá một lúc thì cửa sổ đến sau dùng khoá của cửa sổ đến trước, không ghi đè ([IdentityKeyStore.java:86-89](../crypto/src/main/java/com/datn/chatp2p/crypto/IdentityKeyStore.java#L86-L89)).
- Ghi file tạm rồi đổi tên, không ghi đè: [IdentityKeyStore.java:205-216](../crypto/src/main/java/com/datn/chatp2p/crypto/IdentityKeyStore.java#L205-L216).
- File hỏng thì báo lỗi và không tự sinh khoá mới: [IdentityKeyStore.java:130-164](../crypto/src/main/java/com/datn/chatp2p/crypto/IdentityKeyStore.java#L130-L164), [257-262](../crypto/src/main/java/com/datn/chatp2p/crypto/IdentityKeyStore.java#L257-L262). Bước tự kiểm tra ký thử rồi xác minh lại để chắc public key và private key đúng là một cặp ([IdentityKeyStore.java:243-247](../crypto/src/main/java/com/datn/chatp2p/crypto/IdentityKeyStore.java#L243-L247)).
- Giải mã thất bại thì báo "file chép từ máy hoặc tài khoản Windows khác?": [IdentityKeyStore.java:166-179](../crypto/src/main/java/com/datn/chatp2p/crypto/IdentityKeyStore.java#L166-L179).
- Tự chuyển file `version=1` sang `version=2`: [IdentityKeyStore.java:182-198](../crypto/src/main/java/com/datn/chatp2p/crypto/IdentityKeyStore.java#L182-L198).
- DPAPI qua thư viện JNA: [DpapiKeyProtector.java:19-52](../client-javafx/src/main/java/com/datn/chatp2p/client/util/DpapiKeyProtector.java#L19-L52). Trước khi dùng, nó thử mã hoá rồi giải mã một mẫu nhỏ. Không phải Windows, hoặc nạp DPAPI lỗi, thì trả về `null` và khoá được lưu dạng thường.
- Trên Linux và macOS, file được đặt quyền `600`, chỉ chủ sở hữu đọc ghi được ([IdentityKeyStore.java:237-241](../crypto/src/main/java/com/datn/chatp2p/crypto/IdentityKeyStore.java#L237-L241)).

**Đáp ứng yêu cầu:**
- **R9:** dùng đúng khoá cũ, ghi an toàn, không âm thầm thay khoá.
- **R10:** trên Windows, file chép sang máy khác là vô dụng.
- **R11:** người dùng không phải nhập gì thêm.

**→ Lỗ hổng:** chính điều làm nên R10 lại gây ra vấn đề mới: **đổi máy là mất danh tính**.

**Phân tích vấn đề:** Bình mua máy mới, hoặc cài lại Windows. Khoá cũ không mở được ở đó, nên máy mới sinh khoá mới. Hậu quả:
- Bạn bè của Bình thấy "Khoá đã đổi", y hệt như lúc có người mạo danh.
- Tài khoản của Bình **bị từ chối đăng nhập**. Lần đăng nhập đầu tiên, server đã gắn tài khoản với khoá danh tính của máy cũ, nên khoá khác thì báo "Khóa identity không khớp tài khoản này" ([AuthService.java:78-89](../signaling-server/src/main/java/com/datn/chatp2p/signaling/auth/AuthService.java#L78-L89)).

Cần một cách mang khoá sang máy mới. Nhưng bản sao mang đi lại chính là thứ R10 đang cố ngăn: một file mở được ở máy khác.

**Để giải quyết thì cần gì:**
- **R12:** có bản sao khoá **mở được ở bất kỳ máy nào**, nhưng chỉ khi biết một bí mật mà người nhặt được file không có.
- **R13:** bí mật đó phải chống được việc **đoán thử hàng loạt**, vì kẻ gian cầm file có thể thử mật khẩu trên máy của chúng mãi mãi.
- **R14:** nhập bản sao vào máy mới thì **không làm mất** khoá đang có ở đó, phòng khi nhập nhầm.

---

## Bước 6 — Sao lưu và chuyển danh tính bằng mật khẩu

**Làm gì:**
- Bình bỏ con dấu vào một chiếc hộp khoá bằng mật khẩu do mình đặt, ít nhất 8 ký tự, rồi mang hộp sang nhà mới.
- Ổ khoá được làm sao cho mỗi lần thử một mật khẩu mất khá lâu. Kẻ trộm được hộp muốn thử hàng triệu mật khẩu thì phải chờ rất lâu.
- Ở nhà mới, Bình mở hộp, lấy con dấu ra và cất vào ngăn tủ của nhà mới. Con dấu tạm dùng ở nhà mới trước đó không bị vứt đi mà được cất sang một ngăn riêng.

**Về kỹ thuật:**
- Mật khẩu đi qua PBKDF2-HMAC-SHA256 với **310.000 vòng** và salt ngẫu nhiên 16 byte, cho ra khoá AES-256. Khoá này mã hoá private key bằng AES-GCM.
- Sửa một byte trong file sao lưu thì giải mã thất bại.
- Khi nhập, số vòng ghi trong file phải nằm trong khoảng 100.000 đến 10.000.000. Như vậy file giả không hạ được số vòng xuống để dễ đoán, cũng không đẩy lên để làm treo máy.
- Sau khi giải mã, ứng dụng ký thử rồi xác minh lại để chắc hai khoá đúng là một cặp.

**Trong code:**
- Xuất file sao lưu: [IdentityBackup.java:42-62](../crypto/src/main/java/com/datn/chatp2p/crypto/IdentityBackup.java#L42-L62).
- Nhập file sao lưu: [IdentityBackup.java:69-104](../crypto/src/main/java/com/datn/chatp2p/crypto/IdentityBackup.java#L69-L104). Sai mật khẩu báo "Sai mật khẩu hoặc file đã bị sửa".
- Mật khẩu bị xoá khỏi bộ nhớ sau khi dùng: [IdentityBackup.java:125-129](../crypto/src/main/java/com/datn/chatp2p/crypto/IdentityBackup.java#L125-L129).
- Thay khoá trên máy mới, giữ file cũ thành `identity.properties.old-<thời điểm>`: [IdentityKeyStore.java:104-128](../crypto/src/main/java/com/datn/chatp2p/crypto/IdentityKeyStore.java#L104-L128).
- Nút "Xuất khoá…" và "Nhập khoá…" trong Cài đặt: [SettingsDialog.java:30-85](../client-javafx/src/main/java/com/datn/chatp2p/client/view/SettingsDialog.java#L30-L85), [190-200](../client-javafx/src/main/java/com/datn/chatp2p/client/view/SettingsDialog.java#L190-L200).
- Đăng nhập trên máy mới bị báo khoá không khớp thì màn hình đăng nhập hướng dẫn nhập khoá từ máy cũ: [AuthController.java:30](../client-javafx/src/main/java/com/datn/chatp2p/client/view/AuthController.java#L30), [217-222](../client-javafx/src/main/java/com/datn/chatp2p/client/view/AuthController.java#L217-L222).

**Đáp ứng yêu cầu:**
- **R12:** file mở được ở mọi máy nếu biết mật khẩu.
- **R13:** 310.000 vòng PBKDF2 làm mỗi lần đoán tốn thời gian, và số vòng bị giới hạn khi nhập.
- **R14:** khoá cũ được giữ lại thành file `.old-…`.

**→ Lỗ hổng còn lại:** đến đây, An đã xác định được *đúng người* nếu chịu so vân tay. Còn ba chỗ hở.

**Phân tích vấn đề:**
- **Tin vẫn được gửi trước khi xác thực xong:**
  - Ứng dụng gửi tin cho mọi người đã qua bước vào phòng (`admittedPeerIds`, [P2pChatSession.java:344-346](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L344-L346)), kể cả người còn đang "Đang xác minh…".
  - Khi chữ ký sai, nhánh xử lý chỉ báo lỗi ([P2pChatSession.java:815-819](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L815-L819)). Người đó vẫn ở trong danh sách nhận tin.
  - Phần này đã nêu thành đề xuất ở Bước 10 của [Co-che-ma-hoa-tin-nhan.md](Co-che-ma-hoa-tin-nhan.md), nên không nhắc lại ở đây.
- **Sổ tay ghi theo tên, mà tên của khách thì tự đặt:**
  - Ở chế độ tài khoản, server ép tên hiển thị bằng đúng tên tài khoản ([SignalingWebSocketHandler.java:237](../signaling-server/src/main/java/com/datn/chatp2p/signaling/ws/SignalingWebSocketHandler.java#L237)).
  - Nhưng một khách vẫn có thể tự đặt tên trùng tên tài khoản của người khác, và người trong phòng không phân biệt được ai là tài khoản thật, ai là khách đặt trùng tên.
  - Nếu An đã ghi Bình vào sổ thì vẫn có cảnh báo "Khoá đã đổi". Nhưng nếu An chưa từng so với Bình thì không có gì báo hiệu.
- **Server biết khoá thật của tài khoản nhưng không ai dùng tới:**
  - Server lưu `public_key` của mỗi tài khoản ([AuthService.java:78-89](../signaling-server/src/main/java/com/datn/chatp2p/signaling/auth/AuthService.java#L78-L89)).
  - Nhưng người trong phòng không nhận được thông tin này, nên không đối chiếu được với khoá trong `PEER_IDENTITY`.

**Để giải quyết thì cần gì:**
- **R15:** người trong phòng phải biết một người là **tài khoản đã đăng nhập** hay **khách tự đặt tên**.
- **R16:** với tài khoản đã đăng nhập, máy phải tự đối chiếu khoá danh tính trong `PEER_IDENTITY` với khoá mà server đã gắn cho tài khoản đó. Không khớp thì cảnh báo ngay, kể cả khi An chưa từng so vân tay.

---

## Bước 7 — Đánh dấu tài khoản thật và đối chiếu với khoá đã đăng ký *(làm ngày 22/11)*

**Làm gì:**
- Khi chuyển thư, bưu điện ghi thêm lên phong bì: "người này đã trình giấy tờ ở quầy, là Bình có tài khoản; hình in con dấu đã đăng ký là thế này". Thư của khách vãng lai thì ghi "khách, tên tự xưng".
- An nhận thư thì tự so hình in đăng ký với hình in trên tờ giấy Bình đóng dấu.
- Hai cái khác nhau nghĩa là có người đứng giữa, hoặc bưu điện nói sai. An dừng lại ngay.

**Về kỹ thuật:**
- Khi một người vào phòng, signaling server gửi kèm cho những người khác, trong `PEER_LIST` và `PEER_JOINED`, hai trường:
  - `authenticated`: `true` nếu người đó vào bằng token tài khoản hợp lệ, `false` nếu là khách;
  - `identityKey`: `public_key` đã đăng ký của tài khoản đó.
- Hai trường này chỉ server được đặt. Bản tin `OFFER`, `ANSWER`, `ICE_CANDIDATE` do người khác gửi bị server xoá hai trường này trước khi chuyển tiếp, nên khách không tự nhận là "tài khoản" được.
- Client so khoá trong `PEER_IDENTITY` với `identityKey`, theo byte sau khi giải mã Base64. Không khớp thì **không** coi người đó là đã xác thực, dù chữ ký hợp lệ, và báo "khoá danh tính không khớp khoá đã đăng ký của tài khoản này".
- Danh sách người trong phòng hiện nhãn "Tài khoản" hoặc "Khách". Khách đặt trùng tên một tài khoản đang có trong phòng, kể cả chính mình, thì hiện "Khách trùng tên tài khoản" màu đỏ, kèm một dòng cảnh báo trong khung chat.
- **Tương thích với client cũ đã phát hành.** Client cũ đọc bản tin signaling bằng `ObjectMapper` mặc định: gặp trường lạ là lỗi, mất cả bản tin. Vì vậy:
  - Client mới gửi `protocolVersion = 2` trong `JOIN`.
  - Server chỉ gửi hai trường mới cho client đã khai báo như vậy. Client cũ nhận `PEER_LIST` và `PEER_JOINED` y như trước.
  - Server không ghi các trường `null` ra JSON, và xoá `protocolVersion` khỏi bản tin chuyển tiếp.
  - Client mới cũng được cấu hình bỏ qua trường lạ, để lần sau server thêm trường thì không bị lỗi.
- Server cũ không gửi hai trường này thì client mới không hiện nhãn và không đối chiếu khoá.

**Trong code:**
- Server ghi nhận tài khoản và khoá đăng ký khi JOIN: [SignalingWebSocketHandler.java:251-254](../signaling-server/src/main/java/com/datn/chatp2p/signaling/ws/SignalingWebSocketHandler.java#L251-L254), [PeerSession.java](../signaling-server/src/main/java/com/datn/chatp2p/signaling/room/PeerSession.java).
- Ghi nhận phiên bản client, gửi `PEER_LIST` và `PEER_JOINED` theo phiên bản của từng người nhận: [SignalingWebSocketHandler.java:270-283](../signaling-server/src/main/java/com/datn/chatp2p/signaling/ws/SignalingWebSocketHandler.java#L270-L283), [408-431](../signaling-server/src/main/java/com/datn/chatp2p/signaling/ws/SignalingWebSocketHandler.java#L408-L431).
- Không ghi trường `null`: [SignalingWebSocketHandler.java:124-136](../signaling-server/src/main/java/com/datn/chatp2p/signaling/ws/SignalingWebSocketHandler.java#L124-L136).
- Xoá các trường chỉ server được đặt khi chuyển tiếp: [SignalingWebSocketHandler.java:346-352](../signaling-server/src/main/java/com/datn/chatp2p/signaling/ws/SignalingWebSocketHandler.java#L346-L352).
- Client ghi nhận thông tin từ `PEER_LIST` và `PEER_JOINED`, gắn vào kết nối khi ICE xong: [RoomSession.java:332](../p2p-core/src/main/java/com/datn/chatp2p/p2p/RoomSession.java#L332), [352](../p2p-core/src/main/java/com/datn/chatp2p/p2p/RoomSession.java#L352), [515](../p2p-core/src/main/java/com/datn/chatp2p/p2p/RoomSession.java#L515).
- Đối chiếu khoá: [P2pChatSession.java:792-805](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L792-L805), [823-831](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L823-L831).
- Nhãn "Tài khoản" / "Khách": [PeerListCell.java:146](../client-javafx/src/main/java/com/datn/chatp2p/client/view/PeerListCell.java#L146). Phát hiện khách trùng tên: [RoomController.java:277-308](../client-javafx/src/main/java/com/datn/chatp2p/client/view/RoomController.java#L277-L308).
- Test: `PeerAccountInfoTest` (server, có cả trường hợp client cũ) và `RegisteredIdentityKeyTest` (client).

**Đáp ứng yêu cầu:**
- **R15:** cờ `authenticated` phân biệt tài khoản với khách.
- **R16:** khoá đăng ký giúp phát hiện mạo danh ngay ở lần gặp đầu.

**Giới hạn của bước này:** cách này dựa vào lời của server. Nếu server bị chiếm thì server vẫn nói dối được. Vì vậy **so vân tay ở Bước 3 vẫn là cách mạnh nhất**. Bước 7 chỉ là lớp bảo vệ tự động cho những người chưa so vân tay với nhau.

---

## Những giới hạn còn lại cần nêu khi bảo vệ

| Giới hạn | Vì sao chấp nhận / hướng xử lý |
|---|---|
| Ngoài Windows, khoá danh tính lưu dạng thường với quyền `600` | Chưa có lớp tương đương DPAPI cho macOS Keychain và Linux Secret Service. Có thể thêm một `KeyProtector` mới mà không phải sửa phần còn lại, vì giao diện đã tách riêng ([KeyProtector.java](../crypto/src/main/java/com/datn/chatp2p/crypto/KeyProtector.java)). |
| Sổ tay `trusted-peers.properties` không được mã hoá hay ký | File này không chứa bí mật. Kẻ sửa được nó thì đã chạy được chương trình dưới tài khoản người dùng, và khi đó còn làm được nhiều việc tệ hơn. |
| Không thu hồi được khoá bị lộ | Lộ khoá thì phải sinh khoá mới, rồi bạn bè so lại vân tay. Một cơ chế thu hồi tập trung lại cần tin vào server. |
| Lần gặp đầu không so vân tay thì không chống được người đứng giữa | Đây là giới hạn chung của mọi ứng dụng mã hoá đầu cuối, kể cả Signal và WhatsApp. Giao diện luôn hiện "Chưa xác minh" và nút "So vân tay" để nhắc người dùng. |

---

## Tóm tắt chuỗi thiết kế

| Bước | Làm gì | Vá được gì | Sinh ra vấn đề gì |
|---|---|---|---|
| 0 | Tin tên tự xưng | — | Ai cũng xưng được tên, Minh đứng giữa được |
| 1 | Chữ ký ECDSA bằng khoá danh tính | Chỉ người có khoá mới ký được | Chữ ký giống hệt mỗi lần, Minh chuyển tiếp lại được |
| 2 | Ký kèm vân tay hai khoá ECDH của phiên | Chữ ký gắn với đúng lần trao khoá | Minh dùng khoá của chính Minh, chữ ký vẫn hợp lệ |
| 3 | Vân tay và mã an toàn 60 chữ số kèm QR | Nối khoá với người thật qua đường khác | Lần nào cũng phải so lại |
| 4 | Sổ tay `TrustedPeers` và cảnh báo khoá đã đổi | So một lần, máy tự nhớ | Cần giữ đúng một khoá, mà file khoá thì có thể bị chép |
| 5 | Lưu khoá bằng DPAPI, ghi an toàn, không tự ghi đè | File chép sang máy khác là vô dụng | Đổi máy là mất danh tính, không đăng nhập được |
| 6 | Sao lưu bằng mật khẩu, PBKDF2 310.000 vòng, AES-GCM | Chuyển danh tính sang máy mới an toàn | Tên khách trùng tên tài khoản; khoá trên server chưa được dùng |
| 7 | Cờ "đã đăng nhập" và đối chiếu khoá đã đăng ký | Phát hiện mạo danh ngay cả khi chưa so vân tay | Vẫn phải tin server, nên so vân tay vẫn cần |

```
B0 tin tên tự xưng
 ──ai cũng xưng được──────────────► B1 ký bằng khoá danh tính
 ──chữ ký dùng lại được───────────► B2 ký kèm khoá ECDH của phiên
 ──Minh dùng khoá của chính Minh──► B3 so vân tay qua đường khác
 ──lần nào cũng phải so───────────► B4 sổ tay + cảnh báo khoá đã đổi
 ──cần giữ khoá, file bị chép─────► B5 DPAPI, ghi an toàn
 ──đổi máy mất danh tính──────────► B6 sao lưu bằng mật khẩu
 ──khách trùng tên tài khoản──────► B7 cờ tài khoản + khoá đăng ký
```
