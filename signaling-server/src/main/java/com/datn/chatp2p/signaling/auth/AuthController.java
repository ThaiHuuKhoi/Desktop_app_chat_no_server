package com.datn.chatp2p.signaling.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public AuthResponse register(@RequestBody Credentials request) {
        AuthUser user = auth.register(request.username(), request.password(), request.publicKey());
        return new AuthResponse(null, user.username(), null);
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody Credentials request) {
        AuthService.SessionResult result = auth.login(request.username(), request.password(), request.publicKey());
        return new AuthResponse(result.token(), result.user().username(), result.expiresAt());
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
