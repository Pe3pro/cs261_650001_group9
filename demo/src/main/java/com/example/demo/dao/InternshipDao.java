package com.example.demo.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.entity.Internship;

/**
 * DAO ของตาราง INTERNSHIPS (ของ US7 · findById มาจาก JpaRepository)
 * US8 ใช้ findById และ findByStudentId ซึ่งเป็น method ที่ US7 ออกแบบไว้ (DAO-I02 ใน T3 ของ US7)
 */
public interface InternshipDao extends JpaRepository<Internship, Long> {

    /** การฝึกงานของนักศึกษา (1 คนมี 1 การฝึกงาน) */
    Optional<Internship> findByStudentId(Long studentId);
}
