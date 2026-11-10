package com.example.demo.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Entity ตาราง MATCHING_ROUNDS (U6: รอบการจับคู่ เปิดได้ทีละรอบ) */
@Entity
@Table(name = "matching_rounds")
public class MatchingRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "round_no", nullable = false, unique = true)
    private Integer roundNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoundStatus status;

    /** users.id ของเจ้าหน้าที่ที่เปิดรอบ */
    @Column(name = "opened_by", nullable = false)
    private Long openedBy;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "announced_at")
    private LocalDateTime announcedAt;

    /** นักศึกษาต้องยืนยัน / สละสิทธิ์ก่อนเวลานี้ */
    @Column(name = "confirm_deadline", nullable = false)
    private LocalDateTime confirmDeadline;

    protected MatchingRound() {
        // สำหรับ JPA
    }

    public MatchingRound(Integer roundNo, Long openedBy, LocalDateTime openedAt, LocalDateTime confirmDeadline) {
        this.roundNo = roundNo;
        this.openedBy = openedBy;
        this.openedAt = openedAt;
        this.confirmDeadline = confirmDeadline;
        this.status = RoundStatus.OPEN;
    }

    /** OPEN → ANNOUNCED */
    public void announce(LocalDateTime now) {
        if (status != RoundStatus.OPEN) {
            throw new IllegalStateException("ประกาศผลได้เฉพาะรอบที่เปิดอยู่");
        }
        this.status = RoundStatus.ANNOUNCED;
        this.announcedAt = now;
    }

    /** ANNOUNCED → CLOSED */
    public void close() {
        if (status != RoundStatus.ANNOUNCED) {
            throw new IllegalStateException("ปิดได้เฉพาะรอบที่ประกาศผลแล้ว");
        }
        this.status = RoundStatus.CLOSED;
    }

    /** ประกาศผลแล้ว (นักศึกษาเห็นผลได้) */
    public boolean isAnnounced() {
        return status == RoundStatus.ANNOUNCED || status == RoundStatus.CLOSED;
    }

    public boolean isDeadlinePassed(LocalDateTime now) {
        return now.isAfter(confirmDeadline);
    }

    public Long getId() { return id; }
    public Integer getRoundNo() { return roundNo; }
    public RoundStatus getStatus() { return status; }
    public Long getOpenedBy() { return openedBy; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public LocalDateTime getAnnouncedAt() { return announcedAt; }
    public LocalDateTime getConfirmDeadline() { return confirmDeadline; }
}
