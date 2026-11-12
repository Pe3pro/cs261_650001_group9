package com.example.demo.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.CompanyDetailResponse;
import com.example.demo.dto.CompanyRequest;
import com.example.demo.dto.CompanyResponse;
import com.example.demo.dto.PositionRequest;
import com.example.demo.dto.PositionResponse;
import com.example.demo.service.CompanyService;

/**
 * U6: ข้อมูลบริษัทและตำแหน่ง (GET /api/companies ยังอยู่ที่ RankingController ของ U4)
 * GET  /api/companies/{id}             รายละเอียดบริษัทและตำแหน่ง
 * POST /api/companies                  เจ้าหน้าที่เพิ่มบริษัท
 * PUT  /api/companies/{id}             เจ้าหน้าที่แก้ข้อมูลบริษัท
 * POST /api/companies/{id}/positions   เจ้าหน้าที่เพิ่มตำแหน่งและโควตา
 * PUT  /api/positions/{id}             เจ้าหน้าที่แก้ตำแหน่ง
 */
@RestController
public class CompanyController {

    private static final String USER = "X-User-Id";

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping("/api/companies/{id}")
    public CompanyDetailResponse getCompany(@RequestHeader(value = USER, required = false) Long userId,
            @PathVariable Long id) {
        return companyService.getCompany(userId, id);
    }

    @PostMapping("/api/companies")
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyResponse createCompany(@RequestHeader(value = USER, required = false) Long userId,
            @RequestBody(required = false) CompanyRequest request) {
        return companyService.createCompany(userId, request);
    }

    @PutMapping("/api/companies/{id}")
    public CompanyResponse updateCompany(@RequestHeader(value = USER, required = false) Long userId,
            @PathVariable Long id, @RequestBody(required = false) CompanyRequest request) {
        return companyService.updateCompany(userId, id, request);
    }

    @PostMapping("/api/companies/{id}/positions")
    @ResponseStatus(HttpStatus.CREATED)
    public PositionResponse createPosition(@RequestHeader(value = USER, required = false) Long userId,
            @PathVariable Long id, @RequestBody(required = false) PositionRequest request) {
        return companyService.createPosition(userId, id, request);
    }

    @PutMapping("/api/positions/{id}")
    public PositionResponse updatePosition(@RequestHeader(value = USER, required = false) Long userId,
            @PathVariable Long id, @RequestBody(required = false) PositionRequest request) {
        return companyService.updatePosition(userId, id, request);
    }
}
