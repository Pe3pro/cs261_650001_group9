package com.example.demo.dto;

import com.example.demo.entity.PositionStatus;

/** U6: POST /api/companies/{id}/positions · PUT /api/positions/{id} (status ไม่ส่ง = OPEN ตอนสร้าง / คงเดิมตอนแก้) */
public record PositionRequest(String positionName, String description, Integer quota, PositionStatus status) {
}
