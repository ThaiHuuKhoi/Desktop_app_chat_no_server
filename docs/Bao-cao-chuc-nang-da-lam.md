# BÁO CÁO CÁC CHỨC NĂNG ĐÃ HOÀN THÀNH — CHAT P2P JAVA

**Ngày:** 20/11/2026 (cập nhật lần 3: truyền file hoàn thành, báo sai mật khẩu phòng ngay, cập nhật số test) · **Phiên bản code:** nhánh `main` (commit `ee4e764`), đã gộp đủ các nhánh `fix/security-hardening`, `feature/chat-ux`, `feature/room-password-history` và phần truyền file của hai thành viên. Signaling server trên VPS đang chạy bản này.

**Cách đọc:** mỗi chức năng gồm 3 phần:
1. **Luồng hoạt động chính:** các bước khi mọi thứ diễn ra bình thường.
2. **Lỗi có thể xảy ra và cách xử lý:** từng tình huống lỗi, hệ thống đang xử lý thế nào, và hướng cải thiện nếu chưa xử lý trọn vẹn.
3. **Kiểm thử:** test tự động đang chứng minh chức năng chạy đúng.

Ký hiệu ở cột trạng thái: ✅ đã xử lý · ⚠️ xử lý một phần · ❌ chưa xử lý (có hướng khắc phục).

---

## Tổng quan

| # | Chức năng | Trạng thái |
|---|---|---|
| 1 | Tài khoản (đăng ký / đăng nhập) và chế độ khách | ✅ Hoàn thành |
| 2 | Sảnh chờ: tạo phòng, danh sách phòng, vào phòng | ✅ Hoàn thành |
| 3 | Kết nối P2P trực tiếp (ICE/STUN, TURN dự phòng) | ✅ Hoàn thành |
| 4 | Trao khoá và mã hoá đầu-cuối | ✅ Hoàn thành |
| 5 | Xác thực danh tính người trong phòng | ✅ Hoàn thành |
| 6 | Chat nhóm | ✅ Hoàn thành |
| 7 | Chat riêng 1-1 trong phòng | ✅ Hoàn thành |
| 8 | Trạng thái "đang gõ…" | ✅ Hoàn thành |
| 9 | Định dạng tin nhắn (Markdown) và link | ✅ Hoàn thành |
| 10 | Âm báo, thông báo màn hình, cài đặt, chọn giao diện | ✅ Hoàn thành |
| 11 | Lịch sử chat cho người mới vào phòng | ✅ Hoàn thành |
| 12 | Phòng có mật khẩu | ✅ Hoàn thành |
| 13 | Rời phòng / mất kết nối | ✅ Hoàn thành |
| 14 | Truyền file 1-1 | ✅ Hoàn thành |
| 15 | Log theo dõi trên terminal (bình thường / chi tiết / bí mật) | ✅ Hoàn thành |

Kiến trúc chung: mỗi người chạy một client JavaFX. Signaling server (Spring Boot + WebSocket) chỉ làm nhiệm vụ "giới thiệu" các client với nhau. Sau khi kết nối xong, mọi nội dung đi thẳng giữa các máy qua UDP và được mã hoá đầu-cuối; server không đọc được và không lưu nội dung chat.

---

## 1. Tài khoản và chế độ khách

**Mô tả:** người dùng chọn **Khách tự do** (không cần tài khoản) hoặc **Đăng nhập / Đăng ký**. Tài khoản lưu trong MySQL trên server; mật khẩu băm bằng BCrypt; server chỉ lưu bản băm SHA-256 của token phiên, không lưu token gốc.

**Luồng hoạt động chính**
1. Người dùng nhập tên tài khoản và mật khẩu, bấm Đăng ký hoặc Đăng nhập.
2. Client gọi REST `/auth/register` hoặc `/auth/login`, gửi kèm public key danh tính của máy.
3. Server kiểm tra dữ liệu và mật khẩu, tạo token phiên ngẫu nhiên 32 byte (hiệu lực 12 giờ), trả token về client.
4. Khi vào phòng, client gửi token trong bản tin `JOIN`. Server kiểm tra token và dùng tên tài khoản làm tên hiển thị.
5. Người đã đăng nhập xem được danh sách phòng đang hoạt động (`/rooms`).

**Lỗi có thể xảy ra và cách xử lý**

| Tình huống | Hệ thống xử lý | TT | Hướng cải thiện |
|---|---|---|---|
| Sai tên đăng nhập hoặc mật khẩu | Server trả lỗi, giao diện hiện "Tên tài khoản hoặc mật khẩu không đúng" (không tiết lộ tài khoản có tồn tại hay không) | ✅ | |
| Tên tài khoản đã tồn tại | Hiện "Tên tài khoản đã tồn tại" | ✅ | |
| Tên hoặc mật khẩu không hợp lệ | Hiện yêu cầu: tên 3–64 ký tự (chữ, số, `_`), mật khẩu 8–200 ký tự | ✅ | |
| Đăng nhập từ máy có khoá danh tính khác với khoá đã gắn với tài khoản | Từ chối: "Khóa identity không khớp tài khoản này" | ✅ | Thêm chức năng chuyển khoá sang máy mới |
| Không kết nối được tới server | Hiện "Không thể kết nối auth server" | ✅ | |
| Người ngoài truy cập thẳng cơ sở dữ liệu tài khoản | MySQL trên VPS chỉ mở ở `127.0.0.1` (không ra Internet); server không còn mật khẩu DB mặc định, bắt buộc đặt qua biến môi trường | ✅ | |
| Token hết hạn (sau 12 giờ) hoặc không hợp lệ khi vào phòng | Server từ chối và gửi `JOIN_REJECTED(AUTH_EXPIRED / AUTH_REQUIRED)`; client báo "Phiên đăng nhập đã hết hạn" và chuyển sang màn hình đăng nhập | ✅ | |
| Token và mật khẩu đi qua mạng dạng rõ (`ws://`, `http://`) | Chưa mã hoá đường truyền tới server | ❌ | Chuyển sang `wss://` / `https://` (kế hoạch ở Tài liệu kỹ thuật, mục J.1) |

---

## 2. Sảnh chờ: tạo phòng, danh sách phòng, vào phòng

