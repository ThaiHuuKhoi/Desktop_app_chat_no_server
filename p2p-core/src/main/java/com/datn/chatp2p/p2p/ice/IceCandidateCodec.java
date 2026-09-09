package com.datn.chatp2p.p2p.ice;

import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.CandidateType;
import org.ice4j.ice.Component;
import org.ice4j.ice.LocalCandidate;
import org.ice4j.ice.RemoteCandidate;

import java.util.Locale;

/**
 * Chuyen doi qua lai giua {@link LocalCandidate}/{@link RemoteCandidate} cua
 * ice4j va dang chuoi van ban dung de nhet vao {@code IceOfferPayload}/
 * {@code IceAnswerPayload.candidates} (Tai-lieu-ky-thuat.md Phan E.6.1).
 *
 * <p>Khong tu bia dinh dang rieng - dung dung dinh dang chuan RFC 5245/8839 ma
 * {@link org.ice4j.ice.Candidate#toString()} cua ice4j da tao san, vi du:
 * {@code "candidate:1 1 udp 2130706431 192.168.1.5 54321 typ host"}.
 *
 * <p><b>Gioi han da biet:</b> chua parse {@code raddr}/{@code rport} (dia chi
 * lien quan cua candidate server-reflexive/relayed) - candidate giai ma ra se
 * co {@code relatedCandidate = null}. Khong anh huong toi viec ket noi that
 * (ICE van hoat dong dung), chi thieu thong tin chan doan chi tiet cho loai
 * candidate srflx/relay.
 */
public final class IceCandidateCodec {

    private static final String CANDIDATE_PREFIX = "candidate:";
    private static final String TYPE_MARKER = "typ";

    private IceCandidateCodec() {
    }

    /** {@link LocalCandidate} -> 1 dong van ban de dua vao payload gui qua signaling. */
    public static String encode(LocalCandidate candidate) {
        return candidate.toString();
    }

    /**
     * 1 dong van ban nhan tu peer -> {@link RemoteCandidate}, san sang de
     * {@code component.addRemoteCandidate(...)}.
     *
     * @throws IllegalArgumentException neu dong van ban khong dung dinh dang
     *                                   toi thieu (RFC 5245: foundation, component-id,
     *                                   transport, priority, address, port, "typ", type).
     */
    public static RemoteCandidate decode(String candidateLine, Component parentComponent) {
        String body = candidateLine.startsWith(CANDIDATE_PREFIX)
                ? candidateLine.substring(CANDIDATE_PREFIX.length())
                : candidateLine;
        String[] tokens = body.trim().split("\\s+");
        if (tokens.length < 8 || !TYPE_MARKER.equalsIgnoreCase(tokens[6])) {
            throw new IllegalArgumentException("Dong candidate khong dung dinh dang RFC 5245: " + candidateLine);
        }

        String foundation = tokens[0];
        // tokens[1] la component-id - da biet truoc qua parentComponent, khong doc lai tu chuoi.
        Transport transport = Transport.parse(tokens[2].toLowerCase(Locale.ROOT));
        long priority = Long.parseLong(tokens[3]);
        String address = tokens[4];
        int port = Integer.parseInt(tokens[5]);
        CandidateType type = CandidateType.parse(tokens[7]);

        TransportAddress transportAddress = new TransportAddress(address, port, transport);
        return new RemoteCandidate(
                transportAddress,
                parentComponent,
                type,
                foundation,
                priority,
                null // relatedCandidate: chua ho tro raddr/rport, xem javadoc lop nay.
        );
    }
}
