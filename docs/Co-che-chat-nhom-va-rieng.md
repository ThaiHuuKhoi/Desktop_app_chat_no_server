# Cơ chế chat nhóm, chat riêng và các tương tác trong cuộc trò chuyện

Tài liệu này trả lời câu hỏi: **trong một phòng P2P không có server giữ tin nhắn, làm sao gửi được tin cho cả nhóm, nói riêng với một người, và có đủ những thứ người dùng quen thấy: đang gõ, thả cảm xúc, sửa và xoá tin?**

Phần "tin có tới nơi hay không" đã có trong [Co-che-gui-tin-tin-cay.md](Co-che-gui-tin-tin-cay.md). Tài liệu này chỉ bàn chuyện **gửi cho ai** và **gửi cái gì**.

Cách trình bày giống các tài liệu cơ chế khác: đi từ cách ngây thơ, tìm lỗ hổng, phân tích cần gì, rồi sinh ra bước tiếp theo. Ở mỗi chỗ phải chọn có so sánh các phương án.

Nhân vật:
- **An**, **Bình**, **Chi**: ba người trong cùng một nhóm.
- **Minh**: một thành viên khác trong nhóm, nhưng có ý xấu.

---

## Bước 0 — Gửi tin cho cả nhóm

**Làm gì:** An muốn nói với cả nhóm. An không có một tấm bảng chung để dán thư, vì không có ai giữ bảng. An viết một lá thư rồi tự chép ra nhiều bản, gửi riêng cho từng người qua đường riêng giữa An và người đó. Đường của mỗi cặp đã có khoá riêng.

**Về kỹ thuật:**
- Mỗi cặp người trong phòng có một kết nối P2P riêng với cặp khoá AES-GCM riêng. Đây là mạng lưới đầy đủ (mesh): ai cũng nối trực tiếp với tất cả những người còn lại.
- Gửi tin nhóm là mã hoá và gửi lần lượt qua từng kết nối của cặp.
- Mỗi phòng tối đa 8 người.

