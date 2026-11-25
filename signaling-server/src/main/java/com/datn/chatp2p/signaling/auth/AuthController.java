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
    /** Tang rong: tong so yeu cau moi phut cho ca 1 dai IPv6 /48 (xem {@link RequestRateLimiter#wideNetworkKey}). */
    static final int MAX_AUTH_REQUESTS_PER_MINUTE_PER_WIDE_NETWORK = 30;

    private final AuthService auth;
    // Bao mat: truoc day /auth/login va /auth/register khong gioi han - moi yeu cau ton 1 lan BCrypt
    // (CPU) va dang ky ghi them 1 dong users, nguoi la co the lam cham server va phinh database.
    private final RequestRateLimiter limiter = new RequestRateLimiter(MAX_AUTH_REQUESTS_PER_MINUTE, 60_000);
    private final RequestRateLimiter wideLimiter =
            new RequestRateLimiter(MAX_AUTH_REQUESTS_PER_MINUTE_PER_WIDE_NETWORK, 60_000);

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

    /**
     * Doi mat khau: can ve hop le (header Authorization) VA mat khau hien tai. Tra ve ve moi; moi ve cu cua
     * tai khoan, tren moi may, bi thu hoi.
     */
    @PostMapping("/change-password")
    public AuthResponse changePassword(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @RequestBody PasswordChange request, HttpServletRequest http) {
        requireWithinLimit(http);
        AuthService.SessionResult result = auth.changePassword(bearer(authorization),
                request.currentPassword(), request.newPassword());
        return new AuthResponse(result.token(), result.user().username(), result.expiresAt());
    }

    /** Tang /48 truoc, tang /64 (hoac IPv4) sau - xem {@link RequestRateLimiter#wideNetworkKey}. */
    private void requireWithinLimit(HttpServletRequest http) {
        String address = http.getRemoteAddr();
        String wideKey = RequestRateLimiter.wideNetworkKey(address);
        if ((wideKey != null && !wideLimiter.tryAcquire(wideKey))
                || !limiter.tryAcquire(RequestRateLimiter.networkKey(address))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Quá nhiều lần đăng nhập/đăng ký, thử lại sau 1 phút");
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.logout(bearer(authorization));
    }

    @ExceptionHandler(AuthLockedException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> locked(AuthLockedException e) {
        return org.springframework.http.ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(Math.max(1, e.retryAfter().toSeconds())))
                .body(new ErrorResponse(e.getMessage()));
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

    public record PasswordChange(String currentPassword, String newPassword) {
    }

    public record AuthResponse(String token, String username, Instant expiresAt) {
    }

    public record ErrorResponse(String error) {
    }
}
