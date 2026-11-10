package com.example.demo.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.example.demo.entity.FormField;
import com.example.demo.entity.Role;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;

/**
 * U5: เจ้าหน้าที่ / อาจารย์ พิจารณาคำร้อง
 * เจ้าหน้าที่ตัดสินได้เฉพาะคำร้อง SUBMITTED, อาจารย์ตัดสินได้เฉพาะคำร้อง UNDER_REVIEW
 * ของนักศึกษาที่ตนเป็นอาจารย์ที่ปรึกษาเท่านั้น
 */
@Service
@Transactional
public class ApprovalService {

    /** ข้อความนำหน้ารายชื่อช่องที่ต้องแก้ ต่อท้ายเหตุผลการตีกลับ (U3 แสดงเหตุผลนี้ให้นักศึกษาเห็น) */
    static final String FIX_FIELDS_PREFIX = "ช่องที่ต้องแก้: ";

    private final ApplicationRepository applicationRepository;
    private final ApprovalRepository approvalRepository;
    private final StudentRepository studentRepository;
    private final NotificationService notificationService;
    private final FormFieldRepository fieldRepository;
    private final FormResponseRepository responseRepository;
    private final AnswerSnapshotRepository snapshotRepository;
    private final AccessGuard guard;
    private final Clock clock;

    public ApprovalService(ApplicationRepository applicationRepository, ApprovalRepository approvalRepository,
            StudentRepository studentRepository, NotificationService notificationService,
            FormFieldRepository fieldRepository, FormResponseRepository responseRepository,
            AnswerSnapshotRepository snapshotRepository, AccessGuard guard, Clock clock) {
        this.applicationRepository = applicationRepository;
        this.approvalRepository = approvalRepository;
        this.studentRepository = studentRepository;
        this.notificationService = notificationService;
        this.fieldRepository = fieldRepository;
        this.responseRepository = responseRepository;
        this.snapshotRepository = snapshotRepository;
        this.guard = guard;
        this.clock = clock;
    }

    public ApprovalResponse decide(Long applicationId, Long userId, ApprovalRequest request) {
        User approver = guard.require(userId, Role.STAFF, Role.ADVISOR);
        if (request == null || request.decision() == null) {
            throw ApiException.badRequest("กรุณาเลือกผลการพิจารณา");
        }
        ApprovalDecision decision = request.decision();
        String comment = request.comment() == null ? null : request.comment().trim();
        if (comment != null && comment.isEmpty()) {
            comment = null;
        }
        if (decision != ApprovalDecision.APPROVE && comment == null) {
            throw ApiException.badRequest("กรุณากรอกเหตุผลเมื่อส่งกลับแก้ไขหรือปฏิเสธ");
        }

        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> ApiException.notFound("ไม่พบคำร้อง"));
        ApprovalLevel level = approver.getRole() == Role.STAFF ? ApprovalLevel.STAFF : ApprovalLevel.ADVISOR;
        ApplicationStatus expected = level == ApprovalLevel.STAFF
                ? ApplicationStatus.SUBMITTED : ApplicationStatus.UNDER_REVIEW;
        if (app.getStatus() != expected) {
            throw ApiException.conflict("คำร้องนี้ไม่ได้อยู่ในขั้นที่คุณพิจารณาได้ (สถานะปัจจุบัน: "
                    + app.getStatus() + ")");
        }
        if (level == ApprovalLevel.ADVISOR) {
            requireOwnStudent(approver, app);
        }
        if (decision == ApprovalDecision.RETURN) {
            comment = withFieldsToFix(comment, app, request.fieldIds());
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Approval approval = approvalRepository.save(
                new Approval(app.getId(), approver.getId(), level, decision, comment, now));
        if (decision == ApprovalDecision.RETURN) {
            snapshotAnswers(approval, app);
        }
        ApplicationStatus next = nextStatus(level, decision);
        app.changeStatus(next);
        applicationRepository.save(app);
        notificationService.notifyAfterDecision(app, level, decision, comment);

        return new ApprovalResponse(approval.getId(), app.getId(), level, decision, approver.getFullName(),
                comment, now, next);
    }

    /**
     * ต่อท้ายเหตุผลด้วยชื่อช่องที่ต้องแก้ (ถ้าเลือกมา) เพื่อให้นักศึกษาเห็นใน U3 และในแจ้งเตือน
     * ช่องที่ส่งมาต้องเป็นของแบบฟอร์มที่คำร้องนี้ใช้เท่านั้น
     */
    String withFieldsToFix(String comment, Application app, List<Long> fieldIds) {
        if (fieldIds == null || fieldIds.isEmpty()) {
            return comment;
        }
        Map<Long, FormField> fields = fieldRepository
                .findByFormTemplateIdOrderByDisplayOrderAsc(app.getFormTemplateId()).stream()
                .collect(Collectors.toMap(FormField::getId, Function.identity()));
        Set<Long> unique = new LinkedHashSet<>(fieldIds);
        unique.remove(null);
        if (unique.isEmpty()) {
            return comment;
        }
        if (!fields.keySet().containsAll(unique)) {
            throw ApiException.badRequest("ช่องที่เลือกให้แก้ไม่ได้อยู่ในแบบฟอร์มของคำร้องนี้");
        }
        String labels = unique.stream()
                .map(fields::get)
                .sorted((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                .map(FormField::getLabel)
                .collect(Collectors.joining(", "));
        return comment + "\n" + FIX_FIELDS_PREFIX + labels;
    }

    /** เก็บคำตอบ ณ ตอนตีกลับ เพื่อเทียบกับฉบับที่นักศึกษาแก้แล้วส่งกลับมา */
    private void snapshotAnswers(Approval approval, Application app) {
        responseRepository.findByApplicationId(app.getId()).forEach(r ->
                snapshotRepository.save(new AnswerSnapshot(approval.getId(), r.getFormFieldId(), r.getValue())));
    }

    /** อาจารย์พิจารณาได้เฉพาะคำร้องของนักศึกษาที่ตนเป็นอาจารย์ที่ปรึกษา */
    private void requireOwnStudent(User advisor, Application app) {
        Long advisorId = studentRepository.findById(app.getStudentId())
                .map(Student::getAdvisorId).orElse(null);
        if (advisorId == null || !advisorId.equals(advisor.getId())) {
            throw new ApiException(403, "FORBIDDEN", "คุณไม่ได้เป็นอาจารย์ที่ปรึกษาของนักศึกษาเจ้าของคำร้องนี้");
        }
    }

    static ApplicationStatus nextStatus(ApprovalLevel level, ApprovalDecision decision) {
        return switch (decision) {
            case APPROVE -> level == ApprovalLevel.STAFF ? ApplicationStatus.UNDER_REVIEW : ApplicationStatus.APPROVED;
            case RETURN -> ApplicationStatus.NEED_CORRECTION;
            case REJECT -> ApplicationStatus.REJECTED;
        };
    }
}
