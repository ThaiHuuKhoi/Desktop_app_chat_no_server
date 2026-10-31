package com.datn.chatp2p.client.session;

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
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    public AuthResult login(String serverUri, String username, String password, String publicKey) {
        return post(serverUri, "/auth/login", new Credentials(username, password, publicKey), AuthResult.class);
    }

    public void register(String serverUri, String username, String password, String publicKey) {
        post(serverUri, "/auth/register", new Credentials(username, password, publicKey), RegisterResult.class);
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
                throw new IllegalStateException("Đăng xuất thất bại");
            }
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
                throw new IllegalStateException("Không thể tải danh sách phòng");
            }
            return Arrays.asList(JSON.readValue(response.body(), RoomSummary[].class));
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
    public record RoomSummary(String roomId, int peerCount, String ownerUserName) {
        @Override
        public String toString() {
            return roomId + "  ·  " + peerCount + " người";
        }
    }
    private record RegisterResult(String token, String username, String expiresAt) {
    }
    private record ErrorResponse(String error) {
    }
}
