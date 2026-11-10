package com.example.demo.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dao.ApplicationRepository;
import com.example.demo.dao.ApprovalRepository;
import com.example.demo.dao.DocumentRepository;
import com.example.demo.dao.EligibilityRecordDao;
import com.example.demo.dao.FormFieldRepository;
import com.example.demo.dao.FormResponseRepository;
import com.example.demo.dao.FormTemplateRepository;
import com.example.demo.dao.StudentRepository;
import com.example.demo.dto.DocumentResponse;
import com.example.demo.dto.FieldErrorResponse.FieldError;
import com.example.demo.dto.FormAnswersRequest;
import com.example.demo.dto.FormFieldResponse;
import com.example.demo.dto.FormTemplateResponse;
import com.example.demo.dto.PublishedFormResponse;
import com.example.demo.dto.RequestCreateRequest;
import com.example.demo.dto.RequestResponse;
import com.example.demo.dto.StudentProfileResponse;
import com.example.demo.entity.Application;
import com.example.demo.entity.ApplicationStatus;
import com.example.demo.entity.Approval;
import com.example.demo.entity.ApprovalDecision;
import com.example.demo.entity.Document;
import com.example.demo.entity.EligibilityRecord;
import com.example.demo.entity.FieldType;
import com.example.demo.entity.FormField;
import com.example.demo.entity.FormResponse;
import com.example.demo.entity.FormStatus;
import com.example.demo.entity.FormTemplate;
import com.example.demo.entity.Role;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;

/**
 * U3: นักศึกษาสร้าง กรอก และยื่นคำร้องขอฝึกสหกิจศึกษา
 * เวลาปัจจุบันอ่านจาก Clock ของ server เท่านั้น (test กำหนดเวลาได้ และนักศึกษาย้อนนาฬิกาเครื่องตัวเองไม่ได้ผล)
 */
@Service
@Transactional
public class RequestService {

    static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    static final Pattern PHONE = Pattern.compile("^0\\d{8,9}$");
    static final String DUPLICATE_MESSAGE = "คุณมีคำร้องประเภทนี้ที่อยู่ระหว่างดำเนินการแล้ว";
    static final String DEADLINE_MESSAGE = "เกินระยะเวลาที่กำหนด ไม่สามารถยื่นคำร้องได้";

    private static final Set<ApplicationStatus> ACTIVE = EnumSet.allOf(ApplicationStatus.class).stream()
            .filter(ApplicationStatus::blocksNewRequest)
            .collect(() -> EnumSet.noneOf(ApplicationStatus.class), Set::add, Set::addAll);

    private final ApplicationRepository applicationRepository;
    private final FormTemplateRepository templateRepository;
    private final FormFieldRepository fieldRepository;
    private final FormResponseRepository responseRepository;
    private final DocumentRepository documentRepository;
    private final StudentRepository studentRepository;
    private final EligibilityRecordDao eligibilityRecordDao;
    private final ApprovalRepository approvalRepository;
    private final EmailNotifier emailNotifier;
    private final NotificationService notificationService;
    private final AccessGuard guard;
    private final Clock clock;

    public RequestService(ApplicationRepository applicationRepository, FormTemplateRepository templateRepository,
            FormFieldRepository fieldRepository, FormResponseRepository responseRepository,
            DocumentRepository documentRepository, StudentRepository studentRepository,
            EligibilityRecordDao eligibilityRecordDao, ApprovalRepository approvalRepository,
            EmailNotifier emailNotifier, NotificationService notificationService, AccessGuard guard, Clock clock) {
        this.applicationRepository = applicationRepository;
        this.templateRepository = templateRepository;
        this.fieldRepository = fieldRepository;
        this.responseRepository = responseRepository;
        this.documentRepository = documentRepository;
        this.studentRepository = studentRepository;
        this.eligibilityRecordDao = eligibilityRecordDao;
        this.approvalRepository = approvalRepository;
        this.emailNotifier = emailNotifier;
        this.notificationService = notificationService;
        this.guard = guard;
        this.clock = clock;
    }

    /** GET /api/forms/published: แบบฟอร์มที่เผยแพร่ล่าสุด */
    @Transactional(readOnly = true)
    public PublishedFormResponse getPublishedForm(Long userId) {
        guard.require(userId);
        FormTemplate form = templateRepository.findByStatus(FormStatus.PUBLISHED).stream()
                .max(Comparator.comparing(FormTemplate::getVersion))
                .orElseThrow(() -> ApiException.notFound("ยังไม่มีแบบฟอร์มที่เปิดให้ยื่น"));
        LocalDateTime now = now();
        List<FormFieldResponse> fields = fieldRepository.findByFormTemplateIdOrderByDisplayOrderAsc(form.getId())
                .stream().map(FormFieldResponse::from).toList();
        return new PublishedFormResponse(FormTemplateResponse.from(form, fields), now, isPastDeadline(form, now));
    }

