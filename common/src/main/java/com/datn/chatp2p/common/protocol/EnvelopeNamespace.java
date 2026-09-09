package com.datn.chatp2p.common.protocol;

/**
 * Gia tri hop le cho {@link Envelope#namespace()} - tuong duong
 * {@code ActionNamespace} cua chitchatter (Tai-lieu-ky-thuat.md Phan D.4):
 * phan biet tin nhan hien thi o khung chat nhom hay o tab chat rieng (DM),
 * dua tren cung 1 {@code DataChannel} vat ly (vi da la ket noi 1-1).
 *
 * <p>Chi la hang so tien dung, khong bat buoc - {@code Envelope.namespace}
 * van la {@code String} thuan de khong rang buoc cung 1 enum giua cac module.
 */
public final class EnvelopeNamespace {

    /** Tin nhan/hanh dong hien thi o khung chat chung cua ca phong. */
    public static final String GROUP = "g";

    /** Tin nhan/hanh dong chi giua minh va dung 1 peer (direct message). */
    public static final String DIRECT_MESSAGE = "dm";

    private EnvelopeNamespace() {
    }
}
