# CƠ CHẾ TÌM NHAU VÀ KẾT NỐI P2P — GIẢI THÍCH BẰNG VÍ DỤ ĐỜI THƯỜNG, KÈM ĐỐI CHIẾU CODE

**Ngày:** 22/11/2026 · **Phiên bản code:** nhánh `main`, commit `a0ee278` (số dòng bên dưới đúng với bản này)

Tài liệu này giải thích làm sao hai máy, mỗi máy nằm sau router của mình và không biết địa chỉ của nhau, vẫn nói chuyện trực tiếp được với nhau. Cách trình bày là **chuỗi thiết kế**: mỗi bước được phân tích để tìm lỗ hổng, lỗ hổng đó sinh ra bước tiếp theo; tại mỗi vấn đề, các phương án được so sánh trước khi chọn. Khi kênh đã thông, việc mã hoá trên kênh được giải thích trong [Co-che-ma-hoa-tin-nhan.md](Co-che-ma-hoa-tin-nhan.md). Chi tiết kỹ thuật và số đo nằm trong [Tai-lieu-ky-thuat.md](Tai-lieu-ky-thuat.md), mục E.6 và I.3.

Đường dẫn rút gọn:
- `p2p-core/…` = `p2p-core/src/main/java/com/datn/chatp2p/p2p/`
- `server/…` = `signaling-server/src/main/java/com/datn/chatp2p/signaling/`
- `client/…` = `client-javafx/src/main/java/com/datn/chatp2p/client/`

---

## Nhân vật

| Trong ví dụ | Trong hệ thống | Trong code |
|---|---|---|
| **An**, **Bình** | Hai máy chạy ứng dụng | `p2p-core/…/RoomSession.java` (mỗi máy một phiên phòng) |
| **Nhà của An, nhà của Bình** | Mạng nội bộ phía sau router (Wi-Fi nhà, 4G) | — |
| **Bác bảo vệ cổng nhà** | Router làm NAT: mọi gói ra vào đều qua đây | — |
| **Bưu điện** | Signaling server: chỉ giới thiệu hai bên lúc đầu | `server/…/ws/SignalingWebSocketHandler.java` |
| **Trạm hỏi địa chỉ** | Máy chủ STUN công cộng (`stun.l.google.com`) | `client/…/session/P2pChatSession.java:61` |
| **Người chuyển thư trung gian** | Máy chủ TURN (coturn trên VPS của dự án) | `server/…/turn/TurnCredentialService.java` |
| **Minh** | Kẻ muốn chen vào, nghe lén hoặc phá | — |

---

## Chuỗi thiết kế

**Cách đọc.** Mỗi bước có cùng khuôn: **Làm gì** (ví dụ đời thường, không dùng thuật ngữ kỹ thuật) → **Về kỹ thuật** và **Trong code** → **Đáp ứng yêu cầu** của bước trước → **Lỗ hổng** → **Phân tích vấn đề** → **Để giải quyết thì cần gì** (R1, R2…) → sinh ra bước tiếp theo. Bước đánh dấu *(không dùng trong code)* là cách "dễ nghĩ ra", được nêu để thấy vì sao phải bỏ. Bước 6–7 là **đề xuất, chưa làm**.

---

### Bước 0 — Gửi thẳng tới nhà nhau *(không dùng được)*

**Làm gì:** An muốn gửi thư thẳng tới nhà Bình, không qua ai. Nhưng An **không biết địa chỉ nhà Bình**, và địa chỉ đó còn có thể thay đổi mỗi lần Bình chuyển chỗ (từ Wi-Fi nhà sang 4G, sang quán cà phê).

**Về kỹ thuật:** muốn gửi gói UDP thì phải biết địa chỉ IP và cổng của máy kia. Địa chỉ này đổi theo mạng đang dùng, và đa số máy không có địa chỉ công khai riêng.

**Lỗ hổng:** không biết gửi tới đâu.

