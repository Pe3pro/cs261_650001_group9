package com.example.demo.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "status_history")
public class StatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "student_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(
                    name = "fk_status_history_student"
            )
    )
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HistoryType type;

    @Column(name = "from_status")
    private String fromStatus;

    @Column(name = "to_status", nullable = false)
    private String toStatus;

    @Column(name = "changed_by", nullable = false)
    private Long changedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "changed_by",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(
                    name = "fk_status_history_changed_by"
            )
    )
    private User changedByUser;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(length = 1000)
    private String note;

    protected StatusHistory() {
        // สำหรับ JPA
    }

    public StatusHistory(
            Long studentId,
            HistoryType type,
            String fromStatus,
            String toStatus,
            Long changedBy,
            LocalDateTime changedAt,
            String note
    ) {
        this.studentId = studentId;
        this.type = type;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
        this.note = note == null ? null : note.trim();
    }

    public Long getId() {
        return id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public HistoryType getType() {
        return type;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public Long getChangedBy() {
        return changedBy;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public String getNote() {
        return note;
    }
}