**Mô tả:** phòng được xác định bằng tên; mỗi phòng tối đa 8 người, vì kiến trúc mesh có chi phí tăng theo bình phương số người. Server không có bước "tạo phòng" riêng: phòng tự sinh khi có người đầu tiên vào, và tự mất khi người cuối cùng rời đi. Ở sảnh chờ có 3 cách vào phòng:

| Cách | Dành cho | Các bước |
|---|---|---|
| **Tạo phòng** | Người mở phòng mới | Bấm **Tạo phòng** → hộp **"Thông tin phòng"**: tên phòng (có sẵn tên ngẫu nhiên, nút đổi tên, nút sao chép), ô **"Đặt mật khẩu cho phòng"** (hiện thêm ô mật khẩu và ô nhập lại) → **Tạo phòng** |
| **Chọn trong danh sách** | Người đã đăng nhập | Danh sách "Phòng đang hoạt động" hiện tên phòng, số người, biểu tượng 🔒 (có mật khẩu) hoặc `#` (công khai). Bấm một phòng → **hộp xác nhận** → nếu phòng có mật khẩu thì **hộp nhập mật khẩu** → vào |
| **Vào phòng bằng tên** | Khách (không có danh sách), hoặc phòng bạn bè gửi tên qua kênh khác | Hộp nhập tên phòng và mật khẩu (nếu có) → vào |

**Luồng hoạt động chính (sau khi chọn xong phòng)**
1. Client kiểm tra tên hiển thị, địa chỉ server, trạng thái đăng nhập (ở chế độ tài khoản).
2. Client tạo phiên trên **luồng nền**, vì phòng có mật khẩu cần chạy PBKDF2 khoảng 0,1–0,3 s; giao diện hiện "Đang vào phòng …", không bị đứng.
3. Client mở WebSocket tới signaling server và gửi `JOIN {roomId, peerId, userName}`. `peerId` là một UUID mới cho mỗi lần vào phòng; `roomId` là tên phòng, hoặc `tên~thẻ` với phòng có mật khẩu (chức năng 12).
4. Server trả về `PEER_LIST`, là danh sách những người đã ở trong phòng, đồng thời gửi `PEER_JOINED` cho những người đó.
5. Người mới vào chủ động bắt đầu kết nối P2P với từng người trong `PEER_LIST` (chức năng 3).

**Khi server từ chối (thay cho bước 4):** server gửi `JOIN_REJECTED` kèm lý do. Client rời màn hình phòng, quay về sảnh và hiện lý do bằng tiếng Việt:

| Lý do | Người dùng thấy | Bước tiếp theo |
|---|---|---|
| `WRONG_PASSWORD` | "Sai mật khẩu phòng …" | Hộp nhập mật khẩu của đúng phòng đó tự mở lại |
| `RATE_LIMITED` | "Bạn đã nhập sai mật khẩu quá nhiều lần…" | Thử lại sau 1 phút |
| `ROOM_FULL` | "Phòng … đã đủ người (tối đa 8 người)" | Chọn phòng khác |
| `AUTH_EXPIRED`, `AUTH_REQUIRED` | "Phiên đăng nhập đã hết hạn…" | Chế độ tài khoản: chuyển sang màn hình đăng nhập |
| `DUPLICATE_PEER`, `INVALID_JOIN` | Thông báo lỗi chung | Thử vào lại |

**Danh sách phòng (`/rooms`, chỉ cho người đã đăng nhập):** server trả về tên phòng, số người và cờ `passwordProtected`. Với phòng có mật khẩu, server **cắt bỏ thẻ** (`tên~thẻ` → `tên`), nên người xem danh sách không có gì để dò mật khẩu. Client vẫn hiểu định dạng cũ `tên~thẻ` của server chưa cập nhật.

**Lỗi có thể xảy ra và cách xử lý**

| Tình huống | Hệ thống xử lý | TT | Hướng cải thiện |
|---|---|---|---|
| Nhập sai trong hộp thoại (tên rỗng, tên có `~`, mật khẩu dưới 6 ký tự, nhập lại không khớp, chưa nhập mật khẩu) | Hộp thoại **giữ nguyên** và hiện lỗi ngay bên dưới, không đóng mất dữ liệu đã nhập | ✅ | |
| Gõ thừa dấu cách ở đầu/cuối mật khẩu hoặc tên phòng | Tự cắt khoảng trắng hai đầu, ở mọi phía, nên vẫn khớp nhau | ✅ | |
| Tạo phòng trùng tên phòng công khai đang có người | Hộp "Thông tin phòng" cảnh báo "bạn sẽ vào chung phòng với những người đang ở đó" | ✅ | |
| Tạo phòng có mật khẩu trùng tên phòng có mật khẩu đang có | Cảnh báo "nếu mật khẩu của bạn khác, bạn sẽ bị báo sai mật khẩu — hãy chọn tên khác"; nếu vẫn vào thì server trả `WRONG_PASSWORD` | ✅ | |
| Nội dung hộp thoại cao thêm (hiện ô mật khẩu, hiện dòng lỗi) | Cửa sổ hộp thoại tự giãn theo, hàng nút luôn hiện | ✅ | |
| Ở một mình trong phòng quá lâu (sai tên phòng, hoặc bạn bè chưa vào) | Sau 20 s hiện gợi ý kiểm tra lại tên phòng (phân biệt hoa thường). Sai mật khẩu thì đã được báo ngay | ⚠️ | |
| Tên phòng phân biệt hoa thường (`Phong-A` khác `phong-a`) | Ghi rõ trong gợi ý ở sảnh và trong hộp thoại | ⚠️ | Chuẩn hoá tên phòng về chữ thường |
| Không kết nối được tới server khi vào phòng | Báo lỗi lên giao diện: "Không thể kết nối tới signaling server", mở lại các nút ở sảnh | ✅ | |
| Mất kết nối với server khi đang trong phòng | Client tự kết nối lại với thời gian chờ tăng dần (1 s, 2 s, 4 s… tối đa 30 s) và tự gửi lại `JOIN`. Các kết nối P2P đã có **vẫn hoạt động** vì không đi qua server | ✅ | Hiện trạng thái "Đang kết nối lại…" trên giao diện |
| Phòng đã đủ 8 người, hoặc trùng `peerId` | Server gửi `JOIN_REJECTED(ROOM_FULL / DUPLICATE_PEER)`; client quay về sảnh và hiện lý do | ✅ | |
| Bản tin JSON hỏng hoặc thiếu trường | Server bỏ qua bản tin đó, không ảnh hưởng người khác | ✅ | |
| Một client cố giả mạo người khác khi gửi OFFER/ANSWER | Server chỉ chuyển tiếp nếu người gửi đã `JOIN` đúng phòng và đúng `peerId` | ✅ | |
| Server sập | Không ai vào phòng mới được; các cuộc chat đang diễn ra vẫn tiếp tục | ⚠️ | Chấp nhận ở quy mô đồ án (Tài liệu kỹ thuật, rủi ro 5) |

