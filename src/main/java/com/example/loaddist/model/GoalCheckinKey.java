package com.example.loaddist.model;

import java.io.Serializable;
import java.util.Objects;

public class GoalCheckinKey implements Serializable {
    private Long goalSlotId;
    private Integer periodIndex;

    public GoalCheckinKey() {}

    public GoalCheckinKey(Long goalSlotId, Integer periodIndex) {
        this.goalSlotId = goalSlotId;
        this.periodIndex = periodIndex;
    }

    public Long getGoalSlotId() { return goalSlotId; }
    public void setGoalSlotId(Long goalSlotId) { this.goalSlotId = goalSlotId; }

    public Integer getPeriodIndex() { return periodIndex; }
    public void setPeriodIndex(Integer periodIndex) { this.periodIndex = periodIndex; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GoalCheckinKey that = (GoalCheckinKey) o;
        return Objects.equals(goalSlotId, that.goalSlotId) && Objects.equals(periodIndex, that.periodIndex);
    }

    @Override
    public int hashCode() {
        return Objects.hash(goalSlotId, periodIndex);
    }
}
