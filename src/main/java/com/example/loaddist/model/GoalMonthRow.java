package com.example.loaddist.model;

import jakarta.persistence.*;

@Entity
@Table(name = "ld_goal_month")
@IdClass(GoalMonthKey.class)
public class GoalMonthRow {

    /** Writable FK column; keep in sync with {@link #goalSlot} via {@link #setGoalSlot}. */
    @Id
    @Column(name = "goal_slot_id", nullable = false)
    private Long goalSlotId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_slot_id", nullable = false, insertable = false, updatable = false)
    private GoalSlot goalSlot;

    @Id
    @Column(name = "month_index", nullable = false)
    private Integer monthIndex;

    @Column(name = "doing_text", length = 8000, nullable = false)
    private String doingText = "";

    @Column(name = "outcome_text", length = 8000, nullable = false)
    private String outcomeText = "";

    public Long getGoalSlotId() { return goalSlotId; }
    public void setGoalSlotId(Long goalSlotId) { this.goalSlotId = goalSlotId; }

    public GoalSlot getGoalSlot() { return goalSlot; }
    public void setGoalSlot(GoalSlot goalSlot) {
        this.goalSlot = goalSlot;
        this.goalSlotId = goalSlot != null ? goalSlot.getId() : null;
    }

    public Integer getMonthIndex() { return monthIndex; }
    public void setMonthIndex(Integer monthIndex) { this.monthIndex = monthIndex; }

    public String getDoingText() { return doingText; }
    public void setDoingText(String doingText) { this.doingText = doingText; }

    public String getOutcomeText() { return outcomeText; }
    public void setOutcomeText(String outcomeText) { this.outcomeText = outcomeText; }
}
