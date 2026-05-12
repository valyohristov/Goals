package com.example.loaddist.repository;

import com.example.loaddist.model.WeekSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface WeekSettingRepository extends JpaRepository<WeekSetting, LocalDate> {
}
