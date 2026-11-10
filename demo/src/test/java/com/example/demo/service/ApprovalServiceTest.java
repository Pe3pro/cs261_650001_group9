package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.dao.AnswerSnapshotRepository;
import com.example.demo.dao.ApplicationRepository;
import com.example.demo.dao.ApprovalRepository;
import com.example.demo.dao.FormFieldRepository;
import com.example.demo.dao.FormResponseRepository;
import com.example.demo.dao.StudentRepository;
import com.example.demo.dto.ApprovalRequest;
import com.example.demo.dto.ApprovalResponse;
import com.example.demo.entity.AnswerSnapshot;
import com.example.demo.entity.Application;
import com.example.demo.entity.ApplicationStatus;
import com.example.demo.entity.Approval;
import com.example.demo.entity.ApprovalDecision;
import com.example.demo.entity.ApprovalLevel;
import com.example.demo.entity.FieldType;
import com.example.demo.entity.FormField;
import com.example.demo.entity.FormResponse;
import com.example.demo.entity.Role;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;

/** U5:T8 Unit test logic การพิจารณาคำร้อง (ApprovalService) โดยไม่ต่อฐานข้อมูล */
class ApprovalServiceTest {

    private static final ZoneId BKK = ZoneId.of("Asia/Bangkok");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-16T04:00:00Z"), BKK); // 11:00 น.

    private ApplicationRepository applications;
    private ApprovalRepository approvals;
    private StudentRepository students;
    private NotificationService notifications;
    private FormFieldRepository fields;
    private FormResponseRepository responses;
    private AnswerSnapshotRepository snapshots;
    private AccessGuard guard;
    private ApprovalService service;

    private final User staff = user(1L, "สมศรี ทำงานดี", Role.STAFF);
    private final User advisor = user(2L, "อ.ดร.วิชัย สอนดี", Role.ADVISOR);
    private final User otherAdvisor = user(4L, "อ.ดร.สมปอง คนอื่น", Role.ADVISOR);

    @BeforeEach
    void setUp() {
        applications = mock(ApplicationRepository.class);
        approvals = mock(ApprovalRepository.class);
        students = mock(StudentRepository.class);
        notifications = mock(NotificationService.class);
        fields = mock(FormFieldRepository.class);
        responses = mock(FormResponseRepository.class);
        snapshots = mock(AnswerSnapshotRepository.class);
        guard = mock(AccessGuard.class);
        service = new ApprovalService(applications, approvals, students, notifications, fields, responses, snapshots,
                guard, CLOCK);
        when(guard.require(1L, Role.STAFF, Role.ADVISOR)).thenReturn(staff);
        when(guard.require(2L, Role.STAFF, Role.ADVISOR)).thenReturn(advisor);
        when(guard.require(4L, Role.STAFF, Role.ADVISOR)).thenReturn(otherAdvisor);
        // นักศึกษา id 20 (เจ้าของคำร้อง) มี advisor id 2 เป็นอาจารย์ที่ปรึกษา
        when(students.findById(20L)).thenReturn(Optional.of(student(20L, 2L)));
        when(approvals.save(any(Approval.class))).thenAnswer(inv -> {
            Approval a = inv.getArgument(0);
            ReflectionTestUtils.setField(a, "id", 900L);
            return a;
        });
    }

    private static User user(Long id, String name, Role role) {
        User u = new User("u" + id, name, null, role);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private static Student student(Long id, Long advisorId) {
        Student s = new Student(100L, "6609650001", null, null, null);
        ReflectionTestUtils.setField(s, "id", id);
        s.setAdvisorId(advisorId);
        return s;
    }

    private Application application(ApplicationStatus status) {
        Application a = new Application(20L, 10L, status);
        ReflectionTestUtils.setField(a, "id", 50L);
        when(applications.findById(50L)).thenReturn(Optional.of(a));
        return a;
    }

    @Test
    @DisplayName("SV-AP01 เจ้าหน้าที่อนุมัติคำร้อง SUBMITTED → UNDER_REVIEW และแจ้งเตือน")
    void staffApprove() {
        Application app = application(ApplicationStatus.SUBMITTED);

        ApprovalResponse res = service.decide(50L, 1L, new ApprovalRequest(ApprovalDecision.APPROVE, null));

        assertThat(res.applicationStatus()).isEqualTo(ApplicationStatus.UNDER_REVIEW);
        assertThat(res.approvalLevel()).isEqualTo(ApprovalLevel.STAFF);
        assertThat(res.approverName()).isEqualTo("สมศรี ทำงานดี");
        assertThat(res.decidedAt()).isEqualTo(LocalDateTime.of(2026, 10, 16, 11, 0));
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.UNDER_REVIEW);
        verify(applications).save(app);
        verify(notifications).notifyAfterDecision(app, ApprovalLevel.STAFF, ApprovalDecision.APPROVE, null);
    }

