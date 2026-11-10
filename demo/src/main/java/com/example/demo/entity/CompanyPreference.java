package com.example.demo.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Entity ตาราง COMPANY_PREFERENCES (U6: นักศึกษาที่ใบสมัคร APPROVED เลือกตำแหน่ง 1–5 อันดับ)
 * · ใบสมัครเดียวใช้อันดับเดียวกันซ้ำไม่ได้ (application_id, rank_no)
 * · ใบสมัครเดียวเลือกตำแหน่งเดียวกันซ้ำไม่ได้ (application_id, position_id)
 */
@Entity
@Table(name = "company_preferences", uniqueConstraints = {
        @UniqueConstraint(name = "uk_pref_application_rank", columnNames = { "application_id", "rank_no" }),
        @UniqueConstraint(name = "uk_pref_application_position", columnNames = { "application_id", "position_id" }) })
public class CompanyPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    /** id ในตาราง COMPANY_POSITIONS */
    @Column(name = "position_id", nullable = false)
    private Long positionId;

    /** อันดับ 1–5 (ใช้ rank_no เพราะ RANK เป็นคำสงวนของ MySQL 8) */
    @Column(name = "rank_no", nullable = false)
    private Integer rankNo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected CompanyPreference() {
        // สำหรับ JPA
    }

    public CompanyPreference(Long applicationId, Long positionId, Integer rankNo, LocalDateTime createdAt) {
        this.applicationId = applicationId;
        this.positionId = positionId;
        this.rankNo = rankNo;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getApplicationId() { return applicationId; }
    public Long getPositionId() { return positionId; }
    public Integer getRankNo() { return rankNo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
