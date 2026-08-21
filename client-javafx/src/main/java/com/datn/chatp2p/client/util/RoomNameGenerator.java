package com.datn.chatp2p.client.util;

import java.security.SecureRandom;

/**
 * Sinh ten phong ngan, de nho/de doc qua dien thoai, thay vi UUID dai - phong
 * theo tinh than {@code lib/RoomNameGenerator} cua chitchatter (ten phong mac
 * dinh de chia se qua kenh rieng cho nguoi minh muon chat cung).
 */
public final class RoomNameGenerator {

    private static final String[] ADJECTIVES = {
            "nhanh", "lang-le", "bi-mat", "an-toan", "trong-suot",
            "vung-chac", "linh-hoat", "am-tham", "sang-suot", "kien-dinh"
    };

    private static final String[] NOUNS = {
            "cho", "phong", "kenh", "cau", "tram", "dao", "ho", "rung", "nui", "song"
    };

    private static final SecureRandom RANDOM = new SecureRandom();

    private RoomNameGenerator() {
    }

    /** Vi du: {@code an-toan-cho-4821}. */
    public static String generate() {
        String adjective = ADJECTIVES[RANDOM.nextInt(ADJECTIVES.length)];
        String noun = NOUNS[RANDOM.nextInt(NOUNS.length)];
        int suffix = 1000 + RANDOM.nextInt(9000);
        return "%s-%s-%d".formatted(adjective, noun, suffix);
    }
}
