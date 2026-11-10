package com.example.demo.dto;

import com.example.demo.entity.CompanyStatus;

/** U6: POST /api/companies · PUT /api/companies/{id} (status ไม่ส่ง = OPEN ตอนสร้าง / คงเดิมตอนแก้) */
public record CompanyRequest(String name, String address, String contactPerson, String email, String phone,
        CompanyStatus status) {
}
