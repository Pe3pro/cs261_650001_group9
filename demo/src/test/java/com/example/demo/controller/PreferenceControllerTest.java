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

import com.example.demo.dto.PreferenceRequest;
import com.example.demo.dto.PreferenceResponse;
import com.example.demo.service.ApiException;
import com.example.demo.service.PreferenceService;

/** U6:T4 Unit test ส่วน API: การเลือกตำแหน่ง 1–5 อันดับ (PreferenceController) */
@WebMvcTest(PreferenceController.class)
class PreferenceControllerTest {

    private static final String URL = "/api/applications/40/preferences";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PreferenceService preferenceService;

    @Test
    @DisplayName("U6-API-P01 บันทึก 2 อันดับ ได้ 200 พร้อมอันดับที่บันทึก")
    void save() throws Exception {
        when(preferenceService.savePreferences(eq(9L), eq(40L), eq(new PreferenceRequest(List.of(12L, 11L)))))
                .thenReturn(List.of(
                        new PreferenceResponse(1, 12L, "Data Analyst", "SCB TechX", PreferenceResponse.PENDING),
                        new PreferenceResponse(2, 11L, "Backend Developer", "SCB TechX", PreferenceResponse.PENDING)));

        mvc.perform(put(URL).header("X-User-Id", "9")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"positionIds\": [12, 11]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].positionName").value("Data Analyst"))
                .andExpect(jsonPath("$[1].resultStatus").value("PENDING"));
    }

    @Test
    @DisplayName("U6-API-P02 ใบสมัครยังไม่อนุมัติ ได้ 409 APPLICATION_NOT_APPROVED")
    void notApproved() throws Exception {
        when(preferenceService.savePreferences(eq(9L), eq(40L), any()))
                .thenThrow(new ApiException(409, "APPLICATION_NOT_APPROVED", "เลือกอันดับได้หลังใบสมัครได้รับการอนุมัติแล้วเท่านั้น"));

        mvc.perform(put(URL).header("X-User-Id", "9")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"positionIds\": [11]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPLICATION_NOT_APPROVED"));
    }

    @Test
    @DisplayName("U6-API-P03 เลือกเกิน 5 อันดับ ได้ 400 INVALID_PREFERENCE")
    void invalid() throws Exception {
        when(preferenceService.savePreferences(eq(9L), eq(40L), any()))
                .thenThrow(new ApiException(400, "INVALID_PREFERENCE", "กรุณาเลือกตำแหน่ง 1–5 อันดับ"));

        mvc.perform(put(URL).header("X-User-Id", "9")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"positionIds\": [1, 2, 3, 4, 5, 6]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PREFERENCE"));
    }

    @Test
    @DisplayName("U6-API-P04 ดูอันดับของใบสมัครคนอื่น ได้ 403")
    void forbidden() throws Exception {
        when(preferenceService.getPreferences(9L, 40L)).thenThrow(ApiException.forbidden());

        mvc.perform(get(URL).header("X-User-Id", "9"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
