package com.example.loaddist.model;

import jakarta.persistence.*;

@Entity
@Table(name = "ld_app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 320)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 200)
    private String passwordHash;

    /** "manager" (app admin) or "employee" — matches Node users.json */
    @Column(nullable = false, length = 32)
    private String role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(name = "username_manual", nullable = false)
    private boolean usernameManual;

    public AppUser() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }

    public boolean isUsernameManual() { return usernameManual; }
    public void setUsernameManual(boolean usernameManual) { this.usernameManual = usernameManual; }
}
