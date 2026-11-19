package com.datn.chatp2p.signaling.turn;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Map<String, Deque<Long>> requestsByIp = new ConcurrentHashMap<>();

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
        long now = System.currentTimeMillis();
        Deque<Long> times = requestsByIp.computeIfAbsent(ip, ignored -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() <= now - WINDOW_MILLIS) {
                times.removeFirst();
            }
            if (times.size() >= MAX_REQUESTS_PER_MINUTE) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }
}
