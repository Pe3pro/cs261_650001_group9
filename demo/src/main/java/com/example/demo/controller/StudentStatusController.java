package com.example.demo.controller;

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

import com.example.demo.dto.PageResponse;
import com.example.demo.dto.StatusHistoryResponse;
import com.example.demo.dto.StudentStatusDetailResponse;
import com.example.demo.dto.StudentStatusFilter;
import com.example.demo.dto.StudentStatusResponse;
import com.example.demo.entity.InternshipStage;
import com.example.demo.service.ApiException;
import com.example.demo.service.StudentStatusService;

/** U7: API ตรวจสอบข้อมูลและสถานะนักศึกษา */
@RestController
@RequestMapping("/api/students")
public class StudentStatusController {

    private static final String USER_HEADER = "X-User-Id";

    private final StudentStatusService studentStatusService;

    public StudentStatusController(
            StudentStatusService studentStatusService) {
        this.studentStatusService = studentStatusService;
    }

    /**
     * รายการนักศึกษาพร้อมตัวกรองและแบ่งหน้า
     */
    @GetMapping("/status")
    public PageResponse<StudentStatusResponse> search(
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long advisorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(
                    value = USER_HEADER,
                    required = false) Long userId) {

        InternshipStage parsedStage =
                parseStage(stage);

        StudentStatusFilter filter =
                new StudentStatusFilter(
                        parsedStage,
                        companyId,
                        q,
                        advisorId,
                        page,
                        size);

        return studentStatusService.search(
                userId,
                filter);
    }

    /**
     * จำนวนของนักศึกษาในแต่ละขั้น
     */
    @GetMapping("/status/summary")
    public Map<InternshipStage, Long> summary(
            @RequestHeader(
                    value = USER_HEADER,
                    required = false) Long userId) {

        return studentStatusService.summary(userId);
    }

    /**
     * ดาวน์โหลดรายการเป็น CSV สำหรับเปิดด้วย Excel
     */
    @GetMapping("/status/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String stage,
            @RequestHeader(
                    value = USER_HEADER,
                    required = false) Long userId) {

        InternshipStage parsedStage =
                parseStage(stage);

        byte[] csv =
                studentStatusService.exportCsv(
                        userId,
                        parsedStage);

        String filename = parsedStage == null
                ? "student-status.csv"
                : "student-status-"
                        + parsedStage.name().toLowerCase()
                        + ".csv";

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\""
                                + filename
                                + "\"")
                .contentType(
                        MediaType.parseMediaType(
                                "text/csv;charset=UTF-8"))
                .body(csv);
    }

    /**
     * นักศึกษาดูสถานะของตนเอง
     */
    @GetMapping("/me/status")
    public StudentStatusDetailResponse myStatus(
            @RequestHeader(
                    value = USER_HEADER,
                    required = false) Long userId) {

        return studentStatusService.getMyStatus(userId);
    }

    /**
     * รายละเอียดสถานะของนักศึกษา
     */
    @GetMapping("/{studentId}/status")
    public StudentStatusDetailResponse detail(
            @PathVariable Long studentId,
            @RequestHeader(
                    value = USER_HEADER,
                    required = false) Long userId) {

        return studentStatusService.getDetail(
                userId,
                studentId);
    }

    /**
     * Timeline การเปลี่ยนสถานะ
     */
    @GetMapping("/{studentId}/timeline")
    public List<StatusHistoryResponse> timeline(
            @PathVariable Long studentId,
            @RequestHeader(
                    value = USER_HEADER,
                    required = false) Long userId) {

        return studentStatusService.getTimeline(
                userId,
                studentId);
    }

    private InternshipStage parseStage(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return InternshipStage.valueOf(
                    value.trim().toUpperCase());

        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest(
                    "สถานะ stage ไม่ถูกต้อง");
        }
    }
}