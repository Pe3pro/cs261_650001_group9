package com.example.demo.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.example.demo.dto.BulkWorkLogReviewResponse;
import com.example.demo.dto.WorkLogDetailResponse;
import com.example.demo.dto.WorkLogFilter;
import com.example.demo.dto.WorkLogRequest;
import com.example.demo.dto.WorkLogResponse;
import com.example.demo.dto.WorkLogSummaryResponse;
import com.example.demo.entity.Internship;
import com.example.demo.entity.InternshipStatus;
import com.example.demo.entity.ReviewDecision;
import com.example.demo.entity.WorkLog;
import com.example.demo.entity.WorkLogAttachment;
import com.example.demo.entity.WorkLogStatus;
import com.example.demo.service.ApiException;
import com.example.demo.service.WorkLogReviewService;
import com.example.demo.service.WorkLogService;

/**
 * U8:T4 Unit test ส่วน API: WorkLogController, WorkLogAttachmentController, WorkLogReviewController
 * Service ถูก mock · ผู้ใช้ทดสอบ: นักศึกษา userId 3 (internship 7) · อาจารย์ userId 2
 */
@WebMvcTest({ WorkLogController.class, WorkLogAttachmentController.class, WorkLogReviewController.class })
class WorkLogControllerTest {

    private static final String BODY = """
            {"logDate":"2026-11-09","startTime":"09:00","endTime":"12:00",
             "activity":"ประชุมทีม","learning":"การวางแผนงาน","problem":null}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private WorkLogService service;

    @MockitoBean
    private WorkLogReviewService reviewService;

    private static WorkLog log(Long id, WorkLogStatus status) {
        WorkLog w = new WorkLog(7L, 3L, LocalDate.of(2026, 11, 9));
        ReflectionTestUtils.setField(w, "id", id);
        w.setStatus(status);
        return w;
    }

    private static WorkLogResponse response(Long id, String hours, WorkLogStatus status, boolean late) {
        return new WorkLogResponse(id, 7L, "สมชาย ใจดี", LocalDate.of(2026, 11, 9), new BigDecimal(hours),
                "ประชุมทีม", status, 0, late, status == WorkLogStatus.DRAFT ? null : LocalDateTime.of(2026, 11, 9, 18, 0),
                null);
    }

    @Test
    @DisplayName("UT-API-01 สร้างบันทึกสำเร็จ ได้ 201 มี id และ hours = 3.0 เรียก service.create 1 ครั้ง")
    void create() throws Exception {
        WorkLog created = log(1L, WorkLogStatus.DRAFT);
        when(service.create(eq(3L), eq(7L), any())).thenReturn(created);
        when(service.toResponse(created)).thenReturn(response(1L, "3.00", WorkLogStatus.DRAFT, false));

        mvc.perform(post("/api/internships/7/work-logs").header("X-User-Id", "3")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.hours").value(3.0))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        ArgumentCaptor<WorkLogRequest> req = ArgumentCaptor.forClass(WorkLogRequest.class);
        verify(service).create(eq(3L), eq(7L), req.capture());
        org.assertj.core.api.Assertions.assertThat(req.getValue().startTime()).isEqualTo(LocalTime.of(9, 0));
        org.assertj.core.api.Assertions.assertThat(req.getValue().activity()).isEqualTo("ประชุมทีม");
    }

    @Test
    @DisplayName("UT-API-02 ข้อมูลไม่ถูกต้อง (เช่นไม่มี activity / ชั่วโมงผิด) ได้ 400 INVALID_WORK_LOG")
    void invalid() throws Exception {
        when(service.create(eq(3L), eq(7L), any()))
                .thenThrow(new ApiException(400, "INVALID_WORK_LOG", "กรุณากรอกงานที่ทำ"));

        mvc.perform(post("/api/internships/7/work-logs").header("X-User-Id", "3")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WORK_LOG"))
                .andExpect(jsonPath("$.message").value("กรุณากรอกงานที่ทำ"));
    }

    @Test
    @DisplayName("UT-API-03 JSON รูปแบบเวลาผิด ได้ 400 VALIDATION_ERROR ไม่เรียก service")
    void badJson() throws Exception {
        mvc.perform(post("/api/internships/7/work-logs").header("X-User-Id", "3")
                .contentType(MediaType.APPLICATION_JSON).content("{\"logDate\":\"2026-11-09\",\"startTime\":\"9 โมง\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(service, never()).create(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("UT-API-04 บันทึกวันเดียวกันซ้ำ ได้ 409 DUPLICATE_LOG_DATE")
    void duplicate() throws Exception {
        when(service.create(eq(3L), eq(7L), any()))
                .thenThrow(new ApiException(409, "DUPLICATE_LOG_DATE", "มีบันทึกของวันที่นี้แล้ว"));

        mvc.perform(post("/api/internships/7/work-logs").header("X-User-Id", "3")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_LOG_DATE"));
    }

    @Test
    @DisplayName("UT-API-05 การฝึกงานไม่ใช่ IN_PROGRESS ได้ 409 INTERNSHIP_NOT_IN_PROGRESS")
    void notInProgress() throws Exception {
        when(service.create(eq(3L), eq(7L), any())).thenThrow(
                new ApiException(409, "INTERNSHIP_NOT_IN_PROGRESS", "บันทึกได้เฉพาะช่วงที่กำลังฝึกงาน"));

        mvc.perform(post("/api/internships/7/work-logs").header("X-User-Id", "3")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INTERNSHIP_NOT_IN_PROGRESS"));
    }

    @Test
    @DisplayName("UT-API-06 ไม่ส่ง X-User-Id ได้ 401 UNAUTHORIZED")
    void unauthorized() throws Exception {
        when(service.create(isNull(), eq(7L), any())).thenThrow(ApiException.unauthorized());

        mvc.perform(post("/api/internships/7/work-logs")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("UT-API-07 ดึงรายการตามช่วงวันและสถานะ ส่งตัวกรองให้ service ได้ 200 array")
    void list() throws Exception {
        List<WorkLog> logs = List.of(log(1L, WorkLogStatus.SUBMITTED), log(2L, WorkLogStatus.SUBMITTED));
        WorkLogFilter filter = new WorkLogFilter(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                WorkLogStatus.SUBMITTED);
        when(service.getLogs(3L, 7L, filter)).thenReturn(logs);
        when(service.toResponses(logs)).thenReturn(List.of(response(1L, "3.00", WorkLogStatus.SUBMITTED, false),
                response(2L, "4.00", WorkLogStatus.SUBMITTED, true)));

        mvc.perform(get("/api/internships/7/work-logs").header("X-User-Id", "3")
                .param("from", "2026-11-01").param("to", "2026-11-30").param("status", "SUBMITTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].lateSubmitted").value(true));
    }

    @Test
    @DisplayName("UT-API-08 ดึงรายการที่ไม่มีข้อมูล ได้ 200 [] · สถานะที่ไม่มีจริง ได้ 400")
    void emptyAndBadStatus() throws Exception {
        when(service.getLogs(eq(3L), eq(7L), any())).thenReturn(List.of());
        when(service.toResponses(List.of())).thenReturn(List.of());

        mvc.perform(get("/api/internships/7/work-logs").header("X-User-Id", "3").param("from", "2026-11-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/internships/7/work-logs").header("X-User-Id", "3").param("status", "DONE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("UT-API-09 ดูสรุป ได้ชั่วโมงรวม วันที่ขาด และจำนวนแต่ละสถานะ")
    void summary() throws Exception {
        when(service.summary(3L, 7L)).thenReturn(new WorkLogSummaryResponse(new BigDecimal("11.00"), 3, 5,
                List.of(LocalDate.of(2026, 11, 12)), Map.of(WorkLogStatus.SUBMITTED, 1L)));

        mvc.perform(get("/api/internships/7/work-logs/summary").header("X-User-Id", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHours").value(11.0))
                .andExpect(jsonPath("$.workingDays").value(5))
                .andExpect(jsonPath("$.missingDates[0]").value("2026-11-12"))
                .andExpect(jsonPath("$.countByStatus.SUBMITTED").value(1));
    }

    @Test
    @DisplayName("UT-API-10 ดึงตาม id ที่ไม่มี ได้ 404 · มีอยู่ได้รายละเอียดพร้อมความเห็นอาจารย์")
    void detail() throws Exception {
        when(service.getDetail(3L, 9999L)).thenThrow(ApiException.notFound("ไม่พบบันทึกการทำงาน"));
        when(service.getDetail(3L, 1L)).thenReturn(new WorkLogDetailResponse(1L, 7L, "สมชาย ใจดี",
                LocalDate.of(2026, 11, 9), new BigDecimal("3.00"), "ประชุมทีม", WorkLogStatus.NEED_REVISION, 0,
                false, LocalDateTime.of(2026, 11, 9, 18, 0), LocalDateTime.of(2026, 11, 10, 9, 0),
                LocalTime.of(9, 0), LocalTime.of(12, 0), "การวางแผนงาน", null, List.of(), "อ.ดร.วิชัย สอนดี",
                "เพิ่มรายละเอียดงาน"));

        mvc.perform(get("/api/work-logs/9999").header("X-User-Id", "3"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/api/work-logs/1").header("X-User-Id", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime", org.hamcrest.Matchers.startsWith("09:00")))
                .andExpect(jsonPath("$.reviewComment").value("เพิ่มรายละเอียดงาน"));
    }

    @Test
    @DisplayName("UT-API-11 แก้ไขบันทึกของตัวเอง ได้ 200 hours คำนวณใหม่เป็น 4.0")
    void update() throws Exception {
        WorkLog updated = log(1L, WorkLogStatus.DRAFT);
        when(service.update(eq(3L), eq(1L), any())).thenReturn(updated);
        when(service.toResponse(updated)).thenReturn(response(1L, "4.00", WorkLogStatus.DRAFT, false));

        mvc.perform(put("/api/work-logs/1").header("X-User-Id", "3")
                .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("12:00", "13:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hours").value(4.0));
    }

    @Test
    @DisplayName("UT-API-12 แก้ไข / ลบบันทึกของคนอื่น ได้ 403 · แก้บันทึกที่ส่งแล้ว ได้ 409")
    void forbiddenAndConflict() throws Exception {
        when(service.update(eq(4L), eq(1L), any())).thenThrow(ApiException.forbidden());
        org.mockito.Mockito.doThrow(ApiException.forbidden()).when(service).delete(4L, 1L);
        when(service.update(eq(3L), eq(2L), any()))
                .thenThrow(ApiException.conflict("แก้ไขได้เฉพาะบันทึกฉบับร่างหรือที่ถูกส่งกลับแก้ไข"));

        mvc.perform(put("/api/work-logs/1").header("X-User-Id", "4")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/work-logs/1").header("X-User-Id", "4"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/work-logs/2").header("X-User-Id", "3")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"));
    }

    @Test
    @DisplayName("UT-API-13 ลบบันทึกร่าง ได้ 204 เรียก service.delete 1 ครั้ง")
    void deleteDraft() throws Exception {
        mvc.perform(delete("/api/work-logs/1").header("X-User-Id", "3"))
                .andExpect(status().isNoContent());

        verify(service).delete(3L, 1L);
    }

    @Test
    @DisplayName("UT-API-14 ส่งบันทึกให้อาจารย์ ได้ 200 status = SUBMITTED")
    void submit() throws Exception {
        WorkLog submitted = log(1L, WorkLogStatus.SUBMITTED);
        when(service.submit(3L, 1L)).thenReturn(submitted);
        when(service.toResponse(submitted)).thenReturn(response(1L, "3.00", WorkLogStatus.SUBMITTED, false));

        mvc.perform(post("/api/work-logs/1/submit").header("X-User-Id", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.submittedAt").exists());
    }

    @Test
    @DisplayName("UT-API-15 แนบไฟล์ ได้ 201 · ไฟล์ไม่ผ่านเงื่อนไข ได้ 400 INVALID_ATTACHMENT")
    void attach() throws Exception {
        MockMultipartFile pdf = new MockMultipartFile("file", "proof.pdf", "application/pdf",
                "%PDF-1.4".getBytes(StandardCharsets.US_ASCII));
        WorkLogAttachment saved = new WorkLogAttachment(1L, "proof.pdf", "work-logs/1/x.pdf", "application/pdf", 8);
        ReflectionTestUtils.setField(saved, "id", 5L);
        when(service.addAttachment(eq(3L), eq(1L), any())).thenReturn(saved);
        when(service.addAttachment(eq(3L), eq(2L), any()))
                .thenThrow(new ApiException(400, "INVALID_ATTACHMENT", "แนบไฟล์ได้ไม่เกิน 5 ไฟล์ต่อบันทึก"));

        mvc.perform(multipart("/api/work-logs/1/attachments").file(pdf).header("X-User-Id", "3"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.fileName").value("proof.pdf"));
        mvc.perform(multipart("/api/work-logs/2/attachments").file(pdf).header("X-User-Id", "3"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ATTACHMENT"));
    }

    @Test
    @DisplayName("UT-API-16 ดาวน์โหลดไฟล์แนบด้วย header X-User-Id ได้ไฟล์ · ส่ง ?userId= แทน header ได้ 401")
    void download() throws Exception {
        byte[] bytes = "%PDF-1.4".getBytes(StandardCharsets.US_ASCII);
        when(service.downloadAttachment(3L, 1L, 5L))
                .thenReturn(new WorkLogService.FileContent("proof.pdf", "application/pdf", bytes));
        when(service.downloadAttachment(null, 1L, 5L)).thenThrow(ApiException.unauthorized());

        mvc.perform(get("/api/work-logs/1/attachments/5").header("X-User-Id", "3"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(bytes));
        // ไม่รับ userId ทาง URL: เปลี่ยนเลขใน URL เพื่อเปิดไฟล์ของคนอื่นไม่ได้
        mvc.perform(get("/api/work-logs/1/attachments/5").param("userId", "3"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("UT-API-17 ส่งออก CSV (อาจารย์) ได้ text/csv · นักศึกษาส่งออกไม่ได้ 403")
    void export() throws Exception {
        when(service.export(2L, 7L)).thenReturn("วันที่\r\n".getBytes(StandardCharsets.UTF_8));
        when(service.export(3L, 7L)).thenThrow(ApiException.forbidden());

        mvc.perform(get("/api/internships/7/work-logs/export").header("X-User-Id", "2"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("work-logs-7.csv")));
        mvc.perform(get("/api/internships/7/work-logs/export").header("X-User-Id", "3"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("UT-API-18 อาจารย์ดูบันทึกรอตรวจ ได้ 200 array")
    void pending() throws Exception {
        List<WorkLog> logs = List.of(log(1L, WorkLogStatus.SUBMITTED));
        when(reviewService.getPending(2L)).thenReturn(logs);
        when(service.toResponses(logs)).thenReturn(List.of(response(1L, "3.00", WorkLogStatus.SUBMITTED, false)));

        mvc.perform(get("/api/work-logs/pending-review").header("X-User-Id", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].studentName").value("สมชาย ใจดี"));
    }

    @Test
    @DisplayName("UT-API-19 ส่งกลับแก้ไขโดยไม่มีความเห็น ได้ 400 COMMENT_REQUIRED · อนุมัติได้ 200 APPROVED")
    void review() throws Exception {
        when(reviewService.review(2L, 1L, ReviewDecision.REVISE, null))
                .thenThrow(new ApiException(400, "COMMENT_REQUIRED", "กรุณากรอกความเห็นเมื่อส่งกลับแก้ไข"));
        WorkLog approved = log(1L, WorkLogStatus.APPROVED);
        when(reviewService.review(2L, 1L, ReviewDecision.APPROVE, "ดีมาก")).thenReturn(approved);
        when(service.toResponse(approved)).thenReturn(response(1L, "3.00", WorkLogStatus.APPROVED, false));

        mvc.perform(post("/api/work-logs/1/review").header("X-User-Id", "2")
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"REVISE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMENT_REQUIRED"));
        mvc.perform(post("/api/work-logs/1/review").header("X-User-Id", "2")
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\",\"comment\":\"ดีมาก\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("UT-API-20 อนุมัติหลายบันทึก ได้ approved และ skipped พร้อมเหตุผล")
    void reviewBulk() throws Exception {
        when(reviewService.reviewBulk(eq(2L), anyList(), eq("ผ่าน"))).thenReturn(new BulkWorkLogReviewResponse(
                List.of(1L), List.of(new BulkWorkLogReviewResponse.SkippedItem(2L, "บันทึกไม่ได้อยู่ในสถานะรอตรวจ"))));

        mvc.perform(post("/api/work-logs/review-bulk").header("X-User-Id", "2")
                .contentType(MediaType.APPLICATION_JSON).content("{\"workLogIds\":[1,2],\"comment\":\"ผ่าน\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved[0]").value(1))
                .andExpect(jsonPath("$.skipped[0].workLogId").value(2))
                .andExpect(jsonPath("$.skipped[0].reason").value("บันทึกไม่ได้อยู่ในสถานะรอตรวจ"));
    }

    @Test
    @DisplayName("UT-API-21 การฝึกงานของนักศึกษาที่ login (GET /api/work-logs/my-internship) ได้ 200 · ยังไม่มีได้ 404")
    void myInternship() throws Exception {
        Internship internship = new Internship(3L, LocalDate.of(2026, 11, 2), LocalDate.of(2027, 2, 19),
                InternshipStatus.IN_PROGRESS);
        ReflectionTestUtils.setField(internship, "id", 7L);
        when(service.myInternship(3L)).thenReturn(internship);
        when(service.myInternship(5L)).thenThrow(ApiException.notFound("ยังไม่มีข้อมูลการฝึกงาน"));

        mvc.perform(get("/api/work-logs/my-internship").header("X-User-Id", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.startDate").value("2026-11-02"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mvc.perform(get("/api/work-logs/my-internship").header("X-User-Id", "5"))
                .andExpect(status().isNotFound());
    }
}
