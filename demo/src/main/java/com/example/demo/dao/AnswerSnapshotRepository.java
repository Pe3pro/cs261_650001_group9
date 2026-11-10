package com.example.demo.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.entity.AnswerSnapshot;

/** DAO ของตาราง ANSWER_SNAPSHOTS (U5: เทียบคำตอบก่อน/หลังตีกลับ) */
public interface AnswerSnapshotRepository extends JpaRepository<AnswerSnapshot, Long> {

    List<AnswerSnapshot> findByApprovalId(Long approvalId);
}
