# Kết quả đo xuyên NAT giữa hai máy thật

Tài liệu ghi lại các lần thử kết nối P2P giữa **hai máy thật ở các loại mạng khác nhau**, để kiểm chứng bảng "Khi nào đi thẳng, khi nào qua TURN" ở mục I.3 của [Tai-lieu-ky-thuat.md](Tai-lieu-ky-thuat.md). Mỗi trường hợp ghi: mạng của hai bên, loại NAT đo được, loại đường mà ICE chọn, thời gian thiết lập và chất lượng truyền.

**Cách đọc loại đường** (dòng `ICE selected pair` trong `~/.chat-p2p-java/logs/client-0.log`):
- `typ host`: đi thẳng qua IP nội bộ.
- `typ srflx` / `prflx`: đi thẳng qua địa chỉ public, tức xuyên NAT thành công.
- `typ relay`: đi vòng qua TURN.

**Cách đo loại NAT:** từ cùng một cổng UDP, gửi STUN Binding Request tới Google STUN, Cloudflare STUN và TURN của dự án, rồi so địa chỉ public mà mỗi server nhìn thấy.

---

## Trường hợp 1: cả hai máy cùng Wi-Fi trường (PTIT_WIFI)

### Mạng

| | Máy 1 (khoi) | Máy 2 (Huy) |
|---|---|---|
| Mạng | PTIT_WIFI, 802.11ac, mạng mở | PTIT_WIFI |
| IP nội bộ | `172.18.19.38/18` | `172.18.19.33/18` |
| Card mạng ảo | WSL `172.26.112.1` | VMware `192.168.229.1`, `192.168.78.1` |

### Kiểm tra trước khi vào phòng (25/11/2026)

| Kiểm tra | Kết quả | Ý nghĩa |
|---|---|---|
| Ping máy 1 → máy 2 | 4/4 gói, 20–110 ms, trung bình 52 ms, TTL = 128 | Wi-Fi **không cách ly máy khách**: hai máy gửi gói thẳng cho nhau, không qua router |
| UDP ra Internet | Không bị chặn | — |
| Tới TURN `165.101.47.83:3478` | Có trả lời, 81–208 ms | Đường dự phòng dùng được |
| Loại NAT, máy 1 | Cổng public giữ nguyên với mọi server; **IP public đổi theo điểm đến**: `103.238.68.82` (Google, TURN) và `113.160.14.17` (Cloudflare) | Trường có hai đường ra Internet, chia theo điểm đến |
| Hairpin NAT | Không hỗ trợ: gửi tới IP public của chính trường thì không quay lại được bên trong | Hai máy cùng trường không đi được qua địa chỉ public, chỉ đi được qua IP nội bộ |

### Các lần thử

| Lần | Phòng | Đường được chọn | Thời gian ICE | Từ lúc vào phòng tới lúc thấy nhau | Tin nhắn | File |
|---|---|---|---|---|---|---|
| 1 | `lang-le-cho-1202`, máy 1 trả lời | **Đi thẳng**, `typ host`, `172.18.19.38:10000` ↔ `172.18.19.33:10000` | 335 ms | ~11 s, trong đó ~9 s là ice4j khởi động lần đầu trong JVM | 1 gửi, 1 nhận, đều có ACK | — |
| 2 | `lang-le-cho-1202`, máy 1 vào lại, máy 1 gửi OFFER | **Đi thẳng**, `typ host`, `172.18.19.38:10000` ↔ `172.18.19.33:10001` | 538 ms | 2,05 s, trong đó ~1,1 s là ice4j khởi động lần đầu (vừa mở lại app) | Nhận 2 tin lịch sử do Huy chia sẻ | — |
| 3 | `lang-le-nui-5559`, máy 1 trả lời, app đã khởi động sẵn | **Đi thẳng**, `typ host`, `172.18.19.38:10001` ↔ `172.18.19.33:10002` | 582 ms | **1,53 s** từ lúc Huy vào phòng; 0,9 s từ lúc nhận OFFER | — | Xem bảng dưới |

### Truyền file (lần 3)

| Chiều | Dung lượng | Số mảnh 4 KB | Thời gian | Tốc độ | Mảnh phải gửi lại |
|---|---|---|---|---|---|
| Máy 1 → máy 2, ảnh | 0,06 MB | 16 | 0,31 s | 0,20 MB/s | 0 |
| Máy 1 → máy 2, video | 14,37 MB | 3679 | 25,77 s | **0,56 MB/s** | 7, tức 0,19% |
| Máy 2 → máy 1, video | 14,37 MB | 3679 | 21,83 s | **0,66 MB/s** | không đo ở bên nhận |

