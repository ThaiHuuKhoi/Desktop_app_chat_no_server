package com.datn.chatp2p.client.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomNameGeneratorTest {

    @Test
    void generatesANonEmptyLowercaseHyphenatedName() {
        String name = RoomNameGenerator.generate();

        assertTrue(name.matches("[a-z-]+-\\d{4}"), "Ten phong khong dung dinh dang: " + name);
    }
}
