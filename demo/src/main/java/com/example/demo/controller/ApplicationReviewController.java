package com.example.demo.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.ApplicationDetailResponse;
import com.example.demo.dto.ApplicationReviewResponse;
import com.example.demo.entity.ApplicationStatus;
import com.example.demo.service.ApplicationReviewService;

/**
 * U5: API รับคำร้องของนักศึกษา (เจ้าหน้าที่ / อาจารย์)
 * ใช้ params = "status" เพื่อไม่ชนกับ GET /api/applications?studentId=&formId= ของ U3
 */
@RestController
@RequestMapping("/api/applications")
public class ApplicationReviewController {

    private final ApplicationReviewService reviewService;

    public ApplicationReviewController(ApplicationReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping(params = "status")
    public List<ApplicationReviewResponse> getByStatus(@RequestParam ApplicationStatus status,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return reviewService.getByStatus(status, userId);
    }

    /** U5: จำนวนคำร้องแต่ละสถานะ (ตัวเลขบนแท็บ) */
    @GetMapping("/summary")
    public Map<ApplicationStatus, Long> getSummary(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return reviewService.getSummary(userId);
    }

    /** U5: ดาวน์โหลดรายการคำร้องเป็นไฟล์ CSV (เปิดด้วย Excel) · ไม่ระบุ status = ทุกสถานะ */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) ApplicationStatus status,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        byte[] body = reviewService.exportCsv(status, userId).getBytes(StandardCharsets.UTF_8);
        String name = "applications-" + (status == null ? "ALL" : status.name()) + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .body(body);
    }

    @GetMapping("/{id}/detail")
    public ApplicationDetailResponse getDetail(@PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return reviewService.getDetail(id, userId);
    }
}
