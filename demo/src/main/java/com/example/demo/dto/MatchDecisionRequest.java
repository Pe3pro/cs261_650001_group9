package com.example.demo.dto;

/** U6: POST /api/matches/{id}/respond · accept = true ยืนยันสิทธิ์ / false สละสิทธิ์ (ต้องมี reason) */
public record MatchDecisionRequest(Boolean accept, String reason) {
}
