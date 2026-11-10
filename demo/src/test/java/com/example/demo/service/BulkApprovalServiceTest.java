package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.demo.dto.BulkApprovalRequest;
import com.example.demo.dto.BulkApprovalResponse;
import com.example.demo.entity.Role;

/** U5: อนุมัติหลายคำร้องพร้อมกัน (BulkApprovalService) */
class BulkApprovalServiceTest {

    private ApprovalService approvalService;
    private AccessGuard guard;
    private BulkApprovalService service;

    @BeforeEach
    void setUp() {
        approvalService = mock(ApprovalService.class);
        guard = mock(AccessGuard.class);
        service = new BulkApprovalService(approvalService, guard);
        when(guard.require(3L, Role.STAFF, Role.ADVISOR)).thenThrow(ApiException.forbidden());
    }

    @Test
    @DisplayName("BK-01 อนุมัติ 3 รายการ รายการที่ 2 สถานะเปลี่ยนไปแล้ว: อีก 2 รายการสำเร็จ รายการที่ 2 ถูกข้ามพร้อมเหตุผล")
    void partialSuccess() {
        when(approvalService.decide(eq(51L), eq(1L), any()))
                .thenThrow(ApiException.conflict("คำร้องนี้ไม่ได้อยู่ในขั้นที่คุณพิจารณาได้"));

        BulkApprovalResponse res = service.approveAll(new BulkApprovalRequest(List.of(50L, 51L, 52L, 50L)), 1L);

        assertThat(res.approved()).containsExactly(50L, 52L);
        assertThat(res.skipped()).hasSize(1);
        assertThat(res.skipped().get(0).applicationId()).isEqualTo(51L);
        assertThat(res.skipped().get(0).reason()).contains("ไม่ได้อยู่ในขั้น");
        verify(approvalService, times(3)).decide(any(), eq(1L), any()); // id ซ้ำทำครั้งเดียว
    }

    @Test
    @DisplayName("BK-02 ไม่เลือกรายการ / เกิน 100 รายการ: 400 · นักศึกษา: 403 และไม่พิจารณาเลย")
    void rejected() {
        assertThatThrownBy(() -> service.approveAll(new BulkApprovalRequest(List.of()), 1L))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(400);
        List<Long> many = Arrays.stream(LongStream.rangeClosed(1, 101).toArray()).boxed().toList();
        assertThatThrownBy(() -> service.approveAll(new BulkApprovalRequest(many), 1L))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(400);
        assertThatThrownBy(() -> service.approveAll(new BulkApprovalRequest(List.of(50L)), 3L))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(403);
        verify(approvalService, never()).decide(any(), any(), any());
    }
}