Số liệu lấy từ dòng `Gui file xong` / `Nhan file xong` trong log máy 1. Bên gửi tính từ lúc bên kia bấm "Nhận" tới lúc bên kia báo đã kiểm SHA-256 xong; bên nhận tính từ lúc bấm "Nhận" tới lúc kiểm SHA-256 xong. Cả ba file đều qua kiểm tra SHA-256.

**Vì sao tốc độ chỉ khoảng 0,6 MB/s dù hai máy cùng mạng Wi-Fi:** bên gửi chỉ được có tối đa **8 mảnh × 4 KB = 32 KB** đang trên đường chờ xác nhận, nên mỗi vòng khứ hồi chỉ đi được 32 KB. Độ trễ khứ hồi trên Wi-Fi trường đo bằng ping là ~52 ms, nên tốc độ tối đa theo lý thuyết là 32 KB / 0,052 s ≈ **0,6 MB/s**, khớp với số đo. Tốc độ bị giới hạn bởi **cửa sổ gửi cố định**, không phải bởi băng thông Wi-Fi. Hướng cải thiện: cửa sổ tự giãn theo mạng, giống TCP.

**Ghi chú lần 1:**
- Đo ở máy 1, máy này là bên trả lời ANSWER. Thời gian ICE tính từ dòng `ICE create` tới `ICE state changed: Running -> Completed`.
- Khởi động lần đầu: nhận OFFER lúc 15:39:28 nhưng tới 15:39:37 mới tạo ICE, vì ice4j nạp cấu hình và thử `AwsCandidateHarvester` rồi bỏ. Phần này chỉ xảy ra một lần mỗi lần mở app.
- Sau ICE: trao khoá ECDH xong sau 45 ms; chữ ký danh tính xác thực xong sau 0,4 s.
- Cả hai bên đều lấy được cổng relay trên TURN, nhưng ICE ưu tiên đường nội bộ nên không dùng tới.
- Các cặp qua địa chỉ ảo (WSL, VMware) và qua IP public của trường đều thất bại như dự đoán, không ảnh hưởng kết quả.

**Ghi chú lần 2 và 3:**
- Lần 2 máy 1 là bên gửi OFFER: thời gian ICE gồm cả thu thập địa chỉ (~150 ms) và một vòng OFFER/ANSWER qua signaling server (~190 ms).
- Lần 3 app đã khởi động sẵn nên không còn phần chờ ice4j: OFFER tới lúc 15:48:36.265, 21 ms sau đã tạo ICE.
- Phần khởi động ice4j lần đầu dao động mạnh: ~9 s ở lần 1, ~1,1 s ở lần 2. Cần đo thêm nếu muốn nêu con số này.
- **Cần theo dõi:** ở lần 2 và 3, danh sách địa chỉ của máy 1 **không có candidate `relay`**, trong khi lần 1 có. Trường hợp này không ảnh hưởng vì đã đi thẳng, nhưng ở các trường hợp **bắt buộc qua TURN** thì thiếu relay sẽ làm kết nối thất bại. Có thể liên quan tới việc ice4j bỏ bộ thu thập TURN khi gặp lỗi gửi qua card ảo WSL `172.26.112.1` (log: `harvest did not start, removed: TurnCandidateHarvest`). Kiểm tra lại khi thử trường hợp 4G ↔ 4G.

### Kết luận trường hợp 1

- **3/3 lần đi thẳng qua IP nội bộ** (`typ host`), không cần TURN. Wi-Fi trường không cách ly máy khách nên hai máy cùng mạng gửi gói thẳng cho nhau, đúng dự đoán ở mục I.3: *"Cùng LAN / Wi-Fi: đi thẳng qua IP nội bộ"*. Dòng này của bảng nay đã được **kiểm chứng trên hai máy thật**.
- **Thời gian ICE 335–582 ms.** Khi app đã khởi động sẵn, từ lúc người thứ hai vào phòng tới lúc hai bên thấy nhau và đã trao khoá ECDH mất khoảng **1,5 s**. Lần đầu mở app cộng thêm 1–9 s cho ice4j khởi động.
- **Truyền file 14,37 MB: 0,56–0,66 MB/s**, gửi lại 0,19% số mảnh, cả hai chiều đều qua kiểm tra SHA-256. Tốc độ bị giới hạn bởi cửa sổ gửi cố định 32 KB mỗi vòng khứ hồi, không phải bởi mạng.
- Mọi dữ liệu đều đi trên kênh đã mã hoá AES-256-GCM; danh tính hai bên được xác thực bằng chữ ký trong vòng 0,1–0,4 s sau khi trao khoá.

