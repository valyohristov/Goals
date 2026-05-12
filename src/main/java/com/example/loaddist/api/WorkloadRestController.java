package com.example.loaddist.api;

import com.example.loaddist.model.Allocation;
import com.example.loaddist.model.Employee;
import com.example.loaddist.model.Project;
import com.example.loaddist.repository.AllocationRepository;
import com.example.loaddist.repository.EmployeeRepository;
import com.example.loaddist.repository.ProjectRepository;
import com.example.loaddist.security.LdUserPrincipal;
import com.example.loaddist.service.AccountService;
import com.example.loaddist.service.ForecastService;
import com.example.loaddist.service.SankeyService;
import com.example.loaddist.service.TeamScope;
import com.example.loaddist.service.WeekResolutionService;
import com.example.loaddist.util.RequestWeek;
import com.example.loaddist.util.Weeks;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class WorkloadRestController {

    private static final String[] PALETTE = {
            "#8b5cf6", "#0ea5e9", "#f59e0b", "#10b981", "#ef4444", "#f97316",
            "#14b8a6", "#6366f1", "#22c55e", "#e11d48"
    };

    private final SankeyService sankeyService;
    private final ForecastService forecastService;
    private final WeekResolutionService weekResolutionService;
    private final EmployeeRepository employeeRepository;
    private final ProjectRepository projectRepository;
    private final AllocationRepository allocationRepository;
    private final AccountService accountService;

    public WorkloadRestController(SankeyService sankeyService,
                                  ForecastService forecastService,
                                  WeekResolutionService weekResolutionService,
                                  EmployeeRepository employeeRepository,
                                  ProjectRepository projectRepository,
                                  AllocationRepository allocationRepository,
                                  AccountService accountService) {
        this.sankeyService = sankeyService;
        this.forecastService = forecastService;
        this.weekResolutionService = weekResolutionService;
        this.employeeRepository = employeeRepository;
        this.projectRepository = projectRepository;
        this.allocationRepository = allocationRepository;
        this.accountService = accountService;
    }

    private static boolean isAppManager(Authentication auth) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_MANAGER".equals(a.getAuthority()));
    }

    @GetMapping("/api/sankey")
    public ResponseEntity<?> sankey(@RequestParam(required = false) String week, Authentication auth) {
        LocalDate w = RequestWeek.parseWeekParam(week);
        TeamScope scope = TeamScope.resolveWithRoster(auth, employeeRepository);
        return ResponseEntity.ok(sankeyService.build(w, scope));
    }

    @GetMapping("/api/week-settings")
    public ResponseEntity<?> weekSettingsGet(@RequestParam(required = false) String week, Authentication auth) {
        if (!isAppManager(auth)) return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        LocalDate w = RequestWeek.parseWeekParam(week);
        var ws = weekResolutionService.getOrCreate(w);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("weekKey", w.toString());
        m.put("copyToNextWeek", ws.isCopyToNextWeek());
        return ResponseEntity.ok(m);
    }

    @PutMapping("/api/week-settings")
    public ResponseEntity<?> weekSettingsPut(@RequestParam(required = false) String week,
                                             @RequestBody JsonNode body, Authentication auth) {
        if (!isAppManager(auth)) return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        LocalDate w = RequestWeek.parseWeekParam(week);
        boolean copy = body.get("copyToNextWeek") == null || body.get("copyToNextWeek").asBoolean(true);
        var ws = weekResolutionService.updateCopyToNext(w, copy);
        return ResponseEntity.ok(Map.of("weekKey", ws.getWeekStartDate().toString(), "copyToNextWeek", ws.isCopyToNextWeek()));
    }

    @GetMapping("/api/forecast")
    public ResponseEntity<?> forecast(@RequestParam(required = false) String week,
                                      @RequestParam(defaultValue = "13") int weeks, Authentication auth) {
        if (!isAppManager(auth)) return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        LocalDate w = RequestWeek.parseWeekParam(week);
        return ResponseEntity.ok(forecastService.build(w, weeks, new TeamScope(true, false, null, null)));
    }

    @GetMapping("/api/employees")
    public ResponseEntity<?> employeesList(@RequestParam(required = false) String week,
                                           @RequestParam(required = false) String q,
                                           Authentication auth) {
        TeamScope scope = TeamScope.resolveWithRoster(auth, employeeRepository);
        if (!scope.isAppManager() && !scope.isPeopleManager()) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }
        LocalDate w = RequestWeek.parseWeekParam(week);
        weekResolutionService.syncNonCurrentWeekFromPrevious(w);
        String qq = q != null ? q.trim().toLowerCase() : "";
        List<Employee> list = employeeRepository.findAllOrderByNameIgnoreCase();
        if (!qq.isEmpty()) {
            list = list.stream().filter(e -> e.getName().toLowerCase().contains(qq)).collect(Collectors.toList());
        }
        if (scope.isPeopleManager() && !scope.isAppManager()) {
            Set<Long> team = new HashSet<>(scope.getTeamEmployeeIds());
            list = list.stream().filter(e -> team.contains(e.getId())).collect(Collectors.toList());
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Employee e : list) {
            accountService.ensureEmployeeLogin(e);
            items.add(employeeToMap(e));
        }
        List<Map<String, String>> managerOptions = null;
        if (scope.isPeopleManager() && !scope.isAppManager()) {
            managerOptions = employeeRepository.findAllOrderByNameIgnoreCase().stream()
                    .map(e -> Map.of("id", String.valueOf(e.getId()), "name", e.getName()))
                    .collect(Collectors.toList());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("weekKey", w.toString());
        out.put("count", items.size());
        out.put("items", items);
        out.put("managerOptions", managerOptions);
        return ResponseEntity.ok(out);
    }

    private Map<String, Object> employeeToMap(Employee e) {
        accountService.ensureEmployeeLogin(e);
        String un = accountService.getLoginUsername(e.getId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(e.getId()));
        m.put("name", e.getName());
        m.put("capacityFte", e.getCapacityFte().doubleValue());
        m.put("managerId", e.getManager() != null ? String.valueOf(e.getManager().getId()) : "");
        m.put("isManager", e.isManager());
        m.put("loginUsername", un.isEmpty() ? null : un);
        m.put("hasLogin", !un.isEmpty());
        if (e.getManager() != null) {
            employeeRepository.findById(e.getManager().getId()).ifPresent(mgr -> m.put("managerName", mgr.getName()));
        } else {
            m.put("managerName", null);
        }
        return m;
    }

    @PostMapping("/api/employees")
    public ResponseEntity<?> createEmployee(@RequestParam(required = false) String week,
                                            @RequestBody JsonNode body, Authentication auth) {
        if (!isAppManager(auth)) return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        String name = body.has("name") ? body.get("name").asText("").trim() : "";
        if (name.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "Employee name is required"));
        if (duplicateEmployeeName(name, null)) {
            return ResponseEntity.status(409).body(Map.of("error", "Employee already exists"));
        }
        Employee e = new Employee();
        e.setName(name);
        e.setCapacityFte(sanitizeFte(body.get("capacityFte"), BigDecimal.ONE));
        e.setIsManager(body.has("isManager") && body.get("isManager").asBoolean(false));
        if (body.hasNonNull("managerId") && !body.get("managerId").asText("").isBlank()) {
            Long mid = Long.parseLong(body.get("managerId").asText());
            employeeRepository.findById(mid).ifPresent(e::setManager);
        }
        e = employeeRepository.save(e);
        accountService.ensureEmployeeLogin(e);
        LocalDate w = RequestWeek.parseWeekParam(week);
        weekResolutionService.markManual(w);
        return ResponseEntity.status(201).body(employeeToMap(e));
    }

    @PutMapping("/api/employees/{id}")
    public ResponseEntity<?> updateEmployee(@PathVariable long id, @RequestBody JsonNode body, Authentication auth) {
        TeamScope scope = TeamScope.resolveWithRoster(auth, employeeRepository);
        if (!scope.isAppManager() && (!scope.isPeopleManager() || !scope.canAccessEmployee(id))) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }
        Employee e = employeeRepository.findById(id).orElse(null);
        if (e == null) return ResponseEntity.status(404).body(Map.of("error", "Employee not found"));
        if (body.has("name")) e.setName(body.get("name").asText("").trim());
        if (body.has("capacityFte")) e.setCapacityFte(sanitizeFte(body.get("capacityFte"), e.getCapacityFte()));
        if (body.has("isManager")) e.setIsManager(body.get("isManager").asBoolean(false));
        if (body.has("managerId")) {
            String mid = body.get("managerId").asText("");
            if (mid.isBlank()) e.setManager(null);
            else {
                Long m = Long.parseLong(mid);
                if (!m.equals(id)) employeeRepository.findById(m).ifPresent(e::setManager);
                else e.setManager(null);
            }
        }
        if (e.getName() == null || e.getName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Employee name is required"));
        }
        if (duplicateEmployeeName(e.getName(), id)) {
            return ResponseEntity.status(409).body(Map.of("error", "Employee already exists"));
        }
        employeeRepository.save(e);
        return ResponseEntity.ok(employeeToMap(e));
    }

    @DeleteMapping("/api/employees/{id}")
    public ResponseEntity<?> deleteEmployee(@PathVariable long id,
                                            @RequestParam(required = false) String week,
                                            Authentication auth) {
        if (!isAppManager(auth)) return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        Employee e = employeeRepository.findById(id).orElse(null);
        if (e == null) return ResponseEntity.status(404).body(Map.of("error", "Employee not found"));
        List<Employee> others = employeeRepository.findAllOrderByNameIgnoreCase();
        for (Employee o : others) {
            if (o.getManager() != null && o.getManager().getId().equals(id)) {
                o.setManager(null);
                employeeRepository.save(o);
            }
        }
        employeeRepository.delete(e);
        accountService.deleteLoginForEmployee(id);
        LocalDate w = RequestWeek.parseWeekParam(week);
        weekResolutionService.markManual(w);
        return ResponseEntity.ok(Map.of("ok", true, "deletedId", String.valueOf(id)));
    }

    @GetMapping("/api/projects")
    public ResponseEntity<?> listProjects(@RequestParam(required = false) String week,
                                          @RequestParam(required = false) String q,
                                          Authentication auth) {
        TeamScope scope = TeamScope.resolveWithRoster(auth, employeeRepository);
        if (!scope.isAppManager() && !scope.isPeopleManager()) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }
        LocalDate w = RequestWeek.parseWeekParam(week);
        weekResolutionService.syncNonCurrentWeekFromPrevious(w);
        String qq = q != null ? q.trim().toLowerCase() : "";
        List<Project> list = projectRepository.findAll().stream().sorted(Comparator.comparing(Project::getId)).collect(Collectors.toList());
        if (!qq.isEmpty()) {
            list = list.stream().filter(p -> p.getName().toLowerCase().contains(qq)).collect(Collectors.toList());
        }
        List<Map<String, Object>> items = list.stream().map(this::projectToMap).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("weekKey", w.toString(), "count", items.size(), "items", items));
    }

    private Map<String, Object> projectToMap(Project p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(p.getId()));
        m.put("name", p.getName());
        m.put("capacityFte", p.getCapacityFte().doubleValue());
        m.put("color", p.getColor() != null ? p.getColor() : "");
        return m;
    }

    @PostMapping("/api/projects")
    public ResponseEntity<?> createProject(@RequestParam(required = false) String week,
                                           @RequestBody JsonNode body, Authentication auth) {
        if (!isAppManager(auth)) return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        String name = body.has("name") ? body.get("name").asText("").trim() : "";
        if (name.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "Project name is required"));
        if (duplicateProjectName(name, null)) {
            return ResponseEntity.status(409).body(Map.of("error", "Project already exists"));
        }
        Project p = new Project();
        p.setName(name);
        p.setCapacityFte(sanitizeFte(body.get("capacityFte"), BigDecimal.ZERO));
        p.setColor(sanitizeColor(body.has("color") ? body.get("color").asText("") : "")
                != null ? sanitizeColor(body.get("color").asText("")) : fallbackColor(name, (int) projectRepository.count()));
        p = projectRepository.save(p);
        LocalDate w = RequestWeek.parseWeekParam(week);
        weekResolutionService.markManual(w);
        return ResponseEntity.status(201).body(projectToMap(p));
    }

    @PutMapping("/api/projects/{id}")
    public ResponseEntity<?> updateProject(@PathVariable long id,
                                           @RequestParam(required = false) String week,
                                           @RequestBody JsonNode body, Authentication auth) {
        if (!isAppManager(auth)) return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        Project p = projectRepository.findById(id).orElse(null);
        if (p == null) return ResponseEntity.status(404).body(Map.of("error", "Project not found"));
        if (body.has("name")) p.setName(body.get("name").asText("").trim());
        if (body.has("capacityFte")) p.setCapacityFte(sanitizeFte(body.get("capacityFte"), p.getCapacityFte()));
        if (body.has("color")) {
            String c = sanitizeColor(body.get("color").asText(""));
            p.setColor(c != null ? c : p.getColor());
        }
        if (p.getName() == null || p.getName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Project name is required"));
        }
        if (duplicateProjectName(p.getName(), id)) {
            return ResponseEntity.status(409).body(Map.of("error", "Project already exists"));
        }
        projectRepository.save(p);
        weekResolutionService.markManual(RequestWeek.parseWeekParam(week));
        return ResponseEntity.ok(projectToMap(p));
    }

    @DeleteMapping("/api/projects/{id}")
    public ResponseEntity<?> deleteProject(@PathVariable long id,
                                           @RequestParam(required = false) String week,
                                           Authentication auth) {
        if (!isAppManager(auth)) return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        if (!projectRepository.existsById(id)) return ResponseEntity.status(404).body(Map.of("error", "Project not found"));
        projectRepository.deleteById(id);
        weekResolutionService.markManual(RequestWeek.parseWeekParam(week));
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/api/employees/{id}/allocations")
    public ResponseEntity<?> getAlloc(@PathVariable long id,
                                       @RequestParam(required = false) String week,
                                       Authentication auth) {
        TeamScope scope = TeamScope.resolveWithRoster(auth, employeeRepository);
        if (!scope.isAppManager() && (!scope.isPeopleManager() || !scope.canAccessEmployee(id))) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }
        if (!employeeRepository.existsById(id)) return ResponseEntity.status(404).body(Map.of("error", "Employee not found"));
        LocalDate w = RequestWeek.parseWeekParam(week);
        weekResolutionService.syncNonCurrentWeekFromPrevious(w);
        List<Allocation> rows = allocationRepository.findByEmployeeIdAndWeekStartDate(id, w);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Allocation a : rows) {
            if (a.getFte().compareTo(BigDecimal.ZERO) <= 0) continue;
            out.add(Map.of("projectId", String.valueOf(a.getProject().getId()), "fte", a.getFte().doubleValue()));
        }
        return ResponseEntity.ok(Map.of("weekKey", w.toString(), "employeeId", String.valueOf(id), "allocations", out));
    }

    @Transactional
    @PutMapping("/api/employees/{id}/allocations")
    public ResponseEntity<?> putAlloc(@PathVariable long id,
                                      @RequestParam(required = false) String week,
                                      @RequestBody JsonNode body,
                                      Authentication auth) {
        TeamScope scope = TeamScope.resolveWithRoster(auth, employeeRepository);
        if (!scope.isAppManager() && (!scope.isPeopleManager() || !scope.canAccessEmployee(id))) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }
        Employee emp = employeeRepository.findById(id).orElse(null);
        if (emp == null) return ResponseEntity.status(404).body(Map.of("error", "Employee not found"));
        LocalDate w = RequestWeek.parseWeekParam(week);
        JsonNode arr = body.get("allocations");
        if (arr == null || !arr.isArray()) {
            return ResponseEntity.badRequest().body(Map.of("error", "allocations array required"));
        }
        Set<Long> projectIds = projectRepository.findAll().stream().map(Project::getId).collect(Collectors.toSet());
        List<Allocation> next = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (JsonNode row : arr) {
            String pid = row.has("projectId") ? row.get("projectId").asText("") : "";
            BigDecimal fte = sanitizeFte(row.get("fte"), BigDecimal.ZERO);
            if (pid.isEmpty() || !projectIds.contains(Long.parseLong(pid)) || fte.compareTo(BigDecimal.ZERO) <= 0) continue;
            long pl = Long.parseLong(pid);
            if (seen.contains(pl)) continue;
            seen.add(pl);
            Allocation a = new Allocation();
            a.setEmployee(emp);
            a.setProject(projectRepository.findById(pl).orElseThrow());
            a.setWeekStartDate(Weeks.mondayOf(w));
            a.setFte(fte);
            next.add(a);
        }
        allocationRepository.deleteByEmployeeIdAndWeekStartDate(id, Weeks.mondayOf(w));
        allocationRepository.flush();
        for (Allocation a : next) {
            validateCap(a, null);
            allocationRepository.save(a);
        }
        weekResolutionService.markManual(w);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Allocation a : allocationRepository.findByEmployeeIdAndWeekStartDate(id, Weeks.mondayOf(w))) {
            if (a.getFte().compareTo(BigDecimal.ZERO) <= 0) continue;
            out.add(Map.of("projectId", String.valueOf(a.getProject().getId()), "fte", a.getFte().doubleValue()));
        }
        return ResponseEntity.ok(Map.of("weekKey", Weeks.mondayOf(w).toString(), "employeeId", String.valueOf(id), "allocations", out));
    }

    private void validateCap(Allocation allocation, Long excludeId) {
        LocalDate monday = allocation.getWeekStartDate();
        BigDecimal empSum = allocationRepository.sumFteByEmployeeAndWeekExcluding(
                allocation.getEmployee().getId(), monday, excludeId);
        if (empSum.add(allocation.getFte()).compareTo(allocation.getEmployee().getCapacityFte()) > 0) {
            throw new IllegalArgumentException("Employee capacity exceeded for this week");
        }
        BigDecimal projSum = allocationRepository.sumFteByProjectAndWeekExcluding(
                allocation.getProject().getId(), monday, excludeId);
        if (projSum.add(allocation.getFte()).compareTo(allocation.getProject().getCapacityFte()) > 0) {
            throw new IllegalArgumentException("Project capacity exceeded for this week");
        }
    }

    @PutMapping("/api/users/employee/{employeeId}/password")
    public ResponseEntity<?> setPwd(@PathVariable long employeeId, @RequestBody JsonNode body, Authentication auth) {
        if (!isAppManager(auth)) return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        try {
            accountService.setEmployeePassword(employeeId, body.get("password").asText(""), "");
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/api/users/employee/{employeeId}/username")
    public ResponseEntity<?> setUser(@PathVariable long employeeId, @RequestBody JsonNode body, Authentication auth) {
        if (!isAppManager(auth)) return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        try {
            accountService.ensureEmployeeLogin(employeeRepository.findById(employeeId).orElseThrow());
            accountService.setEmployeeUsername(employeeId, body.get("username").asText(""));
            return ResponseEntity.ok(Map.of("ok", true, "username", accountService.getLoginUsername(employeeId)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private boolean duplicateEmployeeName(String name, Long excludeId) {
        String n = name.trim().toLowerCase();
        return employeeRepository.findAllOrderByNameIgnoreCase().stream()
                .anyMatch(e -> !e.getId().equals(excludeId) && e.getName().trim().toLowerCase().equals(n));
    }

    private boolean duplicateProjectName(String name, Long excludeId) {
        String n = name.trim().toLowerCase();
        return projectRepository.findAll().stream()
                .anyMatch(p -> !p.getId().equals(excludeId) && p.getName().trim().toLowerCase().equals(n));
    }

    private static BigDecimal sanitizeFte(JsonNode n, BigDecimal dft) {
        if (n == null || n.isNull()) return dft;
        double v = n.asDouble();
        if (!Double.isFinite(v) || v < 0) return dft;
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP);
    }

    private static String sanitizeColor(String raw) {
        String c = raw.trim().toLowerCase();
        if (c.matches("#[0-9a-f]{6}")) return c;
        return null;
    }

    private static String fallbackColor(String seed, int index) {
        int h = (seed.hashCode() * 31 + index) & 0x7fffffff;
        return PALETTE[h % PALETTE.length];
    }
}
