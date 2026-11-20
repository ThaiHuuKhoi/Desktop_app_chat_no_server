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
    void numberGroupsAreTwelveFiveDigitGroupsSameOnBothSides() {
        String alice = "A1B2 C3D4 E5F6";
        String bob = "FFEE DDCC BBAA";
        java.util.List<String> fromAlice = SafetyCode.numberGroups(SafetyCode.combine(alice, bob));
        java.util.List<String> fromBob = SafetyCode.numberGroups(SafetyCode.combine(bob, alice));

        assertEquals(fromAlice, fromBob, "hai may phai hien cung 1 ma so");
        assertEquals(12, fromAlice.size());
        assertTrue(fromAlice.stream().allMatch(group -> group.matches("\\d{5}")));
        assertNotEquals(fromAlice, SafetyCode.numberGroups(SafetyCode.combine(alice, "FFEE DDCC BBAB")),
                "doi 1 ky tu van tay -> ma so khac");
    }

    @Test
    void qrMatrixIsSquare() {
        boolean[][] cells = SafetyCode.qrMatrix(SafetyCode.combine("A".repeat(64), "B".repeat(64)));
        assertEquals(cells.length, cells[0].length);
        assertTrue(cells.length >= 21);
    }
}
