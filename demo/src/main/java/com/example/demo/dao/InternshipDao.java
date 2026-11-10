package com.example.demo.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.entity.Internship;

/** DAO ของตาราง INTERNSHIP (findById มาจาก JpaRepository) */
public interface InternshipDao extends JpaRepository<Internship, Long> {

    /** การฝึกงานล่าสุดของนักศึกษา ใช้ในหน้าบันทึกการทำงาน (GET /api/internships/me) */
    Optional<Internship> findFirstByStudentIdOrderByStartDateDescIdDesc(Long studentId);
}
