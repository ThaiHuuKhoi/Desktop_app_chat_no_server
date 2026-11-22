# GIẢI THÍCH CƠ CHẾ MÃ HOÁ CHAT P2P BẰNG VÍ DỤ ĐỜI THƯỜNG — KÈM ĐỐI CHIẾU CODE

**Ngày:** 22/11/2026 · **Phiên bản code:** nhánh `main`, commit `a0ee278` (số dòng bên dưới đúng với bản này)

Tài liệu này giải thích, bằng ví dụ dễ hiểu, cách ứng dụng mã hoá tin nhắn giữa hai người, theo dạng **chuỗi thiết kế**: mỗi bước được phân tích để tìm lỗ hổng, và lỗ hổng đó sinh ra bước tiếp theo để vá. Mỗi bước có mục **"Trong code"** chỉ ra lớp và dòng code đang làm đúng việc được kể trong ví dụ. Chi tiết kỹ thuật đầy đủ nằm trong [Tai-lieu-ky-thuat.md](Tai-lieu-ky-thuat.md), mục E.3.4 (mã hoá) và E.5 (xác thực danh tính).

Đường dẫn rút gọn dùng trong tài liệu:
- `crypto/…` = `crypto/src/main/java/com/datn/chatp2p/crypto/`
- `p2p-core/…` = `p2p-core/src/main/java/com/datn/chatp2p/p2p/`
- `client/…` = `client-javafx/src/main/java/com/datn/chatp2p/client/`

---

## Nhân vật

| Trong ví dụ | Trong hệ thống | Trong code |
|---|---|---|
| **An**, **Bình** | Hai client JavaFX | `client/…/session/P2pChatSession.java` (mỗi máy một phiên) |
| **Minh** | Kẻ nghe lén / mạo danh trên mạng hoặc trên server | — |
| **Bưu điện** | Signaling server: chỉ giới thiệu hai bên lúc đầu | `signaling-server/…/ws/SignalingWebSocketHandler.java` |
| **Đường đi thẳng giữa hai nhà** | Kênh UDP do ice4j tìm ra | `p2p-core/…/ice/IceP2pConnectionEstablisher.java`, `p2p-core/…/channel/P2pDataChannel.java` |

## Mỗi người có hai loại khoá

| Loại khoá | Ví dụ đời thường | Sống bao lâu | Dùng để | Trong code |
|---|---|---|---|---|
| **Khoá danh tính** (ECDSA P-256) | Chữ ký tay | Lâu dài, lưu ở `~/.chat-p2p-java/identity.properties` | **Ký tên** chứng minh "tôi là tôi" | `crypto/…/IdentityKeyStore.java`, `crypto/…/IdentitySignatureService.java` (thuật toán `SHA256withECDSA`, dòng 36), đường dẫn file ở `client/…/session/PeerIdentityService.java:21` |
| **Khoá tạm** (ECDH P-256) | Bí mật riêng nghĩ ra mới cho từng buổi (Bước 2) | Một kết nối, chỉ nằm trong RAM | Tạo **bí mật chung** (nguyên liệu chìa), rồi tách thành 2 chìa (mỗi chiều 1 chìa) để mã hoá | Sinh ở `p2p-core/…/RoomSession.java:505` bằng `crypto/…/KeyExchangeService.java:40` (`generateKeyPair`) |

---

## Chuỗi thiết kế: mỗi bước sinh ra để vá lỗ hổng của bước trước

**Cách đọc.** Mỗi bước có cùng khuôn:

1. **Làm gì**: kể bằng ví dụ đời thường (An, Bình, Minh, bưu điện, thư, dấu niêm phong…), không dùng thuật ngữ kỹ thuật.
2. **Về kỹ thuật** và **Trong code**: ví dụ đó tương ứng với cơ chế kỹ thuật nào, và lớp / dòng code nào thực hiện.
3. **Đáp ứng yêu cầu**: bước này thoả mãn từng yêu cầu mà bước trước đặt ra như thế nào.
4. **Lỗ hổng**: phân tích bước này còn hở chỗ nào.
5. **Phân tích vấn đề**: nói cụ thể kẻ tấn công có gì, muốn gì, vì sao bước hiện tại không chặn được.
6. **Để giải quyết thì cần gì**: liệt kê các yêu cầu (R1, R2…) mà giải pháp phải đạt; từ đó sinh ra bước tiếp theo.

Bước đánh dấu *(không dùng trong code)* là cách "dễ nghĩ ra", được nêu để thấy vì sao phải bỏ. Bước 10–11 là **đề xuất, chưa làm**.

### Chuẩn bị — Bưu điện giới thiệu, hai nhà mở đường đi thẳng

An và Bình vào cùng phòng. Bưu điện cho hai bên biết nhau và chuyển giúp "thư hỏi đường" (OFFER / ANSWER chứa địa chỉ); sau đó hai nhà tự tìm ra đường đi thẳng (hoặc đi qua người chuyển thư trung gian TURN).

**Trong code:** `RoomSession` nhận danh sách người trong phòng và gửi OFFER; `IceP2pConnectionEstablisher` thu thập địa chỉ (STUN dòng 176, TURN dòng 185) và chọn đường; khi xong, `RoomSession.onIceConnected` được gọi và tạo kênh `P2pDataChannel`.

---

### Bước 0 — Gửi thẳng tin nhắn, không mã hoá

**Làm gì:** An viết một tấm bưu thiếp "Chào Bình, hẹn 7 giờ" rồi gửi qua bưu điện, không bỏ vào phong bì. Tấm thiếp đi qua tay rất nhiều người: nhân viên bưu cục, người phân loại, người đưa thư.

**Về kỹ thuật:** tương ứng với việc gửi thẳng nội dung tin nhắn, chưa mã hoá, lên kênh UDP vừa mở.

**→ Lỗ hổng:** người ở bất kỳ chặng nào cũng **đọc, sửa, giả mạo** được tin, giống bưu thiếp không có phong bì.

