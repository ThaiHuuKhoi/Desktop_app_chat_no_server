package com.datn.chatp2p.crypto;

/**
 * Bao ve private key danh tinh khi luu xuong dia (vd DPAPI cua Windows: ma hoa gan voi tai khoan
 * Windows cua nguoi dung, chi tai khoan do tren may do giai ma duoc). Module {@code crypto} chi dinh
 * nghia hop dong - cai dat cu the theo he dieu hanh nam o client.
 */
public interface KeyProtector {

    /** Ten luu vao file de biet cach giai ma, vd {@code "dpapi"}. */
    String name();

    byte[] protect(byte[] secret);

    /** @throws IllegalStateException neu khong giai ma duoc (vd file chep tu may / tai khoan khac). */
    byte[] unprotect(byte[] protectedSecret);
}
