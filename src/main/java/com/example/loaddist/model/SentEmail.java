package com.example.loaddist.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ld_sent_emails")
public class SentEmail {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "to_addr", nullable = false, length = 320)
    private String toAddress;

    @Column(nullable = false, length = 500)
    private String subject;

    @Column(nullable = false, length = 64)
    private String kind;

    @Column(name = "trigger_kind", nullable = false, length = 64)
    private String triggerKind;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "error_msg", length = 2000)
    private String errorMessage;

    @Column(name = "planned_key", length = 400, nullable = false)
    private String plannedKey = "";

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }

    public String getToAddress() { return toAddress; }
    public void setToAddress(String toAddress) { this.toAddress = toAddress; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public String getTriggerKind() { return triggerKind; }
    public void setTriggerKind(String triggerKind) { this.triggerKind = triggerKind; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getPlannedKey() { return plannedKey; }
    public void setPlannedKey(String plannedKey) { this.plannedKey = plannedKey; }
}
