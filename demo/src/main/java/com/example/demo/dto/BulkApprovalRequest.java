package com.example.demo.dto;

import java.util.List;

/** U5: อนุมัติหลายคำร้องพร้อมกัน */
public record BulkApprovalRequest(List<Long> applicationIds) {
}
