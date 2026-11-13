package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.example.demo.entity.WorkLog;
import com.example.demo.entity.WorkLogStatus;

/**
 * U8: รายละเอียดบันทึก = WorkLogResponse ทุก field + เวลา สิ่งที่ได้เรียนรู้ ปัญหา ไฟล์แนบ และความเห็นอาจารย์
 * (record สืบทอดกันไม่ได้ จึงเขียน field ของ WorkLogResponse ซ้ำ ส่งออกเป็น JSON เหมือนกัน)
 */
public record WorkLogDetailResponse(Long id, Long internshipId, String studentName, LocalDate logDate,
        BigDecimal hours, String activity, WorkLogStatus status, int attachmentCount, boolean lateSubmitted,
        LocalDateTime submittedAt, LocalDateTime reviewedAt, LocalTime startTime, LocalTime endTime,
        String learning, String problem, List<WorkLogAttachmentResponse> attachments, String reviewerName,
        String reviewComment) {

    public static WorkLogDetailResponse from(WorkLog w, String studentName,
            List<WorkLogAttachmentResponse> attachments, String reviewerName) {
        return new WorkLogDetailResponse(w.getId(), w.getInternshipId(), studentName, w.getLogDate(), w.getHours(),
                w.getActivity(), w.getStatus(), attachments.size(), w.isLateSubmitted(), w.getSubmittedAt(),
                w.getReviewedAt(), w.getStartTime(), w.getEndTime(), w.getLearning(), w.getProblem(), attachments,
                reviewerName, w.getReviewComment());
    }
}
