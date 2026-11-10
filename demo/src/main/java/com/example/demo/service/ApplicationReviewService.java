package com.example.demo.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dao.AnswerSnapshotRepository;
import com.example.demo.dao.ApplicationRepository;
import com.example.demo.dao.ApprovalRepository;
import com.example.demo.dao.DocumentRepository;
import com.example.demo.dao.FormFieldRepository;
import com.example.demo.dao.FormResponseRepository;
import com.example.demo.dao.FormTemplateRepository;
import com.example.demo.dao.StudentRepository;
import com.example.demo.dao.UserRepository;
import com.example.demo.dto.AnswerResponse;
import com.example.demo.dto.ApplicationDetailResponse;
import com.example.demo.dto.ApplicationReviewResponse;
import com.example.demo.dto.ApprovalResponse;
import com.example.demo.dto.DocumentResponse;
import com.example.demo.entity.AnswerSnapshot;
import com.example.demo.entity.Application;
import com.example.demo.entity.ApplicationStatus;
import com.example.demo.entity.Approval;
import com.example.demo.entity.ApprovalDecision;
import com.example.demo.entity.Document;
import com.example.demo.entity.FieldType;
import com.example.demo.entity.FormField;
import com.example.demo.entity.FormResponse;
import com.example.demo.entity.FormTemplate;
import com.example.demo.entity.Role;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;

/** U5: เจ้าหน้าที่ / อาจารย์ ดูคำร้องที่นักศึกษายื่นเข้ามา (อาจารย์เห็นเฉพาะนักศึกษาที่ตนเป็นอาจารย์ที่ปรึกษา) */
@Service
@Transactional(readOnly = true)
public class ApplicationReviewService {

    /** รอตรวจนานเกินกี่วันถึงขึ้นป้าย "รอนาน" */
    public static final int OVERDUE_DAYS = 3;
    /** แบบฟอร์มจะหมดเขตภายในกี่วันถึงขึ้นป้าย "ใกล้หมดเขต" */
    public static final int DEADLINE_SOON_DAYS = 2;
    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ApplicationRepository applicationRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final FormTemplateRepository templateRepository;
    private final FormFieldRepository fieldRepository;
    private final FormResponseRepository responseRepository;
    private final ApprovalRepository approvalRepository;
    private final AnswerSnapshotRepository snapshotRepository;
    private final AccessGuard guard;
    private final Clock clock;

    public ApplicationReviewService(ApplicationRepository applicationRepository, StudentRepository studentRepository,
            UserRepository userRepository, DocumentRepository documentRepository,
            FormTemplateRepository templateRepository, FormFieldRepository fieldRepository,
            FormResponseRepository responseRepository, ApprovalRepository approvalRepository,
            AnswerSnapshotRepository snapshotRepository, AccessGuard guard, Clock clock) {
        this.applicationRepository = applicationRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.templateRepository = templateRepository;
        this.fieldRepository = fieldRepository;
        this.responseRepository = responseRepository;
        this.approvalRepository = approvalRepository;
        this.snapshotRepository = snapshotRepository;
        this.guard = guard;
        this.clock = clock;
    }

