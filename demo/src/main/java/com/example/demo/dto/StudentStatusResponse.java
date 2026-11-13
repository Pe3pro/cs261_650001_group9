package com.example.demo.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.example.demo.entity.Attention;
import com.example.demo.entity.InternshipStage;
import com.example.demo.entity.InternshipStatus;

public record StudentStatusResponse(
        Long studentId,
        String studentCode,
        String studentName,
        String advisorName,
        Long companyId,
        String companyName,
        String positionTitle,
        InternshipStage stage,
        InternshipStatus internshipStatus,
        Attention attention,
        LocalDate startDate,
        LocalDateTime updatedAt
) {
}