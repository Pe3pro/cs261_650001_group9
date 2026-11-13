package com.example.demo.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dto.CompanyDetailResponse;
import com.example.demo.dto.CompanyRequest;
import com.example.demo.dto.CompanyResponse;
import com.example.demo.dto.CompanyStatusRequest;
import com.example.demo.dto.PositionRequest;
import com.example.demo.dto.PositionResponse;
import com.example.demo.entity.Company;
import com.example.demo.entity.CompanyPosition;
import com.example.demo.entity.CompanyStatus;
import com.example.demo.entity.PositionStatus;
import com.example.demo.entity.Role;
import com.example.demo.repository.CompanyPositionRepository;
import com.example.demo.repository.CompanyRepository;

/**
 * U4: รายชื่อบริษัทที่เปิดรับ และให้เจ้าหน้าที่เปิด–ปิดรับบริษัท
 * U6: เพิ่มจำนวนตำแหน่ง / ที่ว่างในรายชื่อบริษัท · รายละเอียดบริษัท · เจ้าหน้าที่เพิ่ม / แก้บริษัทและตำแหน่ง
 */
@Service
@Transactional
public class CompanyService {

    private static final int MAX_QUOTA = 999;

    private final CompanyRepository companyRepository;
    private final CompanyPositionRepository positionRepository;
    private final AccessGuard guard;

    /** U4: ใช้ใน test เดิมที่ไม่มีข้อมูลตำแหน่ง (จำนวนตำแหน่ง / ที่ว่าง = 0) */
    public CompanyService(CompanyRepository companyRepository, AccessGuard guard) {
        this(companyRepository, null, guard);
    }

    @Autowired
    public CompanyService(CompanyRepository companyRepository, CompanyPositionRepository positionRepository,
            AccessGuard guard) {
        this.companyRepository = companyRepository;
        this.positionRepository = positionRepository;
        this.guard = guard;
    }

    /** U4-SVC-01: บริษัทที่เปิดรับเท่านั้น เรียงตาม id (ผู้ใช้ทุกบทบาทที่ login แล้วดูได้) · U6: พร้อมจำนวนตำแหน่งและที่ว่าง */
    @Transactional(readOnly = true)
    public List<CompanyResponse> getOpenCompanies(Long userId) {
        guard.require(userId);
        return companyRepository.findByStatus(CompanyStatus.OPEN).stream()
                .sorted(Comparator.comparing(Company::getId))
                .map(c -> positionRepository == null ? CompanyResponse.from(c)
                        : CompanyResponse.from(c, positionRepository.findByCompanyId(c.getId())))
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

    /** U6-SVC-C01: รายละเอียดบริษัทและตำแหน่งทั้งหมด (ทุกบทบาทที่ login แล้ว) */
    @Transactional(readOnly = true)
    public CompanyDetailResponse getCompany(Long userId, Long companyId) {
        guard.require(userId);
        Company company = findCompany(companyId);
        List<PositionResponse> positions = positionRepository.findByCompanyId(companyId).stream()
                .sorted(Comparator.comparing(CompanyPosition::getId))
                .map(p -> PositionResponse.from(p, company.getCompanyName()))
                .toList();
        return CompanyDetailResponse.from(company, positions);
    }

    /** U6-SVC-C02: เจ้าหน้าที่เพิ่มบริษัท (ไม่ระบุสถานะ = เปิดรับ) */
    public CompanyResponse createCompany(Long userId, CompanyRequest request) {
        guard.require(userId, Role.STAFF);
        String name = requireName(request);
        Company company = new Company(name, request.status() == null ? CompanyStatus.OPEN : request.status());
        company.setAddress(trim(request.address()));
        company.updateContactInfo(trim(request.contactPerson()), trim(request.email()), trim(request.phone()));
        return CompanyResponse.from(companyRepository.save(company), List.of());
    }

    /** U6-SVC-C03: เจ้าหน้าที่แก้ข้อมูลบริษัท (ไม่ระบุสถานะ = คงเดิม) */
    public CompanyResponse updateCompany(Long userId, Long companyId, CompanyRequest request) {
        guard.require(userId, Role.STAFF);
        String name = requireName(request);
        Company company = findCompany(companyId);
        company.setCompanyName(name);
        company.setAddress(trim(request.address()));
        company.updateContactInfo(trim(request.contactPerson()), trim(request.email()), trim(request.phone()));
        if (request.status() != null) {
            company.setStatus(request.status());
        }
        Company saved = companyRepository.save(company);
        return CompanyResponse.from(saved, positionRepository.findByCompanyId(saved.getId()));
    }

    /** U6-SVC-C04: เจ้าหน้าที่เพิ่มตำแหน่งและโควตาให้บริษัท */
    public PositionResponse createPosition(Long userId, Long companyId, PositionRequest request) {
        guard.require(userId, Role.STAFF);
        String name = requirePositionName(request);
        int quota = requireQuota(request);
        Company company = findCompany(companyId);
        CompanyPosition position = new CompanyPosition(companyId, name, quota,
                request.status() == null ? PositionStatus.OPEN : request.status());
        position.setDescription(trim(request.description()));
        return PositionResponse.from(positionRepository.save(position), company.getCompanyName());
    }

    /** U6-SVC-C05: เจ้าหน้าที่แก้ตำแหน่ง · ลดโควตาต่ำกว่าจำนวนที่จับคู่แล้วไม่ได้ (400 QUOTA_BELOW_MATCHED) */
    public PositionResponse updatePosition(Long userId, Long positionId, PositionRequest request) {
        guard.require(userId, Role.STAFF);
        String name = requirePositionName(request);
        int quota = requireQuota(request);
        CompanyPosition position = positionRepository.findByIdForUpdate(positionId)
                .orElseThrow(() -> ApiException.notFound("ไม่พบตำแหน่งนี้"));
        if (quota < position.usedSlots()) {
            throw new ApiException(400, "QUOTA_BELOW_MATCHED",
                    "ลดโควตาต่ำกว่าจำนวนที่จับคู่แล้ว (" + position.usedSlots() + " คน) ไม่ได้");
        }
        position.updateQuota(quota);
        position.setPositionName(name);
        position.setDescription(trim(request.description()));
        if (request.status() != null) {
            position.setStatus(request.status());
        }
        CompanyPosition saved = positionRepository.save(position);
        String companyName = companyRepository.findById(saved.getCompanyId())
                .map(Company::getCompanyName).orElse(null);
        return PositionResponse.from(saved, companyName);
    }

    private Company findCompany(Long companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> ApiException.notFound("ไม่พบบริษัทนี้"));
    }

    private static String requireName(CompanyRequest request) {
        String name = request == null ? null : trim(request.name());
        if (name == null) {
            throw ApiException.badRequest("กรุณากรอกชื่อบริษัท");
        }
        return name;
    }

    private static String requirePositionName(PositionRequest request) {
        String name = request == null ? null : trim(request.positionName());
        if (name == null) {
            throw ApiException.badRequest("กรุณากรอกชื่อตำแหน่ง");
        }
        return name;
    }

    private static int requireQuota(PositionRequest request) {
        Integer quota = request.quota();
        if (quota == null || quota < 1 || quota > MAX_QUOTA) {
            throw ApiException.badRequest("โควตาต้องเป็นจำนวนเต็ม 1–" + MAX_QUOTA);
        }
        return quota;
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
