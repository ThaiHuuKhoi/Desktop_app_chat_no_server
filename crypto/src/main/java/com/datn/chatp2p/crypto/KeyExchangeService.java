package com.datn.chatp2p.crypto;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.SecretKeySpec;

/**
 * Trao khoa phien bang ECDH (curve secp256r1 / NIST P-256), dung JCA (Java
 * Cryptography Architecture) - khong phu thuoc thu vien ben ngoai, dung theo
 * De-cuong-Chat-P2P-Java.md muc 5.
 *
 * <p>Quy trinh: moi peer tu sinh 1 cap khoa ECDH bang {@link #generateKeyPair()},
 * gui public key cho doi phuong (qua signaling hoac truc tiep qua kenh P2P),
 * roi ca hai cung goi {@link #deriveSharedSecret(PrivateKey, PublicKey)} de ra
 * cung mot khoa AES-256 dung cho {@link AesGcmCipher}.
 *
 * <p>Ket noi P2P dung {@link #deriveSessionKeys}: HKDF-SHA256 tach 2 khoa rieng cho 2 chieu.
 * {@link #deriveSharedSecret} (bam SHA-256 truc tiep, 1 khoa chung) chi con giu cho test cu.
 */
public final class KeyExchangeService {

    private static final String KEY_ALGORITHM = "EC";
    private static final String CURVE_NAME = "secp256r1";
    private static final String KEY_AGREEMENT_ALGORITHM = "ECDH";
    private static final String AES_ALGORITHM = "AES";

    private KeyExchangeService() {
    }

    /** Sinh mot cap khoa ECDH moi (dung 1 lan cho moi phien / moi phong). */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            generator.initialize(new java.security.spec.ECGenParameterSpec(CURVE_NAME));
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw new IllegalStateException("Khong the sinh cap khoa ECDH", e);
        }
    }

    /**
     * Tinh shared secret ECDH tu private key cua minh va public key cua doi
     * phuong, roi bam SHA-256 de ra khoa AES-256 dung chung cho ca hai ben.
     */
    public static SecretKeySpec deriveSharedSecret(PrivateKey myPrivateKey, PublicKey peerPublicKey) {
        try {
            KeyAgreement keyAgreement = KeyAgreement.getInstance(KEY_AGREEMENT_ALGORITHM);
            keyAgreement.init(myPrivateKey);
            keyAgreement.doPhase(peerPublicKey, true);
            byte[] sharedSecret = keyAgreement.generateSecret();

            java.security.MessageDigest sha256 = java.security.MessageDigest.getInstance("SHA-256");
            byte[] aesKeyBytes = sha256.digest(sharedSecret);
            return new SecretKeySpec(aesKeyBytes, AES_ALGORITHM);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Khong the tinh shared secret ECDH", e);
        }
    }

    /**
     * Cap khoa phien RIENG cho 2 chieu cua 1 ket noi: {@code sendKey} de ma hoa goi minh gui,
     * {@code receiveKey} de giai ma goi ben kia gui. Ben kia co dung cap nay nhung DAO NGUOC.
     */
    public record SessionKeys(SecretKeySpec sendKey, SecretKeySpec receiveKey) {
    }

    /** Nhan HKDF - doi khi doi cach tao khoa de 2 phien ban khong vo tinh dung chung khoa. */
    private static final String HKDF_LABEL = "chat-p2p/v2/session-key|";

    /**
     * Tao cap khoa phien tu ECDH bang HKDF-SHA256 (RFC 5869), moi chieu 1 khoa:
     * <ul>
     *   <li>{@code IKM} = bi mat chung ECDH;</li>
     *   <li>{@code salt} = SHA-256(public key nho hon || public key lon hon) - gan khoa voi DUNG
     *       2 public key cua phien, 2 ben tinh ra cung salt du ai la "minh";</li>
     *   <li>{@code info} = nhan + SHA-256(public key cua BEN GUI) - khoa cua chieu A-&gt;B khac
     *       khoa chieu B-&gt;A, nen 2 chieu khong bao gio dung chung (khoa, nonce).</li>
     * </ul>
     */
    public static SessionKeys deriveSessionKeys(KeyPair myKeyPair, PublicKey peerPublicKey) {
        try {
            KeyAgreement keyAgreement = KeyAgreement.getInstance(KEY_AGREEMENT_ALGORITHM);
            keyAgreement.init(myKeyPair.getPrivate());
            keyAgreement.doPhase(peerPublicKey, true);
            byte[] sharedSecret = keyAgreement.generateSecret();

            byte[] mine = myKeyPair.getPublic().getEncoded();
            byte[] theirs = peerPublicKey.getEncoded();
            boolean mineFirst = java.util.Arrays.compareUnsigned(mine, theirs) < 0;
            java.security.MessageDigest sha256 = java.security.MessageDigest.getInstance("SHA-256");
            sha256.update(mineFirst ? mine : theirs);
            sha256.update(mineFirst ? theirs : mine);
            byte[] salt = sha256.digest();
            byte[] prk = Hkdf.extract(salt, sharedSecret);

            byte[] sendKey = Hkdf.expand(prk, directionInfo(mine), 32);
            byte[] receiveKey = Hkdf.expand(prk, directionInfo(theirs), 32);
            return new SessionKeys(new SecretKeySpec(sendKey, AES_ALGORITHM), new SecretKeySpec(receiveKey, AES_ALGORITHM));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Khong the tao khoa phien ECDH", e);
        }
    }

    private static byte[] directionInfo(byte[] senderPublicKey) throws NoSuchAlgorithmException {
        byte[] label = HKDF_LABEL.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] senderHash = java.security.MessageDigest.getInstance("SHA-256").digest(senderPublicKey);
        byte[] info = new byte[label.length + senderHash.length];
        System.arraycopy(label, 0, info, 0, label.length);
        System.arraycopy(senderHash, 0, info, label.length, senderHash.length);
        return info;
    }

    /** Giai ma public key da duoc encode dang X.509 (vi du nhan qua signaling) thanh {@link PublicKey}. */
    public static PublicKey decodePublicKey(byte[] x509EncodedKey) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
            return keyFactory.generatePublic(new X509EncodedKeySpec(x509EncodedKey));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalArgumentException("Public key khong hop le", e);
        }
    }
}
