# Tổng quan các cơ chế của Chat P2P Java

Trang này là **bản đồ** của 14 tài liệu cơ chế. Nó đi theo hành trình của An từ lúc mở ứng dụng tới lúc rời phòng. Ở mỗi chặng có:
- chuyện gì xảy ra, kể bằng lời thường;
- cơ chế nào lo chuyện đó;
- đọc chi tiết ở file nào.

Cuối trang có ba phần tổng hợp:
- server biết gì và không biết gì;
- các vấn đề còn tồn đọng, xếp theo mức độ;
- gợi ý thứ tự trình bày khi bảo vệ.

Mỗi tài liệu cơ chế đều viết theo cùng một kiểu:
- **Sâu:** đi từ cách ngây thơ, tìm lỗ hổng, phân tích cần gì (R1, R2…), rồi từ đó sinh ra bước tiếp theo.
- **Rộng:** ở mỗi chỗ phải chọn, so sánh các phương án trước khi chọn.
- **Kết thúc:** bằng các lỗ hổng còn lại và một bước *(đề xuất, chưa làm)*.

---

## Kiến trúc trong một hình

```
        ┌──────────── client An ────────────┐          ┌──────────── client Bình ───────────┐
        │ JavaFX UI ── P2pChatSession       │          │                                     │
        │      RoomSession ── PeerConnection│◄════════►│ (giống An)                          │
        │      ice4j / P2pDataChannel (UDP) │  đường   │                                     │
        │      khoá danh tính (DPAPI)       │  riêng   │                                     │
        └──────────────┬────────────────────┘  mã hoá  └──────────────┬──────────────────────┘
                       │ wss / https (TLS)       AES-GCM               │
                       ▼                      (hoặc qua TURN)          ▼
        ┌─────────────── VPS chat.zingme.fun ─────────────────────────────────────┐
        │ Caddy :443 → signaling-server 127.0.0.1:8080 → MySQL 127.0.0.1:3306      │
        │ coturn :3478 (TURN, tài khoản tạm)                                       │
        └──────────────────────────────────────────────────────────────────────────┘
```

Server chỉ làm bốn việc:
1. xử lý đăng nhập;
2. xếp phòng;
3. chuyển tín hiệu kết nối;
4. cấp tài khoản TURN tạm.

**Tin nhắn và file không bao giờ đi qua server ứng dụng.** Khi cần đi vòng qua TURN thì TURN chỉ thấy các gói đã mã hoá.

---

## Hành trình của An

