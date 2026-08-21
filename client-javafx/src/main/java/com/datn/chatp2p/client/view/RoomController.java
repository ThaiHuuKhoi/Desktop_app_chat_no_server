package com.datn.chatp2p.client.view;

import com.datn.chatp2p.client.ChatApplication;
import com.datn.chatp2p.client.demo.DemoPeerSimulator;
import com.datn.chatp2p.common.channel.DataChannel;
import com.datn.chatp2p.common.model.ChatMessage;
import com.datn.chatp2p.common.model.Peer;
import com.datn.chatp2p.common.model.PeerVerificationState;
import com.datn.chatp2p.crypto.AesGcmCipher;
import com.datn.chatp2p.crypto.Fingerprint;
import com.datn.chatp2p.crypto.KeyExchangeService;
import com.datn.chatp2p.p2p.LoopbackDataChannel;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;

/**
 * Man hinh phong chat: danh sach peer, khung chat, o nhap tin nhan
 * (De-cuong-Chat-P2P-Java.md muc 5).
 *
 * <p><b>Trang thai hien tai (scaffold):</b> ket noi qua
 * {@code LoopbackDataChannel} voi mot {@link DemoPeerSimulator} noi bo, ma
 * hoa/giai ma that bang ECDH + AES-GCM (module {@code crypto}). Khi
 * {@code p2p-core}'s {@code P2pDataChannel} va {@code SignalingClient} duoc
 * cai dat that (tuan 7-9), chi can thay hai dong khoi tao {@code myChannel} /
 * {@code demoPeer} trong {@link #start} bang ket noi that toi cac peer trong
 * cung phong qua signaling-server - phan con lai (UI, ma hoa) khong doi.
 */
public class RoomController {

    @FXML
    private Label roomTitleLabel;

    @FXML
    private ListView<Peer> peerListView;

    @FXML
    private ListView<ChatMessage> transcriptListView;

    @FXML
    private TextField messageField;

    private final ObservableList<Peer> peers = FXCollections.observableArrayList();
    private final ObservableList<ChatMessage> messages = FXCollections.observableArrayList();
    private final Map<String, String> peerIdToName = new HashMap<>();

    private Stage stage;
    private String myPeerId;
    private KeyPair myKeyPair;
    private SecretKey sharedKey;
    private DataChannel myChannel;
    private DemoPeerSimulator demoPeer;

    public void start(Stage stage, String userName, String roomName) {
        this.stage = stage;
        this.myPeerId = UUID.randomUUID().toString();
        this.myKeyPair = KeyExchangeService.generateKeyPair();

        roomTitleLabel.setText("Phong: " + roomName);

        LoopbackDataChannel.Pair pair = LoopbackDataChannel.createPair();
        myChannel = pair.endpointA();
        demoPeer = new DemoPeerSimulator(pair.endpointB(), myKeyPair.getPublic());
        sharedKey = KeyExchangeService.deriveSharedSecret(myKeyPair.getPrivate(), demoPeer.getPublicKey());
        myChannel.onReceive(this::onEncryptedMessageReceived);

        Peer you = new Peer(myPeerId, userName);
        you.setPublicKeyFingerprint(Fingerprint.of(myKeyPair.getPublic()));
        you.setVerificationState(PeerVerificationState.VERIFIED);

        Peer demoPeerInfo = new Peer(demoPeer.getPeerId(), "Nguoi dung demo");
        demoPeerInfo.setPublicKeyFingerprint(Fingerprint.of(demoPeer.getPublicKey()));
        demoPeerInfo.setVerificationState(PeerVerificationState.UNVERIFIED);

        peerIdToName.put(you.getPeerId(), userName + " (ban)");
        peerIdToName.put(demoPeerInfo.getPeerId(), demoPeerInfo.getCustomUsername());

        peers.addAll(you, demoPeerInfo);
        peerListView.setItems(peers);
        peerListView.setCellFactory(list -> new PeerListCell(this::onVerifyRequested));

        transcriptListView.setItems(messages);
        transcriptListView.setCellFactory(
                list -> new MessageListCell(myPeerId, MessageListCell.nameResolver(peerIdToName)));
        messages.addListener((ListChangeListener<ChatMessage>) change ->
                transcriptListView.scrollTo(messages.size() - 1));
    }

    @FXML
    private void onSendMessage() {
        String text = messageField.getText() == null ? "" : messageField.getText().trim();
        if (text.isEmpty()) {
            return;
        }

        messages.add(new ChatMessage(myPeerId, text));
        messageField.clear();

        byte[] encrypted = AesGcmCipher.encrypt(sharedKey, text.getBytes(StandardCharsets.UTF_8));
        myChannel.send(encrypted);
    }

    private void onEncryptedMessageReceived(byte[] encrypted) {
        byte[] decrypted = AesGcmCipher.decrypt(sharedKey, encrypted);
        String text = new String(decrypted, StandardCharsets.UTF_8);

        Platform.runLater(() -> {
            ChatMessage message = new ChatMessage(demoPeer.getPeerId(), text);
            message.markReceived();
            messages.add(message);
        });
    }

    private void onVerifyRequested(Peer peer) {
        String myFingerprint = Fingerprint.of(myKeyPair.getPublic());

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Xac thuc doi phuong");
        alert.setHeaderText("So sanh van tay khoa cong khai voi " + peer.getCustomUsername());
        alert.setContentText("""
                Fingerprint cua ban:
                %s

                Fingerprint cua %s:
                %s

                Hay so sanh qua mot kenh khac (goi dien, gap truc tiep...) truoc khi xac nhan.
                Bam OK neu khop.""".formatted(myFingerprint, peer.getCustomUsername(), peer.getPublicKeyFingerprint()));

        alert.showAndWait().filter(button -> button.getButtonData().isDefaultButton()).ifPresent(button -> {
            peer.setVerificationState(PeerVerificationState.VERIFIED);
            peerListView.refresh();
        });
    }

    @FXML
    private void onLeaveRoom() {
        myChannel.close();
        demoPeer.stop();

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/home.fxml"));
            Parent root = loader.load();

            HomeController controller = loader.getController();
            controller.setStage(stage);

            Scene scene = new Scene(root, 480, 360);
            scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());

            stage.setTitle(ChatApplication.APP_TITLE);
            stage.setScene(scene);
        } catch (IOException e) {
            throw new IllegalStateException("Khong the quay ve man hinh Home", e);
        }
    }
}
