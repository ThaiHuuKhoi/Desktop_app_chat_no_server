package com.datn.chatp2p.signaling.turn;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnCredentialServiceTest {

    private static final Clock FIXED = Clock.fixed(Instant.ofEpochSecond(1_800_000_000L), ZoneOffset.UTC);

    /** RFC 2202, HMAC-SHA1 test case 2 - cung thuat toan coturn dung de kiem tra mat khau. */
    @Test
    void passwordIsBase64HmacSha1LikeCoturn() {
        TurnCredentialService service = new TurnCredentialService("Jefe", "turn.example", 3478, 3600, FIXED);

        assertEquals("7/zfauXrL6LSdBbV8YTfnCWafHk=", service.passwordFor("what do ya want for nothing?"));
    }

    @Test
    void usernameCarriesExpiryAndPasswordMatchesIt() {
        TurnCredentialService service = new TurnCredentialService("bi-mat", "165.101.47.83", 3478, 3600, FIXED);

        TurnCredentialService.TurnCredentials credentials = service.issue().orElseThrow();

        assertEquals("1800003600:chat-p2p", credentials.username(), "het han = bay gio + ttl");
        assertEquals(service.passwordFor(credentials.username()), credentials.password());
        assertEquals("165.101.47.83", credentials.host());
        assertEquals(3478, credentials.port());
        assertEquals(3600, credentials.ttlSeconds());
    }

    @Test
    void notConfiguredMeansNoTurn() {
        assertTrue(new TurnCredentialService("", "host", 3478, 3600, FIXED).issue().isEmpty());
        assertTrue(new TurnCredentialService("secret", " ", 3478, 3600, FIXED).issue().isEmpty());
    }

    @Test
    void controllerRateLimitsPerIp() {
        TurnController controller = new TurnController(new TurnCredentialService("s", "h", 3478, 60, FIXED));
        for (int i = 0; i < TurnController.MAX_REQUESTS_PER_MINUTE; i++) {
            assertTrue(controller.allow("1.2.3.4"));
        }
        assertFalse(controller.allow("1.2.3.4"));
        assertTrue(controller.allow("5.6.7.8"), "Moi IP tinh rieng");
    }
}
