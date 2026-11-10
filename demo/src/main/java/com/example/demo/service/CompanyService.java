package com.example.demo.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dto.CompanyResponse;
import com.example.demo.dto.CompanyStatusRequest;
import com.example.demo.entity.Company;
import com.example.demo.entity.CompanyStatus;
import com.example.demo.entity.Role;
import com.example.demo.repository.CompanyRepository;

/** U4: รายชื่อบริษัทที่เปิดรับ และให้เจ้าหน้าที่เปิด–ปิดรับบริษัท */
@Service
@Transactional
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final AccessGuard guard;

    public CompanyService(CompanyRepository companyRepository, AccessGuard guard) {
        this.companyRepository = companyRepository;
        this.guard = guard;
    }

    /** U4-SVC-01: บริษัทที่เปิดรับเท่านั้น เรียงตาม id (ผู้ใช้ทุกบทบาทที่ login แล้วดูได้) */
    @Transactional(readOnly = true)
    public List<CompanyResponse> getOpenCompanies(Long userId) {
        guard.require(userId);
        return companyRepository.findByStatus(CompanyStatus.OPEN).stream()
                .sorted(Comparator.comparing(Company::getId))
                .map(CompanyResponse::from)
                .toList();
    }

    /** U4-API-08: เฉพาะเจ้าหน้าที่ เปิด (OPEN) หรือปิด (CLOSED) การรับนักศึกษาของบริษัท */
    public CompanyResponse updateStatus(Long companyId, CompanyStatusRequest request, Long userId) {
        guard.require(userId, Role.STAFF);
        if (request == null || request.status() == null) {
            throw ApiException.badRequest("กรุณาระบุสถานะ OPEN หรือ CLOSED");
        }
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> ApiException.notFound("ไม่พบบริษัทนี้"));
        if (request.status() == CompanyStatus.OPEN) {
            company.openRecruitment();
        } else {
            company.closeRecruitment();
        }
        return CompanyResponse.from(companyRepository.save(company));
    }
}
