# Chat P2P Java

Ung dung chat ngang hang (P2P), ma hoa dau-cuoi, tren nen tang Java. Xem dinh
huong day du trong [docs/De-cuong-Chat-P2P-Java.md](docs/De-cuong-Chat-P2P-Java.md)
va phan cong cong viec trong [docs/Phan-cong-cong-viec.md](docs/Phan-cong-cong-viec.md).

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

# Chay signaling server that (mac dinh cong 8080, can MySQL - xem ben duoi)
java -jar signaling-server/target/signaling-server.jar

# Chay client JavaFX (can signaling-server dang chay)
mvn -pl client-javafx javafx:run
```

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
bao man hinh, chon theme, phong co mat khau, lich su chat cho nguoi moi vao.

Dang lam: truyen file (phia gui tin cay da xong; phia nhan + giao dien do B lam).

Chua co (theo De-cuong): audio/video/screen share - cac `EnvelopeType` tuong
ung moi chi khai bao.