    /** POST /api/requests: สร้างคำร้องสถานะ DRAFT */
    public RequestResponse create(RequestCreateRequest request, Long userId) {
        Student student = currentStudent(userId);
        requireEligible(student);
        FormTemplate form = request == null || request.formTemplateId() == null
                ? templateRepository.findByStatus(FormStatus.PUBLISHED).stream()
                        .max(Comparator.comparing(FormTemplate::getVersion))
                        .orElseThrow(() -> ApiException.notFound("ยังไม่มีแบบฟอร์มที่เปิดให้ยื่น"))
                : templateRepository.findById(request.formTemplateId())
                        .orElseThrow(() -> ApiException.notFound("ไม่พบแบบฟอร์ม"));
        if (form.getStatus() != FormStatus.PUBLISHED) {
            throw new ApiException(409, "FORM_NOT_PUBLISHED", "แบบฟอร์มนี้ยังไม่เปิดให้ยื่น");
        }
        // ร่าง (DRAFT) ของแบบฟอร์มฉบับเก่าที่ปิดไปแล้วไม่นับเป็นคำร้องค้าง เพื่อไม่ให้นักศึกษาติดอยู่กับร่างที่ส่งไม่ได้
        final Long formId = form.getId();
        boolean hasOpen = applicationRepository.findByStudentIdOrderByIdDesc(student.getId()).stream()
                .anyMatch(a -> ACTIVE.contains(a.getStatus())
                        && !(a.getStatus() == ApplicationStatus.DRAFT && !a.getFormTemplateId().equals(formId)));
        if (hasOpen) {
            throw new ApiException(409, "DUPLICATE_REQUEST", DUPLICATE_MESSAGE);
        }
        Application app = applicationRepository.save(
                new Application(student.getId(), form.getId(), ApplicationStatus.DRAFT));
        return toResponse(app, student);
    }

    /** PUT /api/requests/{id}/form: บันทึกคำตอบ (แก้ค่าเดิม ไม่เพิ่มแถวซ้ำ) */
    public RequestResponse saveAnswers(Long id, FormAnswersRequest request, Long userId) {
        Student student = currentStudent(userId);
        Application app = ownedApplication(id, student);
        requireEditable(app);
        List<FormField> fields = fieldRepository.findByFormTemplateIdOrderByDisplayOrderAsc(app.getFormTemplateId());
        Map<String, String> answers = request == null || request.answers() == null ? Map.of() : request.answers();
        List<FieldError> errors = validateAnswers(fields, answers);
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        for (FormField field : fields) {
            if (field.getFieldType() == FieldType.FILE || !answers.containsKey(field.getFieldName())) {
                continue;
            }
            String value = trim(answers.get(field.getFieldName()));
            FormResponse response = responseRepository
                    .findByApplicationIdAndFormFieldId(app.getId(), field.getId())
                    .orElseGet(() -> new FormResponse(app.getId(), field.getId(), value));
            response.setValue(value);
            responseRepository.save(response);
        }
        return toResponse(applicationRepository.saveAndFlush(touch(app)), student); // updated_at เปลี่ยนเมื่อแก้คำตอบ
    }

