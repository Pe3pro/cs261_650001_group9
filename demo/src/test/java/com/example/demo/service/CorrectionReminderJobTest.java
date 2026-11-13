package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

import com.example.demo.dao.ApplicationRepository;
import com.example.demo.dao.FormTemplateRepository;
import com.example.demo.entity.Application;
import com.example.demo.entity.ApplicationStatus;
import com.example.demo.entity.FormTemplate;

/** U5: งานเตือนนักศึกษาที่ต้องแก้คำร้องก่อนหมดเขต (CorrectionReminderJob) · เวลา = 29 พ.ย. 2569 08:00 น. */
class CorrectionReminderJobTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-29T01:00:00Z"), ZoneId.of("Asia/Bangkok"));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 29, 8, 0);

    private ApplicationRepository applications;
    private FormTemplateRepository templates;
    private NotificationService notifications;
    private CorrectionReminderJob job;

    @BeforeEach
    void setUp() {
        applications = mock(ApplicationRepository.class);
        templates = mock(FormTemplateRepository.class);
        notifications = mock(NotificationService.class);
        job = new CorrectionReminderJob(applications, templates, notifications, CLOCK);
        when(notifications.remindCorrection(any(), any(), any())).thenReturn(true);
    }

    private Application needCorrection(Long id, Long formId, LocalDateTime deadline) {
        Application a = new Application(20L, formId, ApplicationStatus.NEED_CORRECTION);
        ReflectionTestUtils.setField(a, "id", id);
        FormTemplate f = new FormTemplate("แบบฟอร์ม " + formId, 1, 1L);
        f.setDeadline(deadline);
        when(templates.findById(formId)).thenReturn(Optional.of(f));
        return a;
    }

    @Test
    @DisplayName("RM-01 เตือนเฉพาะคำร้องที่แบบฟอร์มจะหมดเขตภายใน 2 วัน (ไม่เตือนที่ยังอีกนาน / หมดเขตแล้ว)")
    void remindOnlyNearDeadline() {
        Application soon = needCorrection(1L, 10L, NOW.plusDays(1));
        Application far = needCorrection(2L, 11L, NOW.plusDays(10));
        Application passed = needCorrection(3L, 12L, NOW.minusHours(1));
        when(applications.findByStatusOrderBySubmittedAtAsc(ApplicationStatus.NEED_CORRECTION))
                .thenReturn(List.of(soon, far, passed));

        assertThat(job.remindPendingCorrections()).isEqualTo(1);
        verify(notifications).remindCorrection(soon, NOW.plusDays(1), NOW);
        verify(notifications, never()).remindCorrection(far, NOW.plusDays(10), NOW);
        verify(notifications, never()).remindCorrection(passed, NOW.minusHours(1), NOW);
    }

    @Test
    @DisplayName("RM-02 ไม่มีคำร้องที่ต้องแก้: ไม่เตือนใคร")
    void nothingToRemind() {
        when(applications.findByStatusOrderBySubmittedAtAsc(ApplicationStatus.NEED_CORRECTION)).thenReturn(List.of());

        assertThat(job.remindPendingCorrections()).isZero();
        verify(notifications, never()).remindCorrection(any(), any(), any());
    }
}