---

## 3. Kết nối P2P trực tiếp (ICE/STUN, TURN dự phòng)

**Mô tả:** hai máy tìm đường kết nối UDP trực tiếp xuyên qua NAT/router bằng thư viện ice4j. STUN dùng để biết địa chỉ public; TURN dùng làm đường relay dự phòng khi không đi thẳng được.

**Luồng hoạt động chính**
1. Bên vào sau tạo **OFFER**, gồm danh sách địa chỉ có thể kết nối (ICE candidate), và gửi qua server tới bên kia.
2. Bên kia tạo **ANSWER**, gồm candidate của mình, và gửi ngược lại.
3. Hai bên thử ghép từng cặp địa chỉ (connectivity check) và chọn cặp kết nối được tốt nhất: trực tiếp trước, TURN sau.
4. Khi thành công, mỗi bên có một kênh UDP riêng tới bên kia (`P2pDataChannel`). Từ đây server không còn tham gia.

**Lỗi có thể xảy ra và cách xử lý**

| Tình huống | Hệ thống xử lý | TT | Hướng cải thiện |
|---|---|---|---|
| Hai mạng có NAT chặt (symmetric NAT), không đi thẳng được | Tự chuyển sang relay qua TURN nếu đã cấu hình TURN | ✅ | |
| Chưa cấu hình TURN mà đi thẳng cũng thất bại | Báo trên giao diện: "Không kết nối được với X: …". Thông tin TURN **không còn đóng gói trong jar**; mỗi máy đặt trong `~/.chat-p2p-java/client.properties` (hoặc biến môi trường `CHAT_P2P_TURN_*`) | ⚠️ | Hướng dẫn người dùng cấu hình TURN ngay trong thông báo lỗi |
| Tường lửa Windows chặn UDP | Lần đầu chạy, app xin quyền tạo luật tường lửa cho dải cổng UDP 10000–11000 | ✅ | |
| OFFER/ANSWER hỏng hoặc candidate sai định dạng | Chỉ huỷ kết nối với đúng người đó, những người khác không ảnh hưởng | ✅ | |
| Một người gửi OFFER liên tục (cố ý làm cạn cổng UDP) | Mỗi OFFER mới huỷ phiên ICE cũ của cùng người đó, không rò rỉ cổng | ✅ | Giới hạn tốc độ OFFER ở server |
| ICE hoàn tất **sau khi** người dùng đã rời phòng | Phát hiện và đóng ngay, không "hồi sinh" kết nối | ✅ | |
| Rời phòng mà cổng UDP không được giải phóng | Khi rời phòng, giải phóng toàn bộ phiên ICE, kể cả những phiên đã kết nối | ✅ | |

---

## 4. Trao khoá và mã hoá đầu-cuối

**Mô tả:** mọi dữ liệu qua kênh P2P đều được mã hoá bằng **AES-256-GCM**, với khoá phiên sinh bằng **ECDH** (đường cong secp256r1). Mỗi kết nối có cặp khoá ECDH riêng, sinh mới mỗi lần.

**Luồng hoạt động chính**
1. Ngay khi kênh UDP mở, mỗi bên gửi public key ECDH của mình. Đây là gói duy nhất không mã hoá, vì public key không cần giữ bí mật.
2. Mỗi bên tự tính khoá bí mật chung từ private key của mình và public key của bên kia, rồi băm SHA-256 ra khoá AES-256.
3. Từ gói thứ hai, mọi dữ liệu đóng thành `Envelope` (JSON) và mã hoá AES-GCM với IV ngẫu nhiên 12 byte. GCM vừa giữ bí mật vừa phát hiện dữ liệu bị sửa.

**Lỗi có thể xảy ra và cách xử lý**

| Tình huống | Hệ thống xử lý | TT | Hướng cải thiện |
|---|---|---|---|
| Gói public key ECDH bị mất (UDP không đảm bảo) | Tự gửi lại tối đa 5 lần, mỗi lần cách 300 ms | ✅ | |
| Dữ liệu bị sửa trên đường truyền, hoặc giải mã sai khoá | GCM phát hiện, gói bị bỏ qua, kết nối không bị sập | ✅ | |
| Kẻ tấn công bắt gói rồi gửi lại (replay) | Mỗi kết nối nhớ dấu vân tay SHA-256 của 256 gói gần nhất; gói trùng bị bỏ qua | ✅ | Thêm số thứ tự gói để chống replay gói cũ hơn 256 gói |
| Kẻ đứng giữa tráo khoá ECDH khi trao khoá | Phát hiện ở bước xác thực danh tính (chức năng 5), vì chữ ký gắn với khoá ECDH của phiên | ✅ | |

---

## 5. Xác thực danh tính người trong phòng

**Mô tả:** mỗi máy có một cặp khoá danh tính **ECDSA** lâu dài, lưu ở `~/.chat-p2p-java/identity.properties`. Sau khi trao khoá, hai bên chứng minh danh tính với nhau; giao diện hiện dấu "đã xác thực" và fingerprint để người dùng đối chiếu.

**Luồng hoạt động chính**
1. Mỗi bên gửi `PEER_IDENTITY`, gồm public key danh tính và chữ ký trên chuỗi `phòng | peerId | SHA-256(khoá ECDH của mình) | SHA-256(khoá ECDH của bên kia)`.
2. Bên nhận kiểm tra chữ ký bằng chính các khoá ECDH mà nó thấy trong phiên.
3. Hợp lệ thì đánh dấu người đó là **Đã xác thực** và hiện fingerprint (người dùng có thể so qua kênh khác như gọi điện hoặc gặp trực tiếp).

**Lỗi có thể xảy ra và cách xử lý**

