package com.example.loaddist.api;

import com.example.loaddist.service.GoalNotificationQueueService;
import com.example.loaddist.service.GraphMailService;
import com.example.loaddist.service.NotificationSettingsService;
import com.example.loaddist.service.SentEmailService;
import com.example.loaddist.service.GoalsService;
import com.example.loaddist.model.NotificationSettings;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
public class AdminNotificationRestController {

    private final NotificationSettingsService notificationSettingsService;
    private final GraphMailService graphMailService;
    private final SentEmailService sentEmailService;
    private final GoalNotificationQueueService goalNotificationQueueService;
    private final ObjectMapper objectMapper;
    private final String appBaseUrl;

    public AdminNotificationRestController(NotificationSettingsService notificationSettingsService,
                                           GraphMailService graphMailService,
                                           SentEmailService sentEmailService,
                                           GoalNotificationQueueService goalNotificationQueueService,
                                           ObjectMapper objectMapper,
                                           @org.springframework.beans.factory.annotation.Value("${app.base-url:}") String appBaseUrl) {
        this.notificationSettingsService = notificationSettingsService;
        this.graphMailService = graphMailService;
        this.sentEmailService = sentEmailService;
        this.goalNotificationQueueService = goalNotificationQueueService;
        this.objectMapper = objectMapper;
        this.appBaseUrl = appBaseUrl != null ? appBaseUrl : "";
    }

    private static boolean isAppManager(Authentication auth) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_MANAGER".equals(a.getAuthority()));
    }

    @GetMapping("/api/admin/notification-settings")
    public ResponseEntity<?> getSettings(Authentication auth) {
        if (!isAppManager(auth)) return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        return ResponseEntity.ok(notificationSettingsService.toApiMap(notificationSettingsService.get()));
    }

    @PutMapping("/api/admin/notification-settings")
    public ResponseEntity<?> putSettings(@RequestBody JsonNode body, Authentication auth) {
        if (!isAppManager(auth)) return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        String secret = body.has("clientSecret") && !body.get("clientSecret").isNull()
                ? body.get("clientSecret").asText("") : "";
        Map<String, Object> map = objectMapper.convertValue(body, new TypeReference<>() {});
        var saved = notificationSettingsService.mergeFromApi(map, secret.isBlank() ? null : secret);
        return ResponseEntity.ok(notificationSettingsService.toApiMap(saved));
    }

    @PostMapping("/api/admin/notification-settings/test-email")
    public ResponseEntity<?> testEmail(@RequestBody JsonNode body, Authentication auth) {
        if (!isAppManager(auth)) return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        String to = body.has("to") ? body.get("to").asText("").trim() : "";
        if (to.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Recipient email is required"));
        }
        NotificationSettings settings = notificationSettingsService.get();
        if (!graphMailService.isConfigured(settings)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Microsoft Graph settings are incomplete (tenant, client ID, secret)"));
        }
        if (settings.getSenderUser() == null || settings.getSenderUser().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sender user/email (UPN) is required"));
        }
        if (settings.getFromEmail() == null || settings.getFromEmail().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "From email is required"));
        }
        String subject = "Goals — test email";
        String text = "This is a test message from the Goals app.";
        String html = "<p>This is a test message from the <strong>Goals</strong> app.</p>";
        try {
            graphMailService.sendMail(settings, to, subject, text, html);
            sentEmailService.append(to, subject, "test", "manual_test", "sent", null, "");
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Failed to send test email";
            sentEmailService.append(to, subject, "test", "manual_test", "failed", msg, "");
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        }
    }

    @GetMapping("/api/admin/sent-emails")
    public ResponseEntity<?> sentEmails(@RequestParam(defaultValue = "250") int limit, Authentication auth) {
        if (!isAppManager(auth)) return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        int lim = Math.min(500, Math.max(1, limit));
        return ResponseEntity.ok(Map.of("items", sentEmailService.recent(lim)));
    }

    @GetMapping("/api/admin/notification-queue")
    public ResponseEntity<?> queue(@RequestParam(required = false) Integer year,
                                   @RequestParam(required = false) String week,
                                   Authentication auth) {
        if (!isAppManager(auth)) return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        try {
            int y = year != null ? GoalsService.sanitizeYear(year) : GoalsService.sanitizeYear(java.time.Year.now(java.time.ZoneOffset.UTC).getValue());
            String wk = com.example.loaddist.util.RequestWeek.parseWeekParam(week).toString();
            String base = resolvePublicBaseUrl();
            return ResponseEntity.ok(goalNotificationQueueService.buildQueue(y, wk, base));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Failed to build notification queue"));
        }
    }

    @PostMapping("/api/admin/notification-queue/send")
    public ResponseEntity<?> sendPlanned(@RequestBody JsonNode body, Authentication auth) {
        if (!isAppManager(auth)) return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        String pk = body.has("plannedKey") ? body.get("plannedKey").asText("").trim() : "";
        if (pk.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "plannedKey is required"));
        }
        GoalNotificationQueueService.ParsedKey parsed = GoalNotificationQueueService.parsePlannedKey(pk);
        if (parsed == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid plannedKey"));
        }
        String weekParam = body.has("week") ? body.get("week").asText("").trim() : "";
        String weekKey = weekParam.isBlank()
                ? com.example.loaddist.util.RequestWeek.parseWeekParam(null).toString()
                : com.example.loaddist.util.RequestWeek.parseWeekParam(weekParam).toString();
        Map<String, String> outbound;
        try {
            outbound = goalNotificationQueueService.buildOutbound(parsed, weekKey, resolvePublicBaseUrl());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Could not build email"));
        }
        if (!pk.equals(outbound.get("plannedKey"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "Planned key mismatch"));
        }
        NotificationSettings settings = notificationSettingsService.get();
        if (!graphMailService.isConfigured(settings)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Microsoft Graph settings are incomplete (tenant, client ID, secret)"));
        }
        try {
            graphMailService.sendMail(settings, outbound.get("to"), outbound.get("subject"),
                    outbound.get("text"), outbound.get("html"));
            sentEmailService.append(outbound.get("to"), outbound.get("subject"), outbound.get("kind"),
                    "manual_planned", "sent", null, pk);
            return ResponseEntity.ok(Map.of("ok", true, "plannedKey", pk));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Failed to send email";
            sentEmailService.append(outbound.get("to"), outbound.get("subject"), outbound.get("kind"),
                    "manual_planned", "failed", msg, pk);
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        }
    }

    /** Prefer configured base URL; callers can pass absolute app URL in future if needed. */
    private String resolvePublicBaseUrl() {
        if (appBaseUrl != null && !appBaseUrl.isBlank()) {
            return appBaseUrl.replaceAll("/+$", "");
        }
        return "";
    }
}
