package com.example.demo.entity;

import java.time.LocalDate;
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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "internships",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_internships_match",
                        columnNames = "match_id"
                )
        }
)
public class Internship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "match_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(
                    name = "fk_internships_match"
            )
    )
    private Match match;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "student_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(
                    name = "fk_internships_student"
            )
    )
    private Student student;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "supervisor_name")
    private String supervisorName;

    @Column(name = "supervisor_email")
    private String supervisorEmail;

    @Column(name = "supervisor_phone")
    private String supervisorPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InternshipStatus status;

    @Column(name = "terminated_reason", length = 1000)
    private String terminatedReason;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "updated_by",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(
                    name = "fk_internships_updated_by"
            )
    )
    private User updatedByUser;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Internship() {
        // สำหรับ JPA
    }

    public Internship(
            Long matchId,
            Long studentId,
            Long updatedBy,
            LocalDateTime now
    ) {
        this.matchId = matchId;
        this.studentId = studentId;
        this.updatedBy = updatedBy;
        this.status = InternshipStatus.NOT_STARTED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateDetails(
            LocalDate startDate,
            LocalDate endDate,
            String supervisorName,
            String supervisorEmail,
            String supervisorPhone,
            Long updatedBy,
            LocalDateTime now
    ) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.supervisorName = trim(supervisorName);
        this.supervisorEmail = trim(supervisorEmail);
        this.supervisorPhone = trim(supervisorPhone);
        this.updatedBy = updatedBy;
        this.updatedAt = now;
    }

    public void changeStatus(
            InternshipStatus target,
            String reason,
            Long updatedBy,
            LocalDateTime now
    ) {
        if (!status.canMoveTo(target)) {
            throw new IllegalStateException(
                    "ไม่สามารถเปลี่ยนสถานะจาก "
                            + status
                            + " เป็น "
                            + target
            );
        }

        this.status = target;
        this.updatedBy = updatedBy;
        this.updatedAt = now;

        if (target == InternshipStatus.TERMINATED) {
            this.terminatedReason = trim(reason);
        }
    }

    public boolean infoComplete() {
        return startDate != null
                && endDate != null
                && hasText(supervisorName)
                && hasText(supervisorEmail)
                && hasText(supervisorPhone);
    }

    public boolean isClosed() {
        return status.isClosed();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    public Long getId() {
        return id;
    }

    public Long getMatchId() {
        return matchId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getSupervisorName() {
        return supervisorName;
    }

    public String getSupervisorEmail() {
        return supervisorEmail;
    }

    public String getSupervisorPhone() {
        return supervisorPhone;
    }

    public InternshipStatus getStatus() {
        return status;
    }

    public String getTerminatedReason() {
        return terminatedReason;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}