**Phân tích vấn đề:**
- Gói tin đi qua rất nhiều chặng: Wi-Fi quán cà phê, router, nhà mạng, có khi cả máy chủ TURN. Minh chỉ cần đứng ở **một** chặng bất kỳ.
- Minh làm được 3 việc khác nhau, mỗi việc gây một loại thiệt hại:
  - **Đọc**: lộ nội dung (mất *tính bí mật*).
  - **Sửa**: tẩy "7 giờ" thành "9 giờ", Bình không biết (mất *tính toàn vẹn*).
  - **Giả mạo**: tự viết thư ký tên An (mất *tính xác thực nguồn gửi*).
- Không thể giấu đường đi: mạng Internet không cho An chọn ai được chạm vào gói tin.

**Để giải quyết thì cần gì:**
- **R1:** biến thư thành dạng mà người ngoài không đọc được (mã hoá), nhưng Bình đọc lại được.
- **R2:** mọi chỉnh sửa, dù chỉ một byte, đều bị Bình phát hiện (niêm phong).
- **R3:** chỉ An và Bình làm được hai việc trên.

**Phân tích thêm R1: Bình đọc lại được nhờ cái gì?**

R1 đòi hai điều nghe như mâu thuẫn: Minh cầm lá thư trong tay mà **không đọc được**, còn Bình cầm đúng lá thư đó lại **đọc được**. Vậy Bình phải có **một thứ mà Minh không có**. Thứ đó là gì?

- **Không thể là "cách làm" (công thức xáo trộn chữ).** Ví dụ: "dịch mỗi chữ cái lên 3 vị trí". Công thức kiểu này sớm muộn cũng lộ: nó nằm sẵn trong phần mềm mà ai cũng tải được, có thể bị dịch ngược, hoặc bị đoán ra sau vài lá thư. Lộ công thức là lộ **mọi thư của mọi người**, và muốn đổi công thức thì phải cập nhật phần mềm cho tất cả. Vì vậy trong mật mã học có một nguyên tắc (nguyên tắc Kerckhoffs): **cách làm phải được coi như công khai, mọi bí mật nằm ở chìa.** Dự án cũng dùng thuật toán công khai, chuẩn quốc tế (AES), không tự chế.
- **Phải là một chiếc chìa.** Công thức thì ai cũng biết, nhưng cần thêm một chiếc chìa mới làm được. Giống ổ khoá cùng một mẫu bán ngoài chợ, ai cũng biết cấu tạo, nhưng chỉ chiếc chìa đúng mới mở được. Bình đọc lại được thư **vì Bình có chìa**; Minh biết rõ công thức mà vẫn chịu, **vì Minh không có chìa**.
- **Chìa của Bình phải "khớp" với chìa An dùng để khoá thư.** Cách đơn giản nhất: hai người dùng **cùng một chiếc chìa**, một chìa vừa khoá vừa mở (gọi là mã hoá đối xứng). Cách này nhanh, hợp để khoá từng tin nhắn và từng mảnh file, nên dự án dùng nó cho phần mã hoá chính.
- **Chìa phải đủ "khó đoán".** Nếu chìa chỉ có vài nghìn khả năng, Minh thử lần lượt từng chìa là ra. Chìa của dự án là một con số 256 bit, tức khoảng 10⁷⁷ khả năng (gần bằng số nguyên tử trong vũ trụ quan sát được). Kể cả thử một tỷ tỷ chìa mỗi giây thì cũng cần thời gian gấp vô số lần tuổi vũ trụ, nên thử hết là bất khả thi.
- **Chìa chỉ được nằm ở hai nơi: máy An và máy Bình.** Đây là điều kiện khó nhất, vì An và Bình ở xa nhau và mọi thứ gửi qua bưu điện đều có thể bị Minh nhìn thấy (xem Bước 1).

Tóm lại, Bình đọc lại được thư là **nhờ chiếc chìa bí mật mà chỉ An và Bình có**, chứ không nhờ giữ kín cách làm. R2 và R3 cũng quy về cùng điều kiện đó: dấu niêm phong chỉ người có chìa mới đóng được và mới kiểm tra được.

Vậy cả ba yêu cầu đều quy về một thứ: **một chìa bí mật, đủ khó đoán, mà chỉ An và Bình có**. Câu hỏi đầu tiên là: **làm sao cả hai cùng có chìa đó, khi họ ở xa nhau và mọi thứ gửi qua bưu điện đều có thể bị nhìn thấy?** **→ Sinh ra Bước 1.**

---

### Bước 1 — Lấy chìa chung theo cách "dễ nghĩ ra" *(không dùng trong code)*

**Làm gì:** An và Bình cần một chiếc chìa chung để khoá thư. Các cách nghĩ ra ngay:
- (a) nhờ bưu điện đánh một chiếc chìa rồi gửi cho mỗi người một bản;
- (b) An tự đánh chìa rồi gửi bản sao cho Bình qua đường bưu điện;
- (c) hai người dùng một câu "mật khẩu" quen thuộc làm chìa.

**Về kỹ thuật:** (a) server sinh khoá rồi gửi cho hai client; (b) một client sinh khoá rồi gửi qua mạng; (c) dùng mật khẩu phòng làm khoá.

**→ Lỗ hổng:** cả ba cách đều để lộ chìa cho người không đáng tin, hoặc tạo ra chìa yếu.

**Phân tích vấn đề:**
- **(a)** Bưu điện **biết chìa**. Bưu điện bị hack hay bị mua chuộc là đọc được mọi thư, đi ngược lại đúng mục tiêu "server không đọc được nội dung" của đề tài.
- **(b)** Chìa đi qua mạng, nên Minh **bắt được chìa** y như bắt được thư ở Bước 0. Muốn gửi chìa an toàn thì lại cần một chìa khác để khoá nó: vòng luẩn quẩn.
- **(c)** Phòng công khai không có mật khẩu. Mật khẩu do người dùng đặt thường ngắn, dễ đoán, và dùng mãi không đổi. Lộ mật khẩu một lần là lộ mọi buổi nói chuyện, cả quá khứ lẫn tương lai.
- **Mấu chốt:** mọi thứ An và Bình gửi cho nhau đều có thể bị Minh nhìn thấy, và không có kênh bí mật nào sẵn có từ trước.

