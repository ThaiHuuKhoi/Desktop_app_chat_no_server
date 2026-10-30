package com.datn.chatp2p.crypto;

import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.ECGenParameterSpec;

/**
 * Ky va xac thuc "danh tinh" cua peer bang ECDSA (curve secp256r1, SHA-256) -
 * Tai-lieu-ky-thuat.md muc E.5.1, dung JCA, khong phu thuoc thu vien ngoai.
 *
 * <p>Cap khoa danh tinh KHAC voi cap khoa ECDH cua {@link KeyExchangeService}:
 * <ul>
 *   <li>ECDH: sinh moi cho moi phien ket noi, chi de trao khoa AES.</li>
 *   <li>ECDSA (lop nay): sinh 1 lan va giu lau dai, la "danh tinh" cua nguoi
 *       dung - dung de ky ban tin {@code PEER_IDENTITY} chung minh minh la ai.</li>
 * </ul>
 *
 * <p>Thong diep duoc ky theo quy uoc cua chitchatter: {@code roomId + "_" + userId}
 * (xem {@link #challengeFor}). Co che nay chong <b>mao danh lap lai</b>, KHONG
 * chong tuyet doi MITM o lan gap dau tien (Tai-lieu-ky-thuat.md muc E.5.3).
 */
public final class IdentitySignatureService {

    private static final String KEY_ALGORITHM = "EC";
    private static final String CURVE_NAME = "secp256r1";
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

    private IdentitySignatureService() {
    }

    /** Sinh cap khoa danh tinh ECDSA moi (chi goi 1 lan, sau do luu lai de dung lau dai). */
    public static KeyPair generateIdentityKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            generator.initialize(new ECGenParameterSpec(CURVE_NAME));
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw new IllegalStateException("Khong the sinh cap khoa danh tinh ECDSA", e);
        }
    }

    /**
     * Thong diep thach thuc chuan de ky/xac thuc danh tinh trong 1 phong. Ca ben
     * ky lan ben xac thuc deu phai dung ham nay de khong lech dinh dang.
     */
    public static String challengeFor(String roomId, String userId) {
        return roomId + "_" + userId;
    }

    /** Ky {@code message} (UTF-8) bang private key danh tinh, tra ve chu ky dinh dang DER. */
    public static byte[] sign(PrivateKey identityPrivateKey, String message) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(identityPrivateKey);
            signature.update(message.getBytes(StandardCharsets.UTF_8));
            return signature.sign();
        } catch (NoSuchAlgorithmException | SignatureException e) {
            throw new IllegalStateException("Ky ECDSA that bai", e);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("Private key khong hop le de ky ECDSA", e);
        }
    }

    /**
     * Kiem tra {@code signature} co dung la chu ky cua {@code message} boi chu
     * so huu {@code identityPublicKey} hay khong.
     *
     * <p>Tra ve {@code false} (khong nem loi) ca khi chu ky sai lan khi chu ky
     * bi hong/sai dinh dang - voi ben xac thuc, ca hai truong hop deu chi co
     * nghia la "khong tin duoc peer nay".
     */
    public static boolean verify(PublicKey identityPublicKey, byte[] signature, String message) {
        try {
            Signature verifier = Signature.getInstance(SIGNATURE_ALGORITHM);
            verifier.initVerify(identityPublicKey);
            verifier.update(message.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(signature);
        } catch (SignatureException e) {
            return false;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA256withECDSA khong kha dung", e);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("Public key khong hop le de xac thuc ECDSA", e);
        }
    }

    /**
     * Giai ma public key danh tinh dang X.509 (vi du nhan qua {@code PEER_IDENTITY}).
     * Cung dinh dang EC/X.509 voi khoa ECDH nen dung lai {@link KeyExchangeService#decodePublicKey}.
     */
    public static PublicKey decodePublicKey(byte[] x509EncodedKey) {
        return KeyExchangeService.decodePublicKey(x509EncodedKey);
    }
}
