package com.datn.chatp2p.p2p.ice;

import com.datn.chatp2p.common.channel.DataChannel;
import com.datn.chatp2p.common.signal.ice.IceAnswerPayload;
import com.datn.chatp2p.common.signal.ice.IceOfferPayload;
import com.datn.chatp2p.p2p.channel.P2pDataChannel;
import org.ice4j.TransportAddress;
import org.ice4j.ice.Agent;
import org.ice4j.ice.CandidatePair;
import org.ice4j.ice.CandidateType;
import org.ice4j.ice.Component;
import org.ice4j.ice.IceMediaStream;
import org.ice4j.ice.IceProcessingState;
import org.ice4j.ice.RemoteCandidate;
import org.ice4j.ice.harvest.StunCandidateHarvester;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Dieu phoi 1 phien thiet lap ket noi P2P that bang ice4j giua ban va DUNG 1
 * peer khac - Tai-lieu-ky-thuat.md Phan E.6.2/E.6.3. Moi peer trong phong can
 * 1 instance rieng cua lop nay (mesh N peer = N instance song song, quan ly
 * boi {@code RoomSession} - viec sau nay, chua lam trong ban dau nay).
 *
 * <p><b>Vai tro trong phong:</b> ai la nguoi <i>chu dong</i> (goi
 * {@link #createOffer()}) va ai <i>tra loi</i> (goi {@link #createAnswer}) do
 * lop goi no quyet dinh (vi du: peer vao phong sau, thay peer co san qua
 * PEER_LIST, se chu dong goi offer truoc - dung quy uoc o Tai-lieu-ky-thuat.md
 * Phan E.6.2).
 *
 * <p><b>Luong dung (ben chu dong - "offerer"):</b>
 * <pre>
 *   var establisher = new IceP2pConnectionEstablisher(stunServers);
 *   establisher.onConnected(channel -> ...);
 *   establisher.onFailed(err -> ...);
 *   IceOfferPayload offer = establisher.createOffer();
 *   // ... gui offer qua SignalingClient.sendOffer(), cho ANSWER tu ve ...
 *   establisher.acceptAnswer(answerNhanDuoc);
 * </pre>
 *
 * <p><b>Luong dung (ben tra loi - "answerer"):</b>
 * <pre>
 *   var establisher = new IceP2pConnectionEstablisher(stunServers);
 *   establisher.onConnected(channel -> ...);
 *   establisher.onFailed(err -> ...);
 *   IceAnswerPayload answer = establisher.createAnswer(offerNhanDuoc);
 *   // ... gui answer qua SignalingClient.sendAnswer() ...
 * </pre>
 *
 * <p><b>Chua lam trong ban dau nay</b> (de bo sung sau khi co ket qua test that):
 * <ul>
 *   <li>TURN relay du phong (chi moi co STUN harvester) - Tai-lieu-ky-thuat.md
 *       Phan G.6 va E.6.2 co nhac toi TURN nhung chua cai dat cu the.</li>
 *   <li>Trickle ICE (gui candidate ho tro sau khi da gui offer/answer) - hien
 *       tai gather toan bo candidate xong roi moi gui 1 lan.</li>
 * </ul>
 */
public final class IceP2pConnectionEstablisher {

    /**
     * Cong UDP goi y de ice4j thu dung truoc - neu bi chiem, ice4j tu do tim
     * cong khac trong khoang [MIN_PORT, MAX_PORT]. Khac voi java.net.ServerSocket,
     * ice4j KHONG chap nhan preferredPort=0 nghia la "cong bat ky" - phai la 1
     * gia tri nam trong chinh khoang nay (da xac nhan qua loi that khi chay
     * IceP2pConnectionEstablisherTest: "preferredPort (0) must be between
     * minPort (10000) and maxPort (10100)").
     */
    private static final int PREFERRED_PORT = 10_000;
    private static final int MIN_PORT = 10_000;
    private static final int MAX_PORT = 10_100;

    private final Agent agent = new Agent();
    private final IceMediaStream mediaStream;
    private final Component component;
    /** Moc thoi gian tao ra instance nay - dung lam "diem bat dau" khi tinh {@link IceConnectionStats#establishmentMillis()}. */
    private final long constructedAtNanos = System.nanoTime();

    private volatile Consumer<DataChannel> onConnectedHandler;
    private volatile Consumer<Throwable> onFailedHandler;
    private volatile IceConnectionStats stats;

    /**
     * @param stunServers danh sach dia chi STUN server (vi du
     *                    {@code new TransportAddress("stun.l.google.com", 19302, Transport.UDP)}).
     *                    Can it nhat 1 server de tim duoc server-reflexive
     *                    candidate khi 2 peer khac mang/sau NAT.
     */
    public IceP2pConnectionEstablisher(List<TransportAddress> stunServers) {
        // Tat trickle ICE tuong minh (khong phu thuoc gia tri mac dinh cua ice4j):
        // thiet ke nay gather toan bo candidate ngay trong createComponent() roi
        // moi gui 1 lan qua signaling, xem javadoc lop nay va Tai-lieu-ky-thuat.md
        // Phan E.6.2. Neu trickling=true, STUN harvester se KHONG duoc goi ngay,
        // lam encodeLocalCandidates() thieu candidate server-reflexive.
        agent.setTrickling(false);
        for (TransportAddress stunServer : stunServers) {
            agent.addCandidateHarvester(new StunCandidateHarvester(stunServer));
        }
        this.mediaStream = agent.createMediaStream("data");
        try {
            // createComponent() tu goi gather candidate ngay (khong trickle) - xong buoc nay
            // la co du candidate cuc bo (host + server-reflexive qua STUN o tren).
            this.component = agent.createComponent(mediaStream, PREFERRED_PORT, MIN_PORT, MAX_PORT);
        } catch (IOException e) {
            // BindException (khong tim duoc cong UDP trong khoang MIN_PORT-MAX_PORT) la
            // truong hop hay gap nhat - bao loi ro rang thay vi de checked exception
            // ro ri qua constructor, dong bo voi cac lop khac trong module nay.
            throw new IllegalStateException(
                    "Khong the tao ICE Component (co the do khong con cong UDP trong " +
                            MIN_PORT + "-" + MAX_PORT + ")", e);
        }
        agent.addStateChangeListener(new AgentStateListener());
    }

    /** Goi cho callback khi ICE thiet lap thanh cong - {@code channel} da san sang de dung ngay. */
    public void onConnected(Consumer<DataChannel> handler) {
        this.onConnectedHandler = handler;
    }

    /** Goi cho callback khi ICE that bai (khong tim duoc duong truyen, ke ca qua TURN neu co cau hinh). */
    public void onFailed(Consumer<Throwable> handler) {
        this.onFailedHandler = handler;
    }

    /**
     * Ben chu dong goi ham nay dau tien: danh dau minh la "controlling agent"
     * (quy uoc ICE - 1 ben phai la controlling, ben kia la controlled) va tra
     * ve toan bo thong tin can gui cho peer kia qua signaling.
     */
    public IceOfferPayload createOffer() {
        agent.setControlling(true);
        return new IceOfferPayload(agent.getLocalUfrag(), agent.getLocalPassword(), encodeLocalCandidates());
    }

    /**
     * Ben tra loi goi ham nay khi nhan duoc offer: ap dung thong tin cua peer
     * kia, tao answer cua minh, RỒI TỰ BẮT ĐẦU connectivity establishment luon
     * (ben tra loi da co du thong tin ca 2 phia ngay tai thoi diem nay, khong
     * can cho gi them).
     */
    public IceAnswerPayload createAnswer(IceOfferPayload remoteOffer) {
        agent.setControlling(false);
        applyRemote(remoteOffer.ufrag(), remoteOffer.password(), remoteOffer.candidates());

        IceAnswerPayload answer =
                new IceAnswerPayload(agent.getLocalUfrag(), agent.getLocalPassword(), encodeLocalCandidates());

        agent.startConnectivityEstablishment();
        return answer;
    }

    /**
     * Ben chu dong goi ham nay khi nhan duoc answer tu peer kia: ap dung thong
     * tin cua ho ROI moi bat dau connectivity establishment (khac ben tra loi -
     * ben chu dong phai cho co answer moi du thong tin de bat dau).
     */
    public void acceptAnswer(IceAnswerPayload remoteAnswer) {
        applyRemote(remoteAnswer.ufrag(), remoteAnswer.password(), remoteAnswer.candidates());
        agent.startConnectivityEstablishment();
    }

    /** Giai phong toan bo tai nguyen ice4j (goi khi huy phien thiet lap ket noi, vi du peer roi phong truoc khi ICE xong). */
    public void dispose() {
        agent.free();
    }

    /**
     * So lieu do hiệu nang cua phien nay - {@link Optional#empty()} neu ICE
     * chua {@code COMPLETED} (con dang chay, da {@code FAILED}, hoac chua bat
     * dau) - Tai-lieu-ky-thuat.md Phan F.1. Doc duoc bat ky luc nao SAU khi
     * callback {@link #onConnected} da chay xong (thuong doc ngay trong chinh
     * callback do, hoac luu lai {@code this} de doc sau).
     */
    public Optional<IceConnectionStats> getStats() {
        return Optional.ofNullable(stats);
    }

    private void applyRemote(String remoteUfrag, String remotePassword, List<String> remoteCandidateLines) {
        mediaStream.setRemoteUfrag(remoteUfrag);
        mediaStream.setRemotePassword(remotePassword);
        for (String line : remoteCandidateLines) {
            RemoteCandidate remoteCandidate = IceCandidateCodec.decode(line, component);
            component.addRemoteCandidate(remoteCandidate);
        }
    }

    private List<String> encodeLocalCandidates() {
        return component.getLocalCandidates().stream()
                .map(IceCandidateCodec::encode)
                .collect(Collectors.toList());
    }

    /** Bat su kien tu Agent, chi quan tam COMPLETED (thanh cong) va FAILED (that bai han). */
    private final class AgentStateListener implements PropertyChangeListener {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (!Agent.PROPERTY_ICE_PROCESSING_STATE.equals(evt.getPropertyName())) {
                return;
            }
            IceProcessingState newState = (IceProcessingState) evt.getNewValue();

            if (newState == IceProcessingState.COMPLETED) {
                handleCompleted();
            } else if (newState == IceProcessingState.FAILED) {
                Consumer<Throwable> handler = onFailedHandler;
                if (handler != null) {
                    handler.accept(new IllegalStateException(
                            "ICE that bai - khong tim duoc duong truyen truc tiep lan qua TURN (neu co cau hinh)"));
                }
            }
        }

        private void handleCompleted() {
            try {
                CandidatePair selectedPair = component.getSelectedPair();
                DatagramSocket socket = component.getSocket();
                InetSocketAddress remoteAddress = selectedPair.getRemoteCandidate().getTransportAddress();

                long establishmentMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - constructedAtNanos);
                boolean usingRelay = isRelayed(selectedPair);
                stats = new IceConnectionStats(establishmentMillis, usingRelay);

                DataChannel channel = new P2pDataChannel(socket, remoteAddress);
                Consumer<DataChannel> handler = onConnectedHandler;
                if (handler != null) {
                    handler.accept(channel);
                }
            } catch (RuntimeException e) {
                Consumer<Throwable> failedHandler = onFailedHandler;
                if (failedHandler != null) {
                    failedHandler.accept(e);
                }
            }
        }

        /**
         * {@code true} neu 1 trong 2 phia cua candidate pair da chon la
         * {@code RELAYED_CANDIDATE} (du lieu di qua TURN) - kiem tra ca 2 phia
         * vi ke ca chi 1 ben phai dung TURN, ca ket noi van tinh la "qua relay"
         * (Tai-lieu-ky-thuat.md Phan F.1: "Ti le... phai relay").
         */
        private boolean isRelayed(CandidatePair pair) {
            return pair.getLocalCandidate().getType() == CandidateType.RELAYED_CANDIDATE
                    || pair.getRemoteCandidate().getType() == CandidateType.RELAYED_CANDIDATE;
        }
    }
}