---

## Trường hợp 2: máy 1 ở Wi-Fi trường, máy 2 dùng 4G Viettel

### Mạng

| | Máy 1 (khoi) | Máy 2 (Huy) |
|---|---|---|
| Mạng | PTIT_WIFI | 4G Viettel, phát qua điểm phát sóng iPhone |
| IP nội bộ | `172.18.19.38` | `172.20.10.2` (dải của điểm phát sóng iPhone) |
| IP public | `103.238.68.82`, cổng giữ nguyên theo cổng nội bộ | `116.98.218.14` |
| Loại NAT | Cổng ổn định, nhưng có hai IP ra Internet (trường hợp 1) | **NAT đối xứng**: từ **cùng một cổng nội bộ** 10003, hai server nhìn thấy **hai cổng public khác nhau**, 17533 và 14935. Cổng public phụ thuộc điểm đến |

Loại NAT của máy 2 đọc trực tiếp từ hai candidate `srflx` mà máy 2 gửi sang: cùng `raddr 172.20.10.2 rport 10003` nhưng cổng public khác nhau. Đây là NAT cấp nhà mạng điển hình của 4G, giống kết quả đo ngày 20/11.

### Các lần thử

| Lần | Phòng | Đường được chọn | Thời gian ICE | Từ lúc máy 2 vào phòng tới lúc thấy nhau | Relay của máy 1 |
|---|---|---|---|---|---|
| 1 | `lang-le-nui-5559` | **Qua TURN**: `172.18.19.38:10002` (host) → `165.101.47.83:57918` (relay của máy 2) | 627 ms | 0,99 s | **Không có** |
| 2 | `bi-mat-nui-5686` | **Qua TURN**: `172.18.19.38:10003` (host) → `165.101.47.83:53953` (relay của máy 2) | 696 ms | 1,04 s | Có, `165.101.47.83:52839` |
| 3 | `linh-hoat-cho-1913`, vừa mở lại app | **Qua TURN**: `165.101.47.83:59138` (relay của **máy 1**) → `116.98.218.14:7242` (`prflx` của máy 2) | 693 ms | 3,0 s, gồm ~0,8 s ice4j khởi động lần đầu và ~1,3 s máy 2 thu thập địa chỉ trước khi gửi OFFER; 1,6 s từ lúc nhận OFFER | Có, `165.101.47.83:59138` |

Log ghi `Duong truyen toi Huy: qua TURN relay`.

**Đường đi của dữ liệu:** chỉ một chặng TURN, nhưng ICE có thể dùng relay của **bên nào cũng được**:
- Lần 1 và 2: máy 1 gửi thẳng từ Wi-Fi trường tới **cổng relay của máy 2** trên TURN, TURN chuyển tiếp qua 4G tới máy 2.
- Lần 3: máy 1 gửi qua **cổng relay của chính mình**; máy 2 gửi thẳng từ 4G tới cổng relay đó. Địa chỉ `116.98.218.14:7242` là cổng mà NAT 4G mở riêng cho đường tới TURN, nên ICE chỉ biết được nó khi gói của máy 2 tới nơi (`prflx`, peer-reflexive), không có trong danh sách máy 2 gửi trước.

**Vì sao không đi thẳng được:** NAT 4G của máy 2 cấp cổng public khác nhau cho mỗi điểm đến. Cổng mà máy 2 quảng bá (thu được khi hỏi STUN server) không phải cổng mà NAT sẽ mở cho gói từ máy 1, nên mọi cặp đi thẳng tới `116.98.218.14` đều hết thời gian chờ. ICE tự chuyển sang cặp qua relay, người dùng không phải làm gì.

### Truyền file

