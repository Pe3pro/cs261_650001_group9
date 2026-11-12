package com.example.demo.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.WorkLogDetailResponse;
import com.example.demo.dto.WorkLogFilter;
import com.example.demo.dto.WorkLogInternshipResponse;
import com.example.demo.dto.WorkLogRequest;
import com.example.demo.dto.WorkLogResponse;
import com.example.demo.dto.WorkLogSummaryResponse;
import com.example.demo.entity.WorkLogStatus;
import com.example.demo.service.WorkLogService;

/** U8: API บันทึกการทำงาน · ทุก request ส่ง header X-User-Id */
@RestController
public class WorkLogController {

    private static final String USER = "X-User-Id";

    private final WorkLogService workLogService;

    public WorkLogController(WorkLogService workLogService) {
        this.workLogService = workLogService;
    }

    @GetMapping("/api/internships/{id}/work-logs")
    public List<WorkLogResponse> getLogs(@RequestHeader(value = USER, required = false) Long userId,
            @PathVariable("id") Long internshipId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) WorkLogStatus status) {
        return workLogService.toResponses(
                workLogService.getLogs(userId, internshipId, new WorkLogFilter(from, to, status)));
    }

    @GetMapping("/api/internships/{id}/work-logs/summary")
    public WorkLogSummaryResponse getSummary(@RequestHeader(value = USER, required = false) Long userId,
            @PathVariable("id") Long internshipId) {
        return workLogService.summary(userId, internshipId);
    }

    /** ดาวน์โหลดไฟล์ CSV · รับผู้ใช้จาก header X-User-Id เท่านั้น (หน้าเว็บดาวน์โหลดด้วย fetch + blob) */
    @GetMapping("/api/internships/{id}/work-logs/export")
    public ResponseEntity<byte[]> export(@RequestHeader(value = USER, required = false) Long userId,
            @PathVariable("id") Long internshipId) {
        byte[] csv = workLogService.export(userId, internshipId);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("work-logs-" + internshipId + ".csv").build().toString())
                .body(csv);
    }

    /** การฝึกงานของนักศึกษาที่ login อยู่ (ไม่อยู่ใต้ /api/internships เพื่อไม่ชนกับ InternshipController ของ US7) */
    @GetMapping("/api/work-logs/my-internship")
    public WorkLogInternshipResponse myInternship(@RequestHeader(value = USER, required = false) Long userId) {
        return WorkLogInternshipResponse.from(workLogService.myInternship(userId));
    }

    @PostMapping("/api/internships/{id}/work-logs")
    public ResponseEntity<WorkLogResponse> create(@RequestHeader(value = USER, required = false) Long userId,
            @PathVariable("id") Long internshipId, @RequestBody WorkLogRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workLogService.toResponse(workLogService.create(userId, internshipId, req)));
    }

    @GetMapping("/api/work-logs/{id}")
    public WorkLogDetailResponse getDetail(@RequestHeader(value = USER, required = false) Long userId,
            @PathVariable Long id) {
        return workLogService.getDetail(userId, id);
    }

    @PutMapping("/api/work-logs/{id}")
    public WorkLogResponse update(@RequestHeader(value = USER, required = false) Long userId,
            @PathVariable Long id, @RequestBody WorkLogRequest req) {
        return workLogService.toResponse(workLogService.update(userId, id, req));
    }

    @DeleteMapping("/api/work-logs/{id}")
    public ResponseEntity<Void> delete(@RequestHeader(value = USER, required = false) Long userId,
            @PathVariable Long id) {
        workLogService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/work-logs/{id}/submit")
    public WorkLogResponse submit(@RequestHeader(value = USER, required = false) Long userId,
            @PathVariable Long id) {
        return workLogService.toResponse(workLogService.submit(userId, id));
    }
}
