package com.example.demo.dto;

import java.time.LocalDate;

import com.example.demo.entity.WorkLogStatus;

/** U8: ตัวกรองรายการบันทึก ?from=&to=&status= (ค่า null = ไม่กรอง) */
public record WorkLogFilter(LocalDate from, LocalDate to, WorkLogStatus status) {
}