| Lần | Chiều | Dung lượng | Thời gian | Tốc độ | Mảnh phải gửi lại |
|---|---|---|---|---|---|
| 2 | Máy 1 → máy 2 | 14,37 MB, 3679 mảnh | 91,21 s | **0,16 MB/s** | **3987**, tức 108% |
| 2 | Máy 2 → máy 1 | 14,37 MB, 3679 mảnh | 50,05 s | **0,29 MB/s** | không đo ở bên nhận |
| 3 | Máy 1 → máy 2 | 0,97 MB, 249 mảnh | 7,67 s | **0,13 MB/s** | **240**, tức 96% |
| 3 | Máy 2 → máy 1 | 0,39 MB, 99 mảnh | 1,45 s | **0,27 MB/s** | không đo ở bên nhận |

Cả bốn file đều qua kiểm tra SHA-256.

**Phân tích:**
- Tốc độ tiếp tục khớp giới hạn **32 KB mỗi vòng khứ hồi**: 0,16 MB/s ứng với vòng khứ hồi khoảng 200 ms, 0,29 MB/s ứng với khoảng 110 ms. Đi qua TURN cộng thêm một chặng, và 4G có độ trễ cao hơn Wi-Fi, nên vòng khứ hồi dài hơn trường hợp 1.
- Chiều máy 1 → máy 2 phải **gửi lại gần như mọi mảnh**, 108% ở lần 2 và 96% ở lần 3, dù đi qua relay của bên nào. Con số gần 100% gợi ý mỗi mảnh bị gửi **hai lần**, nhiều hơn mức mất gói thường gặp. Các giả thuyết, chưa kiểm chứng:
  - **Thời gian chờ cố định 500 ms ngắn hơn độ trễ thực tế**: khi bên gửi đẩy cả cửa sổ vào đường 4G chiều xuống, gói xếp hàng trong mạng nhà mạng, độ trễ tăng vọt quá 500 ms, nên bên gửi tưởng mất và gửi lại mảnh dù mảnh cũ vẫn đang trên đường.
  - Gói đến **sai thứ tự** khi qua TURN, khiến cơ chế "3 mảnh sau đã tới thì coi mảnh trước là mất" gửi lại nhầm.
  - Mất gói thật trên chặng 4G chiều xuống.
  
  Muốn phân biệt cần log thêm độ trễ từng mảnh. Hướng cải thiện nếu giả thuyết đầu đúng: thời gian chờ tự điều chỉnh theo độ trễ đo được, giống TCP. Dù gửi lại nhiều, file vẫn tới đủ và đúng.
- Hai chiều chênh nhau gần gấp đôi: đường lên và đường xuống của 4G không đối xứng.

**Ghi chú:**
- Máy 1 có lúc không lấy được relay, như lần 1, giống hiện tượng ở trường hợp 1. Vì máy 2 luôn có relay, và chỉ cần **một bên** có relay, nên kết nối vẫn thành công.
- Ở lần 2, kiểm tra cặp **relay ↔ relay**, tức `165.101.47.83:52839` → `165.101.47.83:53953`, **thành công**. Lần đo ngày 20/11 cặp này từng thất bại vì coturn chặn chuyển tiếp giữa hai cổng của chính nó. Có thể cấu hình coturn đã thay đổi; cần kiểm tra lại trước khi ghi kết luận.

### Kết luận trường hợp 2

- **3/3 lần phải đi qua TURN**, và **3/3 lần kết nối thành công tự động**. NAT 4G Viettel là NAT đối xứng, nên không đi thẳng được; ICE tự chọn đường qua relay, người dùng không phải cấu hình gì. Dòng *"4G ↔ Wi-Fi: đi thẳng nếu router bên kia dễ tính, nếu không thì qua TURN"* ở mục I.3 nay đã có số đo: với Wi-Fi trường, kết quả là **qua TURN**.
- **Thời gian ICE 627–696 ms**, chậm hơn trường hợp 1 khoảng 100–300 ms vì phải thử thêm các cặp và đi qua TURN. Khi app đã mở sẵn, từ lúc máy 2 vào phòng tới lúc thấy nhau khoảng **1 s**.
- ICE dùng được **relay của bất kỳ bên nào**: lần 1–2 dùng relay của máy 2, lần 3 dùng relay của máy 1. Chỉ cần một bên lấy được relay là đủ; ở lần 1 máy 1 không có relay mà vẫn kết nối được.
- **Truyền file qua TURN: 0,13–0,29 MB/s**, chậm hơn trường hợp 1 khoảng 2–5 lần vì vòng khứ hồi dài hơn. Chiều Wi-Fi → 4G phải gửi lại gần như mọi mảnh; chiều ngược lại nhanh gấp đôi. File luôn tới đủ và đúng SHA-256.
- Dữ liệu qua TURN vẫn là dữ liệu đã mã hoá AES-256-GCM. TURN chỉ chuyển tiếp gói, không giữ khoá.

