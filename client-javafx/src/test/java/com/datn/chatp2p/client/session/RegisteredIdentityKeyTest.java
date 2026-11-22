package com.datn.chatp2p.client.session;

import com.datn.chatp2p.crypto.IdentitySignatureService;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Doi chieu khoa trong PEER_IDENTITY voi khoa server bao la da dang ky cho tai khoan
 * (Co-che-xac-thuc-danh-tinh.md Buoc 7).
 */
class RegisteredIdentityKeyTest {

    private final String aliceKey = encode(IdentitySignatureService.generateIdentityKeyPair().getPublic().getEncoded());
    private final String malloryKey = encode(IdentitySignatureService.generateIdentityKeyPair().getPublic().getEncoded());

    @Test
    void accountWithTheRegisteredKeyIsAccepted() {
        assertTrue(P2pChatSession.matchesRegisteredKey(true, aliceKey, aliceKey));
        // Khac dinh dang chuoi (khoang trang) nhung cung byte van la cung khoa
        assertTrue(P2pChatSession.matchesRegisteredKey(true, " " + aliceKey + "\n", aliceKey));
    }

    @Test
    void accountPresentingAnotherKeyIsRejected() {
        assertFalse(P2pChatSession.matchesRegisteredKey(true, aliceKey, malloryKey));
        assertFalse(P2pChatSession.matchesRegisteredKey(true, aliceKey, null));
        assertFalse(P2pChatSession.matchesRegisteredKey(true, aliceKey, "khong-phai-base64!!"));
    }

    @Test
    void nothingToCompareMeansNoExtraCheck() {
        // Khach, server cu (null), hoac tai khoan chua gan khoa: chi dua vao chu ky + so van tay nhu truoc
        assertTrue(P2pChatSession.matchesRegisteredKey(false, aliceKey, malloryKey));
        assertTrue(P2pChatSession.matchesRegisteredKey(null, aliceKey, malloryKey));
        assertTrue(P2pChatSession.matchesRegisteredKey(true, null, malloryKey));
    }

    @Test
    void accountKindFollowsWhatTheServerSaid() {
        assertEquals(SessionPeer.AccountKind.ACCOUNT, SessionPeer.AccountKind.of(true));
        assertEquals(SessionPeer.AccountKind.GUEST, SessionPeer.AccountKind.of(false));
        assertEquals(SessionPeer.AccountKind.UNKNOWN, SessionPeer.AccountKind.of(null));
    }

    private static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
