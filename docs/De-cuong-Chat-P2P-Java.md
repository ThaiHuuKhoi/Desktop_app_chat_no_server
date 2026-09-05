Đề cương đồ án tốt nghiệp

# ỨNG DỤNG CHAT NGANG HÀNG (P2P) MÃ HOÁ ĐẦU-CUỐI TRÊN NỀN TẢNG JAVA

**Peer-to-Peer End-to-End Encrypted Desktop Chat Application in Java**

| | |
|---|---|
| Sinh viên thực hiện | *[Họ và tên]* |
| MSSV | *[Mã số sinh viên]* |
| Lớp / Khoá | *[Điền thông tin]* |
| Ngành | Công nghệ thông tin / Kỹ thuật phần mềm |
| Giảng viên hướng dẫn | *[Họ và tên GVHD]* |
| Thời gian thực hiện | *[Ngày bắt đầu]* – *[Ngày dự kiến bảo vệ]* |

---

*Đề cương này đã được cập nhật để khớp với bản thiết kế thi công chi tiết ở [Tai-lieu-ky-thuat.md](Tai-lieu-ky-thuat.md) — tài liệu đó lấy `chitchatter` (ứng dụng web P2P mã nguồn mở, xem Phần D) làm tham chiếu kiến trúc/tập tính năng và mở rộng phạm vi so với bản đề cương gốc ban đầu (đặc biệt là mục 3 và mục 8 dưới đây). Nếu GVHD không duyệt phần "mở rộng" ở mục 3, bỏ các mục được đánh dấu *(mở rộng)* và quay lại đúng phạm vi tối giản: chat 1-1, truyền file, mã hoá đầu-cuối, xác thực thủ công.*

## 1. Lý do chọn đề tài

Các ứng dụng nhắn tin phổ biến hiện nay (Zalo, Messenger, Telegram...) đều vận hành theo mô hình client–server tập trung: toàn bộ tin nhắn phải đi qua máy chủ của nhà cung cấp trước khi đến người nhận. Mô hình này tiềm ẩn một số rủi ro:

- Dữ liệu người dùng bị lưu trữ tập trung, có thể bị truy cập trái phép, rò rỉ, hoặc bị khai thác cho mục đích thương mại.
- Nhà cung cấp dịch vụ hoặc bên thứ ba (cơ quan quản lý, hacker) có khả năng truy xuất nội dung trò chuyện nếu dữ liệu không được mã hoá đầu-cuối đúng cách.
- Ứng dụng phụ thuộc hoàn toàn vào hạ tầng máy chủ trung tâm — máy chủ ngừng hoạt động đồng nghĩa dịch vụ ngừng hoạt động.

Kiến trúc **ngang hàng (peer-to-peer – P2P)** giải quyết trực tiếp các vấn đề trên bằng cách để các máy khách trao đổi dữ liệu trực tiếp với nhau, không cần một máy chủ trung tâm lưu trữ nội dung — máy chủ trung gian (nếu có) chỉ đóng vai trò "giới thiệu" ban đầu, không bao giờ chạm vào nội dung tin nhắn.

Đề tài này hướng đến việc tự nghiên cứu và xây dựng một ứng dụng chat desktop bằng Java áp dụng mô hình trên, nhằm:

- Vận dụng kiến thức về mạng máy tính, lập trình socket và các kỹ thuật xuyên NAT (STUN/TURN/ICE) vào một bài toán thực tế.
- Vận dụng kiến thức an toàn thông tin (trao khoá, mã hoá đối xứng/bất đối xứng, chữ ký số) để cài đặt cơ chế mã hoá đầu-cuối và xác thực danh tính thực sự, không phụ thuộc vào bên thứ ba.
- Tạo ra một sản phẩm hoàn chỉnh, có thể chạy thực tế trên nhiều máy qua Internet, minh hoạ được toàn bộ vòng đời của một hệ thống phân tán quy mô nhỏ: từ thiết lập kết nối, bảo mật kênh truyền, đến giao diện người dùng.
- Đối chiếu với một ứng dụng P2P mã nguồn mở đã vận hành thật (`chitchatter`, nền web/WebRTC) để hiểu đúng một kiến trúc mesh + mã hoá đầu-cuối hoàn chỉnh trông như thế nào trước khi tự thiết kế lại bằng Java — nơi không có sẵn WebRTC/DTLS.