**Để giải quyết thì cần gì:** một cách tạo chìa chung sao cho:
- **R1:** An và Bình kết thúc với **cùng một giá trị bí mật**.
- **R2:** những gì gửi qua mạng là thứ **Minh thấy cũng không sao**.
- **R3:** từ những thứ công khai đó, **không ai tính ra được giá trị bí mật**. Cần một phép toán *dễ làm xuôi, cực khó làm ngược*.
- **R4:** không cần gặp nhau trước, không cần mật khẩu chung từ trước, không cần tin bưu điện.
- **R5:** mỗi buổi nói chuyện ra một giá trị mới, để lộ buổi này không làm lộ buổi khác.

**→ Sinh ra Bước 2.**

---

### Bước 2 — Trao khoá ECDH

**Làm gì:**

1. An tự nghĩ ra một **bí mật riêng**, không nói cho ai, kể cả Bình. Bình cũng tự nghĩ ra bí mật riêng của mình.
2. Từ bí mật riêng, mỗi người tính ra một **mẩu công khai** bằng một cách tính chỉ làm xuôi được, không làm ngược được: biết bí mật thì tính ra mẩu công khai trong nháy mắt, nhưng cầm mẩu công khai thì không ai lần ngược ra được bí mật. Hai người gửi mẩu công khai cho nhau qua bưu điện.
3. An kết hợp **bí mật riêng của An** với **mẩu công khai của Bình**; Bình kết hợp **bí mật riêng của Bình** với **mẩu công khai của An**. Cả hai ra **cùng một kết quả**, dù chưa ai từng gửi bí mật của mình đi.
4. Minh chặn được cả hai mẩu công khai trên đường, nhưng không lần ngược ra được bí mật riêng nào, nên **không tính ra được kết quả chung**.

Kết quả chung này chính là **"chìa chung" mà Bước 1 cần, nhưng mới ở dạng nguyên liệu**; tới Bước 5 nó mới được làm thành chìa dùng thật. Mỗi buổi nói chuyện, hai người lại nghĩ ra bí mật riêng mới.

**Về kỹ thuật:** đây là trao khoá ECDH trên đường cong P-256. Bí mật riêng là private key `a`, `b`; mẩu công khai là public key `a·G`, `b·G` (`G` là một điểm cố định, công khai trên đường cong); kết quả chung là **bí mật chung** `a·b·G` (32 byte). "Cách tính chỉ làm xuôi được" là phép nhân điểm trên đường cong: tính `a·G` thì nhanh, còn từ `a·G` tìm lại `a` là bài toán logarit rời rạc trên đường cong elliptic, chưa ai giải được trong thời gian thực tế.

**Trong code:**
- `p2p-core/…/RoomSession.java:505`: `KeyExchangeService.generateKeyPair()` (`crypto/…/KeyExchangeService.java:40`) sinh bí mật riêng (và mẩu công khai) mới cho riêng kết nối này.
- `p2p-core/…/PeerConnection.java:154` (`sendEcdhPublicKey`): gửi mẩu công khai (public key). Đây là **gói duy nhất không mã hoá**. Vì UDP có thể làm mất gói, nó được gửi lại tối đa 5 lần (`HANDSHAKE_RETRY_ATTEMPTS`, dòng 84), mỗi lần cách 300 ms (dòng 86).
- `p2p-core/…/PeerConnection.java:247, 253–256`: gói đầu tiên nhận được là mẩu công khai của bên kia; `crypto/…/KeyExchangeService.java:94` (`generateSecret`) tính bí mật chung.

**Đáp ứng yêu cầu của Bước 1:**
- **R1:** `a·(b·G) = b·(a·G)`, nên hai bên ra cùng một giá trị.
- **R2:** chỉ gửi `a·G` và `b·G`. Đây là thứ công khai, Minh thấy cũng không sao.
- **R3:** từ `a·G` tìm lại `a` là bài toán logarit rời rạc trên đường cong elliptic, với P-256 thì máy tính hiện nay cần thời gian vượt xa tuổi vũ trụ.
- **R4:** không cần gì chuẩn bị từ trước; bưu điện chỉ chuyển các mẩu công khai, không biết bí mật.
- **R5:** `a`, `b` được chọn mới cho mỗi kết nối và không lưu lại.

**→ Lỗ hổng (chưa xong):** mới có **nguyên liệu làm chìa**; tin nhắn vẫn chưa được mã hoá.

**Phân tích vấn đề:** An và Bình đã có chung nguyên liệu làm chìa, nhưng lá thư thì vẫn đang ở dạng ai cầm cũng đọc được. Còn thiếu một cách **dùng chìa để biến lá thư thành dạng người ngoài không đọc được, còn Bình đọc lại được** (yêu cầu R1 của Bước 0).

**Để giải quyết thì cần gì:**
- **R1:** một thuật toán mã hoá đối xứng mạnh, đã được chuẩn hoá (không tự chế thuật toán).
- **R2:** chạy nhanh, vì mỗi tin, mỗi chunk file đều phải mã hoá.

**→ Sinh ra Bước 3.**

---

### Bước 3 — Mã hoá thông thường (AES-CBC / AES-CTR) *(không dùng trong code)*

**Làm gì:** An bỏ thư vào một chiếc hộp, khoá bằng chìa chung rồi gửi đi. Bình mở hộp bằng chiếc chìa giống hệt.

**Về kỹ thuật:** dùng bí mật chung làm khoá AES để mã hoá từng tin bằng chế độ CBC hoặc CTR, như nhiều ví dụ trên mạng.

**Đáp ứng yêu cầu của Bước 2:** AES là chuẩn quốc tế, có tăng tốc phần cứng trên hầu hết CPU, nên đạt R1 và R2. Minh **không đọc được** thư nữa.

**→ Lỗ hổng:** các chế độ này **chỉ mã hoá, không xác thực**: chúng không biết thư có bị động vào hay không.

