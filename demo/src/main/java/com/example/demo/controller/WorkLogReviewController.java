package com.example.demo.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.BulkWorkLogReviewRequest;
import com.example.demo.dto.BulkWorkLogReviewResponse;
import com.example.demo.dto.WorkLogResponse;
import com.example.demo.dto.WorkLogReviewRequest;
import com.example.demo.service.WorkLogReviewService;
import com.example.demo.service.WorkLogService;

/** U8: API อาจารย์ที่ปรึกษาตรวจบันทึกการทำงาน */
@RestController
public class WorkLogReviewController {

    private static final String USER = "X-User-Id";

    private final WorkLogReviewService reviewService;
    /** ใช้แปลง WorkLog เป็น WorkLogResponse (ชื่อนักศึกษา จำนวนไฟล์แนบ) */
    private final WorkLogService workLogService;

    public WorkLogReviewController(WorkLogReviewService reviewService, WorkLogService workLogService) {
        this.reviewService = reviewService;
        this.workLogService = workLogService;
    }

    @GetMapping("/api/work-logs/pending-review")
    public List<WorkLogResponse> getPending(@RequestHeader(value = USER, required = false) Long userId) {
        return workLogService.toResponses(reviewService.getPending(userId));
    }

    @PostMapping("/api/work-logs/{id}/review")
    public WorkLogResponse review(@RequestHeader(value = USER, required = false) Long userId,
            @PathVariable Long id, @RequestBody WorkLogReviewRequest req) {
        return workLogService.toResponse(reviewService.review(userId, id, req.decision(), req.comment()));
    }

    @PostMapping("/api/work-logs/review-bulk")
    public BulkWorkLogReviewResponse reviewBulk(@RequestHeader(value = USER, required = false) Long userId,
            @RequestBody BulkWorkLogReviewRequest req) {
        return reviewService.reviewBulk(userId, req.workLogIds(), req.comment());
    }
}
