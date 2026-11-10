package com.example.demo.entity;

/**
 * U6: สถานะผลการจับคู่
 * PROPOSED = จับคู่แล้ว รอนักศึกษาตอบ · CONFIRMED = ยืนยันสิทธิ์
 * DECLINED = สละสิทธิ์ / ไม่ตอบภายในกำหนด · CANCELLED = เจ้าหน้าที่ยกเลิก
 * PROPOSED และ CONFIRMED นับเป็น "ผลที่ยังใช้อยู่" (active) และกินโควตา
 */
public enum MatchStatus {
    PROPOSED,
    CONFIRMED,
    DECLINED,
    CANCELLED;

    public boolean isActive() {
        return this == PROPOSED || this == CONFIRMED;
    }
}
