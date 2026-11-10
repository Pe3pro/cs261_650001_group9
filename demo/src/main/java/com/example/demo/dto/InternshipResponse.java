package com.example.demo.dto;

import java.time.LocalDate;

import com.example.demo.entity.Internship;
import com.example.demo.entity.InternshipStatus;

/** ข้อมูลการฝึกงานที่หน้าบันทึกการทำงานใช้หา internshipId (GET /api/internships/me) */
public record InternshipResponse(Long id, Long studentId, LocalDate startDate, LocalDate endDate,
        InternshipStatus status) {

    public static InternshipResponse from(Internship i) {
        return new InternshipResponse(i.getId(), i.getStudentId(), i.getStartDate(), i.getEndDate(),
                i.getStatus());
    }
}