**Phân tích vấn đề:**
- An và Bình không có cách nào tự biết địa chỉ của nhau trước khi liên lạc được với nhau.
- Hỏi nhau qua kênh khác (gọi điện đọc địa chỉ cho nhau) thì bất tiện, và địa chỉ đổi liên tục.
- Cần một nơi **cả hai đều biết sẵn** để gặp nhau lần đầu.

**Để giải quyết thì cần gì:**
- **R1:** một điểm hẹn cố định mà mọi người đều biết, để hai bên báo cho nhau biết mình đang ở đâu.
- **R2:** điểm hẹn chỉ làm nhiệm vụ giới thiệu, **không** đứng giữa chuyển mọi lá thư (nếu không thì không còn là nói chuyện trực tiếp).

**Các phương án:**
- **Nhập địa chỉ bằng tay:** bất tiện, địa chỉ đổi liên tục, đa số người dùng không biết địa chỉ của mình.
- **Một máy chủ chuyển toàn bộ tin nhắn** (như các ứng dụng chat thông thường): máy chủ phải gánh toàn bộ băng thông, và thấy được ai nói với ai, lúc nào, bao nhiêu.
- **Dùng mạng có sẵn của người khác** (chitchatter dùng các máy chủ BitTorrent công cộng làm điểm hẹn): không phải tự vận hành, nhưng phụ thuộc dịch vụ ngoài và khó kiểm soát.
- **Một máy chủ riêng chỉ làm việc giới thiệu**, xong là rút lui: nhẹ, tự kiểm soát được, đúng tinh thần P2P.

Dự án chọn phương án cuối. **→ Sinh ra Bước 1.**

---

### Bước 1 — Bưu điện giới thiệu hai bên

**Làm gì:** An và Bình cùng biết địa chỉ một bưu điện. Ai vào phòng thì báo với bưu điện "tôi vào phòng X". Bưu điện trả lời người mới vào: "trong phòng đang có những người này". Người mới vào viết cho từng người có sẵn một **thư hỏi đường** ghi "đây là những địa chỉ tôi có thể nhận thư", nhờ bưu điện chuyển; người kia viết thư trả lời tương tự. Sau đó hai bên tự thử gửi thẳng cho nhau. Bưu điện không đọc nội dung thư hỏi đường; nó chỉ chuyển cho đúng người.

**Về kỹ thuật:** signaling server (Spring Boot + WebSocket, `wss://chat.zingme.fun/ws`). Các bản tin: `JOIN` → server trả `PEER_LIST` cho người mới và gửi `PEER_JOINED` cho người cũ → người mới gửi `OFFER` (danh sách địa chỉ và mật khẩu ICE) cho từng người có sẵn → người kia trả `ANSWER`. Server chỉ chuyển tiếp, không đọc phần `payload`.

**Trong code:**
- `p2p-core/…/RoomSession.java:182` (`join`): kết nối WebSocket và gửi `JOIN`.
- `RoomSession.java:329` (`handlePeerList`): với mỗi người có sẵn, gọi `connectAsOfferer` (dòng 410). Người **mới vào** luôn là người chủ động gửi OFFER, nên hai bên không bao giờ cùng gửi OFFER cho nhau.
- `RoomSession.java:420` (`handleOffer`) tạo ANSWER; dòng 442 (`handleAnswer`) nhận ANSWER.
- `server/…/ws/SignalingWebSocketHandler.java`: chuyển tiếp `OFFER` / `ANSWER` / `ICE_CANDIDATE` cho đúng người, chỉ khi người gửi thực sự đang ở trong phòng.

**Đáp ứng yêu cầu của Bước 0:** R1 (bưu điện là điểm hẹn cố định), R2 (sau khi giới thiệu, thư từ đi thẳng giữa hai nhà, không qua bưu điện).

**Lỗ hổng:** địa chỉ An biết về mình là **địa chỉ trong nhà**, người ngoài không gửi tới được; và bác bảo vệ cổng nhà Bình **không cho thư lạ đi vào**.

