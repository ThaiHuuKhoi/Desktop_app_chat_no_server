package com.datn.chatp2p.signaling.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AuthService tren CSDL that (H2 che do MySQL, dung schema.sql cua server). */
class AuthServiceTest {

    private static final String PASSWORD = "matkhau-dung-1";

    private final LoginThrottleTest.MutableClock clock = new LoginThrottleTest.MutableClock();
    private AuthService auth;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:auth-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        auth = new AuthService(jdbc, clock);
        auth.register("an_nguyen", PASSWORD, "KHOA-AN");
    }

    @Test
    void wrongPasswordsLockTheAccountEvenFromDifferentAddresses() {
        for (int i = 0; i < LoginThrottle.FREE_FAILURES + 1; i++) {
            assertThrows(AuthException.class, () -> auth.login("an_nguyen", "sai-mat-khau", "KHOA-AN"));
        }
        AuthLockedException locked = assertThrows(AuthLockedException.class,
                () -> auth.login("an_nguyen", PASSWORD, "KHOA-AN"));
        assertEquals(LoginThrottle.FIRST_LOCK, locked.retryAfter(), "Dang bi khoa thi mat khau dung cung bi tu choi");

        clock.advance(LoginThrottle.FIRST_LOCK);
        assertTrue(auth.login("an_nguyen", PASSWORD, "KHOA-AN").token().startsWith("v1_"), "Het khoa thi vao duoc");
    }

    @Test
    void unknownUserGetsTheSameAnswerAsWrongPassword() {
        AuthException unknown = assertThrows(AuthException.class, () -> auth.login("khong_ton_tai", PASSWORD, null));
        AuthException wrong = assertThrows(AuthException.class, () -> auth.login("an_nguyen", "sai-mat-khau", null));
        assertEquals(wrong.getMessage(), unknown.getMessage());
        assertThrows(AuthException.class, () -> auth.login("an_nguyen", null, null), "Mat khau null khong lam sap server");
    }

    @Test
    void changePasswordRevokesEveryOldSessionAndIssuesANewOne() {
        String laptop = auth.login("an_nguyen", PASSWORD, "KHOA-AN").token();
        String stolen = auth.login("an_nguyen", PASSWORD, "KHOA-AN").token();

        AuthService.SessionResult changed = auth.changePassword(laptop, PASSWORD, "mat-khau-moi-2");

        assertTrue(auth.authenticate(laptop).isEmpty(), "Ve cu cua chinh may doi cung bi thu hoi");
        assertTrue(auth.authenticate(stolen).isEmpty(), "Ve bi danh cap bi thu hoi");
        assertTrue(auth.authenticate(changed.token()).isPresent(), "May doi mat khau nhan ve moi");
        assertNotEquals(laptop, changed.token());
        assertThrows(AuthException.class, () -> auth.login("an_nguyen", PASSWORD, "KHOA-AN"), "Mat khau cu het dung");
        assertTrue(auth.login("an_nguyen", "mat-khau-moi-2", "KHOA-AN").token().startsWith("v1_"));
    }

    @Test
    void changePasswordNeedsTheCurrentPasswordNotJustAToken() {
        String stolen = auth.login("an_nguyen", PASSWORD, "KHOA-AN").token();

        AuthException wrongCurrent = assertThrows(AuthException.class,
                () -> auth.changePassword(stolen, "doan-bua", "mat-khau-cua-ke-gian"));
        assertEquals("Mật khẩu hiện tại không đúng", wrongCurrent.getMessage());
        assertTrue(auth.login("an_nguyen", PASSWORD, "KHOA-AN").token().startsWith("v1_"), "Mat khau khong bi doi");

        assertThrows(AuthException.class, () -> auth.changePassword("v1_ve-gia", PASSWORD, "mat-khau-moi-2"));
        assertThrows(AuthException.class, () -> auth.changePassword(stolen, PASSWORD, "ngan"), "Mat khau moi qua ngan");
        assertThrows(AuthException.class, () -> auth.changePassword(stolen, PASSWORD, PASSWORD), "Mat khau moi trung cu");
    }

    @Test
    void guessingTheCurrentPasswordWithAStolenTokenIsThrottledToo() {
        String stolen = auth.login("an_nguyen", PASSWORD, "KHOA-AN").token();
        for (int i = 0; i < LoginThrottle.FREE_FAILURES + 1; i++) {
            assertThrows(AuthException.class, () -> auth.changePassword(stolen, "doan-bua", "mat-khau-moi-2"));
        }
        assertThrows(AuthLockedException.class, () -> auth.login("an_nguyen", PASSWORD, "KHOA-AN"),
                "Doan mat khau qua doi mat khau va qua dang nhap dung chung bo dem");
        assertTrue(new AuthLockedException(Duration.ofSeconds(90)).getMessage().contains("2 phút"));
    }
}
