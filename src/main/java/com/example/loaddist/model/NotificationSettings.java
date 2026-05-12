package com.example.loaddist.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ld_notification_settings")
public class NotificationSettings {

    public static final byte SINGLETON_ID = 1;

    @Id
    private byte id = SINGLETON_ID;

    @Column(name = "notifications_enabled", nullable = false)
    private boolean notificationsEnabled;

    @Column(name = "tenant_id", length = 128, nullable = false)
    private String tenantId = "";

    @Column(name = "client_id", length = 128, nullable = false)
    private String clientId = "";

    @Column(name = "client_secret", length = 512, nullable = false)
    private String clientSecret = "";

    @Column(name = "sender_user", length = 320, nullable = false)
    private String senderUser = "";

    @Column(name = "from_name", length = 200, nullable = false)
    private String fromName = "Goals";

    @Column(name = "from_email", length = 320, nullable = false)
    private String fromEmail = "";

    @Column(name = "employee_email_subject", length = 500, nullable = false)
    private String employeeEmailSubject = "";

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "employee_email_body")
    private String employeeEmailBody;

    @Column(name = "manager_email_subject", length = 500, nullable = false)
    private String managerEmailSubject = "";

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "manager_email_body")
    private String managerEmailBody;

    @Column(name = "manager_goal_setting_january_subject", length = 500, nullable = false)
    private String managerGoalSettingJanuarySubject = "";

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "manager_goal_setting_january_body")
    private String managerGoalSettingJanuaryBody;

    @Column(name = "employee_goal_setting_january_subject", length = 500, nullable = false)
    private String employeeGoalSettingJanuarySubject = "";

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "employee_goal_setting_january_body")
    private String employeeGoalSettingJanuaryBody;

    public byte getId() { return id; }
    public void setId(byte id) { this.id = id; }

    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public void setNotificationsEnabled(boolean notificationsEnabled) { this.notificationsEnabled = notificationsEnabled; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public String getSenderUser() { return senderUser; }
    public void setSenderUser(String senderUser) { this.senderUser = senderUser; }

    public String getFromName() { return fromName; }
    public void setFromName(String fromName) { this.fromName = fromName; }

    public String getFromEmail() { return fromEmail; }
    public void setFromEmail(String fromEmail) { this.fromEmail = fromEmail; }

    public String getEmployeeEmailSubject() { return employeeEmailSubject; }
    public void setEmployeeEmailSubject(String employeeEmailSubject) { this.employeeEmailSubject = employeeEmailSubject; }

    public String getEmployeeEmailBody() { return employeeEmailBody; }
    public void setEmployeeEmailBody(String employeeEmailBody) { this.employeeEmailBody = employeeEmailBody; }

    public String getManagerEmailSubject() { return managerEmailSubject; }
    public void setManagerEmailSubject(String managerEmailSubject) { this.managerEmailSubject = managerEmailSubject; }

    public String getManagerEmailBody() { return managerEmailBody; }
    public void setManagerEmailBody(String managerEmailBody) { this.managerEmailBody = managerEmailBody; }

    public String getManagerGoalSettingJanuarySubject() { return managerGoalSettingJanuarySubject; }
    public void setManagerGoalSettingJanuarySubject(String managerGoalSettingJanuarySubject) { this.managerGoalSettingJanuarySubject = managerGoalSettingJanuarySubject; }

    public String getManagerGoalSettingJanuaryBody() { return managerGoalSettingJanuaryBody; }
    public void setManagerGoalSettingJanuaryBody(String managerGoalSettingJanuaryBody) { this.managerGoalSettingJanuaryBody = managerGoalSettingJanuaryBody; }

    public String getEmployeeGoalSettingJanuarySubject() { return employeeGoalSettingJanuarySubject; }
    public void setEmployeeGoalSettingJanuarySubject(String employeeGoalSettingJanuarySubject) { this.employeeGoalSettingJanuarySubject = employeeGoalSettingJanuarySubject; }

    public String getEmployeeGoalSettingJanuaryBody() { return employeeGoalSettingJanuaryBody; }
    public void setEmployeeGoalSettingJanuaryBody(String employeeGoalSettingJanuaryBody) { this.employeeGoalSettingJanuaryBody = employeeGoalSettingJanuaryBody; }
}
