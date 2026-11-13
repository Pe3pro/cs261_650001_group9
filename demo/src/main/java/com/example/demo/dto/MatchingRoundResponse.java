package com.example.demo.dto;

import java.time.LocalDateTime;

import com.example.demo.entity.MatchingRound;
import com.example.demo.entity.RoundStatus;

/** U6: รอบการจับคู่ · matchedCount = จำนวนผลที่ยังใช้อยู่ (PROPOSED / CONFIRMED) ในรอบนี้ */
public record MatchingRoundResponse(Long id, Integer roundNo, RoundStatus status, LocalDateTime openedAt,
        LocalDateTime announcedAt, LocalDateTime confirmDeadline, long matchedCount) {

    public static MatchingRoundResponse from(MatchingRound r, long matchedCount) {
        return new MatchingRoundResponse(r.getId(), r.getRoundNo(), r.getStatus(), r.getOpenedAt(),
                r.getAnnouncedAt(), r.getConfirmDeadline(), matchedCount);
    }
}
