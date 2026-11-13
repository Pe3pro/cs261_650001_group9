package com.example.demo.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dao.ApplicationRepository;
import com.example.demo.dao.InternshipRepository;
import com.example.demo.dao.MatchRepository;
import com.example.demo.dao.MatchingRoundRepository;
import com.example.demo.dao.StudentRepository;
import com.example.demo.dao.StudentStatusQueryDao;
import com.example.demo.dao.UserRepository;
import com.example.demo.dto.InternshipResponse;
import com.example.demo.dto.MatchResponse;
import com.example.demo.dto.PageResponse;
import com.example.demo.dto.PreferenceResponse;
import com.example.demo.dto.RequestResponse;
import com.example.demo.dto.StatusHistoryResponse;
import com.example.demo.dto.StudentProfileResponse;
import com.example.demo.dto.StudentStatusDetailResponse;
import com.example.demo.dto.StudentStatusFilter;
import com.example.demo.dto.StudentStatusResponse;
import com.example.demo.entity.Application;
import com.example.demo.entity.ApplicationStatus;
import com.example.demo.entity.Attention;
import com.example.demo.entity.Internship;
import com.example.demo.entity.InternshipStage;
import com.example.demo.entity.InternshipStatus;
import com.example.demo.entity.Match;
import com.example.demo.entity.MatchStatus;
import com.example.demo.entity.Role;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;

/** U7: ตรวจสอบข้อมูลและสถานะการฝึกงานของนักศึกษา */
@Service
@Transactional(readOnly = true)
public class StudentStatusService {

    private static final DateTimeFormatter CSV_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final StudentStatusQueryDao queryDao;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;
    private final MatchRepository matchRepository;
    private final MatchingRoundRepository matchingRoundRepository;
    private final InternshipRepository internshipRepository;
    private final PreferenceService preferenceService;
    private final MatchViews matchViews;
    private final RequestService requestService;
    private final StatusHistoryService statusHistoryService;
    private final AccessGuard guard;
    private final Clock clock;

    public StudentStatusService(
            StudentStatusQueryDao queryDao,
            StudentRepository studentRepository,
            UserRepository userRepository,
            ApplicationRepository applicationRepository,
            MatchRepository matchRepository,
            MatchingRoundRepository matchingRoundRepository,
            InternshipRepository internshipRepository,
            PreferenceService preferenceService,
            MatchViews matchViews,
            RequestService requestService,
            StatusHistoryService statusHistoryService,
            AccessGuard guard,
            Clock clock) {

        this.queryDao = queryDao;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
        this.matchRepository = matchRepository;
        this.matchingRoundRepository = matchingRoundRepository;
        this.internshipRepository = internshipRepository;
        this.preferenceService = preferenceService;
        this.matchViews = matchViews;
        this.requestService = requestService;
        this.statusHistoryService = statusHistoryService;
        this.guard = guard;
        this.clock = clock;
    }

    /** รายการนักศึกษาสำหรับเจ้าหน้าที่และอาจารย์ */
    public PageResponse<StudentStatusResponse> search(
            Long userId,
            StudentStatusFilter filter) {

        User user = guard.require(
                userId,
                Role.STAFF,
                Role.ADVISOR);

        StudentStatusFilter safeFilter =
                normalizeFilter(filter);

        /*
         * อาจารย์ต้องดูได้เฉพาะนักศึกษาที่ตนดูแล
         * แม้ส่ง advisorId ของคนอื่นมาก็ถูกแทนด้วย id ตนเอง
         */
        if (user.getRole() == Role.ADVISOR) {
            safeFilter = new StudentStatusFilter(
                    safeFilter.stage(),
                    safeFilter.companyId(),
                    safeFilter.q(),
                    user.getId(),
                    safeFilter.page(),
                    safeFilter.size());
        }

        Page<StudentStatusResponse> page =
                queryDao.search(safeFilter)
                        .map(this::withAttention);

        return PageResponse.from(page);
    }

    /** จำนวนในแต่ละขั้น */
    public Map<InternshipStage, Long> summary(
            Long userId) {

        User user = guard.require(
                userId,
                Role.STAFF,
                Role.ADVISOR);

        Long advisorId = user.getRole() == Role.ADVISOR
                ? user.getId()
                : null;

        return queryDao.countByStage(advisorId);
    }

