package com.example.loaddist.service;

import com.example.loaddist.model.NotificationSettings;
import com.example.loaddist.repository.NotificationSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class NotificationSettingsService {

    private final NotificationSettingsRepository repository;

    public NotificationSettingsService(NotificationSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public NotificationSettings get() {
        return repository.findById(NotificationSettings.SINGLETON_ID).orElseGet(() ->
                repository.save(newNotificationRow()));
    }

    private static NotificationSettings newNotificationRow() {
        NotificationSettings n = new NotificationSettings();
        n.setId(NotificationSettings.SINGLETON_ID);
        applyTextDefaults(n);
        return n;
    }

    public static void applyTextDefaults(NotificationSettings s) {
        if (s.getEmployeeEmailSubject() == null || s.getEmployeeEmailSubject().isBlank()) {
            s.setEmployeeEmailSubject(NotificationDefaults.EMP_SUBJ);
        }
        if (s.getManagerEmailSubject() == null || s.getManagerEmailSubject().isBlank()) {
            s.setManagerEmailSubject(NotificationDefaults.MGR_SUBJ);
        }
        if (s.getEmployeeEmailBody() == null || s.getEmployeeEmailBody().isBlank()) {
            s.setEmployeeEmailBody(NotificationDefaults.EMP_BODY);
        }
        if (s.getManagerEmailBody() == null || s.getManagerEmailBody().isBlank()) {
            s.setManagerEmailBody(NotificationDefaults.MGR_BODY);
        }
        if (s.getManagerGoalSettingJanuarySubject() == null || s.getManagerGoalSettingJanuarySubject().isBlank()) {
            s.setManagerGoalSettingJanuarySubject(NotificationDefaults.MGR_JAN_SUBJ);
        }
        if (s.getEmployeeGoalSettingJanuarySubject() == null || s.getEmployeeGoalSettingJanuarySubject().isBlank()) {
            s.setEmployeeGoalSettingJanuarySubject(NotificationDefaults.EMP_JAN_SUBJ);
        }
        if (s.getManagerGoalSettingJanuaryBody() == null || s.getManagerGoalSettingJanuaryBody().isBlank()) {
            s.setManagerGoalSettingJanuaryBody(NotificationDefaults.MGR_JAN_BODY);
        }
        if (s.getEmployeeGoalSettingJanuaryBody() == null || s.getEmployeeGoalSettingJanuaryBody().isBlank()) {
            s.setEmployeeGoalSettingJanuaryBody(NotificationDefaults.EMP_JAN_BODY);
        }
    }

    public Map<String, Object> toApiMap(NotificationSettings s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("notificationsEnabled", s.isNotificationsEnabled());
        m.put("tenantId", s.getTenantId());
        m.put("clientId", s.getClientId());
        m.put("clientSecret", "");
        m.put("senderUser", s.getSenderUser());
        m.put("fromName", s.getFromName());
        m.put("fromEmail", s.getFromEmail());
        m.put("employeeEmailSubject", s.getEmployeeEmailSubject());
        m.put("employeeEmailBody", s.getEmployeeEmailBody());
        m.put("managerEmailSubject", s.getManagerEmailSubject());
        m.put("managerEmailBody", s.getManagerEmailBody());
        m.put("managerGoalSettingJanuarySubject", s.getManagerGoalSettingJanuarySubject());
        m.put("managerGoalSettingJanuaryBody", s.getManagerGoalSettingJanuaryBody());
        m.put("employeeGoalSettingJanuarySubject", s.getEmployeeGoalSettingJanuarySubject());
        m.put("employeeGoalSettingJanuaryBody", s.getEmployeeGoalSettingJanuaryBody());
        return m;
    }

    @Transactional
    public void ensureDefaultsPersisted() {
        NotificationSettings s = get();
        applyTextDefaults(s);
        repository.save(s);
    }

    @Transactional
    public NotificationSettings mergeFromApi(Map<String, Object> body, String clientSecretIfPresent) {
        NotificationSettings s = get();
        if (body.containsKey("notificationsEnabled")) {
            s.setNotificationsEnabled(Boolean.TRUE.equals(body.get("notificationsEnabled")));
        }
        putStr(body, "tenantId", s::setTenantId);
        putStr(body, "clientId", s::setClientId);
        if (clientSecretIfPresent != null && !clientSecretIfPresent.isBlank()) {
            s.setClientSecret(clientSecretIfPresent);
        }
        putStr(body, "senderUser", s::setSenderUser);
        putStr(body, "fromName", s::setFromName);
        putStr(body, "fromEmail", s::setFromEmail);
        putStr(body, "employeeEmailSubject", s::setEmployeeEmailSubject);
        putStr(body, "employeeEmailBody", s::setEmployeeEmailBody);
        putStr(body, "managerEmailSubject", s::setManagerEmailSubject);
        putStr(body, "managerEmailBody", s::setManagerEmailBody);
        putStr(body, "managerGoalSettingJanuarySubject", s::setManagerGoalSettingJanuarySubject);
        putStr(body, "managerGoalSettingJanuaryBody", s::setManagerGoalSettingJanuaryBody);
        putStr(body, "employeeGoalSettingJanuarySubject", s::setEmployeeGoalSettingJanuarySubject);
        putStr(body, "employeeGoalSettingJanuaryBody", s::setEmployeeGoalSettingJanuaryBody);
        applyTextDefaults(s);
        return repository.save(s);
    }

    private static void putStr(Map<String, Object> body, String key, java.util.function.Consumer<String> sink) {
        if (!body.containsKey(key)) return;
        Object v = body.get(key);
        sink.accept(v != null ? String.valueOf(v) : "");
    }
}
