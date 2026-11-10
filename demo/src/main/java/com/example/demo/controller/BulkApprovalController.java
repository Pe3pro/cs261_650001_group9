package com.example.demo.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.BulkApprovalRequest;
import com.example.demo.dto.BulkApprovalResponse;
import com.example.demo.service.BulkApprovalService;

/** U5: API อนุมัติหลายคำร้องพร้อมกัน */
@RestController
public class BulkApprovalController {

    private final BulkApprovalService bulkApprovalService;

    public BulkApprovalController(BulkApprovalService bulkApprovalService) {
        this.bulkApprovalService = bulkApprovalService;
    }

    @PostMapping("/api/approvals/bulk")
    public BulkApprovalResponse approveAll(@RequestBody BulkApprovalRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return bulkApprovalService.approveAll(request, userId);
    }
}
