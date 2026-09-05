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

## 1. Lý do chọn đề tài

Các ứng dụng nhắn tin phổ biến hiện nay (Zalo, Messenger, Telegram...) đều vận hành theo mô hình client–server tập trung: toàn bộ tin nhắn phải đi qua máy chủ của nhà cung cấp trước khi đến người nhận. Mô hình này tiềm ẩn một số rủi ro:

- Dữ liệu người dùng bị lưu trữ tập trung, có thể bị truy cập trái phép, rò rỉ, hoặc bị khai thác cho mục đích thương mại.
- Nhà cung cấp dịch vụ hoặc bên thứ ba (cơ quan quản lý, hacker) có khả năng truy xuất nội dung trò chuyện nếu dữ liệu không được mã hoá đầu-cuối đúng cách.
- Ứng dụng phụ thuộc hoàn toàn vào hạ tầng máy chủ trung tâm — máy chủ ngừng hoạt động đồng nghĩa dịch vụ ngừng hoạt động.

Kiến trúc **ngang hàng (peer-to-peer – P2P)** giải quyết trực tiếp các vấn đề trên bằng cách để hai máy khách trao đổi dữ liệu trực tiếp với nhau, không cần một máy chủ trung tâm lưu trữ nội dung — máy chủ trung gian (nếu có) chỉ đóng vai trò "giới thiệu" ban đầu, không bao giờ chạm vào nội dung tin nhắn.

Đề tài này hướng đến việc tự nghiên cứu và xây dựng một ứng dụng chat desktop bằng Java áp dụng mô hình trên, nhằm:

- Vận dụng kiến thức về mạng máy tính, lập trình socket và các kỹ thuật xuyên NAT (STUN/TURN/ICE) vào một bài toán thực tế.
- Vận dụng kiến thức an toàn thông tin (trao khoá, mã hoá đối xứng/bất đối xứng) để cài đặt cơ chế mã hoá đầu-cuối thực sự, không phụ thuộc vào bên thứ ba.
- Tạo ra một sản phẩm hoàn chỉnh, có thể chạy thực tế trên nhiều máy qua Internet, minh hoạ được toàn bộ vòng đời của một hệ thống phân tán quy mô nhỏ: từ thiết lập kết nối, bảo mật kênh truyền, đến giao diện người dùng.

## 2. Mục tiêu đề tài

**Mục tiêu tổng quát:** Xây dựng một ứng dụng chat desktop bằng Java cho phép hai hoặc nhiều người dùng trò chuyện trực tiếp (P2P), có mã hoá đầu-cuối, không lưu trữ dữ liệu trên máy chủ trung gian.

**Mục tiêu cụ thể:**

1. Nghiên cứu và trình bày cơ sở lý thuyết về mạng ngang hàng, các giao thức xuyên NAT (STUN/TURN/ICE) và mã hoá đầu-cuối.
2. Thiết kế và cài đặt một signaling server tối giản (chỉ dùng để hai peer "tìm thấy nhau", không xử lý nội dung chat).
3. Cài đặt cơ chế thiết lập kết nối trực tiếp giữa hai máy dùng ICE, có dự phòng qua TURN khi NAT chặn kết nối trực tiếp.
4. Cài đặt cơ chế trao đổi khoá (ECDH) và mã hoá/giải mã tin nhắn, file bằng AES-GCM.
5. Xây dựng giao diện desktop (JavaFX) cho phép: tạo/tham gia phòng chat, gửi nhận tin nhắn văn bản, gửi nhận file, xác thực đối phương qua "vân tay" khoá công khai.
6. Đánh giá hệ thống về mặt hiệu năng (độ trễ, tỉ lệ thiết lập kết nối P2P thành công) và bảo mật (khả năng chống nghe lén trung gian).

## 3. Đối tượng và phạm vi nghiên cứu

**Đối tượng nghiên cứu:** Mô hình mạng ngang hàng, giao thức xuyên NAT, mã hoá đầu-cuối, lập trình mạng và giao diện desktop bằng Java.

