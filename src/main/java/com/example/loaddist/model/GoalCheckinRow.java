package com.example.loaddist.model;

import jakarta.persistence.*;

@Entity
@Table(name = "ld_goal_checkin")
@IdClass(GoalCheckinKey.class)
public class GoalCheckinRow {

    /** Writable FK column; keep in sync with {@link #goalSlot} via {@link #setGoalSlot}. */
    @Id
    @Column(name = "goal_slot_id", nullable = false)
    private Long goalSlotId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_slot_id", nullable = false, insertable = false, updatable = false)
    private GoalSlot goalSlot;

    @Id
    @Column(name = "period_index", nullable = false)
    private Integer periodIndex;

    @Column(name = "meeting_date", length = 10, nullable = false)
    private String meetingDate = "";

    @Column(name = "employee_notes", length = 4000, nullable = false)
    private String employeeNotes = "";

    @Column(name = "manager_notes", length = 4000, nullable = false)
    private String managerNotes = "";

    public Long getGoalSlotId() { return goalSlotId; }
    public void setGoalSlotId(Long goalSlotId) { this.goalSlotId = goalSlotId; }

    public GoalSlot getGoalSlot() { return goalSlot; }
    public void setGoalSlot(GoalSlot goalSlot) {
        this.goalSlot = goalSlot;
        this.goalSlotId = goalSlot != null ? goalSlot.getId() : null;
    }

    public Integer getPeriodIndex() { return periodIndex; }
    public void setPeriodIndex(Integer periodIndex) { this.periodIndex = periodIndex; }

    public String getMeetingDate() { return meetingDate; }
    public void setMeetingDate(String meetingDate) { this.meetingDate = meetingDate; }

    public String getEmployeeNotes() { return employeeNotes; }
    public void setEmployeeNotes(String employeeNotes) { this.employeeNotes = employeeNotes; }

    public String getManagerNotes() { return managerNotes; }
    public void setManagerNotes(String managerNotes) { this.managerNotes = managerNotes; }
}
