package com.datn.chatp2p.client.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginRulesTest {

    @Test
    void recognisesKeyMismatchFromServer() {
        assertTrue(LoginRules.isKeyMismatch("Khóa identity không khớp tài khoản này"));
        assertFalse(LoginRules.isKeyMismatch("Tên tài khoản hoặc mật khẩu không đúng"));
        assertFalse(LoginRules.isKeyMismatch(null));
    }

    @Test
    void shortFingerprintKeepsFirstThreeGroups() {
        assertEquals("3F9C A1D2 7B40 …", LoginRules.shortFingerprint("3F9C A1D2 7B40 E615 0C8A"));
        assertEquals("", LoginRules.shortFingerprint(null));
        assertEquals("ABCDEF012345 …", LoginRules.shortFingerprint("ABCDEF0123456789"));
    }

    @Test
    void registrationNeedsNameLengthAndMatchingConfirmation() {
        assertEquals("Vui lòng nhập tên tài khoản.", LoginRules.validateRegistration("", "matkhau123", "matkhau123"));
        assertEquals("Mật khẩu cần ít nhất 8 ký tự.", LoginRules.validateRegistration("khoi", "ngan", "ngan"));
        assertEquals("Mật khẩu nhập lại không khớp.", LoginRules.validateRegistration("khoi", "matkhau123", "matkhau124"));
        assertNull(LoginRules.validateRegistration("khoi", "matkhau123", "matkhau123"));
    }
}
