package com.example.demo.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * U8: สร้าง / แก้ไขบันทึกการทำงาน
 * logDate อยู่ในช่วงฝึกงานและไม่เป็นวันในอนาคต · ชั่วโมงคำนวณจาก startTime–endTime (0.5–12 ชม.)
 */
public record WorkLogRequest(LocalDate logDate, LocalTime startTime, LocalTime endTime, String activity,
        String learning, String problem) {
}
