package com.datn.chatp2p.signaling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Signaling server toi gian: chi giup cac peer "tim thay nhau" trong cung mot
 * phong va relay SDP/ICE candidate giua ho - khong bao gio doc hay luu noi
 * dung chat (De-cuong-Chat-P2P-Java.md muc 6, nguyen tac thiet ke cot loi).
 */
@SpringBootApplication
public class SignalingServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SignalingServerApplication.class, args);
    }
}