**Phân tích vấn đề:**
- Trong nhà, máy của An có địa chỉ kiểu "phòng 3, nhà số 192.168.1.x". Địa chỉ đó chỉ có nghĩa **bên trong nhà An**; ở ngoài có hàng triệu nhà khác cũng có "phòng 3".
- Mọi thư ra vào nhà đều đi qua bác bảo vệ cổng (router). Khi An gửi thư ra ngoài, bác ghi nhớ "phòng 3 vừa gửi thư tới địa chỉ Y" và ghi lên phong bì **địa chỉ cổng nhà** thay cho địa chỉ phòng. Thư từ Y gửi về thì bác chuyển vào đúng phòng 3.
- Nhưng thư từ **một địa chỉ lạ** chưa từng được An gửi tới thì bác **chặn lại**. Đây là lý do Bình không thể tự gửi thẳng vào nhà An.
- An cũng **không biết địa chỉ cổng nhà mình** trông thế nào khi nhìn từ ngoài vào, nên không ghi được nó vào thư hỏi đường.

**Để giải quyết thì cần gì:**
- **R1:** An phải biết **địa chỉ cổng nhà mình** (địa chỉ nhìn từ ngoài vào) để báo cho Bình.
- **R2:** phải làm cho bác bảo vệ ở **cả hai nhà** coi thư của bên kia là thư "đã hẹn", không phải thư lạ.
- **R3:** nếu hai nhà ở cùng một khu (cùng mạng Wi-Fi), vẫn nên đi đường ngắn nhất trong khu, không cần vòng ra ngoài.

**→ Sinh ra Bước 2.**

---

### Bước 2 — Hỏi địa chỉ cổng nhà và cùng mở lối qua cổng

**Làm gì:**
1. An gửi một lá thư tới **trạm hỏi địa chỉ** ở ngoài, hỏi: "thư của tôi tới chỗ bạn mang địa chỉ người gửi là gì?". Trạm trả lời đúng địa chỉ cổng nhà An. Bình cũng làm vậy.
2. Trong thư hỏi đường, mỗi người ghi **mọi địa chỉ có thể nhận thư**: địa chỉ trong nhà (để dùng khi hai người ở cùng khu), và địa chỉ cổng nhà vừa hỏi được.
3. Hai người **cùng lúc** gửi thư thử cho nhau theo từng địa chỉ. Khi An gửi thư ra tới địa chỉ cổng nhà Bình, bác bảo vệ nhà An ghi nhớ "An đã gửi thư tới nhà Bình". Nhờ đó, thư thử của Bình gửi tới được coi là thư trả lời, và được cho vào. Bên nhà Bình cũng vậy.
4. Cặp địa chỉ nào thư thử đi và về được thì chọn cặp đó, ưu tiên đường ngắn nhất.

**Về kỹ thuật:** ICE (RFC 8445) do thư viện ice4j thực hiện. Các loại địa chỉ (candidate): `host` (địa chỉ trong mạng nội bộ) và `srflx` (địa chỉ public hỏi được qua STUN). Hai bên gửi gói kiểm tra kết nối (connectivity check) song song, vừa thử từng cặp vừa mở lối qua NAT, gọi là UDP hole punching. Cổng UDP dùng trong dải 10000–11000.

**Trong code** (`p2p-core/…/ice/IceP2pConnectionEstablisher.java`):
- Dòng 108–109: dải cổng `MIN_PORT = 10_000`, `MAX_PORT = 11_000`.
- Dòng 176: `StunCandidateHarvester`, máy chủ STUN `stun.l.google.com:19302` (khai báo ở `client/…/session/P2pChatSession.java:61`).
- Dòng 344 (`isAdvertisable`): chỉ quảng bá địa chỉ IPv4 dùng được; bỏ loopback, link-local, multicast, để ICE không mất thời gian thử các card mạng ảo.
- Dòng 227 (`createOffer`, bên điều khiển) và 241 (`createAnswer`): đóng gói mật khẩu ICE và danh sách địa chỉ; dòng 262 (`acceptAnswer`) bắt đầu kiểm tra.
- Dòng 364–391: khi ICE báo `COMPLETED`, lấy cặp địa chỉ đã chọn (`getSelectedPair`), rồi tạo kênh `P2pDataChannel` trên đúng cặp đó.

