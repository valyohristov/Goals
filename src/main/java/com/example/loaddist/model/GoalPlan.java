package com.example.loaddist.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ld_goal_plan")
public class GoalPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "goal_year", nullable = false)
    private int goalYear;

    @OneToMany(mappedBy = "goalPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GoalSlot> goalSlots = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }

    public int getGoalYear() { return goalYear; }
    public void setGoalYear(int goalYear) { this.goalYear = goalYear; }

    public List<GoalSlot> getGoalSlots() { return goalSlots; }
    public void setGoalSlots(List<GoalSlot> goalSlots) { this.goalSlots = goalSlots; }
}
