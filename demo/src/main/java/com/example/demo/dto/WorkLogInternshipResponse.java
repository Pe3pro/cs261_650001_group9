package com.example.demo.dto;

import java.time.LocalDate;

import com.example.demo.entity.Internship;
import com.example.demo.entity.InternshipStatus;

/**
 * U8: ข้อมูลการฝึกงานที่หน้าบันทึกการทำงานต้องใช้ (id, ช่วงฝึก, สถานะ)
 * แยกจาก InternshipResponse ของ US7 เพื่อไม่ให้ชื่อคลาสชนกัน
 */
public record WorkLogInternshipResponse(Long id, LocalDate startDate, LocalDate endDate, InternshipStatus status) {

    public static WorkLogInternshipResponse from(Internship i) {
        return new WorkLogInternshipResponse(i.getId(), i.getStartDate(), i.getEndDate(), i.getStatus());
    }
}
