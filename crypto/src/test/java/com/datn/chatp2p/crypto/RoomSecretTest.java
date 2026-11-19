package com.datn.chatp2p.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomSecretTest {

    private final byte[] aliceEcdh = KeyExchangeService.generateKeyPair().getPublic().getEncoded();
    private final byte[] bobEcdh = KeyExchangeService.generateKeyPair().getPublic().getEncoded();

    @Test
    void samePasswordGivesSameServerTagAndValidProofs() {
        RoomSecret alice = RoomSecret.derive("phong-a", "mat-khau");
        RoomSecret bob = RoomSecret.derive("phong-a", "mat-khau");

        assertEquals(alice.serverRoomTag(), bob.serverRoomTag());
        byte[] proof = alice.proof("alice", aliceEcdh, bobEcdh);
        assertTrue(bob.verifyProof(proof, "alice", aliceEcdh, bobEcdh));
    }

    @Test
    void wrongPasswordOrRoomGivesDifferentTagAndRejectedProof() {
        RoomSecret alice = RoomSecret.derive("phong-a", "mat-khau");
        RoomSecret mallory = RoomSecret.derive("phong-a", "doan-bua");
        RoomSecret otherRoom = RoomSecret.derive("phong-b", "mat-khau");

        assertNotEquals(alice.serverRoomTag(), mallory.serverRoomTag());
        assertNotEquals(alice.serverRoomTag(), otherRoom.serverRoomTag(), "Cung mat khau, khac phong -> khac the");
        assertFalse(alice.verifyProof(mallory.proof("mallory", bobEcdh, aliceEcdh), "mallory", bobEcdh, aliceEcdh));
    }

    @Test
    void proofIsBoundToEcdhSessionAndSender() {
        RoomSecret secret = RoomSecret.derive("phong-a", "mat-khau");
        byte[] proof = secret.proof("alice", aliceEcdh, bobEcdh);
        byte[] otherSession = KeyExchangeService.generateKeyPair().getPublic().getEncoded();

        assertFalse(secret.verifyProof(proof, "alice", aliceEcdh, otherSession), "Khong dung lai duoc o phien khac");
        assertFalse(secret.verifyProof(proof, "bob", aliceEcdh, bobEcdh), "Khong mao danh duoc ben gui khac");
        assertFalse(secret.verifyProof(proof, "alice", bobEcdh, aliceEcdh), "Dao chieu thi khong hop le");
        assertFalse(secret.verifyProof(null, "alice", aliceEcdh, bobEcdh));
    }

    @Test
    void tagIsShortHexAndPasswordRequired() {
        assertTrue(RoomSecret.derive("p", "x").serverRoomTag().matches("[0-9a-f]{24}"));
        assertThrows(IllegalArgumentException.class, () -> RoomSecret.derive("p", ""));
    }
}