**Phân tích vấn đề:**
- Minh không cần đọc được thư mới phá được. Với AES-CTR, bản mã = bản rõ XOR với một dãy bí mật. Minh **lật bit thứ k** của bản mã thì bit thứ k của bản rõ cũng bị lật theo. Nếu đoán được vị trí của chữ "7" (ví dụ tin có định dạng cố định), Minh đổi nó thành "9" mà không cần biết chìa.
- Với AES-CBC, sửa một khối làm hỏng khối đó và lật bit có kiểm soát ở khối kế tiếp.
- Người nhận **giải mã ra một thứ gì đó** và không có cách nào biết thứ đó đã bị sửa. Giống phong bì không có dấu niêm phong: người ngoài không đọc được thư, nhưng rạch ra, đổi trang bên trong rồi dán lại được.
- Vậy yêu cầu R2 của Bước 0 (phát hiện mọi chỉnh sửa) **chưa đạt**.

**Để giải quyết thì cần gì:**
- **R1:** mỗi gói phải kèm một **dấu niêm phong** tính từ toàn bộ nội dung *và* chìa bí mật; sửa một byte là dấu không còn khớp.
- **R2:** chỉ người có chìa mới tạo được dấu hợp lệ, để Minh không tự làm dấu mới cho thư đã sửa.
- **R3:** kiểm tra dấu **trước khi** dùng nội dung; dấu sai thì vứt cả gói.
- **R4:** mã hoá và niêm phong làm trong một thao tác chuẩn, để tránh lỗi tự ghép hai cơ chế.

**→ Sinh ra Bước 4.**

---

### Bước 4 — Mã hoá có xác thực: AES-256-GCM

**Làm gì:** ngoài việc khoá hộp, An còn đóng một **dấu sáp niêm phong** lên nắp hộp. Dấu này chỉ người có chìa chung mới đóng được. Bình kiểm tra dấu trước khi mở hộp: dấu vỡ hoặc không đúng thì vứt cả hộp, không đọc.

**Về kỹ thuật:** AES-256-GCM, chế độ mã hoá có xác thực: mỗi gói được mã hoá và kèm một tag xác thực 128 bit tính bằng khoá; sửa một byte là tag không khớp và gói bị vứt.

**Trong code:** `crypto/…/AesGcmCipher.java:52` (`encryptWithNonce`) và `:64` (`decryptWithNonce`); độ dài tag 128 bit ở dòng 22.

**Đáp ứng yêu cầu của Bước 3:**
- **R1, R2:** tag GCM được tính từ bản mã, nonce và chìa; không có chìa thì không tạo được tag hợp lệ.
- **R3:** `decryptWithNonce` kiểm tra tag trước khi trả bản rõ; tag sai thì ném lỗi và gói bị bỏ.
- **R4:** GCM là chế độ AEAD (mã hoá có xác thực) chuẩn hoá bởi NIST (SP 800-38D), làm cả hai việc trong một lần.

**→ Lỗ hổng:** GCM có một luật sống còn: **một chìa không bao giờ được dùng hai lần với cùng một nonce** (số định danh của từng lá thư). Cách dùng chìa hiện tại vi phạm luật này, và còn hở hai chỗ khác.

**Phân tích vấn đề:**
- **Trùng nonce:** An và Bình **dùng chung một chìa** cho cả hai chiều, và mỗi người tự đánh số thư từ 0 mà không bàn trước với nhau (UDP có thể mất hoặc đảo gói, nên không thể thống nhất "lượt"). "Thư số 0 của An" và "thư số 0 của Bình" vì vậy trùng (chìa, nonce). Khi trùng: XOR hai bản mã ra XOR hai bản rõ, nên Minh đoán được nội dung; tệ hơn, Minh **tính lại được khoá xác thực của GCM và làm giả được dấu niêm phong** cho thư tuỳ ý.
- **Phản chiếu:** Minh chặn một lá thư An gửi Bình rồi **gửi ngược lại cho chính An**. Thư được niêm phong bằng đúng chìa chung, nên An mở được và tưởng Bình vừa gửi (ví dụ "Đã nhận file").
- **Chìa thô:** bí mật `a·b·G` là toạ độ của một điểm trên đường cong. Không phải chuỗi 256 bit nào cũng xuất hiện được, và giá trị luôn nhỏ hơn số nguyên tố của đường cong, nên phân bố bị lệch. Chìa AES cần 256 bit **ngẫu nhiên đều**.

**Để giải quyết thì cần gì:**
- **R1:** mỗi chiều gửi dùng **một chìa riêng**: thư An gửi và thư Bình gửi không bao giờ chung chìa (vá chuyện trùng nonce và phản chiếu).
- **R2:** hai bên phải **tự biết** chìa nào để gửi, chìa nào để nhận, mà không cần gửi thêm gì cho nhau.
- **R3:** biến bí mật thô thành chìa **ngẫu nhiên đều** (vá chuyện chìa thô).
- **R4:** chìa phải **gắn với đúng buổi nói chuyện này**: cùng bí mật nhưng khác cặp mẩu công khai thì phải ra chìa khác.
- **R5:** chỉ dùng **một** bí mật chung (không trao khoá ECDH hai lần cho hai chiều).

**→ Sinh ra Bước 5.**

---

### Bước 5 — HKDF: làm chìa thật từ bí mật chung, tách thành hai chìa (mỗi chiều một chìa)

**Làm gì:** kết quả chung ở Bước 2 mới là nguyên liệu thô, chưa dùng ngay làm dấu được. Hai người cùng xử lý nó theo một công thức chung cho thành chìa đúng chuẩn, rồi cũng theo công thức đó để mỗi người tự làm ra **hai con dấu**: dấu "An gửi Bình" và dấu "Bình gửi An". An chỉ dùng dấu "An gửi Bình" để niêm phong thư mình gửi đi, và chỉ chấp nhận thư mang dấu "Bình gửi An". Bình thì ngược lại.

**Về kỹ thuật:** HKDF-SHA256 (hàm dẫn xuất khoá, RFC 5869). Bước `extract` biến bí mật chung thành một chuỗi ngẫu nhiên đều; bước `expand` chạy hai lần để ra **khoá A→B** và **khoá B→A** (AES-256).

**Trong code** (`crypto/…/KeyExchangeService.java`):
- Dòng 103: `Hkdf.extract(salt, sharedSecret)`, với salt = SHA-256 của hai public key (sắp theo thứ tự byte để hai bên ra cùng một salt).
- Dòng 105–106: `Hkdf.expand(...)` hai lần với nhãn `"chat-p2p/v2/session-key|"` (dòng 77) + SHA-256 public key **của bên gửi**, ra **chìa gửi** và **chìa nhận** (AES-256).