## 2. Mục tiêu đề tài

**Mục tiêu tổng quát:** Xây dựng một ứng dụng chat desktop bằng Java cho phép nhiều người dùng trò chuyện trực tiếp (P2P) trong cùng một phòng, có mã hoá đầu-cuối, không lưu trữ dữ liệu trên máy chủ trung gian.

**Mục tiêu cụ thể:**

1. Nghiên cứu và trình bày cơ sở lý thuyết về mạng ngang hàng, các giao thức xuyên NAT (STUN/TURN/ICE) và mã hoá đầu-cuối (ECDH, AES-GCM, ECDSA).
2. Thiết kế và cài đặt một signaling server tối giản (chỉ dùng để các peer "tìm thấy nhau", không xử lý nội dung chat).
3. Cài đặt cơ chế thiết lập kết nối trực tiếp giữa nhiều máy dùng ICE, có dự phòng qua TURN khi NAT chặn kết nối trực tiếp — đạt kiến trúc **mesh đầy đủ** (mỗi peer kết nối trực tiếp với mọi peer khác trong phòng).
4. Cài đặt cơ chế trao đổi khoá (ECDH) và mã hoá/giải mã tin nhắn, file bằng AES-GCM; xác thực danh tính đối phương **tự động** bằng chữ ký số (ECDSA) thay vì yêu cầu người dùng tự so khớp thủ công.
5. Xây dựng giao diện desktop (JavaFX) cho phép: tạo/tham gia phòng chat công khai hoặc riêng tư, nhắn tin nhóm (đa dòng, Markdown) và chat riêng (direct message), hiển thị trạng thái đang gõ, gửi nhận file có mã hoá, cài đặt cá nhân (theme, âm thanh, thông báo).
6. *(Mở rộng, thực hiện sau cùng nếu còn thời gian)* Gọi thoại, gọi video và chia sẻ màn hình giữa các peer, dùng giải pháp đơn giản hoá không phụ thuộc codec chuẩn (xem mục 3 và mục 8).
7. Đánh giá hệ thống về mặt hiệu năng (độ trễ, tỉ lệ thiết lập kết nối P2P thành công) và bảo mật (khả năng chống nghe lén trung gian, khả năng chống mạo danh lặp lại).

## 3. Đối tượng và phạm vi nghiên cứu

**Đối tượng nghiên cứu:** Mô hình mạng ngang hàng, giao thức xuyên NAT, mã hoá đầu-cuối, chữ ký số, lập trình mạng và giao diện desktop bằng Java.

**Trong phạm vi đề tài (cam kết):**
- Chat văn bản nhiều peer trong cùng phòng (mesh đầy đủ, không giới hạn ở 1-1), nhắn tin nhóm và chat riêng (direct message), đa dòng, Markdown, trạng thái đang gõ.
- Phòng công khai (biết tên phòng là vào được) và phòng riêng tư (thêm mật khẩu, không gửi mật khẩu thô qua mạng).
- Truyền file P2P có mã hoá theo từng chunk.
- Mã hoá đầu-cuối toàn bộ nội dung trao đổi (AES-GCM), khoá phiên trao bằng ECDH.
- Xác thực đối phương **tự động** bằng chữ ký số (ECDSA) trên chuỗi thách thức gắn với phòng/người dùng — chống mạo danh lặp lại giữa các lần vào phòng (xem mục 8 về giới hạn "trust on first use" cần nêu khi đánh giá bảo mật).
- Cài đặt cá nhân (theme sáng/tối, âm thanh, thông báo), lưu cục bộ.
- Không lưu trữ tin nhắn/file trên server (ephemeral) — chỉ cài đặt cá nhân và cặp khoá danh tính được lưu bền trên máy người dùng.

**Trong phạm vi đề tài (mở rộng, tuỳ thời gian còn lại — không phải cam kết cứng):**
- Gọi thoại, gọi video, chia sẻ màn hình giữa các peer trong phòng — dùng giải pháp đơn giản hoá (chụp khung hình định kỳ nén JPEG, PCM thô cho âm thanh) thay vì codec chuẩn (H.264/VP8/Opus), vì mục tiêu là chứng minh khái niệm chứ không phải chất lượng sản xuất. Nếu không kịp thời gian, dừng lại và ghi rõ trong báo cáo đây là phần đã cắt theo kế hoạch, không phải thiếu sót.

