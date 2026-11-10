package com.example.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dao.InternshipDao;
import com.example.demo.dto.InternshipResponse;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.service.AccessGuard;
import com.example.demo.service.ApiException;

/**
 * ให้หน้าบันทึกการทำงาน (US8) หา internshipId ของนักศึกษาที่ login อยู่
 * ใช้ชั่วคราวจนกว่า US7 (สถานะการฝึกงาน) จะมี API ของการฝึกงานจริง
 */
@RestController
public class InternshipController {

    private final InternshipDao internshipDao;
    private final AccessGuard guard;

    public InternshipController(InternshipDao internshipDao, AccessGuard guard) {
        this.internshipDao = internshipDao;
        this.guard = guard;
    }

    @GetMapping("/api/internships/me")
    public InternshipResponse me(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        User student = guard.require(userId, Role.STUDENT);
        return internshipDao.findFirstByStudentIdOrderByStartDateDescIdDesc(student.getId())
                .map(InternshipResponse::from)
                .orElseThrow(() -> ApiException.notFound("ยังไม่มีข้อมูลการฝึกงาน"));
    }
}
