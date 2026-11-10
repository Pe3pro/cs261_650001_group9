package com.example.demo.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * U8: Entity ตาราง WORK_LOG บันทึกการทำงาน 1 วัน
 * unique (internship_id, log_date) กันบันทึกซ้ำวันเดียวกัน
 */
@Entity
@Table(name = "work_log", uniqueConstraints = @UniqueConstraint(
        name = "uk_work_log_internship_date", columnNames = { "internship_id", "log_date" }))
public class WorkLog {

    /** ส่งหลังวันที่บันทึกเกินกี่วันจึงนับว่าส่งล่าช้า */
    public static final int LATE_AFTER_DAYS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "internship_id", nullable = false)
    private Long internshipId;

    /** นักศึกษาเจ้าของบันทึก (USERS.id) */
    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /** 0.5 ถึง 12 ชั่วโมง คำนวณจาก startTime–endTime */
    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal hours;

    @Column(nullable = false, length = 2000)
    private String activity;

    @Column(length = 2000)
    private String learning;

    @Column(length = 2000)
    private String problem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkLogStatus status = WorkLogStatus.DRAFT;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /** อาจารย์ที่ตรวจ (USERS.id) */
    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_comment", length = 1000)
    private String reviewComment;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected WorkLog() {
        // สำหรับ JPA
    }

    public WorkLog(Long internshipId, Long studentId, LocalDate logDate) {
        this.internshipId = internshipId;
        this.studentId = studentId;
        this.logDate = logDate;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** แก้ไข / แนบไฟล์ / ส่งได้เฉพาะ DRAFT หรือ NEED_REVISION */
    public boolean isEditable() {
        return status == WorkLogStatus.DRAFT || status == WorkLogStatus.NEED_REVISION;
    }

    /** ส่งให้อาจารย์ตรวจ */
    public void submit(LocalDateTime now) {
        this.status = WorkLogStatus.SUBMITTED;
        this.submittedAt = now;
    }

    /** อาจารย์อนุมัติ หรือส่งกลับแก้ไข */
    public void review(ReviewDecision decision, Long reviewerId, String comment, LocalDateTime now) {
        this.status = decision == ReviewDecision.APPROVE ? WorkLogStatus.APPROVED : WorkLogStatus.NEED_REVISION;
        this.reviewedBy = reviewerId;
        this.reviewedAt = now;
        this.reviewComment = comment == null || comment.isBlank() ? null : comment.trim();
    }

    /** ส่งหลังวันที่บันทึกเกิน 3 วัน */
    public boolean isLateSubmitted() {
        return submittedAt != null && submittedAt.toLocalDate().isAfter(logDate.plusDays(LATE_AFTER_DAYS));
    }

    public Long getId() { return id; }
    public Long getInternshipId() { return internshipId; }
    public Long getStudentId() { return studentId; }
    public LocalDate getLogDate() { return logDate; }
    public void setLogDate(LocalDate logDate) { this.logDate = logDate; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public BigDecimal getHours() { return hours; }
    public void setHours(BigDecimal hours) { this.hours = hours; }
    public String getActivity() { return activity; }
    public void setActivity(String activity) { this.activity = activity; }
    public String getLearning() { return learning; }
    public void setLearning(String learning) { this.learning = learning; }
    public String getProblem() { return problem; }
    public void setProblem(String problem) { this.problem = problem; }
    public WorkLogStatus getStatus() { return status; }
    public void setStatus(WorkLogStatus status) { this.status = status; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public Long getReviewedBy() { return reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public String getReviewComment() { return reviewComment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
