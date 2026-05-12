package com.example.loaddist.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DashboardData(
        LocalDate week,
        List<LocalDate> weekChoices,
        List<SharedProjectColumn> projects,
        List<EmployeeMatrixRow> rows
) {
    public record SharedProjectColumn(Long id, String name, String color, BigDecimal capacityFte, BigDecimal allocatedSum) {}

    public record EmployeeMatrixRow(Long id, String name, BigDecimal capacityFte, BigDecimal allocatedSum, List<BigDecimal> ftesByProjectOrder) {}
}
