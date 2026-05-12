package com.example.loaddist.repository;

import com.example.loaddist.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {

    List<PasswordResetToken> findByUser_IdAndUsedIsFalse(Long userId);
}
