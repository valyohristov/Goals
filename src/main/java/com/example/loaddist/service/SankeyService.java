package com.example.loaddist.service;

import com.example.loaddist.model.Allocation;
import com.example.loaddist.model.Employee;
import com.example.loaddist.model.Project;
import com.example.loaddist.repository.AllocationRepository;
import com.example.loaddist.repository.EmployeeRepository;
import com.example.loaddist.repository.ProjectRepository;
import com.example.loaddist.repository.WeekSettingRepository;
import com.example.loaddist.util.Weeks;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SankeyService {

    private final EmployeeRepository employeeRepository;
    private final ProjectRepository projectRepository;
    private final AllocationRepository allocationRepository;
    private final WeekSettingRepository weekSettingRepository;
    private final WeekResolutionService weekResolutionService;

    public SankeyService(EmployeeRepository employeeRepository,
                         ProjectRepository projectRepository,
                         AllocationRepository allocationRepository,
                         WeekSettingRepository weekSettingRepository,
                         WeekResolutionService weekResolutionService) {
        this.employeeRepository = employeeRepository;
        this.projectRepository = projectRepository;
        this.allocationRepository = allocationRepository;
        this.weekSettingRepository = weekSettingRepository;
        this.weekResolutionService = weekResolutionService;
    }

    @Transactional
    public Map<String, Object> build(LocalDate weekMonday, TeamScope scope) {
        LocalDate week = Weeks.mondayOf(weekMonday);
        weekResolutionService.syncNonCurrentWeekFromPrevious(week);
        if (scope.isAppManager()) {
            return payloadFull(week, "admin");
        }
        if (scope.isPeopleManager()) {
            return payloadTeam(week, scope.getTeamEmployeeIds(), "team");
        }
        if (scope.getSelfEmployeeId() != null) {
            return payloadEmployee(week, scope.getSelfEmployeeId(), "employee");
        }
        return payloadFull(week, "employee");
    }

    private Map<String, Object> weekBlock(LocalDate weekKey) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", weekKey.toString());
        m.put("prevKey", weekKey.minusWeeks(1).toString());
        m.put("nextKey", weekKey.plusWeeks(1).toString());
        m.put("currentKey", Weeks.mondayUtcToday().toString());
        m.put("copyToNextWeek", weekSettingRepository.findById(weekKey).map(ws -> ws.isCopyToNextWeek()).orElse(true));
        return m;
    }

    private Map<String, Object> payloadFull(LocalDate weekKey, String scopeLabel) {
        List<Employee> employees = employeeRepository.findAllOrderByNameIgnoreCase();
        List<Project> projects = projectRepository.findAll().stream()
                .sorted(Comparator.comparing(Project::getId))
                .collect(Collectors.toList());
        List<Allocation> allocations = allocationRepository.findByWeekStartWithAssociations(weekKey);
        return buildNodesLinks(weekKey, scopeLabel, employees, projects, allocations);
    }

    private Map<String, Object> payloadTeam(LocalDate weekKey, List<Long> teamIds, String scopeLabel) {
        Set<Long> idSet = new HashSet<>(teamIds);
        List<Employee> employees = employeeRepository.findAllOrderByNameIgnoreCase().stream()
                .filter(e -> idSet.contains(e.getId()))
                .collect(Collectors.toList());
        List<Allocation> all = allocationRepository.findByWeekStartWithAssociations(weekKey);
        List<Allocation> allocations = all.stream()
                .filter(a -> idSet.contains(a.getEmployee().getId()) && a.getFte().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());
        Set<Long> projectIds = allocations.stream().map(a -> a.getProject().getId()).collect(Collectors.toSet());
        List<Project> projects = projectRepository.findAll().stream()
                .filter(p -> projectIds.contains(p.getId()))
                .sorted(Comparator.comparing(Project::getId))
                .collect(Collectors.toList());
        return buildNodesLinks(weekKey, scopeLabel, employees, projects, allocations);
    }

    private Map<String, Object> payloadEmployee(LocalDate weekKey, Long employeeId, String scopeLabel) {
        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        List<Allocation> all = allocationRepository.findByWeekStartWithAssociations(weekKey);
        List<Allocation> allocations = all.stream()
                .filter(a -> employeeId.equals(a.getEmployee().getId()) && a.getFte().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());
        List<Employee> employees = employee != null ? List.of(employee) : List.of();
        Set<Long> projectIds = allocations.stream().map(a -> a.getProject().getId()).collect(Collectors.toSet());
        List<Project> projects = projectRepository.findAll().stream()
                .filter(p -> projectIds.contains(p.getId()))
                .sorted(Comparator.comparing(Project::getId))
                .collect(Collectors.toList());
        return buildNodesLinks(weekKey, scopeLabel, employees, projects, allocations);
    }

    private Map<String, Object> buildNodesLinks(
            LocalDate weekKey,
            String scopeLabel,
            List<Employee> employees,
            List<Project> projects,
            List<Allocation> allocations) {

        Map<Long, Integer> employeeIndex = new HashMap<>();
        for (int i = 0; i < employees.size(); i++) {
            employeeIndex.put(employees.get(i).getId(), i);
        }
        Map<Long, Integer> projectIndex = new HashMap<>();
        for (int i = 0; i < projects.size(); i++) {
            projectIndex.put(projects.get(i).getId(), employees.size() + i);
        }
        Map<Long, Project> projectById = projects.stream().collect(Collectors.toMap(Project::getId, p -> p));

        List<Map<String, Object>> links = new ArrayList<>();
        for (Allocation a : allocations) {
            Integer si = employeeIndex.get(a.getEmployee().getId());
            Integer ti = projectIndex.get(a.getProject().getId());
            if (si == null || ti == null) continue;
            Project p = projectById.get(a.getProject().getId());
            Map<String, Object> link = new LinkedHashMap<>();
            link.put("source", si);
            link.put("target", ti);
            link.put("value", a.getFte().setScale(2, RoundingMode.HALF_UP).doubleValue());
            link.put("employeeId", String.valueOf(a.getEmployee().getId()));
            link.put("projectId", String.valueOf(a.getProject().getId()));
            link.put("projectColor", p != null && p.getColor() != null ? p.getColor() : "");
            links.add(link);
        }

        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Employee e : employees) {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("id", String.valueOf(e.getId()));
            n.put("name", e.getName());
            n.put("type", "employee");
            n.put("capacityFte", e.getCapacityFte().doubleValue());
            nodes.add(n);
        }
        for (Project p : projects) {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("id", String.valueOf(p.getId()));
            n.put("name", p.getName());
            n.put("type", "project");
            n.put("capacityFte", p.getCapacityFte().doubleValue());
            n.put("color", p.getColor() != null ? p.getColor() : "");
            nodes.add(n);
        }

        Map<Long, BigDecimal> allocatedByEmployee = sumByEmployeeId(allocations);
        Map<Long, BigDecimal> allocatedByProject = sumByProjectId(allocations);

        BigDecimal empCap = employees.stream().map(Employee::getCapacityFte).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal empAlloc = allocations.stream().map(Allocation::getFte).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal projCap = projects.stream().map(Project::getCapacityFte).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("week", weekBlock(weekKey));
        out.put("scope", scopeLabel);
        out.put("nodes", nodes);
        out.put("links", links);
        out.put("employees", employees.stream().map(e -> employeeJson(e)).collect(Collectors.toList()));
        out.put("projects", projects.stream().map(p -> projectJson(p)).collect(Collectors.toList()));
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("employeeCapacity", empCap.doubleValue());
        totals.put("employeeAllocated", empAlloc.doubleValue());
        totals.put("projectCapacity", projCap.doubleValue());
        out.put("totals", totals);
        out.put("employeeUtilization", employees.stream().map(e -> utilRow(e, allocatedByEmployee)).collect(Collectors.toList()));
        out.put("projectDemand", projects.stream().map(p -> demandRow(p, allocatedByProject)).collect(Collectors.toList()));
        return out;
    }

    private static Map<Long, BigDecimal> sumByEmployeeId(List<Allocation> allocations) {
        Map<Long, BigDecimal> m = new HashMap<>();
        for (Allocation a : allocations) {
            m.merge(a.getEmployee().getId(), a.getFte(), BigDecimal::add);
        }
        return m;
    }

    private static Map<Long, BigDecimal> sumByProjectId(List<Allocation> allocations) {
        Map<Long, BigDecimal> m = new HashMap<>();
        for (Allocation a : allocations) {
            m.merge(a.getProject().getId(), a.getFte(), BigDecimal::add);
        }
        return m;
    }

    private static Map<String, Object> employeeJson(Employee e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(e.getId()));
        m.put("name", e.getName());
        m.put("capacityFte", e.getCapacityFte().doubleValue());
        m.put("managerId", e.getManager() != null ? String.valueOf(e.getManager().getId()) : "");
        m.put("isManager", e.isManager());
        return m;
    }

    private static Map<String, Object> projectJson(Project p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(p.getId()));
        m.put("name", p.getName());
        m.put("capacityFte", p.getCapacityFte().doubleValue());
        m.put("color", p.getColor() != null ? p.getColor() : "");
        return m;
    }

    private static Map<String, Object> utilRow(Employee e, Map<Long, BigDecimal> sums) {
        BigDecimal alloc = sums.getOrDefault(e.getId(), BigDecimal.ZERO);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(e.getId()));
        m.put("name", e.getName());
        m.put("capacityFte", e.getCapacityFte().doubleValue());
        m.put("allocatedFte", alloc.setScale(2, RoundingMode.HALF_UP).doubleValue());
        return m;
    }

    private static Map<String, Object> demandRow(Project p, Map<Long, BigDecimal> sums) {
        BigDecimal alloc = sums.getOrDefault(p.getId(), BigDecimal.ZERO);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(p.getId()));
        m.put("name", p.getName());
        m.put("capacityFte", p.getCapacityFte().doubleValue());
        m.put("allocatedFte", alloc.setScale(2, RoundingMode.HALF_UP).doubleValue());
        return m;
    }
}
