package com.example.demo.dto;

import com.example.demo.entity.CompanyStatus;

/** U4: PUT /api/staff/companies/{id} · เจ้าหน้าที่เปิด–ปิดรับบริษัท เช่น {"status": "CLOSED"} */
public record CompanyStatusRequest(CompanyStatus status) {
}
