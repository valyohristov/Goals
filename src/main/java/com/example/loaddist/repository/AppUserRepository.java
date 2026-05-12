package com.example.loaddist.repository;

import com.example.loaddist.model.AppUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    @EntityGraph(attributePaths = "employee")
    Optional<AppUser> findByUsernameIgnoreCase(String username);

    @EntityGraph(attributePaths = "employee")
    Optional<AppUser> findByEmployee_Id(Long employeeId);
}
