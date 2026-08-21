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
 * <p><b>Luu y:</b> shared secret duoc bam SHA-256 truc tiep de lam khoa AES -
 * mot KDF don gian, du cho pham vi do an. Huong nang cap: dung HKDF (RFC 5869)
 * de tach bach khoa ma hoa / khoa xac thuc neu mo rong.
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