    /** รายละเอียดของนักศึกษา */
    public StudentStatusDetailResponse getDetail(
            Long userId,
            Long studentId) {

        User user = guard.require(userId);

        Student student =
                studentRepository.findById(studentId)
                        .orElseThrow(() ->
                                ApiException.notFound(
                                        "ไม่พบนักศึกษา"));

        checkAccess(user, student);

        User studentUser =
                userRepository.findById(student.getUserId())
                        .orElseThrow(() ->
                                ApiException.notFound(
                                        "ไม่พบบัญชีนักศึกษา"));

        Application application =
                applicationRepository
                        .findLatestByStudentId(student.getId())
                        .orElse(null);

        Match match = application == null
                ? null
                : matchRepository
                        .findActiveWithPositionByApplicationId(
                                application.getId())
                        .orElse(null);

        Internship internship =
                internshipRepository
                        .findByStudentId(student.getId())
                        .orElse(null);

        StudentProfileResponse profile =
                new StudentProfileResponse(
                        student.getStudentId(),
                        studentUser.getFullName(),
                        studentUser.getEmail(),
                        student.getFaculty(),
                        student.getMajor(),
                        student.getGpa());

        RequestResponse applicationResponse =
                application == null
                        ? null
                        : requestService.get(
                                application.getId(),
                                userId);

        List<PreferenceResponse> preferences =
                application == null
                        ? List.of()
                        : preferenceService
                                .preferencesOf(application);

        MatchResponse matchResponse =
                match == null
                        ? null
                        : matchViews.toResponse(
                                match,
                                application);

        InternshipResponse internshipResponse =
                internship == null
                        ? null
                        : InternshipResponse.from(internship);

        List<StatusHistoryResponse> timeline =
                statusHistoryService.getTimeline(
                        student.getId());

        InternshipStage stage =
                computeStage(
                        application,
                        match,
                        internship);

        Attention attention =
                computeAttention(
                        application,
                        match,
                        internship);

        return new StudentStatusDetailResponse(
                profile,
                applicationResponse,
                preferences,
                matchResponse,
                internshipResponse,
                timeline,
                stage,
                attention);
    }

    /** นักศึกษาดูสถานะของตนเอง */
    public StudentStatusDetailResponse getMyStatus(
            Long userId) {

        User user = guard.require(
                userId,
                Role.STUDENT);

        Student student =
                studentRepository.findByUserId(user.getId())
                        .orElseThrow(() ->
                                ApiException.notFound(
                                        "ไม่พบข้อมูลนักศึกษา"));

        return getDetail(userId, student.getId());
    }

    /** Timeline ของนักศึกษา */
    public List<StatusHistoryResponse> getTimeline(
            Long userId,
            Long studentId) {

        User user = guard.require(userId);

        Student student =
                studentRepository.findById(studentId)
                        .orElseThrow(() ->
                                ApiException.notFound(
                                        "ไม่พบนักศึกษา"));

        checkAccess(user, student);

        return statusHistoryService.getTimeline(
                student.getId());
    }

    /** สร้างไฟล์ CSV พร้อม UTF-8 BOM */
    public byte[] exportCsv(
            Long userId,
            InternshipStage stage) {

        User user = guard.require(
                userId,
                Role.STAFF,
                Role.ADVISOR);

        Long advisorId = user.getRole() == Role.ADVISOR
                ? user.getId()
                : null;

        List<StudentStatusResponse> rows =
                queryDao.findAllForExport(
                        stage,
                        advisorId)
                        .stream()
                        .map(this::withAttention)
                        .toList();

        StringBuilder csv = new StringBuilder();

        // UTF-8 BOM ช่วยให้ Excel แสดงภาษาไทยถูกต้อง
        csv.append('\uFEFF');

        csv.append(
                "รหัสนักศึกษา,ชื่อ-นามสกุล,"
                        + "อาจารย์ที่ปรึกษา,บริษัท,ตำแหน่ง,"
                        + "ขั้นปัจจุบัน,สถานะการฝึกงาน,"
                        + "ป้ายเตือน,วันเริ่มฝึกงาน,"
                        + "อัปเดตล่าสุด\r\n");

        for (StudentStatusResponse row : rows) {
            appendCsv(csv, row.studentCode());
            appendCsv(csv, row.studentName());
            appendCsv(csv, row.advisorName());
            appendCsv(csv, row.companyName());
            appendCsv(csv, row.positionTitle());
            appendCsv(
                    csv,
                    row.stage() == null
                            ? null
                            : row.stage().name());
            appendCsv(
                    csv,
                    row.internshipStatus() == null
                            ? null
                            : row.internshipStatus().name());
            appendCsv(
                    csv,
                    row.attention() == null
                            ? null
                            : row.attention().name());
            appendCsv(
                    csv,
                    row.startDate() == null
                            ? null
                            : row.startDate().toString());
            appendLastCsv(
                    csv,
                    row.updatedAt() == null
                            ? null
                            : row.updatedAt().format(CSV_TIME));
        }

        return csv.toString()
                .getBytes(StandardCharsets.UTF_8);
    }

