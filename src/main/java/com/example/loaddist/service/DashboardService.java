package com.example.loaddist.service;

import com.example.loaddist.model.Allocation;
import com.example.loaddist.model.Employee;
import com.example.loaddist.model.Project;
import com.example.loaddist.service.dto.DashboardData;
import com.example.loaddist.util.Weeks;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private final EmployeeService employeeService;
    private final ProjectService projectService;
    private final AllocationService allocationService;

    public DashboardService(EmployeeService employeeService,
                            ProjectService projectService,
                            AllocationService allocationService) {
        this.employeeService = employeeService;
        this.projectService = projectService;
        this.allocationService = allocationService;
    }

    public DashboardData build(LocalDate week) {
        LocalDate monday = Weeks.mondayOf(week);
        List<Project> projects = projectService.getAll();
        List<Employee> employees = employeeService.getAll();
        List<Allocation> allocations = allocationService.listForWeek(monday);

        Map<Long, Map<Long, BigDecimal>> matrix = new HashMap<>();
        for (Employee e : employees) {
            matrix.put(e.getId(), new HashMap<>());
            for (Project p : projects) {
                matrix.get(e.getId()).put(p.getId(), BigDecimal.ZERO.setScale(3));
            }
        }

        Map<Long, BigDecimal> projectTotals = new HashMap<>();
        for (Project p : projects) {
            projectTotals.put(p.getId(), BigDecimal.ZERO.setScale(3));
        }

        for (Allocation a : allocations) {
            Long eid = a.getEmployee().getId();
            Long pid = a.getProject().getId();
            BigDecimal v = a.getFte().setScale(3, java.math.RoundingMode.HALF_UP);
            matrix.get(eid).put(pid, v);
            projectTotals.merge(pid, v, BigDecimal::add);
        }

        List<DashboardData.SharedProjectColumn> cols = new ArrayList<>();
        for (Project p : projects) {
            cols.add(new DashboardData.SharedProjectColumn(
                    p.getId(), p.getName(), p.getColor(), p.getCapacityFte(), projectTotals.get(p.getId())));
        }

        List<DashboardData.EmployeeMatrixRow> rows = new ArrayList<>();
        for (Employee e : employees) {
            List<BigDecimal> cells = new ArrayList<>();
            BigDecimal sum = BigDecimal.ZERO.setScale(3);
            for (Project p : projects) {
                BigDecimal v = matrix.get(e.getId()).get(p.getId());
                cells.add(v);
                sum = sum.add(v);
            }
            rows.add(new DashboardData.EmployeeMatrixRow(e.getId(), e.getName(), e.getCapacityFte(), sum, cells));
        }

        List<LocalDate> choices = allocationService.weekChoices();
        return new DashboardData(monday, choices, cols, rows);
    }
}
