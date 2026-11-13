package com.example.demo.controller;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.demo.dto.DocumentResponse;
import com.example.demo.dto.FieldErrorResponse.FieldError;
import com.example.demo.dto.RequestResponse;
import com.example.demo.entity.ApplicationStatus;
import com.example.demo.entity.DocumentStatus;
import com.example.demo.service.ApiException;
import com.example.demo.service.DocumentService;
import com.example.demo.service.FieldValidationException;
import com.example.demo.service.RequestService;

/**
 * U3:T8 Unit test ส่วน API (RequestController) ตาม T8.md U3-API-01 ถึง U3-API-13
 * จำลอง RequestService และ DocumentService ด้วย Mockito · ผู้เรียกระบุด้วย header X-User-Id
 * userId ตามข้อมูลตั้งต้น: นศ. A = 3, นศ. B = 4, นศ. X (ยังไม่ผ่านเกณฑ์) = 9
 */
@WebMvcTest(RequestController.class)
class RequestControllerTest {

    private static final String A = "3";
    private static final String B = "4";
    private static final String X = "9";
    private static final Long F1 = 10L;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RequestService requestService;

    @MockitoBean
    private DocumentService documentService;

    private static RequestResponse request(Long id, String studentId, Long formId, ApplicationStatus status) {
        return new RequestResponse(id, studentId, formId, "คำร้องขอฝึกสหกิจศึกษา 2/2569", status,
                LocalDateTime.of(2026, 10, 19, 10, 0), status == ApplicationStatus.DRAFT ? null
                        : LocalDateTime.of(2026, 10, 19, 10, 5),
                LocalDateTime.of(2026, 10, 19, 10, 5), LocalDateTime.of(2026, 11, 30, 23, 59, 59),
                Map.of("field_1", "A arizona"), List.of(), null);
    }