**Ngoài phạm vi đề tài** *(nêu rõ để tránh kỳ vọng sai khi bảo vệ)*:
- Ứng dụng di động (mobile).
- Tài khoản người dùng bền vững, danh bạ liên hệ lâu dài, đồng bộ đa thiết bị cho 1 người dùng.
- Nhúng ứng dụng qua iframe/SDK (khái niệm thuần web, không có ý nghĩa với app desktop).
- Gọi thoại/video/màn hình dùng codec nén chuẩn chất lượng sản xuất (nếu làm mục mở rộng ở trên, chỉ dùng giải pháp đơn giản hoá, không đầu tư codec riêng).

## 4. Phương pháp nghiên cứu và thực hiện

- **Nghiên cứu lý thuyết:** tổng hợp tài liệu về kiến trúc P2P, các RFC liên quan đến STUN/TURN/ICE, các chuẩn mã hoá ECDH/AES-GCM/ECDSA.
- **Nghiên cứu đối chiếu:** đọc mã nguồn thật của `chitchatter` (ứng dụng P2P web mã nguồn mở, dùng WebRTC qua thư viện `trystero`) để hiểu đúng cách một hệ mesh + mã hoá đầu-cuối + xác thực danh tính hoàn chỉnh vận hành, từ đó thiết kế phần tương đương bằng Java ở những chỗ JVM không có sẵn WebRTC/DTLS (chi tiết đối chiếu từng khối kỹ thuật ở Tai-lieu-ky-thuat.md, Phần D và E.12).
- **Thiết kế:** xây dựng kiến trúc hệ thống, giao thức truyền tin và các module từ đầu, dựa trên nghiên cứu lý thuyết và đối chiếu ở trên.
- **Thực nghiệm:** cài đặt từng module theo hướng tăng dần (incremental) — signaling → thiết lập kết nối P2P → mã hoá + xác thực danh tính → mesh nhiều peer → giao diện chat/DM/file → *(mở rộng)* video/audio/screen share — kiểm thử sau mỗi giai đoạn.
- **Đánh giá:** kiểm thử kết nối qua các môi trường mạng khác nhau (cùng LAN, khác mạng có NAT, sau firewall), đo thời gian thiết lập kết nối, kiểm tra tính đúng đắn của mã hoá và xác thực danh tính.

## 5. Công nghệ sử dụng

| Thành phần | Công nghệ | Vai trò |
|---|---|---|
| Ngôn ngữ | Java 17+ | Toàn bộ hệ thống |
| Giao diện | JavaFX | Ứng dụng desktop |
| Signaling server | Spring Boot + WebSocket | Trung gian trao đổi thông tin kết nối, không chạm nội dung chat |
| Xuyên NAT | ice4j (thư viện Java của dự án Jitsi) | Cài đặt STUN/TURN/ICE |
| Truyền dữ liệu P2P | UDP socket (qua candidate pair do ice4j chọn) | Kênh dữ liệu trực tiếp sau khi kết nối được thiết lập |
| Mã hoá & xác thực | Java Cryptography Architecture (JCA): ECDH (trao khoá phiên), AES-GCM (mã hoá nội dung), ECDSA (chữ ký danh tính) | Bảo mật kênh truyền tự cài, vì không có DTLS như WebRTC |
| Serialize dữ liệu | Jackson | Đóng gói `Envelope` (đa kênh logic: chat, DM, file, media...) trước khi mã hoá |
| Markdown *(mở rộng UI)* | `flexmark` hoặc tương đương | Render nội dung tin nhắn dạng Markdown |
| Video/audio *(mở rộng, tuỳ thời gian)* | `webcam-capture`, `javax.sound.sampled` (JDK), `java.awt.Robot` (JDK) | Chụp webcam/âm thanh/màn hình định kỳ, không dùng codec chuẩn |
| Build tool | Maven | Quản lý dự án, phụ thuộc (đa module: `common`, `crypto`, `p2p-core`, `signaling-server`, `client-javafx`) |
| Kiểm thử | JUnit 5 | Unit test + integration test cho các module lõi |

## 6. Kiến trúc hệ thống

