package com.datn.chatp2p.client.session;

import com.datn.chatp2p.common.log.SecretLogging;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/** HTTP client for the signaling server account API. */
public final class AuthClient {
    /** Bo qua truong la tu server (server moi hon client van doc duoc). */
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(AuthClient.class.getName());
    private final HttpClient http = HttpClient.newHttpClient();

    // Mat khau / token chi hien that khi bat che do log bi mat (SecretLogging), mac dinh la "***".

    public AuthResult login(String serverUri, String username, String password, String publicKey) {
        LOG.info(() -> "Dang nhap tai khoan '" + username + "', mat khau=" + SecretLogging.reveal(password)
                + " qua " + httpBase(serverUri));
        try {
            AuthResult result = post(serverUri, "/auth/login", new Credentials(username, password, publicKey), AuthResult.class);
            LOG.info(() -> "Dang nhap thanh cong: '" + result.username() + "', token=" + SecretLogging.reveal(result.token())
                    + ", het han luc " + result.expiresAt());
            return result;
        } catch (RuntimeException e) {
            LOG.warning(() -> "Dang nhap '" + username + "' that bai: " + e.getMessage());
            throw e;
        }
    }

    public void register(String serverUri, String username, String password, String publicKey) {
        LOG.info(() -> "Dang ky tai khoan '" + username + "', mat khau=" + SecretLogging.reveal(password)
                + " qua " + httpBase(serverUri));
        try {
            post(serverUri, "/auth/register", new Credentials(username, password, publicKey), RegisterResult.class);
            LOG.info(() -> "Dang ky thanh cong: '" + username + "'");
        } catch (RuntimeException e) {
            LOG.warning(() -> "Dang ky '" + username + "' that bai: " + e.getMessage());
            throw e;
        }
    }

    public void logout(String serverUri, String token) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(httpBase(serverUri) + "/auth/logout"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                LOG.warning("Dang xuat that bai: HTTP " + response.statusCode());
                throw new IllegalStateException("Đăng xuất thất bại");
            }
            LOG.info(() -> "Da dang xuat, token " + SecretLogging.reveal(token) + " bi thu hoi tren server");
        } catch (IOException e) {
            throw new IllegalStateException("Không thể kết nối auth server", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Yêu cầu đăng xuất bị gián đoạn", e);
        }
    }

    public List<RoomSummary> rooms(String serverUri, String token) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(httpBase(serverUri) + "/rooms"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + token)
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                LOG.warning("Tai danh sach phong that bai: HTTP " + response.statusCode());
                throw new IllegalStateException("Không thể tải danh sách phòng");
            }
            List<RoomSummary> result = Arrays.stream(JSON.readValue(response.body(), RoomSummary[].class))
                    .map(RoomSummary::normalized)
                    .toList();
            LOG.info(() -> "Tai danh sach phong: " + result.size() + " phong dang hoat dong");
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Không thể tải danh sách phòng", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Yêu cầu danh sách phòng bị gián đoạn", e);
        }
    }

    private <T> T post(String serverUri, String path, Object body, Class<T> responseType) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(httpBase(serverUri) + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                ErrorResponse error = JSON.readValue(response.body(), ErrorResponse.class);
                throw new IllegalStateException(error.error() == null ? "Yêu cầu xác thực thất bại" : error.error());
            }
            return JSON.readValue(response.body(), responseType);
        } catch (IOException e) {
            throw new IllegalStateException("Không thể kết nối auth server", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Yêu cầu xác thực bị gián đoạn", e);
        }
    }

    private static String httpBase(String serverUri) {
        String base = serverUri.trim();
        if (base.startsWith("ws://")) return "http://" + base.substring(5);
        if (base.startsWith("wss://")) return "https://" + base.substring(6);
        return base;
    }

    private record Credentials(String username, String password, String publicKey) {
    }
    public record AuthResult(String token, String username, String expiresAt) {
    }
    /**
     * 1 phong dang co nguoi.
     *
     * @param roomId            ten phong de hien thi va de vao (khong kem "the" mat khau).
     * @param passwordProtected phong co mat khau - phai hoi mat khau truoc khi vao.
     */
    public record RoomSummary(String roomId, int peerCount, String ownerUserName, boolean passwordProtected) {

        /**
         * Server cu (truoc khi co truong passwordProtected) tra nguyen roomId "ten~the" cua phong co
         * mat khau - tach ra o day de giao dien chi thay ten + co mat khau, khong dung toi "the".
         */
        public RoomSummary normalized() {
            int separator = roomId == null ? -1 : roomId.indexOf(RoomConfig.PRIVATE_ROOM_SEPARATOR);
            return separator < 0
                    ? this
                    : new RoomSummary(roomId.substring(0, separator), peerCount, ownerUserName, true);
        }

        @Override
        public String toString() {
            return roomId + "  ·  " + peerCount + " người" + (passwordProtected ? "  ·  có mật khẩu" : "");
        }
    }
    private record RegisterResult(String token, String username, String expiresAt) {
    }
    private record ErrorResponse(String error) {
    }
}
