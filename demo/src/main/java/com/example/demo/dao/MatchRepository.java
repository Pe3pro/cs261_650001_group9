package com.example.demo.dao;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.entity.Match;
import com.example.demo.entity.MatchStatus;

/** DAO ของตาราง MATCHES ใช้ร่วมกับ U6 และ U7 */
public interface MatchRepository
        extends JpaRepository<Match, Long> {

    /**
     * U6: ผลที่ยังใช้อยู่ของใบสมัคร
     * ส่งสถานะ PROPOSED และ CONFIRMED
     */
    Optional<Match>
            findFirstByApplicationIdAndStatusIn(
                    Long applicationId,
                    Collection<MatchStatus> statuses
            );

    /** U6: ผลการจับคู่ล่าสุดของใบสมัคร */
    Optional<Match>
            findFirstByApplicationIdOrderByIdDesc(
                    Long applicationId
            );

    /** U7: ผลการจับคู่ที่ยังใช้อยู่ของใบสมัคร */
    default Optional<Match>
            findActiveWithPositionByApplicationId(
                    Long applicationId
            ) {
        return findFirstByApplicationIdAndStatusIn(
                applicationId,
                List.of(
                        MatchStatus.PROPOSED,
                        MatchStatus.CONFIRMED
                )
        );
    }

    List<Match> findByRoundIdOrderByIdAsc(
            Long roundId
    );

    List<Match> findByRoundIdAndStatus(
            Long roundId,
            MatchStatus status
    );

    List<Match> findByStatusOrderByIdAsc(
            MatchStatus status
    );

    List<Match> findAllByOrderByIdAsc();
}