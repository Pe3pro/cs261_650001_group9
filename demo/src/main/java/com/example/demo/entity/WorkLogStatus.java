package com.example.demo.entity;

/**
 * U8: สถานะบันทึกการทำงาน
 * DRAFT → (นักศึกษาส่ง) SUBMITTED → (อาจารย์อนุมัติ) APPROVED · ส่งกลับแก้ไข = NEED_REVISION → แก้แล้วส่งใหม่ = SUBMITTED
 */
public enum WorkLogStatus {
    DRAFT,
    SUBMITTED,
    NEED_REVISION,
    APPROVED
}
