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
 * Entity ตาราง COMPANY_RANKINGS (U4: นักศึกษาจัดอันดับบริษัท 1–5)
 * หนึ่งแถว = บริษัทหนึ่งบริษัทในลำดับของนักศึกษาหนึ่งคน
 * · นักศึกษาคนเดียวใช้อันดับเดียวกันซ้ำไม่ได้ (student_id, rank_no)
 * · นักศึกษาคนเดียวเลือกบริษัทเดียวกันซ้ำไม่ได้ (student_id, company_id)
 * ใช้ชื่อคอลัมน์ rank_no เพราะ RANK เป็นคำสงวนของ MySQL 8
 */
@Entity
@Table(name = "company_rankings", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ranking_student_rank", columnNames = { "student_id", "rank_no" }),
        @UniqueConstraint(name = "uk_ranking_student_company", columnNames = { "student_id", "company_id" }) })
public class CompanyRanking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** id ในตาราง STUDENTS (ไม่ใช่รหัสนักศึกษา 10 หลัก) */
    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** อันดับ 1–5 */
    @Column(name = "rank_no", nullable = false)
    private Integer rankNo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected CompanyRanking() {
        // สำหรับ JPA
    }

    public CompanyRanking(Long studentId, Long companyId, Integer rankNo, LocalDateTime createdAt) {
        this.studentId = studentId;
        this.companyId = companyId;
        this.rankNo = rankNo;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getStudentId() { return studentId; }
    public Long getCompanyId() { return companyId; }
    public Integer getRankNo() { return rankNo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
