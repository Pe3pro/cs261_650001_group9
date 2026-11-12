package com.example.demo.entity;

/**
 * สถานะการฝึกงาน ตามการออกแบบของ US7: NOT_STARTED → IN_PROGRESS → COMPLETED · ยุติก่อนกำหนด = TERMINATED
 * US8 บันทึกการทำงานได้เฉพาะ IN_PROGRESS
 */
public enum InternshipStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    TERMINATED
}