**Đáp ứng yêu cầu của Bước 4:**
- **R1:** hai lần `expand` với hai `info` khác nhau cho ra hai chìa độc lập.
- **R2:** `info` chứa public key **của bên gửi**. Mỗi bên đều biết public key của mình và của bên kia, nên tự suy ra chìa gửi và chìa nhận mà không cần trao đổi gì thêm.
- **R3:** bước `extract` biến bí mật thô thành chuỗi ngẫu nhiên đều.
- **R4:** salt là SHA-256 của hai public key của buổi này, nên chìa gắn chặt với buổi nói chuyện.
- **R5:** cả hai chìa đều ra từ một bí mật chung duy nhất.

TLS (HTTPS) cũng tách chìa theo chiều như vậy (`client_write_key` / `server_write_key`).

**→ Lỗ hổng (chưa xong):** đã có chìa riêng cho từng chiều, nhưng **chưa có cách đánh số thư (chọn nonce)** bảo đảm không bao giờ trùng trong một chiều.

**Phân tích vấn đề:**
- Nonce của GCM dài 12 byte (96 bit). Nếu chọn **ngẫu nhiên hoàn toàn**, sau khoảng 2³² gói xác suất trùng đã đáng kể (nghịch lý ngày sinh); một lần truyền file 100 MB đã là hàng chục nghìn gói.
- Nếu chọn **tăng dần**, bên gửi phải nhớ mình đã dùng tới số nào, và bên nhận phải đọc được số đó để giải mã.

**Để giải quyết thì cần gì:**
- **R1:** trong mỗi chiều, nonce **không bao giờ lặp lại**, chắc chắn chứ không dựa vào may rủi.
- **R2:** bên nhận **đọc được** nonce để giải mã, nên nonce phải đi kèm gói.
- **R3:** nếu Minh sửa nonce trên đường truyền, việc đó phải bị phát hiện.

**→ Sinh ra Bước 6.**

---

### Bước 6 — Nonce = số thứ tự tăng dần (8 byte) + 4 byte ngẫu nhiên

**Làm gì:** An ghi **số thứ tự** lên mỗi phong bì: thư số 0, số 1, số 2…, tăng dần, không bao giờ dùng lại số cũ. Con số này nằm dưới lớp niêm phong, nên ai tẩy sửa là vỡ dấu.

**Về kỹ thuật:** số thứ tự 8 byte cộng 4 byte ngẫu nhiên tạo thành nonce 12 byte, đặt ở đầu gói; nonce là đầu vào của GCM nên cũng được xác thực.

**Trong code:** `p2p-core/…/protocol/EnvelopeCodec.java:71–73`: `nextSendSequence.getAndIncrement()` ghi vào 8 byte đầu của nonce 12 byte (`NONCE_BYTES`, dòng 38).

**Đáp ứng yêu cầu của Bước 5:**
- **R1:** bộ đếm 8 byte chỉ tăng; mỗi chìa chỉ một người dùng để mã hoá (nhờ Bước 5), nên nonce không bao giờ lặp.
- **R2:** 12 byte nonce đứng ở đầu gói: `gói = [nonce 12 byte][bản mã + tag 16 byte]`.
- **R3:** nonce là đầu vào của GCM, nên sửa nonce thì tag sai.

**→ Lỗ hổng:** một lá thư cũ vẫn mang **dấu niêm phong hợp lệ**, vì đó là thư thật An đã gửi.

**Phân tích vấn đề:**
- Minh không cần đọc hay sửa gì. Minh chỉ **ghi lại** gói "Chuyển tiền đi" (hoặc "Đồng ý", "Huỷ gửi file"…) rồi **gửi lại** vào lúc khác.
- Gói đó có chìa đúng, nonce đúng, tag đúng, nên Bình giải mã thành công và tưởng là tin mới. Đây là tấn công phát lại (replay).
- Bước 4 chỉ kiểm tra "thư có bị sửa không", không kiểm tra "thư này đã nhận rồi chưa".
- Không thể đơn giản là "chỉ nhận số lớn hơn số trước": UDP **đảo thứ tự** gói, nên thư số 5 có thể tới trước thư số 4 một cách hợp lệ.

**Để giải quyết thì cần gì:**
- **R1:** bên nhận nhớ được **những số thứ tự đã nhận** và vứt thư trùng số.
- **R2:** vẫn nhận thư **đến sai thứ tự** trong một giới hạn hợp lý.
- **R3:** bộ nhớ **có giới hạn**, không phải nhớ mọi số từ đầu buổi.
- **R4:** thư quá cũ (ngoài giới hạn nhớ) thì vứt luôn cho an toàn.

**→ Sinh ra Bước 7.**

---

### Bước 7 — Cửa sổ chống phát lại 1024 số thứ tự

**Làm gì:** Bình giữ một **cuốn sổ** ghi số của những lá thư vừa nhận gần đây (khoảng 1024 lá gần nhất). Thư mang số đã có trong sổ, hoặc số quá cũ so với trang sổ hiện tại, thì vứt. Thư đến lộn thứ tự (số 5 đến trước số 4) nhưng vẫn còn trong phạm vi sổ thì vẫn nhận.

**Về kỹ thuật:** cửa sổ trượt chống phát lại (replay window) 1024 số thứ tự, giống cách DTLS và IPsec làm.

**Trong code:** `p2p-core/…/protocol/EnvelopeCodec.java:115` (`replayWindow.isFresh(sequence)`), `p2p-core/…/protocol/ReplayWindow.java` (cửa sổ 1024 ở dòng 21). DTLS và IPsec cũng làm như vậy.

**Đáp ứng yêu cầu của Bước 6:** R1 (nhớ số đã nhận trong cửa sổ), R2 (thư sai thứ tự trong 1024 số vẫn nhận), R3 (chỉ nhớ 1024 bit), R4 (cũ hơn cửa sổ thì vứt).

**→ Lỗ hổng:** chính cái cửa sổ có thể bị **lợi dụng để chặn thư thật**.