| Tình huống | Hệ thống xử lý | TT | Hướng cải thiện |
|---|---|---|---|
| Gói `PEER_IDENTITY` bị mất | Gửi lại sau 0,4 s, 1,2 s và 2,5 s | ✅ | |
| Chữ ký sai (giả mạo, hoặc bị tráo khoá ECDH) | Không đánh dấu xác thực; giao diện cảnh báo "Identity peer không hợp lệ"; người đó vẫn hiện "chưa xác thực" | ✅ | Tự động ngắt kết nối với người có chữ ký sai |
| Lần gặp đầu tiên với kẻ mạo danh có khoá riêng (TOFU) | Không phát hiện tự động được, vì không có cơ quan cấp chứng chỉ | ⚠️ | Người dùng đối chiếu fingerprint; có thể lưu fingerprint đã tin cậy để cảnh báo khi khoá đổi |
| File khoá danh tính bị hỏng | App dừng và báo rõ đường dẫn file, **không tự ghi đè** để tránh mất danh tính | ✅ | Thêm nút "tạo lại danh tính" có xác nhận |

---

## 6. Chat nhóm

**Mô tả:** tin nhắn gửi tới mọi người trong phòng. Mỗi tin đi thẳng tới từng người qua kênh P2P đã mã hoá (mesh), không qua server.

**Luồng hoạt động chính**
1. Người dùng gõ tin (Enter để gửi, Shift+Enter để xuống dòng); tin hiện ngay ở khung chat của mình.
2. Client gửi `MESSAGE {id, text, timeSent}` tới từng người trong phòng.
3. Người nhận hiển thị tin, trả `MESSAGE_ACK {id}`, và lưu tin vào lịch sử nhóm (phục vụ chức năng 11).
4. Người gửi nhận ACK thì ngừng theo dõi tin đó.

**Lỗi có thể xảy ra và cách xử lý**

| Tình huống | Hệ thống xử lý | TT | Hướng cải thiện |
|---|---|---|---|
| Tin bị mất trên đường truyền | Không nhận ACK thì gửi lại, tối đa 6 lần, mỗi lần cách 0,5 s | ✅ | |
| ACK bị mất nên người nhận nhận cùng một tin 2 lần | Người nhận loại bản trùng theo `id` của tin, vẫn trả ACK lại | ✅ | |
| Gửi lại 6 lần vẫn không có ACK | Giao diện cảnh báo "tin nhắn chưa tới nơi" cho đúng người đó | ✅ | Hiện trạng thái "đã gửi / chưa tới" ngay trên từng tin |
| Gửi lỗi với một người trong phòng | Vẫn gửi bình thường cho những người còn lại | ✅ | |
| Người nhận rời phòng giữa lúc đang gửi | Huỷ việc gửi lại tới người đó, không báo lỗi thừa | ✅ | |
| Tin đến sai thứ tự (UDP có thể đảo gói) | Hiển thị theo thứ tự nhận | ⚠️ | Sắp xếp theo thời điểm gửi khi chênh lệch nhỏ |
| Tin quá dài (khoảng trên 30 KB sau mã hoá, vượt giới hạn một gói UDP) | Không gửi được; giao diện báo lỗi, nhưng câu chữ mang tính kỹ thuật | ⚠️ | Giới hạn độ dài ô nhập và báo rõ cho người dùng, hoặc tự chia nhỏ tin |
| Đồng hồ máy người gửi bị lệch về tương lai | Thời gian tin bị giới hạn không vượt quá thời điểm nhận | ✅ | |

---

## 7. Chat riêng 1-1 trong phòng

**Mô tả:** trong phòng đông người, bấm biểu tượng chat cạnh tên một người để mở tab chat riêng với người đó. Tin chỉ gửi tới đúng người đó.

**Luồng hoạt động chính**
1. Người dùng bấm biểu tượng chat cạnh tên người nhận, một tab "DM: Tên" được mở.
2. Tin gửi trong tab này đi kèm đánh dấu tin riêng (namespace `dm`), **chỉ** tới kênh P2P của người nhận.
3. Người nhận thấy tin trong tab DM tương ứng. Nếu tab chưa có thì tự tạo; nếu người nhận đang gõ dở ở tab khác thì app **không** tự chuyển tab.
4. Tin riêng có ACK và gửi lại giống chat nhóm, và **không bao giờ** được đưa vào lịch sử gửi cho người mới vào phòng.
5. **Gửi file trong tab DM** (nút gửi tệp hoặc kéo thả) thì file chỉ gửi cho đúng người đó; thẻ file hiện trong tab DM của cả hai bên (chức năng 14).

**Lỗi có thể xảy ra và cách xử lý**

| Tình huống | Hệ thống xử lý | TT | Hướng cải thiện |
|---|---|---|---|
| Người nhận đã rời phòng | Báo "Gửi tin thất bại: Người này chưa vào phòng hoặc đã rời phòng" | ✅ | Khoá ô nhập của tab DM khi người đó rời phòng |
| Mất gói, trùng gói, hết lượt gửi lại | Như chức năng 6 | ✅ | |
| Người thứ ba muốn đọc tin riêng | Không thể: tin chỉ đi trên kênh mã hoá giữa hai người, server không chuyển tiếp nội dung | ✅ | |
| Muốn nhắn riêng cho người không ở cùng phòng | Chưa hỗ trợ (DM chỉ trong phạm vi phòng) | ❌ | Danh bạ và chat 1-1 theo tài khoản (ngoài phạm vi đề cương) |

---

## 8. Trạng thái "đang gõ…"

**Luồng hoạt động chính**
1. Khi người dùng bắt đầu gõ, client gửi `TYPING_STATUS_CHANGE {typing: true}` tới khung chat đang mở (nhóm, hoặc người trong tab DM).
2. Nếu vẫn gõ tiếp, cứ 2,5 s nhắc lại một lần. Khi ngừng gõ 3 s, xoá hết chữ, gửi tin hoặc đổi tab thì gửi `typing: false`.
3. Bên nhận hiện "An đang gõ…", "An và Bình đang gõ…" hoặc "An, Bình và 2 người khác đang gõ…" ngay trên ô nhập.

**Lỗi có thể xảy ra và cách xử lý**

