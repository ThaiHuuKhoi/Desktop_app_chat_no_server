# Cơ chế triển khai server

Tài liệu này trả lời câu hỏi: **đưa signaling server, cơ sở dữ liệu và TURN lên một VPS thật ngoài Internet thế nào để người dùng kết nối được an toàn, server tự chạy lại khi có sự cố, và mỗi lần cập nhật không làm hỏng hệ thống?**

Các tài liệu khác đã bàn **ứng dụng** tự bảo vệ ra sao. Tài liệu này bàn **môi trường chạy** ứng dụng. Cấu hình được mô tả theo đúng VPS đang chạy `chat.zingme.fun`.

Cách trình bày giống các tài liệu cơ chế khác: đi từ cách ngây thơ, tìm lỗ hổng, phân tích cần gì, rồi sinh ra bước tiếp theo. Ở mỗi chỗ phải chọn có so sánh các phương án.

Nhân vật:
- **Khôi** là người vận hành VPS.
- **An** là người dùng.
- **Minh** là người trên cùng mạng Wi-Fi với An, hoặc bất kỳ ai trên Internet quét thấy VPS.

---

## Bước 0 — Chạy thẳng jar trên VPS, mở cổng ra Internet

**Làm gì:** Khôi chép file jar lên VPS và chạy `java -jar` ở cổng 8080. Cơ sở dữ liệu MySQL cài mặc định. Mở tường lửa cho cổng 8080 và cổng MySQL. Client kết nối bằng `ws://IP:8080` và `http://IP:8080`.

**Đáp ứng yêu cầu:** chạy được ngay.

**→ Lỗ hổng:** mọi thứ đi trên đường **không mã hoá**, và nhiều thứ không nên mở lại **mở ra Internet**.

**Phân tích vấn đề:**
- **Nghe lén đăng nhập:**
  - Mật khẩu khi đăng nhập và token trong mỗi bản tin `JOIN` đi dạng chữ rõ.
  - Minh ngồi cùng quán cà phê chép được cả hai.
  - Tin nhắn thì vẫn an toàn vì đã mã hoá đầu cuối, nhưng tài khoản thì không (xem [Co-che-dang-nhap.md](Co-che-dang-nhap.md), Bước 4).
- **Sửa đổi trên đường:** Minh đứng giữa còn có thể sửa danh sách địa chỉ ICE, hoặc tráo bản tin signaling.
- **Cơ sở dữ liệu ra Internet:** cổng MySQL mở công khai thì ai cũng thử đăng nhập được. Nếu còn mật khẩu mặc định thì lộ toàn bộ bảng tài khoản.
- **Server không tự chạy lại:** jar chạy trong một phiên SSH. Đóng SSH hoặc VPS khởi động lại là server tắt.

**Để giải quyết thì cần gì:**
- **R1:** mọi kết nối từ client tới server đi qua **TLS**, với chứng chỉ hợp lệ và **tự gia hạn**.
- **R2:** chỉ thành phần làm TLS được mở ra Internet. Server ứng dụng và cơ sở dữ liệu **chỉ nghe ở địa chỉ nội bộ**.
- **R3:** không còn mật khẩu mặc định ở bất kỳ đâu.
- **R4:** server **tự khởi động** cùng máy và **tự chạy lại** khi bị tắt bất ngờ.

**Xét các phương án làm TLS:**
- **Bật TLS ngay trong Spring Boot:** phải tự xin chứng chỉ, chuyển sang keystore, và nhớ gia hạn mỗi 90 ngày. Quên gia hạn là toàn bộ client mất kết nối.
- **Nginx kèm certbot:** phổ biến, nhưng có hai phần mềm phải cấu hình và một tác vụ gia hạn chạy định kỳ phải theo dõi.
- **Caddy làm reverse proxy:** tự xin chứng chỉ Let's Encrypt, tự gia hạn, tự chuyển hướng HTTP sang HTTPS. Cấu hình chỉ vài dòng. Đây là hướng được chọn.