**Trong phạm vi đề tài:**
- Chat văn bản trực tiếp giữa hai peer (1-1), mở rộng nhiều peer trong cùng phòng nếu còn thời gian.
- Truyền file P2P có mã hoá.
- Mã hoá đầu-cuối toàn bộ nội dung trao đổi.
- Xác thực đối phương thủ công qua so khớp vân tay khoá công khai.
- Không lưu trữ tin nhắn/file trên server (ephemeral).

**Ngoài phạm vi đề tài** *(nêu rõ để tránh kỳ vọng sai khi bảo vệ)*:
- Gọi thoại/video call (yêu cầu xử lý audio/video codec, độ phức tạp vượt quy mô đồ án).
- Ứng dụng di động (mobile).
- Tài khoản người dùng, danh bạ liên hệ lâu dài.

## 4. Phương pháp nghiên cứu và thực hiện

- **Nghiên cứu lý thuyết:** tổng hợp tài liệu về kiến trúc P2P, các RFC liên quan đến STUN/TURN/ICE, các chuẩn mã hoá ECDH/AES-GCM.
- **Thiết kế:** xây dựng kiến trúc hệ thống, giao thức truyền tin và các module từ đầu, dựa trên nghiên cứu lý thuyết ở trên.
- **Thực nghiệm:** cài đặt từng module theo hướng tăng dần (incremental) — signaling → thiết lập kết nối P2P → mã hoá → giao diện → truyền file — kiểm thử sau mỗi giai đoạn.
- **Đánh giá:** kiểm thử kết nối qua các môi trường mạng khác nhau (cùng LAN, khác mạng có NAT, sau firewall), đo thời gian thiết lập kết nối, kiểm tra tính đúng đắn của mã hoá.

## 5. Công nghệ sử dụng

| Thành phần | Công nghệ | Vai trò |
|---|---|---|
| Ngôn ngữ | Java 17+ | Toàn bộ hệ thống |
| Giao diện | JavaFX | Ứng dụng desktop |
| Signaling server | Spring Boot + WebSocket (STOMP) | Trung gian trao đổi thông tin kết nối, không chạm nội dung chat |
| Xuyên NAT | ice4j (thư viện Java của dự án Jitsi) | Cài đặt STUN/TURN/ICE |
| Truyền dữ liệu P2P | Java Socket / Netty | Kênh dữ liệu trực tiếp sau khi kết nối được thiết lập |
| Mã hoá | Java Cryptography Architecture (JCA): ECDH, AES-GCM | Trao khoá phiên và mã hoá nội dung |
| Build tool | Maven hoặc Gradle | Quản lý dự án, phụ thuộc |
| Kiểm thử | JUnit 5 | Unit test cho các module lõi |

## 6. Kiến trúc hệ thống

```
   Peer A (JavaFX)                                    Peer B (JavaFX)
        |                                                    |
        |  (1) Đăng ký phòng / gửi SDP + ICE candidate        |
        |------------------> Signaling Server <---------------|
        |        (Spring Boot + WebSocket — chỉ chuyển tiếp,   |
        |         không lưu, không đọc nội dung chat)          |
        |                                                    |
        |  (2) Thiết lập kết nối trực tiếp qua ICE (ice4j)     |
        |<==================================================>|
        |     ưu tiên: kết nối trực tiếp (NAT hole punching)   |
        |     dự phòng: relay qua TURN nếu NAT chặn            |
        |                                                    |
        |  (3) Trao khoá phiên bằng ECDH                       |
        |<==================================================>|
        |                                                    |
        |  (4) Kênh dữ liệu mã hoá AES-GCM                     |
        |<==================================================>|
        |     - Tin nhắn văn bản                               |
        |     - File (chia chunk, mã hoá từng chunk)           |
```

**Nguyên tắc thiết kế cốt lõi:** signaling server chỉ đóng vai trò "môi giới ban đầu" giữa hai peer — không bao giờ nhìn thấy nội dung tin nhắn hay file, vì toàn bộ được mã hoá đầu-cuối trước khi rời khỏi máy gửi.

