package com.example.demo.dto;

import java.util.List;

import com.example.demo.entity.ApprovalDecision;

/**
 * รับผลการพิจารณา: APPROVE / RETURN / REJECT (ถ้าไม่ใช่ APPROVE ต้องมี comment)
 * fieldIds = ช่องในแบบฟอร์มที่ต้องแก้ ใช้เฉพาะตอนตีกลับ (RETURN)
 */
public record ApprovalRequest(ApprovalDecision decision, String comment, List<Long> fieldIds) {

    public ApprovalRequest(ApprovalDecision decision, String comment) {
        this(decision, comment, null);
    }
}
