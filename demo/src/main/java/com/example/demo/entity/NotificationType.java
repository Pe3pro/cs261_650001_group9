package com.example.demo.entity;

/** ประเภทการแจ้งเตือน */
public enum NotificationType {
    APPLICATION_FORWARDED,
    APPLICATION_APPROVED,
    APPLICATION_RETURNED,
    APPLICATION_REJECTED,
    /** U8: นักศึกษาส่งบันทึกการทำงาน (แจ้งอาจารย์ที่ปรึกษา) */
    WORK_LOG_SUBMITTED,
    /** U8: อาจารย์ตรวจบันทึกการทำงานแล้ว (แจ้งนักศึกษา) */
    WORK_LOG_REVIEWED
}