| Tình huống | Hệ thống xử lý | TT |
|---|---|---|
| Gói "ngừng gõ" bị mất, chữ "đang gõ" bị treo mãi | Bên nhận tự tắt sau 6 s nếu không được nhắc lại | ✅ |
| Gửi tín hiệu theo từng phím bấm gây tốn băng thông | Chỉ gửi khi bắt đầu gõ và nhắc lại định kỳ | ✅ |
| Người đang gõ rời phòng | Xoá trạng thái của người đó ngay | ✅ |
| Gửi tín hiệu lỗi | Bỏ qua, không làm phiền người dùng (tín hiệu không quan trọng) | ✅ |

---

## 9. Định dạng tin nhắn (Markdown) và link

**Mô tả:** hỗ trợ `**đậm**`, `*nghiêng*`, `~~gạch ngang~~`, `` `code` ``, khối code ```` ``` ````, danh sách `- mục`, trích dẫn `> …`; link `http(s)://` tự động bấm được.

**Luồng hoạt động chính:** khi hiển thị, nội dung tin được phân tích thành các khối và đoạn chữ có kiểu, rồi vẽ bằng JavaFX `TextFlow`. Bấm vào link sẽ mở trình duyệt mặc định.

**Lỗi có thể xảy ra và cách xử lý**

| Tình huống | Hệ thống xử lý | TT |
|---|---|---|
| Ký hiệu chưa đóng (`**abc`) hoặc tên biến kiểu `my_var_name` | Hiện nguyên văn, không định dạng sai | ✅ |
| Muốn gõ ký tự `*` thật | Dùng `\*` | ✅ |
| Link lừa đảo dạng `[Ngân hàng](http://trang-gia)` | Cố ý **không hỗ trợ** cú pháp này, nên địa chỉ thật của link luôn hiện ra | ✅ |
| Link nguy hiểm (`file:`, `javascript:`) | Chỉ mở link `http`/`https` | ✅ |
| Khung chat không cuộn hết xuống tin dài | Cuộn lại sau khi giao diện tính xong chiều cao các tin | ✅ |

---

## 10. Âm báo, thông báo màn hình, cài đặt, chọn giao diện

**Luồng hoạt động chính**
1. Có tin mới mà người dùng không đang nhìn đúng khung chat đó thì phát âm báo ngắn. Âm được tạo bằng code, không cần file âm thanh.
2. Nếu cửa sổ app không được chọn thì hiện thông báo của Windows: "An đã gửi tin nhắn trong phòng …". Bấm vào thông báo sẽ mở lại cửa sổ.
3. Nút **Cài đặt** (bánh răng) cho phép chọn giao diện (Primer, Nord, Cupertino sáng/tối, Dracula), bật/tắt âm báo và thông báo. Lựa chọn được lưu ở `~/.chat-p2p-java/settings.properties`.

**Lỗi có thể xảy ra và cách xử lý**

| Tình huống | Hệ thống xử lý | TT |
|---|---|---|
| Thông báo làm lộ nội dung tin trên màn hình khoá | Thông báo **chỉ hiện tên người gửi**, không hiện nội dung | ✅ |
| Máy không có loa / thiết bị âm thanh | Bỏ qua, không lỗi | ✅ |
| Hệ điều hành không hỗ trợ khay hệ thống | Bỏ qua thông báo, app vẫn chạy | ✅ |
| Nhiều tin đến cùng lúc gây âm báo dồn dập | Đang phát thì bỏ qua âm mới | ✅ |
| File cài đặt hỏng | Dùng giá trị mặc định, app không bị sập | ✅ |

---

## 11. Lịch sử chat cho người mới vào phòng

**Mô tả:** người vào sau thấy được các tin nhóm trước đó, do những người đang ở trong phòng gửi lại. Lịch sử chỉ nằm trong RAM (tối đa 200 tin), mất khi rời phòng; server không lưu.

**Luồng hoạt động chính**
1. Khi xác thực xong danh tính một người, client gửi cho người đó lịch sử nhóm qua `MESSAGE_TRANSCRIPT`, chia thành nhiều gói vừa một gói UDP.
2. Mỗi gói có ACK và được gửi lại nếu mất.
3. Người nhận gộp lịch sử, bỏ các tin đã có (theo `id`), rồi chèn vào khung chat đúng thứ tự thời gian, kèm nhãn **"lịch sử · X chia sẻ"** và dòng thông báo "Đã nhận N tin nhắn trước đó do X chia sẻ".
4. Cả hai bên đều gửi lịch sử cho nhau. Người mới vào (lịch sử rỗng) chỉ nhận; hai người cũ kết nối lại thì bù cho nhau phần còn thiếu.

**Lỗi có thể xảy ra và cách xử lý**

| Tình huống | Hệ thống xử lý | TT | Hướng cải thiện |
|---|---|---|---|
| Gói lịch sử bị mất | Gửi lại tối đa 6 lần theo cơ chế ACK | ✅ | |
| Nhiều người cùng gửi lịch sử nên trùng tin | Loại trùng theo `id` | ✅ | |
| Lịch sử quá dài vượt giới hạn gói UDP | Chia nhiều gói; tin dài trên 12.000 ký tự không đưa vào lịch sử | ✅ | |
| Người chia sẻ chưa được xác thực danh tính | Không nhận và không ACK; bên gửi sẽ gửi lại sau khi xác thực xong | ✅ | |
| Người trong phòng gửi lịch sử giả (bịa tin của người khác) | Giới hạn 200 tin mỗi người chia sẻ; giao diện ghi rõ "do X chia sẻ, không xác minh được người viết" | ⚠️ | Ký từng tin bằng khoá danh tính của người viết |
| Tin nhắn riêng bị lộ qua lịch sử | Lịch sử **chỉ** gồm tin nhóm | ✅ | |
| Đồng hồ các máy lệch nhau | Không cho thời gian ở tương lai; sắp xếp theo thời điểm gửi | ⚠️ | |

---

## 12. Phòng có mật khẩu

**Mô tả:** người tạo phòng tích "Đặt mật khẩu cho phòng" trong hộp **Thông tin phòng** (tối thiểu 6 ký tự, có ô nhập lại). Người vào phòng được hỏi mật khẩu trong hộp nhập mật khẩu sau khi chọn phòng có biểu tượng 🔒, hoặc nhập trong hộp "Vào phòng bằng tên". **Mật khẩu không bao giờ rời khỏi máy**, kể cả server cũng không biết.