**Đáp ứng yêu cầu của Bước 1:**
- **R1:** STUN cho biết địa chỉ public của mình.
- **R2:** hai bên cùng gửi gói kiểm tra, nên NAT ở cả hai phía đều ghi nhớ "đã liên lạc với bên kia" và cho gói trả lời đi vào.
- **R3:** địa chỉ `host` được thử luôn; cùng mạng thì đi thẳng trong mạng.

**Lỗ hổng:** có những bác bảo vệ **khó tính** mà cách mở lối này không qua được.

**Phân tích vấn đề:**
- **Bác bảo vệ đổi địa chỉ theo từng nơi nhận** (NAT đối xứng, rất hay gặp trên mạng 4G): thư gửi tới trạm hỏi địa chỉ mang một địa chỉ cổng, nhưng thư gửi tới nhà Bình lại mang **một địa chỉ cổng khác**. Địa chỉ An đã báo cho Bình là sai, nên Bình gửi tới đó sẽ bị chặn. Đo thực tế ngày 20/11/2026 trên mạng 4G của nhóm: hỏi cùng lúc hai trạm STUN thì nhận về **hai địa chỉ public khác nhau**, tức đúng là NAT đối xứng.
- **Có nơi cấm hẳn thư loại này** (mạng trường học, công ty chặn UDP đi ra ngoài).
- Khi đó, dù đã làm đúng mọi bước, An và Bình **không có đường đi thẳng**. Hai người dùng 4G gần như chắc chắn rơi vào trường hợp này.

**Để giải quyết thì cần gì:**
- **R1:** một đường dự phòng **luôn đi được** khi đường thẳng thất bại.
- **R2:** đường dự phòng **không được đọc** nội dung thư.
- **R3:** chỉ dùng đường dự phòng **khi thật sự cần**, vì nó chậm hơn và tốn tài nguyên của người vận hành.

**Các phương án:**
- **Để bưu điện chuyển luôn thư:** bưu điện biết hết ai nói với ai, nặng tải, và trộn lẫn hai việc giới thiệu với chuyển thư.
- **Một người chuyển thư trung gian riêng**, đặt ở chỗ ai cũng gửi tới được; cả hai bên chủ động gửi thư ra cho người đó, nên bác bảo vệ ở hai nhà không chặn. Đây là cách chuẩn (TURN), có phần mềm mã nguồn mở sẵn (coturn).

**→ Sinh ra Bước 3.**

---

### Bước 3 — Người chuyển thư trung gian (TURN)

**Làm gì:** dự án thuê một **người chuyển thư trung gian** đứng ở một chỗ ai cũng gửi tới được. Mỗi người xin người trung gian một hộp thư riêng, rồi ghi địa chỉ hộp thư đó vào thư hỏi đường như một địa chỉ "dự phòng". Nếu thử mọi đường thẳng đều thất bại, hai người gửi thư qua hộp thư này. Người trung gian chỉ chuyển những phong bì đã niêm phong; nội dung bên trong vẫn được khoá bằng chìa của An và Bình.

**Về kỹ thuật:** máy chủ TURN (coturn trên VPS, UDP cổng 3478) cấp một địa chỉ relay cho mỗi bên (candidate loại `relay`). ICE vẫn ưu tiên các cặp đi thẳng; relay chỉ được chọn khi các cặp khác thất bại. Dữ liệu qua TURN vẫn là gói đã mã hoá AES-GCM, nên TURN không đọc được.

**Trong code:**
- `p2p-core/…/ice/IceP2pConnectionEstablisher.java:185`: `TurnCandidateHarvester` dùng tài khoản TURN được truyền vào.
- Dòng 388: `isRelayed(selectedPair)` cho biết kết nối có đi qua TURN không (`IceConnectionStats.usingRelay()`).

