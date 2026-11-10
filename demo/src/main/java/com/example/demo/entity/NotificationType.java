package com.example.demo.entity;

/** ประเภทการแจ้งเตือน */
public enum NotificationType {
    APPLICATION_FORWARDED,
    APPLICATION_APPROVED,
    APPLICATION_RETURNED,
    APPLICATION_REJECTED,
    /** U5: แจ้งเจ้าหน้าที่ว่ามีคำร้องใหม่ หรือคำร้องที่แก้แล้วส่งกลับมา */
    APPLICATION_SUBMITTED,
    /** U5: เตือนนักศึกษาที่ถูกตีกลับแต่ยังไม่ส่งใหม่ ก่อนแบบฟอร์มหมดเขต */
    CORRECTION_REMINDER
}
