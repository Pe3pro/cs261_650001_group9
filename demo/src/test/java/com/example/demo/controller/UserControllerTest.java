package com.example.demo.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.demo.dto.UserResponse;
import com.example.demo.entity.Role;
import com.example.demo.service.ApiException;
import com.example.demo.service.UserService;

/** U5:T8 Unit test ส่วน API: /api/users/{username} (เชื่อมกับหน้า login ของ U1) */
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserService service;

    @Test
    @DisplayName("API-23 username ของเจ้าหน้าที่ ได้ id และ role STAFF")
    void staff() throws Exception {
        when(service.findByUsername("staff01")).thenReturn(new UserResponse(1L, "staff01", "S staff", Role.STAFF));

        mvc.perform(get("/api/users/staff01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.role").value("STAFF"))
                .andExpect(jsonPath("$.name").value("S staff"));
    }

    @Test
    @DisplayName("API-24 username ที่ไม่มีในระบบ ได้ 404 NOT_FOUND")
    void notFound() throws Exception {
        when(service.findByUsername("nobody")).thenThrow(ApiException.notFound("ไม่พบบัญชีผู้ใช้ nobody ในระบบ"));

        mvc.perform(get("/api/users/nobody"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
