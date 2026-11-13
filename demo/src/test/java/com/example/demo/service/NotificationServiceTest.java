package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.dao.NotificationRepository;
import com.example.demo.dao.StudentRepository;
import com.example.demo.dao.UserRepository;
import com.example.demo.entity.Application;
import com.example.demo.entity.ApplicationStatus;
import com.example.demo.entity.ApprovalDecision;
import com.example.demo.entity.ApprovalLevel;
import com.example.demo.entity.Notification;
import com.example.demo.entity.NotificationType;
import com.example.demo.entity.Role;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;

/** U5: Unit test การแจ้งเตือนหลังพิจารณาคำร้อง (NotificationService) โดยไม่ต่อฐานข้อมูล */
class NotificationServiceTest {

    private NotificationRepository notifications;
    private UserRepository users;
    private StudentRepository students;
    private EmailNotifier email;
    private NotificationService service;

    private final User studentUser = user(30L, Role.STUDENT, "somchai@dome.tu.ac.th");
    private final User advisor = user(2L, Role.ADVISOR, "advisor01@tu.ac.th");
    private Application app;

    @BeforeEach
    void setUp() {
        notifications = mock(NotificationRepository.class);
        users = mock(UserRepository.class);
        students = mock(StudentRepository.class);
        email = mock(EmailNotifier.class);
        service = new NotificationService(notifications, users, students, mock(AccessGuard.class), email);

        app = new Application(20L, 10L, ApplicationStatus.SUBMITTED);
        ReflectionTestUtils.setField(app, "id", 50L);
        when(users.findById(30L)).thenReturn(Optional.of(studentUser));
        when(users.findById(2L)).thenReturn(Optional.of(advisor));
    }

    private static User user(Long id, Role role, String mail) {
        User u = new User("u" + id, "ชื่อ " + id, mail, role);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private void studentWithAdvisor(Long advisorId) {
        Student s = new Student(30L, "6609650001", null, null, null);
        ReflectionTestUtils.setField(s, "id", 20L);
        s.setAdvisorId(advisorId);
        when(students.findById(20L)).thenReturn(Optional.of(s));
    }

    private List<Long> notifiedUserIds() {
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, org.mockito.Mockito.atLeast(0)).save(saved.capture());
        return saved.getAllValues().stream().map(Notification::getUserId).toList();
    }

    @Test
    @DisplayName("NT-01 เจ้าหน้าที่อนุมัติ แจ้งเฉพาะอาจารย์ที่ปรึกษาของนักศึกษา และแจ้งนักศึกษา")
    void staffApproveNotifiesOwnAdvisorOnly() {
        studentWithAdvisor(2L);

        service.notifyAfterDecision(app, ApprovalLevel.STAFF, ApprovalDecision.APPROVE, null);

        assertThat(notifiedUserIds()).containsExactlyInAnyOrder(2L, 30L);
        verify(users, org.mockito.Mockito.never()).findByRoleAndActiveTrue(any());
    }

    @Test
    @DisplayName("NT-02 นักศึกษายังไม่มีอาจารย์ที่ปรึกษา แจ้งเฉพาะนักศึกษา ไม่แจ้งอาจารย์คนอื่น")
    void noAdvisorOnlyStudentNotified() {
        studentWithAdvisor(null);

        service.notifyAfterDecision(app, ApprovalLevel.STAFF, ApprovalDecision.APPROVE, null);

        assertThat(notifiedUserIds()).containsExactly(30L);
    }

    @Test
    @DisplayName("NT-03 ส่งกลับแก้ไข ส่ง Email ถึงนักศึกษาพร้อมเหตุผล")
    void returnSendsEmailWithReason() {
        studentWithAdvisor(2L);

        service.notifyAfterDecision(app, ApprovalLevel.STAFF, ApprovalDecision.RETURN, "Transcript ไม่ชัด");

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(email).send(eq("somchai@dome.tu.ac.th"), anyString(), body.capture());
        assertThat(body.getValue()).contains("Transcript ไม่ชัด").contains("50");
    }

    @Test
    @DisplayName("NT-04 ส่ง Email ไม่สำเร็จ ไม่โยน exception และแจ้งเตือนในระบบยังถูกบันทึก")
    void emailFailureDoesNotBreakDecision() {
        studentWithAdvisor(2L);
        doThrow(new RuntimeException("SMTP down")).when(email).send(anyString(), anyString(), anyString());

        assertThatCode(() -> service.notifyAfterDecision(app, ApprovalLevel.ADVISOR, ApprovalDecision.APPROVE, null))
                .doesNotThrowAnyException();
        assertThat(notifiedUserIds()).containsExactly(30L);
    }

    @Test
    @DisplayName("NT-05 มีคำร้องใหม่: แจ้งเจ้าหน้าที่ทุกคนที่ใช้งานอยู่ ชนิด APPLICATION_SUBMITTED (ไม่ส่ง Email)")
    void notifyAllStaffOnSubmit() {
        when(users.findByRoleAndActiveTrue(Role.STAFF)).thenReturn(List.of(
                user(1L, Role.STAFF, "staff01@tu.ac.th"), user(5L, Role.STAFF, "staff02@tu.ac.th")));

        service.notifyStaffSubmitted(app, false);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Notification::getUserId).containsExactly(1L, 5L);
        assertThat(saved.getAllValues()).extracting(Notification::getType)
                .containsOnly(NotificationType.APPLICATION_SUBMITTED);
        assertThat(saved.getValue().getTitle()).isEqualTo("มีคำร้องใหม่รอตรวจ");
        verify(email, org.mockito.Mockito.never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("NT-06 คำร้องที่แก้แล้วส่งกลับมา: หัวข้อแจ้งเตือนบอกว่าเป็นฉบับแก้")
    void notifyStaffOnResubmit() {
        when(users.findByRoleAndActiveTrue(Role.STAFF)).thenReturn(List.of(user(1L, Role.STAFF, null)));

        service.notifyStaffSubmitted(app, true);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(saved.capture());
        assertThat(saved.getValue().getTitle()).isEqualTo("มีคำร้องที่แก้ไขแล้วส่งกลับมา");
    }

    @Test
    @DisplayName("NT-07 เตือนนักศึกษาก่อนหมดเขต: วันนี้ยังไม่เคยเตือน → แจ้งในระบบ (บอกวันหมดเขต) · เตือนแล้ว → ข้าม")
    void remindOncePerDay() {
        studentWithAdvisor(2L);
        LocalDateTime now = LocalDateTime.of(2026, 11, 29, 8, 0);
        LocalDateTime deadline = LocalDateTime.of(2026, 11, 30, 23, 59);
        when(notifications.existsByApplicationIdAndTypeAndCreatedAtAfter(50L, NotificationType.CORRECTION_REMINDER,
                LocalDateTime.of(2026, 11, 29, 0, 0))).thenReturn(false, true);

        assertThat(service.remindCorrection(app, deadline, now)).isTrue();
        assertThat(service.remindCorrection(app, deadline, now)).isFalse();

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(30L);
        assertThat(saved.getValue().getType()).isEqualTo(NotificationType.CORRECTION_REMINDER);
        assertThat(saved.getValue().getMessage()).contains("30/11/2026 23:59");
    }
}

