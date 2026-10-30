package com.datn.chatp2p.p2p.ice;

import org.ice4j.ice.Agent;
import org.ice4j.ice.Component;
import org.ice4j.ice.IceMediaStream;
import org.ice4j.ice.RemoteCandidate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Kiem tra rieng {@link IceCandidateCodec#decode} co that su tu choi ("chiu
 * loi") cac dong candidate SAI DINH DANG hay khong - truoc gio moi chi duoc
 * dung GIAN TIEP qua cac test khac (voi dong candidate LUON hop le, tu chinh
 * ice4j sinh ra), CHUA co test nao dua thang 1 dong SAI dinh dang vao de
 * kiem chung. Quan trong vi tang Tang 1 (Signaling) da xac nhan server KHONG
 * bao gio validate noi dung {@code payload} - 1 dong candidate hong/gia mao
 * (bug o peer gui, hoac co y) hoan toan co the toi day nguyen ven.
 *
 * <p>Dung 1 {@link Component} that (tu {@code Agent}/{@code IceMediaStream}
 * that cua ice4j, giong het cach {@link IceP2pConnectionEstablisher} tu tao)
 * thay vi truyen {@code null} - de dung dung API that, tranh doan mo hanh vi
 * cua {@code RemoteCandidate} khi parent component null.
 */
class IceCandidateCodecTest {

    private Agent agent;
    private Component component;

    @BeforeEach
    void setUp() throws Exception {
        agent = new Agent();
        IceMediaStream stream = agent.createMediaStream("data");
        component = agent.createComponent(stream, 10_000, 10_000, 10_100);
    }

    @AfterEach
    void tearDown() {
        agent.free();
    }

    @Test
    void decodesAWellFormedHostCandidateLine() {
        RemoteCandidate candidate = IceCandidateCodec.decode(
                "candidate:1 1 udp 2130706431 127.0.0.1 54321 typ host", component);

        assertEquals(54321, candidate.getTransportAddress().getPort());
        assertEquals("127.0.0.1", candidate.getTransportAddress().getHostAddress());
    }

    @Test
    void decodesALineWithoutTheCandidatePrefix() {
        // IceCandidateCodec.encode() luon co tien to "candidate:" (candidate.toString()
        // cua ice4j sinh ra dung vay) - nhung decode() co tu ho tro ca truong hop
        // thieu tien to (vd 1 peer/thu vien khac gui khong kem tien to) hay khong.
        RemoteCandidate candidate = IceCandidateCodec.decode(
                "1 1 udp 2130706431 127.0.0.1 54321 typ host", component);
        assertEquals(54321, candidate.getTransportAddress().getPort());
    }

    @Test
    void rejectsAnEmptyLine() {
        assertThrows(IllegalArgumentException.class, () -> IceCandidateCodec.decode("", component));
    }

    @Test
    void rejectsALineWithTooFewTokens() {
        assertThrows(IllegalArgumentException.class, () ->
                IceCandidateCodec.decode("candidate:1 1 udp 2130706431 127.0.0.1", component));
    }

    @Test
    void rejectsALineMissingTheTypMarker() {
        // Dung 8 token nhung SAI vi tri "typ" (bug thuong gap nhat neu 1 ben tu
        // sinh chuoi candidate thu cong thay vi dung candidate.toString() cua ice4j).
        assertThrows(IllegalArgumentException.class, () ->
                IceCandidateCodec.decode("candidate:1 1 udp 2130706431 127.0.0.1 54321 XYZ host", component));
    }

    @Test
    void rejectsANonNumericPriority() {
        assertThrows(RuntimeException.class, () ->
                IceCandidateCodec.decode("candidate:1 1 udp khong-phai-so 127.0.0.1 54321 typ host", component));
    }

    @Test
    void rejectsANonNumericPort() {
        assertThrows(RuntimeException.class, () ->
                IceCandidateCodec.decode("candidate:1 1 udp 2130706431 127.0.0.1 khong-phai-so typ host", component));
    }

    @Test
    void rejectsAnUnknownCandidateType() {
        assertThrows(RuntimeException.class, () ->
                IceCandidateCodec.decode("candidate:1 1 udp 2130706431 127.0.0.1 54321 typ khong-ton-tai", component));
    }
}
