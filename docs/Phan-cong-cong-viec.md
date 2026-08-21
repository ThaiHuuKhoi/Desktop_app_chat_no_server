Phân công công việc — nhóm 2 thành viên

# PHÂN CÔNG CÔNG VIỆC

**Đồ án:** Ứng dụng chat ngang hàng (P2P) mã hoá đầu-cuối trên nền tảng Java
**Thành viên:** *[Tên A]* — *[MSSV]* &nbsp;|&nbsp; *[Tên B]* — *[MSSV]*

---

## 1. Nguyên tắc chia việc

Chia theo **tầng kỹ thuật**, không chia theo tính năng lắt nhắt, để mỗi người sở hữu trọn vẹn một mảng, chịu trách nhiệm rõ ràng và có thể làm song song:

- **Thành viên A** phụ trách **Mạng & Kết nối** (signaling, xuyên NAT, truyền dữ liệu tầng thấp).
- **Thành viên B** phụ trách **Bảo mật & Ứng dụng** (mã hoá, giao diện JavaFX, truyền file).

Để hai người không phải chờ nhau, cả hai thống nhất **một interface chung** ngay từ đầu (mục 3) — mỗi người code độc lập trên interface đó, ghép lại ở tuần tích hợp.

## 2. Bảng phân công theo module

| Module | Phụ trách | Nội dung |
|---|---|---|
| Signaling server | **A** | Spring Boot + WebSocket, quản lý phòng, chuyển tiếp SDP/ICE candidate |
| P2P core (NAT traversal) | **A** | Tích hợp ice4j, thiết lập kết nối trực tiếp, fallback TURN relay |
| Giao thức đóng gói dữ liệu | **A** | Framing, xử lý lỗi kết nối, retry |
| Đo hiệu năng kết nối | **A** | Tỉ lệ kết nối P2P thành công, độ trễ thiết lập |
| Module mã hoá | **B** | ECDH trao khoá phiên, AES-GCM mã hoá/giải mã |
| Xác thực đối phương | **B** | Sinh & hiển thị fingerprint khoá công khai, UI so khớp |
| Giao diện JavaFX | **B** | Màn hình tạo/join phòng, khung chat, UI truyền file |
| Truyền file (mã hoá chunk) | **B** | Chia file thành chunk, mã hoá từng chunk, ráp file, UI tiến trình |
| Đánh giá bảo mật | **B** | Test chống nghe lén trung gian (MITM), kiểm tra tính đúng đắn mã hoá |
| Kiểm thử tích hợp, viết báo cáo | **A + B** | Cả hai cùng làm ở giai đoạn cuối |

## 3. Interface chung — điểm tích hợp giữa hai phần việc

Chốt interface này **trước khi tách ra làm song song** (tuần 3–4):

```java
public interface DataChannel {
    void send(byte[] data);
    void onReceive(Consumer<byte[]> handler);
    void close();
}
```

- **A** cài đặt bản thật `P2pDataChannel` — chạy trên kết nối ICE/socket thật.
- **B** cài đặt tạm bản giả lập `LoopbackDataChannel` — chạy nội bộ trong cùng máy, đủ để dựng UI và mã hoá mà **không cần chờ A xong phần mạng**.
- Đến tuần tích hợp, chỉ cần thay `LoopbackDataChannel` bằng `P2pDataChannel` thật của A — B không phải sửa lại logic mã hoá/UI.

## 4. Lịch chạy song song

| Tuần | Thành viên A (Mạng) | Thành viên B (Bảo mật & UI) |
|---|---|---|
| 1–2 | Nghiên cứu ICE/STUN/TURN | Nghiên cứu ECDH/AES-GCM |
| 3–4 | Thiết kế giao thức mạng + chốt interface `DataChannel` chung với B | Thiết kế UI + chốt interface `DataChannel` chung với A |
| 5–6 | Cài signaling server | Cài module mã hoá, test trên kênh giả lập |
| 7–9 | Cài ICE/NAT traversal, kết nối P2P thật | Xây giao diện JavaFX, gắn kênh giả lập |
| 10–11 | **Tích hợp**: cắm kênh P2P thật vào interface chung | **Tích hợp**: cắm mã hoá thật vào UI |
| 12–13 | Truyền file ở tầng mạng (chunk, framing) | Mã hoá từng chunk + UI hiển thị tiến trình |
| 14–15 | Xử lý lỗi kết nối, tối ưu, đo hiệu năng | Fingerprint verify UI, đo bảo mật |
| 16–17 | Kiểm thử tích hợp toàn diện (cả hai) | Kiểm thử tích hợp toàn diện (cả hai) |
| 18–19 | Viết báo cáo phần mình phụ trách + hoàn thiện chung | Viết báo cáo phần mình phụ trách + hoàn thiện chung |

## 5. Quy tắc phối hợp

- **Git**: mỗi người làm trên nhánh riêng (`feature/network-*` cho A, `feature/security-ui-*` cho B), merge vào `main` qua pull request, review chéo trước khi merge.
- **Họp nhóm**: định kỳ 1 buổi/tuần để đồng bộ tiến độ và rà lại interface chung có cần đổi không.
- **Mốc tích hợp** (tuần 10–11) là mốc quan trọng nhất — nên demo thử sớm hơn 1 tuần nếu có thể, để còn thời gian xử lý phát sinh.
- **Tài liệu hoá interface**: mọi thay đổi với `DataChannel` (hoặc interface chung khác phát sinh) phải thông báo cho người còn lại ngay, tránh code lệch nhau.

## 6. Đóng góp cá nhân trong báo cáo (để ghi rõ khi nộp)

| Chương / Phần | Phụ trách |
|---|---|
| Chương 1 — Tổng quan | A + B (viết chung) |
| Chương 2 — Cơ sở lý thuyết: mục NAT/STUN/TURN/ICE | A |
| Chương 2 — Cơ sở lý thuyết: mục mã hoá ECDH/AES-GCM | B |
| Chương 3 — Thiết kế hệ thống | A + B (viết chung, mỗi người phần module của mình) |
| Chương 4 — Cài đặt: phần mạng & signaling | A |
| Chương 4 — Cài đặt: phần mã hoá & giao diện | B |
| Chương 5 — Kiểm thử & đánh giá | A + B (mỗi người phần đo đạc của mình) |
| Chương 6 — Kết luận | A + B (viết chung) |

---

*Ghi chú: điền tên/MSSV ở đầu file. Nếu vai trò công việc thực tế thay đổi trong quá trình làm, cập nhật lại bảng ở mục 2 và 6 để báo cáo cuối kỳ phản ánh đúng đóng góp thực tế của từng người.*