**Đáp ứng yêu cầu của Bước 2:** R1 (cả hai bên đều chủ động gửi ra người trung gian), R2 (gói đã mã hoá đầu-cuối), R3 (ICE chỉ chọn relay khi đường thẳng thất bại).

**Kết quả đo thực tế qua TURN** (20/11/2026, mạng 4G): cấp địa chỉ relay mất 221 ms; thiết lập ICE 354 ms; thời gian khứ hồi trung vị 86 ms; 200 tin × 1 KB mất 1,5% tính cả hai chiều.

**Lỗ hổng:** người chuyển thư trung gian **tốn tiền và băng thông** của người vận hành, nên phải có mật khẩu mới được dùng. Mật khẩu đó để ở đâu?

**Phân tích vấn đề:**
- Nếu để **một mật khẩu cố định nằm sẵn trong ứng dụng**, ai tải ứng dụng về cũng moi ra được, rồi dùng người trung gian vào việc riêng (chuyển dữ liệu khác, tốn băng thông của dự án).
- Chuyện này **đã xảy ra thật**: mật khẩu TURN cũ từng nằm trong lịch sử git của repo công khai (commit `fa173d1`).
- Đổi mật khẩu cố định thì phải phát hành lại ứng dụng cho tất cả mọi người.
- Bắt người dùng tự cấu hình TURN thì quá khó với người dùng bình thường.

**Để giải quyết thì cần gì:**
- **R1:** **không có mật khẩu cố định** nào nằm trong ứng dụng.
- **R2:** mỗi người dùng nhận một mật khẩu **tạm thời**, tự hết hạn sau một thời gian.
- **R3:** người trung gian tự kiểm tra được mật khẩu tạm thời mà **không cần hỏi lại** ai (không phải lưu danh sách mật khẩu).
- **R4:** người dùng **không phải cấu hình gì**.
- **R5:** người lạ không xin được mật khẩu hàng loạt.

**→ Sinh ra Bước 4.**

---

### Bước 4 — Mật khẩu tạm thời cho người chuyển thư trung gian

**Làm gì:** bưu điện và người chuyển thư trung gian cùng giữ một **mật mã chung** mà không ai khác biết. Khi An vào phòng, ứng dụng tự xin bưu điện một vé dùng người trung gian. Bưu điện ghi trên vé "hết hạn lúc X giờ", rồi đóng một dấu tạo từ mật mã chung và dòng chữ đó. Người trung gian nhìn vé: còn hạn, và dấu đúng với mật mã chung, thì cho dùng. Hết 6 giờ, vé tự vô hiệu. Bưu điện chỉ phát vé cho mỗi người gửi tối đa 20 lần mỗi phút.

**Về kỹ thuật:** cơ chế "TURN REST API" của coturn (`use-auth-secret`): username = `<thời điểm hết hạn>:chat-p2p`, password = Base64(HMAC-SHA1(static-auth-secret, username)), hạn mặc định 21600 giây (6 giờ). Client gọi `GET /turn-credentials` qua HTTPS. Thứ tự ưu tiên ở client: cấu hình TURN cố định (để test) → tài khoản tạm thời từ server → không có TURN (chỉ đi thẳng).

**Trong code:**
- `server/…/turn/TurnCredentialService.java`: tạo username / password (mô tả ở dòng 17–18), hạn ở dòng 41 (`ttl-seconds`, mặc định 21600).
- `server/…/turn/TurnController.java`: `GET /turn-credentials`, giới hạn 20 lần/phút/IP bằng `RequestRateLimiter` (từ 22/11 có tự dọn bộ nhớ và gộp IPv6 theo dải /64).
- `client/…/session/AuthClient.java:100` (`turnServers`): xin tài khoản tạm thời, chờ tối đa 5 giây.
- `client/…/session/P2pChatSession.java:131-146`: chọn nguồn TURN theo thứ tự ưu tiên rồi truyền vào `RoomSession`.