**Luồng hoạt động chính**
1. Từ (tên phòng, mật khẩu), client dùng PBKDF2 (120.000 vòng) tạo ra một khoá chính, rồi tách thành "thẻ phòng" và "khoá chứng minh". Bước này chạy trên luồng nền.
2. Client gửi lên server tên phòng dạng `tên~thẻ`. Người nhập mật khẩu khác sẽ ra thẻ khác, rơi vào phòng khác trên server, nên không nhìn thấy nhau.
3. Sau khi trao khoá ECDH, hai bên gửi `ROOM_PROOF`, một mã HMAC gắn với khoá ECDH của cả hai trong phiên, chứng minh mình biết mật khẩu.
4. Chỉ người có bằng chứng hợp lệ mới hiện trên giao diện và được trao đổi tin.
5. Trong danh sách phòng ở sảnh, phòng hiện với biểu tượng 🔒, tên và số người; **thẻ không bao giờ được gửi cho người xem danh sách**.

**Khi nhập sai mật khẩu (Tài liệu kỹ thuật mục J.2)**
1. Client tạo ra thẻ khác thẻ của phòng thật, nên gửi JOIN `tên~thẻ-khác`.
2. Server thấy đang có phòng có mật khẩu **cùng tên nhưng khác thẻ**, nên **từ chối** và gửi lại `JOIN_REJECTED(WRONG_PASSWORD)`. Server vốn đã biết các thẻ đang dùng, nên không lộ thêm thông tin gì; thẻ đúng không bao giờ được gửi cho client.
3. Client rời màn hình phòng, quay về sảnh, hiện "Sai mật khẩu phòng …" và **mở lại hộp nhập mật khẩu** của đúng phòng đó.
4. **Giới hạn số lần thử:** quá 5 lần sai trong 1 phút từ cùng một địa chỉ IP thì server trả `RATE_LIMITED` cho mọi lần JOIN phòng có mật khẩu (kể cả mật khẩu đúng) đến khi hết 1 phút. Người ngoài chỉ thử được từng lần, không dò hàng loạt được.
5. **Hệ quả:** khi tên phòng đang được một nhóm dùng với mật khẩu khác, nhóm vào sau sẽ bị báo sai mật khẩu và phải chọn tên khác. Khi mọi người đã rời phòng, tên đó lại dùng được với mật khẩu mới.

**Lỗi có thể xảy ra và cách xử lý**

| Tình huống | Hệ thống xử lý | TT | Hướng cải thiện |
|---|---|---|---|
| Nhập sai mật khẩu | Server từ chối ngay (`WRONG_PASSWORD`); client quay về sảnh, báo "Sai mật khẩu phòng" và mở lại hộp nhập mật khẩu | ✅ | |
| Thử sai mật khẩu nhiều lần liên tiếp | Quá 5 lần/phút từ cùng IP thì bị chặn (`RATE_LIMITED`) trong 1 phút | ✅ | |
| Mật khẩu quá ngắn khi tạo phòng | Không cho tạo, yêu cầu tối thiểu 6 ký tự; phải nhập lại trùng khớp | ✅ | |
| Server bị chiếm và cố ghép người lạ vào phòng | Người lạ không có bằng chứng hợp lệ nên bị bỏ qua; giao diện cảnh báo "người này không có đúng mật khẩu phòng" | ✅ | |
| Người sai mật khẩu vẫn giữ kết nối P2P | Mọi bản tin của họ bị lọc, nhưng kết nối chưa bị chủ động ngắt | ⚠️ | Thêm chức năng ngắt kết nối một người trong `RoomSession` |
| Dùng lại bằng chứng của phiên khác | Không được, vì bằng chứng gắn với khoá ECDH riêng của từng phiên | ✅ | |
| Người quản trị server (hoặc kẻ chiếm server) đoán mật khẩu từ thẻ phòng | Phải dò PBKDF2 120.000 vòng cho mỗi lần đoán; mật khẩu tối thiểu 6 ký tự | ⚠️ | Người dùng nên đặt mật khẩu dài |
| Người xem danh sách phòng muốn dò mật khẩu | Không được: danh sách chỉ có tên, số người và cờ có mật khẩu, không có thẻ | ✅ | |
| Gói `ROOM_PROOF` bị mất | Gửi lại sau 0,4 s, 1,2 s và 2,5 s | ✅ | |
| Tên phòng chứa ký tự `~` (trùng ký tự ngăn cách) | Không cho phép, báo lỗi ngay trong hộp thoại | ✅ | |

---

## 13. Rời phòng / mất kết nối

**Luồng hoạt động chính**
1. Người dùng bấm **Rời phòng**, xác nhận cảnh báo "tin nhắn sẽ bị xoá khỏi máy", hoặc đóng cửa sổ.
2. Client **huỷ mọi file đang gửi / nhận** (gửi `FILE_CANCEL` cho bên kia, từ chối các lời mời nhận file chưa trả lời, xoá file tạm `.part`), gửi tín hiệu ngừng gõ, rồi đóng mọi kết nối P2P, giải phóng cổng UDP, ngắt WebSocket.
3. Server gửi `PEER_LEFT` cho những người còn lại; giao diện của họ hiện "X đã rời phòng", và thẻ file đang truyền với người đó chuyển sang trạng thái thất bại, kèm lý do người kia đã rời phòng.

**Lỗi có thể xảy ra và cách xử lý**

| Tình huống | Hệ thống xử lý | TT |
|---|---|---|
| Tắt app đột ngột, không kịp gửi `LEAVE` | Server phát hiện WebSocket đóng và tự báo `PEER_LEFT` | ✅ |
| Lỗi khi đóng một kết nối | Vẫn tiếp tục đóng các kết nối còn lại, không rò rỉ | ✅ |
| Callback mạng đến sau khi đã rời phòng | Bị bỏ qua, không cập nhật giao diện cũ | ✅ |

---

## 14. Truyền file 1-1

**Mô tả:** gửi file tới người trong phòng, đi thẳng qua kênh P2P đã mã hoá (server không thấy nội dung). Mỗi lần truyền là **1-1**: gửi trong tab DM thì tới đúng người đó; gửi trong khung nhóm thì app tạo **một lần truyền riêng cho từng người** đang ở trong phòng (mỗi người tự bấm Nhận hoặc Từ chối). Giao thức do hai thành viên thống nhất (`docs/Thong-nhat-giao-thuc-truyen-file.md`, `docs/Tra-loi-giao-thuc-truyen-file.md`): A làm tầng gửi tin cậy (`ReliableChunkSender`), B làm đọc/ghi file (`FileSender`, `FileReceiver`, `FileNames`), điều phối (`FileTransferManager`) và giao diện. Phạm vi: 1-1, tối đa 100 MB, mỗi người một file đang nhận tại một thời điểm, mất kết nối thì huỷ (không gửi tiếp).

