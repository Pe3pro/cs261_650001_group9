package com.example.demo.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.example.demo.entity.ApplicationStatus;

/**
 * ส่งรายการคำร้องให้เจ้าหน้าที่/อาจารย์ตรวจ (GET /api/applications?status=)
 * reviewedBy / reviewedAt = ผู้พิจารณาล่าสุดและเวลา (ว่างถ้ายังไม่มีใครพิจารณา) ใช้ในหน้ารายการของอาจารย์
 */
public record ApplicationReviewResponse(Long id, String studentId, String studentName, ApplicationStatus status,
        LocalDateTime submittedAt, List<DocumentResponse> documents, String reviewedBy, LocalDateTime reviewedAt,
        String attention) {

    /** U5: ป้ายเตือนในรายการ · OVERDUE = รอตรวจนานเกินกำหนด · DEADLINE_SOON = แบบฟอร์มใกล้หมดเขต */
    public static final String OVERDUE = "OVERDUE";
    public static final String DEADLINE_SOON = "DEADLINE_SOON";

    public ApplicationReviewResponse(Long id, String studentId, String studentName, ApplicationStatus status,
            LocalDateTime submittedAt, List<DocumentResponse> documents, String reviewedBy, LocalDateTime reviewedAt) {
        this(id, studentId, studentName, status, submittedAt, documents, reviewedBy, reviewedAt, null);
    }
}
