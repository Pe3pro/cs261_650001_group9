package com.example.demo.dto;

import java.util.List;

import com.example.demo.entity.Company;
import com.example.demo.entity.CompanyPosition;
import com.example.demo.entity.CompanyStatus;

/**
 * U4: ข้อมูลบริษัทที่แสดงในหน้าจัดอันดับ (GET /api/companies)
 * U6: เพิ่ม openPositions (ตำแหน่งที่เปิดรับ) และ remainingQuota (ที่ว่างรวมของตำแหน่งที่เปิดรับ)
 */
public record CompanyResponse(Long id, String name, String address, CompanyStatus status, int openPositions,
        int remainingQuota) {

    /** U4: ใช้ได้เหมือนเดิม (ไม่มีข้อมูลตำแหน่ง = 0) */
    public CompanyResponse(Long id, String name, String address, CompanyStatus status) {
        this(id, name, address, status, 0, 0);
    }

    public static CompanyResponse from(Company c) {
        return new CompanyResponse(c.getId(), c.getCompanyName(), c.getAddress(), c.getStatus());
    }

    /** U6: นับเฉพาะตำแหน่งที่เปิดรับ */
    public static CompanyResponse from(Company c, List<CompanyPosition> positions) {
        List<CompanyPosition> open = positions.stream().filter(CompanyPosition::isOpen).toList();
        int remaining = open.stream().mapToInt(p -> p.getAvailableSlots() == null ? 0 : p.getAvailableSlots()).sum();
        return new CompanyResponse(c.getId(), c.getCompanyName(), c.getAddress(), c.getStatus(), open.size(),
                remaining);
    }
}
