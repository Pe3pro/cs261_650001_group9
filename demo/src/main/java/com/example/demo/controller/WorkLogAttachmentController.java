package com.example.demo.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.dto.WorkLogAttachmentResponse;
import com.example.demo.service.WorkLogService;
import com.example.demo.service.WorkLogService.FileContent;

/** U8: API ไฟล์แนบของบันทึกการทำงาน */
@RestController
public class WorkLogAttachmentController {

    private static final String USER = "X-User-Id";

    private final WorkLogService workLogService;

    public WorkLogAttachmentController(WorkLogService workLogService) {
        this.workLogService = workLogService;
    }

    @PostMapping("/api/work-logs/{id}/attachments")
    public ResponseEntity<WorkLogAttachmentResponse> upload(
            @RequestHeader(value = USER, required = false) Long userId, @PathVariable Long id,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(WorkLogAttachmentResponse.from(workLogService.addAttachment(userId, id, file)));
    }

    /** เปิดไฟล์ด้วย window.open ใส่ header ไม่ได้ จึงรับ userId จาก query string ได้ด้วย */
    @GetMapping("/api/work-logs/{id}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> download(@RequestHeader(value = USER, required = false) Long headerUserId,
            @RequestParam(value = "userId", required = false) Long queryUserId, @PathVariable Long id,
            @PathVariable Long attachmentId) {
        FileContent file = workLogService.downloadAttachment(headerUserId != null ? headerUserId : queryUserId, id,
                attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.content());
    }

    @DeleteMapping("/api/work-logs/{id}/attachments/{attachmentId}")
    public ResponseEntity<Void> delete(@RequestHeader(value = USER, required = false) Long userId,
            @PathVariable Long id, @PathVariable Long attachmentId) {
        workLogService.deleteAttachment(userId, id, attachmentId);
        return ResponseEntity.noContent().build();
    }
}
