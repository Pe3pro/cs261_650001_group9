package com.example.demo.dto;

import java.util.List;

/**
 * U6: GET /api/matches/me/summary ข้อมูลหน้าผลการจับคู่ทั้งหน้า
 * match = null คือไม่ได้รับการจับคู่ในรอบที่ประกาศ · quota / availableSlots เป็นของตำแหน่งที่ได้
 */
public record MatchSummaryResponse(MatchingRoundResponse round, List<PreferenceResponse> preferences,
        MatchResponse match, Integer quota, Integer availableSlots, String summary) {
}
