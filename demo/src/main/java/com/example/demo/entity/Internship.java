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
 * Entity ตาราง INTERNSHIPS: การฝึกงานของนักศึกษา 1 ครั้ง
 *
 * เจ้าของคลาสนี้คือ US7 (InternshipService / InternshipController ออกแบบไว้ใน T2 ของ US7 แต่ยังไม่มีโค้ด)
 * US8 ใช้แค่ id, studentId, startDate, endDate, status จึงสร้างเฉพาะ field เหล่านี้ไว้ก่อน
 * โดยใช้ชื่อตาราง ชื่อคอลัมน์ และค่าสถานะเดียวกับ US7 · ตอน merge กับ US7 ให้ใช้ไฟล์ของ US7 แทนไฟล์นี้
 * (ไฟล์ของ US7 ต้องมี getter 5 ตัวข้างต้น)
 */
@Entity
@Table(name = "internships")
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
