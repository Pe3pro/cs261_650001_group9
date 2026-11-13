package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Entity ตาราง STUDENTS: ข้อมูลนักศึกษา ผูกกับ USERS ด้วย userId (ใช้ร่วมกับ U3) */
@Entity
@Table(name = "students")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /** รหัสนักศึกษา 10 หลัก */
    @Column(name = "student_id", nullable = false, unique = true)
    private String studentId;

    private String faculty;

    private String major;

    private Double gpa;

    @Column(name = "total_credits")
    private Integer totalCredits;

    /** U5: id ของอาจารย์ที่ปรึกษา (users.id ที่ role = ADVISOR) · null = ยังไม่ได้กำหนด */
    @Column(name = "advisor_id")
    private Long advisorId;

    protected Student() {
        // สำหรับ JPA
    }

    public Student(Long userId, String studentId, String faculty, String major, Double gpa) {
        this.userId = userId;
        this.studentId = studentId;
        this.faculty = faculty;
        this.major = major;
        this.gpa = gpa;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getStudentId() { return studentId; }
    public String getFaculty() { return faculty; }
    public String getMajor() { return major; }
    public Double getGpa() { return gpa; }
    public Integer getTotalCredits() { return totalCredits; }
    public void setTotalCredits(Integer totalCredits) { this.totalCredits = totalCredits; }
    public Long getAdvisorId() { return advisorId; }
    public void setAdvisorId(Long advisorId) { this.advisorId = advisorId; }
}