---

## Bước 1 — Caddy giữ TLS, server chỉ nghe nội bộ

**Làm gì:**
- Trước cửa VPS đặt một người gác là Caddy. Người gác có chứng chỉ của `chat.zingme.fun` và tự đi gia hạn khi sắp hết hạn.
- Mọi kết nối từ ngoài vào cổng 443 được người gác mở lớp mã hoá, rồi chuyển vào server bên trong qua địa chỉ `127.0.0.1:8080`.
- Server chỉ nghe ở `127.0.0.1`, nên từ Internet gọi thẳng cổng 8080 sẽ không thấy gì.
- Client mặc định dùng `wss://chat.zingme.fun` cho signaling và `https://` cho đăng nhập và danh sách phòng.

**Về kỹ thuật:**
- Cấu hình Caddy trên VPS:

  ```
  chat.zingme.fun {
      reverse_proxy 127.0.0.1:8080
  }
  ```

- Caddy tự xử lý WebSocket upgrade.
- Server Spring Boot đọc `server.address` từ biến môi trường `CHAT_P2P_SERVER_ADDRESS`. Trên VPS biến này là `127.0.0.1`; mặc định khi chạy thử trên máy là `0.0.0.0`.
- Kiểm tra trên VPS bằng `ss -ltnp`: `*:443` (Caddy) và `127.0.0.1:8080` (Java).

