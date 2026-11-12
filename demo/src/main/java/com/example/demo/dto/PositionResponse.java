package com.example.demo.dto;

import com.example.demo.entity.CompanyPosition;
import com.example.demo.entity.PositionStatus;

/** U6: ตำแหน่งงานของบริษัท พร้อมโควตาและที่ว่าง */
public record PositionResponse(Long id, Long companyId, String companyName, String positionName, String description,
        Integer quota, Integer availableSlots, PositionStatus status) {

    public static PositionResponse from(CompanyPosition p, String companyName) {
        return new PositionResponse(p.getId(), p.getCompanyId(), companyName, p.getPositionName(),
                p.getDescription(), p.getQuota(), p.getAvailableSlots(), p.getStatus());
    }
}