    public List<ApplicationReviewResponse> getByStatus(ApplicationStatus status, Long userId) {
        User viewer = guard.require(userId, Role.STAFF, Role.ADVISOR, Role.ADMIN);
        if (status == null) {
            throw ApiException.badRequest("กรุณาระบุสถานะคำร้อง");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        Map<Long, Optional<FormTemplate>> forms = new HashMap<>();
        return applicationRepository.findByStatusOrderBySubmittedAtAsc(status).stream().map(app -> {
            Student student = studentRepository.findById(app.getStudentId()).orElse(null);
            if (!canSee(viewer, student)) {
                return null;
            }
            List<Approval> history = approvalRepository.findByApplicationIdOrderByIdAsc(app.getId());
            Approval last = history.isEmpty() ? null : history.get(history.size() - 1);
            String reviewedBy = last == null ? null
                    : userRepository.findById(last.getApproverId()).map(User::getFullName).orElse(null);
            LocalDateTime deadline = forms.computeIfAbsent(app.getFormTemplateId(), templateRepository::findById)
                    .map(FormTemplate::getDeadline).orElse(null);
            return new ApplicationReviewResponse(app.getId(), studentCode(student), studentName(student),
                    app.getStatus(), app.getSubmittedAt(), documents(app.getId()), reviewedBy,
                    last == null ? null : last.getDecidedAt(),
                    attention(app.getStatus(), app.getSubmittedAt(), deadline, now));
        }).filter(Objects::nonNull)
                // ป้ายเตือนขึ้นก่อน (เรียงแบบ stable: ภายในกลุ่มยังเรียงตามเวลาที่ส่ง)
                .sorted(Comparator.comparingInt(r -> attentionRank(r.attention())))
                .toList();
    }

    /**
     * U5: ป้ายเตือนของคำร้องที่ยังรอผู้ตรวจ (SUBMITTED / UNDER_REVIEW)
     * DEADLINE_SOON = แบบฟอร์มจะหมดเขตภายใน 2 วัน (ถ้าต้องตีกลับ นักศึกษาจะเหลือเวลาแก้น้อย)
     * OVERDUE = ส่งเข้ามานานเกิน 3 วันแล้วยังไม่มีผล
     */
    static String attention(ApplicationStatus status, LocalDateTime submittedAt, LocalDateTime deadline,
            LocalDateTime now) {
        if (status != ApplicationStatus.SUBMITTED && status != ApplicationStatus.UNDER_REVIEW) {
            return null;
        }
        if (deadline != null && deadline.isAfter(now) && !deadline.isAfter(now.plusDays(DEADLINE_SOON_DAYS))) {
            return ApplicationReviewResponse.DEADLINE_SOON;
        }
        if (submittedAt != null && !submittedAt.isAfter(now.minusDays(OVERDUE_DAYS))) {
            return ApplicationReviewResponse.OVERDUE;
        }
        return null;
    }

    private static int attentionRank(String attention) {
        if (ApplicationReviewResponse.DEADLINE_SOON.equals(attention)) return 0;
        if (ApplicationReviewResponse.OVERDUE.equals(attention)) return 1;
        return 2;
    }

    /** U5: จำนวนคำร้องทุกสถานะ (อาจารย์นับเฉพาะนักศึกษาที่ตนดูแล) สำหรับแสดงบนแท็บ */
    public Map<ApplicationStatus, Long> getSummary(Long userId) {
        User viewer = guard.require(userId, Role.STAFF, Role.ADVISOR, Role.ADMIN);
        Map<ApplicationStatus, Long> counts = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus status : ApplicationStatus.values()) {
            counts.put(status, 0L);
        }
        if (viewer.getRole() != Role.ADVISOR) {
            for (ApplicationStatus status : ApplicationStatus.values()) {
                counts.put(status, applicationRepository.countByStatus(status));
            }
            return counts;
        }
        Map<Long, Boolean> visible = new HashMap<>();
        for (Application app : applicationRepository.findAll()) {
            boolean mine = visible.computeIfAbsent(app.getStudentId(),
                    sid -> canSee(viewer, studentRepository.findById(sid).orElse(null)));
            if (mine) {
                counts.merge(app.getStatus(), 1L, Long::sum);
            }
        }
        return counts;
    }

    /**
     * U5: ส่งออกรายการคำร้องเป็น CSV (เปิดใน Excel ได้ภาษาไทยไม่เพี้ยน เพราะขึ้นต้นด้วย UTF-8 BOM)
     * status = null → ทุกสถานะ ยกเว้นฉบับร่าง
     */
    public String exportCsv(ApplicationStatus status, Long userId) {
        List<ApplicationStatus> statuses = status != null ? List.of(status)
                : Arrays.stream(ApplicationStatus.values())
                        .filter(s -> s != ApplicationStatus.DRAFT).toList();
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("เลขที่คำร้อง,รหัสนักศึกษา,ชื่อ-นามสกุล,สถานะ,วันที่ส่ง,ผู้พิจารณาล่าสุด,วันที่พิจารณา,ไฟล์แนบ,หมายเหตุ\r\n");
        for (ApplicationStatus s : statuses) {
            for (ApplicationReviewResponse r : getByStatus(s, userId)) {
                csv.append(r.id()).append(',')
                        .append(csvCell(r.studentId())).append(',')
                        .append(csvCell(r.studentName())).append(',')
                        .append(r.status()).append(',')
                        .append(r.submittedAt() == null ? "" : r.submittedAt().format(CSV_TIME)).append(',')
                        .append(csvCell(r.reviewedBy())).append(',')
                        .append(r.reviewedAt() == null ? "" : r.reviewedAt().format(CSV_TIME)).append(',')
                        .append(r.documents() == null ? 0 : r.documents().size()).append(',')
                        .append(r.attention() == null ? "" : r.attention())
                        .append("\r\n");
            }
        }
        return csv.toString();
    }

    /** ใส่เครื่องหมายคำพูดเมื่อจำเป็น และกันสูตร Excel (ค่าที่ขึ้นต้นด้วย = + - @) */
    static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String v = value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    public ApplicationDetailResponse getDetail(Long applicationId, Long userId) {
        User viewer = guard.require(userId, Role.STAFF, Role.ADVISOR, Role.ADMIN);
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> ApiException.notFound("ไม่พบคำร้อง"));
        Student student = studentRepository.findById(app.getStudentId()).orElse(null);
        if (!canSee(viewer, student)) {
            throw new ApiException(403, "FORBIDDEN", "คุณไม่ได้เป็นอาจารย์ที่ปรึกษาของนักศึกษาเจ้าของคำร้องนี้");
        }
        User studentUser = student == null ? null : userRepository.findById(student.getUserId()).orElse(null);
        String formName = templateRepository.findById(app.getFormTemplateId())
                .map(FormTemplate::getName).orElse(null);

