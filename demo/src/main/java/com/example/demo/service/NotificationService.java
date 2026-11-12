package com.example.demo.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

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
import com.example.demo.entity.Internship;
import com.example.demo.entity.Match;
import com.example.demo.entity.MatchStatus;
import com.example.demo.entity.MatchingRound;
import com.example.demo.entity.Notification;
import com.example.demo.entity.NotificationType;
import com.example.demo.entity.Role;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;

/** U5-U7: สร้างและอ่านการแจ้งเตือนในระบบ */
@Service
@Transactional
public class NotificationService {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final AccessGuard guard;
    private final EmailNotifier emailNotifier;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            StudentRepository studentRepository,
            AccessGuard guard,
            EmailNotifier emailNotifier) {

        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.guard = guard;
        this.emailNotifier = emailNotifier;
    }

    /** แจ้งคนที่เกี่ยวข้องหลังพิจารณาคำร้อง */
    public void notifyAfterDecision(
            Application app,
            ApprovalLevel level,
            ApprovalDecision decision,
            String comment) {

        String reason = comment == null || comment.isBlank()
                ? ""
                : " เหตุผล: " + comment.trim();

        if (decision == ApprovalDecision.APPROVE
                && level == ApprovalLevel.STAFF) {

            studentRepository.findById(app.getStudentId())
                    .map(Student::getAdvisorId)
                    .flatMap(userRepository::findById)
                    .filter(user ->
                            user.getRole() == Role.ADVISOR
                                    && !Boolean.FALSE.equals(user.getActive()))
                    .ifPresentOrElse(
                            advisor -> save(
                                    advisor.getId(),
                                    "มีใบสมัครรอพิจารณา",
                                    "ใบสมัครเลขที่ " + app.getId()
                                            + " ผ่านการตรวจของเจ้าหน้าที่แล้ว "
                                            + "รออาจารย์พิจารณา",
                                    NotificationType.APPLICATION_FORWARDED,
                                    app),
                            () -> log.warn(
                                    "ใบสมัครเลขที่ {} ไม่มีอาจารย์ที่ปรึกษาที่ใช้งานอยู่",
                                    app.getId()));

            notifyStudent(
                    app,
                    "เจ้าหน้าที่ตรวจใบสมัครแล้ว",
                    "ใบสมัครของคุณถูกส่งต่อให้อาจารย์พิจารณา",
                    NotificationType.APPLICATION_FORWARDED);

        } else if (decision == ApprovalDecision.APPROVE) {

            notifyStudent(
                    app,
                    "ใบสมัครได้รับการอนุมัติ",
                    "อาจารย์อนุมัติใบสมัครฝึกสหกิจศึกษาของคุณแล้ว "
                            + "ขั้นตอนต่อไปคือจับคู่บริษัท",
                    NotificationType.APPLICATION_APPROVED);

        } else if (decision == ApprovalDecision.RETURN) {

            notifyStudent(
                    app,
                    "ใบสมัครถูกส่งกลับให้แก้ไข",
                    "กรุณาแก้ไขใบสมัครแล้วส่งใหม่ก่อนหมดเขต." + reason,
                    NotificationType.APPLICATION_RETURNED);

        } else {

            notifyStudent(
                    app,
                    "ใบสมัครไม่ได้รับการอนุมัติ",
                    "ใบสมัครฝึกสหกิจศึกษาของคุณไม่ได้รับการอนุมัติ."
                            + reason,
                    NotificationType.APPLICATION_REJECTED);
        }
    }

    /** U5: แจ้งเจ้าหน้าที่เมื่อมีคำร้องใหม่หรือส่งกลับมาใหม่ */
    public void notifyStaffSubmitted(
            Application app,
            boolean resubmitted) {

        String title = resubmitted
                ? "มีคำร้องที่แก้ไขแล้วส่งกลับมา"
                : "มีคำร้องใหม่รอตรวจ";

        String message = resubmitted
                ? "คำร้องเลขที่ " + app.getId()
                        + " ถูกแก้ไขตามที่ส่งกลับและส่งเข้ามาใหม่แล้ว"
                : "คำร้องเลขที่ " + app.getId()
                        + " ถูกส่งเข้ามาในระบบ รอเจ้าหน้าที่ตรวจสอบ";

        for (User staff :
                userRepository.findByRoleAndActiveTrue(Role.STAFF)) {

            save(
                    staff.getId(),
                    title,
                    message,
                    NotificationType.APPLICATION_SUBMITTED,
                    app);
        }
    }

    /** U5: เตือนนักศึกษาที่ถูกส่งกลับแต่ยังไม่ส่งใหม่ */
    public boolean remindCorrection(
            Application app,
            LocalDateTime deadline,
            LocalDateTime now) {

        boolean alreadySent =
                notificationRepository
                        .existsByApplicationIdAndTypeAndCreatedAtAfter(
                                app.getId(),
                                NotificationType.CORRECTION_REMINDER,
                                now.toLocalDate().atStartOfDay());

        if (alreadySent) {
            return false;
        }

        String when = deadline.format(
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

        notifyStudent(
                app,
                "ใกล้หมดเขตส่งคำร้องที่ต้องแก้ไข",
                "คำร้องของคุณถูกส่งกลับให้แก้ไขและยังไม่ได้ส่งใหม่ "
                        + "กรุณาส่งภายใน " + when + " น.",
                NotificationType.CORRECTION_REMINDER);

        return true;
    }

    /** U6: ประกาศผลการจับคู่ */
    public void notifyMatchAnnounced(
            MatchingRound round,
            Map<Application, String> matched,
            List<Application> notMatched) {

        String deadline = round.getConfirmDeadline().format(
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

        matched.forEach((app, where) ->
                notifyStudent(
                        app,
                        "ประกาศผลการจับคู่ รอบที่ "
                                + round.getRoundNo(),
                        "คุณได้รับการจับคู่กับ " + where
                                + " กรุณายืนยันหรือสละสิทธิ์ภายใน "
                                + deadline + " น.",
                        NotificationType.MATCH_ANNOUNCED));

        for (Application app : notMatched) {
            notifyStudent(
                    app,
                    "ประกาศผลการจับคู่ รอบที่ "
                            + round.getRoundNo(),
                    "รอบนี้คุณยังไม่ได้รับการจับคู่ "
                            + "สามารถแก้อันดับเพื่อรอจับคู่รอบถัดไปได้",
                    NotificationType.MATCH_ANNOUNCED);
        }
    }

    /** U6: นักศึกษายืนยันหรือสละสิทธิ์ */
    public void notifyMatchResponded(
            Match match,
            String studentName,
            String where) {

        boolean confirmed =
                match.getStatus() == MatchStatus.CONFIRMED;

        String title = confirmed
                ? "นักศึกษายืนยันสิทธิ์"
                : "นักศึกษาสละสิทธิ์";

        String message = studentName
                + (confirmed ? " ยืนยันสิทธิ์ " : " สละสิทธิ์ ")
                + where
                + (confirmed
                        ? ""
                        : " เหตุผล: " + match.getDeclineReason());

        for (User staff :
                userRepository.findByRoleAndActiveTrue(Role.STAFF)) {

            notificationRepository.save(new Notification(
                    staff.getId(),
                    title,
                    message,
                    NotificationType.MATCH_RESPONDED,
                    match.getApplicationId()));
        }
    }

    /** U6: เจ้าหน้าที่ยกเลิกผลการจับคู่ */
    public void notifyMatchCancelled(
            Application app,
            String where) {

        notifyStudent(
                app,
                "ผลการจับคู่ถูกยกเลิก",
                "เจ้าหน้าที่ยกเลิกผลการจับคู่กับ "
                        + where
                        + " กรุณาติดต่อเจ้าหน้าที่ฝ่ายสหกิจศึกษา",
                NotificationType.MATCH_CANCELLED);
    }

    /** U7: แจ้งเมื่อเจ้าหน้าที่แก้ข้อมูลการฝึกงาน */
    public void notifyInternshipUpdated(Internship internship) {
        notifyInternshipPeople(
                internship,
                "ข้อมูลการฝึกงานถูกแก้ไข",
                "เจ้าหน้าที่แก้ไขวันที่หรือข้อมูลผู้ดูแลการฝึกงานของคุณ",
                NotificationType.INTERNSHIP_UPDATED);
    }

    /** U7: แจ้งเมื่อสถานะการฝึกงานเปลี่ยน */
    public void notifyInternshipStatusChanged(
            Internship internship) {

        String message = switch (internship.getStatus()) {
            case NOT_STARTED ->
                    "การฝึกงานอยู่ในสถานะยังไม่เริ่ม";

            case IN_PROGRESS ->
                    "การฝึกงานเริ่มต้นแล้ว";

            case COMPLETED ->
                    "การฝึกงานเสร็จสิ้นแล้ว";

            case TERMINATED ->
                    "การฝึกงานถูกยุติก่อนกำหนด"
                            + (internship.getTerminatedReason() == null
                                    ? ""
                                    : " เหตุผล: "
                                            + internship.getTerminatedReason());
        };

        notifyInternshipPeople(
                internship,
                "สถานะการฝึกงานเปลี่ยนแปลง",
                message,
                NotificationType.INTERNSHIP_STATUS_CHANGED);
    }

    /** แจ้งนักศึกษาและอาจารย์ที่ปรึกษา */
    private void notifyInternshipPeople(
            Internship internship,
            String title,
            String message,
            NotificationType type) {

        studentRepository.findById(internship.getStudentId())
                .ifPresent(student -> {
                    notificationRepository.save(new Notification(
                            student.getUserId(),
                            title,
                            message,
                            type,
                            null));

                    if (student.getAdvisorId() != null) {
                        userRepository.findById(student.getAdvisorId())
                                .filter(advisor ->
                                        advisor.getRole() == Role.ADVISOR
                                                && !Boolean.FALSE.equals(
                                                        advisor.getActive()))
                                .ifPresent(advisor ->
                                        notificationRepository.save(
                                                new Notification(
                                                        advisor.getId(),
                                                        title,
                                                        message,
                                                        type,
                                                        null)));
                    }

                    userRepository.findById(student.getUserId())
                            .filter(user ->
                                    user.getEmail() != null
                                            && !user.getEmail().isBlank())
                            .ifPresent(user ->
                                    sendEmailAfterCommit(
                                            user.getEmail(),
                                            title,
                                            message));
                });
    }

    /** อ่านรายการแจ้งเตือน */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(
            Long userId) {

        User user = guard.require(userId);

        return notificationRepository
                .findByUserIdOrderByCreatedAtDescIdDesc(user.getId())
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    /** ทำเครื่องหมายว่าอ่านแล้ว */
    public NotificationResponse markAsRead(
            Long notificationId,
            Long userId) {

        User user = guard.require(userId);

        Notification notification =
                notificationRepository.findById(notificationId)
                        .orElseThrow(() ->
                                ApiException.notFound(
                                        "ไม่พบการแจ้งเตือน"));

        if (!notification.getUserId().equals(user.getId())) {
            throw ApiException.forbidden();
        }

        notification.markAsRead();

        return NotificationResponse.from(
                notificationRepository.save(notification));
    }

    /** แจ้งนักศึกษาเกี่ยวกับคำร้อง */
    private void notifyStudent(
            Application app,
            String title,
            String message,
            NotificationType type) {

        studentRepository.findById(app.getStudentId())
                .map(Student::getUserId)
                .ifPresent(studentUserId -> {
                    save(
                            studentUserId,
                            title,
                            message,
                            type,
                            app);

                    userRepository.findById(studentUserId)
                            .filter(user ->
                                    user.getEmail() != null
                                            && !user.getEmail().isBlank())
                            .ifPresent(user ->
                                    sendEmailAfterCommit(
                                            user.getEmail(),
                                            title,
                                            "คำร้องเลขที่ "
                                                    + app.getId()
                                                    + ": "
                                                    + message));
                });
    }

    /** ส่งอีเมลหลังบันทึกข้อมูลสำเร็จ */
    void sendEmailAfterCommit(
            String to,
            String subject,
            String body) {

        Runnable send = () -> {
            try {
                emailNotifier.send(to, subject, body);
            } catch (RuntimeException exception) {
                log.warn(
                        "[EMAIL-FAILED] to={} subject={} reason={}",
                        to,
                        subject,
                        exception.getMessage());
            }
        };

        if (TransactionSynchronizationManager
                .isSynchronizationActive()) {

            TransactionSynchronizationManager
                    .registerSynchronization(
                            new TransactionSynchronization() {
                                @Override
                                public void afterCommit() {
                                    send.run();
                                }
                            });

        } else {
            send.run();
        }
    }

    /** บันทึกการแจ้งเตือนที่เกี่ยวข้องกับใบสมัคร */
    private void save(
            Long userId,
            String title,
            String message,
            NotificationType type,
            Application app) {

        notificationRepository.save(new Notification(
                userId,
                title,
                message,
                type,
                app.getId()));
    }
}