**Phân tích vấn đề:**
- Cửa sổ trượt theo số thứ tự lớn nhất đã thấy. Nếu cập nhật cửa sổ **ngay khi đọc số thứ tự**, trước khi kiểm tra niêm phong, thì Minh gửi một gói rác ghi số rất lớn (ví dụ 1.000.000) là cửa sổ nhảy tới đó.
- Từ lúc đó, mọi thư thật của An (số 37, 38…) đều bị coi là "quá cũ" và bị vứt. Minh **cắt đứt cuộc trò chuyện** mà không cần chìa.
- Vấn đề gốc: tin vào một con số **chưa được xác thực**.

**Để giải quyết thì cần gì:**
- **R1:** chỉ những gói **chứng minh được là thật** (qua kiểm tra niêm phong) mới được làm thay đổi trạng thái chống phát lại.
- **R2:** hai gói trùng nhau tới cùng lúc trên hai luồng xử lý không được cùng lọt qua.

**→ Sinh ra Bước 7b.**

### Bước 7b — Chỉ cập nhật cửa sổ **sau khi** mở niêm phong thành công

**Làm gì:** Bình chỉ ghi số vào sổ **sau khi** đã kiểm tra dấu niêm phong là thật. Thư mang dấu giả thì vứt ngay, không được ghi gì vào sổ.

**Về kỹ thuật:** chỉ đánh dấu số thứ tự là "đã thấy" sau khi giải mã và kiểm tra tag thành công.

**Trong code:** `p2p-core/…/protocol/EnvelopeCodec.java:120`: `replayWindow.markSeen(sequence)` nằm **sau** lời gọi giải mã; kiểm tra, giải mã và đánh dấu nằm trong cùng một khối `synchronized`.

**Đáp ứng yêu cầu của Bước 7:** R1 (gói rác không qua được kiểm tra tag nên không chạm vào cửa sổ), R2 (khối `synchronized`).

Đến đây **đường truyền đã kín**: Minh không đọc, không sửa, không giả mạo, không phát lại được.

**→ Lỗ hổng:** nhưng **người ở đầu kia của đường truyền có đúng là Bình không?**

**Phân tích vấn đề:**
- Quay lại Bước 2: các mẩu công khai (public key) đi qua bưu điện. Bước 2 chỉ bảo đảm "hai người đang trao đổi mẩu công khai với nhau có chung kết quả", **không** bảo đảm người kia là ai.
- Nếu bưu điện bị mua chuộc, nó **tráo mẩu công khai của cả hai bằng mẩu công khai của Minh**. An tạo bí mật chung với Minh, Bình cũng tạo bí mật chung với Minh. Minh đứng giữa: mở thư của An bằng bí mật An–Minh, đọc, rồi khoá lại bằng bí mật Minh–Bình và chuyển tiếp (tấn công kẻ đứng giữa, MITM).
- Mọi bước từ 3 đến 7b vẫn chạy hoàn hảo, chỉ có điều là chạy giữa An với Minh và giữa Minh với Bình. Không bước nào phát hiện được, vì không bước nào kiểm tra **danh tính**.

**Để giải quyết thì cần gì:**
- **R1:** mỗi người có một **danh tính bền vững** (dùng lâu dài, không đổi mỗi buổi), để người khác nhận ra.
- **R2:** chứng minh được "tôi chính là chủ của danh tính này" mà **không để lộ** bí mật của danh tính.
- **R3:** lời chứng minh phải **gắn với đúng hai mẩu công khai của buổi này**. Nếu không, Minh chỉ cần chuyển tiếp nguyên lời chứng minh thật của Bình là qua mặt được.
- **R4:** kiểm tra **tự động**, không bắt người dùng làm gì.

**→ Sinh ra Bước 8.**

---

### Bước 8 — Chữ ký danh tính gắn với phiên (`PEER_IDENTITY`)

**Làm gì:** mỗi người có một **chữ ký tay** riêng, dùng cả đời. Ngay khi nhận được mẩu công khai của nhau, An viết một tờ giấy: *"Trong buổi hôm nay, đây là mẩu tôi gửi đi và đây là mẩu tôi nhận được"*, chép lại cả hai mẩu, rồi ký tên. Bình cũng làm vậy. Mỗi người đối chiếu hai mẩu trên tờ giấy với đúng hai mẩu mình đang có, rồi kiểm tra chữ ký.

**Về kỹ thuật:** chữ ký số ECDSA P-256 bằng khoá danh tính lâu dài. Nội dung được ký là `phòng | peerId | SHA-256(public key ECDH của mình) | SHA-256(public key ECDH của bên kia)`; người nhận kiểm tra bằng đúng các public key mà máy mình nhận được trong phiên.

**Trong code:**
- Ký: `client/…/session/P2pChatSession.java:776` (`sendIdentity`) → `client/…/session/PeerIdentityService.java:58` → `crypto/…/IdentitySignatureService.java:76` (`handshakeChallengeFor`), `:93` (`sign`).
- Kiểm tra: `client/…/session/P2pChatSession.java:805` (`handlePeerIdentity`) → `PeerIdentityService.java:73` → `IdentitySignatureService.verify` (dòng 114). Gửi lại sau 0,4 s, 1,2 s và 2,5 s nếu mất gói.

**Đáp ứng yêu cầu của Bước 7b:**
- **R1:** khoá danh tính sinh một lần cho mỗi máy, lưu ở `~/.chat-p2p-java/identity.properties` (mã hoá DPAPI trên Windows).
- **R2:** chữ ký số: ai cũng kiểm tra được bằng public key danh tính, nhưng chỉ chủ private key mới ký được.
- **R3:** nội dung được ký chứa SHA-256 của **cả hai** public key ECDH. Nếu Minh đã tráo mẩu công khai, chữ ký thật của Bình được ký lên mẩu *thật* của Bình, không khớp với mẩu mà An đang nhận, nên **không chuyển tiếp được**.
- **R4:** chạy tự động ngay sau khi trao khoá; khớp thì giao diện hiện "Đã xác thực".

**→ Lỗ hổng:** chữ ký chứng minh "người kia giữ khoá danh tính này", **không** chứng minh "khoá danh tính này là của Bình".

