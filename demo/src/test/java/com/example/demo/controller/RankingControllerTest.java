package com.example.demo.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.demo.dto.CompanyResponse;
import com.example.demo.dto.RankingItemResponse;
import com.example.demo.dto.RankingRequest;
import com.example.demo.entity.CompanyStatus;
import com.example.demo.service.ApiException;
import com.example.demo.service.CompanyService;
import com.example.demo.service.RankingService;

/** U4:T8 Unit test ส่วน API: การจับคู่บริษัท (จัดอันดับ) */
@WebMvcTest(RankingController.class)
class RankingControllerTest {

    private static final String RANKING = "/api/students/me/company-ranking";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RankingService rankingService;

    @MockitoBean
    private CompanyService companyService;

    private static List<RankingItemResponse> fiveRanks() {
        return List.of(
                new RankingItemResponse(1, 3L, "KBTG"),
                new RankingItemResponse(2, 1L, "SCB TechX"),
                new RankingItemResponse(3, 5L, "Bitkub"),
                new RankingItemResponse(4, 2L, "Agoda Services"),
                new RankingItemResponse(5, 4L, "LINE MAN Wongnai"));
    }

    @Test
    @DisplayName("U4-API-01 GET /api/companies ได้ 200 เฉพาะบริษัทที่เปิดรับ")
    void openCompanies() throws Exception {
        when(companyService.getOpenCompanies(3L)).thenReturn(List.of(
                new CompanyResponse(1L, "SCB TechX", "กรุงเทพฯ", CompanyStatus.OPEN),
                new CompanyResponse(2L, "Agoda Services", "กรุงเทพฯ", CompanyStatus.OPEN)));

        mvc.perform(get("/api/companies").header("X-User-Id", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("SCB TechX"))
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[1].status").value("OPEN"));
    }

    @Test
    @DisplayName("U4-API-02 บันทึกครบ 5 อันดับไม่ซ้ำ ได้ 200 พร้อมลำดับ 1–5")
    void saveFiveRanks() throws Exception {
        when(rankingService.saveMyRanking(eq(3L), eq(new RankingRequest(List.of(3L, 1L, 5L, 2L, 4L)))))
                .thenReturn(fiveRanks());

        mvc.perform(put(RANKING).header("X-User-Id", "3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"companyIds\": [3, 1, 5, 2, 4]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].companyName").value("KBTG"))
                .andExpect(jsonPath("$[4].rank").value(5));
    }

    @Test
    @DisplayName("U4-API-03 เลือกน้อยกว่า 5 อันดับ ได้ 400")
    void lessThanFive() throws Exception {
        when(rankingService.saveMyRanking(eq(3L), any()))
                .thenThrow(new ApiException(400, "INVALID_RANKING", "ต้องเลือกบริษัทให้ครบ 5 อันดับ (เลือกมา 3 อันดับ)"));

        mvc.perform(put(RANKING).header("X-User-Id", "3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"companyIds\": [1, 2, 3]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RANKING"));
    }

    @Test
    @DisplayName("U4-API-03 มีบริษัทซ้ำ ได้ 400")
    void duplicateCompany() throws Exception {
        when(rankingService.saveMyRanking(eq(3L), any()))
                .thenThrow(new ApiException(400, "DUPLICATE_COMPANY", "เลือกบริษัทเดียวกันซ้ำหลายอันดับไม่ได้"));

        mvc.perform(put(RANKING).header("X-User-Id", "3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"companyIds\": [1, 1, 2, 3, 4]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_COMPANY"));
    }

    @Test
    @DisplayName("U4-API-04 Company ID ที่ไม่มีอยู่ ได้ 400")
    void companyNotFound() throws Exception {
        when(rankingService.saveMyRanking(eq(3L), any()))
                .thenThrow(new ApiException(400, "COMPANY_NOT_FOUND", "ไม่พบบริษัท id: 99999"));

        mvc.perform(put(RANKING).header("X-User-Id", "3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"companyIds\": [1, 2, 3, 4, 99999]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMPANY_NOT_FOUND"));
    }

    @Test
    @DisplayName("U4-API-05 นักศึกษาที่ยังไม่ผ่าน U2 ได้ 403")
    void notEligible() throws Exception {
        when(rankingService.saveMyRanking(eq(6L), any()))
                .thenThrow(new ApiException(403, "NOT_ELIGIBLE", "คุณยังไม่ผ่านการตรวจสอบคุณสมบัติ"));

        mvc.perform(put(RANKING).header("X-User-Id", "6")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"companyIds\": [1, 2, 3, 4, 5]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ELIGIBLE"));
    }

    @Test
    @DisplayName("U4-API-06 ยื่นคำร้อง (U3) ไปแล้ว ได้ 409")
    void alreadySubmitted() throws Exception {
        when(rankingService.saveMyRanking(eq(3L), any()))
                .thenThrow(new ApiException(409, "RANKING_LOCKED", "คุณยื่นคำร้องแล้ว ไม่สามารถแก้ไขอันดับบริษัทได้"));

        mvc.perform(put(RANKING).header("X-User-Id", "3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"companyIds\": [1, 2, 3, 4, 5]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RANKING_LOCKED"));
    }

    @Test
    @DisplayName("U4-API-07 เคยบันทึกไว้แล้ว GET ได้ 200 เรียงอันดับ 1–5")
    void getSavedRanking() throws Exception {
        when(rankingService.getMyRanking(3L)).thenReturn(fiveRanks());

        mvc.perform(get(RANKING).header("X-User-Id", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[1].rank").value(2))
                .andExpect(jsonPath("$[2].rank").value(3))
                .andExpect(jsonPath("$[3].rank").value(4))
                .andExpect(jsonPath("$[4].rank").value(5));
    }

    @Test
    @DisplayName("U4-API-08 นักศึกษาเรียก PUT /api/staff/companies/{id} ได้ 403")
    void studentCannotUpdateCompany() throws Exception {
        when(companyService.updateStatus(eq(1L), any(), eq(3L))).thenThrow(ApiException.forbidden());

        mvc.perform(put("/api/staff/companies/1").header("X-User-Id", "3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"CLOSED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("U4-API-09 เจ้าหน้าที่ปิดรับบริษัท ได้ 200 สถานะ CLOSED")
    void staffClosesCompany() throws Exception {
        when(companyService.updateStatus(eq(1L), any(), eq(1L)))
                .thenReturn(new CompanyResponse(1L, "SCB TechX", "กรุงเทพฯ", CompanyStatus.CLOSED));

        mvc.perform(put("/api/staff/companies/1").header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"CLOSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    @DisplayName("U4-API-10 ส่งสถานะบริษัทที่ไม่มีอยู่ (ไม่ใช่ OPEN/CLOSED) ได้ 400")
    void invalidCompanyStatus() throws Exception {
        mvc.perform(put("/api/staff/companies/1").header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"MAYBE\"}"))
                .andExpect(status().isBadRequest());
    }
}
