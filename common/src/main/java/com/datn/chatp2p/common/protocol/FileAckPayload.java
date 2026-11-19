package com.datn.chatp2p.common.protocol;

import java.util.List;

/**
 * Payload cua {@link EnvelopeType#FILE_ACK} - nguoi nhan bao cac chunk da nhan
 * (docs/Thong-nhat-giao-thuc-truyen-file.md D4).
 *
 * @param contiguousUpTo so chunk lien tiep tu 0 da nhan: moi chunk co
 *                       {@code index < contiguousUpTo} deu da co.
 * @param receivedAbove  cac chunk nhan le phia tren {@code contiguousUpTo}
 *                       (toi da 64 phan tu) - ben gui khong gui lai nhung chunk nay.
 * @param verified       {@code true} chi trong ACK cuoi cung, sau khi nguoi nhan
 *                       da kiem SHA-256 ca file khop - ben gui chi coi la gui
 *                       thanh cong khi nhan duoc ACK nay.
 */
public record FileAckPayload(String fileId, int contiguousUpTo, List<Integer> receivedAbove, boolean verified) {
}