---

## Trường hợp 3: cả hai máy dùng 4G, khác nhà mạng

### Mạng

| | Máy 1 (khoi) | Máy 2 (Huy) |
|---|---|---|
| Mạng | 4G **Vinaphone**, phát qua điểm phát sóng iPhone | 4G **Viettel**, phát qua điểm phát sóng iPhone |
| IP nội bộ | `172.20.10.2` | `172.20.10.2`, trùng dải vì cùng là điểm phát sóng iPhone, nhưng là hai mạng riêng |
| IP public | `113.185.53.174` và `113.185.44.11` | `116.98.218.14` |
| Loại NAT | **NAT đối xứng, đổi cả IP lẫn cổng**: từ cùng cổng nội bộ 10000, hai server thấy `113.185.53.174:15903` và `113.185.44.11:3359` | **NAT đối xứng, đổi cổng**: từ cùng cổng nội bộ, hai server thấy cổng 23704 và 23623 |

NAT của Vinaphone giống hệt kết quả đo ngày 20/11 trên cùng loại mạng: mỗi điểm đến thấy một địa chỉ public khác cả IP lẫn cổng.

**Ghi chú IPv6:** cả hai máy đều có **IPv6 công khai**: máy 1 qua Vinaphone `2001:ee0:22c:e3c8:…`, máy 2 qua Viettel `2401:d800:cbd:e244:…` (kèm một địa chỉ tạm thời). Kiểm tra thêm trên máy 1: ping Google qua IPv6 thông (65–70 ms), và địa chỉ một server bên ngoài nhìn thấy **trùng với địa chỉ trên máy**, tức là **không có NAT**, khác hẳn IPv4 (`172.20.10.2` trên máy, bên ngoài thấy `113.185.x.x` thay đổi theo điểm đến). Ping thẳng từ máy 1 sang máy 2 qua IPv6 không tới, nhưng điều đó chưa kết luận được gì vì Windows mặc định chặn ping từ ngoài vào; ICE thì hai bên cùng gửi một lúc nên tường lửa mỗi bên cho gói trả lời đi vào.

Lúc đo, app chỉ quảng bá địa chỉ IPv4 nên không thử đường IPv6. Một ghi nhận sai cần sửa: địa chỉ `2001:0:…` trong `ipconfig` của máy 2 là **Teredo** (đường hầm cũ của Windows), không phải IPv6 thật; IPv6 thật của máy 2 chỉ có khi dùng 4G, không có khi dùng Wi-Fi trường. **Từ tối 25/11 app quảng bá cả IPv6 công khai** (loại link-local, ULA, Teredo, 6to4) → cần đo lại trường hợp này (xem trường hợp 3b bên dưới).

### Các lần thử

| Lần | Phòng | Đường được chọn | Thời gian ICE | Từ lúc máy 2 vào phòng tới lúc thấy nhau |
|---|---|---|---|---|
| 1 | `sang-suot-ho-5247`, vừa mở lại app | **Qua TURN**: `165.101.47.83:65498` (relay của máy 1) → `116.98.218.14:20517` (`prflx` của máy 2) | 918 ms | 2,1 s, gồm ~0,8 s ice4j khởi động lần đầu; 1,9 s từ lúc nhận OFFER |

Trường hợp này chỉ đo **một lần**.

**Đường đi:** máy 2 gửi thẳng từ 4G Viettel tới cổng relay của máy 1 trên TURN; máy 1 gửi ra qua chính cổng relay đó. Một chặng TURN, không cần relay ↔ relay. Mọi cặp đi thẳng giữa hai địa chỉ 4G đều hết thời gian chờ, đúng như dự đoán với hai NAT đối xứng.

### Truyền file

| Lần | Chiều | Dung lượng | Thời gian | Tốc độ | Mảnh phải gửi lại |
|---|---|---|---|---|---|
| 1 | Máy 1 → máy 2 | 0,39 MB, 99 mảnh | 4,44 s | **0,09 MB/s** | **84**, tức 85% |
| 1 | Máy 2 → máy 1 | 0,97 MB, 249 mảnh | 3,21 s | **0,30 MB/s** | không đo ở bên nhận |

