package com.example.demo.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.demo.dto.AnswerResponse;
import com.example.demo.dto.ApplicationDetailResponse;
import com.example.demo.dto.ApplicationReviewResponse;
import com.example.demo.dto.DocumentResponse;
import com.example.demo.entity.ApplicationStatus;
import com.example.demo.entity.DocumentStatus;
import com.example.demo.service.ApiException;
import com.example.demo.service.ApplicationReviewService;

/** U5:T8 Unit test ส่วน API: GET /api/applications?status= และ /api/applications/{id}/detail */
@WebMvcTest(ApplicationReviewController.class)
class ApplicationReviewControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ApplicationReviewService service;

    private final List<DocumentResponse> transcript =
            List.of(new DocumentResponse(7L, "transcript.pdf", DocumentStatus.UPLOADED));

    @Test
    @DisplayName("API-10 เจ้าหน้าที่ดูคำร้องรอตรวจ ได้ 200 พร้อมชื่อนักศึกษาและเอกสาร")
    void listSubmitted() throws Exception {
        when(service.getByStatus(ApplicationStatus.SUBMITTED, 1L)).thenReturn(List.of(
                new ApplicationReviewResponse(50L, "6609650001", "A arizona", ApplicationStatus.SUBMITTED,
                        LocalDateTime.of(2026, 10, 15, 10, 24), transcript, null, null)));

        mvc.perform(get("/api/applications").param("status", "SUBMITTED").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].studentId").value("6609650001"))
                .andExpect(jsonPath("$[0].studentName").value("A arizona"))
                .andExpect(jsonPath("$[0].documents[0].fileName").value("transcript.pdf"));
    }

    @Test
    @DisplayName("API-11 ส่ง status ที่ไม่มีอยู่จริง ได้ 400")
    void invalidStatus() throws Exception {
        mvc.perform(get("/api/applications").param("status", "WAITING").header("X-User-Id", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("API-12 นักศึกษาเรียกดูรายการคำร้องของทุกคน ได้ 403")
    void studentCannotList() throws Exception {
        when(service.getByStatus(ApplicationStatus.SUBMITTED, 3L)).thenThrow(ApiException.forbidden());

        mvc.perform(get("/api/applications").param("status", "SUBMITTED").header("X-User-Id", "3"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("API-13 ดูรายละเอียดคำร้อง ได้คำตอบในแบบฟอร์มเรียงตามช่อง")
    void detail() throws Exception {
        when(service.getDetail(50L, 1L)).thenReturn(new ApplicationDetailResponse(50L, "6609650001",
                "A arizona", "arizona@dome.tu.ac.th", "วิทยาศาสตร์และเทคโนโลยี", "วิทยาการคอมพิวเตอร์", 3.10,
                "คำร้องขอฝึกสหกิจศึกษา 2/2569", ApplicationStatus.SUBMITTED, LocalDateTime.of(2026, 10, 15, 10, 24),
                List.of(new AnswerResponse("ชื่อ-นามสกุล", "A arizona"),
                        new AnswerResponse("เบอร์โทรศัพท์", "0812345678")),
                transcript, List.of()));

        mvc.perform(get("/api/applications/50/detail").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers", hasSize(2)))
                .andExpect(jsonPath("$.answers[1].label").value("เบอร์โทรศัพท์"))
                .andExpect(jsonPath("$.answers[1].value").value("0812345678"))
                .andExpect(jsonPath("$.approvals", hasSize(0)));
    }

    @Test
    @DisplayName("API-14 ดูรายละเอียดคำร้องที่ไม่มีอยู่ ได้ 404")
    void detailNotFound() throws Exception {
        when(service.getDetail(999L, 1L)).thenThrow(ApiException.notFound("ไม่พบคำร้อง"));

        mvc.perform(get("/api/applications/999/detail").header("X-User-Id", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("ไม่พบคำร้อง"));
    }
}
