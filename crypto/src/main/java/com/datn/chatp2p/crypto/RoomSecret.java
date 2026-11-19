package com.datn.chatp2p.crypto;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Bi mat cua 1 phong co mat khau - server KHONG BAO GIO biet mat khau.
 *
 * <p>Tu {@code (roomName, password)} sinh khoa chinh bang PBKDF2-HMAC-SHA256 ({@value #ITERATIONS}
 * vong, salt gan voi ten phong), roi tach 2 khoa con bang HMAC voi nhan khac nhau:
 * <ul>
 *   <li>{@link #serverRoomTag()}: gan vao roomId gui len signaling server
 *       ({@code tenPhong~the}). Nguoi khac mat khau roi vao phong KHAC tren server, khong thay
 *       nhau. The chi la 1 gia tri bam - server muon do mat khau phai brute-force PBKDF2.</li>
 *   <li>{@link #proof}: sau khi trao khoa ECDH, moi ben chung minh voi ben kia la biet mat khau
 *       bang HMAC gan voi khoa ECDH cua CA 2 ben trong phien - phong khi server gian lan ghep 1
 *       nguoi la (khong co mat khau) vao phong. Gan voi khoa ECDH nen khong the dem bang chung
 *       cua phien nay di dung lai o phien khac.</li>
 * </ul>
 */
public final class RoomSecret {

    static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final int TAG_HEX_CHARS = 24;

    private final String roomName;
    private final byte[] tagKey;
    private final byte[] proofKey;

    private RoomSecret(String roomName, byte[] master) {
        this.roomName = roomName;
        this.tagKey = hmac(master, "chat-p2p-room-tag-v1");
        this.proofKey = hmac(master, "chat-p2p-room-proof-v1");
        Arrays.fill(master, (byte) 0);
    }

    /** Ton ~0,1-0,3 s (PBKDF2) - goi ngoai JavaFX thread neu co the. */
    public static RoomSecret derive(String roomName, String password) {
        Objects.requireNonNull(roomName, "roomName");
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Mat khau phong rong");
        }
        char[] chars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars,
                ("chat-p2p-room-salt-v1|" + roomName).getBytes(StandardCharsets.UTF_8), ITERATIONS, KEY_BITS);
        try {
            byte[] master = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return new RoomSecret(roomName, master);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 khong kha dung", e);
        } finally {
            spec.clearPassword();
            Arrays.fill(chars, '\0');
        }
    }

    /** Doan hex gan vao roomId tren server, vd {@code "phong-a~3f9c..."}. */
    public String serverRoomTag() {
        return HexFormat.of().formatHex(tagKey).substring(0, TAG_HEX_CHARS);
    }

    /**
     * Bang chung biet mat khau cua {@code senderPeerId} trong phien ECDH nay.
     *
     * @param senderEcdhPublicKey   khoa ECDH (X.509) cua BEN GUI bang chung.
     * @param receiverEcdhPublicKey khoa ECDH cua BEN NHAN bang chung (theo goc nhin ben gui).
     */
    public byte[] proof(String senderPeerId, byte[] senderEcdhPublicKey, byte[] receiverEcdhPublicKey) {
        String message = "chat-p2p-room-proof-v1|" + roomName + "|" + senderPeerId
                + "|" + sha256Hex(senderEcdhPublicKey) + "|" + sha256Hex(receiverEcdhPublicKey);
        return hmac(proofKey, message);
    }

    /** Kiem tra bang chung cua peer (so sanh hang thoi gian). */
    public boolean verifyProof(byte[] proof, String senderPeerId,
                               byte[] senderEcdhPublicKey, byte[] receiverEcdhPublicKey) {
        if (proof == null) {
            return false;
        }
        return MessageDigest.isEqual(proof, proof(senderPeerId, senderEcdhPublicKey, receiverEcdhPublicKey));
    }

    private static byte[] hmac(byte[] key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 khong kha dung", e);
        }
    }

    private static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 khong kha dung", e);
        }
    }
}