    @Test
    @DisplayName("SV-AP02 อาจารย์อนุมัติคำร้อง UNDER_REVIEW → APPROVED")
    void advisorApprove() {
        application(ApplicationStatus.UNDER_REVIEW);

        ApprovalResponse res = service.decide(50L, 2L, new ApprovalRequest(ApprovalDecision.APPROVE, "ดีมาก"));

        assertThat(res.applicationStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(res.approvalLevel()).isEqualTo(ApprovalLevel.ADVISOR);
        assertThat(res.comment()).isEqualTo("ดีมาก");
    }

    @Test
    @DisplayName("SV-AP03 ส่งกลับแก้ไขพร้อมเหตุผล → NEED_CORRECTION (ตัดช่องว่างหน้าหลัง)")
    void returnWithReason() {
        application(ApplicationStatus.SUBMITTED);

        ApprovalResponse res = service.decide(50L, 1L,
                new ApprovalRequest(ApprovalDecision.RETURN, "  Transcript ไม่ชัด  "));

        assertThat(res.applicationStatus()).isEqualTo(ApplicationStatus.NEED_CORRECTION);
        assertThat(res.comment()).isEqualTo("Transcript ไม่ชัด");
    }

    @Test
    @DisplayName("SV-AP04 อาจารย์ปฏิเสธพร้อมเหตุผล → REJECTED")
    void advisorReject() {
        application(ApplicationStatus.UNDER_REVIEW);

        ApprovalResponse res = service.decide(50L, 2L,
                new ApprovalRequest(ApprovalDecision.REJECT, "หน่วยกิตไม่ครบ"));

        assertThat(res.applicationStatus()).isEqualTo(ApplicationStatus.REJECTED);
    }

    @Test
    @DisplayName("SV-AP05 ส่งกลับแก้ไขโดยเหตุผลเป็นช่องว่าง ได้ 400 และไม่บันทึก")
    void returnWithoutReason() {
        application(ApplicationStatus.SUBMITTED);

        assertThatThrownBy(() -> service.decide(50L, 1L, new ApprovalRequest(ApprovalDecision.RETURN, "   ")))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(400);
        verify(approvals, never()).save(any());
    }

    @Test
    @DisplayName("SV-AP06 ไม่เลือกผลการพิจารณา ได้ 400")
    void noDecision() {
        assertThatThrownBy(() -> service.decide(50L, 1L, new ApprovalRequest(null, null)))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(400);
    }

    @Test
    @DisplayName("SV-AP07 อาจารย์ตัดสินคำร้องที่ยัง SUBMITTED ได้ 409 และสถานะไม่เปลี่ยน")
    void advisorTooEarly() {
        Application app = application(ApplicationStatus.SUBMITTED);

        assertThatThrownBy(() -> service.decide(50L, 2L, new ApprovalRequest(ApprovalDecision.APPROVE, null)))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(409);
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
        verify(approvals, never()).save(any());
    }

    @Test
    @DisplayName("SV-AP08 เจ้าหน้าที่ตัดสินคำร้องที่ตัดสินไปแล้ว (UNDER_REVIEW) ได้ 409 กันการกดซ้ำ")
    void staffDecidesTwice() {
        application(ApplicationStatus.UNDER_REVIEW);

        assertThatThrownBy(() -> service.decide(50L, 1L, new ApprovalRequest(ApprovalDecision.APPROVE, null)))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(409);
    }

    @Test
    @DisplayName("SV-AP09 คำร้องที่ไม่มีอยู่ ได้ 404")
    void notFound() {
        when(applications.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.decide(999L, 1L, new ApprovalRequest(ApprovalDecision.APPROVE, null)))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(404);
    }

    @Test
    @DisplayName("SV-AP10 นักศึกษาเรียกใช้ ได้ 403 จาก AccessGuard")
    void studentForbidden() {
        when(guard.require(3L, Role.STAFF, Role.ADVISOR)).thenThrow(ApiException.forbidden());

        assertThatThrownBy(() -> service.decide(50L, 3L, new ApprovalRequest(ApprovalDecision.APPROVE, null)))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(403);
    }

    @Test
    @DisplayName("SV-AP11 อาจารย์ที่ไม่ใช่อาจารย์ที่ปรึกษาของนักศึกษา ตัดสินไม่ได้ ได้ 403 และไม่บันทึก")
    void otherAdvisorForbidden() {
        Application app = application(ApplicationStatus.UNDER_REVIEW);

        assertThatThrownBy(() -> service.decide(50L, 4L, new ApprovalRequest(ApprovalDecision.APPROVE, null)))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(403);
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.UNDER_REVIEW);
        verify(approvals, never()).save(any());
        verify(notifications, never()).notifyAfterDecision(any(), any(), any(), any());
    }

