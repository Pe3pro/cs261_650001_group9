package com.example.demo.dto;

import java.time.LocalDate;

public record InternshipRequest(
        LocalDate startDate,
        LocalDate endDate,
        String supervisorName,
        String supervisorEmail,
        String supervisorPhone
) {
}