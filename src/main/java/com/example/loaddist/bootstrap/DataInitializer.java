package com.example.loaddist.bootstrap;

import com.example.loaddist.model.AppUser;
import com.example.loaddist.repository.AppUserRepository;
import com.example.loaddist.repository.EmployeeRepository;
import com.example.loaddist.service.NotificationSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    ApplicationRunner seedAdmin(AppUserRepository users,
                                EmployeeRepository employees,
                                PasswordEncoder encoder,
                                NotificationSettingsService notificationSettingsService,
                                @Value("${app.admin-username:admin}") String adminUser,
                                @Value("${app.admin-password:MegaSecret#1}") String adminPass) {
        return args -> {
            if (users.count() == 0) {
                AppUser a = new AppUser();
                a.setUsername(adminUser.toLowerCase());
                a.setPasswordHash(encoder.encode(adminPass));
                a.setRole("manager");
                a.setUsernameManual(true);
                users.save(a);
            }
            if (employees.count() == 0) {
                log.warn("Table ld_employees is empty. Year goals and the employee picker need people — add them under Manage Employees or ensure Flyway migrations (e.g. V2__ld_seed.sql) have run against this database.");
            }
            notificationSettingsService.ensureDefaultsPersisted();
        };
    }
}