**Cách dùng:** bấm nút gửi tệp (hoặc **kéo thả 1 file** vào khung chat) trong khung nhóm hoặc tab chat riêng. Trong khung chat hiện **thẻ file** với tên, dung lượng, trạng thái và thanh tiến độ. Người nhận bấm **Nhận** hoặc **Từ chối**; hai bên đều có nút **Huỷ** khi đang truyền. File nhận được lưu vào `~/Downloads`, có nút mở thư mục chứa file.

**Luồng hoạt động chính**
1. Bên gửi tính SHA-256 cả file (trên luồng nền), gửi `FILE_OFFER` (tên, kích thước, số chunk, SHA-256), gửi lại mỗi giây cho tới khi có trả lời.
2. Bên nhận kiểm tra offer (kích thước, số chunk, định dạng SHA-256), hiện thẻ file; người dùng bấm Nhận hoặc Từ chối → `FILE_ANSWER`.
3. Bên gửi chia file thành chunk **512 byte** và gửi theo cửa sổ trượt (tối đa 32 chunk chờ xác nhận). Chunk nhỏ vì thư viện ICE nhận mỗi gói UDP vào bộ đệm cố định 1500 byte: chunk 4 KB như thiết kế ban đầu thành gói ~7,3 KB sau mã hoá và bị cắt mất.
4. Bên nhận ghi vào file tạm `.part`, trả `FILE_ACK` (sau mỗi 8 chunk mới, khi thấy lỗ hổng, hoặc sau 50 ms không có chunk mới) cho biết đã nhận tới đâu; chunk thiếu được gửi lại.
5. Đủ chunk thì bên nhận kiểm tra SHA-256. Khớp thì đổi `.part` thành tên thật và trả `verified = true`; chỉ khi đó bên gửi mới báo "Đã gửi xong".

**Lỗi có thể xảy ra và cách xử lý**

| Tình huống | Hệ thống xử lý | TT |
|---|---|---|
| Mất, trùng hoặc đảo thứ tự chunk | Gửi lại theo thời gian chờ tăng dần (0,5 → 4 s), tối đa 10 lần mỗi chunk; bên nhận bỏ chunk trùng | ✅ |
| Mất offer hoặc câu trả lời | Gửi lại offer; quá 60 s không trả lời thì báo `TIMEOUT` | ✅ |
| Mất bản xác nhận cuối cùng | Bên gửi gửi lại chunk cuối để hỏi; bên nhận đã xong thì trả lại xác nhận | ✅ |
| File nhận được bị sai (SHA-256 không khớp) | Bên nhận huỷ với lý do `CHECKSUM_MISMATCH`, bên gửi báo lỗi | ✅ |
| Người nhận từ chối, hoặc một bên bấm Huỷ | Dừng ngay, báo lý do (`REJECTED`, `CANCELLED_BY_USER`) | ✅ |
| File quá 100 MB hoặc offer sai quy ước | Từ chối (`FILE_TOO_LARGE`, `INVALID_OFFER`) | ✅ |
| Tên file độc hại (`../`, ký tự cấm, `CON`…) hoặc ghi ra ngoài thư mục | Bên nhận làm sạch tên (`FileNames`), chỉ ghi trong `~/Downloads`; trùng tên thì thêm ` (1)`, ` (2)`… Không tự mở file | ✅ |
| Đang nhận một file khác từ cùng người đó | Tự trả lời `BUSY`, bên gửi báo bận | ✅ |
| Một bên rời phòng hoặc mất kết nối giữa chừng | Huỷ cả hai phía, xoá file tạm `.part` | ✅ |
| Lỗi đọc / ghi đĩa (đầy ổ, không có quyền) | Huỷ với lý do `IO_ERROR`, báo bên kia | ✅ |
| Người chưa nhập đúng mật khẩu phòng gửi file vào | Bản tin `FILE_*` của người chưa qua bước xác nhận mật khẩu bị bỏ qua; cũng không gửi file được cho người đó | ✅ |
| Tốc độ thấp vì chunk chỉ 512 byte | Chấp nhận ở phạm vi đồ án; hướng cải thiện: `P2pDataChannel` tự chia gói lớn để tăng lại kích thước chunk | ⚠️ |

**Kiểm thử:**
- `ReliableChunkSenderTest` (10 kịch bản): mạng hoàn hảo; mất 20% + nhân đôi 10% + đảo thứ tự; mất ACK cuối; bị từ chối; không trả lời; file rỗng; sai checksum; huỷ giữa chừng.
- `FileTransferOverUnreliablePeerConnectionTest`: 1 MB qua kênh mã hoá thật, mất 15% và nhân đôi 5% gói, khoảng 1,7 s, đúng từng byte.
- `FileSenderTest`, `FileReceiverTest`, `FileNamesTest`, `FileTransferLimitsTest`: đọc/ghi file thật, làm sạch tên, kiểm tra offer.
- `FileTransferManagerTest` (16 test): hai bên nối với nhau trong bộ nhớ; nhận, từ chối, huỷ, `BUSY`, người rời phòng.

---

## 15. Log theo dõi trên terminal

**Mô tả:** mỗi bước chính in một dòng lên terminal, dạng `17:02:56.060 INFO   [P2pChatSession] ...`, để kiểm tra khi test. Mỗi người được ghi kèm 8 ký tự đầu của `peerId`, để phân biệt nhiều client chạy trên cùng một máy.

