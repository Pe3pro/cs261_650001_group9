package com.example.demo.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entity ตาราง INTERNSHIP: การฝึกงานของนักศึกษา 1 ครั้ง
 * เป็นของ US7 (ยังไม่มีในโปรเจกต์) จึงสร้างเฉพาะคอลัมน์ที่ US8 ใช้ตาม ER Diagram ไว้ก่อน
 */
@Entity
@Table(name = "internship")
public class Internship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** นักศึกษาที่ฝึกงาน (USERS.id) */
    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InternshipStatus status;

    protected Internship() {
        // สำหรับ JPA
    }

    public Internship(Long studentId, LocalDate startDate, LocalDate endDate, InternshipStatus status) {
        this.studentId = studentId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
    }

    public Long getId() { return id; }
    public Long getStudentId() { return studentId; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public InternshipStatus getStatus() { return status; }
    public void setStatus(InternshipStatus status) { this.status = status; }
}