```
                         Signaling Server (Spring Boot + WebSocket)
                         chỉ chuyển tiếp JOIN/PEER_LIST/OFFER/ANSWER/
                         ICE_CANDIDATE — KHÔNG đọc, KHÔNG lưu nội dung chat
                                    ▲
                    JSON/WebSocket  │  (JOIN / OFFER / ANSWER / ICE)
        ┌───────────────────────────┴───────────────────────────┐
        │                    (mesh: N peer → N kết nối song song)│
┌───────▼────────┐                                       ┌───────▼────────┐
│   Peer A (JavaFX)│◄──── DataChannel/Envelope mã hoá ───►│   Peer B (JavaFX)│
│                   │   ice4j P2P, moi Envelope da AES-GCM  │                   │
└───────────────────┘                                       └───────────────────┘
```

Với mỗi cặp peer, tuần tự thiết lập kết nối là:

1. Đăng ký phòng / gửi SDP + ICE candidate qua signaling server (server chỉ giới thiệu, không chạm nội dung).
2. Thiết lập kết nối trực tiếp qua ICE (ice4j) — ưu tiên kết nối trực tiếp (NAT hole punching), dự phòng relay qua TURN nếu NAT chặn.
3. Trao khoá phiên bằng ECDH, xác thực danh tính đối phương bằng chữ ký số (ECDSA) trên chuỗi thách thức gắn với phòng.
4. Kênh dữ liệu mã hoá AES-GCM cho mọi loại nội dung (`Envelope`: tin nhắn văn bản/nhóm/DM, trạng thái đang gõ, file chia chunk, và *(mở rộng)* khung hình media) — nhiều peer trong phòng tạo thành **mesh đầy đủ**, mỗi peer duy trì kết nối song song với mọi peer còn lại.

**Nguyên tắc thiết kế cốt lõi:** signaling server chỉ đóng vai trò "môi giới ban đầu" giữa các peer — không bao giờ nhìn thấy nội dung tin nhắn hay file, vì toàn bộ được mã hoá đầu-cuối trước khi rời khỏi máy gửi. Vì Java không có DTLS sẵn như WebRTC, **mọi** byte đi qua kênh dữ liệu (trừ chính gói tin trao khoá) đều phải tự mã hoá ở tầng ứng dụng — đây là khác biệt lớn nhất so với bản tham chiếu `chitchatter` (chi tiết ở Tai-lieu-ky-thuat.md, Phần D.6 và E.12.1).

## 7. Nội dung dự kiến của báo cáo

1. **Chương 1 — Tổng quan:** đặt vấn đề, lý do chọn đề tài, mục tiêu, phạm vi (bao gồm phần mở rộng và giới hạn rõ ràng của nó).
2. **Chương 2 — Cơ sở lý thuyết:** kiến trúc P2P/mesh, NAT và các kỹ thuật xuyên NAT (STUN/TURN/ICE), mật mã học (ECDH, AES-GCM, ECDSA).
3. **Chương 3 — Phân tích và thiết kế hệ thống:** yêu cầu chức năng/phi chức năng, kiến trúc tổng thể, thiết kế các module, giao thức truyền tin tự định nghĩa (`Envelope`), đối chiếu với kiến trúc tham chiếu `chitchatter` (chỗ nào giữ ý tưởng, chỗ nào phải tự viết lại vì thiếu WebRTC/DTLS).
4. **Chương 4 — Cài đặt:** chi tiết cài đặt từng module (signaling, ICE, mã hoá + xác thực danh tính, mesh, giao diện, truyền file, và mục mở rộng nếu có làm).
5. **Chương 5 — Kiểm thử và đánh giá:** kịch bản kiểm thử, kết quả đo hiệu năng và bảo mật, nêu rõ giới hạn đã biết (ví dụ: không chống MITM tuyệt đối ở lần gặp đầu tiên).
6. **Chương 6 — Kết luận và hướng phát triển:** kết quả đạt được, hạn chế, hướng mở rộng (codec chuẩn cho media, mobile, nhóm chat lớn hơn quy mô mesh...).

## 8. Kế hoạch thực hiện dự kiến

*(Mốc thời gian tính theo tuần kể từ ngày bắt đầu — điều chỉnh theo lịch cụ thể của khoa/trường. Phân công chi tiết theo 2 thành viên xem [Phan-cong-cong-viec.md](Phan-cong-cong-viec.md).)*

