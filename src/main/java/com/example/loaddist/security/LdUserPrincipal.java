package com.example.loaddist.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public class LdUserPrincipal implements UserDetails {

    private final Long userId;
    private final String username;
    private final String passwordHash;
    private final String role;
    private final Long employeeId;
    private final Collection<? extends GrantedAuthority> authorities;

    public LdUserPrincipal(Long userId, String username, String passwordHash, String role,
                           Long employeeId, Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.employeeId = employeeId;
        this.authorities = authorities;
    }

    public Long getUserId() { return userId; }

    public String getRole() { return role; }

    public Long getEmployeeId() { return employeeId; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}
