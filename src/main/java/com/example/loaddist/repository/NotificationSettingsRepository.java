package com.example.loaddist.repository;

import com.example.loaddist.model.NotificationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSettingsRepository extends JpaRepository<NotificationSettings, Byte> {
}