**Trong code:**
- Địa chỉ nghe lấy từ biến môi trường: [application.yml:1-4](../signaling-server/src/main/resources/application.yml#L1-L4).
- Server mặc định của client là `wss://chat.zingme.fun`: [HomeController.java:54](../client-javafx/src/main/java/com/datn/chatp2p/client/view/HomeController.java#L54).
- Client tự đổi `wss://` sang `https://` khi gọi REST: `AuthClient.httpBase` (xem [Co-che-dang-nhap.md](Co-che-dang-nhap.md), Bước 4).

**Đáp ứng yêu cầu:**
- **R1:** TLS với chứng chỉ Let's Encrypt, tự gia hạn.
- **R2** cho server ứng dụng: chỉ nghe ở `127.0.0.1`.

**→ Lỗ hổng:** đứng sau người gác, server **thấy mọi yêu cầu đều đến từ `127.0.0.1`**.

**Phân tích vấn đề:**
- Các giới hạn chống quá tải tính theo địa chỉ IP: 10 lần đăng nhập mỗi phút, 5 lần sai mật khẩu phòng, 32 kết nối, 20 lần xin TURN (xem [Co-che-chong-qua-tai-server.md](Co-che-chong-qua-tai-server.md)).
- Nếu mọi người đều là `127.0.0.1`, một người thử sai 5 lần là **cả thế giới** bị chặn. Một kẻ phá mở 32 kết nối là không ai vào được nữa.
- Cách thông thường là đọc header `X-Forwarded-For` mà proxy gắn vào. Nhưng nếu server tin header đó **từ bất kỳ ai**, thì Minh chỉ cần tự gắn một header giả, mỗi lần một IP khác, là vượt mọi giới hạn.

**Để giải quyết thì cần gì:**
- **R5:** server dùng **IP thật của người dùng** do proxy chuyển vào.
- **R6:** header chứa IP chỉ được tin khi yêu cầu **đến từ chính proxy nội bộ**.

---

## Bước 2 — Đọc IP thật, chỉ tin header từ proxy nội bộ

**Làm gì:**
- Người gác ghi lên mỗi thư "thư này của người ở địa chỉ X".
- Server chỉ tin dòng ghi đó khi thư được chuyển vào từ chính người gác, tức là từ địa chỉ nội bộ. Thư đi đường khác mà tự ghi "tôi ở địa chỉ Y" thì bị lờ đi.

**Về kỹ thuật:**
- `server.forward-headers-strategy: native` bật `RemoteIpValve` của Tomcat.
- Valve này chỉ lấy IP từ `X-Forwarded-For` khi kết nối tới từ dải địa chỉ nội bộ, như `127.0.0.1`, `10.x`, `192.168.x`. Lúc đó `getRemoteAddr()` trả về IP thật.
- Caddy gửi `X-Forwarded-For` mặc định.
- Các bộ giới hạn gộp IPv6 theo dải /64.

**Trong code:**
- Bật đọc header từ proxy: [application.yml:5-7](../signaling-server/src/main/resources/application.yml#L5-L7).
- Ghi chú cho người vận hành: [SignalingWebSocketHandler.java:78-83](../signaling-server/src/main/java/com/datn/chatp2p/signaling/ws/SignalingWebSocketHandler.java#L78-L83) và mục "Bảo mật và giới hạn của signaling server" trong [README.md](../README.md).

**Đáp ứng yêu cầu:**
- **R5:** dùng IP thật của người dùng.
- **R6:** chỉ tin header khi yêu cầu đến từ địa chỉ nội bộ.

**→ Lỗ hổng:** server đã kín, nhưng nó cần **cơ sở dữ liệu và các bí mật** để chạy: mật khẩu MySQL, khoá bí mật dùng chung với TURN. Chúng nằm ở đâu?

**Phân tích vấn đề:**
- Nếu để mật khẩu ngay trong `application.yml`, nó sẽ được đẩy lên GitHub cùng mã nguồn.
- Phiên bản cũ của dự án từng có mật khẩu cơ sở dữ liệu **mặc định** trong cấu hình. Ai đọc mã nguồn cũng biết.
- MySQL chạy bằng Docker, nếu khai `ports: "3306:3306"`, sẽ mở ra mọi địa chỉ, kể cả Internet. Docker còn tự thêm luật tường lửa riêng, nên có thể vượt qua cả `ufw`.

**Để giải quyết thì cần gì:**
- **R7:** bí mật **không nằm trong mã nguồn**. Thiếu bí mật thì server **không khởi động**, thay vì chạy với giá trị mặc định.
- **R8:** cơ sở dữ liệu chỉ nghe ở `127.0.0.1`.
- **R9:** tài khoản TURN không phải là một mật khẩu cố định cấp cho mọi người.

---

## Bước 3 — Bí mật qua biến môi trường, cơ sở dữ liệu nội bộ, TURN tạm thời

**Làm gì:**
- Mật khẩu cơ sở dữ liệu và khoá bí mật của TURN được Khôi đặt trong cấu hình dịch vụ trên VPS, không có trong mã nguồn. Thiếu thì server báo lỗi và không chạy.
- MySQL chỉ mở cửa cho chính máy VPS.
- Mỗi người dùng xin server một tài khoản TURN dùng tạm 6 giờ. Tài khoản này được tính ra từ khoá bí mật mà chỉ server và TURN biết.

**Về kỹ thuật:**
- `CHAT_P2P_DB_URL`, `CHAT_P2P_DB_USERNAME`, `CHAT_P2P_DB_PASSWORD` không có giá trị mặc định trong `application.yml`.
- Docker Compose bắt buộc có `MYSQL_PASSWORD` và `MYSQL_ROOT_PASSWORD` (cú pháp `${...:?...}`), và chỉ bind `127.0.0.1:3306`.
- Trên VPS, các biến đặt trong `/etc/systemd/system/chat-p2p.service.d/override.conf`, gồm `CHAT_P2P_DB_*`, `CHAT_P2P_SERVER_ADDRESS`, `CHAT_P2P_AUTH_REQUIRED`, `CHAT_P2P_TURN_*`.
- coturn chạy chế độ `use-auth-secret` với `static-auth-secret` trùng `CHAT_P2P_TURN_SECRET`.

**Trong code:**
- Không có giá trị mặc định cho cơ sở dữ liệu: [application.yml:12-15](../signaling-server/src/main/resources/application.yml#L12-L15).
- Secret TURN để trống thì không cấp TURN: [application.yml:28-31](../signaling-server/src/main/resources/application.yml#L28-L31).
- MySQL chỉ bind nội bộ, bắt buộc có mật khẩu: [docker-compose.yml](../docker-compose.yml).
- Cấp tài khoản TURN tạm: xem [Co-che-ket-noi-p2p.md](Co-che-ket-noi-p2p.md), Bước 4; cấu hình coturn trong [README.md](../README.md), mục TURN.

**Đáp ứng yêu cầu:**
- **R7:** bí mật nằm ngoài mã nguồn; thiếu thì không chạy.
- **R8:** MySQL chỉ nghe ở `127.0.0.1`.
- **R9:** tài khoản TURN tạm thời, hết hạn sau 6 giờ.

**→ Lỗ hổng:** hệ thống đã an toàn khi **đang chạy**. Còn chuyện **giữ cho nó chạy**, và **cập nhật** nó, thì chưa.

**Phân tích vấn đề:**
- Server bị lỗi tràn bộ nhớ, hay VPS khởi động lại sau khi cập nhật hệ điều hành, mà không ai bật lại thì tất cả người dùng mất kết nối.
- Mỗi lần cập nhật mà làm tay theo trí nhớ thì dễ sót bước, ví dụ quên build module `common` khi giao thức thay đổi. Bản mới mà lỗi thì không có đường lui.

**Để giải quyết thì cần gì:**
- **R10:** hệ điều hành **quản lý tiến trình server**: tự khởi động cùng máy, tự chạy lại khi chết.
- **R11:** có **quy trình cập nhật cố định**, có bản sao lưu để **quay lại**.

---

## Bước 4 — systemd quản lý tiến trình, quy trình cập nhật có đường lui

**Làm gì:**
- Server được đăng ký thành một dịch vụ của hệ điều hành. VPS bật lên là dịch vụ chạy. Dịch vụ chết thì 5 giây sau tự chạy lại.
- Mỗi lần cập nhật đi đúng năm bước:
  1. sao lưu file jar đang chạy;
  2. lấy mã mới, chỉ chấp nhận khi đi thẳng tiếp từ bản đang có;
  3. build lại;
  4. khởi động lại dịch vụ;
  5. xem trạng thái và log.
- Lỗi thì chép lại jar cũ rồi khởi động lại.

**Về kỹ thuật:**
- Unit `chat-p2p.service`: `ExecStart=/usr/bin/java -jar /root/Desktop_app_chat_no_server/signaling-server/target/signaling-server.jar`, `Restart=always`, `RestartSec=5`, `WantedBy=multi-user.target`. Biến môi trường để trong `override.conf`.
- Quy trình cập nhật:

  ```bash
  cp signaling-server/target/signaling-server.jar /root/signaling-server.jar.bak-$(date +%F-%H%M)
  git pull --ff-only
  mvn -q -pl signaling-server -am clean package -DskipTests
  systemctl restart chat-p2p
  systemctl status chat-p2p --no-pager; journalctl -u chat-p2p -n 30 --no-pager
  ```

- `-am` build cả các module mà server phụ thuộc, như `common` chứa `SignalMessage`.
- `--ff-only` từ chối nếu trên VPS có commit lạ, tránh lỡ tay merge.
- Dòng `status=143` trong log khi restart là tiến trình cũ nhận SIGTERM, không phải lỗi.
- Kiểm tra từ ngoài sau khi cập nhật: `GET https://chat.zingme.fun/rooms` không kèm token phải trả `401`; `POST /auth/logout` phải trả `204`.

**Đáp ứng yêu cầu:**
- **R10:** `Restart=always` và tự khởi động cùng máy.
- **R11:** quy trình cố định, có bản sao jar để quay lại.

**→ Lỗ hổng còn lại:** lớp ứng dụng đã được bảo vệ kỹ, nhưng **lớp máy chủ** vẫn còn những điểm yếu mà một lần đột nhập sẽ vô hiệu hoá tất cả những thứ trên.

**Phân tích vấn đề:**
- **Server chạy bằng tài khoản root:**
  - Unit có `User=root`, mã nguồn và jar nằm trong `/root`.
  - Nếu Spring Boot, Tomcat hay một thư viện có lỗ hổng cho chạy mã từ xa, kẻ tấn công có ngay toàn quyền VPS: đọc khoá bí mật TURN, mật khẩu cơ sở dữ liệu, sửa Caddy để chặn TLS.
- **Hệ điều hành hết hỗ trợ chuẩn:**
  - VPS chạy Ubuntu 20.04, đã hết hỗ trợ chuẩn từ 31/05/2025.
  - Lần đăng nhập gần nhất báo 194 bản vá bảo mật chỉ có qua ESM chưa được áp, và "System restart required".
- **Đăng nhập SSH bằng root và mật khẩu:** bot trên Internet liên tục dò mật khẩu SSH. Một mật khẩu yếu là mất cả máy.
- **Bí mật đọc được bằng `systemctl show`:** biến môi trường trong `override.conf` hiện ra với lệnh `systemctl show chat-p2p -p Environment`, và lệnh này mọi tài khoản trên máy đều chạy được.
- **Build và deploy thẳng trên máy đang phục vụ:**
  - `-DskipTests`: test không được chạy trước khi bản mới lên.
  - Build tốn CPU và RAM của chính máy đang phục vụ người dùng.
  - Không có bản build cố định nào để so sánh với mã đã kiểm tra.
- **Không có giám sát:** `Restart=always` cứu được khi tiến trình chết, nhưng không cứu được khi tiến trình còn sống mà không phục vụ, ví dụ treo hoặc mất kết nối cơ sở dữ liệu. Cũng không ai được báo.
- **Sao lưu cơ sở dữ liệu:** trong mã nguồn và tài liệu chưa có quy trình sao lưu MySQL định kỳ. Mất VPS là mất toàn bộ tài khoản và khoá danh tính đã đăng ký.

**Để giải quyết thì cần gì:**
- **R12:** server chạy bằng **tài khoản riêng không có đặc quyền**, bị giới hạn quyền ghi file.
- **R13:** hệ điều hành **còn được vá**.
- **R14:** SSH chỉ cho đăng nhập bằng **khoá**, không cho root đăng nhập bằng mật khẩu.
- **R15:** bí mật nằm trong file **chỉ root đọc được**.
- **R16:** bản build được **test trước** ở nơi khác, máy chủ chỉ nhận file jar đã kiểm tra.
- **R17:** có **kiểm tra sức khoẻ** định kỳ, có cảnh báo, và có **sao lưu cơ sở dữ liệu**.

---

## Bước 5 — *(đề xuất, chưa làm)* Tăng cứng máy chủ và tự động hoá deploy

**Làm gì:**
- Tạo tài khoản `chatp2p` riêng để chạy server. Chuyển ứng dụng sang `/opt/chat-p2p`. Tài khoản này chỉ ghi được vào thư mục log của nó.
- Nâng hệ điều hành lên Ubuntu 22.04 hoặc 24.04, hoặc bật ESM, rồi khởi động lại máy.
- SSH chỉ cho đăng nhập bằng khoá, tắt đăng nhập root bằng mật khẩu, thêm công cụ chặn IP dò mật khẩu.
- Bí mật chuyển sang một file chỉ root đọc được.
- Mỗi lần đẩy mã lên GitHub, hệ thống tự chạy toàn bộ test và build jar. VPS chỉ tải về jar đã qua test.
- Mỗi phút một tác vụ gọi thử `/rooms`; không thấy trả `401` thì gửi cảnh báo. Mỗi đêm sao lưu cơ sở dữ liệu ra nơi khác.

**Về kỹ thuật:**
- Trong unit systemd: `User=chatp2p`, `NoNewPrivileges=yes`, `ProtectSystem=strict`, `ProtectHome=yes`, `PrivateTmp=yes`, `ReadWritePaths=/var/log/chat-p2p`.
- Bí mật: `EnvironmentFile=/etc/chat-p2p/secrets.env` với quyền `chmod 600`, hoặc `LoadCredential=`.
- SSH: `PasswordAuthentication no`, `PermitRootLogin prohibit-password`, thêm `fail2ban`.
- GitHub Actions chạy `mvn -pl common,crypto,signaling-server,p2p-core,client-javafx test` rồi đính kèm `signaling-server.jar` vào bản phát hành. Script deploy tải jar, kiểm tra SHA-256, rồi chạy các bước sao lưu và restart như Bước 4.
- `mysqldump --single-transaction chat_p2p | gzip` chạy bằng cron hoặc systemd timer, đẩy sang nơi lưu trữ khác.
- Tuỳ chọn: thêm Spring Boot Actuator `/actuator/health` chỉ mở ở `127.0.0.1` để kiểm tra sức khoẻ.

**Đáp ứng yêu cầu:**
- **R12** đến **R17**: mỗi đề xuất ứng với một yêu cầu ở trên.

---

## Sơ đồ triển khai hiện tại

```
Internet
   │  TCP 443 (TLS, chứng chỉ Let's Encrypt)        UDP 3478 + dải relay (TURN)
   ▼                                                   ▼
┌──────────────── VPS chat.zingme.fun ─────────────────────────────────┐
│  Caddy :443 ──reverse_proxy──► Java signaling-server 127.0.0.1:8080  │
│                                   │  (systemd chat-p2p, Restart=always)│
│                                   ▼                                    │
│                         MySQL (Docker) 127.0.0.1:3306                  │
│  coturn :3478 (use-auth-secret, dùng chung khoá bí mật với server)    │
└───────────────────────────────────────────────────────────────────────┘
Tin nhắn và file: đi thẳng giữa các máy client (UDP), hoặc qua coturn khi cần;
server không bao giờ nhận nội dung.
```

---

## Tóm tắt chuỗi thiết kế

| Bước | Làm gì | Vá được gì | Sinh ra vấn đề gì |
|---|---|---|---|
| 0 | Chạy jar trực tiếp, `ws://`, mở cổng MySQL | — | Nghe lén mật khẩu và token; cơ sở dữ liệu ra Internet; không tự chạy lại |
| 1 | Caddy giữ TLS tự gia hạn, server chỉ nghe `127.0.0.1` | Mã hoá đường tới server | Server thấy mọi người là `127.0.0.1` |
| 2 | `forward-headers-strategy: native`, chỉ tin header từ proxy nội bộ | Giới hạn theo IP hoạt động đúng | Bí mật và cơ sở dữ liệu cần chỗ an toàn |
| 3 | Bí mật qua biến môi trường, không có mặc định; MySQL `127.0.0.1`; TURN tạm 6 giờ | Không còn mật khẩu mặc định hay cổng cơ sở dữ liệu công khai | Giữ cho chạy và cập nhật vẫn làm tay |
| 4 | systemd `Restart=always`; quy trình sao lưu, pull, build, restart, kiểm tra | Tự hồi phục, cập nhật có đường lui | Chạy bằng root, hệ điều hành chưa vá, SSH mật khẩu, không có CI, giám sát, sao lưu cơ sở dữ liệu |
| 5 *(đề xuất)* | Tài khoản riêng và hardening systemd, nâng hệ điều hành, SSH bằng khoá, CI, health check, sao lưu | Một lỗ hổng ứng dụng không thành mất cả máy | — |

```
B0 chạy trần
 ──nghe lén, cổng mở──────────────► B1 Caddy TLS, server nội bộ
 ──mọi người là 127.0.0.1─────────► B2 IP thật từ proxy tin cậy
 ──bí mật, cơ sở dữ liệu ở đâu────► B3 biến môi trường, MySQL nội bộ, TURN tạm
 ──giữ chạy và cập nhật───────────► B4 systemd + quy trình có đường lui
 ──lớp máy chủ yếu────────────────► B5 hardening + CI + giám sát (đề xuất)
```
