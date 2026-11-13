package com.example.demo.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.entity.Internship;

public interface InternshipRepository
        extends JpaRepository<Internship, Long> {

    Optional<Internship> findByStudentId(Long studentId);

    Optional<Internship> findByMatchId(Long matchId);
}