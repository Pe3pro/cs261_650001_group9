package com.example.demo.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.entity.StatusHistory;

public interface StatusHistoryRepository
        extends JpaRepository<StatusHistory, Long> {

    List<StatusHistory>
            findByStudentIdOrderByChangedAt(Long studentId);
}