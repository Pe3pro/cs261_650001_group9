package com.example.demo.controller;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.entity.Role;
import com.example.demo.service.ApiException;
import com.example.demo.service.AuthService;

/** U1:T8 Unit test ส่วน API: POST /api/auth/login (AuthService เป็น mock) */
@WebMvcTest(AuthController.class)
class AuthControllerTest {

    private static final String URL = "/api/auth/login";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService service;

    private static String body(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    @DisplayName("API-L01 login ถูกต้อง ได้ 200 พร้อม token, userId และ role")
    void success() throws Exception {
        when(service.login(new LoginRequest("6609650001", "secret"))).thenReturn(new LoginResponse(
                "token-1", "Bearer", Instant.parse("2026-10-19T04:00:00Z"), 3L, "6609650001", "A arizona", Role.STUDENT));

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("6609650001", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token-1"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.userId").value(3))
                .andExpect(jsonPath("$.username").value("6609650001"))
                .andExpect(jsonPath("$.fullName").value("A arizona"))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("API-L02 Username หรือ Password ผิด ได้ 401 INVALID_CREDENTIALS")
    void wrongPassword() throws Exception {
        when(service.login(any())).thenThrow(new ApiException(401, "INVALID_CREDENTIALS", "Username หรือ Password ไม่ถูกต้อง"));

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("6609650001", "wrong")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Username หรือ Password ไม่ถูกต้อง"));
    }

    @Test
    @DisplayName("API-L03 ไม่กรอก Username หรือ Password ได้ 400 VALIDATION_ERROR")
    void blank() throws Exception {
        when(service.login(any())).thenThrow(ApiException.badRequest("กรุณากรอก Username และ Password"));

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("API-L04 body ไม่ใช่ JSON ได้ 400 และไม่เรียก service")
    void badJson() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("username=6609650001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(service, never()).login(any());
    }

    @Test
    @DisplayName("API-L05 บัญชีถูกปิดใช้งาน ได้ 403 FORBIDDEN")
    void inactive() throws Exception {
        when(service.login(any())).thenThrow(new ApiException(403, "FORBIDDEN", "บัญชีนี้ถูกปิดการใช้งาน กรุณาติดต่อเจ้าหน้าที่"));

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("advisor03", "secret")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("API-L06 เชื่อมต่อ TU REST API ไม่ได้ ได้ 502 TU_API_UNAVAILABLE")
    void tuDown() throws Exception {
        when(service.login(any())).thenThrow(new ApiException(502, "TU_API_UNAVAILABLE", "ไม่สามารถเชื่อมต่อระบบของมหาวิทยาลัยได้ กรุณาลองใหม่อีกครั้ง"));

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body("6609650001", "secret")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("TU_API_UNAVAILABLE"));
    }
}