    /** คำนวณขั้นปัจจุบัน */
    public InternshipStage computeStage(
            Application application,
            Match match,
            Internship internship) {

        if (internship != null) {
            return switch (internship.getStatus()) {
                case NOT_STARTED ->
                        InternshipStage.READY;
                case IN_PROGRESS ->
                        InternshipStage.IN_PROGRESS;
                case COMPLETED ->
                        InternshipStage.COMPLETED;
                case TERMINATED ->
                        InternshipStage.TERMINATED;
            };
        }

        if (application == null) {
            return InternshipStage.NOT_APPLIED;
        }

        if (application.getStatus()
                == ApplicationStatus.REJECTED) {
            return InternshipStage.REJECTED;
        }

        if (application.getStatus()
                != ApplicationStatus.APPROVED) {
            return InternshipStage.APPLYING;
        }

        if (match == null) {
            return InternshipStage.WAITING_MATCH;
        }

        if (match.getStatus()
                == MatchStatus.PROPOSED) {
            return InternshipStage.MATCHING;
        }

        if (match.getStatus()
                == MatchStatus.CONFIRMED) {
            return InternshipStage.READY;
        }

        return InternshipStage.WAITING_MATCH;
    }

    /** คำนวณป้ายเตือน */
    public Attention computeAttention(
            Application application,
            Match match,
            Internship internship) {

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = LocalDate.now(clock);

        if (internship != null
                && internship.getStatus()
                        == InternshipStatus.NOT_STARTED) {

            if (!internship.infoComplete()) {
                return Attention.MISSING_INFO;
            }

            if (internship.getStartDate() != null
                    && internship.getStartDate()
                            .isBefore(today)) {
                return Attention.START_OVERDUE;
            }

            return null;
        }

        if (match != null
                && match.getStatus()
                        == MatchStatus.PROPOSED
                && match.getRoundId() != null) {

            boolean deadlinePassed =
                    matchingRoundRepository
                            .findById(match.getRoundId())
                            .map(round ->
                                    round.isDeadlinePassed(now))
                            .orElse(false);

            if (deadlinePassed) {
                return Attention.NOT_CONFIRMED;
            }
        }

        if (application != null
                && application.getStatus()
                        == ApplicationStatus.APPROVED
                && match == null) {

            LocalDateTime approvedTime =
                    application.getUpdatedAt() != null
                            ? application.getUpdatedAt()
                            : application.getSubmittedAt();

            if (approvedTime != null
                    && approvedTime.isBefore(
                            now.minusDays(14))) {
                return Attention.NO_MATCH;
            }
        }

        return null;
    }

    /** ตรวจสิทธิ์ดูนักศึกษา */
    public void checkAccess(
            User user,
            Student student) {

        if (user.getRole() == Role.STAFF) {
            return;
        }

        if (user.getRole() == Role.STUDENT
                && student.getUserId()
                        .equals(user.getId())) {
            return;
        }

        if (user.getRole() == Role.ADVISOR
                && student.getAdvisorId() != null
                && student.getAdvisorId()
                        .equals(user.getId())) {
            return;
        }

        throw ApiException.forbidden();
    }

    private StudentStatusResponse withAttention(
            StudentStatusResponse row) {

        Application application =
                applicationRepository
                        .findLatestByStudentId(row.studentId())
                        .orElse(null);

        Match match = application == null
                ? null
                : matchRepository
                        .findActiveWithPositionByApplicationId(
                                application.getId())
                        .orElse(null);

        Internship internship =
                internshipRepository
                        .findByStudentId(row.studentId())
                        .orElse(null);

        Attention attention =
                computeAttention(
                        application,
                        match,
                        internship);

        return new StudentStatusResponse(
                row.studentId(),
                row.studentCode(),
                row.studentName(),
                row.advisorName(),
                row.companyId(),
                row.companyName(),
                row.positionTitle(),
                row.stage(),
                row.internshipStatus(),
                attention,
                row.startDate(),
                row.updatedAt());
    }

    private StudentStatusFilter normalizeFilter(
            StudentStatusFilter filter) {

        if (filter == null) {
            return new StudentStatusFilter(
                    null,
                    null,
                    null,
                    null,
                    0,
                    20);
        }

        if (filter.page() < 0) {
            throw ApiException.badRequest(
                    "หมายเลขหน้าต้องไม่ติดลบ");
        }

        int size = filter.size();

        if (size <= 0) {
            size = 20;
        }

        if (size > 100) {
            size = 100;
        }

        return new StudentStatusFilter(
                filter.stage(),
                filter.companyId(),
                filter.q(),
                filter.advisorId(),
                filter.page(),
                size);
    }

    private void appendCsv(
            StringBuilder csv,
            String value) {

        csv.append(escapeCsv(value));
        csv.append(',');
    }

    private void appendLastCsv(
            StringBuilder csv,
            String value) {

        csv.append(escapeCsv(value));
        csv.append("\r\n");
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }

        String escaped = value.replace(
                "\"",
                "\"\"");

        if (escaped.contains(",")
                || escaped.contains("\"")
                || escaped.contains("\n")
                || escaped.contains("\r")) {

            return "\"" + escaped + "\"";
        }

        return escaped;
    }
}