    @Test
    @DisplayName("SV-AP12 นักศึกษายังไม่มีอาจารย์ที่ปรึกษา อาจารย์คนใดก็ตัดสินไม่ได้ ได้ 403")
    void studentWithoutAdvisor() {
        when(students.findById(20L)).thenReturn(Optional.of(student(20L, null)));
        application(ApplicationStatus.UNDER_REVIEW);

        assertThatThrownBy(() -> service.decide(50L, 2L, new ApprovalRequest(ApprovalDecision.APPROVE, null)))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(403);
        verify(approvals, never()).save(any());
    }

    @Test
    @DisplayName("SV-AP13 เจ้าหน้าที่ตัดสินได้ทุกคำร้อง ไม่ขึ้นกับอาจารย์ที่ปรึกษา")
    void staffNotLimitedByAdvisor() {
        when(students.findById(20L)).thenReturn(Optional.of(student(20L, null)));
        application(ApplicationStatus.SUBMITTED);

        ApprovalResponse res = service.decide(50L, 1L, new ApprovalRequest(ApprovalDecision.APPROVE, null));

        assertThat(res.applicationStatus()).isEqualTo(ApplicationStatus.UNDER_REVIEW);
    }

    @Test
    @DisplayName("SV-AP14 ตีกลับพร้อมเลือกช่องที่ต้องแก้: เหตุผลต่อท้ายด้วยชื่อช่อง (เรียงตามลำดับในแบบฟอร์ม)")
    void returnWithFieldsToFix() {
        application(ApplicationStatus.SUBMITTED);
        when(fields.findByFormTemplateIdOrderByDisplayOrderAsc(10L)).thenReturn(List.of(
                field(101L, "ชื่อ-นามสกุล", 1), field(102L, "เบอร์โทรศัพท์", 2), field(105L, "Transcript", 5)));

        ApprovalResponse res = service.decide(50L, 1L,
                new ApprovalRequest(ApprovalDecision.RETURN, "ข้อมูลไม่ครบ", List.of(105L, 102L, 102L)));

        assertThat(res.comment()).isEqualTo("ข้อมูลไม่ครบ\nช่องที่ต้องแก้: เบอร์โทรศัพท์, Transcript");
        verify(notifications).notifyAfterDecision(any(), any(), any(),
                eq("ข้อมูลไม่ครบ\nช่องที่ต้องแก้: เบอร์โทรศัพท์, Transcript"));
    }

    @Test
    @DisplayName("SV-AP15 เลือกช่องที่ไม่อยู่ในแบบฟอร์มของคำร้อง: 400 และไม่บันทึกผล")
    void returnWithUnknownField() {
        Application app = application(ApplicationStatus.SUBMITTED);
        when(fields.findByFormTemplateIdOrderByDisplayOrderAsc(10L)).thenReturn(List.of(field(101L, "ชื่อ", 1)));

        assertThatThrownBy(() -> service.decide(50L, 1L,
                new ApprovalRequest(ApprovalDecision.RETURN, "แก้", List.of(999L))))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(400);
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
        verify(approvals, never()).save(any());
    }

    @Test
    @DisplayName("SV-AP16 ตีกลับ: เก็บสำเนาคำตอบทุกช่องไว้เทียบกับฉบับแก้ · อนุมัติ: ไม่เก็บ")
    void snapshotOnReturnOnly() {
        application(ApplicationStatus.SUBMITTED);
        when(responses.findByApplicationId(50L)).thenReturn(List.of(
                new FormResponse(50L, 101L, "สมชาย"), new FormResponse(50L, 102L, "0812345678")));

        service.decide(50L, 1L, new ApprovalRequest(ApprovalDecision.RETURN, "แก้เบอร์"));

        verify(snapshots, times(2)).save(any(AnswerSnapshot.class));

        application(ApplicationStatus.SUBMITTED);
        clearInvocations(snapshots);
        service.decide(50L, 1L, new ApprovalRequest(ApprovalDecision.APPROVE, null));
        verify(snapshots, never()).save(any());
    }

    private static FormField field(Long id, String label, int order) {
        FormField f = new FormField(10L, label, FieldType.TEXT, true, order);
        ReflectionTestUtils.setField(f, "id", id);
        return f;
    }
}
