package com.example.loaddist.controller;

import com.example.loaddist.service.AllocationService;
import com.example.loaddist.service.DashboardService;
import com.example.loaddist.service.dto.DashboardData;
import com.example.loaddist.util.Weeks;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;
    private final AllocationService allocationService;

    public DashboardController(DashboardService dashboardService, AllocationService allocationService) {
        this.dashboardService = dashboardService;
        this.allocationService = allocationService;
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(required = false) LocalDate week, Model model) {
        LocalDate effective = week;
        if (effective == null) {
            effective = allocationService.latestWeekStart().orElse(Weeks.mondayOf(LocalDate.now()));
        }
        LocalDate monday = Weeks.mondayOf(effective);
        DashboardData data = dashboardService.build(monday);
        model.addAttribute("data", data);
        model.addAttribute("weekParam", monday);
        return "dashboard";
    }

    @PostMapping("/dashboard/copy-week")
    public String copyWeek(
            @RequestParam LocalDate fromWeek,
            @RequestParam LocalDate toWeek,
            RedirectAttributes redirectAttributes) {
        try {
            int n = allocationService.copyWeek(fromWeek, toWeek);
            redirectAttributes.addFlashAttribute("successMessage", "Copied " + n + " allocation(s) to " + Weeks.mondayOf(toWeek) + ".");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/dashboard?week=" + Weeks.mondayOf(toWeek);
    }
}
