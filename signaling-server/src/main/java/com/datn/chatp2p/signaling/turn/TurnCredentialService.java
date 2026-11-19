package com.datn.chatp2p.signaling.turn;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;

/**
 * Cap tai khoan TURN TAM THOI theo co che "TURN REST API" cua coturn ({@code use-auth-secret}):
 * <ul>
 *   <li>{@code username = <thoi diem het han (giay Unix)>:chat-p2p}</li>
 *   <li>{@code password = Base64(HMAC-SHA1(static-auth-secret, username))}</li>
 * </ul>
 * coturn tu tinh lai bang cung {@code static-auth-secret} de kiem tra va tu choi khi da qua han -
 * khong can luu danh sach nguoi dung. Nguoi dung khong phai cau hinh gi; lo ra thi cung het han sau
 * {@link #ttlSeconds} giay.
 */
@Service
public class TurnCredentialService {

    /** Tai khoan tam thoi tra ve cho client. */
    public record TurnCredentials(String host, int port, String username, String password, long ttlSeconds) {
    }

    private final String secret;
    private final String host;
    private final int port;
    private final long ttlSeconds;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public TurnCredentialService(@Value("${chat-p2p.turn.secret:}") String secret,
                                 @Value("${chat-p2p.turn.host:}") String host,
                                 @Value("${chat-p2p.turn.port:3478}") int port,
                                 @Value("${chat-p2p.turn.ttl-seconds:21600}") long ttlSeconds) {
        this(secret, host, port, ttlSeconds, Clock.systemUTC());
    }

    TurnCredentialService(String secret, String host, int port, long ttlSeconds, Clock clock) {
        this.secret = secret == null ? "" : secret.trim();
        this.host = host == null ? "" : host.trim();
        this.port = port;
        this.ttlSeconds = ttlSeconds;
        this.clock = clock;
    }

    /** Rong neu server khong cau hinh TURN (thieu secret hoac host). */
    public Optional<TurnCredentials> issue() {
        if (secret.isEmpty() || host.isEmpty()) {
            return Optional.empty();
        }
        long expiresAt = clock.instant().getEpochSecond() + ttlSeconds;
        String username = expiresAt + ":chat-p2p";
        return Optional.of(new TurnCredentials(host, port, username, passwordFor(username), ttlSeconds));
    }

    String passwordFor(String username) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(username.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 khong kha dung", e);
        }
    }
}
