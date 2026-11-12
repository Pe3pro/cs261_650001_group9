package com.example.demo.dto;

import java.util.List;

import com.example.demo.entity.Company;
import com.example.demo.entity.CompanyStatus;

/** U6: GET /api/companies/{id} รายละเอียดบริษัทและตำแหน่งทั้งหมด */
public record CompanyDetailResponse(Long id, String name, String address, String contactPerson, String email,
        String phone, CompanyStatus status, List<PositionResponse> positions) {

    public static CompanyDetailResponse from(Company c, List<PositionResponse> positions) {
        return new CompanyDetailResponse(c.getId(), c.getCompanyName(), c.getAddress(), c.getContactPerson(),
                c.getEmail(), c.getPhone(), c.getStatus(), positions);
    }
}