## 7. Nội dung dự kiến của báo cáo

1. **Chương 1 — Tổng quan:** đặt vấn đề, lý do chọn đề tài, mục tiêu, phạm vi.
2. **Chương 2 — Cơ sở lý thuyết:** kiến trúc P2P, NAT và các kỹ thuật xuyên NAT (STUN/TURN/ICE), mật mã học (ECDH, AES-GCM).
3. **Chương 3 — Phân tích và thiết kế hệ thống:** yêu cầu chức năng/phi chức năng, kiến trúc tổng thể, thiết kế các module, giao thức truyền tin tự định nghĩa.
4. **Chương 4 — Cài đặt:** chi tiết cài đặt từng module (signaling, ICE, mã hoá, truyền file, giao diện).
5. **Chương 5 — Kiểm thử và đánh giá:** kịch bản kiểm thử, kết quả đo hiệu năng và bảo mật.
6. **Chương 6 — Kết luận và hướng phát triển:** kết quả đạt được, hạn chế, hướng mở rộng (video call, mobile, nhóm chat lớn...).

## 8. Kế hoạch thực hiện dự kiến

*(Mốc thời gian tính theo tuần kể từ ngày bắt đầu — điều chỉnh theo lịch cụ thể của khoa/trường)*

| Giai đoạn | Nội dung | Thời gian |
|---|---|---|
| 1 | Nghiên cứu lý thuyết, viết & duyệt đề cương | Tuần 1–2 |
| 2 | Thiết kế kiến trúc chi tiết, giao thức truyền tin | Tuần 3–4 |
| 3 | Cài đặt signaling server (Spring Boot + WebSocket) | Tuần 5–6 |
| 4 | Cài đặt module xuyên NAT (ice4j) + thiết lập kết nối P2P | Tuần 7–9 |
| 5 | Cài đặt module mã hoá (ECDH + AES-GCM) | Tuần 10–11 |
| 6 | Xây dựng giao diện JavaFX + tích hợp chat văn bản | Tuần 12–13 |
| 7 | Cài đặt truyền file P2P có mã hoá | Tuần 14–15 |
| 8 | Kiểm thử toàn diện, đo hiệu năng/bảo mật | Tuần 16–17 |
| 9 | Viết báo cáo hoàn chỉnh, chuẩn bị bảo vệ | Tuần 18–19 |

## 9. Kết quả dự kiến đạt được

- Ứng dụng desktop Java hoàn chỉnh, chạy được trên Windows/macOS/Linux, cho phép hai máy chat trực tiếp và truyền file có mã hoá đầu-cuối qua mạng Internet thực tế (khác NAT).
- Báo cáo đồ án trình bày đầy đủ cơ sở lý thuyết, thiết kế, cài đặt và đánh giá.
- Số liệu đánh giá thực nghiệm về tỉ lệ thiết lập kết nối P2P thành công, độ trễ, và tính đúng đắn của cơ chế mã hoá.

## 10. Tài liệu tham khảo

1. RFC 8445, *Interactive Connectivity Establishment (ICE)*, IETF, 2018.
2. RFC 8489, *Session Traversal Utilities for NAT (STUN)*, IETF, 2020.
3. RFC 8656, *Traversal Using Relays around NAT (TURN)*, IETF, 2020.
4. Jitsi, *ice4j* — <https://github.com/jitsi/ice4j>.
5. NIST Special Publication 800-38D, *Recommendation for Block Cipher Modes of Operation: Galois/Counter Mode (GCM)*.
6. Oracle, *Java Cryptography Architecture (JCA) Reference Guide*.
7. Oracle, *JavaFX Documentation*.

---

*Ghi chú: Các mục in nghiêng trong ngoặc vuông [...] và mốc thời gian ở mục 8 cần được điền/điều chỉnh theo thông tin cá nhân và lịch trình cụ thể do khoa/trường quy định trước khi nộp giảng viên hướng dẫn duyệt.*
