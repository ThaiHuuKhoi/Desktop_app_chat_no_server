package com.datn.chatp2p.signaling.room;

import com.datn.chatp2p.signaling.auth.AuthController;
import com.datn.chatp2p.signaling.auth.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/rooms")
public class RoomController {
    private final RoomRegistry rooms;
    private final AuthService auth;

    public RoomController(RoomRegistry rooms, AuthService auth) {
        this.rooms = rooms;
        this.auth = auth;
    }

    @GetMapping
    public List<RoomRegistry.RoomSnapshot> list(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (AuthController.bearer(authorization).isBlank()
                || auth.authenticate(AuthController.bearer(authorization)).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        }
        return rooms.activeRooms();
    }
}
