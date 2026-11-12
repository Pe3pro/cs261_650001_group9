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
import jakarta.persistence.UniqueConstraint;

/**
 * Entity ตาราง MATCHES (U6: ผลการจับคู่ใบสมัครกับตำแหน่ง ในรอบหนึ่ง)
 * การคืนโควตา (CompanyPosition.releaseSlot) ทำใน MatchingService เมื่อ DECLINED / CANCELLED
 * ชื่อ entity ใน JPQL คือ MatchResult (เลี่ยงคำว่า MATCH ที่เป็นคำสงวนของ SQL)
 * 1 ใบสมัครมีผลที่ใช้อยู่ (PROPOSED / CONFIRMED) ได้ 1 รายการ: unique บน active_application_id
 */
@Entity(name = "MatchResult")
@Table(name = "matches", uniqueConstraints = {
        @UniqueConstraint(name = Match.UK_ACTIVE_APPLICATION, columnNames = { "active_application_id" }) })
public class Match {

    /** ชื่อ constraint ที่ ApiExceptionHandler ใช้แปลงเป็น 409 ALREADY_MATCHED */
    public static final String UK_ACTIVE_APPLICATION = "uk_match_active_application";

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

    /**
     * = applicationId ขณะสถานะ PROPOSED / CONFIRMED · เป็น null เมื่อ DECLINED / CANCELLED (null ซ้ำกันได้)
     * ใช้กับ unique constraint กันใบสมัครเดียวมีผลที่ใช้อยู่ 2 รายการ แม้ request มาพร้อมกัน (T2 ข้อ S7)
     * entity ตั้งค่าเองใน constructor / decline / cancel (ไม่ใช้ generated column เพราะ H2 ที่ใช้ตอน test ไม่รองรับ)
     */
    @Column(name = "active_application_id")
    private Long activeApplicationId;

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
        this.activeApplicationId = applicationId;
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
        this.activeApplicationId = null;
        this.declineReason = reason;
        this.respondedAt = now;
    }

    /** PROPOSED / CONFIRMED → CANCELLED (เจ้าหน้าที่ยกเลิก) */
    public void cancel() {
        if (!status.isActive()) {
            throw new IllegalStateException("ยกเลิกได้เฉพาะผลที่ยังใช้อยู่");
        }
        this.status = MatchStatus.CANCELLED;
        this.activeApplicationId = null;
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
    public Long getActiveApplicationId() { return activeApplicationId; }
}
