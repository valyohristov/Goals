package com.example.loaddist.service;

import com.example.loaddist.model.SentEmail;
import com.example.loaddist.repository.SentEmailRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SentEmailService {

    private final SentEmailRepository repository;

    public SentEmailService(SentEmailRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void append(String to, String subject, String kind, String trigger, String status, String error, String plannedKey) {
        SentEmail e = new SentEmail();
        e.setId(UUID.randomUUID().toString());
        e.setSentAt(Instant.now());
        e.setToAddress(to);
        e.setSubject(subject);
        e.setKind(kind);
        e.setTriggerKind(trigger);
        e.setStatus(status);
        e.setErrorMessage(error);
        e.setPlannedKey(plannedKey != null ? plannedKey : "");
        repository.save(e);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> recent(int limit) {
        return repository.findTop500ByOrderBySentAtDesc().stream().limit(Math.max(1, Math.min(500, limit)))
                .map(this::toJson)
                .collect(Collectors.toList());
    }

    private Map<String, Object> toJson(SentEmail e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("sentAt", e.getSentAt().toString());
        m.put("to", e.getToAddress());
        m.put("subject", e.getSubject());
        m.put("kind", e.getKind());
        m.put("trigger", e.getTriggerKind());
        m.put("status", e.getStatus());
        m.put("error", e.getErrorMessage() != null ? e.getErrorMessage() : "");
        m.put("plannedKey", e.getPlannedKey());
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, SentEmail> latestByPlannedKey() {
        Map<String, SentEmail> idx = new LinkedHashMap<>();
        for (SentEmail e : repository.findTop500ByOrderBySentAtDesc()) {
            String pk = e.getPlannedKey();
            if (pk != null && !pk.isBlank() && !idx.containsKey(pk)) {
                idx.put(pk.trim(), e);
            }
        }
        return idx;
    }
}
