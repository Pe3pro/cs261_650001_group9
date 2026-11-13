package com.example.demo.dto;

import com.example.demo.entity.ReviewDecision;

/** U8: ผลการตรวจ decision = APPROVE / REVISE · comment บังคับเมื่อ REVISE */
public record WorkLogReviewRequest(ReviewDecision decision, String comment) {
}
