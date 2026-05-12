package com.example.loaddist.service;

import com.example.loaddist.security.LdUserPrincipal;
import org.springframework.security.core.Authentication;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Mirrors Node getTeamScopeForRequest using global Employee.manager. */
public final class TeamScope {

    private final boolean appManager;
    private final boolean peopleManager;
    private final List<Long> teamEmployeeIds;
    private final Long selfEmployeeId;

    public TeamScope(boolean appManager, boolean peopleManager, List<Long> teamEmployeeIds, Long selfEmployeeId) {
        this.appManager = appManager;
        this.peopleManager = peopleManager;
        this.teamEmployeeIds = teamEmployeeIds != null ? teamEmployeeIds : List.of();
        this.selfEmployeeId = selfEmployeeId;
    }

    public static TeamScope fromAuthentication(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof LdUserPrincipal p)) {
            return new TeamScope(false, false, List.of(), null);
        }
        if ("manager".equalsIgnoreCase(p.getRole())) {
            return new TeamScope(true, false, null, null);
        }
        Long self = p.getEmployeeId();
        if (self == null) {
            return new TeamScope(false, false, List.of(), null);
        }
        return new TeamScope(false, false, List.of(self), self);
    }

    public TeamScope withTeamDetails(boolean peopleManager, List<Long> teamIds) {
        return new TeamScope(appManager, peopleManager, teamIds, selfEmployeeId);
    }

    public boolean isAppManager() { return appManager; }

    public boolean isPeopleManager() { return peopleManager; }

    public List<Long> getTeamEmployeeIds() { return teamEmployeeIds; }

    public Long getSelfEmployeeId() { return selfEmployeeId; }

    public boolean canAccessEmployee(long employeeId) {
        if (appManager) return true;
        return teamEmployeeIds.contains(employeeId);
    }

    public static TeamScope resolveWithRoster(
            Authentication auth,
            com.example.loaddist.repository.EmployeeRepository employeeRepository) {
        TeamScope base = TeamScope.fromAuthentication(auth);
        if (base.isAppManager()) {
            return base;
        }
        Long self = base.getSelfEmployeeId();
        if (self == null) {
            return base;
        }
        return employeeRepository.findById(self)
                .map(selfEmp -> {
                    boolean isLead = selfEmp.isManager();
                    if (!isLead) {
                        return base.withTeamDetails(false, List.of(self));
                    }
                    List<Long> team = new ArrayList<>();
                    team.add(self);
                    employeeRepository.findAllOrderByNameIgnoreCase().stream()
                            .filter(e -> e.getManager() != null && self.equals(e.getManager().getId()))
                            .forEach(e -> team.add(e.getId()));
                    return base.withTeamDetails(true, Collections.unmodifiableList(team));
                })
                .orElse(base.withTeamDetails(false, List.of(self)));
    }
}