| # | Chuyện gì xảy ra | Cơ chế lo việc đó | Đọc ở |
|---|---|---|---|
| 1 | An mở ứng dụng. Máy lấy ra con dấu danh tính đã cất trong ngăn tủ chỉ An mở được; lần đầu thì khắc con dấu mới | Khoá danh tính ECDSA, lưu bằng DPAPI; sao lưu bằng mật khẩu khi đổi máy | [Co-che-xac-thuc-danh-tinh.md](Co-che-xac-thuc-danh-tinh.md), Bước 5–6 |
| 2 | An đăng nhập, hoặc vào làm khách | BCrypt, token 12 giờ lưu dạng băm, giới hạn thử, tài khoản gắn với khoá danh tính | [Co-che-dang-nhap.md](Co-che-dang-nhap.md) |
| 3 | Mọi thứ đi tới server đều được bọc trong TLS | Caddy, chứng chỉ tự gia hạn, server chỉ nghe nội bộ | [Tai-lieu-ky-thuat.md](Tai-lieu-ky-thuat.md), mục C.3 |
| 4 | An xem danh sách phòng, chọn phòng, nhập mật khẩu | Sảnh chỉ cho người đã đăng nhập; phòng có mật khẩu bị giấu thẻ | [Co-che-phong-mat-khau.md](Co-che-phong-mat-khau.md), Bước 1 |
| 5 | An xin vào phòng. Server không biết mật khẩu, chỉ biết "thẻ phòng" | Thẻ phòng từ PBKDF2, báo sai mật khẩu, giới hạn 5 lần mỗi phút | [Co-che-phong-mat-khau.md](Co-che-phong-mat-khau.md), Bước 1–2 |
| 6 | Server không để ai làm nó quá tải | Giới hạn kích thước, số kết nối, hạn chót JOIN, hàng đợi gửi, tần suất theo IP | [Co-che-chong-qua-tai-server.md](Co-che-chong-qua-tai-server.md) |
| 7 | An và Bình tìm đường đi thẳng tới nhau; không được thì đi vòng qua TURN | ICE, STUN, TURN với tài khoản tạm 6 giờ, lọc địa chỉ nguồn | [Co-che-ket-noi-p2p.md](Co-che-ket-noi-p2p.md) |
| 8 | An và Bình làm chìa chung mà người ngoài không làm ra được | ECDH P-256, HKDF ra hai khoá AES-256 cho hai chiều | [Co-che-ma-hoa-tin-nhan.md](Co-che-ma-hoa-tin-nhan.md), Bước 2–5 |
| 9 | Bình chứng minh biết mật khẩu phòng, mà không đọc mật khẩu ra | `ROOM_PROOF`: HMAC gắn với khoá ECDH của phiên | [Co-che-phong-mat-khau.md](Co-che-phong-mat-khau.md), Bước 3 |
| 10 | Bình chứng minh mình đúng là Bình | `PEER_IDENTITY` ký kèm khoá ECDH; vân tay, mã an toàn 60 chữ số; sổ tay người đã so; nhãn "Tài khoản/Khách" và đối chiếu khoá đã đăng ký | [Co-che-xac-thuc-danh-tinh.md](Co-che-xac-thuc-danh-tinh.md) |
| 11 | An vào sau, nhận lại các tin nhóm đã nói trước đó | Người cũ gửi `MESSAGE_TRANSCRIPT`, có giới hạn, có nhãn "chưa xác minh người viết" | [Co-che-lich-su-tin-nhan.md](Co-che-lich-su-tin-nhan.md), Bước 1–2 |
| 12 | An gửi tin cho cả nhóm, hoặc nhắn riêng cho Bình | Gửi qua kết nối riêng của từng cặp; tin riêng mang `namespace = "dm"` trong phần đã mã hoá; người viết lấy theo kết nối chở tin | [Co-che-chat-nhom-va-rieng.md](Co-che-chat-nhom-va-rieng.md), Bước 0–2 |
| 13 | Tin được khoá, đóng dấu niêm phong, đánh số để không ai phát lại được | AES-256-GCM, nonce gồm số thứ tự và phần ngẫu nhiên, cửa sổ chống phát lại 1024 | [Co-che-ma-hoa-tin-nhan.md](Co-che-ma-hoa-tin-nhan.md), Bước 3–7 |
| 14 | Gói UDP có thể rơi; tin vẫn phải tới đủ, không trùng, và An biết đã tới hay chưa | ACK, gửi lại 6 × 500 ms, chống trùng, "Đã nhận" / "Đã xem", chia mảnh 1150 byte | [Co-che-gui-tin-tin-cay.md](Co-che-gui-tin-tin-cay.md) |
| 15 | Bình thấy "An đang gõ…", thả cảm xúc, An sửa hoặc xoá tin | Tín hiệu tự hết hạn, bộ cảm xúc cố định, sửa/xoá chỉ tác giả | [Co-che-chat-nhom-va-rieng.md](Co-che-chat-nhom-va-rieng.md), Bước 3–5 |
| 16 | Máy Bình hiện tin của An mà không bị treo hay bị lừa | Không HTML, tập con Markdown, link hiện đúng địa chỉ, giới hạn 12.000 ký tự, thông báo không có nội dung | [Co-che-hien-thi-noi-dung-an-toan.md](Co-che-hien-thi-noi-dung-an-toan.md) |
| 17 | An gửi một file 40 MB cho Bình | Hỏi trước rồi gửi từng phần 4 KB, cửa sổ trượt, gửi lại nhanh, SHA-256, làm sạch tên file | [Co-che-truyen-file.md](Co-che-truyen-file.md) |
| 18 | Mạng nhà An chập chờn, hoặc server khởi động lại | Tự nối lại, chờ 1 tới 30 giây; `PEER_LEFT` và dọn dẹp; đường mới phải chứng minh lại | [Co-che-duy-tri-ket-noi.md](Co-che-duy-tri-ket-noi.md) |
| 19 | An rời phòng; nếu bật tuỳ chọn thì tin nhóm được lưu mã hoá trên máy | `LocalHistoryStore` mã hoá bằng DPAPI, tên file là mã băm | [Co-che-lich-su-tin-nhan.md](Co-che-lich-su-tin-nhan.md), Bước 3 |
| 20 | An bấm "Đăng xuất" | Server thu hồi token | [Co-che-dang-nhap.md](Co-che-dang-nhap.md), Bước 3 |

