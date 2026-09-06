package com.datn.chatp2p.p2p;

import com.datn.chatp2p.common.channel.DataChannel;
import com.datn.chatp2p.common.protocol.Envelope;
import com.datn.chatp2p.common.protocol.EnvelopeType;
import com.datn.chatp2p.common.signal.SignalMessage;
import com.datn.chatp2p.common.signal.ice.IceAnswerPayload;
import com.datn.chatp2p.common.signal.ice.IceOfferPayload;
import com.datn.chatp2p.crypto.KeyExchangeService;
import com.datn.chatp2p.p2p.ice.IceP2pConnectionEstablisher;
import com.datn.chatp2p.p2p.signaling.SignalingClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ice4j.TransportAddress;

import java.io.IOException;
import java.security.KeyPair;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Quan ly TAT CA peer trong 1 phong - "PeerRoom" cua chitchatter phia Java
 * (Tai-lieu-ky-thuat.md Phan E.4). La lop DUY NHAT trong p2p-core biet ca 3
 * mang da co: {@link SignalingClient} (tim nhau), {@link IceP2pConnectionEstablisher}
 * (thiet lap ket noi), {@link PeerConnection} (trao khoa + Envelope) - cac lop
 * kia khong biet ve nhau, chi RoomSession noi chung lai.
 *
 * <p><b>Ai chu dong, ai tra loi</b> (quy uoc bat buoc de 2 ben khong cung luc
 * gui OFFER hoac cung cho nhau - se ket noi khong bao gio xong):
 * <ul>
 *   <li>Peer thay trong {@code PEER_LIST} luc vua vao phong (nghia la ho da
 *       vao TRUOC minh) -> <b>minh chu dong gui OFFER</b> ({@link #connectAsOfferer}).</li>
 *   <li>Peer bao qua {@code PEER_JOINED} (vao SAU minh) -> ho se thay minh
 *       trong PEER_LIST cua HO va tu gui OFFER cho minh -> minh chi can
 *       cho va {@link #handleOffer} khi OFFER toi, KHONG tu goi offer.</li>
 * </ul>
 *
 * <p><b>Chua lam</b> (xem docs/Bao-cao-thuc-hien-Nhiem-vu-A.md muc "Chua lam"):
 * gui/xac thuc tu dong {@code EnvelopeType.PEER_IDENTITY} ngay sau khi
 * {@code PeerConnection} handshake xong (can {@code IdentitySignatureService}
 * o module crypto, chua co) - {@code verificationState} hien luon giu nguyen
 * gia tri mac dinh {@code UNVERIFIED} cua {@link PeerConnection}.
 */
public final class RoomSession {

    private final String roomId;
    private final String selfPeerId;
    private final String selfUserName;
    private final SignalingClient signalingClient;
    private final List<TransportAddress> stunServers;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, PeerConnection> peers = new ConcurrentHashMap<>();
    private final Map<String, IceP2pConnectionEstablisher> pendingEstablishers = new ConcurrentHashMap<>();
    /** userName cua peer bao qua PEER_JOINED, giu tam de gan vao PeerConnection khi ICE xong (chi dung khi minh la ben tra loi). */
    private final Map<String, String> pendingUserNames = new ConcurrentHashMap<>();

    private final Map<EnvelopeType, List<BiConsumer<String, Envelope>>> envelopeHandlers =
            new EnumMap<>(EnvelopeType.class);
    private volatile Consumer<PeerConnection> onPeerJoinedHandler;
    private volatile Consumer<String> onPeerLeftHandler;
    private volatile BiConsumer<String, Throwable> onConnectionFailedHandler;

    /**
     * @param roomId        ten phong (hoac {@code effectiveRoomId} da bam neu phong rieng tu -
     *                      xem Tai-lieu-ky-thuat.md Phan E.12.3, chua cai dat trong lop nay).
     * @param selfPeerId    id on dinh cua chinh minh trong phong nay.
     * @param selfUserName  ten hien thi cua chinh minh.
     * @param signalingClient thuong la {@code WebSocketSignalingClient} that; co the la fake khi test.
     * @param stunServers   danh sach STUN server truyen cho MOI {@link IceP2pConnectionEstablisher}
     *                      moi tao - xem {@code IceP2pConnectionEstablisher} de biet vi sao can.
     */
    public RoomSession(
            String roomId,
            String selfPeerId,
            String selfUserName,
            SignalingClient signalingClient,
            List<TransportAddress> stunServers) {
        this.roomId = roomId;
        this.selfPeerId = selfPeerId;
        this.selfUserName = selfUserName;
        this.signalingClient = signalingClient;
        this.stunServers = stunServers;

        signalingClient.onPeerList(this::handlePeerList);
        signalingClient.onPeerJoined(this::handlePeerJoinedNotice);
        signalingClient.onPeerLeft(this::handlePeerLeftNotice);
        signalingClient.onOffer(this::handleOffer);
        signalingClient.onAnswer(this::handleAnswer);
    }

    /** Ket noi toi signaling server va gui JOIN - bat dau toan bo vong doi cua phong. */
    public void join(String signalingServerUri) {
        signalingClient.connect(signalingServerUri, roomId, selfPeerId, selfUserName);
    }

    /** Dong toan bo ket noi voi moi peer, huy cac phien ICE dang cho, roi ngat signaling. */
    public void leave() {
        for (PeerConnection connection : peers.values()) {
            connection.close();
        }
        peers.clear();
        for (IceP2pConnectionEstablisher establisher : pendingEstablishers.values()) {
            establisher.dispose();
        }
        pendingEstablishers.clear();
        signalingClient.disconnect();
    }

    /** Goi khi 1 peer moi da hoan tat CA ICE lan trao khoa ECDH - {@code connection.send(...)} dung duoc ngay. */
    public void onPeerJoined(Consumer<PeerConnection> handler) {
        this.onPeerJoinedHandler = handler;
    }

    /** Goi khi 1 peer roi phong (PEER_LEFT tu signaling server). */
    public void onPeerLeft(Consumer<String> handler) {
        this.onPeerLeftHandler = handler;
    }

    /** Goi khi thiet lap ICE voi 1 peer that bai han (khong tim duoc duong truyen). */
    public void onConnectionFailed(BiConsumer<String, Throwable> handler) {
        this.onConnectionFailedHandler = handler;
    }

    /** Dang ky nhan {@link Envelope} theo dung {@code type}, tu BAT KY peer nao. Co the goi nhieu lan cho cung 1 type. */
    public void onEnvelope(EnvelopeType type, BiConsumer<String, Envelope> handler) {
        envelopeHandlers.computeIfAbsent(type, t -> new CopyOnWriteArrayList<>()).add(handler);
    }

    /** Gui toi TAT CA peer dang co trong phong (nhom). */
    public void broadcast(EnvelopeType type, Object payload) {
        for (PeerConnection connection : peers.values()) {
            connection.send(type, payload);
        }
    }

    /** Gui toi DUNG 1 peer (dung cho direct message hoac dieu khien). */
    public void sendTo(String peerId, EnvelopeType type, Object payload) {
        PeerConnection connection = peers.get(peerId);
        if (connection == null) {
            throw new IllegalArgumentException("Khong co ket noi voi peer " + peerId + " (chua ket noi xong hoac da roi phong)");
        }
        connection.send(type, payload);
    }

    /** Danh sach peerId da ket noi xong (khong bao gom cac phien ICE dang cho). */
    public Collection<String> getPeerIds() {
        return List.copyOf(peers.keySet());
    }

    /**
     * CHI DUNG CHO TEST (package-private, khong phai API cong khai): {@code true}
     * neu {@code peerId} van con 1 phien ICE dang cho trong {@link #pendingEstablishers} -
     * dung de xac nhan KHONG co establisher nao bi "mo coi" (ro ri UDP socket)
     * sau khi 1 loi voi DUNG peer do da duoc xu ly xong, thay vi chi tin vao doc
     * code. Kiem tra dung 1 peerId (khong phai tong so luong) vi cac peer KHAC
     * trong cung phong van co the con dang ICE that (chua COMPLETED) tai cung
     * thoi diem - do khong phai ro ri, chi la dang xu ly binh thuong.
     */
    boolean hasPendingEstablisherFor(String peerId) {
        return pendingEstablishers.containsKey(peerId);
    }

    private void handlePeerList(SignalMessage message) {
        for (SignalMessage.PeerInfo info : message.getPeers()) {
            try {
                connectAsOfferer(info.getPeerId(), info.getUserName());
            } catch (RuntimeException e) {
                // 1 peer loi (vd het cong UDP cho ICE) khong duoc chan cac peer con lai
                // trong cung PEER_LIST - dung nguyen tac da ap dung o SignalingWebSocketHandler
                // (Tai-lieu-ky-thuat.md Phan H.1). failConnection() cung tu don dep
                // establisher da tao (neu co) truoc khi bao loi - xem javadoc cua no.
                failConnection(info.getPeerId(), e);
            }
        }
    }

    private void handlePeerJoinedNotice(SignalMessage message) {
        // Chi la thong bao "co peer moi vao sau minh" - HO se tu gui OFFER (ho thay
        // minh qua PEER_LIST cua chinh ho), minh chi ghi nho ten hien thi de gan
        // vao PeerConnection luc handleOffer/onIceConnected, KHONG tu goi offer o day.
        pendingUserNames.put(message.getFromPeerId(), message.getUserName());
    }

    private void handlePeerLeftNotice(SignalMessage message) {
        String peerId = message.getFromPeerId();
        pendingUserNames.remove(peerId);

        IceP2pConnectionEstablisher pendingEstablisher = pendingEstablishers.remove(peerId);
        if (pendingEstablisher != null) {
            pendingEstablisher.dispose();
        }

        PeerConnection removed = peers.remove(peerId);
        if (removed != null) {
            removed.close();
            Consumer<String> handler = onPeerLeftHandler;
            if (handler != null) {
                handler.accept(peerId);
            }
        }
    }

    /**
     * Tao 1 {@link IceP2pConnectionEstablisher} moi cho {@code peerId}, dang ky vao
     * {@link #pendingEstablishers} va gan san 2 callback {@code onConnected}/{@code onFailed}
     * dung chung cho ca 2 vai tro (offerer lan answerer - {@link #connectAsOfferer} va
     * {@link #handleOffer} truoc day tu lap lai y het doan nay).
     */
    private IceP2pConnectionEstablisher createEstablisherFor(String peerId, String userName) {
        IceP2pConnectionEstablisher establisher = new IceP2pConnectionEstablisher(stunServers);
        // QUAN TRONG (bao mat): pendingEstablishers.put() tra ve GIA TRI CU neu da
        // co san 1 establisher dang cho cho DUNG peerId nay (vd 1 peer gui lai OFFER
        // lan 2 truoc khi lan dau kip hoan tat - do bug o phia ho, hoac CO Y tan
        // cong DoS: gui lien tiep nhieu OFFER toi CUNG 1 nan nhan de chiem het dai
        // cong UDP cua nan nhan, dai cong nay huu han - Tai-lieu-ky-thuat.md Phan
        // H.2 da xac nhan signaling KHONG gioi han toc do/xac thuc ban tin, nen
        // khong co gi ngan 1 peer gui OFFER lien tuc). Neu khong dispose() gia tri
        // cu truoc khi ghi de, establisher do se "mo coi" vinh vien - ro ri 1 UDP
        // socket moi lan bi ghi de, du dai cong co rong bao nhieu cung se can kiet
        // neu ke tan cong gui du nhieu OFFER.
        IceP2pConnectionEstablisher previous = pendingEstablishers.put(peerId, establisher);
        if (previous != null) {
            previous.dispose();
        }
        establisher.onConnected(channel -> onIceConnected(peerId, userName, channel));
        establisher.onFailed(error -> handleIceFailed(peerId, error));
        return establisher;
    }

    private void connectAsOfferer(String peerId, String userName) {
        IceP2pConnectionEstablisher establisher = createEstablisherFor(peerId, userName);

        var offer = establisher.createOffer();
        signalingClient.sendOffer(peerId, toJson(offer));
    }

    private void handleOffer(SignalMessage message) {
        String peerId = message.getFromPeerId();
        try {
            var offer = fromJson(message.getPayload(), IceOfferPayload.class);
            String userName = pendingUserNames.get(peerId);

            IceP2pConnectionEstablisher establisher = createEstablisherFor(peerId, userName);

            var answer = establisher.createAnswer(offer);
            signalingClient.sendAnswer(peerId, toJson(answer));
        } catch (RuntimeException e) {
            // OFFER hong (JSON/candidate sai dinh dang) hoac ICE khoi tao that bai - chi
            // bo qua dung peer nay, khong duoc lam sap ca luong xu ly ban tin cua RoomSession.
            failConnection(peerId, e);
        }
    }

    private void handleAnswer(SignalMessage message) {
        String peerId = message.getFromPeerId();
        IceP2pConnectionEstablisher establisher = pendingEstablishers.get(peerId);
        if (establisher == null) {
            return; // Khong khop phien ICE nao dang cho (vd peer da roi phong truoc do) - bo qua.
        }
        try {
            var answer = fromJson(message.getPayload(), IceAnswerPayload.class);
            establisher.acceptAnswer(answer);
        } catch (RuntimeException e) {
            failConnection(peerId, e);
        }
    }

    /**
     * Xu ly 1 loi ket noi voi {@code peerId}: don dep establisher dang do dang
     * (neu con) roi bao ra ngoai qua {@link #onConnectionFailed}. Goi chung boi
     * CA 4 nhanh loi khac nhau trong lop nay (loi parse OFFER/ANSWER trong
     * {@link #handlePeerList}/{@link #handleOffer}/{@link #handleAnswer}, va
     * ICE THAT SU bao FAILED trong {@link #handleIceFailed}) - truoc day 4 noi
     * nay tu lap lai y het 2 dong nay, mot 1 lan (trong {@link #handleIceFailed})
     * quen mat goi dispose() gay ro ri UDP socket that (dai cong ICE rat hep -
     * xem lich su sua loi o {@link #handleIceFailed}), gop lai o day tranh lap
     * lai sai lam tuong tu trong tuong lai.
     */
    private void failConnection(String peerId, Throwable error) {
        cleanupFailedEstablisher(peerId);
        notifyConnectionFailed(peerId, error);
    }

    /** Go 1 phien ICE dang do dang (da that bai giua chung) khoi {@link #pendingEstablishers} va giai phong tai nguyen. */
    private void cleanupFailedEstablisher(String peerId) {
        IceP2pConnectionEstablisher establisher = pendingEstablishers.remove(peerId);
        if (establisher != null) {
            establisher.dispose();
        }
    }

    private void onIceConnected(String peerId, String userName, DataChannel channel) {
        pendingEstablishers.remove(peerId);

        KeyPair ecdhKeyPair = KeyExchangeService.generateKeyPair();
        PeerConnection connection = new PeerConnection(
                peerId,
                channel,
                ecdhKeyPair,
                this::dispatchEnvelope,
                () -> notifyPeerJoined(peerId));
        connection.setCustomUsername(userName);

        peers.put(peerId, connection);
        connection.sendEcdhPublicKey();
    }

    private void notifyPeerJoined(String peerId) {
        PeerConnection connection = peers.get(peerId);
        if (connection == null) {
            return; // Peer co the da roi phong ngay trong luc dang trao khoa ECDH.
        }
        Consumer<PeerConnection> handler = onPeerJoinedHandler;
        if (handler != null) {
            handler.accept(connection);
        }
    }

    private void dispatchEnvelope(PeerConnection from, Envelope envelope) {
        List<BiConsumer<String, Envelope>> handlersForType = envelopeHandlers.get(envelope.type());
        if (handlersForType == null) {
            return;
        }
        for (BiConsumer<String, Envelope> handler : handlersForType) {
            handler.accept(from.getPeerId(), envelope);
        }
    }

    private void handleIceFailed(String peerId, Throwable error) {
        // ICE THAT SU bao FAILED (vd sau NAT doi xung khong xuyen qua duoc, mang
        // that su khong thong) - truoc day nhanh nay QUEN goi dispose() (khac voi
        // 3 nhanh loi kia), ro ri UDP socket that cua Agent (dai cong ICE rat
        // hep) moi lan that bai, cuoi cung can kiet ca dai cong. Da gop chung vao
        // failConnection() de tranh lap lai sai lam tuong tu.
        failConnection(peerId, error);
    }

    /** Bao loi ket noi voi 1 peer cu the ra ngoai qua {@link #onConnectionFailed} (khong lam gi neu chua ai dang ky nghe). */
    private void notifyConnectionFailed(String peerId, Throwable error) {
        BiConsumer<String, Throwable> handler = onConnectionFailedHandler;
        if (handler != null) {
            handler.accept(peerId, error);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Khong serialize duoc " + value.getClass().getSimpleName(), e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (IOException e) {
            throw new IllegalArgumentException("Khong parse duoc JSON thanh " + type.getSimpleName(), e);
        }
    }
}