        List<FormField> fields = fieldRepository.findByFormTemplateIdOrderByDisplayOrderAsc(app.getFormTemplateId());
        Map<Long, String> values = responseRepository.findByApplicationId(app.getId()).stream()
                .filter(r -> r.getValue() != null)
                .collect(Collectors.toMap(FormResponse::getFormFieldId, FormResponse::getValue, (x, y) -> y));
        List<Document> docs = documentRepository.findByApplicationId(app.getId());
        Long firstFileField = fields.stream().filter(f -> f.getFieldType() == FieldType.FILE)
                .map(FormField::getId).findFirst().orElse(null);
        List<Approval> history = approvalRepository.findByApplicationIdOrderByIdAsc(app.getId());
        Map<Long, String> before = valuesBeforeLastReturn(app, history);
        // ทุกช่องตามลำดับในแบบฟอร์ม · ช่องไฟล์แสดงชื่อไฟล์ที่แนบ (ไฟล์ที่ไม่ระบุช่อง = ช่องไฟล์ช่องแรก)
        List<AnswerResponse> answers = fields.stream().map(f -> {
            if (f.getFieldType() == FieldType.FILE) {
                String names = docs.stream()
                        .filter(d -> f.getId().equals(d.getFormFieldId())
                                || (d.getFormFieldId() == null && f.getId().equals(firstFileField)))
                        .map(Document::getFileName).collect(Collectors.joining(", "));
                return new AnswerResponse(f.getId(), f.getLabel(), names.isEmpty() ? null : names, null);
            }
            String value = values.get(f.getId());
            return new AnswerResponse(f.getId(), f.getLabel(), value, changedFrom(before, f.getId(), value));
        }).toList();

        List<ApprovalResponse> approvals = history.stream()
                .map(a -> new ApprovalResponse(a.getId(), a.getApplicationId(), a.getApprovalLevel(),
                        a.getDecision(), userRepository.findById(a.getApproverId()).map(User::getFullName).orElse(null),
                        a.getComment(), a.getDecidedAt(), app.getStatus()))
                .toList();

        return new ApplicationDetailResponse(app.getId(), studentCode(student),
                studentUser == null ? null : studentUser.getFullName(),
                studentUser == null ? null : studentUser.getEmail(),
                student == null ? null : student.getFaculty(),
                student == null ? null : student.getMajor(),
                student == null ? null : student.getGpa(),
                formName, app.getStatus(), app.getSubmittedAt(), answers, documents(app.getId()), approvals);
    }

    /**
     * U5: คำตอบ ณ ตอนตีกลับครั้งล่าสุด (fieldId → ค่าเดิม)
     * ใช้เฉพาะเมื่อนักศึกษาส่งฉบับแก้กลับมาแล้ว (ยังรออยู่ที่นักศึกษา = ยังไม่มีอะไรให้เทียบ)
     */
    private Map<Long, String> valuesBeforeLastReturn(Application app, List<Approval> history) {
        if (app.getStatus() == ApplicationStatus.NEED_CORRECTION || app.getStatus() == ApplicationStatus.DRAFT) {
            return Map.of();
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            Approval a = history.get(i);
            if (a.getDecision() == ApprovalDecision.RETURN) {
                Map<Long, String> values = new HashMap<>();
                for (AnswerSnapshot snap : snapshotRepository.findByApprovalId(a.getId())) {
                    values.put(snap.getFormFieldId(), snap.getValue());
                }
                return values;
            }
        }
        return Map.of();
    }

    /** คืนค่าเดิมเมื่อช่องนี้ถูกแก้หลังตีกลับ · ไม่ได้แก้ / ไม่เคยถูกตีกลับ → null */
    static String changedFrom(Map<Long, String> before, Long fieldId, String current) {
        if (before.isEmpty()) {
            return null;
        }
        String old = before.get(fieldId); // ไม่มีในสำเนา = ตอนตีกลับช่องนี้ยังว่าง
        String a = old == null ? "" : old.trim();
        String b = current == null ? "" : current.trim();
        return a.equals(b) ? null : (old == null ? "" : old);
    }

    /** เจ้าหน้าที่ / ผู้ดูแลระบบเห็นทุกคำร้อง · อาจารย์เห็นเฉพาะนักศึกษาที่ตนเป็นอาจารย์ที่ปรึกษา */
    static boolean canSee(User viewer, Student student) {
        if (viewer.getRole() != Role.ADVISOR) {
            return true;
        }
        return student != null && viewer.getId().equals(student.getAdvisorId());
    }

    private List<DocumentResponse> documents(Long applicationId) {
        return documentRepository.findByApplicationId(applicationId).stream().map(DocumentResponse::from).toList();
    }

    private String studentCode(Student student) {
        return student == null ? null : student.getStudentId();
    }

    private String studentName(Student student) {
        if (student == null) return null;
        return userRepository.findById(student.getUserId()).map(User::getFullName).orElse(null);
    }
}
