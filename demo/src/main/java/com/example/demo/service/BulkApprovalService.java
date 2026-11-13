package com.example.demo.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.demo.dto.ApprovalRequest;
import com.example.demo.dto.BulkApprovalRequest;
import com.example.demo.dto.BulkApprovalResponse;
import com.example.demo.dto.BulkApprovalResponse.Skipped;
import com.example.demo.entity.ApprovalDecision;
import com.example.demo.entity.Role;

/**
 * U5: เจ้าหน้าที่ / อาจารย์ อนุมัติหลายคำร้องในครั้งเดียว
 * ไม่ครอบด้วย transaction เดียว: แต่ละคำร้องผ่าน ApprovalService.decide (transaction ของตัวเอง)
 * ถ้าบางรายการอนุมัติไม่ได้ (สถานะเปลี่ยนไปแล้ว / ไม่ใช่นักศึกษาในที่ปรึกษา) รายการอื่นยังสำเร็จตามปกติ
 */
@Service
public class BulkApprovalService {

    /** จำกัดจำนวนต่อครั้ง กันส่งรายการใหญ่เกินไปในคำขอเดียว */
    public static final int MAX_ITEMS = 100;

    private final ApprovalService approvalService;
    private final AccessGuard guard;

    public BulkApprovalService(ApprovalService approvalService, AccessGuard guard) {
        this.approvalService = approvalService;
        this.guard = guard;
    }

    public BulkApprovalResponse approveAll(BulkApprovalRequest request, Long userId) {
        guard.require(userId, Role.STAFF, Role.ADVISOR);
        if (request == null || request.applicationIds() == null || request.applicationIds().isEmpty()) {
            throw ApiException.badRequest("กรุณาเลือกคำร้องอย่างน้อย 1 รายการ");
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>(request.applicationIds());
        ids.remove(null);
        if (ids.size() > MAX_ITEMS) {
            throw ApiException.badRequest("เลือกได้ไม่เกิน " + MAX_ITEMS + " รายการต่อครั้ง");
        }
        List<Long> approved = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        ApprovalRequest approve = new ApprovalRequest(ApprovalDecision.APPROVE, null);
        for (Long id : ids) {
            try {
                approvalService.decide(id, userId, approve);
                approved.add(id);
            } catch (RuntimeException e) {
                skipped.add(new Skipped(id, e.getMessage()));
            }
        }
        return new BulkApprovalResponse(approved, skipped);
    }
}
