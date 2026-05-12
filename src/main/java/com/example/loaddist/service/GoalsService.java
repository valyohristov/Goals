package com.example.loaddist.service;

import com.example.loaddist.model.*;
import com.example.loaddist.repository.EmployeeRepository;
import com.example.loaddist.repository.GoalPlanRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class GoalsService {

    private static final String[] PERIOD_LABELS = {
            "January–February · annual goals set in January",
            "March–April", "May–June", "July–August", "September–October", "November–December"
    };
    private static final String[] PERIOD_END = {
            "the end of February", "the end of April", "the end of June",
            "the end of August", "the end of October", "5 December (annual goals deadline)"
    };

    private final GoalPlanRepository goalPlanRepository;
    private final EmployeeRepository employeeRepository;

    public GoalsService(GoalPlanRepository goalPlanRepository, EmployeeRepository employeeRepository) {
        this.goalPlanRepository = goalPlanRepository;
        this.employeeRepository = employeeRepository;
    }

    public static int sanitizeYear(int y) {
        if (y < 2000 || y > 2100) return java.time.Year.now(java.time.ZoneOffset.UTC).getValue();
        return y;
    }

    public Long resolveEmployeeIdForGoals(TeamScope scope, String requestedId, List<Employee> roster) {
        if (roster.isEmpty()) return null;
        if (scope.isAppManager()) {
            if (requestedId != null && !requestedId.isBlank()) {
                try {
                    long want = Long.parseLong(requestedId.trim());
                    if (roster.stream().anyMatch(e -> e.getId().equals(want))) return want;
                } catch (NumberFormatException ignored) { }
            }
            return roster.get(0).getId();
        }
        if (scope.isPeopleManager()) {
            Set<Long> team = new HashSet<>(scope.getTeamEmployeeIds());
            if (requestedId != null && !requestedId.isBlank()) {
                try {
                    long want = Long.parseLong(requestedId.trim());
                    if (team.contains(want)) return want;
                } catch (NumberFormatException ignored) { }
            }
            Long self = scope.getSelfEmployeeId();
            if (self != null && team.contains(self)) return self;
            return roster.stream().filter(e -> team.contains(e.getId())).findFirst().map(Employee::getId).orElse(null);
        }
        Long self = scope.getSelfEmployeeId();
        if (self != null && roster.stream().anyMatch(e -> e.getId().equals(self))) return self;
        return null;
    }

    public List<Employee> rosterOrdered() {
        return employeeRepository.findAllOrderByNameIgnoreCase();
    }

    /**
     * Response when {@code ld_employees} has no rows (or goals cannot load a target). Keeps HTTP 200 so the UI can explain the situation.
     */
    public Map<String, Object> goalsPayloadWhenRosterEmpty(int year, TeamScope scope, String weekKey) {
        List<Map<String, String>> picker;
        if (scope.isAppManager()) {
            picker = List.of();
        } else if (scope.isPeopleManager()) {
            picker = List.of();
        } else {
            picker = null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("year", sanitizeYear(year));
        out.put("weekKey", weekKey);
        out.put("employeeId", "");
        out.put("employeeName", "");
        out.put("goals", List.of());
        out.put("canEdit", false);
        out.put("canEditEmployeeCheckIns", false);
        out.put("canEditManagerCheckIns", false);
        out.put("canDeleteCheckIns", false);
        out.put("employeePicker", picker);
        out.put("rosterEmpty", true);
        out.put("hint", "There are no employees in the database yet, so Year goals cannot load anyone. "
                + "As an app admin, open Manage Employees and add people (or ensure Flyway migration V2 ran so seed data is loaded).");
        return out;
    }

    @Transactional
    public Map<String, Object> getGoalsApi(int year, Long employeeId, TeamScope scope, String weekKey) {
        GoalPlan plan = loadOrCreatePlan(employeeId, year);
        List<Map<String, Object>> goalsJson = plan.getGoalSlots().stream()
                .sorted(Comparator.comparingInt(GoalSlot::getSlotIndex))
                .map(this::toGoalJson)
                .collect(Collectors.toList());
        boolean canEdit = goalsCanEdit(scope, employeeId);
        CheckPerms cp = checkInPerms(scope, employeeId);

        List<Map<String, String>> picker = null;
        List<Employee> roster = rosterOrdered();
        if (scope.isAppManager()) {
            picker = roster.stream().map(e -> Map.of("id", String.valueOf(e.getId()), "name", e.getName())).collect(Collectors.toList());
        } else if (scope.isPeopleManager()) {
            Set<Long> team = new HashSet<>(scope.getTeamEmployeeIds());
            picker = roster.stream().filter(e -> team.contains(e.getId()))
                    .map(e -> Map.of("id", String.valueOf(e.getId()), "name", e.getName()))
                    .collect(Collectors.toList());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("year", sanitizeYear(year));
        out.put("weekKey", weekKey);
        out.put("employeeId", String.valueOf(employeeId));
        out.put("employeeName", employeeRepository.findById(employeeId).map(Employee::getName).orElse(""));
        out.put("goals", goalsJson);
        out.put("canEdit", canEdit);
        out.put("canEditEmployeeCheckIns", cp.canEditEmployeeNotes());
        out.put("canEditManagerCheckIns", cp.canEditManagerNotes());
        out.put("canDeleteCheckIns", scope.isAppManager());
        out.put("employeePicker", picker);
        return out;
    }

    private record CheckPerms(boolean canEditEmployeeNotes, boolean canEditManagerNotes) {}

    private CheckPerms checkInPerms(TeamScope scope, long targetEmployeeId) {
        if (scope.isAppManager()) return new CheckPerms(true, true);
        Long self = scope.getSelfEmployeeId();
        boolean isSelf = self != null && self.equals(targetEmployeeId);
        boolean mgrForThem = scope.isPeopleManager() && scope.canAccessEmployee(targetEmployeeId) && !isSelf;
        return new CheckPerms(isSelf, mgrForThem);
    }

    private boolean goalsCanEdit(TeamScope scope, long employeeId) {
        if (scope.isAppManager()) return true;
        if (scope.isPeopleManager() && scope.canAccessEmployee(employeeId)) return true;
        return scope.getSelfEmployeeId() != null && scope.getSelfEmployeeId().equals(employeeId);
    }

    private Map<String, Object> toGoalJson(GoalSlot slot) {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("id", String.valueOf(slot.getId()));
        g.put("title", slot.getTitle());
        g.put("description", slot.getDescription());
        List<Map<String, String>> months = new ArrayList<>();
        slot.getMonths().stream().sorted(Comparator.comparingInt(GoalMonthRow::getMonthIndex)).forEach(m -> {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("doing", m.getDoingText());
            row.put("outcome", m.getOutcomeText());
            months.add(row);
        });
        while (months.size() < 12) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("doing", "");
            row.put("outcome", "");
            months.add(row);
        }
        g.put("months", months.subList(0, 12));
        List<Map<String, String>> cis = new ArrayList<>();
        slot.getCheckIns().stream().sorted(Comparator.comparingInt(GoalCheckinRow::getPeriodIndex)).forEach(c -> {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("meetingDate", c.getMeetingDate());
            row.put("employeeNotes", c.getEmployeeNotes());
            row.put("managerNotes", c.getManagerNotes());
            cis.add(row);
        });
        while (cis.size() < 6) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("meetingDate", "");
            row.put("employeeNotes", "");
            row.put("managerNotes", "");
            cis.add(row);
        }
        g.put("checkIns", cis.subList(0, 6));
        return g;
    }

    @Transactional
    public GoalPlan loadOrCreatePlan(long employeeId, int year) {
        int y = sanitizeYear(year);
        return goalPlanRepository.findFetchedByEmployeeAndYear(employeeId, y)
                .orElseGet(() -> createEmptyPlan(employeeId, y));
    }

    private GoalPlan createEmptyPlan(long employeeId, int y) {
        Employee emp = employeeRepository.findById(employeeId).orElseThrow();
        GoalPlan plan = new GoalPlan();
        plan.setEmployee(emp);
        plan.setGoalYear(y);
        for (int slot = 0; slot < 2; slot++) {
            GoalSlot gs = new GoalSlot();
            gs.setGoalPlan(plan);
            gs.setSlotIndex(slot);
            gs.setTitle("");
            gs.setDescription("");
            plan.getGoalSlots().add(gs);
        }
        GoalPlan saved = goalPlanRepository.saveAndFlush(plan);
        for (GoalSlot gs : saved.getGoalSlots().stream()
                .sorted(Comparator.comparingInt(GoalSlot::getSlotIndex))
                .collect(Collectors.toList())) {
            for (int m = 0; m < 12; m++) {
                GoalMonthRow gm = new GoalMonthRow();
                gm.setGoalSlot(gs);
                gm.setMonthIndex(m);
                gm.setDoingText("");
                gm.setOutcomeText("");
                gs.getMonths().add(gm);
            }
            for (int p = 0; p < 6; p++) {
                GoalCheckinRow gc = new GoalCheckinRow();
                gc.setGoalSlot(gs);
                gc.setPeriodIndex(p);
                gs.getCheckIns().add(gc);
            }
        }
        return goalPlanRepository.save(saved);
    }

    @Transactional
    public void mergeFromPut(JsonNode body, int year, long employeeId, TeamScope scope) {
        GoalPlan existing = loadOrCreatePlan(employeeId, sanitizeYear(year));
        CheckPerms perms = checkInPerms(scope, employeeId);
        boolean canGoals = goalsCanEdit(scope, employeeId);
        JsonNode incomingGoals = body.get("goals");
        if (incomingGoals == null || !incomingGoals.isArray()) return;
        List<GoalSlot> slots = existing.getGoalSlots().stream().sorted(Comparator.comparingInt(GoalSlot::getSlotIndex)).collect(Collectors.toList());
        for (int gi = 0; gi < 2 && gi < slots.size(); gi++) {
            GoalSlot slot = slots.get(gi);
            JsonNode incG = incomingGoals.get(gi);
            if (incG != null && incG.has("checkIns")) {
                applyCheckinMerge(slot, incG.get("checkIns"), perms);
            }
            if (canGoals && incG != null && incG.isObject()) {
                if (incG.hasNonNull("title")) slot.setTitle(clamp(incG.get("title").asText(), 200));
                if (incG.hasNonNull("description")) slot.setDescription(clamp(incG.get("description").asText(), 4000));
                mergeMonths(slot, incG.get("months"));
            }
        }
        goalPlanRepository.save(existing);
    }

    private void applyCheckinMerge(GoalSlot slot, JsonNode incArr, CheckPerms perms) {
        if (incArr == null || !incArr.isArray()) return;
        boolean canDate = perms.canEditEmployeeNotes() || perms.canEditManagerNotes();
        for (int i = 0; i < 6 && i < incArr.size(); i++) {
            GoalCheckinRow row = findCi(slot, i);
            if (row == null) continue;
            JsonNode inc = incArr.get(i);
            if (inc == null || !inc.isObject()) continue;
            if (canDate && inc.has("meetingDate")) row.setMeetingDate(sanitizeDate(inc.get("meetingDate").asText("")));
            if (perms.canEditEmployeeNotes() || perms.canEditManagerNotes()) {
                if (inc.has("employeeNotes")) row.setEmployeeNotes(clamp(inc.get("employeeNotes").asText(""), 4000));
            }
            if (perms.canEditManagerNotes() && inc.has("managerNotes")) {
                row.setManagerNotes(clamp(inc.get("managerNotes").asText(""), 4000));
            }
        }
    }

    private GoalCheckinRow findCi(GoalSlot slot, int period) {
        return slot.getCheckIns().stream().filter(c -> c.getPeriodIndex() == period).findFirst().orElse(null);
    }

    private void mergeMonths(GoalSlot slot, JsonNode monthsNode) {
        if (monthsNode == null || !monthsNode.isArray()) return;
        for (int m = 0; m < 12 && m < monthsNode.size(); m++) {
            JsonNode raw = monthsNode.get(m);
            if (raw == null || !raw.isObject()) continue;
            int mi = m;
            GoalMonthRow row = slot.getMonths().stream().filter(x -> x.getMonthIndex() == mi).findFirst().orElse(null);
            if (row == null) continue;
            if (raw.has("doing")) row.setDoingText(clamp(raw.get("doing").asText(""), 8000));
            if (raw.has("outcome")) row.setOutcomeText(clamp(raw.get("outcome").asText(""), 8000));
        }
    }

    private static String sanitizeDate(String s) {
        String t = s == null ? "" : s.trim();
        if (!t.matches("\\d{4}-\\d{2}-\\d{2}")) return "";
        return t;
    }

    private static String clamp(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    @Transactional
    public void clearCheckin(int year, long employeeId, int goalIndex, int periodIndex) {
        GoalPlan plan = loadOrCreatePlan(employeeId, year);
        List<GoalSlot> slots = plan.getGoalSlots().stream().sorted(Comparator.comparingInt(GoalSlot::getSlotIndex)).collect(Collectors.toList());
        if (goalIndex < 0 || goalIndex >= slots.size()) return;
        GoalSlot g = slots.get(goalIndex);
        GoalCheckinRow c = findCi(g, periodIndex);
        if (c != null) {
            c.setMeetingDate("");
            c.setEmployeeNotes("");
            c.setManagerNotes("");
        }
        goalPlanRepository.save(plan);
    }

    public static String[] getPeriodLabels() { return PERIOD_LABELS; }
    public static String[] getPeriodEndDescriptions() { return PERIOD_END; }
}
