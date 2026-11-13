package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.example.demo.entity.WorkLog;
import com.example.demo.entity.WorkLogStatus;

/** U8: บันทึก 1 แถวในรายการ · lateSubmitted = ส่งหลังวันที่บันทึกเกิน 3 วัน */
public record WorkLogResponse(Long id, Long internshipId, String studentName, LocalDate logDate, BigDecimal hours,
        String activity, WorkLogStatus status, int attachmentCount, boolean lateSubmitted,
        LocalDateTime submittedAt, LocalDateTime reviewedAt) {

    public static WorkLogResponse from(WorkLog w, String studentName, int attachmentCount) {
        return new WorkLogResponse(w.getId(), w.getInternshipId(), studentName, w.getLogDate(), w.getHours(),
                w.getActivity(), w.getStatus(), attachmentCount, w.isLateSubmitted(), w.getSubmittedAt(),
                w.getReviewedAt());
    }
}