**Xuyên suốt cả hành trình:**

| Việc | Cơ chế | Đọc ở |
|---|---|---|
| Ghi log để tìm lỗi mà không lộ bí mật | `SecretLogging`, chế độ gỡ lỗi có cảnh báo, log xoay vòng | [Co-che-ghi-log-an-toan.md](Co-che-ghi-log-an-toan.md) |
| Chứng minh tất cả những điều trên là đúng | 291 test, test đóng vai kẻ tấn công, Docker cô lập, benchmark | [Co-che-kiem-thu.md](Co-che-kiem-thu.md) |

---

## Server biết gì và không biết gì

Hội đồng thường hỏi câu này dưới dạng: "nếu server bị chiếm thì sao?"

| Thông tin | Server biết? | Ghi chú |
|---|---|---|
| Nội dung tin nhắn, file | **Không** | Đi thẳng giữa các máy; qua TURN thì vẫn là gói đã mã hoá |
| Khoá mã hoá tin | **Không** | ECDH làm ra ngay trên hai máy |
| Mật khẩu tài khoản | Chỉ lúc đăng nhập (qua TLS); lưu dạng băm BCrypt | |
| Token đăng nhập | Lưu dạng SHA-256 | Không chuyển tiếp cho người khác |
| Mật khẩu phòng | **Không** | Chỉ thấy thẻ phòng; đoán ngoại tuyến từ thẻ được (xem phần tồn đọng) |
| Ai đang ở phòng nào, tên hiển thị | **Có** | Cần để xếp phòng |
| Địa chỉ IP của người dùng | **Có** | Server thấy khi kết nối; người cùng phòng thấy qua ICE nếu đi thẳng |
| Khoá danh tính công khai của tài khoản | **Có** | Dùng để chặn đăng nhập từ máy lạ, và gửi kèm để client đối chiếu |
| Tin nhắn riêng hay tin nhóm | **Không** | `namespace` nằm trong phần đã mã hoá |

**Nếu server bị chiếm:**
- Kẻ chiếm server **không đọc được tin cũ hay tin mới**.
- Kẻ đó có thể đứng giữa ở lần gặp đầu nếu hai người **không so vân tay**.
- Kẻ đó có thể ghép người lạ vào phòng, nhưng người lạ bị `ROOM_PROOF` chặn.
- Kẻ đó có thể nói sai cờ "Tài khoản" của một người. Vì vậy so vân tay vẫn là cách mạnh nhất.

---

## Các vấn đề còn tồn đọng

Đây là tổng hợp các bước *(đề xuất, chưa làm)* và các lỗ hổng đã nêu trong từng tài liệu. Mức độ được đánh giá theo khả năng bị lợi dụng thật, không theo độ khó sửa.

### Nên sửa trước: có thể bị lợi dụng ngay

