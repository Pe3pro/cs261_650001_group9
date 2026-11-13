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

/**
 * Entity ตาราง MATCHES (U6: ผลการจับคู่ใบสมัครกับตำแหน่ง ในรอบหนึ่ง)
 * การคืนโควตา (CompanyPosition.releaseSlot) ทำใน MatchingService เมื่อ DECLINED / CANCELLED
 * ชื่อ entity ใน JPQL คือ MatchResult (เลี่ยงคำว่า MATCH ที่เป็นคำสงวนของ SQL)
 */
@Entity(name = "MatchResult")
@Table(name = "matches")
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "position_id", nullable = false)
    private Long positionId;

    @Column(name = "round_id", nullable = false)
    private Long roundId;

    /** อันดับที่ได้ (null = เจ้าหน้าที่จับคู่เองกับตำแหน่งที่นักศึกษาไม่ได้เลือก) */
    @Column(name = "rank_no")
    private Integer rankNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchStatus status;

    /** users.id ของเจ้าหน้าที่ที่สั่งจับคู่ */
    @Column(name = "matched_by", nullable = false)
    private Long matchedBy;

    @Column(name = "matched_at", nullable = false)
    private LocalDateTime matchedAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "decline_reason", length = 500)
    private String declineReason;

    protected Match() {
        // สำหรับ JPA
    }

    public Match(Long applicationId, Long positionId, Long roundId, Integer rankNo, Long matchedBy,
            LocalDateTime matchedAt) {
        this.applicationId = applicationId;
        this.positionId = positionId;
        this.roundId = roundId;
        this.rankNo = rankNo;
        this.matchedBy = matchedBy;
        this.matchedAt = matchedAt;
        this.status = MatchStatus.PROPOSED;
    }

    /** PROPOSED → CONFIRMED */
    public void confirm(LocalDateTime now) {
        requireStatus(MatchStatus.PROPOSED);
        this.status = MatchStatus.CONFIRMED;
        this.respondedAt = now;
    }

    /** PROPOSED → DECLINED (สละสิทธิ์ หรือไม่ตอบภายในกำหนด) */
    public void decline(String reason, LocalDateTime now) {
        requireStatus(MatchStatus.PROPOSED);
        this.status = MatchStatus.DECLINED;
        this.declineReason = reason;
        this.respondedAt = now;
    }

    /** PROPOSED / CONFIRMED → CANCELLED (เจ้าหน้าที่ยกเลิก) */
    public void cancel() {
        if (!status.isActive()) {
            throw new IllegalStateException("ยกเลิกได้เฉพาะผลที่ยังใช้อยู่");
        }
        this.status = MatchStatus.CANCELLED;
    }

    private void requireStatus(MatchStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("สถานะปัจจุบันคือ " + status);
        }
    }

    public Long getId() { return id; }
    public Long getApplicationId() { return applicationId; }
    public Long getPositionId() { return positionId; }
    public Long getRoundId() { return roundId; }
    public Integer getRankNo() { return rankNo; }
    public MatchStatus getStatus() { return status; }
    public Long getMatchedBy() { return matchedBy; }
    public LocalDateTime getMatchedAt() { return matchedAt; }
    public LocalDateTime getRespondedAt() { return respondedAt; }
    public String getDeclineReason() { return declineReason; }
}
