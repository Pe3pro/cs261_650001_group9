package com.example.demo.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dao.ApplicationRepository;
import com.example.demo.dao.InternshipRepository;
import com.example.demo.dao.MatchRepository;
import com.example.demo.dao.StudentRepository;
import com.example.demo.dto.InternshipRequest;
import com.example.demo.dto.InternshipResponse;
import com.example.demo.entity.Application;
import com.example.demo.entity.HistoryType;
import com.example.demo.entity.Internship;
import com.example.demo.entity.InternshipStatus;
import com.example.demo.entity.Match;
import com.example.demo.entity.MatchStatus;
import com.example.demo.entity.Role;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;
import com.example.demo.event.MatchConfirmedEvent;

@Service
@Transactional
public class InternshipService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    private final InternshipRepository internshipRepository;
    private final MatchRepository matchRepository;
    private final ApplicationRepository applicationRepository;
    private final StudentRepository studentRepository;
    private final StatusHistoryService statusHistoryService;
    private final NotificationService notificationService;
    private final AccessGuard guard;
    private final Clock clock;

    public InternshipService(
            InternshipRepository internshipRepository,
            MatchRepository matchRepository,
            ApplicationRepository applicationRepository,
            StudentRepository studentRepository,
            StatusHistoryService statusHistoryService,
            NotificationService notificationService,
            AccessGuard guard,
            Clock clock) {

        this.internshipRepository = internshipRepository;
        this.matchRepository = matchRepository;
        this.applicationRepository = applicationRepository;
        this.studentRepository = studentRepository;
        this.statusHistoryService = statusHistoryService;
        this.notificationService = notificationService;
        this.guard = guard;
        this.clock = clock;
    }

    /**
     * สร้างข้อมูลการฝึกงานเมื่อผลจับคู่ได้รับการยืนยัน
     * ถ้ามีข้อมูลของ match นี้แล้วจะคืนรายการเดิม
     */
    public InternshipResponse createFromMatch(
            Long matchId) {

        return createFromMatch(
                matchId,
                null);
    }

    public InternshipResponse createFromMatch(
            Long matchId,
            Long changedBy) {

        if (matchId == null) {
            throw ApiException.badRequest("กรุณาระบุผลการจับคู่");
        }

        Internship existing =
                internshipRepository.findByMatchId(matchId).orElse(null);

        if (existing != null) {
            return InternshipResponse.from(existing);
        }

        Match match = matchRepository.findById(matchId)
                .orElseThrow(() ->
                        ApiException.notFound("ไม่พบผลการจับคู่"));

        if (match.getStatus() != MatchStatus.CONFIRMED) {
            throw new ApiException(
                    409,
                    "INVALID_TRANSITION",
                    "สร้างข้อมูลการฝึกงานได้เฉพาะผลจับคู่ที่ยืนยันแล้ว");
        }

        Long effectiveChangedBy =
                changedBy != null
                        ? changedBy
                        : match.getMatchedBy();

        Application application =
                applicationRepository.findById(match.getApplicationId())
                        .orElseThrow(() ->
                                ApiException.notFound("ไม่พบใบสมัคร"));

        LocalDateTime now = LocalDateTime.now(clock);

        Internship internship = new Internship(
                match.getId(),
                application.getStudentId(),
                changedBy,
                now);

        internship = internshipRepository.save(internship);

        statusHistoryService.record(
                application.getStudentId(),
                HistoryType.INTERNSHIP,
                null,
                InternshipStatus.NOT_STARTED.name(),
                changedBy,
                null,
                now);

        return InternshipResponse.from(internship);
    }

    /** อ่านข้อมูลการฝึกงานตามสิทธิ์ผู้ใช้งาน */
    /**
     * Receives MatchConfirmedEvent from U6.
     * An exception is propagated to roll back the confirmation.
     */
    @EventListener
    public void onMatchConfirmed(
            MatchConfirmedEvent event) {

        createFromMatch(event.matchId());
    }
    @Transactional(readOnly = true)
    public InternshipResponse get(
            Long userId,
            Long internshipId) {

        User user = guard.require(userId);
        Internship internship = requireInternship(internshipId);

        checkViewPermission(user, internship);

        return InternshipResponse.from(internship);
    }

    /** เจ้าหน้าที่แก้ไขวันที่และข้อมูลผู้ดูแล */
    public InternshipResponse update(
            Long userId,
            Long internshipId,
            InternshipRequest request) {

        User staff = guard.require(userId, Role.STAFF);
        Internship internship = requireInternship(internshipId);

        if (internship.isClosed()) {
            throw new ApiException(
                    409,
                    "INTERNSHIP_CLOSED",
                    "การฝึกงานนี้ปิดแล้ว ไม่สามารถแก้ไขข้อมูลได้");
        }

        validateRequest(request);

        LocalDateTime now = LocalDateTime.now(clock);

        internship.updateDetails(
                request.startDate(),
                request.endDate(),
                normalize(request.supervisorName()),
                normalize(request.supervisorEmail()),
                normalize(request.supervisorPhone()),
                staff.getId(),
                now);

        Internship saved = internshipRepository.save(internship);

        notificationService.notifyInternshipUpdated(saved);

        return InternshipResponse.from(saved);
    }

    /** NOT_STARTED → IN_PROGRESS */
    public InternshipResponse start(
            Long userId,
            Long internshipId) {

        return changeStatus(
                userId,
                internshipId,
                InternshipStatus.IN_PROGRESS,
                null);
    }

    /** IN_PROGRESS → COMPLETED */
    public InternshipResponse complete(
            Long userId,
            Long internshipId) {

        return changeStatus(
                userId,
                internshipId,
                InternshipStatus.COMPLETED,
                null);
    }

    /** NOT_STARTED/IN_PROGRESS → TERMINATED */
    public InternshipResponse terminate(
            Long userId,
            Long internshipId,
            String reason) {

        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest(
                    "กรุณากรอกเหตุผลที่ยุติการฝึกงาน");
        }

        return changeStatus(
                userId,
                internshipId,
                InternshipStatus.TERMINATED,
                reason.trim());
    }

    /** เปลี่ยนสถานะ บันทึกประวัติ และแจ้งเตือน */
    public InternshipResponse changeStatus(
            Long userId,
            Long internshipId,
            InternshipStatus targetStatus,
            String reason) {

        User staff = guard.require(userId, Role.STAFF);
        Internship internship = requireInternship(internshipId);

        InternshipStatus oldStatus = internship.getStatus();

        validateTransition(oldStatus, targetStatus);

        if (targetStatus == InternshipStatus.IN_PROGRESS
                && !internship.infoComplete()) {

            throw new ApiException(
                    400,
                    "MISSING_INFO",
                    "กรุณากรอกข้อมูลการฝึกงานให้ครบก่อนเริ่มฝึกงาน");
        }

        if (targetStatus == InternshipStatus.TERMINATED
                && (reason == null || reason.isBlank())) {

            throw ApiException.badRequest(
                    "กรุณากรอกเหตุผลที่ยุติการฝึกงาน");
        }

        LocalDateTime now = LocalDateTime.now(clock);

        try {
            internship.changeStatus(
                    targetStatus,
                    normalize(reason),
                    staff.getId(),
                    now);
        } catch (IllegalStateException exception) {
            throw new ApiException(
                    409,
                    "INVALID_TRANSITION",
                    exception.getMessage());
        }

        Internship saved = internshipRepository.save(internship);

        statusHistoryService.record(
                saved.getStudentId(),
                HistoryType.INTERNSHIP,
                oldStatus.name(),
                targetStatus.name(),
                staff.getId(),
                normalize(reason),
                now);

        notificationService.notifyInternshipStatusChanged(saved);

        return InternshipResponse.from(saved);
    }

    /** ตรวจสอบเส้นทางเปลี่ยนสถานะ */
    public void validateTransition(
            InternshipStatus currentStatus,
            InternshipStatus targetStatus) {

        if (currentStatus == null || targetStatus == null
                || !currentStatus.canMoveTo(targetStatus)) {

            throw new ApiException(
                    409,
                    "INVALID_TRANSITION",
                    "ไม่สามารถเปลี่ยนสถานะจาก "
                            + currentStatus
                            + " เป็น "
                            + targetStatus
                            + " ได้");
        }
    }

    private Internship requireInternship(Long internshipId) {
        if (internshipId == null) {
            throw ApiException.badRequest(
                    "กรุณาระบุข้อมูลการฝึกงาน");
        }

        return internshipRepository.findById(internshipId)
                .orElseThrow(() ->
                        ApiException.notFound(
                                "ไม่พบข้อมูลการฝึกงาน"));
    }

    private void validateRequest(InternshipRequest request) {
        if (request == null) {
            throw ApiException.badRequest(
                    "กรุณากรอกข้อมูลการฝึกงาน");
        }

        if (request.startDate() != null
                && request.endDate() != null
                && !request.endDate().isAfter(request.startDate())) {

            throw new ApiException(
                    400,
                    "INVALID_DATE_RANGE",
                    "วันสิ้นสุดต้องหลังวันเริ่ม");
        }

        String email = normalize(request.supervisorEmail());

        if (email != null
                && !EMAIL_PATTERN.matcher(email).matches()) {

            throw ApiException.badRequest(
                    "รูปแบบอีเมลผู้ดูแลไม่ถูกต้อง");
        }
    }

    private void checkViewPermission(
            User user,
            Internship internship) {

        if (user.getRole() == Role.STAFF) {
            return;
        }

        Student student = studentRepository
                .findById(internship.getStudentId())
                .orElseThrow(() ->
                        ApiException.notFound("ไม่พบนักศึกษา"));

        if (user.getRole() == Role.STUDENT
                && student.getUserId().equals(user.getId())) {
            return;
        }

        if (user.getRole() == Role.ADVISOR
                && student.getAdvisorId() != null
                && student.getAdvisorId().equals(user.getId())) {
            return;
        }

        throw ApiException.forbidden();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
