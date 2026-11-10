package com.example.demo.dto;

import java.util.List;

/** U5: ผลการอนุมัติหลายรายการ · skipped = คำร้องที่อนุมัติไม่ได้ พร้อมเหตุผล */
public record BulkApprovalResponse(List<Long> approved, List<Skipped> skipped) {

    public record Skipped(Long applicationId, String reason) {
    }
}
