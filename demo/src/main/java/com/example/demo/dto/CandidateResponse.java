package com.example.demo.dto;

/** U6: GET /api/matching/candidates นักศึกษาที่เลือกตำแหน่งนี้ */
public record CandidateResponse(Long applicationId, String studentId, String studentName, int rank,
        boolean alreadyMatched) {
}