| Vấn đề | Hậu quả | Đọc ở |
|---|---|---|
| Spam `OFFER`/`ICE` trên WebSocket làm **người nhận** bị server cắt kết nối | Một thành viên đá được người khác ra khỏi phòng | [Co-che-chong-qua-tai-server.md](Co-che-chong-qua-tai-server.md), Bước 4–5 |
| `/turn-credentials` không cần đăng nhập | Người lạ đi nhờ băng thông relay | [Co-che-chong-qua-tai-server.md](Co-che-chong-qua-tai-server.md), Bước 4–5 |
| Tin vẫn gửi cho người còn "Đang xác minh…"; chữ ký sai không ngắt kết nối | Tin có thể tới người chưa được xác thực | [Co-che-ma-hoa-tin-nhan.md](Co-che-ma-hoa-tin-nhan.md), Bước 10 |
| Thẻ phòng `tên~thẻ` nằm trong log client và log server | Ai đọc được log thì đoán được mật khẩu phòng ngoại tuyến | [Co-che-ghi-log-an-toan.md](Co-che-ghi-log-an-toan.md), Bước 3–4 |
| Ký tự đảo chiều chữ `U+202E` trong tên file, tên người | File `.exe` trông như `.png`; giả được tên người | [Co-che-hien-thi-noi-dung-an-toan.md](Co-che-hien-thi-noi-dung-an-toan.md), Bước 3–4 |

### Nên làm: tăng độ bền và độ an toàn

| Vấn đề | Đọc ở |
|---|---|
| Không có tín hiệu "còn sống"; đường riêng chết không tự dựng lại; nối lại có thể bị `DUPLICATE_PEER`; giao diện không hiện "đang nối lại" | [Co-che-duy-tri-ket-noi.md](Co-che-duy-tri-ket-noi.md) |
| Đoán mật khẩu phòng ngoại tuyến từ thẻ: nên dùng Argon2id và mật khẩu ngẫu nhiên | [Co-che-phong-mat-khau.md](Co-che-phong-mat-khau.md), Bước 4 |
| Đăng nhập chưa có yếu tố thứ hai: nên ký thử thách bằng khoá danh tính | [Co-che-dang-nhap.md](Co-che-dang-nhap.md), Bước 7 |
| Lịch sử chia sẻ không có bằng chứng người viết; mất nhãn "chưa xác minh" sau khi lưu | [Co-che-lich-su-tin-nhan.md](Co-che-lich-su-tin-nhan.md), Bước 4 |
| Tên khách làm giả được dòng log; IP trong log; chưa có thời hạn lưu log | [Co-che-ghi-log-an-toan.md](Co-che-ghi-log-an-toan.md), Bước 4 |
| Link mở ngay không hỏi; ảnh có thể là bom giải nén | [Co-che-hien-thi-noi-dung-an-toan.md](Co-che-hien-thi-noi-dung-an-toan.md), Bước 4 |
| Chưa giới hạn số phòng mỗi IP; chưa có hạn mức băng thông trên coturn | [Co-che-chong-qua-tai-server.md](Co-che-chong-qua-tai-server.md), Bước 5 |
| Không có CI; deploy dùng `-DskipTests`; không có giám sát và sao lưu cơ sở dữ liệu | [Co-che-kiem-thu.md](Co-che-kiem-thu.md) |
| Đổi khoá phiên định kỳ | [Co-che-ma-hoa-tin-nhan.md](Co-che-ma-hoa-tin-nhan.md), Bước 11 |
| Chế độ chỉ đi qua relay để giấu IP; TURN qua TCP/TLS 443 cho mạng chặn UDP | [Co-che-ket-noi-p2p.md](Co-che-ket-noi-p2p.md), Bước 6–7 |

### Cải thiện trải nghiệm

| Vấn đề | Đọc ở |
|---|---|
| Tin có thể hiện lộn thứ tự; không có nút "Gửi lại" | [Co-che-gui-tin-tin-cay.md](Co-che-gui-tin-tin-cay.md), Bước 7 |
| Xoá tin chỉ là đề nghị; cảm xúc có thể lệch giữa các máy | [Co-che-chat-nhom-va-rieng.md](Co-che-chat-nhom-va-rieng.md), Bước 6 |
| Truyền file qua Internet bị giới hạn khoảng 0,37 MB/s do cửa sổ cố định; mất kết nối là gửi lại từ đầu | [Co-che-truyen-file.md](Co-che-truyen-file.md), Bước 5 |
| Ngoài Windows, khoá danh tính và lịch sử không được mã hoá hoặc không lưu được | [Co-che-xac-thuc-danh-tinh.md](Co-che-xac-thuc-danh-tinh.md), [Co-che-lich-su-tin-nhan.md](Co-che-lich-su-tin-nhan.md) |

