package com.example.demo.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.dao.InternshipDao;
import com.example.demo.dao.UserRepository;
import com.example.demo.dao.WorkLogAttachmentDao;
import com.example.demo.dao.WorkLogDao;
import com.example.demo.dto.WorkLogAttachmentResponse;
import com.example.demo.dto.WorkLogDetailResponse;
import com.example.demo.dto.WorkLogFilter;
import com.example.demo.dto.WorkLogRequest;
import com.example.demo.dto.WorkLogResponse;
import com.example.demo.dto.WorkLogSummaryResponse;
import com.example.demo.entity.Internship;
import com.example.demo.entity.InternshipStatus;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.entity.WorkLog;
import com.example.demo.entity.WorkLogAttachment;
import com.example.demo.entity.WorkLogStatus;

/**
 * U8: บันทึกการทำงานของนักศึกษา (สร้าง แก้ไข ลบ ส่ง แนบไฟล์ ดูรายการ สรุป ส่งออก)
 * ตาม Sequence Diagram "นักศึกษาบันทึก แนบไฟล์ และส่งบันทึก" และ "ดูสรุปชั่วโมงและวันที่ขาดบันทึก"
 */
@Service
@Transactional
public class WorkLogService {

    public static final BigDecimal MIN_HOURS = new BigDecimal("0.5");
    public static final BigDecimal MAX_HOURS = new BigDecimal("12");
    public static final int MAX_TEXT = 2000;
    public static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    public static final int MAX_ATTACHMENTS = 5;

