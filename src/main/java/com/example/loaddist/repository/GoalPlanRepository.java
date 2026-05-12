package com.example.loaddist.repository;

import com.example.loaddist.model.GoalPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GoalPlanRepository extends JpaRepository<GoalPlan, Long> {

    /** Join-fetch only goalSlots; fetching two List collections under each slot causes MultipleBagFetchException. */
    @Query("select distinct g from GoalPlan g left join fetch g.goalSlots where g.employee.id = :eid and g.goalYear = :y")
    Optional<GoalPlan> findFetchedByEmployeeAndYear(@Param("eid") Long employeeId, @Param("y") int goalYear);

    Optional<GoalPlan> findByEmployeeIdAndGoalYear(Long employeeId, int goalYear);
}