Cả hai file đều qua kiểm tra SHA-256.

**Nhận xét:** hiện tượng phải gửi lại rất nhiều tiếp tục xuất hiện ở **chiều gửi tới máy 2**. Qua cả trường hợp 2 lẫn trường hợp 3, chiều tới máy 2 luôn gửi lại 85–108%, còn chiều từ máy 2 về luôn nhanh hơn 2–3 lần. Điểm chung duy nhất là **chặng 4G Viettel chiều xuống của máy 2**, nên đây là nghi phạm chính. Lưu ý số mảnh gửi lại chỉ đo được ở bên gửi, tức máy 1; chưa biết chiều máy 2 → máy 1 có gửi lại nhiều hay không.

### Kết luận trường hợp 3

- **Kết nối thành công qua TURN** giữa hai mạng 4G khác nhà mạng, cả hai đều sau NAT đối xứng. Dòng *"4G ↔ 4G: bắt buộc qua TURN"* ở mục I.3, trước đây chỉ là **suy ra** từ loại NAT, nay đã được **kiểm chứng** trên hai máy thật. Một lần đo nên chưa có tỉ lệ thành công.
- Không cần relay ↔ relay: chỉ cần một bên có cổng relay, bên kia gửi thẳng tới cổng đó.
- **Thời gian ICE 918 ms**, chậm nhất trong ba trường hợp vì phải chờ nhiều cặp đi thẳng thất bại.
- **Truyền file 0,09–0,30 MB/s.** Chiều gửi tới máy 2 tiếp tục phải gửi lại phần lớn số mảnh.

---

## Trường hợp 3b: 4G Vinaphone ↔ 4G Viettel, có IPv6 *(dự kiến đo tối 25/11/2026)*

**Mục đích:** cùng hai mạng 4G như trường hợp 3, nhưng app đã quảng bá IPv6 công khai. Kiểm tra xem ICE có chọn được cặp **IPv6 ↔ IPv6 đi thẳng** thay cho đường TURN qua IPv4 hay không.

**Dự đoán:** cặp IPv6 có độ ưu tiên cao hơn, và không có NAT, nên nếu tường lửa của nhà mạng cho gói trả lời đi vào thì ICE sẽ chọn `typ host` với địa chỉ `2001:ee0:…` ↔ `2401:d800:…`. Nếu tường lửa nhà mạng chặn hẳn chiều vào, ICE quay lại TURN như trường hợp 3.

**Điều kiện:** cả hai máy chạy bản client từ tối 25/11 trở đi.

| Lần | Đường được chọn | Thời gian ICE | Truyền file |
|---|---|---|---|
| 1 | | | |
| 2 | | | |
| 3 | | | |

**Kết luận:** *(điền sau khi đo)*

---

## Trường hợp 4: máy 1 ở Wi-Fi quán cà phê, máy 2 ở Wi-Fi nhà *(dự kiến đo tối 25/11/2026)*

### Mục đích

Đây là trường hợp **xuyên NAT qua địa chỉ public**: hai máy ở hai mạng Wi-Fi khác nhau, mỗi mạng sau một router dân dụng riêng. Ba trường hợp trên chưa có lần nào đi thẳng qua địa chỉ public: trường hợp 1 đi thẳng qua IP nội bộ, trường hợp 2 và 3 phải qua TURN.

### Dự đoán

- Router dân dụng thường giữ nguyên cổng public cho mọi điểm đến (NAT "dễ"). Nếu **cả hai** router như vậy, ICE sẽ **đi thẳng**, log ghi `typ srflx` hoặc `typ prflx`.
- Nếu một bên là NAT đối xứng, như một số router hoặc mạng quán dùng NAT nhiều lớp, thì sẽ qua TURN như trường hợp 2.
- Nếu Wi-Fi quán **chặn UDP**, app sẽ **không kết nối được**, vì TURN hiện chỉ chạy UDP. Kết quả đó cũng kiểm chứng luôn dòng "mạng chặn UDP" ở mục I.3.

### Cách đo

1. Máy 1 ở quán: kiểm tra mạng (tên Wi-Fi, IP nội bộ, cổng ra) và chạy script đo NAT (STUN tới Google, Cloudflare, TURN của dự án).
2. Máy 2 ở nhà: chạy cùng script đo NAT.
3. Vào phòng, nhắn tin, gửi file hai chiều, lặp lại 3 lần với phòng mới. Đọc dòng `ICE selected pair` và dòng `Gui file xong` / `Nhan file xong` trong log máy 1.

