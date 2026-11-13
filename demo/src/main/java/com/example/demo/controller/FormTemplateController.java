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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.FormTemplateRequest;
import com.example.demo.dto.FormTemplateResponse;
import com.example.demo.service.FormTemplateService;

/** U5: API สร้าง/แก้ไข/เปิดใช้งานแบบฟอร์มคำร้อง (เฉพาะเจ้าหน้าที่) */
@RestController
@RequestMapping("/api/forms")
public class FormTemplateController {

    private final FormTemplateService formTemplateService;

    public FormTemplateController(FormTemplateService formTemplateService) {
        this.formTemplateService = formTemplateService;
    }

    @GetMapping
    public List<FormTemplateResponse> getAll(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return formTemplateService.getAll(userId);
    }

    @GetMapping("/{id}")
    public FormTemplateResponse getById(@PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return formTemplateService.getById(id, userId);
    }

    @PostMapping
    public ResponseEntity<FormTemplateResponse> create(@RequestBody FormTemplateRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(formTemplateService.create(request, userId));
    }

    @PutMapping("/{id}")
    public FormTemplateResponse update(@PathVariable Long id, @RequestBody FormTemplateRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return formTemplateService.update(id, request, userId);
    }

    @PostMapping("/{id}/new-version")
    public ResponseEntity<FormTemplateResponse> newVersion(@PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(formTemplateService.newVersion(id, userId));
    }

    @PostMapping("/{id}/publish")
    public FormTemplateResponse publish(@PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return formTemplateService.publish(id, userId);
    }

    /** U5: ปิดรับคำร้องก่อนกำหนด */
    @PostMapping("/{id}/close")
    public FormTemplateResponse close(@PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return formTemplateService.close(id, userId);
    }
}