**Đáp ứng yêu cầu của Bước 3:**
- **R1:** ứng dụng không chứa mật khẩu TURN nào.
- **R2:** mỗi vé hết hạn sau 6 giờ; mật khẩu cũ bị lộ từ git đã vô hiệu.
- **R3:** người trung gian tự tính lại dấu bằng mật mã chung, không cần lưu danh sách vé.
- **R4:** ứng dụng tự xin vé khi vào phòng.
- **R5:** 20 lần/phút/IP. Việc không bắt buộc đăng nhập mới xin được vé là **có chủ đích**: ai cũng tự đăng ký tài khoản được, nên bắt đăng nhập cũng không chặn được người lạ (đợt kiểm định 22/11 đã phân tích và kết luận như vậy). Giới hạn lạm dụng thật sự nằm ở cấu hình quota của coturn.

**Lỗ hổng:** đường đi thẳng đã mở, nhưng **cổng nhà An giờ đang để ngỏ cho một địa chỉ**. Ai gửi thư mạo danh địa chỉ đó cũng lọt vào được?

**Phân tích vấn đề:**
- Kênh UDP nhận được **mọi gói** gửi tới cổng của mình, không riêng gì gói của Bình.
- Minh có thể gửi gói rác hoặc gói giả vào đúng cổng đó, để phá hoặc để thử chen vào cuộc trò chuyện.
- Trong thư, "địa chỉ người gửi" là do người gửi tự ghi, nên **giả mạo được**.

**Để giải quyết thì cần gì:**
- **R1:** chỉ nhận thư đến từ **đúng địa chỉ** của cặp đã chọn; thư từ chỗ khác thì vứt ngay, không xử lý gì thêm.
- **R2:** vì địa chỉ người gửi giả mạo được, cần thêm một lớp **chứng minh thư đúng là của Bình**, không chỉ dựa vào địa chỉ.

**→ Sinh ra Bước 5.**

---

### Bước 5 — Chỉ nhận thư từ đúng địa chỉ đã chọn, và mọi thư đều phải mở được bằng chìa chung

**Làm gì:** bác bảo vệ trong nhà An được dặn: chỉ nhận thư ghi gửi từ đúng địa chỉ của Bình đã thống nhất; thư từ địa chỉ khác thì vứt luôn. Nhưng vì địa chỉ người gửi viết tay giả được, An còn kiểm tra thêm: thư phải mở được bằng chìa chung của An và Bình, và mang dấu niêm phong đúng. Thư không qua được bước này thì vứt, dù ghi đúng địa chỉ.

**Về kỹ thuật:** `P2pDataChannel` lọc gói theo địa chỉ nguồn của cặp ICE đã chọn. Đây không phải xác thực thật (địa chỉ nguồn UDP giả mạo được), nên lớp bảo vệ thật là mã hoá có xác thực AES-GCM ở tầng trên.

**Trong code:**
- `p2p-core/…/channel/P2pDataChannel.java:150–159`: socket **không** dùng `connect()` (vì ice4j cần socket chưa kết nối); thay vào đó, vòng nhận tự lọc bỏ gói không đến từ `remoteAddress`. Gửi đi thì luôn tới đúng `remoteAddress` (dòng 111).
- Lớp mã hoá và chống phát lại: xem [Co-che-ma-hoa-tin-nhan.md](Co-che-ma-hoa-tin-nhan.md), các bước 2–7b.

**Đáp ứng yêu cầu của Bước 4:** R1 (lọc theo địa chỉ), R2 (chỉ gói mở được bằng chìa chung và đúng niêm phong mới được dùng).

**Lỗ hổng:** kênh đã an toàn về nội dung, nhưng việc **nói chuyện trực tiếp** tự nó để lộ một số thứ, và còn vài tình huống chưa xử lý.

