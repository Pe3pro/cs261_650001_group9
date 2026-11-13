package com.example.demo.dto;

/** U6: POST /api/matches (เจ้าหน้าที่จับคู่เอง 1 ใบ) */
public record MatchRequest(Long applicationId, Long positionId) {
}
