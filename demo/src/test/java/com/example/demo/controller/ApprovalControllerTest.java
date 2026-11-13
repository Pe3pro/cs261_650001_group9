package com.example.demo.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.demo.dto.ApprovalRequest;
import com.example.demo.dto.ApprovalResponse;
import com.example.demo.entity.ApplicationStatus;
import com.example.demo.entity.ApprovalDecision;
import com.example.demo.entity.ApprovalLevel;
import com.example.demo.service.ApiException;
import com.example.demo.service.ApprovalService;

/** U5:T8 Unit test ส่วน API: POST /api/approvals/{id}/decision */
@WebMvcTest(ApprovalController.class)
class ApprovalControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ApprovalService service;

    @Test
    @DisplayName("API-15 เจ้าหน้าที่อนุมัติ ได้ 200 และคำร้องเปลี่ยนเป็น UNDER_REVIEW")
    void staffApprove() throws Exception {
        when(service.decide(eq(50L), eq(1L), any(ApprovalRequest.class))).thenReturn(new ApprovalResponse(
                900L, 50L, ApprovalLevel.STAFF, ApprovalDecision.APPROVE, "S staff", null,
                LocalDateTime.of(2026, 10, 16, 11, 0), ApplicationStatus.UNDER_REVIEW));

        mvc.perform(post("/api/approvals/50/decision").header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVE"))
                .andExpect(jsonPath("$.approvalLevel").value("STAFF"))
                .andExpect(jsonPath("$.applicationStatus").value("UNDER_REVIEW"));
    }

    @Test
    @DisplayName("API-16 ส่งกลับแก้ไขโดยไม่กรอกเหตุผล ได้ 400")
    void returnWithoutComment() throws Exception {
        when(service.decide(eq(50L), eq(1L), any()))
                .thenThrow(ApiException.badRequest("กรุณากรอกเหตุผลเมื่อส่งกลับแก้ไขหรือปฏิเสธ"));

        mvc.perform(post("/api/approvals/50/decision").header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"RETURN\",\"comment\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("กรุณากรอกเหตุผลเมื่อส่งกลับแก้ไขหรือปฏิเสธ"));
    }

    @Test
    @DisplayName("API-17 อาจารย์ตัดสินคำร้องที่เจ้าหน้าที่ยังไม่ตรวจ ได้ 409")
    void wrongStage() throws Exception {
        when(service.decide(eq(50L), eq(2L), any()))
                .thenThrow(ApiException.conflict("คำร้องนี้ไม่ได้อยู่ในขั้นที่คุณพิจารณาได้ (สถานะปัจจุบัน: SUBMITTED)"));

        mvc.perform(post("/api/approvals/50/decision").header("X-User-Id", "2")
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"));
    }

    @Test
    @DisplayName("API-18 ส่งผลการพิจารณาที่ไม่มีในระบบ ได้ 400 และไม่เรียก service")
    void unknownDecision() throws Exception {
        mvc.perform(post("/api/approvals/50/decision").header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"MAYBE\"}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).decide(any(), any(), any());
    }
}
