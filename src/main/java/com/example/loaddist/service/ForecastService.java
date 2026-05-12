package com.example.loaddist.service;

import com.example.loaddist.util.Weeks;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
public class ForecastService {

    private final SankeyService sankeyService;

    public ForecastService(SankeyService sankeyService) {
        this.sankeyService = sankeyService;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> build(LocalDate startWeek, int weeksCount, TeamScope adminScope) {
        int n = Math.max(1, Math.min(26, weeksCount));
        LocalDate start = Weeks.mondayOf(startWeek);
        Map<String, Object> startPayload = sankeyService.build(start, adminScope);
        List<Map<String, Object>> demand =
                (List<Map<String, Object>>) startPayload.get("projectDemand");

        List<Map<String, Object>> over = new ArrayList<>();
        List<Map<String, Object>> under = new ArrayList<>();
        for (Map<String, Object> item : demand) {
            double cap = ((Number) item.get("capacityFte")).doubleValue();
            double alloc = ((Number) item.get("allocatedFte")).doubleValue();
            if (alloc > cap + 0.0001) {
                Map<String, Object> m = new LinkedHashMap<>(item);
                m.put("deltaFte", round2(alloc - cap));
                over.add(m);
            } else if (alloc + 0.0001 < cap) {
                Map<String, Object> m = new LinkedHashMap<>(item);
                m.put("deltaFte", round2(cap - alloc));
                under.add(m);
            }
        }
        Map<String, Object> projectIssues = new LinkedHashMap<>();
        projectIssues.put("overallocated", over);
        projectIssues.put("understaffed", under);

        List<Map<String, Object>> weeks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            LocalDate wk = start.plusWeeks(i);
            Map<String, Object> payload = sankeyService.build(wk, adminScope);
            Map<String, Object> totals = (Map<String, Object>) payload.get("totals");
            List<Map<String, Object>> util = (List<Map<String, Object>>) payload.get("employeeUtilization");
            List<Map<String, Object>> projD = (List<Map<String, Object>>) payload.get("projectDemand");
            double ec = ((Number) totals.get("employeeCapacity")).doubleValue();
            double ea = ((Number) totals.get("employeeAllocated")).doubleValue();
            double pc = ((Number) totals.get("projectCapacity")).doubleValue();
            double pa = projD.stream().mapToDouble(x -> ((Number) x.get("allocatedFte")).doubleValue()).sum();
            int utilPct = ec > 0 ? (int) Math.round((ea / ec) * 100) : 0;
            long overEmps = util.stream()
                    .filter(x -> ((Number) x.get("allocatedFte")).doubleValue()
                            > ((Number) x.get("capacityFte")).doubleValue() + 0.0001)
                    .count();
            long underProj = projD.stream()
                    .filter(x -> ((Number) x.get("allocatedFte")).doubleValue() + 0.0001
                            < ((Number) x.get("capacityFte")).doubleValue())
                    .count();
            String status = overEmps > 0 ? "Overallocated" : (underProj > 0 ? "Understaffed" : "Balanced");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("weekKey", wk.toString());
            row.put("employeeCapacity", ec);
            row.put("employeeAllocated", ea);
            row.put("projectCapacity", pc);
            row.put("projectAllocated", pa);
            row.put("utilizationPct", utilPct);
            row.put("overloadedEmployees", overEmps);
            row.put("understaffedProjects", underProj);
            row.put("status", status);
            weeks.add(row);
        }
        int sumUtil = weeks.stream().mapToInt(x -> (Integer) x.get("utilizationPct")).sum();
        int avgUtil = weeks.isEmpty() ? 0 : Math.round((float) sumUtil / weeks.size());
        long risk = weeks.stream().filter(x -> !"Balanced".equals(x.get("status"))).count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("avgUtilizationPct", avgUtil);
        summary.put("riskWeeks", risk);
        summary.put("balancedWeeks", weeks.size() - risk);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("startWeekKey", start.toString());
        out.put("endWeekKey", start.plusWeeks(n - 1).toString());
        out.put("weeksCount", n);
        out.put("weeks", weeks);
        out.put("projectIssues", projectIssues);
        out.put("summary", summary);
        return out;
    }

    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