    /** นามสกุลไฟล์แนบที่รับ → content type ที่บันทึก (ไม่เชื่อ content type ที่ browser ส่งมา) */
    private static final Map<String, String> FILE_TYPES = Map.of(
            "pdf", "application/pdf",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png");

    private final WorkLogDao workLogDao;
    private final WorkLogAttachmentDao attachmentDao;
    private final InternshipDao internshipDao;
    private final UserRepository userDao;
    private final FileStorage fileStorage;
    private final NotificationService notificationService;
    private final AccessGuard guard;
    private final Clock clock;

    public WorkLogService(WorkLogDao workLogDao, WorkLogAttachmentDao attachmentDao, InternshipDao internshipDao,
            UserRepository userDao, FileStorage fileStorage, NotificationService notificationService,
            AccessGuard guard, Clock clock) {
        this.workLogDao = workLogDao;
        this.attachmentDao = attachmentDao;
        this.internshipDao = internshipDao;
        this.userDao = userDao;
        this.fileStorage = fileStorage;
        this.notificationService = notificationService;
        this.guard = guard;
        this.clock = clock;
    }

    /** ไฟล์ที่ดาวน์โหลด */
    public record FileContent(String fileName, String contentType, byte[] content) {
    }

    /* ====================== นักศึกษา: การฝึกงานของตนเอง ====================== */

    /** GET /api/work-logs/my-internship: การฝึกงานของนักศึกษาที่ login อยู่ (หน้าเว็บใช้หา internshipId) */
    @Transactional(readOnly = true)
    public Internship myInternship(Long studentId) {
        User student = guard.require(studentId, Role.STUDENT);
        return internshipDao.findByStudentId(student.getId())
                .orElseThrow(() -> ApiException.notFound("ยังไม่มีข้อมูลการฝึกงาน"));
    }

    /* ====================== นักศึกษา: สร้าง / แก้ไข / ลบ / ส่ง ====================== */

    /** POST /api/internships/{id}/work-logs: สร้างบันทึกฉบับร่าง · 1 วันได้ 1 บันทึก */
    public WorkLog create(Long studentId, Long internshipId, WorkLogRequest req) {
        User student = guard.require(studentId, Role.STUDENT);
        Internship internship = findInternship(internshipId);
        if (!student.getId().equals(internship.getStudentId())) {
            throw ApiException.forbidden();
        }
        requireInProgress(internship);
        BigDecimal hours = validate(internship, req);
        if (workLogDao.existsByInternshipIdAndLogDate(internshipId, req.logDate())) {
            throw duplicateDate();
        }
        WorkLog log = new WorkLog(internshipId, student.getId(), req.logDate());
        apply(log, req, hours);
        return workLogDao.save(log);
    }

    /** PUT /api/work-logs/{id}: แก้บันทึก · ได้เฉพาะ DRAFT / NEED_REVISION */
    public WorkLog update(Long studentId, Long id, WorkLogRequest req) {
        WorkLog log = ownedLog(studentId, id);
        requireEditable(log);
        Internship internship = findInternship(log.getInternshipId());
        requireInProgress(internship);
        BigDecimal hours = validate(internship, req);
        if (!req.logDate().equals(log.getLogDate())
                && workLogDao.existsByInternshipIdAndLogDate(log.getInternshipId(), req.logDate())) {
            throw duplicateDate();
        }
        log.setLogDate(req.logDate());
        apply(log, req, hours);
        return workLogDao.save(log);
    }

    /** DELETE /api/work-logs/{id}: ลบบันทึก · ได้เฉพาะ DRAFT (ลบไฟล์แนบด้วย) */
    public void delete(Long studentId, Long id) {
        WorkLog log = ownedLog(studentId, id);
        if (log.getStatus() != WorkLogStatus.DRAFT) {
            throw ApiException.conflict("ลบได้เฉพาะบันทึกฉบับร่าง");
        }
        List<WorkLogAttachment> files = attachmentDao.findByWorkLogId(log.getId());
        attachmentDao.deleteAll(files);
        workLogDao.delete(log);
        files.forEach(f -> fileStorage.delete(f.getFilePath()));
    }

    /** POST /api/work-logs/{id}/submit: ส่งให้อาจารย์ตรวจ (DRAFT / NEED_REVISION → SUBMITTED) แล้วแจ้งอาจารย์ */
    public WorkLog submit(Long studentId, Long id) {
        WorkLog log = ownedLog(studentId, id);
        if (!log.isEditable()) {
            throw ApiException.conflict("ส่งได้เฉพาะบันทึกฉบับร่างหรือที่ถูกส่งกลับแก้ไข");
        }
        requireInProgress(findInternship(log.getInternshipId()));
        log.submit(LocalDateTime.now(clock));
        WorkLog saved = workLogDao.save(log);
        notificationService.notifyWorkLogSubmitted(saved);
        return saved;
    }

    /* ====================== ไฟล์แนบ ====================== */

    /** POST /api/work-logs/{id}/attachments: แนบรูป / PDF ไม่เกิน 10 MB · ไม่เกิน 5 ไฟล์ต่อบันทึก */
    public WorkLogAttachment addAttachment(Long studentId, Long id, MultipartFile file) {
        WorkLog log = ownedLog(studentId, id);
        requireEditable(log);
        if (attachmentDao.countByWorkLogId(log.getId()) >= MAX_ATTACHMENTS) {
            throw invalidAttachment("แนบไฟล์ได้ไม่เกิน " + MAX_ATTACHMENTS + " ไฟล์ต่อบันทึก");
        }
        String fileName = safeName(file == null ? null : file.getOriginalFilename());
        String contentType = checkFile(file, fileName);
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw invalidAttachment("อ่านไฟล์ไม่ได้ กรุณาเลือกไฟล์ใหม่");
        }
        String path = fileStorage.store("work-logs/" + log.getId(), fileName, content);
        try {
            return attachmentDao.save(new WorkLogAttachment(log.getId(), fileName, path, contentType,
                    content.length));
        } catch (RuntimeException e) {
            fileStorage.delete(path);
            throw e;
        }
    }

    /** GET /api/work-logs/{id}/attachments/{attachmentId}: ดาวน์โหลดไฟล์แนบ */
    @Transactional(readOnly = true)
    public FileContent downloadAttachment(Long userId, Long id, Long attachmentId) {
        WorkLog log = visibleLog(userId, id);
        WorkLogAttachment a = attachmentDao.findByIdAndWorkLogId(attachmentId, log.getId())
                .orElseThrow(() -> ApiException.notFound("ไม่พบไฟล์แนบ"));
        return new FileContent(a.getFileName(), a.getContentType(), fileStorage.load(a.getFilePath()));
    }

