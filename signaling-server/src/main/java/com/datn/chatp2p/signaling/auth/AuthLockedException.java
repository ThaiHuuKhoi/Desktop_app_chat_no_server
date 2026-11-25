package com.datn.chatp2p.signaling.auth;

import java.time.Duration;

/** Tai khoan dang bi khoa tam vi sai mat khau qua nhieu lan ({@link LoginThrottle}) - HTTP 429. */
public class AuthLockedException extends AuthException {

    private final Duration retryAfter;

    public AuthLockedException(Duration retryAfter) {
        super("Sai mật khẩu quá nhiều lần, tài khoản tạm khoá. Thử lại sau "
                + describe(retryAfter) + ".");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    private static String describe(Duration duration) {
        long seconds = Math.max(1, (duration.toMillis() + 999) / 1000);
        return seconds < 60 ? seconds + " giây" : ((seconds + 59) / 60) + " phút";
    }
}
