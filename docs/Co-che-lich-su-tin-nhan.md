# Cơ chế lịch sử tin nhắn: cho người mới vào và lưu trên máy

Tài liệu này trả lời hai câu hỏi:
1. **Không có server giữ tin nhắn, người vào phòng sau lấy đâu ra những gì đã nói trước đó?**
2. **Đóng ứng dụng rồi mở lại, làm sao vẫn thấy tin cũ mà không để lộ ra cho người khác dùng chung máy?**

Cách trình bày giống các tài liệu cơ chế khác: đi từ cách ngây thơ, tìm lỗ hổng, phân tích cần gì, rồi sinh ra bước tiếp theo. Ở mỗi chỗ phải chọn có so sánh các phương án.

Nhân vật:
- **An** và **Bình** đã nói chuyện trong nhóm từ trước.
- **Chi** vào nhóm sau.
- **Minh** là một thành viên có ý xấu.

---

## Bước 0 — Tin chỉ nằm trên màn hình của người đang có mặt

**Làm gì:** An và Bình nói chuyện. Chi vào sau và thấy một căn phòng trống trơn. Muốn biết mọi người vừa bàn gì, Chi phải hỏi lại.

**Về kỹ thuật:** tin nhắn đi thẳng giữa các máy qua kết nối P2P. Server chỉ làm signaling, không bao giờ nhận nội dung tin, nên không có chỗ nào lưu tin tập trung.

**Đáp ứng yêu cầu:** riêng tư tối đa. Không có bản sao nào nằm trên server.

**→ Lỗ hổng:** người vào sau hoàn toàn mất ngữ cảnh.

**Phân tích vấn đề:**
- Một nhóm làm việc thường có người vào muộn, hoặc rớt mạng rồi vào lại.
- Mỗi lần như vậy mà phải hỏi lại "nãy giờ nói gì?" thì ứng dụng gần như không dùng được cho nhóm.
- Nhưng giữ lịch sử ở đâu? Không có server, thì chỉ có thể là trên máy của những người đang ở trong phòng.

**Để giải quyết thì cần gì:**
- **R1:** người mới vào **nhận được** các tin nhóm trước đó.
- **R2:** không đưa nội dung tin lên server.
- **R3:** tin riêng giữa hai người **không bao giờ** được gửi cho người thứ ba.

**Xét các phương án:**
- **Server lưu lịch sử đã mã hoá:** server không đọc được, nhưng cả nhóm phải có chung một khoá để người mới giải mã được. Như vậy là phải xử lý chia khoá và đổi khoá khi có người vào hoặc ra. Hơn nữa, như thế là đi ngược tinh thần không lưu gì trên server.
- **Người mới chủ động hỏi xin lịch sử:** người mới phải biết hỏi ai, hỏi từ mốc nào, và phải xử lý khi người được hỏi không trả lời.
- **Người cũ chủ động gửi lịch sử cho người mới ngay khi xác thực xong, và ai cũng gửi:** đơn giản, không cần biết ai là "chủ phòng". Hai người cũ kết nối lại thì bù cho nhau phần còn thiếu. Đây là hướng được chọn.

---

## Bước 1 — Người cũ gửi lịch sử nhóm cho người mới

**Làm gì:**
- Mỗi người giữ một cuốn sổ chép lại 200 tin nhóm gần nhất, gồm cả tin mình viết lẫn tin nhận được. Thư riêng không được chép vào sổ.
- Khi Chi vào phòng và đã được kiểm tra danh tính xong, An và Bình mỗi người gửi cho Chi bản chép cuốn sổ của mình.
- Sổ dày thì tách thành nhiều tập nhỏ. Mỗi tập có mã số riêng và cần được ký nhận như thư thường.
- Chi nhận hai bản gần giống nhau từ An và Bình. Tin nào đã có, theo mã số tin, thì Chi bỏ qua. Chi xếp tin theo thời gian gửi.

