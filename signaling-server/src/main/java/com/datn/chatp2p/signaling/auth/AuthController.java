package com.datn.chatp2p.signaling.auth;

import com.datn.chatp2p.signaling.limit.RequestRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@RestController
@RequestMapping("/auth")
public class AuthController {
    /** So lan dang nhap / dang ky toi da moi phut cho 1 dia chi (IPv6 gop theo /64). */
    static final int MAX_AUTH_REQUESTS_PER_MINUTE = 10;

    private final AuthService auth;
    // Bao mat: truoc day /auth/login va /auth/register khong gioi han - moi yeu cau ton 1 lan BCrypt
    // (CPU) va dang ky ghi them 1 dong users, nguoi la co the lam cham server va phinh database.
    private final RequestRateLimiter limiter = new RequestRateLimiter(MAX_AUTH_REQUESTS_PER_MINUTE, 60_000);

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public AuthResponse register(@RequestBody Credentials request, HttpServletRequest http) {
        requireWithinLimit(http);
        AuthUser user = auth.register(request.username(), request.password(), request.publicKey());
        return new AuthResponse(null, user.username(), null);
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody Credentials request, HttpServletRequest http) {
        requireWithinLimit(http);
        AuthService.SessionResult result = auth.login(request.username(), request.password(), request.publicKey());
        return new AuthResponse(result.token(), result.user().username(), result.expiresAt());
    }

    private void requireWithinLimit(HttpServletRequest http) {
        if (!limiter.tryAcquire(RequestRateLimiter.networkKey(http.getRemoteAddr()))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Quá nhiều lần đăng nhập/đăng ký, thử lại sau 1 phút");
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.logout(bearer(authorization));
    }

    @ExceptionHandler(AuthConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse conflict(AuthException e) {
        return new ErrorResponse(e.getMessage());
    }

    @ExceptionHandler(AuthException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse unauthorized(AuthException e) {
        return new ErrorResponse(e.getMessage());
    }

    public static String bearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return "";
        }
        return authorization.substring("Bearer ".length()).trim();
    }

    public record Credentials(String username, String password, String publicKey) {
    }

    public record AuthResponse(String token, String username, Instant expiresAt) {
    }

    public record ErrorResponse(String error) {
    }
}
