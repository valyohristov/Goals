package com.example.loaddist.service;

import com.example.loaddist.model.Allocation;
import com.example.loaddist.repository.AllocationRepository;
import com.example.loaddist.util.Weeks;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class AllocationService {

    private static final int SCALE = 3;

    private final AllocationRepository allocationRepository;

    public AllocationService(AllocationRepository allocationRepository) {
        this.allocationRepository = allocationRepository;
    }

    public List<Allocation> listForWeek(LocalDate week) {
        LocalDate monday = Weeks.mondayOf(week);
        return allocationRepository.findByWeekStartWithAssociations(monday);
    }

    public List<LocalDate> weekChoices() {
        return allocationRepository.findDistinctWeekStartsOrderByWeekStartDateDesc();
    }

    public Optional<LocalDate> latestWeekStart() {
        List<LocalDate> w = weekChoices();
        return w.isEmpty() ? Optional.empty() : Optional.of(w.get(0));
    }

    public Optional<Allocation> getById(Long id) {
        return allocationRepository.findByIdWithAssociations(id);
    }

    @Transactional
    public Allocation save(Allocation allocation) {
        LocalDate monday = Weeks.mondayOf(allocation.getWeekStartDate());
        allocation.setWeekStartDate(monday);
        Long excludeId = allocation.getId();
        validateCapacities(allocation, excludeId);
        return allocationRepository.save(allocation);
    }

    private void validateCapacities(Allocation allocation, Long excludeId) {
        LocalDate monday = allocation.getWeekStartDate();
        BigDecimal fte = allocation.getFte().setScale(SCALE, java.math.RoundingMode.HALF_UP);

        BigDecimal empSum = allocationRepository.sumFteByEmployeeAndWeekExcluding(
                allocation.getEmployee().getId(), monday, excludeId);
        BigDecimal empNewTotal = empSum.add(fte);
        if (empNewTotal.compareTo(allocation.getEmployee().getCapacityFte()) > 0) {
            throw new IllegalArgumentException(
                    "Total FTE for " + allocation.getEmployee().getName() + " this week would exceed their capacity.");
        }

        BigDecimal projSum = allocationRepository.sumFteByProjectAndWeekExcluding(
                allocation.getProject().getId(), monday, excludeId);
        BigDecimal projNewTotal = projSum.add(fte);
        if (projNewTotal.compareTo(allocation.getProject().getCapacityFte()) > 0) {
            throw new IllegalArgumentException(
                    "Total FTE on " + allocation.getProject().getName() + " this week would exceed project capacity.");
        }
    }

    @Transactional
    public void delete(Long id) {
        allocationRepository.deleteById(id);
    }

    @Transactional
    public int copyWeek(LocalDate fromWeek, LocalDate toWeek) {
        LocalDate from = Weeks.mondayOf(fromWeek);
        LocalDate to = Weeks.mondayOf(toWeek);
        if (from.equals(to)) {
            return 0;
        }
        List<Allocation> source = allocationRepository.findByWeekStartWithAssociations(from);
        int n = 0;
        for (Allocation a : source) {
            Optional<Allocation> existing = allocationRepository.findByEmployeeIdAndProjectIdAndWeekStartDate(
                    a.getEmployee().getId(), a.getProject().getId(), to);
            if (existing.isPresent()) {
                continue;
            }
            Allocation copy = new Allocation(a.getEmployee(), a.getProject(), to, a.getFte());
            try {
                validateCapacities(copy, null);
                allocationRepository.save(copy);
                n++;
            } catch (IllegalArgumentException ignored) {
                // skip rows that would exceed capacity in target week
            }
        }
        return n;
    }
}