**Phân tích vấn đề:**
- Minh không cần giả chữ ký của Bình. Minh **tự ký bằng khoá danh tính của chính Minh**, đúng lên các mẩu công khai mà An nhận. Chữ ký đó **hoàn toàn hợp lệ**, nên giao diện vẫn hiện "Đã xác thực", chỉ có điều vân tay là của Minh.
- An không có cách nào tự biết khoá danh tính nào thuộc về Bình: không có cơ quan chứng nhận (như CA trong HTTPS) xác nhận "khoá này là của Bình".
- Với chế độ khách, Minh còn đặt được **cùng tên hiển thị** với Bình.
- Đây là hạn chế "tin ở lần gặp đầu" (TOFU) của mọi hệ không có hạ tầng khoá công khai; Signal và WhatsApp cũng vậy.

**Để giải quyết thì cần gì:**
- **R1:** một **kênh khác**, mà Minh không kiểm soát được, để An và Bình đối chiếu khoá danh tính (gặp mặt, gọi điện…).
- **R2:** thứ đem ra đối chiếu phải **ngắn, dễ đọc**, không bắt đọc cả khoá dài.
- **R3:** đã đối chiếu một lần thì **ghi nhớ**, và lần sau khoá thay đổi thì **cảnh báo**.

**→ Sinh ra Bước 9.**

---

### Bước 9 — So vân tay và ghi nhớ khoá tin cậy

**Làm gì:** An gọi điện cho Bình, hai người đọc cho nhau nghe **"số seri" chữ ký** của mình (một dãy số ngắn). Khớp thì An ghi vào sổ tay: *"Chữ ký của Bình có số seri này."* Lần sau gặp một người cũng xưng là Bình mà số seri chữ ký khác trong sổ, An lập tức nghi ngờ. Zalo và WhatsApp có mục "mã bảo mật" cũng vì lý do này.

**Về kỹ thuật:** mã vân tay là SHA-256 của khoá danh tính, hiển thị thành 60 chữ số và mã QR. Bấm "Khớp" để ứng dụng ghi nhớ; lần sau người cùng tên mà khoá khác thì hiện cảnh báo "Khoá đã đổi".

**Trong code:**
- Vân tay: `crypto/…/Fingerprint.java:24` (SHA-256 của khoá danh tính); mã an toàn 60 chữ số và mã QR ở `client/…/view/SafetyCode.java:24, 38, 58`; hộp thoại ở `client/…/view/FingerprintDialog.java:51`.
- Ghi nhớ: bấm "Khớp" → `client/…/view/RoomController.java:353` → `client/…/util/TrustedPeers.java:68` (`trust`). Lần sau `TrustedPeers.status` (dòng 56) trả về `CHANGED` và `RoomController.java:329` (`warnIfKeyChanged`) hiện cảnh báo.

**Đáp ứng yêu cầu của Bước 8:** R1 (kênh khác do người dùng tự chọn), R2 (mã 60 chữ số chia nhóm, hoặc quét QR), R3 (`TrustedPeers` ghi nhớ và cảnh báo "Khoá đã đổi"). Nếu người dùng chịu so vân tay, Minh bị lộ ngay lần đầu; nếu đã từng so, lần sau Minh xen vào sẽ bị cảnh báo.

**→ Lỗ hổng:** vẫn còn hai chỗ hở.

**Phân tích vấn đề thứ nhất — phát hiện được kẻ gian nhưng vẫn gửi thư cho nó:**
- Khi chữ ký sai (Minh làm vụng), ứng dụng **chỉ hiện cảnh báo** rồi dừng xử lý; người đó **vẫn nằm trong danh sách người nhận**, nên mọi tin nhóm An gửi sau đó vẫn được mã hoá **cho Minh**. Giống bảo vệ hô lên "người này giả danh shipper!" nhưng vẫn đưa bưu kiện.
- Trong phòng công khai, người mới được **nhận vào phòng ngay khi trao khoá xong**, *trước khi* kiểm tra chữ ký. Tin gửi đúng lúc đó đi tới người chưa được kiểm tra.
- *Trong code:* `client/…/session/P2pChatSession.java:358` (nhận vào phòng ngay khi trao khoá xong), `:342` (danh sách người nhận chỉ xét "đã vào phòng", không xét "đã xác thực"), `:797` (chữ ký sai chỉ cảnh báo rồi `return`).

**Phân tích vấn đề thứ hai — một chìa dùng cả buổi:**
- `EnvelopeCodec` được tạo **một lần** khi trao khoá xong và dùng tới khi kết nối đóng (`p2p-core/…/PeerConnection.java:274`).
- Nếu Minh đã ghi lại toàn bộ gói tin (dù chưa đọc được), rồi giữa chừng lấy được chìa (ví dụ máy An dính mã độc đọc được bộ nhớ), Minh giải mã được **toàn bộ buổi đó**, cả phần trước lẫn phần sau thời điểm lộ chìa.
- Các buổi khác vẫn an toàn, vì mỗi buổi nghĩ ra bí mật riêng mới (Bước 2, R5).

**Để giải quyết thì cần gì:**
Với vấn đề thứ nhất:
- **R1:** chỉ gửi tin cho người **đã xác thực xong**; người có chữ ký sai phải bị **loại khỏi danh sách nhận và ngắt kết nối**.
- **R2:** tin gửi trong lúc người mới **chưa xác thực xong** phải được xử lý rõ ràng (xếp hàng chờ, hoặc chưa gửi cho người đó).

Với vấn đề thứ hai:
- **R3:** chìa phải **thay đổi theo thời gian** trong một buổi, để lộ chìa lúc này không làm lộ các đoạn khác.
- **R4:** đổi chìa mà **không phải trao khoá ECDH lại** và không làm gián đoạn cuộc trò chuyện.

**→ Sinh ra Bước 10 và Bước 11.**

---

### Bước 10 — *(đề xuất, chưa làm)* Chỉ gửi tin cho người đã xác thực; ngắt kết nối nếu chữ ký sai

