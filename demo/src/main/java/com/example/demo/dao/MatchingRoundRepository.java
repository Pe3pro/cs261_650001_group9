package com.example.demo.dao;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.entity.MatchingRound;
import com.example.demo.entity.RoundStatus;

/** DAO ของตาราง MATCHING_ROUNDS (U6) */
public interface MatchingRoundRepository extends JpaRepository<MatchingRound, Long> {

    /** รอบล่าสุด (GET /api/matching/rounds/current และใช้หาเลขรอบถัดไป) */
    Optional<MatchingRound> findFirstByOrderByRoundNoDesc();

    /** รอบที่อยู่ในสถานะนี้ (มี OPEN ได้ทีละรอบ) */
    Optional<MatchingRound> findFirstByStatus(RoundStatus status);

    /** รอบล่าสุดที่อยู่ในสถานะเหล่านี้ (ใช้หารอบที่ประกาศผลแล้วล่าสุด) */
    Optional<MatchingRound> findFirstByStatusInOrderByRoundNoDesc(Collection<RoundStatus> statuses);
}
