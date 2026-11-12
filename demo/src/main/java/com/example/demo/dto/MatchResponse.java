package com.example.demo.dto;

import java.time.LocalDateTime;

import com.example.demo.entity.MatchStatus;

/** U6: ผลการจับคู่ 1 รายการ (rank = null คือเจ้าหน้าที่จับคู่เองกับตำแหน่งที่ไม่ได้อยู่ในอันดับ) */
public record MatchResponse(Long id, Long applicationId, String studentId, String studentName, Long positionId,
        String positionName, String companyName, Integer rank, MatchStatus status, Long roundId, Integer roundNo,
        LocalDateTime matchedAt, LocalDateTime respondedAt, LocalDateTime confirmDeadline, String declineReason) {
}
