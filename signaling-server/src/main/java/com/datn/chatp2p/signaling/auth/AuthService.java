package com.datn.chatp2p.signaling.auth;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class AuthService {

    private static final Duration SESSION_LIFETIME = Duration.ofHours(12);
    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    public AuthService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AuthUser register(String username, String password, String publicKey) {
        validateCredentials(username, password);
        try {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                var statement = connection.prepareStatement(
                        "INSERT INTO users (username, password_hash, public_key) VALUES (?, ?, ?)",
                        java.sql.Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, username);
                statement.setString(2, passwordEncoder.encode(password));
                statement.setString(3, publicKey);
                return statement;
            }, keyHolder);
            Number generatedId = keyHolder.getKey();
            if (generatedId == null) {
                throw new IllegalStateException("Không nhận được id tài khoản mới");
            }
            long id = generatedId.longValue();
            return new AuthUser(id, username, publicKey);
        } catch (DuplicateKeyException e) {
            throw new AuthConflictException("Tên tài khoản đã tồn tại");
        }
    }

    public SessionResult login(String username, String password, String publicKey) {
        UserRow user = jdbc.query(
                "SELECT id, username, password_hash, public_key FROM users WHERE username = ?",
                rs -> rs.next()
                        ? new UserRow(rs.getLong("id"), rs.getString("username"),
                        rs.getString("password_hash"), rs.getString("public_key"))
                        : null,
                username);
        if (user == null || !passwordEncoder.matches(password, user.passwordHash)) {
            throw new AuthException("Tên tài khoản hoặc mật khẩu không đúng");
        }
        if (publicKey != null && !publicKey.isBlank()) {
            if (user.publicKey != null && !MessageDigest.isEqual(
                    user.publicKey.getBytes(StandardCharsets.UTF_8),
                    publicKey.getBytes(StandardCharsets.UTF_8))) {
                throw new AuthException("Khóa identity không khớp tài khoản này");
            }
            if (user.publicKey == null) {
                jdbc.update("UPDATE users SET public_key = ? WHERE id = ?", publicKey, user.id);
            }
        }
        String token = newToken();
        jdbc.update("INSERT INTO sessions (user_id, token_hash, expires_at) VALUES (?, ?, ?)",
                user.id, hash(token), java.sql.Timestamp.from(Instant.now().plus(SESSION_LIFETIME)));
        return new SessionResult(token, new AuthUser(user.id, user.username,
                user.publicKey == null ? publicKey : user.publicKey),
                Instant.now().plus(SESSION_LIFETIME));
    }

    public void logout(String token) {
        jdbc.update("UPDATE sessions SET revoked_at = CURRENT_TIMESTAMP WHERE token_hash = ?",
                hash(token));
    }

    public Optional<AuthUser> authenticate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return jdbc.query(
                """
                SELECT u.id, u.username, u.public_key
                FROM sessions s JOIN users u ON u.id = s.user_id
                WHERE s.token_hash = ? AND s.revoked_at IS NULL AND s.expires_at > CURRENT_TIMESTAMP
                """,
                rs -> rs.next()
                        ? Optional.of(new AuthUser(rs.getLong("id"), rs.getString("username"),
                        rs.getString("public_key")))
                        : Optional.empty(),
                hash(token));
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return "v1_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Không thể băm session token", e);
        }
    }

    private static void validateCredentials(String username, String password) {
        if (username == null || !username.matches("[A-Za-z0-9_]{3,64}")) {
            throw new AuthException("Tên tài khoản phải dài 3-64 ký tự, chỉ gồm chữ, số và _");
        }
        if (password == null || password.length() < 8 || password.length() > 200) {
            throw new AuthException("Mật khẩu phải dài từ 8 đến 200 ký tự");
        }
    }

    private record UserRow(long id, String username, String passwordHash, String publicKey) {
    }

    public record SessionResult(String token, AuthUser user, Instant expiresAt) {
    }
}
