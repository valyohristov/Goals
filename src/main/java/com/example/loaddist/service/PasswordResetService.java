package com.example.loaddist.service;

import com.example.loaddist.model.AppUser;
import com.example.loaddist.model.PasswordResetToken;
import com.example.loaddist.repository.AppUserRepository;
import com.example.loaddist.repository.PasswordResetTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class PasswordResetService {

    private static final Duration TTL = Duration.ofHours(1);
    private static final Duration RESEND_COOLDOWN = Duration.ofMinutes(2);

    private final PasswordResetTokenRepository tokenRepository;
    private final AppUserRepository appUserRepository;
    private final String sessionSecret;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(PasswordResetTokenRepository tokenRepository,
                                 AppUserRepository appUserRepository,
                                 @Value("${app.session-secret}") String sessionSecret) {
        this.tokenRepository = tokenRepository;
        this.appUserRepository = appUserRepository;
        this.sessionSecret = sessionSecret;
    }

    private String hmacToken(String plain) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec((sessionSecret + ":password_reset").getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional
    public IssueResult issueForEmployeeUser(AppUser user) {
        Instant now = Instant.now();
        List<PasswordResetToken> existing = tokenRepository.findByUser_IdAndUsedIsFalse(user.getId());
        boolean throttled = existing.stream()
                .filter(t -> t.getExpiresAt().isAfter(now))
                .anyMatch(t -> Duration.between(t.getCreatedAt(), now).compareTo(RESEND_COOLDOWN) < 0);
        if (throttled) {
            return new IssueResult(true, null, null);
        }
        tokenRepository.deleteAll(existing);
        byte[] buf = new byte[33];
        random.nextBytes(buf);
        String plain = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        PasswordResetToken row = new PasswordResetToken();
        row.setId(UUID.randomUUID().toString());
        row.setUser(user);
        row.setTokenHash(hmacToken(plain));
        row.setCreatedAt(now);
        row.setExpiresAt(now.plus(TTL));
        row.setUsed(false);
        tokenRepository.save(row);
        return new IssueResult(false, plain, row.getId());
    }

    @Transactional
    public void applyReset(String plainToken, String newPassword, org.springframework.security.crypto.password.PasswordEncoder encoder) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters.");
        }
        String hash = hmacToken(plainToken);
        Instant now = Instant.now();
        PasswordResetToken row = tokenRepository.findAll().stream()
                .filter(t -> !t.isUsed() && t.getTokenHash().equals(hash) && t.getExpiresAt().isAfter(now))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("This link is invalid or has expired."));
        AppUser target = row.getUser();
        if (!"employee".equalsIgnoreCase(target.getRole())) {
            throw new IllegalArgumentException("This link is invalid or has expired.");
        }
        target.setPasswordHash(encoder.encode(newPassword));
        appUserRepository.save(target);
        row.setUsed(true);
        row.setUsedAt(now);
        tokenRepository.save(row);
    }

    public boolean tokenValid(String plainToken) {
        if (plainToken == null || plainToken.isBlank()) return false;
        String hash = hmacToken(plainToken);
        Instant now = Instant.now();
        return tokenRepository.findAll().stream()
                .anyMatch(t -> !t.isUsed() && t.getTokenHash().equals(hash) && t.getExpiresAt().isAfter(now));
    }

    @Transactional
    public void deleteById(String tokenRowId) {
        tokenRepository.deleteById(tokenRowId);
    }

    public record IssueResult(boolean throttled, String plainToken, String tokenRowId) {}
}
