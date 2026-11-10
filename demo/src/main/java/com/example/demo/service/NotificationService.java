package com.example.demo.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.example.demo.entity.WorkLog;
import com.example.demo.entity.WorkLogStatus;

/** U5: สร้างและอ่านการแจ้งเตือนในระบบ */
@Service
@Transactional
public class NotificationService {

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
            for (User advisor : userRepository.findByRoleAndActiveTrue(Role.ADVISOR)) {
                save(advisor.getId(), "มีใบสมัครรอพิจารณา",
                        "ใบสมัครเลขที่ " + app.getId() + " ผ่านการตรวจของเจ้าหน้าที่แล้ว รออาจารย์พิจารณา",
                        NotificationType.APPLICATION_FORWARDED, app);
            }
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

    /** U8: นักศึกษาส่งบันทึกการทำงาน → แจ้งอาจารย์ที่ปรึกษาของนักศึกษา */
    public void notifyWorkLogSubmitted(WorkLog log) {
        userRepository.findById(log.getStudentId())
                .filter(student -> student.getAdvisorId() != null)
                .ifPresent(student -> notificationRepository.save(new Notification(student.getAdvisorId(),
                        "มีบันทึกการทำงานรอตรวจ",
                        student.getFullName() + " ส่งบันทึกการทำงานวันที่ " + log.getLogDate() + " รอตรวจ",
                        NotificationType.WORK_LOG_SUBMITTED, null)));
    }

    /** U8: อาจารย์ตรวจบันทึกแล้ว → แจ้งนักศึกษา (อนุมัติ / ส่งกลับแก้ไขพร้อมความเห็น) */
    public void notifyWorkLogReviewed(WorkLog log) {
        boolean approved = log.getStatus() == WorkLogStatus.APPROVED;
        String comment = log.getReviewComment() == null ? "" : " ความเห็น: " + log.getReviewComment();
        notificationRepository.save(new Notification(log.getStudentId(),
                approved ? "บันทึกการทำงานได้รับการอนุมัติ" : "บันทึกการทำงานถูกส่งกลับให้แก้ไข",
                "บันทึกวันที่ " + log.getLogDate() + (approved ? " ได้รับการอนุมัติแล้ว" : " กรุณาแก้ไขแล้วส่งใหม่")
                        + comment,
                NotificationType.WORK_LOG_REVIEWED, null));
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
                            .ifPresent(u -> emailNotifier.send(u.getEmail(), title,
                                    "คำร้องเลขที่ " + app.getId() + ": " + message));
                });
    }

    private void save(Long userId, String title, String message, NotificationType type, Application app) {
        notificationRepository.save(new Notification(userId, title, message, type, app.getId()));
    }
}
