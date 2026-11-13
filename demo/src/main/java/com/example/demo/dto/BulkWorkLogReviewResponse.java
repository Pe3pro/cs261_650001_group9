package com.example.demo.dto;

import java.util.List;

/** U8: ผลอนุมัติหลายบันทึก · บันทึกที่อนุมัติไม่ได้อยู่ใน skipped พร้อมเหตุผล */
public record BulkWorkLogReviewResponse(List<Long> approved, List<SkippedItem> skipped) {

    public record SkippedItem(Long workLogId, String reason) {
    }
}