**Trong code:**
- Gửi tin nhóm cho từng người đã được nhận vào phòng: [P2pChatSession.java:196-219](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L196-L219).
- Giới hạn 8 người mỗi phòng: [RoomRegistry.java:25](../signaling-server/src/main/java/com/datn/chatp2p/signaling/room/RoomRegistry.java#L25).

**Xét các phương án gửi cho nhóm:**
- **Gửi một bản lên server để server phát lại cho mọi người:** nhẹ cho người gửi. Nhưng server trở thành trạm trung chuyển nội dung, điều mà kiến trúc P2P muốn tránh. Muốn server không đọc được thì cả nhóm phải có chung một khoá, kéo theo bài toán chia khoá ở phương án sau.
- **Cả nhóm dùng chung một khoá nhóm:** mã hoá một lần, gửi một bản. Nhưng mỗi khi có người vào hoặc rời, phải đổi khoá và chia lại cho mọi người. Nếu không, người đã rời vẫn đọc được tin mới. Cách này phức tạp, như giao thức Sender Keys của Signal hay MLS.
- **Gửi riêng cho từng người qua kết nối của cặp:** tận dụng đúng thứ đã có. Người rời phòng thì kết nối của cặp bị đóng, tự nhiên không nhận được gì nữa. Cái giá là người gửi tốn thêm công khi nhóm đông. Với tối đa 8 người, mỗi tin chỉ phải gửi 7 bản, chấp nhận được. Đây là hướng được chọn.

**Đáp ứng yêu cầu:** tin tới được mọi người mà không qua server. Người rời phòng tự động không nhận được tin mới.

**→ Lỗ hổng:** chưa có cách **nói riêng** với một người trong phòng.

**Phân tích vấn đề:**
- An muốn hỏi riêng Bình một câu mà không để Chi và Minh biết.
- Nếu phải lập một phòng mới chỉ có hai người thì rườm rà: phải đặt tên phòng, chờ kết nối lại từ đầu, và rời khỏi cuộc trò chuyện nhóm.
- Hai người đã có sẵn một đường riêng có khoá giữa An và Bình rồi.

**Để giải quyết thì cần gì:**
- **R1:** tin riêng chỉ đi tới **đúng một người**, không ai khác trong phòng nhận được.
- **R2:** người ngoài, kể cả server, **không phân biệt được** đâu là tin riêng, đâu là tin nhóm.
- **R3:** tin riêng **không được lọt vào lịch sử nhóm**. Lịch sử nhóm sẽ được gửi cho người vào sau (xem [Co-che-lich-su-tin-nhan.md](Co-che-lich-su-tin-nhan.md)).

---

## Bước 1 — Tin nhắn riêng dùng chung đường của cặp

**Làm gì:**
- An viết thư cho Bình và ghi ở góc trong thư: "thư riêng". Rồi An chỉ gửi qua đường An–Bình.
- Chữ "thư riêng" nằm **bên trong** phong bì đã khoá. Người đưa thư nhìn bên ngoài thấy thư riêng và thư nhóm giống hệt nhau.
- Bình đọc thấy chữ "thư riêng" thì để thư sang một ngăn riêng mang tên An.
- Thư riêng không bao giờ được chép vào cuốn sổ chung của nhóm.

**Về kỹ thuật:**
- Envelope có trường `namespace`. Tin riêng mang `namespace = "dm"`.
- Trường này nằm trong phần đã mã hoá AES-GCM, nên server và người ngoài không biết.
- Tin riêng chỉ gửi cho đúng một `peerId`, và người đó phải đã được nhận vào phòng.
- Giao diện mở một tab riêng cho cuộc trò chuyện với người đó.

**Trong code:**
- Gửi tin riêng: [P2pChatSession.java:221-235](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L221-L235).
- Định nghĩa namespace: [EnvelopeNamespace.java:18](../common/src/main/java/com/datn/chatp2p/common/protocol/EnvelopeNamespace.java#L18).
- Bên nhận phân loại theo namespace. Chỉ tin nhóm được thêm vào `GroupHistory`: [P2pChatSession.java:499-511](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L499-L511).
- Mở tab riêng: [ChatTranscriptManager.java:160-167](../client-javafx/src/main/java/com/datn/chatp2p/client/view/ChatTranscriptManager.java#L160-L167).

**Đáp ứng yêu cầu:**
- **R1:** chỉ gửi qua đường của đúng cặp đó.
- **R2:** dấu "riêng" nằm trong phần đã mã hoá.
- **R3:** chỉ tin nhóm mới vào lịch sử nhóm.

**→ Lỗ hổng:** bên nhận đang tin vào những gì **người gửi tự khai** trong thư.

**Phân tích vấn đề:** mỗi tin có các trường `authorId` (ai viết) và `timeSent` (gửi lúc nào), do máy người gửi tự điền.
- Minh có thể sửa ứng dụng để điền `authorId` là của Bình. Trên máy An, tin của Minh sẽ hiện như Bình nói.
- Minh có thể điền `timeSent` vào năm 2099, để tin của mình luôn nằm cuối khi sắp xếp theo thời gian.
- Khoá của cặp An–Minh chỉ chứng minh được một điều: **thư đến từ đường An–Minh**. Nó không chứng minh nội dung bên trong là thật.

**Để giải quyết thì cần gì:**
- **R4:** người viết tin phải được xác định bằng **thứ mà mật mã đã chứng minh**, tức là kết nối nào chở tin đó, chứ không phải trường tự khai.
- **R5:** các giá trị tự khai mà không kiểm chứng được, như thời gian, phải được **giới hạn** trong khoảng hợp lý.

---

## Bước 2 — Người viết là người ở đầu kia đường truyền

**Làm gì:**
- Thư đi qua đường An–Minh thì An coi đó là thư của Minh, bất kể trong thư ghi tên ai.
- Thư ghi ngày gửi ở tương lai thì An sửa lại thành "ngay bây giờ".

**Về kỹ thuật:**
- Khi nhận tin, `authorId` được gán bằng `peerId` của kết nối chở tin, bỏ qua giá trị trong payload.
- `timeSent = min(timeSent, now)`.
- Áp dụng cho cả tin trực tiếp, sửa/xoá tin và lịch sử chia sẻ. Riêng lịch sử chia sẻ là tin của người khác do người gửi chuyển lại, nên có thêm quy tắc riêng: không chấp nhận tin gán cho chính mình (xem [Co-che-lich-su-tin-nhan.md](Co-che-lich-su-tin-nhan.md)).

**Trong code:**
- Gán người viết và chặn thời gian ở tương lai: [P2pChatSession.java:493-497](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L493-L497).

**Đáp ứng yêu cầu:**
- **R4:** người viết luôn là người ở đầu kia kết nối đã mã hoá.
- **R5:** không có tin nào có thời gian ở tương lai.

**→ Lỗ hổng:** cuộc trò chuyện vẫn "câm" ở khoảng giữa hai tin nhắn.

**Phân tích vấn đề:**
- An hỏi một câu rồi chờ. An không biết Bình đã thấy chưa, có đang trả lời không.
- Phần "đã nhận / đã xem" ở [Co-che-gui-tin-tin-cay.md](Co-che-gui-tin-tin-cay.md) trả lời được câu "đã thấy chưa". Còn câu "đang trả lời không" thì chưa.
- Nhưng tín hiệu "đang gõ" có tính chất rất khác tin nhắn. Nó thay đổi liên tục theo từng phím bấm. Mất một lần cũng không sao. Và nó **phải tự tắt** nếu người gõ đột ngột mất mạng, nếu không màn hình sẽ hiện "Bình đang gõ…" mãi mãi.

**Để giải quyết thì cần gì:**
- **R6:** báo được "đang gõ" mà **không gửi theo từng phím bấm**.
- **R7:** tín hiệu **tự hết hạn** nếu không được nhắc lại, phòng khi tín hiệu "đã ngừng gõ" bị mất.
- **R8:** không tốn công gửi lại hay ký nhận cho loại tín hiệu này.

---

## Bước 3 — Báo "đang gõ"

**Làm gì:**
- Bình gõ phím đầu tiên thì máy Bình báo "đang gõ".
- Bình gõ tiếp thì cứ 2,5 giây máy nhắc lại một lần.
- Bình ngừng gõ 3 giây, xoá hết chữ, bấm gửi hay chuyển sang cuộc trò chuyện khác thì máy báo "đã ngừng".
- Phía An, nếu 6 giây không nghe nhắc lại thì tự coi là Bình đã ngừng. Bình gửi tin thì cũng tắt ngay dòng "đang gõ".
- Nhiều người cùng gõ thì hiện "An và Bình đang gõ…", hay "An, Bình và 2 người khác đang gõ…".

**Về kỹ thuật:** bản tin `TYPING_STATUS_CHANGE(typing)` được gửi kiểu cố gắng hết sức: không ACK, không gửi lại, không báo lỗi. Tín hiệu "đang gõ" trong tab riêng mang `namespace = "dm"` nên chỉ hiện ở tab riêng.

**Trong code:**
- Bên gửi: nhắc lại sau 2,5 giây, ngừng sau 3 giây không gõ: [TypingNotifier.java:20-21](../client-javafx/src/main/java/com/datn/chatp2p/client/view/TypingNotifier.java#L20-L21), [42-73](../client-javafx/src/main/java/com/datn/chatp2p/client/view/TypingNotifier.java#L42-L73).
- Bên nhận: hết hạn sau 6 giây, xoá khi người đó gửi tin: [TypingTracker.java:17](../client-javafx/src/main/java/com/datn/chatp2p/client/view/TypingTracker.java#L17), [30-48](../client-javafx/src/main/java/com/datn/chatp2p/client/view/TypingTracker.java#L30-L48).
- Gửi không ACK, không gửi lại: [P2pChatSession.java:252-269](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L252-L269).

**Đáp ứng yêu cầu:**
- **R6:** nhắc lại tối đa mỗi 2,5 giây.
- **R7:** tự hết hạn sau 6 giây.
- **R8:** không ACK, không gửi lại.

**→ Lỗ hổng:** nhiều khi người ta chỉ muốn **đáp lại nhanh** một tin, không cần gõ cả câu.

**Phân tích vấn đề:**
- Gõ "ok", "haha" làm cuộc trò chuyện dài ra và che mất những tin quan trọng.
- Người dùng quen thả cảm xúc ngay lên tin của người khác.
- Nhưng nếu cho thả **bất kỳ chuỗi nào** thì Minh có thể chèn một chuỗi dài vào giao diện của mọi người, đặt dưới tin của người khác như thể đó là một phần của tin.

**Để giải quyết thì cần gì:**
- **R9:** thả và bỏ được cảm xúc trên một tin cụ thể.
- **R10:** chỉ cho phép một **bộ cảm xúc cố định**, để không ai chèn được nội dung tuỳ ý vào giao diện.
- **R11:** người rời phòng thì cảm xúc của họ cũng biến mất, để con số luôn khớp với những người đang có mặt.

---

## Bước 4 — Thả cảm xúc

**Làm gì:**
- Dưới mỗi tin có 5 biểu tượng: 👍 ❤️ 😂 😮 😢. Bấm một lần là thả, bấm lần nữa là bỏ.
- Mỗi máy ghi: tin này, biểu tượng này, những ai đã thả. Rê chuột vào thì hiện danh sách tên.
- Thư báo thả cảm xúc mang biểu tượng không nằm trong bộ 5 thì bị bỏ qua.
- Ai rời phòng thì xoá cảm xúc của người đó.

**Về kỹ thuật:**
- Bản tin `MESSAGE_REACTION(messageId, emoji, added)`, gửi kiểu cố gắng hết sức.
- Bộ cho phép là `ReactionPayload.ALLOWED`. Cả bên gửi lẫn bên nhận đều kiểm tra biểu tượng có nằm trong bộ này không.
- Mỗi người chỉ được tính một lần cho mỗi biểu tượng trên mỗi tin, vì danh sách người thả là một tập hợp.

**Trong code:**
- Bộ 5 biểu tượng: [ReactionPayload.java:12](../common/src/main/java/com/datn/chatp2p/common/protocol/ReactionPayload.java#L12).
- Gửi và nhận, có kiểm tra bộ cho phép: [P2pChatSession.java:546-562](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L546-L562), [652-668](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L652-L668).
- Lưu cảm xúc theo tin, biểu tượng và người thả, xoá khi người đó rời phòng: [ReactionStore.java:17-60](../client-javafx/src/main/java/com/datn/chatp2p/client/view/ReactionStore.java#L17-L60).

**Đáp ứng yêu cầu:**
- **R9:** thả và bỏ được.
- **R10:** chỉ 5 biểu tượng cố định.
- **R11:** người rời phòng thì cảm xúc của họ bị xoá.

**→ Lỗ hổng:** gửi đi rồi thì **không sửa được**, kể cả khi gõ nhầm hay gửi nhầm.

**Phân tích vấn đề:**
- Tin đã nằm trên máy của từng người. Không có server nào giữ bản gốc để sửa hộ.
- Muốn sửa thì phải gửi tới **từng máy** một yêu cầu sửa, và yêu cầu này phải tới nơi chắc chắn. Nếu không, các máy sẽ hiện những nội dung khác nhau.
- Nguy hiểm hơn: ai được quyền sửa? Mã số tin do máy người gửi tự đặt. Minh có thể gửi yêu cầu "sửa tin mã số X" với X là mã số một tin của Bình, để đổi lời Bình trên máy An.
- Lịch sử nhóm, thứ sẽ gửi cho người vào sau, cũng phải theo bản đã sửa, nếu không người mới vẫn thấy bản cũ.

**Để giải quyết thì cần gì:**
- **R12:** yêu cầu sửa hoặc xoá phải **tới nơi chắc chắn** và **không bị áp dụng hai lần**.
- **R13:** **chỉ tác giả** mới sửa hoặc xoá được tin của chính mình. Việc này được kiểm tra trên máy người nhận, theo đúng quy tắc ở Bước 2.
- **R14:** lịch sử nhóm phải được cập nhật theo bản sửa.

---

## Bước 5 — Sửa và xoá tin

**Làm gì:**
- An bấm "Sửa" trên tin của mình và gõ lại nội dung. Máy An sửa ngay trên màn hình mình, rồi gửi cho từng người một tờ "sửa thư mã số X thành nội dung mới". Tờ này có mã số riêng và cần được ký nhận như thư thường.
- Bên nhận chỉ sửa khi **thư X đúng là của người gửi tờ sửa**. Minh gửi tờ "sửa thư X" mà X là thư của Bình thì không có gì thay đổi.
- Thư bị sửa hiện thêm chữ "đã sửa". Thư bị xoá hiện "Tin nhắn đã bị xoá".
- Cuốn sổ lịch sử chung của nhóm cũng được sửa hoặc xoá theo.

**Về kỹ thuật:**
- Bản tin `MESSAGE_EDIT(editId, messageId, text, deleted)`.
- `editId` là UUID riêng cho mỗi lần sửa, dùng cho ACK, gửi lại và loại trùng, giống hệt tin nhắn.
- Bên nhận tìm tin theo **cặp** `(messageId, tác giả)`, trong đó tác giả là `peerId` của kết nối chở bản tin sửa. Không tìm theo `messageId` đơn thuần.
- Nội dung mới cũng bị giới hạn 12.000 ký tự như tin thường.

**Trong code:**
- Gửi sửa/xoá có ACK, cập nhật lịch sử nhóm của chính mình: [P2pChatSession.java:586-618](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L586-L618).
- Nhận sửa/xoá: ACK, loại trùng theo `editId`, chỉ sửa lịch sử khi đúng tác giả: [P2pChatSession.java:620-650](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L620-L650).
- Lịch sử nhóm chỉ đổi tin có `authorId` khớp: [GroupHistory.java:51-71](../client-javafx/src/main/java/com/datn/chatp2p/client/session/GroupHistory.java#L51-L71).
- Giao diện tìm theo cặp `(id, tác giả)`: [RoomController.java:532-543](../client-javafx/src/main/java/com/datn/chatp2p/client/view/RoomController.java#L532-L543). Đây là chỗ đã sửa trong đợt rà soát bảo mật: trước đây giao diện tìm theo `id` và áp dụng cho **mọi** tin trùng `id`, nên một người gửi trùng mã số có thể sửa tin của người khác trên máy mình.
- Hiện "đã sửa" và "Tin nhắn đã bị xoá": [TranscriptCell.java:256](../client-javafx/src/main/java/com/datn/chatp2p/client/view/TranscriptCell.java#L256), [265](../client-javafx/src/main/java/com/datn/chatp2p/client/view/TranscriptCell.java#L265).

**Đáp ứng yêu cầu:**
- **R12:** có ACK, gửi lại và loại trùng theo `editId`.
- **R13:** chỉ áp dụng khi đúng tác giả, kiểm tra theo kết nối chở bản tin.
- **R14:** `GroupHistory` được sửa hoặc xoá theo.

**→ Lỗ hổng còn lại:** "xoá" ở đây chỉ là **một lời đề nghị**, không phải xoá thật.

**Phân tích vấn đề:**
- **Người đang không có mặt:** Chi đã rời phòng lúc An xoá tin. Tờ "xoá thư X" không bao giờ tới Chi. Nếu Chi có bật lưu lịch sử trên máy thì tin vẫn còn nguyên trong đó.
- **Người có ý xấu:** máy của Minh có thể đơn giản là bỏ qua tờ "xoá". Một khi tin đã tới máy người khác, không có cách kỹ thuật nào buộc họ quên nó, chưa kể chụp màn hình.
- **Ngay trên máy làm đúng:** tin bị xoá chỉ bị **ẩn** trên giao diện. Nội dung gốc vẫn nằm trong bộ nhớ của phiên ([RoomController.java:556-563](../client-javafx/src/main/java/com/datn/chatp2p/client/view/RoomController.java#L556-L563)).
- **Cảm xúc không được bảo đảm tới nơi:** mất một gói là máy An đếm 3 lượt 👍, máy Bình đếm 2. Không có cách nào đồng bộ lại.
- **Người dùng có thể hiểu sai:** họ dễ nghĩ "xoá" là thu hồi hoàn toàn, giống một số ứng dụng có server.

**Để giải quyết thì cần gì:**
- **R15:** người dùng phải **được báo đúng** xoá làm được gì và không làm được gì.
- **R16:** tin đã xoá thì **không được còn nội dung** trên những máy làm đúng quy tắc: cả trong bộ nhớ lẫn trong lịch sử lưu trên máy.
- **R17:** trạng thái cảm xúc **tự khớp lại** được giữa các máy.

---

## Bước 6 — *(đề xuất, chưa làm)* Nói rõ giới hạn của xoá, xoá sạch nội dung, đồng bộ cảm xúc

**Làm gì:**
- Khi An bấm xoá, máy hỏi lại: "Tin sẽ bị xoá trên máy những người đang ở trong phòng. Người đã rời phòng hoặc đã lưu lịch sử có thể vẫn còn giữ tin."
- Máy làm đúng quy tắc thì xoá hẳn nội dung, chỉ giữ lại dòng "Tin nhắn đã bị xoá".
- Khi có người mới vào hoặc kết nối lại, các máy gửi cho nhau bảng cảm xúc hiện tại để khớp lại.

**Về kỹ thuật:**
- Hộp thoại xác nhận trước khi xoá.
- `applyEdit` thay `text` bằng chuỗi rỗng khi xoá, thay vì giữ nguyên `original.getText()`.
- Xoá tin tương ứng trong lịch sử lưu trên máy (xem [Co-che-lich-su-tin-nhan.md](Co-che-lich-su-tin-nhan.md)).
- Gửi kèm trạng thái cảm xúc trong gói lịch sử, hoặc thêm một bản tin đồng bộ trạng thái nhẹ.

**Trong code (dự kiến):** `RoomController.applyOwnEdit` thêm bước xác nhận; `RoomController.applyEdit` bỏ nội dung khi xoá; `HistoryMessage` hoặc `MessageTranscriptPayload` mang thêm trạng thái cảm xúc.

**Đáp ứng yêu cầu:**
- **R15:** người dùng biết đúng giới hạn.
- **R16:** máy làm đúng không còn giữ nội dung đã xoá.
- **R17:** cảm xúc khớp lại khi có người vào hoặc kết nối lại.

---

## Những giới hạn còn lại cần nêu khi bảo vệ

| Giới hạn | Giải thích |
|---|---|
| Tối đa 8 người mỗi phòng | Mỗi người giữ 7 kết nối và gửi 7 bản cho mỗi tin. Đông hơn thì nên chuyển sang khoá nhóm (Sender Keys, MLS), đổi lại là phải xử lý đổi khoá khi có người vào hoặc ra. |
| Thành viên ác ý không bị buộc phải xoá tin | Giới hạn chung của mọi ứng dụng mã hoá đầu cuối: nội dung đã tới máy người khác thì không thu hồi được bằng kỹ thuật. |
| Trả lời tin (`replyToId`) chỉ là con trỏ tới mã số tin | Nếu tin gốc không có trên máy người nhận, thanh trích dẫn không hiện được nội dung. |
| Tin riêng không vào lịch sử nhóm | Có chủ đích. Người vào sau không bao giờ thấy tin riêng của người khác. |

---

## Tóm tắt chuỗi thiết kế

| Bước | Làm gì | Vá được gì | Sinh ra vấn đề gì |
|---|---|---|---|
| 0 | Gửi tin nhóm riêng cho từng người qua kết nối của cặp | Không qua server, người rời tự động không nhận | Không nói riêng được |
| 1 | Tin riêng: `namespace = "dm"` trong phần đã mã hoá | Riêng tư, không vào lịch sử nhóm | Tin trường tự khai của người gửi |
| 2 | Người viết = `peerId` của kết nối, thời gian không ở tương lai | Không mạo danh người viết được | Cuộc trò chuyện câm giữa hai tin |
| 3 | "Đang gõ": nhắc lại 2,5 giây, hết hạn 6 giây, không ACK | Biết ai đang trả lời | Muốn đáp nhanh không cần gõ |
| 4 | Cảm xúc: bộ 5 biểu tượng cố định | Đáp nhanh, không chèn được nội dung tuỳ ý | Gửi rồi không sửa được |
| 5 | Sửa/xoá: `editId` có ACK, chỉ tác giả, cập nhật lịch sử | Sửa được, không sửa hộ người khác được | Xoá chỉ là đề nghị; cảm xúc có thể lệch |
| 6 *(đề xuất)* | Cảnh báo giới hạn của xoá, xoá sạch nội dung, đồng bộ cảm xúc | Người dùng hiểu đúng, máy làm đúng không còn nội dung | — |

```
B0 gửi nhóm qua kết nối từng cặp
 ──không nói riêng được──────────► B1 tin riêng namespace dm
 ──tin trường tự khai────────────► B2 người viết = kết nối chở tin
 ──câm giữa hai tin──────────────► B3 đang gõ, tự hết hạn
 ──muốn đáp nhanh────────────────► B4 cảm xúc, bộ cố định
 ──gửi rồi không sửa được────────► B5 sửa/xoá, chỉ tác giả
 ──xoá chỉ là đề nghị────────────► B6 nói rõ + xoá sạch + đồng bộ (đề xuất)
```