### Mạng

| | Máy 1 (khoi) | Máy 2 (Huy) |
|---|---|---|
| Mạng | Wi-Fi quán cà phê | Wi-Fi nhà |
| IP nội bộ | | |
| IP public | | |
| Loại NAT | | |
| UDP ra ngoài | | |

### Các lần thử

| Lần | Phòng | Đường được chọn | Thời gian ICE | Từ lúc vào phòng tới lúc thấy nhau |
|---|---|---|---|---|
| 1 | | | | |
| 2 | | | | |
| 3 | | | | |

### Truyền file

| Lần | Chiều | Dung lượng | Thời gian | Tốc độ | Mảnh phải gửi lại |
|---|---|---|---|---|---|
| | Máy 1 → máy 2 | | | | |
| | Máy 2 → máy 1 | | | | |

### Kết luận trường hợp 4

*(điền sau khi đo)*

---

## Tổng kết

| Trường hợp | Mạng hai bên | Số lần | Đường được chọn | Kết nối thành công | Thời gian ICE | Truyền file |
|---|---|---|---|---|---|---|
| 1 | Cùng Wi-Fi trường | 3 | **Đi thẳng**, IP nội bộ | 3/3 | 335–582 ms | 0,56–0,66 MB/s |
| 2 | Wi-Fi trường ↔ 4G Viettel | 3 | **Qua TURN** | 3/3 | 627–696 ms | 0,13–0,29 MB/s |
| 3 | 4G Vinaphone ↔ 4G Viettel | 1 | **Qua TURN** | 1/1 | 918 ms | 0,09–0,30 MB/s |

**Những gì đã chứng minh được:**
1. **App tự tìm đường trong mọi trường hợp đã thử, 7/7 lần kết nối thành công.** Cùng mạng thì đi thẳng; một hoặc cả hai bên sau NAT đối xứng của 4G thì tự chuyển sang TURN, người dùng không phải cấu hình gì.
2. **Thời gian kết nối dưới 1 giây** cho phần ICE. Khi app đã mở sẵn, từ lúc người thứ hai vào phòng tới lúc hai bên thấy nhau và đã trao khoá xong khoảng 1–1,5 s.
3. **Dữ liệu luôn tới đủ và đúng:** 9/9 file qua kiểm tra SHA-256, kể cả khi phải gửi lại phần lớn số mảnh.
4. **Đi qua TURN vẫn mã hoá đầu-cuối:** TURN chỉ chuyển tiếp gói đã mã hoá AES-256-GCM.

**Những giới hạn đo được, kèm hướng cải thiện:**
1. **Tốc độ truyền file bị chặn bởi cửa sổ gửi cố định 32 KB mỗi vòng khứ hồi:** khoảng 0,6 MB/s trong cùng Wi-Fi, 0,1–0,3 MB/s qua TURN. Số đo khớp với công thức 32 KB / thời gian khứ hồi. Hướng cải thiện: cửa sổ tự giãn theo mạng, giống TCP.
2. **Chiều gửi tới máy dùng 4G Viettel phải gửi lại 85–108% số mảnh**, lặp lại ở cả 3 lần có đo. Nghi vấn là thời gian chờ cố định 500 ms ngắn hơn độ trễ thực tế khi đường 4G chiều xuống bị nghẽn. Chưa kiểm chứng; hướng cải thiện: thời gian chờ tự điều chỉnh theo độ trễ đo được.
3. **Máy 1 lúc có lúc không lấy được cổng relay** (có ở 4/7 lần). Chưa gây lỗi vì chỉ cần một bên có relay, nhưng nếu cả hai bên cùng thiếu ở trường hợp 4G ↔ 4G thì sẽ không kết nối được. Cần điều tra thêm, nghi liên quan tới card mạng ảo WSL.
4. **IPv6 chưa được dùng:** 4G Vinaphone cấp IPv6 công khai, nhưng app chỉ quảng bá IPv4.

**Trường hợp chưa thử:** Wi-Fi nhà ↔ Wi-Fi nhà khác, là trường hợp có khả năng đi thẳng xuyên NAT qua địa chỉ public (`srflx` / `prflx`); và mạng chặn UDP.
