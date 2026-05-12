package com.example.loaddist.security;

import com.example.loaddist.model.AppUser;
import com.example.loaddist.repository.AppUserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LdUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public LdUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser u = appUserRepository.findByUsernameIgnoreCase(username.trim())
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        Long empId = u.getEmployee() != null ? u.getEmployee().getId() : null;
        String r = u.getRole() != null ? u.getRole().toUpperCase() : "EMPLOYEE";
        return new LdUserPrincipal(
                u.getId(),
                u.getUsername(),
                u.getPasswordHash(),
                u.getRole(),
                empId,
                List.of(new SimpleGrantedAuthority("ROLE_" + r))
        );
    }
}
