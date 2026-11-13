package com.example.demo.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.demo.dto.PageResponse;
import com.example.demo.dto.StudentStatusDetailResponse;
import com.example.demo.dto.StudentStatusFilter;
import com.example.demo.dto.StudentStatusResponse;
import com.example.demo.entity.InternshipStage;
import com.example.demo.entity.InternshipStatus;
import com.example.demo.service.ApiException;
import com.example.demo.service.StudentStatusService;

@ExtendWith(MockitoExtension.class)
@DisplayName("U7 Student Status Controller")
class StudentStatusControllerTest {

    private static final String USER_HEADER = "X-User-Id";
    private static final Long STAFF_ID = 1L;
    private static final Long STUDENT_ID = 10L;

    @Mock
    private StudentStatusService studentStatusService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        StudentStatusController controller =
                new StudentStatusController(
                        studentStatusService
                );

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(
                        new ApiExceptionHandler()
                )
                .build();
    }

    /*
     * API-S01
     * GET /api/students/status
     * เรียกดูรายการนักศึกษาด้วยค่าเริ่มต้น
     */
    @Test
    @DisplayName("API-S01 แสดงรายการสถานะนักศึกษา")
    void search_returnsStudentStatusList()
            throws Exception {

        when(studentStatusService.search(
                eq(STAFF_ID),
                any(StudentStatusFilter.class)
        )).thenReturn(pageResponse());

        mockMvc.perform(get(
                        "/api/students/status"
                )
                .header(USER_HEADER, STAFF_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()")
                        .value(1))
                .andExpect(jsonPath(
                        "$.content[0].studentId"
                ).value(STUDENT_ID))
                .andExpect(jsonPath(
                        "$.content[0].studentCode"
                ).value("6609650001"))
                .andExpect(jsonPath(
                        "$.content[0].studentName"
                ).value("สมชาย ใจดี"))
                .andExpect(jsonPath(
                        "$.content[0].stage"
                ).value("APPLYING"))
                .andExpect(jsonPath("$.page")
                        .value(0))
                .andExpect(jsonPath("$.size")
                        .value(20))
                .andExpect(jsonPath("$.totalElements")
                        .value(1))
                .andExpect(jsonPath("$.totalPages")
                        .value(1));

        verify(studentStatusService).search(
                eq(STAFF_ID),
                argThat(filter ->
                        filter.stage() == null
                        && filter.companyId() == null
                        && filter.q() == null
                        && filter.advisorId() == null
                        && filter.page() == 0
                        && filter.size() == 20
                )
        );
    }

    /*
     * API-S02
     * ส่งตัวกรอง stage, companyId, q และ advisorId
     */
    @Test
    @DisplayName("API-S02 ส่งตัวกรองไปยัง Service ครบ")
    void search_passesFiltersToService()
            throws Exception {

        when(studentStatusService.search(
                eq(STAFF_ID),
                any(StudentStatusFilter.class)
        )).thenReturn(pageResponse());

        mockMvc.perform(get(
                        "/api/students/status"
                )
                .header(USER_HEADER, STAFF_ID)
                .param("stage", "waiting_match")
                .param("companyId", "30")
                .param("q", "6609650001")
                .param("advisorId", "40"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.content[0].studentCode"
                ).value("6609650001"));

        verify(studentStatusService).search(
                eq(STAFF_ID),
                argThat(filter ->
                        filter.stage()
                                == InternshipStage.WAITING_MATCH
                        && Long.valueOf(30L).equals(
                                filter.companyId())
                        && "6609650001".equals(
                                filter.q())
                        && Long.valueOf(40L).equals(
                                filter.advisorId())
                )
        );
    }

    /*
     * API-S03
     * stage ไม่ถูกต้องต้องตอบ 400
     */
    @Test
    @DisplayName("API-S03 Stage ไม่ถูกต้องตอบ 400")
    void search_invalidStageReturnsBadRequest()
            throws Exception {

        mockMvc.perform(get(
                        "/api/students/status"
                )
                .header(USER_HEADER, STAFF_ID)
                .param("stage", "UNKNOWN_STAGE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"));

        verify(studentStatusService, never())
                .search(
                        any(),
                        any(StudentStatusFilter.class)
                );
    }

    /*
     * API-S04
     * ทดสอบเลขหน้าและขนาดหน้า
     */
    @Test
    @DisplayName("API-S04 ส่ง Page และ Size ถูกต้อง")
    void search_passesPagination()
            throws Exception {

        PageResponse<StudentStatusResponse> page =
                new PageResponse<>(
                        List.of(studentStatus()),
                        2,
                        5,
                        16L,
                        4
                );

        when(studentStatusService.search(
                eq(STAFF_ID),
                any(StudentStatusFilter.class)
        )).thenReturn(page);

        mockMvc.perform(get(
                        "/api/students/status"
                )
                .header(USER_HEADER, STAFF_ID)
                .param("page", "2")
                .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page")
                        .value(2))
                .andExpect(jsonPath("$.size")
                        .value(5))
                .andExpect(jsonPath("$.totalElements")
                        .value(16))
                .andExpect(jsonPath("$.totalPages")
                        .value(4));

        verify(studentStatusService).search(
                eq(STAFF_ID),
                argThat(filter ->
                        filter.page() == 2
                        && filter.size() == 5
                )
        );
    }

    /*
     * API-S05
     * GET /api/students/status/summary
     */
    @Test
    @DisplayName("API-S05 แสดงจำนวนแต่ละ Stage")
    void summary_returnsStageCounts()
            throws Exception {

        Map<InternshipStage, Long> summary =
                Map.of(
                        InternshipStage.NOT_APPLIED, 2L,
                        InternshipStage.APPLYING, 3L,
                        InternshipStage.WAITING_MATCH, 4L
                );

        when(studentStatusService.summary(
                STAFF_ID
        )).thenReturn(summary);

        mockMvc.perform(get(
                        "/api/students/status/summary"
                )
                .header(USER_HEADER, STAFF_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.NOT_APPLIED")
                        .value(2))
                .andExpect(jsonPath("$.APPLYING")
                        .value(3))
                .andExpect(jsonPath("$.WAITING_MATCH")
                        .value(4));

        verify(studentStatusService).summary(
                STAFF_ID
        );
    }

    /*
     * API-S06
     * Export CSV ทั้งหมด
     */
    @Test
    @DisplayName("API-S06 ดาวน์โหลด CSV ทั้งหมด")
    void export_returnsCsvFile()
            throws Exception {

        byte[] csv = (
                "\uFEFFstudentCode,studentName,stage\n"
                + "6609650001,สมชาย ใจดี,APPLYING\n"
        ).getBytes(StandardCharsets.UTF_8);

        when(studentStatusService.exportCsv(
                STAFF_ID,
                null
        )).thenReturn(csv);

        mockMvc.perform(get(
                        "/api/students/status/export"
                )
                .header(USER_HEADER, STAFF_ID))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"student-status.csv\""
                ))
                .andExpect(content().contentType(
                        "text/csv;charset=UTF-8"
                ))
                .andExpect(content().bytes(csv));

        verify(studentStatusService).exportCsv(
                STAFF_ID,
                null
        );
    }

    /*
     * API-S07
     * Export CSV ตาม Stage
     */
    @Test
    @DisplayName("API-S07 ดาวน์โหลด CSV ตาม Stage")
    void export_filtersByStage()
            throws Exception {

        byte[] csv = (
                "\uFEFFstudentCode,stage\n"
                + "6609650001,IN_PROGRESS\n"
        ).getBytes(StandardCharsets.UTF_8);

        when(studentStatusService.exportCsv(
                STAFF_ID,
                InternshipStage.IN_PROGRESS
        )).thenReturn(csv);

        mockMvc.perform(get(
                        "/api/students/status/export"
                )
                .header(USER_HEADER, STAFF_ID)
                .param("stage", "in_progress"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename="
                        + "\"student-status-in_progress.csv\""
                ))
                .andExpect(content().bytes(csv));

        verify(studentStatusService).exportCsv(
                STAFF_ID,
                InternshipStage.IN_PROGRESS
        );
    }

    /*
     * API-S08
     * GET /api/students/me/status
     */
    @Test
    @DisplayName("API-S08 นักศึกษาดูสถานะของตนเอง")
    void myStatus_returnsCurrentStudentStatus()
            throws Exception {

        when(studentStatusService.getMyStatus(
                STUDENT_ID
        )).thenReturn(detailResponse(
                InternshipStage.READY
        ));

        mockMvc.perform(get(
                        "/api/students/me/status"
                )
                .header(USER_HEADER, STUDENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage")
                        .value("READY"))
                .andExpect(jsonPath("$.preferences")
                        .isArray())
                .andExpect(jsonPath("$.timeline")
                        .isArray());

        verify(studentStatusService).getMyStatus(
                STUDENT_ID
        );
    }

    /*
     * API-S09
     * GET /api/students/{studentId}/status
     */
    @Test
    @DisplayName("API-S09 เจ้าหน้าที่ดูรายละเอียดนักศึกษา")
    void detail_returnsStudentDetail()
            throws Exception {

        when(studentStatusService.getDetail(
                STAFF_ID,
                STUDENT_ID
        )).thenReturn(detailResponse(
                InternshipStage.APPLYING
        ));

        mockMvc.perform(get(
                        "/api/students/{studentId}/status",
                        STUDENT_ID
                )
                .header(USER_HEADER, STAFF_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage")
                        .value("APPLYING"))
                .andExpect(jsonPath("$.preferences")
                        .isArray())
                .andExpect(jsonPath("$.timeline")
                        .isArray());

        verify(studentStatusService).getDetail(
                STAFF_ID,
                STUDENT_ID
        );
    }

    /*
     * API-S10
     * GET /api/students/{studentId}/timeline
     */
    @Test
    @DisplayName("API-S10 แสดง Timeline ของนักศึกษา")
    void timeline_returnsStatusHistory()
            throws Exception {

        when(studentStatusService.getTimeline(
                STAFF_ID,
                STUDENT_ID
        )).thenReturn(List.of());

        mockMvc.perform(get(
                        "/api/students/{studentId}/timeline",
                        STUDENT_ID
                )
                .header(USER_HEADER, STAFF_ID))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(studentStatusService).getTimeline(
                STAFF_ID,
                STUDENT_ID
        );
    }

    /*
     * API-S11
     * ไม่มี Login ตอบ 401 และไม่มีสิทธิ์ตอบ 403
     */
    @Test
    @DisplayName("API-S11 ตรวจสอบ Login และสิทธิ์ผู้ใช้งาน")
    void endpoints_returnAuthorizationErrors()
            throws Exception {

        when(studentStatusService.summary(
                isNull()
        )).thenThrow(
                ApiException.unauthorized()
        );

        mockMvc.perform(get(
                        "/api/students/status/summary"
                ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("UNAUTHORIZED"));

        when(studentStatusService.search(
                eq(99L),
                any(StudentStatusFilter.class)
        )).thenThrow(
                ApiException.forbidden()
        );

        mockMvc.perform(get(
                        "/api/students/status"
                )
                .header(USER_HEADER, 99L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("FORBIDDEN"));
    }

    private PageResponse<StudentStatusResponse>
            pageResponse() {

        return new PageResponse<>(
                List.of(studentStatus()),
                0,
                20,
                1L,
                1
        );
    }

    private StudentStatusResponse studentStatus() {
        return new StudentStatusResponse(
                STUDENT_ID,
                "6609650001",
                "สมชาย ใจดี",
                "อ.ดร.วิชัย สอนดี",
                null,
                null,
                null,
                InternshipStage.APPLYING,
                InternshipStatus.NOT_STARTED,
                null,
                LocalDate.of(2026, 11, 15),
                LocalDateTime.of(
                        2026, 11, 12, 15, 49
                )
        );
    }

    private StudentStatusDetailResponse detailResponse(
            InternshipStage stage
    ) {
        return new StudentStatusDetailResponse(
                null,
                null,
                List.of(),
                null,
                null,
                List.of(),
                stage,
                null
        );
    }
}