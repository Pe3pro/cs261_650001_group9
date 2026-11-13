package com.example.demo.dto;

import java.util.List;

/** U8: อนุมัติหลายบันทึกพร้อมกัน (ไม่เกิน 100) */
public record BulkWorkLogReviewRequest(List<Long> workLogIds, String comment) {
}
