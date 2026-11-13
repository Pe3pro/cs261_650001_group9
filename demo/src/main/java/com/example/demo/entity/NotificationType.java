package com.example.demo.entity;

/** ประเภทการแจ้งเตือน */
public enum NotificationType {
    APPLICATION_FORWARDED,
    APPLICATION_APPROVED,
    APPLICATION_RETURNED,
    APPLICATION_REJECTED,

    /** U5: แจ้งเจ้าหน้าที่ว่ามีคำร้องใหม่หรือคำร้องที่แก้แล้ว */
    APPLICATION_SUBMITTED,

    /** U5: เตือนนักศึกษาที่ถูกตีกลับแต่ยังไม่ส่งใหม่ */
    CORRECTION_REMINDER,

    /** U6: ประกาศผลการจับคู่ */
    MATCH_ANNOUNCED,

    /** U6: นักศึกษายืนยันหรือสละสิทธิ์ */
    MATCH_RESPONDED,

    /** U6: เจ้าหน้าที่ยกเลิกผลจับคู่ */
    MATCH_CANCELLED,

    /** U7: แก้ไขข้อมูลการฝึกงาน */
    INTERNSHIP_UPDATED,

    /** U7: เปลี่ยนสถานะการฝึกงาน */
    INTERNSHIP_STATUS_CHANGED
}