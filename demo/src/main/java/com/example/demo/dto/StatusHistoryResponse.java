package com.example.demo.dto;

import java.time.LocalDateTime;

import com.example.demo.entity.HistoryType;
import com.example.demo.entity.StatusHistory;

public record StatusHistoryResponse(
        Long id,
        Long studentId,
        HistoryType type,
        String fromStatus,
        String toStatus,
        Long changedBy,
        String changedByName,
        LocalDateTime changedAt,
        String note
) {

    public static StatusHistoryResponse from(
            StatusHistory history,
            String changedByName
    ) {
        return new StatusHistoryResponse(
                history.getId(),
                history.getStudentId(),
                history.getType(),
                history.getFromStatus(),
                history.getToStatus(),
                history.getChangedBy(),
                changedByName,
                history.getChangedAt(),
                history.getNote()
        );
    }
}