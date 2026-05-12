package com.example.loaddist.service;

import com.example.loaddist.model.Employee;
import com.example.loaddist.model.NotificationSettings;
import com.example.loaddist.model.SentEmail;
import com.example.loaddist.repository.EmployeeRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
@Service
public class GoalNotificationQueueService {

    private final EmployeeRepository employeeRepository;
    private final NotificationSettingsService notificationSettingsService;
    private final SentEmailService sentEmailService;
    private final AccountService accountService;

    public GoalNotificationQueueService(EmployeeRepository employeeRepository,
                                        NotificationSettingsService notificationSettingsService,
                                        SentEmailService sentEmailService,
                                        AccountService accountService) {
        this.employeeRepository = employeeRepository;
        this.notificationSettingsService = notificationSettingsService;
        this.sentEmailService = sentEmailService;
        this.accountService = accountService;
    }

    public Map<String, Object> buildQueue(int yearInput, String weekKey, String appBaseUrl) {
        int year = GoalsService.sanitizeYear(yearInput);
        NotificationSettings settings = notificationSettingsService.get();
        Map<String, SentEmail> logByKey = sentEmailService.latestByPlannedKey();
        List<Employee> emps = employeeRepository.findAllOrderByNameIgnoreCase();
        Map<Long, Employee> byId = new HashMap<>();
        for (Employee e : emps) byId.put(e.getId(), e);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Employee emp : emps) {
            Employee mgrEmp = emp.getManager() != null ? byId.get(emp.getManager().getId()) : null;
            String empEmail = accountService.loginEmailOrEmpty(emp.getId());
            for (int periodIdx = 0; periodIdx < 6; periodIdx++) {
                String periodLabel = GoalsService.getPeriodLabels()[periodIdx];
                String periodEnd = GoalsService.getPeriodEndDescriptions()[periodIdx];
                Map<String, String> baseVars = baseVars(appBaseUrl, weekKey, year, emp, mgrEmp, periodLabel, periodEnd, settings);
                if (mgrEmp != null) {
                    String mgrPk = plannedManagerKey(year, emp.getId(), periodIdx);
                    String mgrEmail = accountService.loginEmailOrEmpty(mgrEmp.getId());
                    var t = pickManagerTemplates(settings, periodIdx);
                    String subj = render(t.subject(), baseVars);
                    SentEmail log = logByKey.get(mgrPk);
                    items.add(queueRow(mgrPk, year, emp.getId(), emp.getName(), periodIdx, periodLabel,
                            plannedSendDate(year, periodIdx, "manager_reminder", null),
                            variant(periodIdx, "manager", null), "manager", mgrEmp.getName(), mgrEmail,
                            mgrEmail == null || mgrEmail.isBlank(), null, "manager_reminder", subj, log));
                }
                for (int r = 1; r <= 3; r++) {
                    String empPk = plannedEmployeeKey(year, emp.getId(), periodIdx, r);
                    Map<String, String> vars = new HashMap<>(baseVars);
                    vars.put("reminderNumber", String.valueOf(r));
                    var t = pickEmployeeTemplates(settings, periodIdx, r);
                    String subj = render(t.subject(), vars);
                    SentEmail log = logByKey.get(empPk);
                    items.add(queueRow(empPk, year, emp.getId(), emp.getName(), periodIdx, periodLabel,
                            plannedSendDate(year, periodIdx, "employee_reminder", r),
                            variant(periodIdx, "employee", r), "employee", emp.getName(), empEmail,
                            empEmail == null || empEmail.isBlank(), r, "employee_reminder", subj, log));
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("year", year);
        out.put("weekKey", weekKey);
        out.put("notificationsEnabled", settings.isNotificationsEnabled());
        out.put("items", items);
        return out;
    }

    private record Templates(String subject, String body) {}

    private Templates pickManagerTemplates(NotificationSettings s, int periodIdx) {
        if (periodIdx == 0) {
            return new Templates(s.getManagerGoalSettingJanuarySubject(), s.getManagerGoalSettingJanuaryBody());
        }
        return new Templates(s.getManagerEmailSubject(), s.getManagerEmailBody());
    }

    private Templates pickEmployeeTemplates(NotificationSettings s, int periodIdx, int reminder) {
        if (periodIdx == 0 && reminder == 1) {
            return new Templates(s.getEmployeeGoalSettingJanuarySubject(), s.getEmployeeGoalSettingJanuaryBody());
        }
        return new Templates(s.getEmployeeEmailSubject(), s.getEmployeeEmailBody());
    }

    private static Map<String, String> baseVars(String baseUrl, String weekKey, int year, Employee emp,
                                               Employee mgrEmp, String periodLabel, String periodEnd,
                                               NotificationSettings settings) {
        Map<String, String> m = new HashMap<>();
        m.put("employeeName", emp.getName());
        m.put("managerName", mgrEmp != null ? mgrEmp.getName() : "");
        m.put("periodLabel", periodLabel);
        m.put("periodEndDescription", periodEnd);
        m.put("goalsUrl", goalsUrl(baseUrl, weekKey, year, emp.getId()));
        m.put("fromName", settings.getFromName() != null ? settings.getFromName() : "Goals");
        m.put("goalYear", String.valueOf(year));
        return m;
    }

    private static String goalsUrl(String base, String weekKey, int year, long employeeId) {
        if (base == null || base.isBlank()) return "";
        String b = base.replaceAll("/+$", "");
        StringBuilder qs = new StringBuilder();
        qs.append("year=").append(year);
        qs.append("&employeeId=").append(employeeId);
        if (weekKey != null && !weekKey.isBlank()) qs.append("&week=").append(weekKey);
        return b + "/goals?" + qs;
    }

    private static String render(String template, Map<String, String> vars) {
        String s = template != null ? template : "";
        String out = s;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("{{" + e.getKey() + "}}", e.getValue() != null ? e.getValue() : "");
        }
        return out;
    }

    public static String plannedManagerKey(int year, long empId, int periodIdx) {
        return "mgr|" + year + "|" + empId + "|" + periodIdx;
    }

    public static String plannedEmployeeKey(int year, long empId, int periodIdx, int reminder) {
        return "emp|" + year + "|" + empId + "|" + periodIdx + "|" + reminder;
    }

    private static String variant(int periodIdx, String role, Integer reminderNumber) {
        if (periodIdx != 0) return "bimonthly_check_in";
        if ("manager".equals(role)) return "january_goal_setting";
        if ("employee".equals(role) && reminderNumber != null && reminderNumber == 1) return "january_goal_setting";
        return "bimonthly_check_in";
    }

    private static String plannedSendDate(int year, int periodIdx, String kind, Integer reminderNumber) {
        int firstMonth = periodIdx * 2;
        int secondMonth = firstMonth + 1;
        java.time.LocalDate d;
        if ("manager_reminder".equals(kind)) {
            d = LocalDate.of(year, firstMonth + 1, 1);
            return d.toString();
        }
        int r = reminderNumber != null ? reminderNumber : 0;
        if (periodIdx == 5) {
            if (r == 1) d = LocalDate.of(year, 11, 8);
            else if (r == 2) d = LocalDate.of(year, 11, 22);
            else if (r == 3) d = LocalDate.of(year, 12, 5);
            else return "";
            return d.toString();
        }
        if (r == 1) d = LocalDate.of(year, firstMonth + 1, 8);
        else if (r == 2) d = LocalDate.of(year, secondMonth + 1, 1);
        else if (r == 3) {
            java.time.YearMonth ym = java.time.YearMonth.of(year, secondMonth + 1);
            d = ym.atEndOfMonth();
        } else return "";
        return d.toString();
    }

    private static Map<String, Object> queueRow(String pk, int year, long empId, String empName, int periodIdx,
                                               String periodLabel, String plannedSend, String msgVariant,
                                               String recipientRole, String recipientName, String recipientEmail,
                                               boolean missing, Integer reminder, String kind, String subject,
                                               SentEmail log) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("plannedKey", pk);
        m.put("year", year);
        m.put("employeeId", String.valueOf(empId));
        m.put("employeeName", empName);
        m.put("periodIdx", periodIdx);
        m.put("periodLabel", periodLabel);
        m.put("plannedSendDate", plannedSend);
        m.put("messageVariant", msgVariant);
        m.put("recipientRole", recipientRole);
        m.put("recipientName", recipientName);
        m.put("recipientEmail", recipientEmail);
        m.put("recipientMissing", missing);
        m.put("reminderNumber", reminder);
        m.put("kind", kind);
        m.put("subject", subject);
        if (log != null) {
            m.put("lastStatus", log.getStatus());
            m.put("lastSentAt", log.getSentAt().toString());
            m.put("lastError", "failed".equals(log.getStatus()) && log.getErrorMessage() != null ? log.getErrorMessage() : "");
        } else {
            m.put("lastStatus", "pending");
            m.put("lastSentAt", "");
            m.put("lastError", "");
        }
        return m;
    }

