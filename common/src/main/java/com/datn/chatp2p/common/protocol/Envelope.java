package com.datn.chatp2p.common.protocol;

/**
 * Goi tin chung cho MOI thu di qua {@code DataChannel} sau khi ket noi P2P da
 * mo (Tai-lieu-ky-thuat.md Phan E.3.2) - thay the cho viec chi gui duoc 1 loai
 * tin nhan duy nhat nhu ban demo ban dau.
 *
 * <p>{@code payload} la JSON (dang byte[]) cua 1 record cu the tuy {@code type}
 * (vi du {@link MessagePayload} cho {@code EnvelopeType.MESSAGE}) - TRUOC KHI
 * ca {@code Envelope} nay bi ma hoa boi {@code EnvelopeCodec} (o module
 * {@code p2p-core}) roi moi goi {@code DataChannel.send()}.
 *
 * <p>Khong co truong {@code targetPeerId} nhu thiet ke goc de xuat: vi moi
 * {@code DataChannel} da la ket noi 1-1 voi dung 1 peer, gui toi ai la goi
 * dung {@code DataChannel} cua peer do - khong can loc target o tang Envelope
 * (don gian hon co che {@code target} cua Trystero, xem Tai-lieu-ky-thuat.md
 * Phan D.4/E.3.2).
 */
public record Envelope(EnvelopeType type, String namespace, long timestamp, byte[] payload) {
}
