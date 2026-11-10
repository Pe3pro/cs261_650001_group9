package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.dao.ApplicationRepository;
import com.example.demo.dao.ApprovalRepository;
import com.example.demo.dao.DocumentRepository;
import com.example.demo.dao.EligibilityRecordDao;
import com.example.demo.dao.FormFieldRepository;
import com.example.demo.dao.FormResponseRepository;
import com.example.demo.dao.FormTemplateRepository;
import com.example.demo.dao.StudentRepository;
import com.example.demo.dto.FormAnswersRequest;
import com.example.demo.dto.RequestCreateRequest;
import com.example.demo.dto.RequestResponse;
import com.example.demo.entity.Application;
import com.example.demo.entity.ApplicationStatus;
import com.example.demo.entity.Document;
import com.example.demo.entity.FieldType;
import com.example.demo.entity.FormField;
import com.example.demo.entity.FormResponse;
import com.example.demo.entity.FormTemplate;
import com.example.demo.entity.Role;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;

/**
 * U3:T8 Unit test ส่วน Service (RequestService) ตาม T8.md U3-SVC-01 ถึง U3-SVC-06, U3-SVC-08 ถึง U3-SVC-10
 * จำลอง Repository, Email และ Clock ด้วย Mockito (ไม่ต่อฐานข้อมูล)
 * เวลา server ในการทดสอบ = 20 ต.ค. 2569 10:00 น. · ฟอร์ม F1 หมดเขต 30 พ.ย. 2569 23:59:59
 */
class RequestServiceTest {

    private static final ZoneId BKK = ZoneId.of("Asia/Bangkok");
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-10-20T03:00:00Z"), BKK);
    private static final LocalDateTime F1_DEADLINE = LocalDateTime.of(2026, 11, 30, 23, 59, 59);

    private ApplicationRepository applications;
    private FormTemplateRepository templates;
    private FormFieldRepository fields;
    private FormResponseRepository responses;
    private DocumentRepository documents;
    private StudentRepository students;
    private EligibilityRecordDao eligibility;
    private ApprovalRepository approvals;
    private EmailNotifier email;
    private NotificationService notifications;
    private AccessGuard guard;

    private final User userA = user(3L, "6609650001", "somchai@dome.tu.ac.th");
    private final Student studentA = student(1L, 3L, "6609650001");
    private FormTemplate f1;
    private List<FormField> f1Fields;

