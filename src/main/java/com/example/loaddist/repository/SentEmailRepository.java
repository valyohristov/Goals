package com.example.loaddist.repository;

import com.example.loaddist.model.SentEmail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SentEmailRepository extends JpaRepository<SentEmail, String> {

    List<SentEmail> findTop500ByOrderBySentAtDesc();
}