**Làm gì:** bảo vệ chung cư chỉ giao bưu kiện cho người **đã đối chiếu giấy tờ xong**. Ai đang chờ đối chiếu thì bưu kiện để tạm ở quầy. Ai bị phát hiện giấy tờ giả thì bị mời ra ngoài và không được giao gì nữa.

**Trong code (dự kiến):** trong `admittedPeerIds()` và `sendDirectMessage`, chỉ gửi cho người đã có trong `verifiedRemoteFingerprints`. Ở nhánh chữ ký sai (`P2pChatSession.java:818`), xoá người đó khỏi danh sách và đóng kết nối. Tin gửi trong lúc chưa xác thực xong thì xếp hàng chờ, hoặc chưa gửi cho người đó.

**Đáp ứng yêu cầu của Bước 9:** R1 và R2.

### Bước 11 — *(đề xuất, chưa làm)* Đổi chìa định kỳ trong buổi nói chuyện

**Làm gì:** thay vì dùng một cặp con dấu suốt cả buổi, cứ mỗi 10 phút hai người cùng đổi sang một **cặp dấu mới**, làm theo cùng công thức từ kết quả chung ban đầu kèm số "đợt". Lỡ mất một con dấu thì kẻ gian chỉ giả được thư trong đúng đợt đó.

**Trong code (dự kiến):** sau N gói hoặc N phút, chạy thêm `Hkdf.expand` với nhãn mới (kèm số "thế hệ khoá") để có khoá mới; hai bên chuyển sang khoá mới theo cùng quy tắc, không cần trao khoá ECDH lại.

**Đáp ứng yêu cầu của Bước 9:** R3 và R4 (một phần). Lộ chìa lúc này chỉ làm lộ một đoạn của buổi nói chuyện. Signal đi xa hơn: đổi chìa sau **mỗi tin nhắn** (Double Ratchet), nhưng phức tạp hơn nhiều.

---

### Tóm tắt chuỗi

```
B0 gửi thẳng ──đọc / sửa / giả──► B1 chìa từ server ──server biết chìa──► B2 trao khoá ECDH
──(có chìa, chưa mã hoá)──► B3 AES thường ──lật bit không bị phát hiện──► B4 AES-GCM
──trùng nonce, phản chiếu, chìa thô──► B5 HKDF tách 2 chìa ──(chọn nonce)──► B6 số thứ tự
──phát lại──► B7 cửa sổ 1024 ──đẩy cửa sổ──► B7b đánh dấu sau khi giải mã
──kẻ đứng giữa tráo mẩu công khai──► B8 chữ ký gắn phiên ──kẻ gian tự ký──► B9 so vân tay
──vẫn gửi cho kẻ gian / một chìa cả buổi──► B10, B11 (đề xuất)
```

| Lỗ hổng | Được vá ở | Trạng thái |
|---|---|---|
| Đọc / sửa / giả mạo trên đường truyền | Bước 2 → 4 | ✅ |
| Server biết chìa / chìa đi qua mạng / mật khẩu yếu | Bước 2 | ✅ |
| Sửa gói mà không bị phát hiện | Bước 4 | ✅ |
| Trùng nonce, phản chiếu, chìa thô | Bước 5, 6 | ✅ |
| Phát lại tin cũ | Bước 7 | ✅ |
| Đẩy cửa sổ chống phát lại | Bước 7b | ✅ |
| Kẻ đứng giữa giả làm chính Bình | Bước 8 | ✅ |
| Kẻ đứng giữa tự ký bằng khoá của mình | Bước 9 | ⚠️ Chỉ khi người dùng so vân tay |
| Vẫn gửi tin cho người có chữ ký sai / chưa xác thực | Bước 10 | ❌ Chưa làm |
| Một chìa dùng cả buổi | Bước 11 | ❌ Chưa làm |

---

## Hành trình của một tin nhắn "Chào Bình" — từ đầu đến cuối

| # | Ví dụ đời thường | Code thực hiện |
|---|---|---|
| 1 | An viết thư | `client/…/view/RoomController.java` → `session.sendGroupMessage` / `sendDirectMessage` (`P2pChatSession.java:196` / `:220`) |
| 2 | Chọn người nhận | `P2pChatSession.admittedPeerIds()` (dòng 344), hoặc đúng một người với tin riêng |
| 3 | Ghi sổ "đã gửi, chờ hồi âm" | `MessageDeliveryTracker.track` (gửi lại tối đa 6 lần, mỗi lần 500 ms nếu chưa có ACK) |
| 4 | Bỏ thư vào phong bì có nhãn loại thư | `RoomSession.sendTo` (dòng 283) tìm đúng kết nối tới người nhận → `PeerConnection.send(type, namespace, payload)` đóng thành `Envelope(MESSAGE, namespace, ...)` |
| 5 | Khoá hộp, đánh số, niêm phong | `EnvelopeCodec.encode` (nonce ở dòng 71–73) → `AesGcmCipher.encryptWithNonce` (dòng 52) |
| 6 | Cắt nhỏ cho vừa khe thư | `P2pDataChannel`: chia mảnh ≤ 1150 byte |
| 7 | Gửi theo đường đi thẳng (hoặc qua TURN) | `P2pDataChannel.java:111`: `socket.send(...)` |
| 8 | Bình nhận và ghép mảnh | Vòng nhận của `P2pDataChannel` (chỉ nhận từ đúng địa chỉ đã chọn) |
| 9 | Kiểm tra số thứ tự và niêm phong, mở hộp | `PeerConnection.handleIncoming` → `EnvelopeCodec.decode` (dòng 105–120) |
| 10 | Kiểm tra người gửi đã được "cho vào nhà" | `P2pChatSession.admittedOnly` (dòng 455) |
| 11 | Đọc thư, xác định tác giả là **người gửi thật** (không tin tên ghi trong thư) | `P2pChatSession.handleMessage`, tác giả = `peerId` của kết nối |
| 12 | Gửi hồi âm "đã nhận" | `MESSAGE_ACK` → bên An: `MessageDeliveryTracker.acknowledge`, giao diện chuyển sang "Đã nhận" |
| 13 | Hiện thư lên màn hình | `RoomController.onMessageReceived` → `TranscriptCell` → `MarkdownParser` |