    @Test
    @DisplayName("U3-API-01 นศ. A (ผ่านเกณฑ์) สร้างคำร้อง ได้ 201 พร้อม id และสถานะ DRAFT")
    void createByEligibleStudent() throws Exception {
        when(requestService.create(any(), eq(3L))).thenReturn(request(100L, "6609650001", F1, ApplicationStatus.DRAFT));

        mvc.perform(post("/api/requests").header("X-User-Id", A)
                .contentType(MediaType.APPLICATION_JSON).content("{\"formTemplateId\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @DisplayName("U3-API-02 นศ. X (ยังไม่ผ่านเกณฑ์) สร้างคำร้อง ได้ 403")
    void createByNotEligibleStudent() throws Exception {
        when(requestService.create(any(), eq(9L))).thenThrow(
                new ApiException(403, "NOT_ELIGIBLE", "คุณยังไม่ผ่านการตรวจสอบคุณสมบัติ ไม่สามารถยื่นคำร้องได้"));

        mvc.perform(post("/api/requests").header("X-User-Id", X)
                .contentType(MediaType.APPLICATION_JSON).content("{\"formTemplateId\":10}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ELIGIBLE"));
    }

    @Test
    @DisplayName("U3-API-03 PUT form ข้อมูลครบและถูกรูปแบบ ได้ 200")
    void saveValidForm() throws Exception {
        when(requestService.saveAnswers(eq(100L), any(), eq(3L)))
                .thenReturn(request(100L, "6609650001", F1, ApplicationStatus.DRAFT));

        mvc.perform(put("/api/requests/100/form").header("X-User-Id", A)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":{\"field_1\":\"A arizona\",\"field_2\":\"0812345678\","
                        + "\"field_3\":\"arizona@dome.tu.ac.th\",\"field_4\":\"99 ม.ธ. รังสิต\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers.field_1").value("A arizona"));
    }

    @Test
    @DisplayName("U3-API-04 PUT form ไม่มีเบอร์โทร / อีเมล arizona@ ได้ 400 พร้อมรายชื่อช่องที่ผิด")
    void saveInvalidForm() throws Exception {
        when(requestService.saveAnswers(eq(100L), any(), eq(3L))).thenThrow(new FieldValidationException(List.of(
                new FieldError("field_2", "เบอร์โทรศัพท์", "กรุณากรอกเบอร์โทรศัพท์"),
                new FieldError("field_3", "อีเมล", "รูปแบบอีเมลไม่ถูกต้อง"))));

        mvc.perform(put("/api/requests/100/form").header("X-User-Id", A)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":{\"field_1\":\"A arizona\",\"field_3\":\"arizona@\",\"field_4\":\"99\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields[*].field", hasItems("field_2", "field_3")));
    }

    @Test
    @DisplayName("U3-API-05 อัปโหลด PDF 500 KB ได้ 201 พร้อม document id")
    void uploadPdf() throws Exception {
        MockMultipartFile pdf = new MockMultipartFile("file", "transcript.pdf", "application/pdf",
                new byte[500 * 1024]);
        when(documentService.upload(eq(100L), any(), eq(3L)))
                .thenReturn(new DocumentResponse(7L, "transcript.pdf", DocumentStatus.UPLOADED));

        mvc.perform(multipart("/api/requests/100/documents").file(pdf).header("X-User-Id", A))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.fileName").value("transcript.pdf"));
    }

    @Test
    @DisplayName("U3-API-06 อัปโหลด photo.jpg หรือ PDF 12 MB ได้ 400")
    void uploadInvalidFile() throws Exception {
        MockMultipartFile jpg = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[1024]);
        when(documentService.upload(eq(100L), any(), eq(3L)))
                .thenThrow(new ApiException(400, "INVALID_FILE", "Transcript ต้องเป็นไฟล์ PDF เท่านั้น"))
                .thenThrow(new ApiException(400, "FILE_TOO_LARGE", "ไฟล์ต้องมีขนาดไม่เกิน 10 MB"));

        mvc.perform(multipart("/api/requests/100/documents").file(jpg).header("X-User-Id", A))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE"));

        MockMultipartFile big = new MockMultipartFile("file", "transcript.pdf", "application/pdf",
                new byte[12 * 1024 * 1024]);
        mvc.perform(multipart("/api/requests/100/documents").file(big).header("X-User-Id", A))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    @DisplayName("U3-API-07 submit คำร้องที่ครบถ้วน ได้ 200 สถานะ SUBMITTED")
    void submitComplete() throws Exception {
        when(requestService.submit(100L, 3L)).thenReturn(request(100L, "6609650001", F1, ApplicationStatus.SUBMITTED));

        mvc.perform(post("/api/requests/100/submit").header("X-User-Id", A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.submittedAt").exists());
    }

    @Test
    @DisplayName("U3-API-08 นศ. A เปิดคำร้องของ นศ. B ได้ 403")
    void openOthersRequest() throws Exception {
        when(requestService.get(200L, 3L)).thenThrow(ApiException.forbidden());

        mvc.perform(get("/api/requests/200").header("X-User-Id", A))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("U3-API-09 นศ. B มีคำร้องที่ยังไม่จบอยู่แล้ว สร้างใหม่ ได้ 409")
    void createDuplicate() throws Exception {
        when(requestService.create(any(), eq(4L))).thenThrow(
                new ApiException(409, "DUPLICATE_REQUEST", "คุณมีคำร้องประเภทนี้ที่อยู่ระหว่างดำเนินการแล้ว"));

        mvc.perform(post("/api/requests").header("X-User-Id", B)
                .contentType(MediaType.APPLICATION_JSON).content("{\"formTemplateId\":10}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_REQUEST"))
                .andExpect(jsonPath("$.message").value("คุณมีคำร้องประเภทนี้ที่อยู่ระหว่างดำเนินการแล้ว"));
    }

    @Test
    @DisplayName("U3-API-10 submit คำร้องของฟอร์ม F2 ที่หมดเขตแล้ว ได้ 409 DEADLINE_PASSED")
    void submitAfterDeadline() throws Exception {
        when(requestService.submit(101L, 3L)).thenThrow(
                new ApiException(409, "DEADLINE_PASSED", "เกินระยะเวลาที่กำหนด ไม่สามารถยื่นคำร้องได้"));

        mvc.perform(post("/api/requests/101/submit").header("X-User-Id", A))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEADLINE_PASSED"));
    }

    @Test
    @DisplayName("U3-API-11 submit id ที่ไม่มีอยู่ (999999) ได้ 404")
    void submitUnknownId() throws Exception {
        when(requestService.submit(999999L, 3L)).thenThrow(ApiException.notFound("ไม่พบคำร้อง"));

        mvc.perform(post("/api/requests/999999/submit").header("X-User-Id", A))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("U3-API-12 นศ. B ดูคำร้องของตัวเอง GET /api/requests/me ได้ 200 สถานะ SUBMITTED")
    void getMine() throws Exception {
        when(requestService.getMine(4L)).thenReturn(
                List.of(request(200L, "6609650002", F1, ApplicationStatus.SUBMITTED)));

        mvc.perform(get("/api/requests/me").header("X-User-Id", B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(200))
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"));
    }

    @Test
    @DisplayName("U3-API-13 สร้างคำร้องโดยไม่มี token (ไม่ส่ง X-User-Id) ได้ 401")
    void createWithoutLogin() throws Exception {
        when(requestService.create(any(), isNull())).thenThrow(ApiException.unauthorized());

        mvc.perform(post("/api/requests")
                .contentType(MediaType.APPLICATION_JSON).content("{\"formTemplateId\":10}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