    /** DELETE /api/work-logs/{id}/attachments/{attachmentId}: ลบไฟล์แนบ · ได้เฉพาะ DRAFT / NEED_REVISION */
    public void deleteAttachment(Long studentId, Long id, Long attachmentId) {
        WorkLog log = ownedLog(studentId, id);
        requireEditable(log);
        WorkLogAttachment a = attachmentDao.findByIdAndWorkLogId(attachmentId, log.getId())
                .orElseThrow(() -> ApiException.notFound("ไม่พบไฟล์แนบ"));
        attachmentDao.delete(a);
        fileStorage.delete(a.getFilePath());
    }

    /* ====================== ดูรายการ / รายละเอียด / สรุป / ส่งออก ====================== */

    /** GET /api/internships/{id}/work-logs: รายการบันทึกเรียงตามวันที่ (อาจารย์ / เจ้าหน้าที่ไม่เห็น DRAFT) */
    @Transactional(readOnly = true)
    public List<WorkLog> getLogs(Long userId, Long internshipId, WorkLogFilter f) {
        Internship internship = findInternship(internshipId);
        User user = checkAccess(userId, internship);
        WorkLogFilter filter = f == null ? new WorkLogFilter(null, null, null) : f;
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw ApiException.badRequest("วันที่เริ่มต้องไม่หลังวันที่สิ้นสุด");
        }
        return workLogDao.findByInternshipIdAndFilter(internshipId, filter.from(), filter.to(), filter.status())
                .stream().filter(w -> canSee(user, w)).toList();
    }

    /** GET /api/work-logs/{id}: รายละเอียดบันทึก ไฟล์แนบ และความเห็นจากอาจารย์ */
    @Transactional(readOnly = true)
    public WorkLogDetailResponse getDetail(Long userId, Long id) {
        WorkLog log = visibleLog(userId, id);
        List<WorkLogAttachmentResponse> files = attachmentDao.findByWorkLogId(log.getId()).stream()
                .map(WorkLogAttachmentResponse::from).toList();
        String reviewer = log.getReviewedBy() == null ? null : nameOf(log.getReviewedBy());
        return WorkLogDetailResponse.from(log, nameOf(log.getStudentId()), files, reviewer);
    }

    /** GET /api/internships/{id}/work-logs/summary: ชั่วโมงรวม วันที่บันทึก วันที่ขาด และจำนวนแต่ละสถานะ */
    @Transactional(readOnly = true)
    public WorkLogSummaryResponse summary(Long userId, Long internshipId) {
        Internship internship = findInternship(internshipId);
        checkAccess(userId, internship);

        BigDecimal totalHours = workLogDao.sumHoursByInternshipId(internshipId);
        Set<LocalDate> logDates = new HashSet<>(workLogDao.findLogDatesByInternshipId(internshipId));
        Map<WorkLogStatus, Long> countByStatus = new EnumMap<>(WorkLogStatus.class);
        for (WorkLogStatus s : WorkLogStatus.values()) {
            countByStatus.put(s, 0L);
        }
        workLogDao.countByInternshipIdGroupByStatus(internshipId)
                .forEach(c -> countByStatus.put(c.getStatus(), c.getCount()));

        List<LocalDate> workingDays = workingDays(internship, LocalDate.now(clock));
        List<LocalDate> missing = workingDays.stream().filter(d -> !logDates.contains(d)).toList();
        return new WorkLogSummaryResponse(totalHours.setScale(2, RoundingMode.HALF_UP), logDates.size(),
                workingDays.size(), missing, countByStatus);
    }

    /** GET /api/internships/{id}/work-logs/export: ไฟล์ CSV (เปิดด้วย Excel ได้) เฉพาะอาจารย์ / เจ้าหน้าที่ */
    @Transactional(readOnly = true)
    public byte[] export(Long userId, Long internshipId) {
        guard.require(userId, Role.ADVISOR, Role.STAFF, Role.ADMIN);
        List<WorkLog> logs = getLogs(userId, internshipId, null);
        StringBuilder csv = new StringBuilder("﻿"); // BOM ให้ Excel อ่านภาษาไทยถูก
        csv.append("วันที่,เวลาเริ่ม,เวลาสิ้นสุด,ชั่วโมง,งานที่ทำ,สิ่งที่ได้เรียนรู้,ปัญหา,สถานะ,ส่งล่าช้า,ความเห็นอาจารย์\r\n");
        for (WorkLog w : logs) {
            csv.append(String.join(",", List.of(
                    String.valueOf(w.getLogDate()), String.valueOf(w.getStartTime()),
                    String.valueOf(w.getEndTime()), w.getHours().toPlainString(), cell(w.getActivity()),
                    cell(w.getLearning()), cell(w.getProblem()), w.getStatus().name(),
                    w.isLateSubmitted() ? "ใช่" : "", cell(w.getReviewComment())))).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** ใช้กับ InternshipService (US7): ปิดการฝึกงานได้เมื่อไม่มีบันทึกค้างตรวจ */
    @Transactional(readOnly = true)
    public boolean hasPendingReview(Long internshipId) {
        return workLogDao.existsByInternshipIdAndStatus(internshipId, WorkLogStatus.SUBMITTED);
    }

    /* ====================== แปลงเป็น Response ====================== */

    @Transactional(readOnly = true)
    public WorkLogResponse toResponse(WorkLog w) {
        return WorkLogResponse.from(w, nameOf(w.getStudentId()), (int) attachmentDao.countByWorkLogId(w.getId()));
    }

    @Transactional(readOnly = true)
    public List<WorkLogResponse> toResponses(List<WorkLog> logs) {
        Map<Long, String> names = new HashMap<>();
        return logs.stream().map(w -> WorkLogResponse.from(w,
                names.computeIfAbsent(w.getStudentId(), this::nameOf),
                (int) attachmentDao.countByWorkLogId(w.getId()))).toList();
    }

    /* ====================== ตรวจสิทธิ์ / ตรวจข้อมูล ====================== */

    /** นักศึกษาเจ้าของ · อาจารย์ที่ปรึกษาของนักศึกษา · เจ้าหน้าที่ ดูได้ ที่เหลือ 403 */
    User checkAccess(Long userId, Internship internship) {
        User user = guard.require(userId);
        boolean allowed = switch (user.getRole()) {
            case STUDENT -> user.getId().equals(internship.getStudentId());
            case ADVISOR -> userDao.findById(internship.getStudentId())
                    .map(s -> user.getId().equals(s.getAdvisorId())).orElse(false);
            case STAFF, ADMIN -> true;
        };
        if (!allowed) {
            throw ApiException.forbidden();
        }
        return user;
    }

    /** ตรวจข้อมูลบันทึก คืนจำนวนชั่วโมงที่คำนวณได้ · ผิด → 400 INVALID_WORK_LOG */
    BigDecimal validate(Internship internship, WorkLogRequest req) {
        if (req == null || req.logDate() == null) {
            throw invalid("กรุณาเลือกวันที่");
        }
        if (req.startTime() == null || req.endTime() == null) {
            throw invalid("กรุณากรอกเวลาเริ่มและเวลาสิ้นสุด");
        }
        if (isBlank(req.activity())) {
            throw invalid("กรุณากรอกงานที่ทำ");
        }
        if (isBlank(req.learning())) {
            throw invalid("กรุณากรอกสิ่งที่ได้เรียนรู้");
        }
        if (tooLong(req.activity()) || tooLong(req.learning()) || tooLong(req.problem())) {
            throw invalid("ข้อความแต่ละช่องต้องไม่เกิน " + MAX_TEXT + " ตัวอักษร");
        }
        if (req.logDate().isAfter(LocalDate.now(clock))) {
            throw invalid("ไม่สามารถบันทึกวันที่ในอนาคตได้");
        }
        if (req.logDate().isBefore(internship.getStartDate())
                || (internship.getEndDate() != null && req.logDate().isAfter(internship.getEndDate()))) {
            throw invalid("วันที่ต้องอยู่ในช่วงฝึกงาน");
        }
        BigDecimal hours = calcHours(req.startTime(), req.endTime());
        if (hours.compareTo(MIN_HOURS) < 0 || hours.compareTo(MAX_HOURS) > 0) {
            throw invalid("ชั่วโมงทำงานต้องอยู่ระหว่าง 0.5 ถึง 12 ชั่วโมง");
        }
        return hours;
    }

    /** ชั่วโมงจาก startTime–endTime ทศนิยม 2 ตำแหน่ง · เวลาสิ้นสุดต้องหลังเวลาเริ่ม */
    static BigDecimal calcHours(LocalTime start, LocalTime end) {
        if (!end.isAfter(start)) {
            throw invalid("เวลาสิ้นสุดต้องหลังเวลาเริ่ม");
        }
        long minutes = Duration.between(start, end).toMinutes();
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    /** วันจันทร์–ศุกร์ตั้งแต่วันเริ่มฝึกถึงวันนี้ (หรือวันสิ้นสุดถ้าจบแล้ว) */
    static List<LocalDate> workingDays(Internship internship, LocalDate today) {
        LocalDate last = internship.getEndDate() != null && internship.getEndDate().isBefore(today)
                ? internship.getEndDate() : today;
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = internship.getStartDate(); !d.isAfter(last); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                days.add(d);
            }
        }
        return days;
    }

    private Internship findInternship(Long internshipId) {
        return internshipDao.findById(internshipId)
                .orElseThrow(() -> ApiException.notFound("ไม่พบข้อมูลการฝึกงาน"));
    }

    private WorkLog findLog(Long id) {
        return workLogDao.findById(id).orElseThrow(() -> ApiException.notFound("ไม่พบบันทึกการทำงาน"));
    }

    /** บันทึกของนักศึกษาคนนี้เท่านั้น */
    private WorkLog ownedLog(Long studentId, Long id) {
        User student = guard.require(studentId, Role.STUDENT);
        WorkLog log = findLog(id);
        if (!student.getId().equals(log.getStudentId())) {
            throw ApiException.forbidden();
        }
        return log;
    }

    /** บันทึกที่ผู้ใช้มีสิทธิ์ดู · DRAFT ให้เห็นเฉพาะเจ้าของ */
    private WorkLog visibleLog(Long userId, Long id) {
        WorkLog log = findLog(id);
        User user = checkAccess(userId, findInternship(log.getInternshipId()));
        if (!canSee(user, log)) {
            throw ApiException.notFound("ไม่พบบันทึกการทำงาน");
        }
        return log;
    }

    private static boolean canSee(User user, WorkLog log) {
        return user.getRole() == Role.STUDENT || log.getStatus() != WorkLogStatus.DRAFT;
    }

    private static void requireInProgress(Internship internship) {
        if (internship.getStatus() != InternshipStatus.IN_PROGRESS) {
            throw new ApiException(409, "INTERNSHIP_NOT_IN_PROGRESS", "บันทึกได้เฉพาะช่วงที่กำลังฝึกงาน");
        }
    }

    private static void requireEditable(WorkLog log) {
        if (!log.isEditable()) {
            throw ApiException.conflict("แก้ไขได้เฉพาะบันทึกฉบับร่างหรือที่ถูกส่งกลับแก้ไข");
        }
    }

    private static void apply(WorkLog log, WorkLogRequest req, BigDecimal hours) {
        log.setStartTime(req.startTime());
        log.setEndTime(req.endTime());
        log.setHours(hours);
        log.setActivity(req.activity().trim());
        log.setLearning(req.learning().trim());
        log.setProblem(isBlank(req.problem()) ? null : req.problem().trim());
    }

    /** ตรวจไฟล์แนบ คืน content type ตามนามสกุล */
    private static String checkFile(MultipartFile file, String fileName) {
        if (file == null || file.isEmpty()) {
            throw invalidAttachment("กรุณาเลือกไฟล์");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw invalidAttachment("ไฟล์ต้องมีขนาดไม่เกิน 10 MB");
        }
        int dot = fileName.lastIndexOf('.');
        String type = dot < 0 ? null : FILE_TYPES.get(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
        if (type == null) {
            throw invalidAttachment("แนบได้เฉพาะไฟล์รูป (JPG, PNG) หรือ PDF");
        }
        return type;
    }

    /** ตัด path ออกเหลือแต่ชื่อไฟล์ */
    private static String safeName(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    private static String cell(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String nameOf(Long userId) {
        return userDao.findById(userId).map(User::getFullName).orElse("");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean tooLong(String s) {
        return s != null && s.trim().length() > MAX_TEXT;
    }

    private static ApiException invalid(String message) {
        return new ApiException(400, "INVALID_WORK_LOG", message);
    }

    private static ApiException invalidAttachment(String message) {
        return new ApiException(400, "INVALID_ATTACHMENT", message);
    }

    private static ApiException duplicateDate() {
        return new ApiException(409, "DUPLICATE_LOG_DATE", "มีบันทึกของวันที่นี้แล้ว");
    }
}