    public record ParsedKey(String kind, int year, long employeeId, int periodIdx, Integer reminderNumber) {}

    public static ParsedKey parsePlannedKey(String pk) {
        String[] p = pk.split("\\|");
        if (p.length == 4 && "mgr".equals(p[0])) {
            return new ParsedKey("manager_reminder", Integer.parseInt(p[1]), Long.parseLong(p[2]), Integer.parseInt(p[3]), null);
        }
        if (p.length == 5 && "emp".equals(p[0])) {
            return new ParsedKey("employee_reminder", Integer.parseInt(p[1]), Long.parseLong(p[2]),
                    Integer.parseInt(p[3]), Integer.parseInt(p[4]));
        }
        return null;
    }

    public Map<String, String> buildOutbound(ParsedKey parsed, String weekKey, String appBaseUrl) {
        Employee emp = employeeRepository.findById(parsed.employeeId()).orElseThrow();
        Employee mgr = emp.getManager();
        NotificationSettings settings = notificationSettingsService.get();
        Map<String, String> base = baseVars(appBaseUrl, weekKey, parsed.year(), emp, mgr,
                GoalsService.getPeriodLabels()[parsed.periodIdx()],
                GoalsService.getPeriodEndDescriptions()[parsed.periodIdx()],
                settings);
        if ("manager_reminder".equals(parsed.kind())) {
            if (mgr == null) throw new IllegalArgumentException("This employee has no manager on the roster");
            String to = accountService.loginEmailOrEmpty(mgr.getId());
            if (to.isBlank()) throw new IllegalArgumentException("Manager login email is missing");
            var t = pickManagerTemplates(settings, parsed.periodIdx());
            String text = render(t.body(), base);
            String subj = render(t.subject(), base);
            Map<String, String> o = new LinkedHashMap<>();
            o.put("to", to);
            o.put("subject", subj);
            o.put("text", text);
            o.put("html", plaintextToHtml(text));
            o.put("kind", "manager_reminder");
            o.put("plannedKey", plannedManagerKey(parsed.year(), emp.getId(), parsed.periodIdx()));
            return o;
        }
        String to = accountService.loginEmailOrEmpty(emp.getId());
        if (to.isBlank()) throw new IllegalArgumentException("Employee login email is missing");
        Map<String, String> vars = new HashMap<>(base);
        vars.put("reminderNumber", String.valueOf(parsed.reminderNumber()));
        var t = pickEmployeeTemplates(settings, parsed.periodIdx(), parsed.reminderNumber());
        String text = render(t.body(), vars);
        String subj = render(t.subject(), vars);
        Map<String, String> o = new LinkedHashMap<>();
        o.put("to", to);
        o.put("subject", subj);
        o.put("text", text);
        o.put("html", plaintextToHtml(text));
        o.put("kind", "employee_reminder");
        o.put("plannedKey", plannedEmployeeKey(parsed.year(), emp.getId(), parsed.periodIdx(), parsed.reminderNumber()));
        return o;
    }

    private static String plaintextToHtml(String text) {
        String esc = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<pre style=\"font-family:system-ui,sans-serif;white-space:pre-wrap\">" + esc + "</pre>";
    }
}
