package com.example.demo.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.demo.dto.InternshipRequest;
import com.example.demo.dto.InternshipResponse;
import com.example.demo.entity.InternshipStatus;
import com.example.demo.service.ApiException;
import com.example.demo.service.InternshipService;

@ExtendWith(MockitoExtension.class)
@DisplayName("U7 Internship Controller")
class InternshipControllerTest {

    private static final String USER_HEADER = "X-User-Id";
    private static final Long STAFF_ID = 1L;
    private static final Long STUDENT_ID = 10L;
    private static final Long INTERNSHIP_ID = 20L;

    @Mock
    private InternshipService internshipService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        InternshipController controller =
                new InternshipController(internshipService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    /*
     * API-I01
     * GET /api/internships/{id}
     * ผู้มีสิทธิ์สามารถดูข้อมูลการฝึกงานได้
     */
    @Test
    @DisplayName("API-I01 ดูข้อมูลการฝึกงานสำเร็จ")
    void getInternship_success() throws Exception {
        when(internshipService.get(
                STAFF_ID,
                INTERNSHIP_ID
        )).thenReturn(response(
                InternshipStatus.NOT_STARTED
        ));

        mockMvc.perform(get(
                        "/api/internships/{id}",
                        INTERNSHIP_ID
                )
                .header(USER_HEADER, STAFF_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id")
                        .value(INTERNSHIP_ID))
                .andExpect(jsonPath("$.matchId")
                        .value(30L))
                .andExpect(jsonPath("$.studentId")
                        .value(STUDENT_ID))
                .andExpect(jsonPath("$.status")
                        .value("NOT_STARTED"))
                .andExpect(jsonPath("$.supervisorName")
                        .value("สมชาย ผู้ดูแล"));

        verify(internshipService).get(
                STAFF_ID,
                INTERNSHIP_ID
        );
    }

    /*
     * API-I02
     * PUT /api/internships/{id}
     * เจ้าหน้าที่แก้ไขข้อมูลการฝึกงานได้
     */
    @Test
    @DisplayName("API-I02 แก้ไขข้อมูลการฝึกงานสำเร็จ")
    void updateInternship_success() throws Exception {
        when(internshipService.update(
                eq(STAFF_ID),
                eq(INTERNSHIP_ID),
                any(InternshipRequest.class)
        )).thenReturn(response(
                InternshipStatus.NOT_STARTED
        ));

        String body = """
                {
                  "startDate": "2026-11-15",
                  "endDate": "2027-03-15",
                  "supervisorName": "สมชาย ผู้ดูแล",
                  "supervisorEmail": "supervisor@example.com",
                  "supervisorPhone": "0812345678"
                }
                """;

        mockMvc.perform(put(
                        "/api/internships/{id}",
                        INTERNSHIP_ID
                )
                .header(USER_HEADER, STAFF_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id")
                        .value(INTERNSHIP_ID))
                .andExpect(jsonPath("$.startDate")
                        .value("2026-11-15"))
                .andExpect(jsonPath("$.endDate")
                        .value("2027-03-15"))
                .andExpect(jsonPath("$.supervisorEmail")
                        .value("supervisor@example.com"));

        verify(internshipService).update(
                eq(STAFF_ID),
                eq(INTERNSHIP_ID),
                any(InternshipRequest.class)
        );
    }

    /*
     * API-I03
     * ข้อมูลไม่ถูกต้องต้องตอบ 400
     */
    @Test
    @DisplayName("API-I03 ข้อมูลวันที่ไม่ถูกต้องตอบ 400")
    void updateInternship_invalidData() throws Exception {
        when(internshipService.update(
                eq(STAFF_ID),
                eq(INTERNSHIP_ID),
                any(InternshipRequest.class)
        )).thenThrow(ApiException.badRequest(
                "วันที่สิ้นสุดต้องไม่น้อยกว่าวันที่เริ่ม"
        ));

        String body = """
                {
                  "startDate": "2027-03-15",
                  "endDate": "2026-11-15",
                  "supervisorName": "สมชาย ผู้ดูแล",
                  "supervisorEmail": "supervisor@example.com",
                  "supervisorPhone": "0812345678"
                }
                """;

        mockMvc.perform(put(
                        "/api/internships/{id}",
                        INTERNSHIP_ID
                )
                .header(USER_HEADER, STAFF_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("วันที่สิ้นสุดต้องไม่น้อยกว่าวันที่เริ่ม"));
    }

    /*
     * API-I04
     * สถานะปิดแล้วแก้ไขไม่ได้
     */
    @Test
    @DisplayName("API-I04 แก้ไขสถานะที่ปิดแล้วตอบ 409")
    void updateInternship_closedStatus() throws Exception {
        when(internshipService.update(
                eq(STAFF_ID),
                eq(INTERNSHIP_ID),
                any(InternshipRequest.class)
        )).thenThrow(ApiException.conflict(
                "ไม่สามารถแก้ไขข้อมูลการฝึกงานที่ปิดแล้ว"
        ));

        String body = """
                {
                  "startDate": "2026-11-15",
                  "endDate": "2027-03-15",
                  "supervisorName": "สมชาย ผู้ดูแล",
                  "supervisorEmail": "supervisor@example.com",
                  "supervisorPhone": "0812345678"
                }
                """;

        mockMvc.perform(put(
                        "/api/internships/{id}",
                        INTERNSHIP_ID
                )
                .header(USER_HEADER, STAFF_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_STATUS"))
                .andExpect(jsonPath("$.message")
                        .value("ไม่สามารถแก้ไขข้อมูลการฝึกงานที่ปิดแล้ว"));
    }

    /*
     * API-I05
     * เริ่มฝึกงาน NOT_STARTED -> IN_PROGRESS
     */
    @Test
    @DisplayName("API-I05 เริ่มฝึกงานสำเร็จ")
    void startInternship_success() throws Exception {
        when(internshipService.start(
                STAFF_ID,
                INTERNSHIP_ID
        )).thenReturn(response(
                InternshipStatus.IN_PROGRESS
        ));

        mockMvc.perform(post(
                        "/api/internships/{id}/start",
                        INTERNSHIP_ID
                )
                .header(USER_HEADER, STAFF_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id")
                        .value(INTERNSHIP_ID))
                .andExpect(jsonPath("$.status")
                        .value("IN_PROGRESS"));

        verify(internshipService).start(
                STAFF_ID,
                INTERNSHIP_ID
        );
    }

    /*
     * API-I06
     * เสร็จสิ้นการฝึกงาน IN_PROGRESS -> COMPLETED
     */
    @Test
    @DisplayName("API-I06 เสร็จสิ้นการฝึกงานสำเร็จ")
    void completeInternship_success() throws Exception {
        when(internshipService.complete(
                STAFF_ID,
                INTERNSHIP_ID
        )).thenReturn(response(
                InternshipStatus.COMPLETED
        ));

        mockMvc.perform(post(
                        "/api/internships/{id}/complete",
                        INTERNSHIP_ID
                )
                .header(USER_HEADER, STAFF_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id")
                        .value(INTERNSHIP_ID))
                .andExpect(jsonPath("$.status")
                        .value("COMPLETED"));

        verify(internshipService).complete(
                STAFF_ID,
                INTERNSHIP_ID
        );
    }

    /*
     * API-I07
     * ยุติการฝึกงานพร้อมเหตุผล
     */
    @Test
    @DisplayName("API-I07 ยุติการฝึกงานสำเร็จ")
    void terminateInternship_success() throws Exception {
        InternshipResponse terminated =
                new InternshipResponse(
                        INTERNSHIP_ID,
                        30L,
                        STUDENT_ID,
                        LocalDate.of(2026, 11, 15),
                        LocalDate.of(2027, 3, 15),
                        "สมชาย ผู้ดูแล",
                        "supervisor@example.com",
                        "0812345678",
                        InternshipStatus.TERMINATED,
                        "นักศึกษาขอยุติการฝึกงาน",
                        STAFF_ID,
                        LocalDateTime.of(
                                2026, 11, 12, 10, 0
                        ),
                        LocalDateTime.of(
                                2026, 11, 12, 12, 0
                        )
                );

        when(internshipService.terminate(
                STAFF_ID,
                INTERNSHIP_ID,
                "นักศึกษาขอยุติการฝึกงาน"
        )).thenReturn(terminated);

        String body = """
                {
                  "reason": "นักศึกษาขอยุติการฝึกงาน"
                }
                """;

        mockMvc.perform(post(
                        "/api/internships/{id}/terminate",
                        INTERNSHIP_ID
                )
                .header(USER_HEADER, STAFF_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status")
                        .value("TERMINATED"))
                .andExpect(jsonPath("$.terminatedReason")
                        .value("นักศึกษาขอยุติการฝึกงาน"));

        verify(internshipService).terminate(
                STAFF_ID,
                INTERNSHIP_ID,
                "นักศึกษาขอยุติการฝึกงาน"
        );
    }

    /*
     * API-I08
     * ไม่มี Login ตอบ 401 และไม่มีสิทธิ์ตอบ 403
     */
    @Test
    @DisplayName("API-I08 ตรวจสอบ Login และสิทธิ์ผู้ใช้งาน")
    void internshipEndpoint_authorizationErrors()
            throws Exception {

        when(internshipService.get(
                isNull(),
                eq(INTERNSHIP_ID)
        )).thenThrow(ApiException.unauthorized());

        mockMvc.perform(get(
                        "/api/internships/{id}",
                        INTERNSHIP_ID
                ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("UNAUTHORIZED"));

        when(internshipService.get(
                STUDENT_ID,
                INTERNSHIP_ID
        )).thenThrow(ApiException.forbidden());

        mockMvc.perform(get(
                        "/api/internships/{id}",
                        INTERNSHIP_ID
                )
                .header(USER_HEADER, STUDENT_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("FORBIDDEN"));
    }

    private InternshipResponse response(
            InternshipStatus status
    ) {
        return new InternshipResponse(
                INTERNSHIP_ID,
                30L,
                STUDENT_ID,
                LocalDate.of(2026, 11, 15),
                LocalDate.of(2027, 3, 15),
                "สมชาย ผู้ดูแล",
                "supervisor@example.com",
                "0812345678",
                status,
                null,
                STAFF_ID,
                LocalDateTime.of(
                        2026, 11, 12, 10, 0
                ),
                LocalDateTime.of(
                        2026, 11, 12, 12, 0
                )
        );
    }
}