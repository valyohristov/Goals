package com.example.loaddist.service;

import com.example.loaddist.model.AppUser;
import com.example.loaddist.model.Employee;
import com.example.loaddist.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class AccountService {

    private static final Pattern EMAIL_OK = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final String loginDomain;

    public AccountService(AppUserRepository appUserRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${app.employee-login-domain:idvkm.com}") String loginDomain) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginDomain = loginDomain;
    }

    public String slugUsername(String name) {
        String[] parts = name.toLowerCase(Locale.ROOT).trim().split("\\s+");
        String local;
        if (parts.length == 0) local = "user";
        else if (parts.length == 1) local = parts[0].replaceAll("[^a-z0-9]", "") + ".user";
        else {
            local = parts[0].replaceAll("[^a-z0-9]", "") + "." + parts[parts.length - 1].replaceAll("[^a-z0-9]", "");
        }
        if (local.isBlank()) local = "user";
        return local + "@" + loginDomain;
    }

    @Transactional
    public AppUser ensureEmployeeLogin(Employee employee) {
        return appUserRepository.findByEmployee_Id(employee.getId()).orElseGet(() -> {
            String base = slugUsername(employee.getName());
            String username = uniqueUsername(base);
            AppUser u = new AppUser();
            u.setUsername(username);
            u.setPasswordHash(passwordEncoder.encode("employee"));
            u.setRole("employee");
            u.setEmployee(employee);
            u.setUsernameManual(false);
            return appUserRepository.save(u);
        });
    }

    private String uniqueUsername(String emailLike) {
        int at = emailLike.lastIndexOf('@');
        String local = at >= 0 ? emailLike.substring(0, at) : emailLike;
        String dom = at >= 0 ? emailLike.substring(at) : "@" + loginDomain;
        int n = 0;
        String candidate = local + dom;
        while (appUserRepository.findByUsernameIgnoreCase(candidate).isPresent()) {
            n++;
            candidate = local + "." + n + dom;
        }
        return candidate.toLowerCase(Locale.ROOT);
    }

    @Transactional
    public void setEmployeePassword(long employeeId, String rawPassword, String employeeNameFallback) {
        if (rawPassword == null || rawPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
        AppUser u = appUserRepository.findByEmployee_Id(employeeId).orElse(null);
        if (u == null) {
            throw new IllegalArgumentException("No login for employee");
        }
        u.setPasswordHash(passwordEncoder.encode(rawPassword));
        appUserRepository.save(u);
    }

    @Transactional
    public void setEmployeeUsername(long employeeId, String newUsernameRaw) {
        String normalized = newUsernameRaw.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < 3 || normalized.length() > 200 || normalized.contains(" ")
                || !normalized.contains("@") || !normalized.substring(normalized.indexOf('@') + 1).contains(".")) {
            throw new IllegalArgumentException("Username must be a single email-style address (user@domain.tld).");
        }
        AppUser u = appUserRepository.findByEmployee_Id(employeeId).orElseThrow();
        appUserRepository.findByUsernameIgnoreCase(normalized).ifPresent(other -> {
            if (!other.getId().equals(u.getId())) {
                throw new IllegalArgumentException("That username is already in use.");
            }
        });
        u.setUsername(normalized);
        u.setUsernameManual(true);
        appUserRepository.save(u);
    }

    @Transactional
    public void deleteLoginForEmployee(long employeeId) {
        appUserRepository.findByEmployee_Id(employeeId).ifPresent(appUserRepository::delete);
    }

    public String getLoginUsername(long employeeId) {
        return appUserRepository.findByEmployee_Id(employeeId).map(AppUser::getUsername).orElse("");
    }

    public String loginEmailOrEmpty(Long employeeId) {
        if (employeeId == null) return "";
        return appUserRepository.findByEmployee_Id(employeeId)
                .map(AppUser::getUsername)
                .filter(s -> EMAIL_OK.matcher(s).matches())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .orElse("");
    }
}
