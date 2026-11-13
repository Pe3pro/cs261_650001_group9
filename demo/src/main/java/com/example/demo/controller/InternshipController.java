package com.example.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.InternshipRequest;
import com.example.demo.dto.InternshipResponse;
import com.example.demo.dto.TerminateRequest;
import com.example.demo.service.InternshipService;

/** U7: API จัดการข้อมูลและสถานะการฝึกงาน */
@RestController
@RequestMapping("/api/internships")
public class InternshipController {

    private static final String USER_HEADER = "X-User-Id";

    private final InternshipService internshipService;

    public InternshipController(
            InternshipService internshipService) {
        this.internshipService = internshipService;
    }

    /** ดูข้อมูลการฝึกงาน */
    @GetMapping("/{id}")
    public InternshipResponse get(
            @PathVariable Long id,
            @RequestHeader(
                    value = USER_HEADER,
                    required = false) Long userId) {

        return internshipService.get(userId, id);
    }

    /** เจ้าหน้าที่แก้ไขวันที่และข้อมูลผู้ดูแล */
    @PutMapping("/{id}")
    public InternshipResponse update(
            @PathVariable Long id,
            @RequestBody(required = false)
                    InternshipRequest request,
            @RequestHeader(
                    value = USER_HEADER,
                    required = false) Long userId) {

        return internshipService.update(userId, id, request);
    }

    /** เริ่มฝึกงาน: NOT_STARTED → IN_PROGRESS */
    @PostMapping("/{id}/start")
    public InternshipResponse start(
            @PathVariable Long id,
            @RequestHeader(
                    value = USER_HEADER,
                    required = false) Long userId) {

        return internshipService.start(userId, id);
    }

    /** เสร็จสิ้นการฝึกงาน: IN_PROGRESS → COMPLETED */
    @PostMapping("/{id}/complete")
    public InternshipResponse complete(
            @PathVariable Long id,
            @RequestHeader(
                    value = USER_HEADER,
                    required = false) Long userId) {

        return internshipService.complete(userId, id);
    }

    /** ยุติการฝึกงานก่อนกำหนด */
    @PostMapping("/{id}/terminate")
    public InternshipResponse terminate(
            @PathVariable Long id,
            @RequestBody(required = false)
                    TerminateRequest request,
            @RequestHeader(
                    value = USER_HEADER,
                    required = false) Long userId) {

        String reason = request == null
                ? null
                : request.reason();

        return internshipService.terminate(
                userId,
                id,
                reason);
    }
}