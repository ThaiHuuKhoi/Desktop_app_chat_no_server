# Chat P2P Java

Ung dung chat ngang hang (P2P), ma hoa dau-cuoi, tren nen tang Java. Xem dinh
huong day du trong [docs/De-cuong-Chat-P2P-Java.md](docs/De-cuong-Chat-P2P-Java.md)
va phan cong cong viec trong [docs/Phan-cong-cong-viec.md](docs/Phan-cong-cong-viec.md).
Tai lieu ky thuat day du (kien truc, chuc nang, kiem thu, van hanh, rui ro, bao
mat) gop trong 1 file: [docs/Tai-lieu-ky-thuat.md](docs/Tai-lieu-ky-thuat.md).

Kien truc va UX (phong chat, danh sach peer, khung chat, xac thuc fingerprint)
lay cam hung tu [chitchatter](https://github.com/jeremyckahn/chitchatter) - mot
ung dung chat P2P mesh mo nguon bang React/WebRTC. Chat P2P Java **khong port
code** cua chitchatter (stack hoan toan khac: JavaFX thay React, Spring Boot +
ice4j thay Trystero/WebRTC), chi ke thua tu duy thiet ke: signaling server toi
gian chi "gioi thieu" cac peer roi rut lui, moi noi dung deu ma hoa dau-cuoi.

## Yeu cau moi truong

- JDK 17+
- Maven 3.9+

## Cau truc du an

Maven multi-module, chia theo tang ky thuat dung nhu
[Phan-cong-cong-viec.md](docs/Phan-cong-cong-viec.md):

| Module | Vai tro | Phu trach |
|---|---|---|
| [common](common) | Model dung chung, interface `DataChannel`, giao thuc `Envelope` (P2P) va `SignalMessage` (signaling) | A + B |
| [crypto](crypto) | Trao khoa ECDH, ma hoa AES-256-GCM, chu ky danh tinh ECDSA, luu khoa danh tinh, fingerprint | Thanh vien B |
| [p2p-core](p2p-core) | `RoomSession` (mesh), `PeerConnection` (trao khoa + chong replay), ICE/STUN/TURN bang ice4j (`IceP2pConnectionEstablisher`), `P2pDataChannel` (UDP), `WebSocketSignalingClient`; `LoopbackDataChannel` chi dung cho test | Thanh vien A |
| [signaling-server](signaling-server) | Spring Boot + WebSocket: quan ly phong, relay OFFER/ANSWER/ICE; REST `/auth/*` + MySQL cho tai khoan | Thanh vien A |
| [client-javafx](client-javafx) | Giao dien desktop JavaFX, ket noi that qua `P2pChatSession` | Thanh vien B |

### Luong ket noi va bao mat

1. Client JOIN phong qua WebSocket -> server tra `PEER_LIST`; nguoi moi vao gui
   ICE OFFER toi tung peer co san, server chi relay OFFER/ANSWER.
2. ICE xong -> 2 ben co duong UDP truc tiep (hoac qua TURN). Moi ben gui public
   key ECDH tam thoi (goi duy nhat khong ma hoa) -> suy ra khoa AES-256-GCM.
3. Moi goi tiep theo la `Envelope` ma hoa AES-GCM, co chong replay.
4. Hai ben gui `PEER_IDENTITY`: public key ECDSA lau dai + chu ky tren
   `roomId | peerId | SHA-256(khoa ECDH ben ky) | SHA-256(khoa ECDH ben nhan)`.
   Vi chu ky gan voi khoa ECDH cua phien, ke dung giua (vd signaling server bi
   chiem) thay khoa ECDH se lam chu ky that bai. Nguoi dung van nen doi chieu
   fingerprint o lan gap dau tien.
5. Tin nhan (`MESSAGE`) duoc ben nhan tra `MESSAGE_ACK`; ben gui tu gui lai
   (toi da 6 lan, cach 500ms) neu chua co ACK, ben nhan loai ban trung theo id.
   Het luot thi UI hien canh bao. Thu tu tin nhan van khong duoc dam bao tuyet doi.
6. **Phong co mat khau**: tu (ten phong, mat khau) sinh khoa bang PBKDF2. Server
   chi thay `ten~the` (the suy ra tu khoa), nen nguoi khac mat khau roi vao phong
   khac tren server. Sau ECDH, 2 ben con gui `ROOM_PROOF` (HMAC gan voi khoa ECDH
   cua phien) - peer khong co bang chung hop le bi bo qua. Mat khau khong bao gio
   roi khoi may.
7. **Lich su cho nguoi moi vao**: khi xac thuc xong danh tinh 1 peer, moi ben gui
   lich su chat nhom (toi da 200 tin, khong gom tin nhan rieng) qua
   `MESSAGE_TRANSCRIPT`, chia goi vua UDP + ACK/gui lai. Giao dien danh dau
   "lich su do X chia se" vi khong xac minh duoc nguoi viet tung tin.

Interface chung giua hai phan viec (da chot trong Phan-cong-cong-viec.md):

```java
public interface DataChannel {
    void send(byte[] data);
    void onReceive(Consumer<byte[]> handler);
    void close();
}
```

## Chay thu

```bash
# Build toan bo
mvn -q -DskipTests package

# Chay toan bo test (test p2p-core can module signaling-server, nen chay tu goc repo)
mvn test
# (22/11/2026: 283 test pass, chay trong Docker co lap - lenh o muc "Chay test trong Docker" ben duoi)

# Chay signaling server that (mac dinh cong 8080, can MySQL - xem ben duoi)
java -jar signaling-server/target/signaling-server.jar

# Chay client JavaFX (can signaling-server dang chay)
mvn -pl client-javafx javafx:run
```

### Dong goi client cho nguoi dung (Windows)

```powershell
powershell -ExecutionPolicy Bypass -File packaging\build-windows.ps1
```

Ra `dist\ChatP2P\ChatP2P.exe` (kem Java runtime, khong can cai Java/Maven) va
`dist\ChatP2P-windows.zip` (~72 MB) de gui cho nguoi dung: giai nen roi bam dup
`ChatP2P.exe`. Da cai WiX Toolset 3 thi them `-Type msi` de ra file cai dat `.msi`.

Log cua client: `~/.chat-p2p-java/logs/client-0.log` (xoay vong 5 file x 5 MB).

### Cau hinh TURN

**Nguoi dung khong can cau hinh gi.** Khi vao phong, client goi
`GET <server>/turn-credentials` (qua https) va nhan 1 tai khoan TURN **tam thoi**
(het han sau 6 gio), theo co che "TURN REST API" cua coturn.

Phia server (VPS), coturn va signaling server dung chung 1 khoa bi mat:

```ini
# /etc/turnserver.conf
use-auth-secret
static-auth-secret=<khoa-bi-mat>
```

```ini
# bien moi truong cua signaling server
CHAT_P2P_TURN_SECRET=<khoa-bi-mat>   # giong static-auth-secret
CHAT_P2P_TURN_HOST=165.101.47.83
CHAT_P2P_TURN_PORT=3478              # mac dinh 3478
CHAT_P2P_TURN_TTL_SECONDS=21600      # mac dinh 6 gio
```

De test voi TURN khac, van co the dat cau hinh co dinh o client (uu tien hon tai
khoan tu server) bang bien moi truong `CHAT_P2P_TURN_HOST/PORT/USERNAME/PASSWORD`
hoac file `~/.chat-p2p-java/client.properties` (`chat.p2p.turn.host=...`).

### Cau hinh tai khoan

`signaling-server` dung MySQL cho tai khoan va session token. Dat cac bien moi
truong truoc khi khoi dong server:

```powershell
$env:CHAT_P2P_DB_URL="jdbc:mysql://localhost:3306/chat_p2p?createDatabaseIfNotExist=true&serverTimezone=UTC"
$env:CHAT_P2P_DB_USERNAME="chat_p2p"
$env:CHAT_P2P_DB_PASSWORD="mat-khau-mysql"
java -jar signaling-server/target/signaling-server.jar
```

Khi mo client, nguoi dung co the chon **Khach tu do** hoac **Dang nhap / Dang ky**.
Client dang ky/dang nhap qua REST `/auth/*`, sau do gui opaque session token
trong ban tin JOIN WebSocket. Server chi luu hash token va BCrypt hash cua
mat khau; khong luu plaintext. WebSocket bat buoc tai khoan mac dinh;
chi dat `CHAT_P2P_AUTH_REQUIRED=false` trong moi truong demo/test tin cay.
Trong moi truong test, co the dung H2 tu
`signaling-server/src/test/resources/application.yml`.

#### Chay MySQL bang Docker

Copy `.env.example` thanh `.env`, doi cac mat khau dev, sau do chay tai thu
muc goc:

```powershell
Copy-Item .env.example .env
docker compose up -d mysql
docker compose ps
```

Khi signaling server chay tren may host, dung:

```powershell
$env:CHAT_P2P_DB_URL="jdbc:mysql://localhost:3306/chat_p2p?serverTimezone=UTC"
$env:CHAT_P2P_DB_USERNAME="chat_p2p"
$env:CHAT_P2P_DB_PASSWORD="mat-khau-trong-env"
```

`CHAT_P2P_DB_URL`, `CHAT_P2P_DB_USERNAME` va `CHAT_P2P_DB_PASSWORD` phai duoc
dat khi chay server; server khong con fallback ve credential mac dinh.
Compose cung yeu cau `MYSQL_PASSWORD` va `MYSQL_ROOT_PASSWORD`, va chi bind
MySQL vao `127.0.0.1` de khong public cong database ra Internet.

ICE direct P2P dung UDP port range `10000-11000`. Tren VPS va may client,
mo UDP outbound/inbound cho range nay; signaling TCP `8080` chi dung de trao
doi candidate, khong chuyen tiep du lieu chat. Client chi quang ba candidate
IPv4 routable, loai bo IPv6 link-local, loopback va multicast de tranh ICE
kiem tra cac adapter ao khong the truy cap. Direct P2P van khong the vuot qua
mot so symmetric NAT; khi do client fallback qua TURN neu da cau hinh (xem
"Cau hinh TURN cho client").

Schema duoc Docker khoi tao tu dong trong lan dau volume duoc tao; server
khong chay lai schema tren moi lan khoi dong. De xoa du lieu database va tao
lai tu dau:

```powershell
docker compose down -v
```

> **Luu y (Windows):** neu duong dan thu muc du an co dau tieng Viet (nhu
> `...\Máy tính\...`), goal `spring-boot:run` co the bao loi
> `Could not find or load main class` du build thanh cong (loi encoding
> classpath cua plugin, da kiem chung thuc te) - dung `mvn package` roi
> `java -jar ...` nhu tren se chay dung.

Chay client JavaFX: chon Khach tu do hoac Dang nhap -> nhap ten -> tao/vao
phong. Mo them client thu 2 (cung may hoac may khac) vao cung phong de chat
P2P that; danh sach peer hien trang thai xac thuc va fingerprint.

## Trang thai va viec tiep theo

Da co: ket noi P2P that qua ice4j (STUN, TURN fallback), signaling + tai
khoan, ma hoa dau-cuoi, xac thuc danh tinh gan voi phien ECDH, tin nhan nhom
va tin nhan rieng co ACK/gui lai, trang thai dang go, Markdown, am bao va thong
bao man hinh, chon theme, phong co mat khau, lich su chat cho nguoi moi vao,
truyen file (xem duoi), da nhan / da xem, tra loi, tha cam xuc, sua / xoa tin
cua minh, xem truoc anh, ma QR so van tay, ghi nho van tay + canh bao khi khoa
doi, lich su tren may (tuy chon, mac dinh tat, ma hoa DPAPI), xuat / nhap khoa
danh tinh sang may khac (PBKDF2 + AES-GCM).

Chua co (theo De-cuong): audio/video/screen share - cac `EnvelopeType` tuong
ung moi chi khai bao; gui tiep file sau khi mat ket noi.

### Bao mat va gioi han cua signaling server (cap nhat 22/11/2026)

Sau dot kiem dinh ma nguon, 13 van de bao mat da duoc sua (chi tiet:
[docs/Tai-lieu-ky-thuat.md](docs/Tai-lieu-ky-thuat.md), muc J.3). Nhung gi
nguoi van hanh can biet:

- Server **khong relay `authToken`** cua nguoi gui sang peer khac nua, va chi
  relay tu ket noi dang thuc su o trong phong.
- Ket noi WebSocket phai `JOIN` trong 30 s; moi IP toi da 32 ket noi dong thoi
  (doi bang `CHAT_P2P_WS_MAX_CONNECTIONS_PER_ADDRESS`); `/auth/login` va
  `/auth/register` toi da 10 lan/phut/IP; `/turn-credentials` 20 lan/phut/IP.
  IPv6 tinh theo dai /64.
- Ten phong toi da 200 ky tu, `peerId` 100, ten khach 64; toi da 5.000 phong
  (vuot thi tra `SERVER_BUSY`).
- Cac gioi han theo IP dua vao IP that cua client: reverse proxy **phai** gui
  `X-Forwarded-For` (Caddy gui mac dinh). Neu khong, moi nguoi dung bi tinh
  chung 1 IP.
- Client: o chon server bi khoa theo server da dang nhap (token chi gui toi
  dung server cap no); ket noi thay the cung `peerId` phai xac thuc lai.

### Chay test trong Docker (co lap, khong ra Internet)

```bash
# Mang noi bo: co IP rieng de ICE chay duoc, nhung khong ra Internet
docker network create --internal chatp2p-isolated
docker run --rm --network chatp2p-isolated \
  -v <ban sao ma nguon>:/work -v <~/.m2/repository>:/m2:ro -w /work \
  maven:3.9.9-eclipse-temurin-17 \
  mvn -o -B -Dmaven.repo.local=/m2 -Djavafx.platform=win \
  -pl common,crypto,signaling-server,p2p-core,client-javafx test
```

Khong dung `--network none`: khi do chi con dia chi loopback, ma ice4j bo qua
loopback nen test ICE khong tao duoc ket noi. `-Djavafx.platform=win` vi kho
Maven tren may phat trien chi co jar JavaFX ban Windows.

### Truyen file

`FILE_OFFER` -> nguoi nhan bam Nhan (`FILE_ANSWER`) -> `FILE_CHUNK` (chunk 4 KB,
cua so truot 8 chunk) <-> `FILE_ACK` (`contiguousUpTo` + danh sach chunk nhan le)
-> ben nhan kiem SHA-256 ca file roi gui ACK `verified`. Toi da 100 MB.

- `P2pDataChannel` chia moi ban tin thanh manh <= 1150 byte (ice4j cat goi UDP
  > 1500 byte) va ghep lai o ben nhan.
- Ben gui **gui lai nhanh** (kieu TCP RACK): chunk duoc coi la mat khi da co 3
  chunk gui sau no toi noi - khong doi het hen gio 500 ms.
- Ben nhan liet ke chunk vua toi + cac chunk moi nhat trong ACK, ACK moi 4 KB.

Do toc do (4 MB qua ICE that tren localhost, trung vi 3 lan, mat goi gia lap
theo tung datagram):

```powershell
mvn -pl common,crypto,signaling-server,p2p-core,client-javafx test `
  "-Dtest=FileTransferBenchmarkTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dchatp2p.benchmark=true"
```

| Mat goi | Truoc (chunk 512 B, cua so 64) | Sau (chunk 4 KB, cua so 8, gui lai nhanh) |
|---|---|---|
| 0% | 6,4 MB/s | 17,9 MB/s |
| 1% | 0,20 MB/s | 18,1 MB/s |
| 3% | 0,12 MB/s | 18,0 MB/s (lan cham nhat 4,7) |
| 5% | 0,09 MB/s | 5,5 MB/s (lan cham nhat 2,2) |

Chi tang chunk ma khong gui lai nhanh thi gan nhu khong giup: code cu voi chunk
4 KB o muc mat 1% chi dat 0,37 MB/s. Chunk 8-16 KB nhanh ngang 4 KB khi mang sach
nhung sup manh khi mat goi (1 chunk 16 KB ~26 manh). So do tren localhost - chua
do tren Internet that / qua TURN.

> **Tuong thich:** dinh dang datagram cua `P2pDataChannel` (chia manh) khac ban
> cu - client moi va client cu khong ket noi duoc voi nhau, can cap nhat tat ca.

## Ghi nguon

Anh emoji mau cho 5 cam xuc (`client-javafx/src/main/resources/emoji/*.png`) lay tu
[Twemoji](https://github.com/jdecked/twemoji) (ban 15.1.0), Copyright Twitter, Inc and
other contributors, do hoa theo giay phep [CC-BY 4.0](https://creativecommons.org/licenses/by/4.0/).
Dung anh vi JavaFX tren Windows chi ve emoji don sac (da thu JavaFX 21, 22, 23, 24).
