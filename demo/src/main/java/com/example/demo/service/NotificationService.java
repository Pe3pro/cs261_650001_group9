package com.example.demo.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.example.demo.dao.NotificationRepository;
import com.example.demo.dao.StudentRepository;
import com.example.demo.dao.UserRepository;
import com.example.demo.dto.NotificationResponse;
import com.example.demo.entity.Application;
import com.example.demo.entity.ApprovalDecision;
import com.example.demo.entity.ApprovalLevel;
import com.example.demo.entity.Notification;
import com.example.demo.entity.NotificationType;
import com.example.demo.entity.Role;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;

/** U5: สร้างและอ่านการแจ้งเตือนในระบบ */
@Service
@Transactional
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final AccessGuard guard;
    private final EmailNotifier emailNotifier;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository,
            StudentRepository studentRepository, AccessGuard guard, EmailNotifier emailNotifier) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.guard = guard;
        this.emailNotifier = emailNotifier;
    }

    /** เรียกหลังบันทึกผลการพิจารณา เพื่อแจ้งคนที่ต้องทำต่อ */
    public void notifyAfterDecision(Application app, ApprovalLevel level, ApprovalDecision decision, String comment) {
        String reason = (comment == null || comment.isBlank()) ? "" : " เหตุผล: " + comment.trim();
        if (decision == ApprovalDecision.APPROVE && level == ApprovalLevel.STAFF) {
            // แจ้งเฉพาะอาจารย์ที่ปรึกษาของนักศึกษาเจ้าของคำร้อง ไม่ใช่อาจารย์ทุกคน
            studentRepository.findById(app.getStudentId())
                    .map(Student::getAdvisorId)
                    .flatMap(userRepository::findById)
                    .filter(u -> u.getRole() == Role.ADVISOR && !Boolean.FALSE.equals(u.getActive()))
                    .ifPresentOrElse(
                            advisor -> save(advisor.getId(), "มีใบสมัครรอพิจารณา",
                                    "ใบสมัครเลขที่ " + app.getId() + " ผ่านการตรวจของเจ้าหน้าที่แล้ว รออาจารย์พิจารณา",
                                    NotificationType.APPLICATION_FORWARDED, app),
                            () -> log.warn("ใบสมัครเลขที่ {} ไม่มีอาจารย์ที่ปรึกษาที่ใช้งานอยู่ จึงไม่ได้แจ้งอาจารย์", app.getId()));
            notifyStudent(app, "เจ้าหน้าที่ตรวจใบสมัครแล้ว",
                    "ใบสมัครของคุณถูกส่งต่อให้อาจารย์พิจารณา",
                    NotificationType.APPLICATION_FORWARDED);
        } else if (decision == ApprovalDecision.APPROVE) {
            notifyStudent(app, "ใบสมัครได้รับการอนุมัติ",
                    "อาจารย์อนุมัติใบสมัครฝึกสหกิจศึกษาของคุณแล้ว ขั้นตอนต่อไปคือจับคู่บริษัท",
                    NotificationType.APPLICATION_APPROVED);
        } else if (decision == ApprovalDecision.RETURN) {
            notifyStudent(app, "ใบสมัครถูกส่งกลับให้แก้ไข",
                    "กรุณาแก้ไขใบสมัครแล้วส่งใหม่ก่อนหมดเขต." + reason,
                    NotificationType.APPLICATION_RETURNED);
        } else {
            notifyStudent(app, "ใบสมัครไม่ได้รับการอนุมัติ",
                    "ใบสมัครฝึกสหกิจศึกษาของคุณไม่ได้รับการอนุมัติ." + reason,
                    NotificationType.APPLICATION_REJECTED);
        }
    }

    /**
     * U5: แจ้งเจ้าหน้าที่ทุกคน (ที่ใช้งานอยู่) เมื่อมีคำร้องใหม่ หรือคำร้องที่แก้แล้วส่งกลับมา
     * แจ้งเฉพาะในระบบ ไม่ส่ง Email เพื่อไม่ให้กล่องจดหมายเจ้าหน้าที่เต็ม
     */
    public void notifyStaffSubmitted(Application app, boolean resubmitted) {
        String title = resubmitted ? "มีคำร้องที่แก้ไขแล้วส่งกลับมา" : "มีคำร้องใหม่รอตรวจ";
        String message = resubmitted
                ? "คำร้องเลขที่ " + app.getId() + " ถูกแก้ไขตามที่ส่งกลับและส่งเข้ามาใหม่แล้ว"
                : "คำร้องเลขที่ " + app.getId() + " ถูกส่งเข้ามาในระบบ รอเจ้าหน้าที่ตรวจสอบ";
        for (User staff : userRepository.findByRoleAndActiveTrue(Role.STAFF)) {
            save(staff.getId(), title, message, NotificationType.APPLICATION_SUBMITTED, app);
        }
    }

    /**
     * U5: เตือนนักศึกษาที่ถูกตีกลับแต่ยังไม่ส่งใหม่ ว่าแบบฟอร์มใกล้หมดเขต
     * เตือนไม่เกินวันละครั้งต่อคำร้อง (ถ้าวันนี้เตือนไปแล้วจะข้าม)
     * @return true ถ้าสร้างการแจ้งเตือนใหม่
     */
    public boolean remindCorrection(Application app, LocalDateTime deadline, LocalDateTime now) {
        if (notificationRepository.existsByApplicationIdAndTypeAndCreatedAtAfter(
                app.getId(), NotificationType.CORRECTION_REMINDER, now.toLocalDate().atStartOfDay())) {
            return false;
        }
        String when = deadline.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        notifyStudent(app, "ใกล้หมดเขตส่งคำร้องที่ต้องแก้ไข",
                "คำร้องของคุณถูกส่งกลับให้แก้ไขและยังไม่ได้ส่งใหม่ กรุณาส่งภายใน " + when + " น.",
                NotificationType.CORRECTION_REMINDER);
        return true;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(Long userId) {
        User user = guard.require(userId);
        return notificationRepository.findByUserIdOrderByCreatedAtDescIdDesc(user.getId())
                .stream().map(NotificationResponse::from).toList();
    }

    public NotificationResponse markAsRead(Long notificationId, Long userId) {
        User user = guard.require(userId);
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> ApiException.notFound("ไม่พบการแจ้งเตือน"));
        if (!n.getUserId().equals(user.getId())) {
            throw ApiException.forbidden();
        }
        n.markAsRead();
        return NotificationResponse.from(notificationRepository.save(n));
    }

    private void notifyStudent(Application app, String title, String message, NotificationType type) {
        studentRepository.findById(app.getStudentId())
                .map(Student::getUserId)
                .ifPresent(studentUserId -> {
                    save(studentUserId, title, message, type, app);
                    // U3: แจ้งนักศึกษาทาง Email ด้วย (Sequence Diagram: Service → Email)
                    userRepository.findById(studentUserId)
                            .filter(u -> u.getEmail() != null && !u.getEmail().isBlank())
                            .ifPresent(u -> sendEmailAfterCommit(u.getEmail(), title,
                                    "คำร้องเลขที่ " + app.getId() + ": " + message));
                });
    }

    /**
     * ส่ง Email หลังบันทึกผลการพิจารณาลงฐานข้อมูลสำเร็จแล้วเท่านั้น
     * ถ้าส่งไม่สำเร็จ ผลการพิจารณาและแจ้งเตือนในระบบยังอยู่ครบ และบันทึกความล้มเหลวไว้ใน log
     */
    void sendEmailAfterCommit(String to, String subject, String body) {
        Runnable send = () -> {
            try {
                emailNotifier.send(to, subject, body);
            } catch (RuntimeException e) {
                log.warn("[EMAIL-FAILED] to={} subject={} reason={}", to, subject, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    private void save(Long userId, String title, String message, NotificationType type, Application app) {
        notificationRepository.save(new Notification(userId, title, message, type, app.getId()));
    }
}