### Giới hạn chung của mọi ứng dụng mã hoá đầu cuối

Phần này không phải lỗi cần sửa, nhưng nên nói rõ khi bảo vệ:
- **Lần gặp đầu:** nếu hai người không so vân tay thì không chống được người đứng giữa.
- **Xoá tin:** tin đã tới máy người khác thì không thu hồi được bằng kỹ thuật.
- **Khoá danh tính bị lộ:** không có cơ chế thu hồi tập trung; phải sinh khoá mới và so vân tay lại.
- **Siêu dữ liệu:** server vẫn biết ai ở phòng nào, lúc nào.

---

## Đã sửa trong đợt rà soát ngày 22/11/2026

Các mục dưới đây có test tái hiện, chi tiết trong mục J.3 của [Tai-lieu-ky-thuat.md](Tai-lieu-ky-thuat.md):
- Không chuyển tiếp token của người gửi cho người khác.
- Token chỉ gửi tới đúng server đã cấp nó.
- Kết nối thay thế cùng `peerId` phải xác thực lại từ đầu.
- Bấm "Không khớp" khi khoá đã đổi không còn xoá mất dòng cũ trong sổ tay.
- Sửa/xoá tin tìm theo cặp `(mã tin, tác giả)`.
- Lịch sử chia sẻ không gán được tin cho chính người nhận.
- Markdown không còn bị treo bởi chuỗi độc.
- Giới hạn đăng nhập, kết nối, hạn chót JOIN, độ dài trường.
- Dọn bảng phiên đăng nhập.

Thêm ngày 22/11, commit `36ccc20`:
- Nút "Đăng xuất".
- Nhãn "Tài khoản/Khách" cùng việc đối chiếu khoá đã đăng ký, tương thích với client cũ.

---

## Gợi ý thứ tự trình bày khi bảo vệ

1. **Tổng thể:** dùng trang này, gồm hình kiến trúc, hành trình 20 chặng và bảng "server biết gì".
2. **Lõi bảo mật:**
   - [Co-che-ma-hoa-tin-nhan.md](Co-che-ma-hoa-tin-nhan.md);
   - [Co-che-xac-thuc-danh-tinh.md](Co-che-xac-thuc-danh-tinh.md): đây là câu hỏi "nếu server bị chiếm";
   - [Co-che-phong-mat-khau.md](Co-che-phong-mat-khau.md).
3. **Lõi mạng:**
   - [Co-che-ket-noi-p2p.md](Co-che-ket-noi-p2p.md);
   - [Co-che-gui-tin-tin-cay.md](Co-che-gui-tin-tin-cay.md);
   - [Co-che-truyen-file.md](Co-che-truyen-file.md): có số đo từ benchmark.
4. **Tính năng:**
   - [Co-che-dang-nhap.md](Co-che-dang-nhap.md);
   - [Co-che-chat-nhom-va-rieng.md](Co-che-chat-nhom-va-rieng.md);
   - [Co-che-lich-su-tin-nhan.md](Co-che-lich-su-tin-nhan.md).
5. **Độ bền và vận hành:**
   - [Co-che-duy-tri-ket-noi.md](Co-che-duy-tri-ket-noi.md);
   - [Co-che-chong-qua-tai-server.md](Co-che-chong-qua-tai-server.md);
   - [Co-che-ghi-log-an-toan.md](Co-che-ghi-log-an-toan.md);
   - [Co-che-hien-thi-noi-dung-an-toan.md](Co-che-hien-thi-noi-dung-an-toan.md).
6. **Bằng chứng:** [Co-che-kiem-thu.md](Co-che-kiem-thu.md), và mục tồn đọng ở trên, để chủ động nói về giới hạn trước khi bị hỏi.
