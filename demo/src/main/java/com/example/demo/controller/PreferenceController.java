package com.example.demo.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.PreferenceRequest;
import com.example.demo.dto.PreferenceResponse;
import com.example.demo.service.PreferenceService;

/**
 * U6: นักศึกษาเลือกตำแหน่ง 1–5 อันดับ (ใบสมัครต้อง APPROVED)
 * GET /api/applications/{id}/preferences   ดูอันดับ พร้อมผลรายอันดับ
 * PUT /api/applications/{id}/preferences   บันทึกอันดับ (แทนที่ของเดิมทั้งหมด)
 */
@RestController
public class PreferenceController {

    private static final String USER = "X-User-Id";

    private final PreferenceService preferenceService;

    public PreferenceController(PreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping("/api/applications/{id}/preferences")
    public List<PreferenceResponse> getPreferences(@RequestHeader(value = USER, required = false) Long userId,
            @PathVariable Long id) {
        return preferenceService.getPreferences(userId, id);
    }

    @PutMapping("/api/applications/{id}/preferences")
    public List<PreferenceResponse> savePreferences(@RequestHeader(value = USER, required = false) Long userId,
            @PathVariable Long id, @RequestBody(required = false) PreferenceRequest request) {
        return preferenceService.savePreferences(userId, id, request);
    }
}
