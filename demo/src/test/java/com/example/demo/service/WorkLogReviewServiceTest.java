package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.dao.UserRepository;
import com.example.demo.dao.WorkLogDao;
import com.example.demo.dto.BulkWorkLogReviewResponse;
import com.example.demo.dto.BulkWorkLogReviewResponse.SkippedItem;
import com.example.demo.entity.ReviewDecision;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.entity.WorkLog;
import com.example.demo.entity.WorkLogStatus;

/**
 * U8:T4 Unit test ส่วน Service: WorkLogReviewService (อาจารย์ตรวจบันทึก) · DAO ถูก mock
 * อาจารย์ที่ปรึกษาของนักศึกษา A (userId 3) = 2 · นักศึกษา B (userId 4) อยู่กับอาจารย์ 5
 */
class WorkLogReviewServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T03:00:00Z"), ZoneId.of("Asia/Bangkok"));

    private WorkLogDao workLogDao;
    private UserRepository userDao;
    private NotificationService notificationService;
    private WorkLogReviewService service;

    @BeforeEach
    void setUp() {
        workLogDao = mock(WorkLogDao.class);
        userDao = mock(UserRepository.class);
        notificationService = mock(NotificationService.class);
        service = new WorkLogReviewService(workLogDao, userDao, notificationService, new AccessGuard(userDao), CLOCK);

        user(2L, Role.ADVISOR, null);
        user(3L, Role.STUDENT, 2L);
        user(4L, Role.STUDENT, 5L);
        user(5L, Role.ADVISOR, null);
        when(workLogDao.save(any(WorkLog.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void user(Long id, Role role, Long advisorId) {
        User u = new User("u" + id, "ผู้ใช้ " + id, null, role);
        ReflectionTestUtils.setField(u, "id", id);
        u.setAdvisorId(advisorId);
        when(userDao.findById(id)).thenReturn(Optional.of(u));
    }

    private WorkLog log(Long id, Long studentId, WorkLogStatus status) {
        WorkLog w = new WorkLog(7L, studentId, LocalDate.of(2026, 11, 9));
        ReflectionTestUtils.setField(w, "id", id);
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
    @DisplayName("UT-RV-01 อาจารย์ที่ปรึกษาอนุมัติ ได้ APPROVED บันทึกผู้ตรวจ / เวลา และแจ้งนักศึกษา")
    void approve() {
        log(1L, 3L, WorkLogStatus.SUBMITTED);

        WorkLog w = service.review(2L, 1L, ReviewDecision.APPROVE, "  ");

        assertThat(w.getStatus()).isEqualTo(WorkLogStatus.APPROVED);
        assertThat(w.getReviewedBy()).isEqualTo(2L);
        assertThat(w.getReviewedAt()).isEqualTo(LocalDateTime.of(2026, 11, 10, 10, 0));
        assertThat(w.getReviewComment()).isNull();
        verify(notificationService).notifyWorkLogReviewed(w);
    }

    @Test
    @DisplayName("UT-RV-02 ส่งกลับแก้ไขไม่มีความเห็น 400 COMMENT_REQUIRED · มีความเห็นได้ NEED_REVISION")
    void revise() {
        log(1L, 3L, WorkLogStatus.SUBMITTED);

        assertThatThrownBy(() -> service.review(2L, 1L, ReviewDecision.REVISE, " "))
                .satisfies(e -> assertApiError(e, 400, "COMMENT_REQUIRED"));
        WorkLog w = service.review(2L, 1L, ReviewDecision.REVISE, " เพิ่มรายละเอียด ");
        assertThat(w.getStatus()).isEqualTo(WorkLogStatus.NEED_REVISION);
        assertThat(w.getReviewComment()).isEqualTo("เพิ่มรายละเอียด");
        assertThat(w.isEditable()).isTrue();
    }

    @Test
    @DisplayName("UT-RV-03 ไม่ใช่อาจารย์ที่ปรึกษาของนักศึกษา 403 · นักศึกษาเรียก 403 · ไม่บันทึก")
    void forbidden() {
        log(1L, 3L, WorkLogStatus.SUBMITTED);

        assertThatThrownBy(() -> service.review(5L, 1L, ReviewDecision.APPROVE, null))
                .satisfies(e -> assertApiError(e, 403, "FORBIDDEN"));
        assertThatThrownBy(() -> service.review(3L, 1L, ReviewDecision.APPROVE, null))
                .satisfies(e -> assertApiError(e, 403, "FORBIDDEN"));
        verify(workLogDao, never()).save(any());
    }

    @Test
    @DisplayName("UT-RV-04 บันทึกไม่ใช่ SUBMITTED ได้ 409 WORK_LOG_NOT_PENDING · ไม่พบบันทึก 404")
    void notPending() {
        log(1L, 3L, WorkLogStatus.DRAFT);

        assertThatThrownBy(() -> service.review(2L, 1L, ReviewDecision.APPROVE, null))
                .satisfies(e -> assertApiError(e, 409, "WORK_LOG_NOT_PENDING"));
        assertThatThrownBy(() -> service.review(2L, 999L, ReviewDecision.APPROVE, null))
                .satisfies(e -> assertApiError(e, 404, "NOT_FOUND"));
    }

    @Test
    @DisplayName("UT-RV-05 getPending ใช้ findSubmittedByAdvisorId ของอาจารย์ที่ login")
    void pending() {
        List<WorkLog> logs = List.of(log(1L, 3L, WorkLogStatus.SUBMITTED));
        when(workLogDao.findSubmittedByAdvisorId(2L)).thenReturn(logs);

        assertThat(service.getPending(2L)).isEqualTo(logs);
        assertThatThrownBy(() -> service.getPending(3L)).satisfies(e -> assertApiError(e, 403, "FORBIDDEN"));
    }

    @Test
    @DisplayName("UT-RV-06 อนุมัติหลายบันทึก: ผ่านเงื่อนไขอนุมัติ ที่เหลือใส่ skipped พร้อมเหตุผล")
    void bulk() {
        WorkLog ok = log(1L, 3L, WorkLogStatus.SUBMITTED);
        WorkLog draft = log(2L, 3L, WorkLogStatus.DRAFT);
        WorkLog other = log(3L, 4L, WorkLogStatus.SUBMITTED);
        when(workLogDao.findAllByIdIn(List.of(1L, 2L, 3L, 999L))).thenReturn(List.of(ok, draft, other));

        BulkWorkLogReviewResponse res = service.reviewBulk(2L, List.of(1L, 2L, 3L, 999L, 1L), "ผ่าน");

        assertThat(res.approved()).containsExactly(1L);
        assertThat(res.skipped()).extracting(SkippedItem::workLogId).containsExactly(2L, 3L, 999L);
        assertThat(res.skipped()).extracting(SkippedItem::reason).containsExactly(
                "บันทึกไม่ได้อยู่ในสถานะรอตรวจ", "ไม่ใช่นักศึกษาในที่ปรึกษา", "ไม่พบบันทึก");
        assertThat(ok.getStatus()).isEqualTo(WorkLogStatus.APPROVED);
        assertThat(other.getStatus()).isEqualTo(WorkLogStatus.SUBMITTED);
        verify(notificationService, times(1)).notifyWorkLogReviewed(any());
    }

    @Test
    @DisplayName("UT-RV-07 อนุมัติหลายบันทึก: ไม่เลือกบันทึก หรือเกิน 100 บันทึก ได้ 400")
    void bulkLimits() {
        List<Long> many = new ArrayList<>(LongStream.rangeClosed(1, 101).boxed().toList());

        assertThatThrownBy(() -> service.reviewBulk(2L, List.of(), null))
                .satisfies(e -> assertApiError(e, 400, "VALIDATION_ERROR"));
        assertThatThrownBy(() -> service.reviewBulk(2L, many, null))
                .satisfies(e -> assertApiError(e, 400, "VALIDATION_ERROR"));
    }
}
