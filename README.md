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
| [common](common) | Model dung chung + interface `DataChannel` + giao thuc signaling | A + B |
| [crypto](crypto) | Trao khoa ECDH, ma hoa AES-GCM, fingerprint xac thuc | Thanh vien B |
| [p2p-core](p2p-core) | Cai dat `DataChannel`: `LoopbackDataChannel` (demo), `P2pDataChannel` (khung, TODO ice4j), `SignalingClient` | Thanh vien A |
| [signaling-server](signaling-server) | Spring Boot + WebSocket: quan ly phong, relay SDP/ICE candidate | Thanh vien A |
| [client-javafx](client-javafx) | Giao dien desktop JavaFX | Thanh vien B |

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

# Test module crypto (ECDH + AES-GCM)
mvn -pl crypto test

# Test signaling server (khoi dong that mot Spring context + WebSocket that de test)
mvn -pl signaling-server test

# Chay signaling server that (mac dinh cong 8080)
mvn -pl signaling-server -DskipTests package
java -jar signaling-server/target/signaling-server.jar

# Chay demo giao dien JavaFX (dung LoopbackDataChannel + ma hoa that,
# chua can signaling-server dang chay)
mvn -pl client-javafx javafx:run
```

> **Luu y (Windows):** neu duong dan thu muc du an co dau tieng Viet (nhu
> `...\Máy tính\...`), goal `spring-boot:run` co the bao loi
> `Could not find or load main class` du build thanh cong (loi encoding
> classpath cua plugin, da kiem chung thuc te) - dung `mvn package` roi
> `java -jar ...` nhu tren se chay dung.

Demo JavaFX: nhap ten hien thi -> tao/vao phong -> man hinh Room hien hai
"peer": ban va mot "Nguoi dung demo" (mo phong noi bo qua `LoopbackDataChannel`
va `DemoPeerSimulator`, xem [client-javafx/.../demo](client-javafx/src/main/java/com/datn/chatp2p/client/demo)).
Goi tin nhan se duoc ma hoa AES-GCM that (khoa phien sinh tu ECDH that) truoc
khi gui qua kenh, va Nguoi dung demo se tu dong tra loi sau ~1 giay - chung
minh tron pipeline UI + ma hoa + DataChannel hoat dong dung truoc khi lap
mang P2P that.

## Trang thai va viec tiep theo

Ban scaffold hien tai **chua** co ket noi P2P qua Internet that (chua co
ice4j/STUN/TURN), chua noi `client-javafx` voi `signaling-server` qua mang,
chua co truyen file/video/audio - dung nhu ke hoach trong De-cuong (giai doan
4 tro di, tuan 7+).

Buoc tiep theo, anh xa vao [Phan-cong-cong-viec.md](docs/Phan-cong-cong-viec.md):

- **Thanh vien A**: cai dat that `P2pDataChannel` va `WebSocketSignalingClient`
  (module `p2p-core`) bang ice4j - xem cac TODO trong hai file do.
- **Thanh vien B**: khi A xong, thay `LoopbackDataChannel` + `DemoPeerSimulator`
  trong [RoomController](client-javafx/src/main/java/com/datn/chatp2p/client/view/RoomController.java)
  bang ket noi that toi cac peer khac trong phong (qua `SignalingClient` +
  `P2pDataChannel`); logic ma hoa/UI giu nguyen. Sau do them truyen file
  (chia chunk, ma hoa tung chunk).
