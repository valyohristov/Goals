package com.example.loaddist.service;

import com.example.loaddist.model.NotificationSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GraphMailService {

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void sendMail(NotificationSettings s, String to, String subject, String text, String html) throws Exception {
        if (s.getSenderUser() == null || s.getSenderUser().isBlank()) {
            throw new IllegalStateException("Sender user/email (UPN) is not configured");
        }
        String token = fetchToken(s);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("subject", subject != null ? subject.substring(0, Math.min(500, subject.length())) : "");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contentType", html != null && !html.isBlank() ? "HTML" : "Text");
        body.put("content", html != null && !html.isBlank() ? html : (text != null ? text : ""));
        message.put("body", body);
        message.put("toRecipients", java.util.List.of(Map.of("emailAddress", Map.of("address", to.trim()))));
        if (s.getFromEmail() != null && !s.getFromEmail().isBlank()) {
            message.put("from", Map.of("emailAddress", Map.of("address", s.getFromEmail(), "name", s.getFromName() != null ? s.getFromName() : "Goals")));
        }
        Map<String, Object> payload = Map.of("message", message, "saveToSentItems", true);
        String json = objectMapper.writeValueAsString(payload);
        String encSender = URLEncoder.encode(s.getSenderUser(), StandardCharsets.UTF_8).replace("+", "%20");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://graph.microsoft.com/v1.0/users/" + encSender + "/sendMail"))
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException("Failed to send email via Microsoft Graph: " + resp.body());
        }
    }

    private String fetchToken(NotificationSettings s) throws Exception {
        if (s.getTenantId() == null || s.getTenantId().isBlank()
                || s.getClientId() == null || s.getClientId().isBlank()
                || s.getClientSecret() == null || s.getClientSecret().isBlank()) {
            throw new IllegalStateException("Microsoft Graph credentials are not configured");
        }
        String form = Map.of(
                "client_id", s.getClientId(),
                "scope", "https://graph.microsoft.com/.default",
                "client_secret", s.getClientSecret(),
                "grant_type", "client_credentials"
        ).entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://login.microsoftonline.com/" + s.getTenantId() + "/oauth2/v2.0/token"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException("Failed to obtain access token for Microsoft Graph");
        }
        JsonNode node = objectMapper.readTree(resp.body());
        if (!node.hasNonNull("access_token")) {
            throw new IllegalStateException("Access token missing in response from Microsoft identity platform");
        }
        return node.get("access_token").asText();
    }

    public boolean isConfigured(NotificationSettings s) {
        return s != null
                && notBlank(s.getTenantId())
                && notBlank(s.getClientId())
                && notBlank(s.getClientSecret())
                && notBlank(s.getSenderUser())
                && notBlank(s.getFromEmail());
    }

    private static boolean notBlank(String x) {
        return x != null && !x.isBlank();
    }
}
