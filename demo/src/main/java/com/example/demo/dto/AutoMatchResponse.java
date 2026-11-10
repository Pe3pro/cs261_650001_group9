package com.example.demo.dto;

import java.util.List;

/** U6: POST /api/matching/auto ผลการจับคู่อัตโนมัติในรอบที่เปิดอยู่ */
public record AutoMatchResponse(Integer roundNo, List<MatchResponse> matched, List<UnmatchedItem> unmatched) {
}
