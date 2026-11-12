package com.example.demo.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.example.demo.entity.Internship;
import com.example.demo.entity.InternshipStatus;

public record InternshipResponse(
        Long id,
        Long matchId,
        Long studentId,
        LocalDate startDate,
        LocalDate endDate,
        String supervisorName,
        String supervisorEmail,
        String supervisorPhone,
        InternshipStatus status,
        String terminatedReason,
        Long updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static InternshipResponse from(
            Internship internship
    ) {
        return new InternshipResponse(
                internship.getId(),
                internship.getMatchId(),
                internship.getStudentId(),
                internship.getStartDate(),
                internship.getEndDate(),
                internship.getSupervisorName(),
                internship.getSupervisorEmail(),
                internship.getSupervisorPhone(),
                internship.getStatus(),
                internship.getTerminatedReason(),
                internship.getUpdatedBy(),
                internship.getCreatedAt(),
                internship.getUpdatedAt()
        );
    }
}