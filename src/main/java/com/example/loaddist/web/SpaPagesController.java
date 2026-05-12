package com.example.loaddist.web;

import com.example.loaddist.repository.EmployeeRepository;
import com.example.loaddist.security.LdUserPrincipal;
import com.example.loaddist.service.GoalsService;
import com.example.loaddist.service.TeamScope;
import com.example.loaddist.util.RequestWeek;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Year;
import java.time.ZoneOffset;

@Controller
public class SpaPagesController {

    private final EmployeeRepository employeeRepository;

    public SpaPagesController(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    private static boolean isAppManager(Authentication auth) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_MANAGER".equals(a.getAuthority()));
    }

    private static String username(Authentication auth) {
        return auth != null ? auth.getName() : "";
    }

    private static String userRole(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof LdUserPrincipal p && p.getRole() != null) {
            return p.getRole();
        }
        return "employee";
    }

    @GetMapping("/")
    public String home(@RequestParam(required = false) String week,
                       Authentication auth,
                       Model model) {
        TeamScope scope = TeamScope.resolveWithRoster(auth, employeeRepository);
        boolean appManager = isAppManager(auth);
        boolean canEdit = appManager || scope.isPeopleManager();
        model.addAttribute("title", "Goals");
        model.addAttribute("initialWeekKey", RequestWeek.parseWeekParam(week).toString());
        model.addAttribute("readOnly", !canEdit);
        model.addAttribute("showFullAdminNav", appManager);
        model.addAttribute("teamLead", scope.isPeopleManager() && !appManager);
        model.addAttribute("username", username(auth));
        model.addAttribute("role", userRole(auth));
        return "home";
    }

    @GetMapping("/goals")
    public String goals(@RequestParam(required = false) String week,
                        @RequestParam(required = false) Integer year,
                        Authentication auth,
                        Model model) {
        TeamScope scope = TeamScope.resolveWithRoster(auth, employeeRepository);
        boolean appManager = isAppManager(auth);
        String navTier = appManager ? "admin" : (scope.isPeopleManager() ? "team" : "employee");
        int y = year != null ? GoalsService.sanitizeYear(year) : GoalsService.sanitizeYear(Year.now(ZoneOffset.UTC).getValue());
        model.addAttribute("title", "Year goals");
        model.addAttribute("initialYear", y);
        model.addAttribute("initialWeekKey", RequestWeek.parseWeekParam(week).toString());
        model.addAttribute("username", username(auth));
        model.addAttribute("role", userRole(auth));
        model.addAttribute("navTier", navTier);
        return "goals";
    }

    @GetMapping("/admin/employees")
    public String employees(@RequestParam(required = false) String week,
                            Authentication auth,
                            Model model) {
        TeamScope scope = TeamScope.resolveWithRoster(auth, employeeRepository);
        if (!isAppManager(auth) && !scope.isPeopleManager()) {
            return "redirect:/";
        }
        boolean app = isAppManager(auth);
        model.addAttribute("initialWeekKey", RequestWeek.parseWeekParam(week).toString());
        model.addAttribute("canAddEmployees", app);
        model.addAttribute("canDeleteEmployees", app);
        model.addAttribute("canSetPasswords", app);
        model.addAttribute("username", username(auth));
        model.addAttribute("role", userRole(auth));
        return "employees";
    }

    @GetMapping("/admin/projects")
    public String projects(@RequestParam(required = false) String week, Authentication auth, Model model) {
        if (!isAppManager(auth)) {
            return "redirect:/";
        }
        model.addAttribute("initialWeekKey", RequestWeek.parseWeekParam(week).toString());
        model.addAttribute("username", username(auth));
        model.addAttribute("role", userRole(auth));
        return "projects";
    }

    @GetMapping("/forecast")
    public String forecast(@RequestParam(required = false) String week, Authentication auth, Model model) {
        if (!isAppManager(auth)) {
            return "redirect:/";
        }
        model.addAttribute("initialWeekKey", RequestWeek.parseWeekParam(week).toString());
        model.addAttribute("username", username(auth));
        model.addAttribute("role", userRole(auth));
        return "forecast";
    }

    @GetMapping("/admin/notifications")
    public String notifications(@RequestParam(required = false) String week, Authentication auth, Model model) {
        if (!isAppManager(auth)) {
            return "redirect:/";
        }
        model.addAttribute("title", "Notifications & email");
        model.addAttribute("initialWeekKey", RequestWeek.parseWeekParam(week).toString());
        model.addAttribute("username", username(auth));
        model.addAttribute("role", userRole(auth));
        return "notifications";
    }

    @GetMapping("/admin/sent-emails")
    public String sentEmails(@RequestParam(required = false) String week,
                             @RequestParam(required = false) Integer year,
                             Authentication auth,
                             Model model) {
        if (!isAppManager(auth)) {
            return "redirect:/";
        }
        int y = year != null ? GoalsService.sanitizeYear(year) : GoalsService.sanitizeYear(Year.now(ZoneOffset.UTC).getValue());
        model.addAttribute("title", "Sent emails");
        model.addAttribute("initialWeekKey", RequestWeek.parseWeekParam(week).toString());
        model.addAttribute("initialYear", y);
        model.addAttribute("username", username(auth));
        model.addAttribute("role", userRole(auth));
        return "sent-emails";
    }
}
