package com.example.loaddist.api;

import com.example.loaddist.model.Employee;
import com.example.loaddist.repository.EmployeeRepository;
import com.example.loaddist.service.GoalsService;
import com.example.loaddist.service.TeamScope;
import com.example.loaddist.util.RequestWeek;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class GoalsRestController {

    private final GoalsService goalsService;
    private final EmployeeRepository employeeRepository;

    public GoalsRestController(GoalsService goalsService, EmployeeRepository employeeRepository) {
        this.goalsService = goalsService;
        this.employeeRepository = employeeRepository;
    }

    @GetMapping("/api/goals")
    public ResponseEntity<?> get(@RequestParam(required = false) Integer year,
                                 @RequestParam(required = false) String employeeId,
                                 @RequestParam(required = false) String week,
                                 Authentication auth) {
        int y = year != null ? GoalsService.sanitizeYear(year)
                : GoalsService.sanitizeYear(Year.now(ZoneOffset.UTC).getValue());
        TeamScope scope = TeamScope.resolveWithRoster(auth, employeeRepository);
        List<Employee> roster = goalsService.rosterOrdered();
        Set<Long> rosterIds = roster.stream().map(Employee::getId).collect(Collectors.toCollection(HashSet::new));
        if (roster.isEmpty()) {
            LocalDate w = RequestWeek.parseWeekParam(week);
            return ResponseEntity.ok(goalsService.goalsPayloadWhenRosterEmpty(y, scope, w.toString()));
        }
        Long target = goalsService.resolveEmployeeIdForGoals(scope, employeeId, roster);
        if (target == null || !rosterIds.contains(target)) {
            return ResponseEntity.status(404).body(Map.of("error", "No employee in this week plan for goals."));
        }
        LocalDate w = RequestWeek.parseWeekParam(week);
        return ResponseEntity.ok(goalsService.getGoalsApi(y, target, scope, w.toString()));
    }

    @PutMapping("/api/goals")
    public ResponseEntity<?> put(@RequestParam(required = false) String week,
                                 @RequestBody JsonNode body,
                                 Authentication auth) {
        int y = GoalsService.sanitizeYear(body.has("year") ? body.get("year").asInt(Year.now(ZoneOffset.UTC).getValue()) : Year.now(ZoneOffset.UTC).getValue());
        String employeeIdStr = body.has("employeeId") ? body.get("employeeId").asText("").trim() : "";
        TeamScope scope = TeamScope.resolveWithRoster(auth, employeeRepository);
        List<Employee> roster = goalsService.rosterOrdered();
        Set<Long> rosterIds = roster.stream().map(Employee::getId).collect(Collectors.toCollection(HashSet::new));

        if (employeeIdStr.isEmpty() || !rosterIds.contains(parseEmployeeId(employeeIdStr))) {
            return ResponseEntity.status(404).body(Map.of("error", "Employee not found"));
        }
        long employeeId = Long.parseLong(employeeIdStr);
        Long scoped = goalsService.resolveEmployeeIdForGoals(scope, employeeIdStr, roster);
        if (scoped == null || !scoped.equals(employeeId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }
        boolean canGoals = goalsCanEdit(scope, employeeId);
        CheckInPerms cp = checkInPerms(scope, employeeId);
        if (!canGoals && !cp.canEditEmployeeNotes() && !cp.canEditManagerNotes()) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }
        goalsService.mergeFromPut(body, y, employeeId, scope);
        LocalDate w = RequestWeek.parseWeekParam(week);
        Map<String, Object> fresh = goalsService.getGoalsApi(y, employeeId, scope, w.toString());
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "year", y,
                "employeeId", String.valueOf(employeeId),
                "goals", fresh.get("goals")));
    }

    @DeleteMapping("/api/goals/check-in")
    public ResponseEntity<?> deleteCheckin(@RequestBody JsonNode body,
                                           @RequestParam(required = false) String week,
                                           Authentication auth) {
        TeamScope scope = TeamScope.resolveWithRoster(auth, employeeRepository);
        if (!scope.isAppManager()) {
            return ResponseEntity.status(403).body(Map.of("error", "Only app admins can delete check-ins."));
        }
        int y = GoalsService.sanitizeYear(body.has("year") ? body.get("year").asInt(Year.now(ZoneOffset.UTC).getValue()) : Year.now(ZoneOffset.UTC).getValue());
        String employeeIdStr = body.has("employeeId") ? body.get("employeeId").asText("").trim() : "";
        int gi = body.has("goalIndex") ? body.get("goalIndex").asInt(-1) : -1;
        int pi = body.has("periodIndex") ? body.get("periodIndex").asInt(-1) : -1;

        if (employeeIdStr.isEmpty() || (gi != 0 && gi != 1) || pi < 0 || pi > 5) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid employee, goalIndex (0–1), or periodIndex (0–5)."));
        }
        long employeeId = Long.parseLong(employeeIdStr);
        List<Employee> roster = goalsService.rosterOrdered();
        Set<Long> rosterIds = roster.stream().map(Employee::getId).collect(Collectors.toCollection(HashSet::new));
        if (!rosterIds.contains(employeeId)) {
            return ResponseEntity.status(404).body(Map.of("error", "Employee not found for this week plan."));
        }
        Long scoped = goalsService.resolveEmployeeIdForGoals(scope, employeeIdStr, roster);
        if (scoped == null || !scoped.equals(employeeId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        goalsService.clearCheckin(y, employeeId, gi, pi);
        LocalDate w = RequestWeek.parseWeekParam(week);
        Map<String, Object> fresh = goalsService.getGoalsApi(y, employeeId, scope, w.toString());
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "year", y,
                "employeeId", employeeIdStr,
                "goals", fresh.get("goals")));
    }

    private static long parseEmployeeId(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private boolean goalsCanEdit(TeamScope scope, long employeeId) {
        if (scope.isAppManager()) return true;
        if (scope.isPeopleManager() && scope.canAccessEmployee(employeeId)) return true;
        return scope.getSelfEmployeeId() != null && scope.getSelfEmployeeId().equals(employeeId);
    }

    private record CheckInPerms(boolean canEditEmployeeNotes, boolean canEditManagerNotes) {}

    private CheckInPerms checkInPerms(TeamScope scope, long targetEmployeeId) {
        if (scope.isAppManager()) return new CheckInPerms(true, true);
        Long self = scope.getSelfEmployeeId();
        boolean isSelf = self != null && self.equals(targetEmployeeId);
        boolean mgrForThem = scope.isPeopleManager() && scope.canAccessEmployee(targetEmployeeId) && !isSelf;
        return new CheckInPerms(isSelf, mgrForThem);
    }
}
