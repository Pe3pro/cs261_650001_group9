package com.example.demo.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dao.StatusHistoryRepository;
import com.example.demo.dao.UserRepository;
import com.example.demo.dto.StatusHistoryResponse;
import com.example.demo.entity.HistoryType;
import com.example.demo.entity.StatusHistory;
import com.example.demo.entity.User;

@Service
@Transactional
public class StatusHistoryService {

    private final StatusHistoryRepository statusHistoryRepository;
    private final UserRepository userRepository;

    public StatusHistoryService(
            StatusHistoryRepository statusHistoryRepository,
            UserRepository userRepository) {
        this.statusHistoryRepository = statusHistoryRepository;
        this.userRepository = userRepository;
    }

    public StatusHistory record(
            Long studentId,
            HistoryType type,
            String fromStatus,
            String toStatus,
            Long changedBy,
            String note,
            LocalDateTime changedAt) {

        if (studentId == null) {
            throw ApiException.badRequest("กรุณาระบุนักศึกษา");
        }
        if (type == null) {
            throw ApiException.badRequest("กรุณาระบุประเภทประวัติ");
        }
        if (toStatus == null || toStatus.isBlank()) {
            throw ApiException.badRequest("กรุณาระบุสถานะใหม่");
        }
        if (changedBy == null) {
            throw ApiException.badRequest("กรุณาระบุผู้เปลี่ยนสถานะ");
        }

        LocalDateTime effectiveTime =
                changedAt != null ? changedAt : LocalDateTime.now();

        StatusHistory history = new StatusHistory(
                studentId,
                type,
                normalize(fromStatus),
                toStatus.trim(),
                changedBy,
                effectiveTime,
                normalize(note));

        return statusHistoryRepository.save(history);
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> getTimeline(Long studentId) {
        if (studentId == null) {
            throw ApiException.badRequest("กรุณาระบุนักศึกษา");
        }

        return statusHistoryRepository
                .findByStudentIdOrderByChangedAt(studentId)
                .stream()
                .map(history -> StatusHistoryResponse.from(
                        history,
                        getChangedByName(history.getChangedBy())))
                .toList();
    }

    private String getChangedByName(Long userId) {
        if (userId == null) {
            return "-";
        }

        return userRepository.findById(userId)
                .map(User::getFullName)
                .orElse("-");
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}