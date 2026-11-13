package com.example.demo.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dao.ApplicationRepository;
import com.example.demo.dao.FormTemplateRepository;
import com.example.demo.entity.Application;
import com.example.demo.entity.ApplicationStatus;
import com.example.demo.entity.FormTemplate;

/**
 * U5: ทุกเช้า เตือนนักศึกษาที่คำร้องถูกตีกลับ (NEED_CORRECTION) แต่ยังไม่ส่งใหม่
 * เมื่อแบบฟอร์มจะหมดเขตภายใน {@link #REMIND_DAYS_BEFORE} วัน
 */
@Service
public class CorrectionReminderJob {

    public static final int REMIND_DAYS_BEFORE = 2;
    private static final Logger log = LoggerFactory.getLogger(CorrectionReminderJob.class);

    private final ApplicationRepository applicationRepository;
    private final FormTemplateRepository templateRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    public CorrectionReminderJob(ApplicationRepository applicationRepository,
            FormTemplateRepository templateRepository, NotificationService notificationService, Clock clock) {
        this.applicationRepository = applicationRepository;
        this.templateRepository = templateRepository;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.reminder.cron:0 0 8 * * *}", zone = "${app.reminder.zone:Asia/Bangkok}")
    public void runDaily() {
        int sent = remindPendingCorrections();
        log.info("[REMINDER] เตือนนักศึกษาที่ต้องแก้คำร้อง {} รายการ", sent);
    }

    /** @return จำนวนการแจ้งเตือนที่ส่งรอบนี้ */
    @Transactional
    public int remindPendingCorrections() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime limit = now.plusDays(REMIND_DAYS_BEFORE);
        Map<Long, Optional<FormTemplate>> forms = new HashMap<>();
        int sent = 0;
        for (Application app : applicationRepository.findByStatusOrderBySubmittedAtAsc(ApplicationStatus.NEED_CORRECTION)) {
            LocalDateTime deadline = forms
                    .computeIfAbsent(app.getFormTemplateId(), templateRepository::findById)
                    .map(FormTemplate::getDeadline).orElse(null);
            if (deadline == null || !deadline.isAfter(now) || deadline.isAfter(limit)) {
                continue; // ไม่มีวันหมดเขต / หมดเขตไปแล้ว / ยังอีกนาน
            }
            if (notificationService.remindCorrection(app, deadline, now)) {
                sent++;
            }
        }
        return sent;
    }
}
