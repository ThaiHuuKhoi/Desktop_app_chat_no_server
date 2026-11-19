package com.datn.chatp2p.client.util;

import com.datn.chatp2p.crypto.KeyProtector;
import com.sun.jna.platform.win32.Crypt32Util;

import java.util.Locale;
import java.util.logging.Logger;

/**
 * Bao ve khoa danh tinh bang DPAPI cua Windows ({@code CryptProtectData}): khoa ma hoa gan voi
 * tai khoan Windows dang dang nhap - chi tai khoan do, tren may do, giai ma duoc. Nguoi dung khong
 * phai nhap them mat khau. Chep file {@code identity.properties} sang may khac la vo dung.
 */
public final class DpapiKeyProtector implements KeyProtector {

    private static final Logger LOG = Logger.getLogger(DpapiKeyProtector.class.getName());

    /** DPAPI tren Windows; he dieu hanh khac (hoac loi nap JNA) thi null - khoa luu dang thuong nhu cu. */
    public static KeyProtector forCurrentPlatform() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return null;
        }
        try {
            DpapiKeyProtector protector = new DpapiKeyProtector();
            byte[] probe = {1, 2, 3};
            if (java.util.Arrays.equals(probe, protector.unprotect(protector.protect(probe)))) {
                return protector;
            }
        } catch (RuntimeException | LinkageError e) {
            LOG.warning(() -> "Khong dung duoc DPAPI, khoa danh tinh se luu dang thuong: " + e.getMessage());
        }
        return null;
    }

    @Override
    public String name() {
        return "dpapi";
    }

    @Override
    public byte[] protect(byte[] secret) {
        return Crypt32Util.cryptProtectData(secret);
    }

    @Override
    public byte[] unprotect(byte[] protectedSecret) {
        try {
            return Crypt32Util.cryptUnprotectData(protectedSecret);
        } catch (RuntimeException e) {
            throw new IllegalStateException("DPAPI khong giai ma duoc: " + e.getMessage(), e);
        }
    }
}
