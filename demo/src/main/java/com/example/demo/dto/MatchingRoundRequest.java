package com.example.demo.dto;

import java.time.LocalDateTime;

/** U6: POST /api/matching/rounds · confirmDeadline = นักศึกษาต้องตอบก่อนเวลานี้ */
public record MatchingRoundRequest(LocalDateTime confirmDeadline) {
}
