package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.dao.InternshipDao;
import com.example.demo.dao.UserRepository;
import com.example.demo.dao.WorkLogAttachmentDao;
import com.example.demo.dao.WorkLogDao;
import com.example.demo.dto.WorkLogRequest;
import com.example.demo.dto.WorkLogSummaryResponse;
import com.example.demo.entity.Internship;
import com.example.demo.entity.InternshipStatus;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.entity.WorkLog;
import com.example.demo.entity.WorkLogAttachment;
import com.example.demo.entity.WorkLogStatus;

/**
 * U8:T4 Unit test ส่วน Service: WorkLogService (กฎของบันทึกการทำงาน) · DAO ถูก mock
 * วันนี้ = อังคาร 2026-11-10 · internship 7 ของนักศึกษา A (userId 3) เริ่ม 2026-11-02 IN_PROGRESS
 * อาจารย์ที่ปรึกษาของ A = userId 2 · อาจารย์อื่น = 5 · นักศึกษา B = 4 · เจ้าหน้าที่ = 1
 */
class WorkLogServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T03:00:00Z"), ZoneId.of("Asia/Bangkok"));

    private WorkLogDao workLogDao;
    private WorkLogAttachmentDao attachmentDao;
    private InternshipDao internshipDao;
    private UserRepository userDao;
    private FileStorage fileStorage;
    private NotificationService notificationService;
    private WorkLogService service;
    private Internship internship;

    @BeforeEach
    void setUp() {
        workLogDao = mock(WorkLogDao.class);
        attachmentDao = mock(WorkLogAttachmentDao.class);
        internshipDao = mock(InternshipDao.class);
        userDao = mock(UserRepository.class);
        fileStorage = mock(FileStorage.class);
        notificationService = mock(NotificationService.class);
        service = new WorkLogService(workLogDao, attachmentDao, internshipDao, userDao, fileStorage,
                notificationService, new AccessGuard(userDao), CLOCK);

        user(1L, Role.STAFF, null);
        user(2L, Role.ADVISOR, null);
        user(3L, Role.STUDENT, 2L);
        user(4L, Role.STUDENT, 5L);
        user(5L, Role.ADVISOR, null);

        internship = new Internship(3L, LocalDate.of(2026, 11, 2), LocalDate.of(2027, 2, 19),
                InternshipStatus.IN_PROGRESS);
        ReflectionTestUtils.setField(internship, "id", 7L);
        when(internshipDao.findById(7L)).thenReturn(Optional.of(internship));
        when(workLogDao.save(any(WorkLog.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void user(Long id, Role role, Long advisorId) {
        User u = new User("u" + id, "ผู้ใช้ " + id, null, role);
        ReflectionTestUtils.setField(u, "id", id);
        u.setAdvisorId(advisorId);
        when(userDao.findById(id)).thenReturn(Optional.of(u));
    }

    private static WorkLogRequest req(String date, String start, String end) {
        return new WorkLogRequest(LocalDate.parse(date), LocalTime.parse(start), LocalTime.parse(end), "ประชุมทีม",
                "การวางแผนงาน", null);
    }

    private WorkLog existing(Long id, String date, WorkLogStatus status) {
        WorkLog w = new WorkLog(7L, 3L, LocalDate.parse(date));
        ReflectionTestUtils.setField(w, "id", id);
        w.setStartTime(LocalTime.of(9, 0));
        w.setEndTime(LocalTime.of(12, 0));
        w.setHours(new BigDecimal("3.00"));
        w.setActivity("งาน");
        w.setLearning("เรียนรู้");
        w.setStatus(status);
        when(workLogDao.findById(id)).thenReturn(Optional.of(w));
        return w;
    }

    private static void assertApiError(Throwable e, int status, String code) {
        assertThat(e).isInstanceOf(ApiException.class);
        assertThat(((ApiException) e).getStatus()).isEqualTo(status);
        assertThat(((ApiException) e).getCode()).isEqualTo(code);
    }

    @Test
    @DisplayName("UT-SV-01 calcHours 09:00–17:30 = 8.50 · เวลาสิ้นสุดไม่หลังเวลาเริ่ม = 400 INVALID_WORK_LOG")
    void calcHours() {
        assertThat(WorkLogService.calcHours(LocalTime.of(9, 0), LocalTime.of(17, 30))).isEqualByComparingTo("8.50");
        assertThat(WorkLogService.calcHours(LocalTime.of(9, 0), LocalTime.of(9, 20))).isEqualByComparingTo("0.33");
        assertThatThrownBy(() -> WorkLogService.calcHours(LocalTime.of(10, 0), LocalTime.of(10, 0)))
                .satisfies(e -> assertApiError(e, 400, "INVALID_WORK_LOG"));
    }

    @Test
    @DisplayName("UT-SV-02 สร้างบันทึกถูกต้อง ได้ DRAFT ชั่วโมง 3.00 ของนักศึกษาเจ้าของ")
    void create() {
        WorkLog log = service.create(3L, 7L, req("2026-11-09", "09:00", "12:00"));

        assertThat(log.getStatus()).isEqualTo(WorkLogStatus.DRAFT);
        assertThat(log.getHours()).isEqualByComparingTo("3.00");
        assertThat(log.getStudentId()).isEqualTo(3L);
        assertThat(log.getInternshipId()).isEqualTo(7L);
        verify(workLogDao).existsByInternshipIdAndLogDate(7L, LocalDate.of(2026, 11, 9));
        verify(workLogDao).save(log);
    }

    @Test
    @DisplayName("UT-SV-03 วันที่ในอนาคต / ก่อนวันเริ่มฝึก ได้ 400 และไม่บันทึก")
    void invalidDates() {
        assertThatThrownBy(() -> service.create(3L, 7L, req("2026-11-11", "09:00", "12:00")))
                .satisfies(e -> assertApiError(e, 400, "INVALID_WORK_LOG"))
                .hasMessage("ไม่สามารถบันทึกวันที่ในอนาคตได้");
        assertThatThrownBy(() -> service.create(3L, 7L, req("2026-10-30", "09:00", "12:00")))
                .hasMessage("วันที่ต้องอยู่ในช่วงฝึกงาน");
        verify(workLogDao, never()).save(any());
    }

    @Test
    @DisplayName("UT-SV-04 ชั่วโมงน้อยกว่า 0.5 หรือเกิน 12 · ไม่มีงานที่ทำ ได้ 400")
    void invalidHoursAndText() {
        assertThatThrownBy(() -> service.create(3L, 7L, req("2026-11-09", "09:00", "09:15")))
                .hasMessage("ชั่วโมงทำงานต้องอยู่ระหว่าง 0.5 ถึง 12 ชั่วโมง");
        assertThatThrownBy(() -> service.create(3L, 7L, req("2026-11-09", "06:00", "18:30")))
                .hasMessage("ชั่วโมงทำงานต้องอยู่ระหว่าง 0.5 ถึง 12 ชั่วโมง");
        WorkLogRequest noActivity = new WorkLogRequest(LocalDate.of(2026, 11, 9), LocalTime.of(9, 0),
                LocalTime.of(12, 0), "  ", "เรียนรู้", null);
        assertThatThrownBy(() -> service.create(3L, 7L, noActivity)).hasMessage("กรุณากรอกงานที่ทำ");
        verify(workLogDao, never()).save(any());
    }

    @Test
    @DisplayName("UT-SV-05 มีบันทึกวันนี้แล้ว ได้ 409 DUPLICATE_LOG_DATE")
    void duplicate() {
        when(workLogDao.existsByInternshipIdAndLogDate(7L, LocalDate.of(2026, 11, 9))).thenReturn(true);

        assertThatThrownBy(() -> service.create(3L, 7L, req("2026-11-09", "09:00", "12:00")))
                .satisfies(e -> assertApiError(e, 409, "DUPLICATE_LOG_DATE"));
        verify(workLogDao, never()).save(any());
    }

    @Test
    @DisplayName("UT-SV-06 ไม่ใช่การฝึกงานของตนเอง 403 · อาจารย์สร้างบันทึกไม่ได้ 403 · ไม่ส่ง userId 401")
    void forbidden() {
        assertThatThrownBy(() -> service.create(4L, 7L, req("2026-11-09", "09:00", "12:00")))
                .satisfies(e -> assertApiError(e, 403, "FORBIDDEN"));
        assertThatThrownBy(() -> service.create(2L, 7L, req("2026-11-09", "09:00", "12:00")))
                .satisfies(e -> assertApiError(e, 403, "FORBIDDEN"));
        assertThatThrownBy(() -> service.create(null, 7L, req("2026-11-09", "09:00", "12:00")))
                .satisfies(e -> assertApiError(e, 401, "UNAUTHORIZED"));
    }

    @Test
    @DisplayName("UT-SV-07 การฝึกงานไม่ใช่ IN_PROGRESS ได้ 409 INTERNSHIP_NOT_IN_PROGRESS")
    void notInProgress() {
        internship.setStatus(InternshipStatus.COMPLETED);

        assertThatThrownBy(() -> service.create(3L, 7L, req("2026-11-09", "09:00", "12:00")))
                .satisfies(e -> assertApiError(e, 409, "INTERNSHIP_NOT_IN_PROGRESS"));
    }

    @Test
    @DisplayName("UT-SV-08 ส่งบันทึก DRAFT → SUBMITTED แจ้งอาจารย์ · ส่งหลังวันที่บันทึกเกิน 3 วัน = ส่งล่าช้า")
    void submit() {
        existing(1L, "2026-11-09", WorkLogStatus.DRAFT);
        existing(2L, "2026-11-03", WorkLogStatus.NEED_REVISION);

        WorkLog onTime = service.submit(3L, 1L);
        WorkLog late = service.submit(3L, 2L);

        assertThat(onTime.getStatus()).isEqualTo(WorkLogStatus.SUBMITTED);
        assertThat(onTime.getSubmittedAt()).isEqualTo(LocalDateTime.of(2026, 11, 10, 10, 0));
        assertThat(onTime.isLateSubmitted()).isFalse();
        assertThat(late.isLateSubmitted()).isTrue();
        verify(notificationService).notifyWorkLogSubmitted(onTime);
    }

    @Test
    @DisplayName("UT-SV-09 ส่ง / แก้บันทึกที่ SUBMITTED หรือ APPROVED แล้ว ได้ 409 INVALID_STATUS")
    void submitWrongStatus() {
        existing(1L, "2026-11-09", WorkLogStatus.APPROVED);
        existing(2L, "2026-11-06", WorkLogStatus.SUBMITTED);

        assertThatThrownBy(() -> service.submit(3L, 1L)).satisfies(e -> assertApiError(e, 409, "INVALID_STATUS"));
        assertThatThrownBy(() -> service.update(3L, 2L, req("2026-11-06", "09:00", "13:00")))
                .satisfies(e -> assertApiError(e, 409, "INVALID_STATUS"));
        verify(notificationService, never()).notifyWorkLogSubmitted(any());
    }

    @Test
    @DisplayName("UT-SV-10 แก้บันทึกที่ถูกส่งกลับแก้ไข ได้ชั่วโมงใหม่ · เปลี่ยนวันไปทับวันที่มีบันทึก ได้ 409")
    void update() {
        existing(1L, "2026-11-09", WorkLogStatus.NEED_REVISION);
        when(workLogDao.existsByInternshipIdAndLogDate(7L, LocalDate.of(2026, 11, 6))).thenReturn(true);

        WorkLog updated = service.update(3L, 1L, req("2026-11-09", "09:00", "13:00"));
        assertThat(updated.getHours()).isEqualByComparingTo("4.00");
        assertThatThrownBy(() -> service.update(3L, 1L, req("2026-11-06", "09:00", "13:00")))
                .satisfies(e -> assertApiError(e, 409, "DUPLICATE_LOG_DATE"));
    }

    @Test
    @DisplayName("UT-SV-11 ลบได้เฉพาะ DRAFT (ลบไฟล์แนบด้วย) · ลบ SUBMITTED ได้ 409")
    void delete() {
        WorkLog draft = existing(1L, "2026-11-09", WorkLogStatus.DRAFT);
        existing(2L, "2026-11-06", WorkLogStatus.SUBMITTED);
        WorkLogAttachment file = new WorkLogAttachment(1L, "a.pdf", "work-logs/1/a.pdf", "application/pdf", 10);
        when(attachmentDao.findByWorkLogId(1L)).thenReturn(List.of(file));

        service.delete(3L, 1L);
        verify(workLogDao).delete(draft);
        verify(fileStorage).delete("work-logs/1/a.pdf");
        assertThatThrownBy(() -> service.delete(3L, 2L)).satisfies(e -> assertApiError(e, 409, "INVALID_STATUS"));
    }

    @Test
    @DisplayName("UT-SV-12 แนบไฟล์: PDF ผ่าน · ครบ 5 ไฟล์ / ชนิดไม่รองรับ / เกิน 10 MB ได้ 400 INVALID_ATTACHMENT")
    void attachments() {
        existing(1L, "2026-11-09", WorkLogStatus.DRAFT);
        when(fileStorage.store(eq("work-logs/1"), anyString(), any())).thenReturn("work-logs/1/x.pdf");
        when(attachmentDao.save(any(WorkLogAttachment.class))).thenAnswer(i -> i.getArgument(0));
        MockMultipartFile pdf = new MockMultipartFile("file", "C:\\fakepath\\proof.PDF", "application/pdf",
                "%PDF-1.4".getBytes(StandardCharsets.US_ASCII));

        WorkLogAttachment saved = service.addAttachment(3L, 1L, pdf);
        assertThat(saved.getFileName()).isEqualTo("proof.PDF");
        assertThat(saved.getContentType()).isEqualTo("application/pdf");
        assertThat(saved.getFilePath()).isEqualTo("work-logs/1/x.pdf");

        MockMultipartFile exe = new MockMultipartFile("file", "virus.exe", "application/pdf", new byte[] { 1 });
        assertThatThrownBy(() -> service.addAttachment(3L, 1L, exe))
                .satisfies(e -> assertApiError(e, 400, "INVALID_ATTACHMENT"));
        MockMultipartFile big = new MockMultipartFile("file", "big.jpg", "image/jpeg",
                new byte[(int) WorkLogService.MAX_FILE_SIZE + 1]);
        assertThatThrownBy(() -> service.addAttachment(3L, 1L, big)).hasMessage("ไฟล์ต้องมีขนาดไม่เกิน 10 MB");
        when(attachmentDao.countByWorkLogId(1L)).thenReturn(5L);
        assertThatThrownBy(() -> service.addAttachment(3L, 1L, pdf))
                .hasMessage("แนบไฟล์ได้ไม่เกิน 5 ไฟล์ต่อบันทึก");
    }

    @Test
    @DisplayName("UT-SV-13 สรุป: วันทำงาน จ.–ศ. ตั้งแต่วันเริ่มถึงวันนี้ และวันที่ขาดบันทึก")
    void summary() {
        when(workLogDao.sumHoursByInternshipId(7L)).thenReturn(new BigDecimal("7"));
        when(workLogDao.findLogDatesByInternshipId(7L))
                .thenReturn(List.of(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3)));
        when(workLogDao.countByInternshipIdGroupByStatus(7L)).thenReturn(List.of());

        WorkLogSummaryResponse sum = service.summary(3L, 7L);

        assertThat(sum.totalHours()).isEqualByComparingTo("7.00");
        assertThat(sum.daysLogged()).isEqualTo(2);
        assertThat(sum.workingDays()).isEqualTo(7);
        assertThat(sum.missingDates()).containsExactly(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 5),
                LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 10));
        assertThat(sum.countByStatus()).containsEntry(WorkLogStatus.DRAFT, 0L).hasSize(4);
    }

    @Test
    @DisplayName("UT-SV-14 รายการ: อาจารย์ที่ปรึกษาไม่เห็น DRAFT · อาจารย์คนอื่น 403 · เจ้าหน้าที่ดูได้")
    void listAccess() {
        WorkLog draft = existing(1L, "2026-11-09", WorkLogStatus.DRAFT);
        WorkLog submitted = existing(2L, "2026-11-06", WorkLogStatus.SUBMITTED);
        when(workLogDao.findByInternshipIdAndFilter(7L, null, null, null)).thenReturn(List.of(submitted, draft));

        assertThat(service.getLogs(3L, 7L, null)).containsExactly(submitted, draft);
        assertThat(service.getLogs(2L, 7L, null)).containsExactly(submitted);
        assertThat(service.getLogs(1L, 7L, null)).containsExactly(submitted);
        assertThatThrownBy(() -> service.getLogs(5L, 7L, null)).satisfies(e -> assertApiError(e, 403, "FORBIDDEN"));
        assertThatThrownBy(() -> service.getDetail(2L, 1L)).satisfies(e -> assertApiError(e, 404, "NOT_FOUND"));
    }

    @Test
    @DisplayName("UT-SV-15 ส่งออก CSV: อาจารย์ได้ไฟล์มีหัวตาราง · นักศึกษา 403")
    void export() {
        WorkLog submitted = existing(2L, "2026-11-06", WorkLogStatus.SUBMITTED);
        submitted.setActivity("ทำ \"API\"");
        when(workLogDao.findByInternshipIdAndFilter(7L, null, null, null)).thenReturn(List.of(submitted));

        String csv = new String(service.export(2L, 7L), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFวันที่,เวลาเริ่ม").contains("2026-11-06,09:00,12:00,3.00,\"ทำ \"\"API\"\"\"");
        assertThatThrownBy(() -> service.export(3L, 7L)).satisfies(e -> assertApiError(e, 403, "FORBIDDEN"));
    }

    @Test
    @DisplayName("UT-SV-16 hasPendingReview ใช้ existsByInternshipIdAndStatus(SUBMITTED)")
    void pendingReview() {
        when(workLogDao.existsByInternshipIdAndStatus(7L, WorkLogStatus.SUBMITTED)).thenReturn(true);

        assertThat(service.hasPendingReview(7L)).isTrue();
        ArgumentCaptor<WorkLogStatus> status = ArgumentCaptor.forClass(WorkLogStatus.class);
        verify(workLogDao).existsByInternshipIdAndStatus(eq(7L), status.capture());
        assertThat(status.getValue()).isEqualTo(WorkLogStatus.SUBMITTED);
    }
}
