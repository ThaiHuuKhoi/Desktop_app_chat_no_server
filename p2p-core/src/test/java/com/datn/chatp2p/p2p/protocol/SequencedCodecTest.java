package com.datn.chatp2p.p2p.protocol;

import com.datn.chatp2p.common.protocol.EnvelopeType;
import com.datn.chatp2p.common.protocol.MessagePayload;
import com.datn.chatp2p.crypto.KeyExchangeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Goi co so thu tu, khoa rieng moi chieu, cua so chong replay. */
class SequencedCodecTest {

    private EnvelopeCodec alice;
    private EnvelopeCodec bob;

    @BeforeEach
    void setUp() {
        KeyPair a = KeyExchangeService.generateKeyPair();
        KeyPair b = KeyExchangeService.generateKeyPair();
        alice = new EnvelopeCodec(KeyExchangeService.deriveSessionKeys(a, b.getPublic()));
        bob = new EnvelopeCodec(KeyExchangeService.deriveSessionKeys(b, a.getPublic()));
    }

    @Test
    void bothDirectionsWork() {
        assertEquals(EnvelopeType.MESSAGE, bob.decode(alice.encode(EnvelopeType.MESSAGE, message("1"))).type());
        assertEquals(EnvelopeType.MESSAGE, alice.decode(bob.encode(EnvelopeType.MESSAGE, message("2"))).type());
    }

    @Test
    void ownPacketCannotBeReflectedBack() {
        // Ke tan cong bat goi A gui roi gui nguoc lai cho chinh A: khac khoa chieu -> giai ma that bai
        byte[] packet = alice.encode(EnvelopeType.MESSAGE, message("1"));
        assertThrows(IllegalStateException.class, () -> alice.decode(packet));
    }

    @Test
    void replayIsRejectedEvenAfterManyNewerPackets() {
        byte[] first = alice.encode(EnvelopeType.MESSAGE, message("first"));
        bob.decode(first);
        for (int i = 0; i < 2000; i++) {
            bob.decode(alice.encode(EnvelopeType.MESSAGE, message("m" + i)));
        }
        // Cach cu (nho 256 van tay) da quen goi nay roi; cach moi van chan vi qua cu
        assertThrows(ReplayedPacketException.class, () -> bob.decode(first));
    }

    @Test
    void outOfOrderPacketsInsideWindowAreAccepted() {
        List<byte[]> packets = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            packets.add(alice.encode(EnvelopeType.MESSAGE, message("m" + i)));
        }
        for (int i = 9; i >= 0; i--) {
            bob.decode(packets.get(i));
        }
        assertThrows(ReplayedPacketException.class, () -> bob.decode(packets.get(3)));
    }

    @Test
    void tamperedSequenceNumberFailsAuthentication() {
        byte[] packet = alice.encode(EnvelopeType.MESSAGE, message("1"));
        packet[7] ^= 1; // doi so thu tu
        assertThrows(IllegalStateException.class, () -> bob.decode(packet));
    }

    @Test
    void forgedPacketDoesNotPoisonTheWindow() {
        byte[] real = alice.encode(EnvelopeType.MESSAGE, message("1"));
        byte[] forged = real.clone();
        java.nio.ByteBuffer.wrap(forged).putLong(1_000_000); // so thu tu rat lon, khong co khoa
        assertThrows(IllegalStateException.class, () -> bob.decode(forged));

        // Goi that so 0 van phai duoc nhan (cua so khong bi day len 1.000.000)
        assertEquals(EnvelopeType.MESSAGE, bob.decode(real).type());
    }

    @Test
    void windowUnitBehaviour() {
        ReplayWindow window = new ReplayWindow();
        assertTrue(window.isFresh(0));
        window.markSeen(0);
        assertFalse(window.isFresh(0));
        window.markSeen(5);
        assertTrue(window.isFresh(3), "So nho hon nhung chua thay va con trong cua so");
        window.markSeen(5 + ReplayWindow.SIZE);
        assertFalse(window.isFresh(3), "Da ra khoi cua so -> coi la qua cu");
        assertFalse(window.isFresh(-1));
    }

    private static MessagePayload message(String id) {
        return new MessagePayload(id, "author", "noi dung " + id, 1L);
    }
}
