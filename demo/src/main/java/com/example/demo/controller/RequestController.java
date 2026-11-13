package com.example.demo.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.dto.DocumentResponse;
import com.example.demo.dto.FormAnswersRequest;
import com.example.demo.dto.PublishedFormResponse;
import com.example.demo.dto.RequestCreateRequest;
import com.example.demo.dto.RequestResponse;
import com.example.demo.dto.StudentProfileResponse;
import com.example.demo.service.DocumentService;
import com.example.demo.service.RequestService;

/**
 * U3: API ยื่นคำร้องขอฝึกสหกิจศึกษา (ผู้เรียกระบุด้วย header X-User-Id เหมือน API ของ U5)
 * error ทั้งหมดส่งผ่าน ApiExceptionHandler
 */
@RestController
public class RequestController {

    private static final String USER = "X-User-Id";

    private final RequestService requestService;
    private final DocumentService documentService;

    public RequestController(RequestService requestService, DocumentService documentService) {
        this.requestService = requestService;
        this.documentService = documentService;
    }

    @GetMapping("/api/forms/published")
    public PublishedFormResponse publishedForm(@RequestHeader(value = USER, required = false) Long userId) {
        return requestService.getPublishedForm(userId);
    }

    @PostMapping("/api/requests")
    public ResponseEntity<RequestResponse> create(@RequestBody(required = false) RequestCreateRequest request,
            @RequestHeader(value = USER, required = false) Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(requestService.create(request, userId));
    }

    @PutMapping("/api/requests/{id}/form")
    public RequestResponse saveForm(@PathVariable Long id, @RequestBody FormAnswersRequest request,
            @RequestHeader(value = USER, required = false) Long userId) {
        return requestService.saveAnswers(id, request, userId);
    }

    @PostMapping("/api/requests/{id}/documents")
    public ResponseEntity<DocumentResponse> upload(@PathVariable Long id,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "fieldId", required = false) Long fieldId,
            @RequestHeader(value = USER, required = false) Long userId) {
        DocumentResponse saved = fieldId == null
                ? documentService.upload(id, file, userId) // ระบบเดิม: Transcript
                : documentService.upload(id, file, fieldId, userId); // U5: ช่องแนบไฟล์อื่นในแบบฟอร์ม
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PostMapping("/api/requests/{id}/submit")
    public RequestResponse submit(@PathVariable Long id,
            @RequestHeader(value = USER, required = false) Long userId) {
        return requestService.submit(id, userId);
    }

    @GetMapping("/api/students/me")
    public StudentProfileResponse profile(@RequestHeader(value = USER, required = false) Long userId) {
        return requestService.getProfile(userId);
    }

    @GetMapping("/api/requests/me")
    public List<RequestResponse> mine(@RequestHeader(value = USER, required = false) Long userId) {
        return requestService.getMine(userId);
    }

    @GetMapping("/api/requests/{id}")
    public RequestResponse get(@PathVariable Long id, @RequestHeader(value = USER, required = false) Long userId) {
        return requestService.get(id, userId);
    }
}
