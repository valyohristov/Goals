package com.example.loaddist.repository;

import com.example.loaddist.model.Allocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AllocationRepository extends JpaRepository<Allocation, Long> {

    List<Allocation> findByWeekStartDateOrderById(LocalDate weekStartDate);

    boolean existsByWeekStartDate(LocalDate weekStartDate);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Allocation a where a.employee.id = :eid and a.weekStartDate = :w")
    void deleteByEmployeeIdAndWeekStartDate(@Param("eid") Long employeeId, @Param("w") LocalDate w);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Allocation a where a.weekStartDate = :w")
    void deleteByWeekStartDate(@Param("w") LocalDate w);

    List<Allocation> findByEmployeeIdAndWeekStartDate(Long employeeId, LocalDate weekStartDate);

    @Query("select a from Allocation a join fetch a.employee join fetch a.project where a.weekStartDate = :w order by a.id")
    List<Allocation> findByWeekStartWithAssociations(@Param("w") LocalDate weekStartDate);

    @Query("select distinct a.weekStartDate from Allocation a order by a.weekStartDate desc")
    List<LocalDate> findDistinctWeekStartsOrderByWeekStartDateDesc();

    @Query("select coalesce(sum(a.fte), 0) from Allocation a where a.employee.id = :employeeId and a.weekStartDate = :week and (:excludeId is null or a.id <> :excludeId)")
    java.math.BigDecimal sumFteByEmployeeAndWeekExcluding(Long employeeId, LocalDate week, Long excludeId);

    @Query("select coalesce(sum(a.fte), 0) from Allocation a where a.project.id = :projectId and a.weekStartDate = :week and (:excludeId is null or a.id <> :excludeId)")
    java.math.BigDecimal sumFteByProjectAndWeekExcluding(Long projectId, LocalDate week, Long excludeId);

    Optional<Allocation> findByEmployeeIdAndProjectIdAndWeekStartDate(Long employeeId, Long projectId, LocalDate weekStartDate);

    @Query("select a from Allocation a join fetch a.employee join fetch a.project where a.id = :id")
    Optional<Allocation> findByIdWithAssociations(@Param("id") Long id);
}
