package com.example.demo.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.CompanyResponse;
import com.example.demo.dto.CompanyStatusRequest;
import com.example.demo.dto.RankingItemResponse;
import com.example.demo.dto.RankingRequest;
import com.example.demo.service.CompanyService;
import com.example.demo.service.RankingService;

/**
 * U4: การจับคู่บริษัท (จัดอันดับ)
 * GET /api/companies                    ดูบริษัทที่เปิดรับ
 * GET /api/students/me/company-ranking  ดูลำดับที่บันทึกไว้
 * PUT /api/students/me/company-ranking  บันทึกลำดับ 5 อันดับ
 * PUT /api/staff/companies/{id}         เจ้าหน้าที่เปิด–ปิดรับบริษัท
 */
@RestController
public class RankingController {

    private static final String USER = "X-User-Id";

    private final RankingService rankingService;
    private final CompanyService companyService;

    public RankingController(RankingService rankingService, CompanyService companyService) {
        this.rankingService = rankingService;
        this.companyService = companyService;
    }

    @GetMapping("/api/companies")
    public List<CompanyResponse> openCompanies(@RequestHeader(value = USER, required = false) Long userId) {
        return companyService.getOpenCompanies(userId);
    }

    @GetMapping("/api/students/me/company-ranking")
    public List<RankingItemResponse> myRanking(@RequestHeader(value = USER, required = false) Long userId) {
        return rankingService.getMyRanking(userId);
    }

    @PutMapping("/api/students/me/company-ranking")
    public List<RankingItemResponse> saveRanking(@RequestBody(required = false) RankingRequest request,
            @RequestHeader(value = USER, required = false) Long userId) {
        return rankingService.saveMyRanking(userId, request);
    }

    @PutMapping("/api/staff/companies/{id}")
    public CompanyResponse updateCompany(@PathVariable Long id,
            @RequestBody(required = false) CompanyStatusRequest request,
            @RequestHeader(value = USER, required = false) Long userId) {
        return companyService.updateStatus(id, request, userId);
    }
}
