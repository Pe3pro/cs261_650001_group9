package com.example.demo.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.example.demo.dto.CompanyDetailResponse;
import com.example.demo.dto.CompanyResponse;
import com.example.demo.dto.PositionResponse;
import com.example.demo.entity.CompanyStatus;
import com.example.demo.entity.PositionStatus;
import com.example.demo.service.ApiException;
import com.example.demo.service.CompanyService;

/** U6:T4 Unit test ส่วน API: บริษัทและตำแหน่ง (CompanyController) */
@WebMvcTest(CompanyController.class)
class CompanyControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CompanyService companyService;

    private static PositionResponse backend(int quota, int available) {
        return new PositionResponse(11L, 1L, "SCB TechX", "Backend Developer", null, quota, available, PositionStatus.OPEN);
    }

    @Test
    @DisplayName("U6-API-C01 รายละเอียดบริษัท ได้ 200 พร้อมตำแหน่งทั้งหมด")
    void detail() throws Exception {
        when(companyService.getCompany(9L, 1L)).thenReturn(new CompanyDetailResponse(1L, "SCB TechX", "กรุงเทพฯ",
                null, null, null, CompanyStatus.OPEN, List.of(backend(2, 1))));

        mvc.perform(get("/api/companies/1").header("X-User-Id", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("SCB TechX"))
                .andExpect(jsonPath("$.positions", hasSize(1)))
                .andExpect(jsonPath("$.positions[0].availableSlots").value(1));
    }

    @Test
    @DisplayName("U6-API-C02 เพิ่มบริษัท ได้ 201")
    void create() throws Exception {
        when(companyService.createCompany(eq(1L), any()))
                .thenReturn(new CompanyResponse(8L, "Thai Beverage", "กรุงเทพฯ", CompanyStatus.OPEN, 0, 0));

        mvc.perform(post("/api/companies").header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Thai Beverage\", \"address\": \"กรุงเทพฯ\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(8))
                .andExpect(jsonPath("$.openPositions").value(0));
    }

    @Test
    @DisplayName("U6-API-C03 เพิ่มตำแหน่ง ได้ 201")
    void createPosition() throws Exception {
        when(companyService.createPosition(eq(1L), eq(1L), any())).thenReturn(backend(2, 2));

        mvc.perform(post("/api/companies/1/positions").header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"positionName\": \"Backend Developer\", \"quota\": 2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quota").value(2));
    }

    @Test
    @DisplayName("U6-API-C04 ลดโควตาต่ำกว่าที่จับคู่แล้ว ได้ 400 QUOTA_BELOW_MATCHED")
    void quotaBelowMatched() throws Exception {
        when(companyService.updatePosition(eq(1L), eq(11L), any()))
                .thenThrow(new ApiException(400, "QUOTA_BELOW_MATCHED", "ลดโควตาต่ำกว่าจำนวนที่จับคู่แล้ว (2 คน) ไม่ได้"));

        mvc.perform(put("/api/positions/11").header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"positionName\": \"Backend Developer\", \"quota\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUOTA_BELOW_MATCHED"));
    }

    @Test
    @DisplayName("U6-API-C05 นักศึกษาเพิ่มบริษัทไม่ได้ 403")
    void studentCannotCreate() throws Exception {
        when(companyService.createCompany(eq(9L), any())).thenThrow(ApiException.forbidden());

        mvc.perform(post("/api/companies").header("X-User-Id", "9")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"X\"}"))
                .andExpect(status().isForbidden());
    }
}