    @BeforeEach
    void setUp() {
        applications = mock(ApplicationRepository.class);
        templates = mock(FormTemplateRepository.class);
        fields = mock(FormFieldRepository.class);
        responses = mock(FormResponseRepository.class);
        documents = mock(DocumentRepository.class);
        students = mock(StudentRepository.class);
        eligibility = mock(EligibilityRecordDao.class);
        approvals = mock(ApprovalRepository.class);
        email = mock(EmailNotifier.class);
        notifications = mock(NotificationService.class);
        guard = mock(AccessGuard.class);

        f1 = form(10L, F1_DEADLINE);
        f1Fields = List.of(field(101L, 1, "ชื่อ-นามสกุล", FieldType.TEXT), field(102L, 2, "เบอร์โทรศัพท์", FieldType.PHONE),
                field(103L, 3, "อีเมล", FieldType.EMAIL), field(104L, 4, "ที่อยู่ปัจจุบัน", FieldType.TEXTAREA),
                field(105L, 5, "Transcript", FieldType.FILE));

        when(guard.require(3L, Role.STUDENT)).thenReturn(userA);
        when(guard.require(3L)).thenReturn(userA);
        when(students.findByUserId(3L)).thenReturn(Optional.of(studentA));
        when(templates.findById(10L)).thenReturn(Optional.of(f1));
        when(templates.findByStatus(any())).thenReturn(List.of(f1));
        when(fields.findByFormTemplateIdOrderByDisplayOrderAsc(10L)).thenReturn(f1Fields);
        when(applications.save(any(Application.class))).thenAnswer(inv -> withId(inv.getArgument(0), 500L));
        when(applications.saveAndFlush(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private RequestService service(Clock clock) {
        return new RequestService(applications, templates, fields, responses, documents, students, eligibility,
                approvals, email, notifications, guard, clock);
    }

    /** คำร้องของ นศ. A ที่กรอกครบ 4 ช่อง และแนบ PDF แล้ว */
    private Application completeApplication(ApplicationStatus status) {
        Application app = withId(new Application(1L, 10L, ApplicationStatus.DRAFT), 500L);
        app.changeStatus(status);
        when(applications.findById(500L)).thenReturn(Optional.of(app));
        when(responses.findByApplicationId(500L)).thenReturn(List.of(
                new FormResponse(500L, 101L, "สมชาย ใจดี"), new FormResponse(500L, 102L, "0812345678"),
                new FormResponse(500L, 103L, "somchai@dome.tu.ac.th"), new FormResponse(500L, 104L, "99 ม.ธ. รังสิต")));
        when(documents.findByApplicationId(500L)).thenReturn(
                List.of(new Document(500L, "transcript.pdf", "transcripts/a.pdf", 512_000L)));
        return app;
    }

    @Test
    @DisplayName("U3-SVC-01 สร้างคำร้องใหม่: สถานะ DRAFT และผูกกับแบบฟอร์มที่เผยแพร่ (F1)")
    void createNewRequest() {
        when(applications.findByStudentIdOrderByIdDesc(1L)).thenReturn(List.of());

        RequestResponse res = service(NOW).create(new RequestCreateRequest(null), 3L);

        assertThat(res.id()).isEqualTo(500L);
        assertThat(res.status()).isEqualTo(ApplicationStatus.DRAFT);
        assertThat(res.formTemplateId()).isEqualTo(10L);
        assertThat(res.studentId()).isEqualTo("6609650001");
    }

    @Test
    @DisplayName("U3-SVC-02 มีคำร้องที่ยังไม่จบ (SUBMITTED) อยู่แล้ว: ปฏิเสธการสร้างฉบับที่สอง (409)")
    void rejectSecondRequest() {
        Application existing = withId(new Application(1L, 10L, ApplicationStatus.DRAFT), 400L);
        existing.submit(LocalDateTime.of(2026, 10, 18, 9, 0));
        when(applications.findByStudentIdOrderByIdDesc(1L)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service(NOW).create(new RequestCreateRequest(10L), 3L))
                .isInstanceOf(ApiException.class)
                .hasMessage("คุณมีคำร้องประเภทนี้ที่อยู่ระหว่างดำเนินการแล้ว")
                .extracting("status", "code").containsExactly(409, "DUPLICATE_REQUEST");
        verify(applications, never()).save(any());
    }

    @Test
    @DisplayName("U3-SVC-03 ส่งคำร้องที่ขาดช่องบังคับ หรือไม่มี PDF: ปฏิเสธ และสถานะยังเป็น DRAFT")
    void rejectIncompleteSubmit() {
        Application app = completeApplication(ApplicationStatus.DRAFT);
        // ก) ขาดเบอร์โทร
        when(responses.findByApplicationId(500L)).thenReturn(List.of(
                new FormResponse(500L, 101L, "สมชาย ใจดี"), new FormResponse(500L, 103L, "somchai@dome.tu.ac.th"),
                new FormResponse(500L, 104L, "99 ม.ธ. รังสิต")));
        assertThatThrownBy(() -> service(NOW).submit(500L, 3L))
                .isInstanceOf(FieldValidationException.class)
                .satisfies(e -> assertThat(((FieldValidationException) e).getFields())
                        .extracting("field").containsExactly("field_2"));
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.DRAFT);

        // ข) กรอกครบ แต่ไม่มี PDF
        completeApplication(ApplicationStatus.DRAFT);
        when(documents.findByApplicationId(500L)).thenReturn(List.of());
        assertThatThrownBy(() -> service(NOW).submit(500L, 3L))
                .isInstanceOf(ApiException.class)
                .extracting("status", "code").containsExactly(400, "MISSING_DOCUMENT");
        assertThat(applications.findById(500L).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.DRAFT);
        verify(email, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("U3-SVC-04 ส่งคำร้องที่ครบ: DRAFT → SUBMITTED บันทึกเวลาส่ง และส่ง Email ยืนยัน")
    void submitComplete() {
        Application app = completeApplication(ApplicationStatus.DRAFT);

        RequestResponse res = service(NOW).submit(500L, 3L);

        assertThat(res.status()).isEqualTo(ApplicationStatus.SUBMITTED);
        assertThat(app.getSubmittedAt()).isEqualTo(LocalDateTime.of(2026, 10, 20, 10, 0));
        verify(applications).saveAndFlush(app);
        verify(email).send(any(), any(), any());
        verify(notifications).notifyStaffSubmitted(app, false);
    }

    @Test
    @DisplayName("U3-SVC-05 แก้ไขคำร้องสถานะ SUBMITTED หรือ APPROVED: ปฏิเสธการแก้ไข (409)")
    void rejectEditAfterSubmit() {
        FormAnswersRequest change = new FormAnswersRequest(Map.of("field_1", "สมชาย ใจดี", "field_2", "0899999999",
                "field_3", "somchai@dome.tu.ac.th", "field_4", "99 ม.ธ. รังสิต"));
        for (ApplicationStatus status : List.of(ApplicationStatus.SUBMITTED, ApplicationStatus.APPROVED)) {
            completeApplication(status);
            assertThatThrownBy(() -> service(NOW).saveAnswers(500L, change, 3L))
                    .isInstanceOf(ApiException.class)
                    .extracting("status", "code").containsExactly(409, "INVALID_STATUS");
        }
        verify(responses, never()).save(any());
    }

    @Test
    @DisplayName("U3-SVC-06 ส่งคำร้องที่แก้ไขแล้วจาก NEED_CORRECTION (CORRECTION_REQUIRED): เป็น SUBMITTED อีกครั้ง")
    void resubmitAfterCorrection() {
        Application app = completeApplication(ApplicationStatus.NEED_CORRECTION);

        RequestResponse res = service(NOW).submit(500L, 3L);

        assertThat(res.id()).isEqualTo(500L);
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
        verify(notifications).notifyStaffSubmitted(app, true); // U5: แจ้งเจ้าหน้าที่ว่าเป็นฉบับแก้
    }

    @Test
    @DisplayName("U5-RQ01 แบบฟอร์มมีช่องไฟล์บังคับ 2 ช่อง แนบแค่ Transcript: ส่งไม่ได้ บอกชื่อช่องที่ขาด")
    void rejectMissingSecondFile() {
        when(fields.findByFormTemplateIdOrderByDisplayOrderAsc(10L)).thenReturn(withExtraFile());
        Application app = completeApplication(ApplicationStatus.DRAFT); // แนบ Transcript แบบไม่ระบุช่อง

        assertThatThrownBy(() -> service(NOW).submit(500L, 3L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("หนังสือรับรอง")
                .extracting("status", "code").containsExactly(400, "MISSING_DOCUMENT");
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.DRAFT);
        verify(notifications, never()).notifyStaffSubmitted(any(), anyBoolean());
    }

    @Test
    @DisplayName("U5-RQ02 แนบไฟล์ครบทุกช่องไฟล์บังคับ (Transcript ไม่ระบุช่อง + ช่องที่ 2 ระบุ fieldId): ส่งได้")
    void submitWithAllFiles() {
        when(fields.findByFormTemplateIdOrderByDisplayOrderAsc(10L)).thenReturn(withExtraFile());
        Application app = completeApplication(ApplicationStatus.DRAFT);
        when(documents.findByApplicationId(500L)).thenReturn(List.of(
                new Document(500L, "transcript.pdf", "k1", 1000L),
                new Document(500L, "letter.pdf", "k2", 1000L, 106L)));

        service(NOW).submit(500L, 3L);

        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
    }

    @Test
    @DisplayName("U5-RQ03 แนบไฟล์ให้ช่องที่ไม่ใช่ช่องไฟล์ / ไม่อยู่ในแบบฟอร์ม: ปฏิเสธ 400")
    void rejectWrongFileField() {
        Application app = completeApplication(ApplicationStatus.DRAFT);
        when(fields.findById(101L)).thenReturn(Optional.of(f1Fields.get(0)));
        when(fields.findById(105L)).thenReturn(Optional.of(f1Fields.get(4)));
        when(fields.findById(999L)).thenReturn(Optional.of(withId(new FormField(77L, "อื่น", FieldType.FILE, true, 1), 999L)));

        assertThatThrownBy(() -> service(NOW).fileField(app, 101L))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(400);
        assertThatThrownBy(() -> service(NOW).fileField(app, 999L))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(400);
        assertThat(service(NOW).fileField(app, 105L)).isSameAs(f1Fields.get(4));
    }

    /** ฟอร์ม F1 + ช่องไฟล์บังคับช่องที่ 2 (id 106) */
    private List<FormField> withExtraFile() {
        List<FormField> list = new ArrayList<>(f1Fields);
        list.add(field(106L, 6, "หนังสือรับรองจากบริษัท", FieldType.FILE));
        return list;
    }

    @Test
    @DisplayName("U3-SVC-08 เปลี่ยนสถานะ DRAFT → APPROVED โดยตรง: ปฏิเสธ สถานะยังเป็น DRAFT")
    void rejectDraftToApproved() {
        Application app = withId(new Application(1L, 10L, ApplicationStatus.DRAFT), 500L);

        assertThatThrownBy(() -> service(NOW).moveTo(app, ApplicationStatus.APPROVED))
                .isInstanceOf(ApiException.class)
                .extracting("status", "code").containsExactly(409, "INVALID_STATUS");
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.DRAFT);
    }

    @Test
    @DisplayName("U3-SVC-09 ส่งหลังหมดเขต (Clock = 1 ธ.ค. 2569 00:00:00): ปฏิเสธ DEADLINE_PASSED สถานะยังเป็น DRAFT")
    void rejectAfterDeadline() {
        Application app = completeApplication(ApplicationStatus.DRAFT);
        Clock afterDeadline = Clock.fixed(Instant.parse("2026-11-30T17:00:00Z"), BKK);

        assertThatThrownBy(() -> service(afterDeadline).submit(500L, 3L))
                .isInstanceOf(ApiException.class)
                .hasMessage("เกินระยะเวลาที่กำหนด ไม่สามารถยื่นคำร้องได้")
                .extracting("status", "code").containsExactly(409, "DEADLINE_PASSED");
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.DRAFT);
        assertThat(app.getSubmittedAt()).isNull();
        verify(email, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("U3-SVC-10 มีคำร้องเดิมสถานะ REJECTED แล้วสร้างใหม่: สร้างได้ สถานะ DRAFT")
    void createAfterRejected() {
        Application rejected = withId(new Application(1L, 10L, ApplicationStatus.DRAFT), 300L);
        rejected.changeStatus(ApplicationStatus.REJECTED);
        when(applications.findByStudentIdOrderByIdDesc(1L)).thenReturn(new ArrayList<>(List.of(rejected)));

        RequestResponse res = service(NOW).create(new RequestCreateRequest(10L), 3L);

        assertThat(res.status()).isEqualTo(ApplicationStatus.DRAFT);
        assertThat(res.id()).isEqualTo(500L);
    }

    // ---------- ตัวช่วยสร้างข้อมูล ----------

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private static User user(Long id, String username, String mail) {
        return withId(new User(username, "สมชาย ใจดี", mail, Role.STUDENT), id);
    }

    private static Student student(Long id, Long userId, String code) {
        return withId(new Student(userId, code, "วิทยาศาสตร์และเทคโนโลยี", "วิทยาการคอมพิวเตอร์", 3.10), id);
    }

    private static FormTemplate form(Long id, LocalDateTime deadline) {
        FormTemplate f = withId(new FormTemplate("คำร้องขอฝึกสหกิจศึกษา 2/2569", 1, 1L), id);
        f.setDeadline(deadline);
        f.publish(LocalDateTime.of(2026, 10, 1, 9, 0));
        return f;
    }

    private static FormField field(Long id, int order, String label, FieldType type) {
        return withId(new FormField(10L, label, type, true, order), id);
    }
}
