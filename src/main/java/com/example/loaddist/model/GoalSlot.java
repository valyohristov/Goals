package com.example.loaddist.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ld_goal_slot")
public class GoalSlot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_plan_id", nullable = false)
    private GoalPlan goalPlan;

    @Column(name = "slot_index", nullable = false)
    private int slotIndex;

    @Column(length = 200, nullable = false)
    private String title = "";

    @Column(length = 4000, nullable = false)
    private String description = "";

    @OneToMany(mappedBy = "goalSlot", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GoalMonthRow> months = new ArrayList<>();

    @OneToMany(mappedBy = "goalSlot", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GoalCheckinRow> checkIns = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public GoalPlan getGoalPlan() { return goalPlan; }
    public void setGoalPlan(GoalPlan goalPlan) { this.goalPlan = goalPlan; }

    public int getSlotIndex() { return slotIndex; }
    public void setSlotIndex(int slotIndex) { this.slotIndex = slotIndex; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<GoalMonthRow> getMonths() { return months; }
    public void setMonths(List<GoalMonthRow> months) { this.months = months; }

    public List<GoalCheckinRow> getCheckIns() { return checkIns; }
    public void setCheckIns(List<GoalCheckinRow> checkIns) { this.checkIns = checkIns; }
}
