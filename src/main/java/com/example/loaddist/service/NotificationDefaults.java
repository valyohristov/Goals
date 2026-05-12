package com.example.loaddist.service;

public final class NotificationDefaults {

    private NotificationDefaults() {}

    public static final String MGR_SUBJ = "Action: schedule a goals check-in with {{employeeName}}";
    public static final String EMP_SUBJ = "Reminder {{reminderNumber}}/3: bimonthly goals check-in this month ({{periodLabel}})";
    public static final String MGR_JAN_SUBJ = "Action: help {{employeeName}} capture {{goalYear}} annual goals";
    public static final String EMP_JAN_SUBJ = "Reminder: enter your {{goalYear}} annual goals in Goals";

    public static final String MGR_BODY = String.join("\n",
            "Hello {{managerName}},", "",
            "Please organize a meeting with {{employeeName}} for their annual goals bimonthly check-in.", "",
            "Period: {{periodLabel}} (this two-month cycle ends {{periodEndDescription}}).", "",
            "After the meeting, capture notes in Goals under Year goals — Check-in for this period.", "",
            "Open goals: {{goalsUrl}}", "",
            "Regards,", "{{fromName}}");

    public static final String EMP_BODY = String.join("\n",
            "Hello {{employeeName}},", "",
            "This is reminder {{reminderNumber}} of 3 this month: you have a bimonthly goals check-in with your manager at the end of this two-month period.", "",
            "Period: {{periodLabel}} (through {{periodEndDescription}}). Your manager: {{managerName}}.", "",
            "Please agree a meeting date in time to complete your check-in in the app.", "",
            "Open goals: {{goalsUrl}}", "",
            "Regards,", "{{fromName}}");

    public static final String MGR_JAN_BODY = String.join("\n",
            "Hello {{managerName}},", "",
            "January is reserved for agreeing annual objectives. Please make time early in January with {{employeeName}} so they can enter their {{goalYear}} goals in the Goals app (two goals with monthly outcomes). Regular bimonthly check-ins follow once objectives exist.", "",
            "Planning window: {{periodLabel}}. The milestone for this period is {{periodEndDescription}} — the priority now is documenting the yearly plan in the system during January.", "",
            "Open goals: {{goalsUrl}}", "",
            "Regards,", "{{fromName}}");

    public static final String EMP_JAN_BODY = String.join("\n",
            "Hello {{employeeName}},", "",
            "January is dedicated to capturing your yearly objectives. Please record your {{goalYear}} annual goals in Goals quickly and involve {{managerName}} if you need support.", "",
            "Period: {{periodLabel}} — this stretch concludes {{periodEndDescription}}, but completing your annual goals belongs in January before the usual check-in reminders continue.", "",
            "Open goals: {{goalsUrl}}", "",
            "Regards,", "{{fromName}}");
}