    /** POST /api/requests/{id}/submit: DRAFT / NEED_CORRECTION → SUBMITTED */
    public RequestResponse submit(Long id, Long userId) {
        Student student = currentStudent(userId);
        Application app = ownedApplication(id, student);
        if (!app.getStatus().isEditableByStudent()) {
            throw new ApiException(409, "INVALID_STATUS", "คำร้องนี้ถูกส่งแล้ว ไม่สามารถส่งซ้ำได้");
        }
        FormTemplate form = templateRepository.findById(app.getFormTemplateId())
                .orElseThrow(() -> ApiException.notFound("ไม่พบแบบฟอร์ม"));
        LocalDateTime now = now();
        if (isPastDeadline(form, now)) {
            throw new ApiException(409, "DEADLINE_PASSED", DEADLINE_MESSAGE);
        }
        List<FormField> fields = fieldRepository.findByFormTemplateIdOrderByDisplayOrderAsc(app.getFormTemplateId());
        Map<String, String> saved = savedAnswers(app.getId(), fields);
        List<FieldError> errors = validateAnswers(fields, saved);
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        requireDocuments(app, fields);
        boolean resubmitted = app.getStatus() == ApplicationStatus.NEED_CORRECTION;
        moveTo(app, ApplicationStatus.SUBMITTED);
        app.submit(now);
        app = applicationRepository.saveAndFlush(app); // ถ้ามีคนส่งพร้อมกัน @Version จะทำให้ครั้งที่สองได้ 409
        notificationService.notifyStaffSubmitted(app, resubmitted); // U5: แจ้งเจ้าหน้าที่ว่ามีคำร้องเข้ามา
        User user = guard.require(userId);
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            emailNotifier.send(user.getEmail(), "ได้รับคำร้องขอฝึกสหกิจศึกษาแล้ว",
                    "ระบบได้รับคำร้องเลขที่ " + app.getId() + " ของคุณแล้ว สถานะ: รอเจ้าหน้าที่ตรวจสอบ");
        }
        return toResponse(app, student);
    }

    /** GET /api/requests/{id}: เจ้าของคำร้อง หรือเจ้าหน้าที่ / อาจารย์ */
    @Transactional(readOnly = true)
    public RequestResponse get(Long id, Long userId) {
        User user = guard.require(userId);
        Application app = applicationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("ไม่พบคำร้อง"));
        if (user.getRole() == Role.STUDENT) {
            Student student = studentOf(user);
            if (!app.getStudentId().equals(student.getId())) {
                throw ApiException.forbidden();
            }
            return toResponse(app, student);
        }
        return toResponse(app, studentRepository.findById(app.getStudentId()).orElse(null));
    }

    /** GET /api/students/me: ข้อมูลนักศึกษาที่ login สำหรับเติมหน้ายื่นคำร้อง */
    @Transactional(readOnly = true)
    public StudentProfileResponse getProfile(Long userId) {
        User user = guard.require(userId, Role.STUDENT);
        Student student = studentOf(user);
        return new StudentProfileResponse(student.getStudentId(), user.getFullName(), user.getEmail(),
                student.getFaculty(), student.getMajor(), student.getGpa());
    }

    /** GET /api/requests/me: คำร้องทั้งหมดของนักศึกษาที่ login ฉบับล่าสุดก่อน */
    @Transactional(readOnly = true)
    public List<RequestResponse> getMine(Long userId) {
        Student student = currentStudent(userId);
        return applicationRepository.findByStudentIdOrderByIdDesc(student.getId()).stream()
                .map(app -> toResponse(app, student)).toList();
    }

    /** เปลี่ยนสถานะตามเส้นทางที่อนุญาตเท่านั้น (เช่น DRAFT → APPROVED โดยตรงถูกปฏิเสธ) */
    public void moveTo(Application app, ApplicationStatus target) {
        if (!app.getStatus().canMoveTo(target)) {
            throw new ApiException(409, "INVALID_STATUS",
                    "ไม่สามารถเปลี่ยนสถานะจาก " + app.getStatus() + " เป็น " + target + " ได้");
        }
        app.changeStatus(target);
    }

    /**
     * ไฟล์แนบ: ต้องมีอย่างน้อย 1 ไฟล์ (Transcript) เหมือนเดิม
     * และ U5: ช่องชนิดไฟล์ที่บังคับทุกช่องต้องมีไฟล์ · ไฟล์ที่แนบแบบไม่ระบุช่อง (ระบบเดิม) นับเป็นของช่องไฟล์ช่องแรก
     */
    void requireDocuments(Application app, List<FormField> fields) {
        List<Document> docs = documentRepository.findByApplicationId(app.getId());
        if (docs.isEmpty()) {
            throw new ApiException(400, "MISSING_DOCUMENT", "กรุณาแนบไฟล์ Transcript (PDF) ก่อนส่งคำร้อง");
        }
        List<FormField> fileFields = fields.stream().filter(f -> f.getFieldType() == FieldType.FILE).toList();
        for (int i = 0; i < fileFields.size(); i++) {
            FormField field = fileFields.get(i);
            if (!Boolean.TRUE.equals(field.getRequired())) {
                continue;
            }
            boolean first = i == 0;
            boolean attached = docs.stream().anyMatch(d -> field.getId().equals(d.getFormFieldId())
                    || (first && d.getFormFieldId() == null));
            if (!attached) {
                throw new ApiException(400, "MISSING_DOCUMENT", "กรุณาแนบไฟล์ " + field.getLabel() + " ก่อนส่งคำร้อง");
            }
        }
    }

    /** U5: ช่องชนิดไฟล์ที่นักศึกษาแนบไฟล์ให้ ต้องอยู่ในแบบฟอร์มของคำร้องนี้ */
    FormField fileField(Application app, Long fieldId) {
        FormField field = fieldRepository.findById(fieldId)
                .filter(f -> f.getFormTemplateId().equals(app.getFormTemplateId()))
                .orElseThrow(() -> ApiException.badRequest("ไม่พบช่องแนบไฟล์นี้ในแบบฟอร์ม"));
        if (field.getFieldType() != FieldType.FILE) {
            throw ApiException.badRequest("ช่องนี้ไม่ใช่ช่องแนบไฟล์");
        }
        return field;
    }

    /** ตรวจคำตอบทุกช่อง: ช่องบังคับต้องไม่ว่าง (ตัดช่องว่างก่อน) · EMAIL / PHONE ต้องถูกรูปแบบ */
    static List<FieldError> validateAnswers(List<FormField> fields, Map<String, String> answers) {
        List<FieldError> errors = new ArrayList<>();
        for (FormField field : fields) {
            if (field.getFieldType() == FieldType.FILE) {
                continue; // ไฟล์ตรวจตอน submit จากตาราง documents
            }
            String value = trim(answers.get(field.getFieldName()));
            if (value.isEmpty()) {
                if (Boolean.TRUE.equals(field.getRequired())) {
                    errors.add(new FieldError(field.getFieldName(), field.getLabel(), "กรุณากรอก" + field.getLabel()));
                }
                continue;
            }
            if (field.getFieldType() == FieldType.EMAIL && !EMAIL.matcher(value).matches()) {
                errors.add(new FieldError(field.getFieldName(), field.getLabel(), "รูปแบบอีเมลไม่ถูกต้อง"));
            }
            if (field.getFieldType() == FieldType.PHONE && !PHONE.matcher(value.replaceAll("[\\s-]", "")).matches()) {
                errors.add(new FieldError(field.getFieldName(), field.getLabel(), "รูปแบบเบอร์โทรศัพท์ไม่ถูกต้อง"));
            }
        }
        return errors;
    }

    // ---------- ตัวช่วย ----------

    Student currentStudent(Long userId) {
        User user = guard.require(userId, Role.STUDENT);
        return studentOf(user);
    }

    private Student studentOf(User user) {
        return studentRepository.findByUserId(user.getId()).orElseThrow(ApiException::forbidden);
    }

    /** ผลตรวจคุณสมบัติ (U2) ล่าสุดของนักศึกษา: ถ้ามีและไม่ผ่าน → 403 */
    private void requireEligible(Student student) {
        List<EligibilityRecord> records =
                eligibilityRecordDao.findByStudentIdOrderByCheckedAtDesc(student.getStudentId());
        if (!records.isEmpty() && !records.get(0).isEligible()) {
            throw new ApiException(403, "NOT_ELIGIBLE", "คุณยังไม่ผ่านการตรวจสอบคุณสมบัติ ไม่สามารถยื่นคำร้องได้");
        }
    }

    Application ownedApplication(Long id, Student student) {
        Application app = applicationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("ไม่พบคำร้อง"));
        if (!app.getStudentId().equals(student.getId())) {
            throw ApiException.forbidden();
        }
        return app;
    }

    static void requireEditable(Application app) {
        if (!app.getStatus().isEditableByStudent()) {
            throw new ApiException(409, "INVALID_STATUS", "คำร้องสถานะ " + app.getStatus() + " แก้ไขไม่ได้");
        }
    }

    private boolean isPastDeadline(FormTemplate form, LocalDateTime now) {
        return form.getDeadline() != null && now.isAfter(form.getDeadline());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private Map<String, String> savedAnswers(Long applicationId, List<FormField> fields) {
        Map<Long, String> byFieldId = new LinkedHashMap<>();
        responseRepository.findByApplicationId(applicationId)
                .forEach(r -> byFieldId.put(r.getFormFieldId(), r.getValue()));
        Map<String, String> answers = new LinkedHashMap<>();
        fields.forEach(f -> {
            if (byFieldId.containsKey(f.getId())) {
                answers.put(f.getFieldName(), byFieldId.get(f.getId()));
            }
        });
        return answers;
    }

    private Application touch(Application app) {
        app.markUpdated(LocalDateTime.now(clock));
        return app;
    }

    private RequestResponse toResponse(Application app, Student student) {
        FormTemplate form = templateRepository.findById(app.getFormTemplateId()).orElse(null);
        List<FormField> fields = fieldRepository.findByFormTemplateIdOrderByDisplayOrderAsc(app.getFormTemplateId());
        List<DocumentResponse> docs = documentRepository.findByApplicationId(app.getId()).stream()
                .map(DocumentResponse::from).toList();
        String reason = null;
        if (app.getStatus() == ApplicationStatus.NEED_CORRECTION) {
            List<Approval> history = approvalRepository.findByApplicationIdOrderByIdAsc(app.getId());
            for (int i = history.size() - 1; i >= 0; i--) {
                if (history.get(i).getDecision() == ApprovalDecision.RETURN) {
                    reason = history.get(i).getComment();
                    break;
                }
            }
        }
        return new RequestResponse(app.getId(), student == null ? null : student.getStudentId(),
                app.getFormTemplateId(), form == null ? null : form.getName(), app.getStatus(), app.getCreatedAt(),
                app.getSubmittedAt(), app.getUpdatedAt(), form == null ? null : form.getDeadline(),
                savedAnswers(app.getId(), fields), docs, reason);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