**Phân tích vấn đề thứ nhất — lộ địa chỉ nhà:**
- Để đi thẳng, An phải cho Bình biết địa chỉ cổng nhà mình, tức **địa chỉ IP thật**. Từ đó Bình có thể biết An đang ở tỉnh nào, dùng nhà mạng nào.
- Thư hỏi đường còn ghi cả **địa chỉ trong nhà** (192.168.x.x) để thử đường ngắn khi ở cùng mạng; mọi người trong phòng đều đọc được.
- Với phòng công khai, người lạ cũng nhận được những thông tin này. Đây là đặc tính chung của mọi ứng dụng P2P (cả WebRTC), không phải lỗi lập trình.

**Phân tích vấn đề thứ hai — đường dự phòng chưa đủ rộng:**
- Người trung gian hiện **chỉ nhận thư loại UDP qua cổng 3478**. Ở nơi cấm UDP (trường học, công ty), kể cả đường dự phòng cũng bị chặn, nên **không kết nối được**.
- Đo thực tế: khi **cả hai bên đều chỉ** dùng đường dự phòng (relay ↔ relay), kết nối thất bại với cấu hình coturn hiện tại.

**Phân tích thêm — khi bưu điện tạm mất liên lạc:** đây là điểm **đã xử lý tốt**. Mất kết nối tới bưu điện thì ứng dụng tự kết nối lại (chờ 1 giây, 2 giây, 4 giây… tối đa 30 giây) rồi báo lại "tôi vẫn ở phòng X"; những đường đi thẳng đã có vẫn hoạt động, vì không đi qua bưu điện (`p2p-core/…/signaling/WebSocketSignalingClient.java`). Còn trường hợp bưu điện bị chiếm rồi gửi thư hỏi đường giả để chen vào giữa thì đã được xử lý ở lớp xác thực danh tính (sửa ngày 22/11, xem `Tai-lieu-ky-thuat.md` mục J.3, vấn đề 1).

**Để giải quyết thì cần gì:**

Với vấn đề thứ nhất:
- **R1:** cho người dùng **chọn giấu địa chỉ**: chỉ đi qua người trung gian, không gửi địa chỉ thật cho ai.

Với vấn đề thứ hai:
- **R2:** đường dự phòng phải đi được cả ở nơi chỉ cho phép thư loại thông dụng (TCP, cổng 443 như web).

**Điểm đã làm tốt — người dùng biết mình đang đi đường nào:** cạnh tên mỗi người trong phòng hiện "Trực tiếp" hoặc "Qua relay", và đầu phòng hiện "N kết nối qua relay" nếu có. Ứng dụng lấy thông tin này từ cặp địa chỉ ICE đã chọn (`P2pChatSession.java:410` gọi `RoomSession.isRelayed`; hiển thị ở `view/PeerListCell.java:168` và `view/RoomController.java:1078`).

**→ Sinh ra Bước 6, 7.**

---

### Bước 6 — *(đề xuất, chưa làm)* Chế độ giấu địa chỉ: chỉ đi qua người chuyển thư trung gian

**Làm gì:** An bật tuỳ chọn "giấu địa chỉ". Trong thư hỏi đường, An chỉ ghi địa chỉ hộp thư ở người trung gian, không ghi địa chỉ nhà. Bình chỉ biết địa chỉ của người trung gian. Đổi lại, thư đi chậm hơn một chút.

**Trong code (dự kiến):** thêm một cờ trong cài đặt; khi bật, `IceP2pConnectionEstablisher` chỉ quảng bá candidate loại `relay` (không thêm `host` và `srflx`).

**Đáp ứng yêu cầu:** R1.

### Bước 7 — *(đề xuất, chưa làm)* Người chuyển thư trung gian nhận cả thư qua cổng web

**Làm gì:** người trung gian mở thêm một quầy nhận thư giống hệt quầy của các trang web thông thường, nên nơi nào cho vào web là nơi đó gửi được thư qua người trung gian.

**Về kỹ thuật:** bật TURN qua TCP và TLS, cổng 443, trong coturn; client thêm các địa chỉ này vào cấu hình TURN. Kiểm tra lại cấu hình coturn để relay ↔ relay hoạt động.