| Chế độ | Cách bật | In ra |
|---|---|---|
| **Bình thường** | Mặc định | Khởi động/thoát; đăng nhập/đăng ký/đăng xuất; vào phòng (công khai hay có mật khẩu, `roomId` trên server); trao khoá ECDH xong; `ROOM_PROOF` hợp lệ/bị từ chối; người mới vào phòng; xác thực danh tính (fingerprint rút gọn); gửi/nhận tin (id + độ dài); gửi/nhận lịch sử; hết lượt gửi lại; người rời phòng; đổi cài đặt; **server từ chối cho vào phòng kèm lý do** (`WRONG_PASSWORD`, `ROOM_FULL`…); **lỗi khi truyền file** (không hoàn tất được file, payload file hỏng, không gửi được bản tin file). Server: kết quả đăng nhập/đăng ký, lý do từ chối JOIN (kể cả sai mật khẩu và bị giới hạn số lần thử) |
| **Chi tiết** | `JAVA_TOOL_OPTIONS=-Dchat.debug=true` | Thêm: từng lần gửi lại, ACK, tín hiệu đang gõ, gói UDP bị bỏ qua, gói replay bị loại, log của thư viện ICE |
| **Bí mật** (chỉ để test) | `CHAT_P2P_LOG_SECRETS=true` | Thêm: **nội dung tin nhắn**, mật khẩu tài khoản và mật khẩu phòng, token, khoá phiên AES-256 của từng kết nối, public key ECDH. Dòng đầu log có cảnh báo `!!! CHE DO LOG BI MAT DANG BAT !!!` |

**Lỗi có thể xảy ra và cách xử lý**

| Tình huống | Hệ thống xử lý | TT |
|---|---|---|
| Lộ nội dung chat / mật khẩu qua log | Mặc định **che** (mật khẩu, token hiện `***`; tin nhắn chỉ hiện độ dài). Chế độ bí mật phải bật chủ động và có cảnh báo | ✅ |
| Lộ khoá danh tính dài hạn | Private key ECDSA **không bao giờ** được in, kể cả ở chế độ bí mật | ✅ |
| Kẻ xấu gửi gói UDP rác làm ngập terminal | Log gói bị bỏ qua chỉ hiện ở chế độ chi tiết, tối đa 20 dòng mỗi kết nối | ✅ |
| Chạy app không có cửa sổ terminal (jar, double-click) | Log chỉ in ra console, không ghi file nên sẽ mất | ❌ — hướng: ghi file `~/.chat-p2p-java/logs/client.log` có xoay vòng |
| Theo dõi một lần truyền file thành công | Truyền file chỉ ghi log khi **lỗi**; các bước bình thường (gửi offer, nhận, tiến độ, hoàn tất) chỉ hiện trên thẻ file trong giao diện | ⚠️ — hướng: thêm log INFO cho từng bước (fileId rút gọn, tên file, dung lượng, thời gian truyền) |

---

## Kiểm thử

Toàn bộ test tự động chạy bằng `mvn test` từ thư mục gốc trên nhánh `main` (commit `ee4e764`) đều pass:

| Module | Số test | Nội dung chính |
|---|---|---|
| crypto | 31 | ECDH, AES-GCM, chữ ký danh tính gắn ECDH (có kịch bản MITM), khoá phòng có mật khẩu |
| signaling-server | 20 | Chuyển tiếp bản tin, không đọc nội dung, xử lý lỗi, danh sách phòng không lộ thẻ mật khẩu; `JOIN_REJECTED` cho từng lý do (sai mật khẩu, phòng đầy, trùng peer) và giới hạn số lần thử |
| p2p-core | 108 (1 bỏ qua) | Mesh 3 và 8 người, mất gói, chống replay, rò rỉ cổng, kết nối lại, TURN dự phòng; truyền file: gửi lại tin cậy, đọc/ghi file, làm sạch tên, 1 MB qua kênh mã hoá trên mạng giả lập xấu |
| client-javafx | 55 | Gửi lại tin nhắn, đang gõ, Markdown, cài đặt, lịch sử, quy tắc ở sảnh chờ, thông báo khi bị từ chối vào phòng; điều phối truyền file (16 test); **2 test tích hợp nhiều client thật** (người vào sau nhận lịch sử; chỉ người cùng mật khẩu thấy nhau) |

`mvn install` (khác với `mvn test`) đang lỗi biên dịch ở `P2pChatSessionIntegrationTest` vì test này cần lớp của `signaling-server`, mà jar Spring Boot đã đóng gói lại không còn dùng làm thư viện được. Tạm thời build bằng `mvn -Dmaven.test.skip=true install`; cần sửa cấu hình build (thêm `classifier` cho jar Spring Boot).

Ngoài test tự động, các thay đổi giao diện (bong bóng chat Markdown, 3 theme, sảnh chờ, 4 hộp thoại kể cả trạng thái sau khi tích mật khẩu và khi hiện lỗi) được kiểm tra bằng cách dựng màn hình với dữ liệu giả và chụp ảnh.

Một lần chạy toàn bộ test, `PeerConnectionBurstThroughputTest` fail đúng lúc máy đang tải nặng; chạy lại riêng 3 lần và chạy lại cả bộ đều pass. Nhiều khả năng test nhạy với thời gian chờ 5 s, cần theo dõi thêm.

---

## Hạn chế chính và hướng phát triển

1. **Signaling chưa mã hoá đường truyền (`ws://`)**: token đăng nhập có thể bị nghe lén (nội dung chat thì không). Kế hoạch chuyển sang `wss://` ở Tài liệu kỹ thuật, mục J.1.
2. *(Đã giải quyết)* Nhập sai mật khẩu phòng, phòng đầy, token hết hạn giờ đều được server báo ngay bằng `JOIN_REJECTED` kèm lý do (Tài liệu kỹ thuật, mục J.2).
3. **Lịch sử chat không xác minh được người viết từng tin**: hướng giải quyết là ký từng tin.
4. **Chưa chủ động ngắt kết nối** người sai mật khẩu hoặc có chữ ký danh tính sai.
5. **Tên phòng phân biệt hoa thường**: nên chuẩn hoá về chữ thường.
6. **Log chưa ghi ra file**: chạy không có terminal thì mất log.
7. **Truyền file còn chậm** (chunk 512 byte); gửi cho nhóm là gửi riêng từng người nên tốn băng thông bên gửi gấp N lần; chưa gửi tiếp được sau khi mất kết nối; chưa có log từng bước khi truyền thành công.
8. **`mvn install` lỗi ở một test tích hợp** (xem phần Kiểm thử); `mvn test` vẫn chạy đúng.
9. **Mật khẩu TURN từng nằm trong lịch sử git** (repo public): đã chuyển ra file cấu hình cục bộ nhưng cần đổi mật khẩu TURN trên server.
10. **Chưa có** gọi thoại, gọi video, chia sẻ màn hình.

Chi tiết rủi ro kỹ thuật: xem `docs/Tai-lieu-ky-thuat.md`, Phần J.
