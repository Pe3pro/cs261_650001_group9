package com.example.demo.dto;

import com.example.demo.entity.Company;
import com.example.demo.entity.CompanyStatus;

/** U4: ข้อมูลบริษัทที่แสดงในหน้าจัดอันดับ (GET /api/companies) */
public record CompanyResponse(Long id, String name, String address, CompanyStatus status) {

    public static CompanyResponse from(Company c) {
        return new CompanyResponse(c.getId(), c.getCompanyName(), c.getAddress(), c.getStatus());
    }
}
