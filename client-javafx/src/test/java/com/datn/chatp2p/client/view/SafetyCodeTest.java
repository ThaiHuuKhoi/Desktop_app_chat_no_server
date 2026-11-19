package com.datn.chatp2p.client.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafetyCodeTest {

    @Test
    void bothSidesGetTheSameCodeAndQr() {
        String alice = "A1B2 C3D4 E5F6";
        String bob = "FFEE DDCC BBAA";

        assertEquals(SafetyCode.combine(alice, bob), SafetyCode.combine(bob, alice));
        assertTrue(java.util.Arrays.deepEquals(
                SafetyCode.qrMatrix(SafetyCode.combine(alice, bob)),
                SafetyCode.qrMatrix(SafetyCode.combine(bob, alice))));
    }

    @Test
    void differentKeysGiveDifferentCodes() {
        assertNotEquals(SafetyCode.combine("AAAA", "BBBB"), SafetyCode.combine("AAAA", "CCCC"));
    }

    @Test
    void qrMatrixIsSquare() {
        boolean[][] cells = SafetyCode.qrMatrix(SafetyCode.combine("A".repeat(64), "B".repeat(64)));
        assertEquals(cells.length, cells[0].length);
        assertTrue(cells.length >= 21);
    }
}