**Về kỹ thuật:**
- `GroupHistory` là một danh sách trong RAM, giữ tối đa 200 tin nhóm, sắp theo `timeSent`.
- Khi xác thực danh tính một người lần đầu trong kết nối, máy gửi cho người đó bản tin `MESSAGE_TRANSCRIPT` gồm các gói tối đa 6.000 ký tự. Mỗi gói có `batchId` riêng, có ACK và gửi lại như tin nhắn.
- Bên nhận loại trùng theo `id` tin, rồi chèn vào giao diện theo thời gian gửi.

**Trong code:**
- Sổ lịch sử, giới hạn 200 tin, chia gói 6.000 ký tự: [GroupHistory.java:18-20](../client-javafx/src/main/java/com/datn/chatp2p/client/session/GroupHistory.java#L18-L20), [31-44](../client-javafx/src/main/java/com/datn/chatp2p/client/session/GroupHistory.java#L31-L44), [89-107](../client-javafx/src/main/java/com/datn/chatp2p/client/session/GroupHistory.java#L89-L107).
- Tin nhóm của mình được thêm vào sổ khi gửi: [P2pChatSession.java:203-204](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L203-L204). Tin nhận được thì chỉ thêm khi là tin nhóm: [P2pChatSession.java:499-502](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L499-L502).
- Gửi lịch sử ngay sau lần xác thực danh tính đầu tiên: [P2pChatSession.java:835-840](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L835-L840), [689-708](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L689-L708).
- Nhận lịch sử: [P2pChatSession.java:710-771](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L710-L771).
- Chèn vào giao diện theo thời gian, bỏ tin đã có: [ChatTranscriptManager.java:127-151](../client-javafx/src/main/java/com/datn/chatp2p/client/view/ChatTranscriptManager.java#L127-L151).

**Đáp ứng yêu cầu:**
- **R1:** người mới nhận được tối đa 200 tin nhóm gần nhất.
- **R2:** lịch sử đi qua kết nối P2P đã mã hoá, không qua server.
- **R3:** tin riêng không bao giờ vào `GroupHistory`.

**→ Lỗ hổng:** Chi đang **tin hoàn toàn vào người chia sẻ**.

**Phân tích vấn đề:** khác với tin nhắn trực tiếp, nơi người viết chính là người ở đầu kia kết nối (xem [Co-che-chat-nhom-va-rieng.md](Co-che-chat-nhom-va-rieng.md), Bước 2), mỗi tin trong lịch sử là tin **của người khác** do người chia sẻ chép lại. Minh là người chia sẻ thì Minh có thể:
- **Bịa lời người khác:** thêm tin "Bình: chuyển tiền vào tài khoản này nhé". Chi không có cách nào biết Bình chưa từng nói câu đó.
- **Bịa lời chính Chi:** thêm tin gán cho Chi. Trên màn hình, tin đó hiện như do Chi tự viết. Nếu Chi bật lưu lịch sử, nó còn được lưu thành "tin của tôi".
- **Làm ngập:** gửi hàng nghìn tin rác để làm chậm và chiếm bộ nhớ máy Chi.
- **Làm sống lại tin đã xoá:** gửi lại bản cũ của một tin mà tác giả đã xoá.
- **Gửi trước khi được kiểm tra danh tính:** lịch sử đến từ một người chưa biết là ai.

**Để giải quyết thì cần gì:**
- **R4:** chỉ trao đổi lịch sử với người **đã xác thực danh tính**.
- **R5:** **giới hạn số tin** mỗi người được chia sẻ.
- **R6:** **không chấp nhận** tin trong lịch sử gán cho chính người nhận.
- **R7:** tin đã bị xoá thì **không được thêm lại** từ lịch sử của người khác.
- **R8:** người dùng phải **thấy rõ** tin nào đến từ lịch sử chia sẻ, chưa được kiểm chứng người viết.

---

## Bước 2 — Giới hạn những gì người chia sẻ làm được

**Làm gì:**
- An chỉ gửi sổ cho Chi sau khi đã kiểm tra danh tính Chi. Chi cũng chỉ nhận sổ từ người đã kiểm tra danh tính. Nhận sớm quá thì Chi không ký nhận, để người gửi gửi lại sau.
- Mỗi người chỉ được chia sẻ tối đa 200 tin mỗi lần kết nối. Quá số đó thì phần còn lại bị bỏ.
- Tin nào trong sổ ghi "Chi viết" thì Chi bỏ qua. Tin thật của Chi thì Chi đã có sẵn rồi.
- Tin có ngày gửi ở tương lai thì sửa thành "bây giờ".
- Mỗi tin lấy từ sổ của người khác có một nhãn nhỏ "lịch sử · Minh chia sẻ". Chi cũng nhận được thông báo: "không xác minh được người viết từng tin".
- Tin bị xoá khỏi sổ thì mã số của nó vẫn được ghi nhớ, để bản cũ trong sổ của người khác không chen vào lại được.

**Về kỹ thuật:**
- `sendHistoryTo` chỉ được gọi sau khi `PEER_IDENTITY` hợp lệ.
- `handleTranscript` không ACK nếu người gửi chưa có trong `verifiedRemoteFingerprints`.
- Bộ đếm `historyAcceptedFrom` giới hạn 200 tin cho mỗi người chia sẻ.
- `GroupHistory.remove` xoá tin nhưng **giữ** `id` trong tập `ids`.

**Trong code:**
- Chỉ nhận lịch sử sau khi đã xác thực danh tính: [P2pChatSession.java:711-715](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L711-L715).
- Giới hạn 200 tin mỗi người chia sẻ: [P2pChatSession.java:733-737](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L733-L737).
- Bỏ tin gán cho chính mình, chặn thời gian ở tương lai: [P2pChatSession.java:741-749](../client-javafx/src/main/java/com/datn/chatp2p/client/session/P2pChatSession.java#L741-L749). Đây là chỗ đã sửa trong đợt rà soát bảo mật.
- Xoá tin nhưng giữ mã số: [GroupHistory.java:66-72](../client-javafx/src/main/java/com/datn/chatp2p/client/session/GroupHistory.java#L66-L72).
- Nhãn "lịch sử · … chia sẻ": [TranscriptCell.java:316-321](../client-javafx/src/main/java/com/datn/chatp2p/client/view/TranscriptCell.java#L316-L321).
- Thông báo "không xác minh được người viết từng tin": [RoomController.java:449-460](../client-javafx/src/main/java/com/datn/chatp2p/client/view/RoomController.java#L449-L460).

**Đáp ứng yêu cầu:**
- **R4:** chỉ trao đổi sau khi xác thực danh tính.
- **R5:** tối đa 200 tin mỗi người chia sẻ.
- **R6:** bỏ tin gán cho chính người nhận.
- **R7:** mã số của tin đã xoá vẫn được ghi nhớ.
- **R8:** có nhãn riêng và thông báo cho tin từ lịch sử chia sẻ.

**→ Lỗ hổng:** lịch sử **chỉ sống khi còn người trong phòng**.

**Phân tích vấn đề:**
- `GroupHistory` nằm trong RAM. An tắt ứng dụng là sổ của An mất.
- Nếu tất cả mọi người cùng rời phòng, lịch sử biến mất hoàn toàn. Hôm sau vào lại, phòng trống trơn.
- Có người thích như vậy, vì không lưu gì là không lộ gì. Có người lại cần xem lại những gì đã bàn.
- Nếu lưu xuống ổ đĩa thì phát sinh rủi ro mới: người dùng chung máy, phần mềm độc hại hay bản sao lưu ổ đĩa đều có thể đọc được toàn bộ cuộc trò chuyện.

**Để giải quyết thì cần gì:**
- **R9:** **tuỳ chọn** lưu lịch sử trên máy, và **mặc định tắt**.
- **R10:** file lưu phải được **mã hoá**, chỉ đúng người dùng đó trên đúng máy đó mở được. Nếu máy không mã hoá được thì **không lưu**.
- **R11:** tên file không được để lộ tên phòng. Hai phòng trùng tên nhưng khác mật khẩu không được lẫn vào nhau.
- **R12:** không lưu tin riêng, không lưu tin đã bị xoá. Tin lấy từ lịch sử chia sẻ không được biến thành "tin của tôi".
- **R13:** có nút **xoá sạch** lịch sử đã lưu.

---

## Bước 3 — Lưu lịch sử trên máy, mã hoá bằng tài khoản Windows

**Làm gì:**
- Trong Cài đặt có ô "Lưu tin nhắn nhóm trên máy này (mã hoá)". Ô này mặc định không được chọn.
- Khi được chọn, lúc rời phòng máy cất bản chép các tin nhóm vào một ngăn tủ chỉ mở được khi đúng người đó đang dùng đúng máy đó. Đây là cùng loại ngăn tủ dùng để cất khoá danh tính.
- Mỗi phòng một ngăn. Tên ngăn là một mã số tính từ tên phòng và mật khẩu phòng, nên nhìn tên ngăn không đoán ra được tên phòng.
- Những gì không được cất vào ngăn:
  - thư riêng;
  - thư đã bị xoá;
  - quá 500 tin của mỗi phòng (chỉ giữ 500 tin gần nhất).
- Tin lấy từ sổ của người khác thì không bao giờ được cất như "tin của tôi".
- Lần sau vào lại phòng, máy mở ngăn ra và hiện lại các tin, mỗi tin có nhãn "đã lưu trên máy".
- Máy nào không có loại ngăn tủ này, như máy không phải Windows, thì ô lựa chọn bị khoá lại và không lưu gì.
- Có nút "Xoá lịch sử đã lưu" để dọn sạch tất cả các ngăn.

**Về kỹ thuật:**
- Mỗi phòng một file `~/.chat-p2p-java/history/<SHA-256(khoá phòng)>.dat`.
- "Khoá phòng" là `roomId` trên server, tức `tên` hoặc `tên~thẻ`. Hai phòng trùng tên khác mật khẩu có thẻ khác nhau nên rơi vào hai file khác nhau.
- Nội dung JSON được mã hoá bằng DPAPI, cùng `KeyProtector` dùng cho khoá danh tính (xem [Co-che-xac-thuc-danh-tinh.md](Co-che-xac-thuc-danh-tinh.md), Bước 5).
- File được ghi ra file tạm trước rồi mới đổi tên.
- `peerId` đổi mỗi lần vào phòng, nên file lưu cờ `mine` và tên người viết thay cho `peerId`.
- Lịch sử nạp từ file chỉ được hiện trên giao diện. Nó **không** được nạp vào `GroupHistory`, nên không bao giờ được chia sẻ cho người khác.

**Trong code:**
- Định dạng file, giới hạn 500 tin, không có DPAPI thì không lưu: [LocalHistoryStore.java:20-60](../client-javafx/src/main/java/com/datn/chatp2p/client/util/LocalHistoryStore.java#L20-L60).
- Ghi file mã hoá, ghi file tạm rồi đổi tên: [LocalHistoryStore.java:62-79](../client-javafx/src/main/java/com/datn/chatp2p/client/util/LocalHistoryStore.java#L62-L79).
- Đọc file. Giải mã không được, ví dụ file chép từ tài khoản khác, thì coi như chưa có: [LocalHistoryStore.java:82-94](../client-javafx/src/main/java/com/datn/chatp2p/client/util/LocalHistoryStore.java#L82-L94).
- Xoá tất cả: [LocalHistoryStore.java:97-111](../client-javafx/src/main/java/com/datn/chatp2p/client/util/LocalHistoryStore.java#L97-L111).
- Tên file là SHA-256 của khoá phòng: [LocalHistoryStore.java:113-120](../client-javafx/src/main/java/com/datn/chatp2p/client/util/LocalHistoryStore.java#L113-L120).
- Lưu khi rời phòng hoặc đóng cửa sổ, bỏ tin đã xoá. Tin chia sẻ không bao giờ là "tin của tôi": [RoomController.java:247](../client-javafx/src/main/java/com/datn/chatp2p/client/view/RoomController.java#L247), [974-998](../client-javafx/src/main/java/com/datn/chatp2p/client/view/RoomController.java#L974-L998).
- Nạp lại khi vào phòng: [RoomController.java:952-972](../client-javafx/src/main/java/com/datn/chatp2p/client/view/RoomController.java#L952-L972).
- Ô lựa chọn mặc định tắt, nút xoá, ghi chú "tin riêng không được lưu": [UserSettings.java:31](../client-javafx/src/main/java/com/datn/chatp2p/client/util/UserSettings.java#L31), [SettingsDialog.java:171-189](../client-javafx/src/main/java/com/datn/chatp2p/client/view/SettingsDialog.java#L171-L189).

**Đáp ứng yêu cầu:**
- **R9:** tuỳ chọn, mặc định tắt.
- **R10:** mã hoá DPAPI; không có DPAPI thì không lưu.
- **R11:** tên file là SHA-256 của khoá phòng, mà khoá phòng có cả thẻ mật khẩu.
- **R12:** bỏ tin riêng và tin đã xoá; tin chia sẻ không thành "tin của tôi".
- **R13:** có nút xoá lịch sử đã lưu.

**→ Lỗ hổng còn lại:** dấu vết "tin này chưa được kiểm chứng" **mất đi sau khi lưu**. Ngoài ra còn hai chỗ hở nhỏ hơn.

**Phân tích vấn đề:**
- **Mất nhãn "không xác minh":**
  1. Ở Bước 2, tin lấy từ lịch sử của Minh có nhãn "lịch sử · Minh chia sẻ".
  2. Chi lưu lịch sử. File chỉ ghi `mine`, tên người viết, nội dung và thời gian. Nó **không ghi nguồn gốc** của tin ([LocalHistoryStore.java:34](../client-javafx/src/main/java/com/datn/chatp2p/client/util/LocalHistoryStore.java#L34)).
  3. Lần sau mở lại, mọi tin đều mang cùng một nhãn "đã lưu trên máy" ([TranscriptCell.java:311-315](../client-javafx/src/main/java/com/datn/chatp2p/client/view/TranscriptCell.java#L311-L315)).
  4. Câu bịa "Bình: chuyển tiền vào tài khoản này" của Minh giờ trông y như một tin Chi đã nhận trực tiếp từ Bình.
- **Chỉ lưu khi rời phòng đúng cách:** ứng dụng bị treo hay máy mất điện thì mất toàn bộ tin của phiên đó.
- **Tin bị xoá khi mình vắng mặt:** Bình xoá một tin lúc Chi không ở trong phòng. Bản Chi đã lưu vẫn còn nguyên tin đó.

Gốc của chỗ hở đầu tiên, và cũng là của mọi rủi ro ở Bước 1, là: **từng tin trong lịch sử không mang bằng chứng nào về người viết**. Các nhãn chỉ là cách nhắc người dùng, không phải bằng chứng.

**Để giải quyết thì cần gì:**
- **R14:** mỗi tin phải mang **bằng chứng về người viết** mà bất kỳ ai cũng kiểm tra được, kể cả khi tin được người khác chuyển lại.
- **R15:** khi chưa có bằng chứng thì ít nhất phải **giữ nguồn gốc** của tin khi lưu xuống máy.
- **R16:** lưu **định kỳ** chứ không chỉ khi rời phòng.

---

## Bước 4 — *(đề xuất, chưa làm)* Tác giả ký từng tin, giữ nguồn gốc khi lưu, lưu định kỳ

**Làm gì:**
- Mỗi lá thư Bình viết đều được Bình đóng con dấu riêng của mình lên, đúng con dấu dùng để chứng minh danh tính.
- Minh chép lại thư của Bình thì phải chép nguyên cả dấu. Minh bịa một thư "của Bình" thì không đóng được dấu của Bình.
- Chi so dấu với hình in con dấu của Bình. Hình in này Chi có từ lúc kết nối với Bình, hoặc từ cuốn sổ tay người đã so vân tay.
- Khoảng một phút máy cất sổ vào ngăn tủ một lần, để lỡ máy tắt đột ngột cũng không mất nhiều.

**Về kỹ thuật:**
- Thêm chữ ký ECDSA của tác giả vào `MessagePayload` và `HistoryMessage`, ký trên `id | phòng | nội dung | timeSent`.
- Lịch sử chia sẻ mang kèm public key danh tính của tác giả. Bên nhận kiểm tra chữ ký. Nếu khoá đó khớp với người đang trong phòng, hoặc khớp với `TrustedPeers`, thì hiện "đã xác minh người viết".
- `SavedMessage` thêm trường nguồn gốc (`direct`, `shared`, `verified`) để nhãn được giữ nguyên khi nạp lại.
- Lưu định kỳ bằng một bộ hẹn giờ trong `RoomController`, ngoài lần lưu khi rời phòng.

**Trong code (dự kiến):** `IdentitySignatureService.sign` đã có sẵn và chỉ cần dùng lại. Cần sửa `MessagePayload`, `HistoryMessage`, `P2pChatSession.handleTranscript` để kiểm tra chữ ký, `LocalHistoryStore.SavedMessage` và `TranscriptCell` để hiện nhãn theo nguồn gốc.

**Đáp ứng yêu cầu:**
- **R14:** bằng chứng người viết đi theo từng tin.
- **R15:** nguồn gốc được giữ khi lưu.
- **R16:** lưu định kỳ.

**Cái giá phải trả:** mỗi tin dài thêm khoảng 70 byte chữ ký, và mỗi tin tốn thêm một lần ký và một lần kiểm tra. Có một hệ quả ngược: chữ ký làm tin nhắn **không chối được**. Ai cầm tin cũng chứng minh được với người thứ ba rằng Bình đã nói câu đó. Một số ứng dụng như Signal cố ý tránh tính chất này. Đây là một lựa chọn về thiết kế cần cân nhắc, không phải chỉ thuần kỹ thuật.

---

## Những giới hạn còn lại cần nêu khi bảo vệ

| Giới hạn | Giải thích |
|---|---|
| Phòng trống là mất lịch sử, nếu không ai bật lưu trên máy | Có chủ đích: mặc định không lưu gì ở đâu cả. |
| Chỉ chia sẻ 200 tin gần nhất | Đủ ngữ cảnh. Giới hạn này còn chặn việc làm ngập máy người nhận. |
| Lưu trên máy chỉ có ở Windows | Cần một `KeyProtector` cho macOS Keychain hoặc Linux Secret Service. Giao diện đã tách sẵn. |
| Lịch sử trên máy không được chia sẻ lại cho người khác | Có chủ đích: tin cũ đã lưu không lan sang những người không có mặt lúc đó. |

---

## Tóm tắt chuỗi thiết kế

| Bước | Làm gì | Vá được gì | Sinh ra vấn đề gì |
|---|---|---|---|
| 0 | Tin chỉ trên màn hình người đang có mặt | Riêng tư tối đa | Người vào sau mất ngữ cảnh |
| 1 | `GroupHistory` 200 tin, người cũ gửi `MESSAGE_TRANSCRIPT` cho người mới | Người mới có lịch sử, không qua server | Tin hoàn toàn vào người chia sẻ |
| 2 | Chỉ sau xác thực, 200 tin mỗi người, bỏ tin gán cho mình, giữ mã số tin đã xoá, nhãn "chia sẻ" | Hạn chế bịa đặt, làm ngập, hồi sinh tin | Rời phòng là mất |
| 3 | Lưu trên máy, tuỳ chọn, DPAPI, tên file băm, 500 tin, nút xoá | Mở lại vẫn thấy tin, không lộ cho người khác | Mất nhãn "không xác minh" khi nạp lại; chỉ lưu khi rời phòng |
| 4 *(đề xuất)* | Tác giả ký từng tin, giữ nguồn gốc, lưu định kỳ | Kiểm chứng được người viết của lịch sử | Tin trở nên không chối được |

```
B0 không lưu gì
 ──người vào sau mất ngữ cảnh────► B1 người cũ gửi lịch sử
 ──tin vào người chia sẻ─────────► B2 giới hạn + nhãn "chia sẻ"
 ──rời phòng là mất──────────────► B3 lưu trên máy, mã hoá DPAPI
 ──mất nhãn, không có bằng chứng─► B4 ký từng tin + giữ nguồn gốc (đề xuất)
```