| Giai đoạn | Nội dung | Thời gian |
|---|---|---|
| 1 | Nghiên cứu lý thuyết (P2P, ICE/STUN/TURN, ECDH/AES-GCM/ECDSA) + đọc đối chiếu `chitchatter`; viết & duyệt đề cương | Tuần 1–2 |
| 2 | Thiết kế kiến trúc chi tiết, giao thức truyền tin, chốt interface `DataChannel` chung giữa 2 thành viên | Tuần 3–4 |
| 3 | Cài đặt signaling server (Spring Boot + WebSocket) song song với module mã hoá (ECDH + AES-GCM) | Tuần 5–6 |
| 4 | Cài đặt module xuyên NAT (ice4j) + thiết lập kết nối P2P thật; song song xây giao diện JavaFX cơ bản trên kênh giả lập | Tuần 7–9 |
| 5 | Tích hợp: giao thức `Envelope` mã hoá + kênh P2P thật, mở rộng mesh nhiều peer, xác thực danh tính tự động (ECDSA) | Tuần 10–11 |
| 6 | Nhắn tin nhóm/Markdown, chat riêng (DM), trạng thái đang gõ, phòng công khai/riêng tư, cài đặt cá nhân | Tuần 12–13 |
| 7 | Cài đặt truyền file P2P có mã hoá theo chunk | Tuần 14–15 |
| 8 | *(Mở rộng, tuỳ thời gian)* Gọi thoại/video/chia sẻ màn hình bằng giải pháp đơn giản hoá | Tuần 16 |
| 9 | Kiểm thử toàn diện, đo hiệu năng/bảo mật | Tuần 17 |
| 10 | Viết báo cáo hoàn chỉnh, chuẩn bị bảo vệ | Tuần 18–19 |

## 9. Kết quả dự kiến đạt được

- Ứng dụng desktop Java hoàn chỉnh, chạy được trên Windows/macOS/Linux, cho phép nhiều máy chat trực tiếp theo mô hình mesh (nhắn tin nhóm, chat riêng, trạng thái đang gõ) và truyền file có mã hoá đầu-cuối qua mạng Internet thực tế (khác NAT).
- Cơ chế xác thực danh tính đối phương tự động bằng chữ ký số, không cần người dùng tự so khớp thủ công.
- *(Nếu còn thời gian)* Gọi thoại/video/chia sẻ màn hình cơ bản giữa các peer.
- Báo cáo đồ án trình bày đầy đủ cơ sở lý thuyết, thiết kế, cài đặt, đối chiếu với kiến trúc tham chiếu và đánh giá.
- Số liệu đánh giá thực nghiệm về tỉ lệ thiết lập kết nối P2P thành công, độ trễ, và tính đúng đắn của cơ chế mã hoá/xác thực.

## 10. Tài liệu tham khảo

1. RFC 8445, *Interactive Connectivity Establishment (ICE)*, IETF, 2018.
2. RFC 8489, *Session Traversal Utilities for NAT (STUN)*, IETF, 2020.
3. RFC 8656, *Traversal Using Relays around NAT (TURN)*, IETF, 2020.
4. Jitsi, *ice4j* — <https://github.com/jitsi/ice4j>.
5. NIST Special Publication 800-38D, *Recommendation for Block Cipher Modes of Operation: Galois/Counter Mode (GCM)*.
6. Oracle, *Java Cryptography Architecture (JCA) Reference Guide*.
7. Oracle, *JavaFX Documentation*.
8. jeremyckahn, *chitchatter* (mã nguồn tham chiếu về kiến trúc P2P/mesh và mã hoá đầu-cuối trên nền web) — <https://github.com/jeremyckahn/chitchatter>.
9. dmotz, *Trystero* (thư viện signaling/WebRTC dùng bởi chitchatter) — <https://github.com/dmotz/trystero>.
10. sarxos, *webcam-capture* (dùng cho mục mở rộng video call) — <https://github.com/sarxos/webcam-capture>.

---

*Ghi chú: Các mục in nghiêng trong ngoặc vuông [...] và mốc thời gian ở mục 8 cần được điền/điều chỉnh theo thông tin cá nhân và lịch trình cụ thể do khoa/trường quy định trước khi nộp giảng viên hướng dẫn duyệt. Các mục đánh dấu *(mở rộng)* cần được GVHD xác nhận có tính vào phạm vi chấm hay không trước khi đầu tư thời gian — nếu không được duyệt, bỏ các mục này và quay lại phạm vi tối giản.*
