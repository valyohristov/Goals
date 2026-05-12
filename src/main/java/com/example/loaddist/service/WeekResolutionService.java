package com.example.loaddist.service;

import com.example.loaddist.model.Allocation;
import com.example.loaddist.model.WeekSetting;
import com.example.loaddist.repository.AllocationRepository;
import com.example.loaddist.repository.WeekSettingRepository;
import com.example.loaddist.util.Weeks;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Non-current weeks follow the previous week's allocations when not manually edited,
 * mirroring Node {@code getOrCreateWeekPlan} auto-copy behaviour.
 */
@Service
public class WeekResolutionService {

    private final WeekSettingRepository weekSettingRepository;
    private final AllocationRepository allocationRepository;

    public WeekResolutionService(WeekSettingRepository weekSettingRepository,
                                 AllocationRepository allocationRepository) {
        this.weekSettingRepository = weekSettingRepository;
        this.allocationRepository = allocationRepository;
    }

    @Transactional
    public void syncNonCurrentWeekFromPrevious(LocalDate weekMonday) {
        LocalDate current = Weeks.mondayUtcToday();
        LocalDate w = Weeks.mondayOf(weekMonday);
        WeekSetting ws = weekSettingRepository.findById(w)
                .orElseGet(() -> weekSettingRepository.save(new WeekSetting(w, false, true)));
        if (w.equals(current)) {
            return;
        }
        if (ws.isManual()) {
            return;
        }
        LocalDate prev = w.minusWeeks(1);
        WeekSetting prevSetting = weekSettingRepository.findById(prev).orElse(null);
        if (prevSetting == null || !prevSetting.isCopyToNextWeek()) {
            return;
        }
        List<Allocation> prevAlloc = allocationRepository.findByWeekStartWithAssociations(prev);
        allocationRepository.deleteByWeekStartDate(w);
        allocationRepository.flush();
        for (Allocation a : prevAlloc) {
            Allocation n = new Allocation();
            n.setEmployee(a.getEmployee());
            n.setProject(a.getProject());
            n.setWeekStartDate(w);
            n.setFte(a.getFte());
            allocationRepository.save(n);
        }
    }

    @Transactional
    public void markManual(LocalDate weekMonday) {
        LocalDate w = Weeks.mondayOf(weekMonday);
        WeekSetting ws = weekSettingRepository.findById(w).orElse(new WeekSetting(w, false, true));
        ws.setWeekStartDate(w);
        ws.setManual(true);
        weekSettingRepository.save(ws);
    }

    @Transactional
    public WeekSetting getOrCreate(LocalDate weekMonday) {
        LocalDate w = Weeks.mondayOf(weekMonday);
        return weekSettingRepository.findById(w).orElseGet(() -> weekSettingRepository.save(new WeekSetting(w, false, true)));
    }

    @Transactional
    public WeekSetting updateCopyToNext(LocalDate weekMonday, boolean copyToNextWeek) {
        LocalDate w = Weeks.mondayOf(weekMonday);
        WeekSetting ws = weekSettingRepository.findById(w).orElseGet(() -> new WeekSetting(w, false, true));
        ws.setWeekStartDate(w);
        ws.setCopyToNextWeek(copyToNextWeek);
        return weekSettingRepository.save(ws);
    }
}
