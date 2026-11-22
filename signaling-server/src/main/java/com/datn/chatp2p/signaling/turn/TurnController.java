package com.datn.chatp2p.signaling.turn;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.datn.chatp2p.signaling.limit.RequestRateLimiter;

/**
 * {@code GET /turn-credentials}: cap tai khoan TURN tam thoi cho client (ca khach lan nguoi da dang
 * nhap). Gioi han {@value #MAX_REQUESTS_PER_MINUTE} lan/phut moi IP de nguoi la khong lay hang loat.
 */
@RestController
public class TurnController {

    static final int MAX_REQUESTS_PER_MINUTE = 20;
    private static final long WINDOW_MILLIS = 60_000;
    private static final Logger log = LoggerFactory.getLogger(TurnController.class);

    private final TurnCredentialService service;
    // Bao mat: ban cu dung 1 map theo IP khong bao gio xoa khoa - xoay vong dia chi (vd ca dai IPv6)
    // lam phinh bo nho server vinh vien va moi dia chi moi lai co han muc moi. RequestRateLimiter xoa
    // khoa het han, chan tong so khoa va gop IPv6 theo /64.
    private final RequestRateLimiter limiter = new RequestRateLimiter(MAX_REQUESTS_PER_MINUTE, WINDOW_MILLIS);

    public TurnController(TurnCredentialService service) {
        this.service = service;
    }

    @GetMapping("/turn-credentials")
    public TurnCredentialService.TurnCredentials credentials(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        if (!allow(ip)) {
            log.warn("Tu choi cap TURN cho {}: qua {} lan/phut", ip, MAX_REQUESTS_PER_MINUTE);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Quá nhiều yêu cầu, thử lại sau");
        }
        return service.issue().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Server không cấu hình TURN"));
    }

    boolean allow(String ip) {
        return limiter.tryAcquire(RequestRateLimiter.networkKey(ip));
    }

    /** So khoa IP dang duoc theo doi (cho test). */
    int trackedKeys() {
        return limiter.trackedKeys();
    }

    /** Xoa khoa het han ngay (cho test). */
    void evictIdle() {
        limiter.evictIdle();
    }
}