**Đáp ứng yêu cầu:** R2.

---

### Tóm tắt chuỗi

```
B0 gửi thẳng ──không biết địa chỉ──► B1 bưu điện giới thiệu ──địa chỉ trong nhà, cổng chặn thư lạ──►
B2 hỏi địa chỉ cổng + mở lối ──cổng khó tính, cấm UDP──► B3 người chuyển thư trung gian
──mật khẩu cố định bị moi / bị lộ──► B4 mật khẩu tạm thời ──thư mạo danh địa chỉ──►
B5 lọc địa chỉ + bắt buộc mở được bằng chìa ──lộ địa chỉ nhà / cấm UDP──►
B6, B7 (đề xuất)
```

| Lỗ hổng | Được vá ở | Trạng thái |
|---|---|---|
| Không biết địa chỉ của nhau | Bước 1 | ✅ |
| Chỉ biết địa chỉ trong nhà; cổng chặn thư lạ | Bước 2 | ✅ |
| Cổng đổi địa chỉ theo nơi nhận (4G); mạng chặn đường thẳng | Bước 3 | ✅ với mạng cho phép UDP |
| Mật khẩu TURN cố định bị moi hoặc lộ | Bước 4 | ✅ |
| Thư mạo danh địa chỉ người gửi | Bước 5 | ✅ |
| Mất liên lạc với bưu điện | Tự kết nối lại | ✅ |
| Lộ địa chỉ IP cho người trong phòng | Bước 6 | ❌ đề xuất |
| Mạng cấm UDP; relay ↔ relay thất bại | Bước 7 | ❌ đề xuất |
| Người dùng không biết đi thẳng hay qua TURN | Nhãn "Trực tiếp / Qua relay" | ✅ |

---

## Hành trình kết nối khi Bình vào phòng có sẵn An — từ đầu đến cuối

| # | Ví dụ đời thường | Code thực hiện |
|---|---|---|
| 1 | Ứng dụng xin vé dùng người chuyển thư trung gian | `P2pChatSession` → `AuthClient.turnServers` → `GET /turn-credentials` |
| 2 | Bình báo bưu điện "tôi vào phòng X" | `RoomSession.join` (dòng 182) → `WebSocketSignalingClient` gửi `JOIN` |
| 3 | Bưu điện trả lời "trong phòng có An"; báo An "Bình vừa vào" | Server gửi `PEER_LIST` cho Bình, `PEER_JOINED` cho An |
| 4 | Bình hỏi trạm hỏi địa chỉ và xin hộp thư ở người trung gian | `IceP2pConnectionEstablisher`: STUN (dòng 176), TURN (dòng 185) |
| 5 | Bình viết thư hỏi đường ghi mọi địa chỉ có thể nhận thư, nhờ bưu điện chuyển | `RoomSession.connectAsOfferer` (396) → `createOffer` (227) → `OFFER` |
| 6 | An viết thư trả lời tương tự | `RoomSession.handleOffer` (406) → `createAnswer` (241) → `ANSWER` |
| 7 | Hai người cùng gửi thư thử theo từng cặp địa chỉ; cổng hai nhà mở lỗ | `acceptAnswer` (262); ice4j kiểm tra kết nối song song |
| 8 | Chọn cặp đi được, ưu tiên đường ngắn; ghi nhận có đi qua trung gian không | ICE `COMPLETED` → `getSelectedPair` (380), `isRelayed` (388) |
| 9 | Mở kênh thư trực tiếp, chỉ nhận thư từ đúng địa chỉ đã chọn | `new P2pDataChannel(socket, remoteAddress)` (391); lọc nguồn ở `P2pDataChannel.java:150–159` |
| 10 | Bắt đầu trao chìa và khoá thư | `RoomSession.onIceConnected` → trao khoá ECDH (xem `Co-che-ma-hoa-tin-nhan.md`) |
