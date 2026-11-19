package com.datn.chatp2p.p2p.protocol;

import com.datn.chatp2p.common.protocol.Envelope;
import com.datn.chatp2p.common.protocol.EnvelopeType;
import com.datn.chatp2p.common.protocol.FileAckPayload;
import com.datn.chatp2p.common.protocol.FileAnswerPayload;
import com.datn.chatp2p.common.protocol.FileCancelPayload;
import com.datn.chatp2p.common.protocol.FileChunkPayload;
import com.datn.chatp2p.common.protocol.FileOfferPayload;
import com.datn.chatp2p.crypto.KeyExchangeService;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cac payload truyen file (docs/Thong-nhat-giao-thuc-truyen-file.md D1) di qua
 * {@link EnvelopeCodec} that (JSON -> AES-GCM -> giai ma -> JSON) van nguyen ven.
 */
class FileTransferPayloadCodecTest {

    /** Gioi han 1 goi UDP IPv4 (65 507) tru 4 byte length-prefix cua P2pDataChannel. */
    private static final int MAX_ENCODED_BYTES = 65_507 - 4;

    private final EnvelopeCodec codec = new EnvelopeCodec(sharedKey());

    private static SecretKey sharedKey() {
        KeyPair a = KeyExchangeService.generateKeyPair();
        KeyPair b = KeyExchangeService.generateKeyPair();
        return KeyExchangeService.deriveSharedSecret(a.getPrivate(), b.getPublic());
    }

    private <T> T roundTrip(EnvelopeType type, T payload, Class<T> payloadType) {
        Envelope decoded = codec.decode(codec.encode(type, payload));
        assertEquals(type, decoded.type());
        return codec.parsePayload(decoded, payloadType);
    }

    @Test
    void fileOfferRoundTrips() {
        FileOfferPayload offer = new FileOfferPayload("file-1", "báo cáo tiến độ.pdf", 2_457_600L,
                "application/pdf", 4096, 600, "a".repeat(64));

        assertEquals(offer, roundTrip(EnvelopeType.FILE_OFFER, offer, FileOfferPayload.class));
    }

    @Test
    void fileOfferWithUnknownMimeTypeRoundTrips() {
        FileOfferPayload offer = new FileOfferPayload("file-2", "rong.bin", 0L, null, 4096, 0, "b".repeat(64));

        assertEquals(offer, roundTrip(EnvelopeType.FILE_OFFER, offer, FileOfferPayload.class));
    }

    @Test
    void fileAnswerRoundTripsForAcceptAndReject() {
        FileAnswerPayload accepted = new FileAnswerPayload("file-1", true, null);
        FileAnswerPayload rejected = new FileAnswerPayload("file-1", false, "REJECTED");

        assertEquals(accepted, roundTrip(EnvelopeType.FILE_ANSWER, accepted, FileAnswerPayload.class));
        assertEquals(rejected, roundTrip(EnvelopeType.FILE_ANSWER, rejected, FileAnswerPayload.class));
    }

    @Test
    void fileChunkKeepsEveryByteValue() {
        // Moi gia tri byte 0..255 - dam bao Base64 trong JSON khong lam hong du lieu nhi phan
        byte[] data = new byte[4096];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        FileChunkPayload chunk = new FileChunkPayload("file-1", 7, data);

        FileChunkPayload received = roundTrip(EnvelopeType.FILE_CHUNK, chunk, FileChunkPayload.class);

        assertEquals("file-1", received.fileId());
        assertEquals(7, received.index());
        assertArrayEquals(data, received.data());
    }

    @Test
    void fileAckRoundTripsWithSelectiveList() {
        FileAckPayload ack = new FileAckPayload("file-1", 12, List.of(14, 15, 20), false);
        FileAckPayload finalAck = new FileAckPayload("file-1", 600, List.of(), true);

        assertEquals(ack, roundTrip(EnvelopeType.FILE_ACK, ack, FileAckPayload.class));
        assertEquals(finalAck, roundTrip(EnvelopeType.FILE_ACK, finalAck, FileAckPayload.class));
    }

    @Test
    void fileCancelRoundTrips() {
        FileCancelPayload cancel = new FileCancelPayload("file-1", "CHECKSUM_MISMATCH");

        assertEquals(cancel, roundTrip(EnvelopeType.FILE_CANCEL, cancel, FileCancelPayload.class));
    }

    @Test
    void largestAllowedChunkStillFitsInOneUdpDatagram() {
        // D5: chunk toi da 32 KB. Du lieu ngau nhien (khong nen duoc) la truong hop xau nhat.
        byte[] data = new byte[32 * 1024];
        new Random(42).nextBytes(data);
        FileChunkPayload chunk = new FileChunkPayload("0b0e6a3c-6a1f-4bb5-9a43-5e2f0c8d7a11", 99_999, data);

        int encodedBytes = codec.encode(EnvelopeType.FILE_CHUNK, chunk).length;

        System.out.printf("Chunk %d byte -> goi ma hoa %d byte (x%.2f)%n",
                data.length, encodedBytes, (double) encodedBytes / data.length);
        assertTrue(encodedBytes <= MAX_ENCODED_BYTES,
                "Chunk 32 KB phai vua 1 goi UDP, nhung goi ma hoa la " + encodedBytes + " byte");
    }

    @Test
    void existingEnvelopeTypesKeepTheirOrderAndFileTypesAreAppended() {
        // D2: 3 gia tri moi them VAO CUOI - khong doi thu tu cac gia tri co san
        List<EnvelopeType> expected = List.of(
                EnvelopeType.MESSAGE, EnvelopeType.MESSAGE_TRANSCRIPT, EnvelopeType.TYPING_STATUS_CHANGE,
                EnvelopeType.PEER_IDENTITY, EnvelopeType.FILE_OFFER, EnvelopeType.FILE_CHUNK,
                EnvelopeType.AUDIO_CHANGE, EnvelopeType.VIDEO_CHANGE, EnvelopeType.SCREEN_SHARE_CHANGE,
                EnvelopeType.MEDIA_FRAME,
                EnvelopeType.MESSAGE_ACK, EnvelopeType.ROOM_PROOF,
                EnvelopeType.FILE_ANSWER, EnvelopeType.FILE_ACK, EnvelopeType.FILE_CANCEL,
                EnvelopeType.MESSAGE_READ, EnvelopeType.MESSAGE_REACTION, EnvelopeType.MESSAGE_EDIT);

        assertEquals(expected, List.of(EnvelopeType.values()));
    }
}
