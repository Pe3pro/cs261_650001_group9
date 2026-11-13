package com.example.demo.dao;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.entity.Match;
import com.example.demo.entity.MatchStatus;

/** DAO ของตาราง MATCHES (U6) */
public interface MatchRepository extends JpaRepository<Match, Long> {

    /** ผลที่ยังใช้อยู่ของใบสมัคร (ส่ง [PROPOSED, CONFIRMED]) */
    Optional<Match> findFirstByApplicationIdAndStatusIn(Long applicationId, Collection<MatchStatus> statuses);

    /** ผลล่าสุดของใบสมัคร (GET /api/matches/me) */
    Optional<Match> findFirstByApplicationIdOrderByIdDesc(Long applicationId);

    List<Match> findByRoundIdOrderByIdAsc(Long roundId);

    List<Match> findByRoundIdAndStatus(Long roundId, MatchStatus status);

    List<Match> findByStatusOrderByIdAsc(MatchStatus status);

    List<Match> findAllByOrderByIdAsc();
}
