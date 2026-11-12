package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.dao.ApplicationRepository;
import com.example.demo.dao.InternshipRepository;
import com.example.demo.dao.MatchRepository;
import com.example.demo.dao.StudentRepository;
import com.example.demo.dao.StudentStatusQueryDao;
import com.example.demo.dao.UserRepository;
import com.example.demo.dto.StudentStatusFilter;
import com.example.demo.dto.StudentStatusResponse;
import com.example.demo.entity.Application;
import com.example.demo.entity.ApplicationStatus;
import com.example.demo.entity.Attention;
import com.example.demo.entity.Internship;
import com.example.demo.entity.InternshipStage;
import com.example.demo.entity.InternshipStatus;
import com.example.demo.entity.Match;
import com.example.demo.entity.Role;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;

class StudentStatusServiceTest {

    private static final ZoneId ZONE =
            ZoneId.of("Asia/Bangkok");

    private static final Clock CLOCK =
            Clock.fixed(
                    Instant.parse("2026-11-13T03:00:00Z"),
                    ZONE);

    private static final LocalDateTime NOW =
            LocalDateTime.now(CLOCK);

    private StudentStatusQueryDao queryDao;
    private StudentRepository studentRepository;
    private UserRepository userRepository;
    private ApplicationRepository applicationRepository;
    private MatchRepository matchRepository;
    private InternshipRepository internshipRepository;
    private PreferenceService preferenceService;
    private MatchViews matchViews;
    private RequestService requestService;
    private StatusHistoryService historyService;
    private AccessGuard guard;

    private StudentStatusService service;

    private User staff;
    private User advisor;
    private User studentUser;
    private Student student;

    @BeforeEach
    void setUp() {
        queryDao =
                mock(StudentStatusQueryDao.class);

        studentRepository =
                mock(StudentRepository.class);

        userRepository =
                mock(UserRepository.class);

        applicationRepository =
                mock(ApplicationRepository.class);

        matchRepository =
                mock(MatchRepository.class);

        internshipRepository =
                mock(InternshipRepository.class);

        preferenceService =
                mock(PreferenceService.class);

        matchViews =
                mock(MatchViews.class);

        requestService =
                mock(RequestService.class);

        historyService =
                mock(StatusHistoryService.class);

        guard =
                mock(AccessGuard.class);

        service = new StudentStatusService(
                queryDao,
                studentRepository,
                userRepository,
                applicationRepository,
                matchRepository,
                internshipRepository,
                preferenceService,
                matchViews,
                requestService,
                historyService,
                guard,
                CLOCK);

        staff = user(
                1L,
                "staff01",
                Role.STAFF);

        advisor = user(
                2L,
                "advisor01",
                Role.ADVISOR);

        studentUser = user(
                3L,
                "6609650001",
                Role.STUDENT);

        student = new Student(
                3L,
                "6609650001",
                "วิทยาศาสตร์และเทคโนโลยี",
                "วิทยาการคอมพิวเตอร์",
                3.1);

        ReflectionTestUtils.setField(
                student,
                "id",
                5L);

        student.setAdvisorId(2L);

        when(guard.require(
                1L,
                Role.STAFF,
                Role.ADVISOR))
                .thenReturn(staff);

        when(guard.require(
                2L,
                Role.STAFF,
                Role.ADVISOR))
                .thenReturn(advisor);
    }

    @Test
    @DisplayName(
            "SV-S01 คำนวณ Stage ก่อนอนุมัติ")
    void computeApplicationStages() {
        assertThat(
                service.computeStage(
                        null,
                        null,
                        null))
                .isEqualTo(
                        InternshipStage.NOT_APPLIED);

        assertThat(
                service.computeStage(
                        application(
                                ApplicationStatus.SUBMITTED,
                                NOW),
                        null,
                        null))
                .isEqualTo(
                        InternshipStage.APPLYING);

        assertThat(
                service.computeStage(
                        application(
                                ApplicationStatus.NEED_CORRECTION,
                                NOW),
                        null,
                        null))
                .isEqualTo(
                        InternshipStage.APPLYING);

        assertThat(
                service.computeStage(
                        application(
                                ApplicationStatus.REJECTED,
                                NOW),
                        null,
                        null))
                .isEqualTo(
                        InternshipStage.REJECTED);
    }

    @Test
    @DisplayName(
            "SV-S02 คำนวณ Stage หลังอนุมัติและฝึกงาน")
    void computeInternshipStages() {
        Application approved =
                application(
                        ApplicationStatus.APPROVED,
                        NOW);

        Match proposed =
                proposedMatch(
                        NOW.minusDays(1));

        assertThat(
                service.computeStage(
                        approved,
                        null,
                        null))
                .isEqualTo(
                        InternshipStage.WAITING_MATCH);

        assertThat(
                service.computeStage(
                        approved,
                        proposed,
                        null))
                .isEqualTo(
                        InternshipStage.MATCHING);

        Internship notStarted =
                internship();

        assertThat(
                service.computeStage(
                        approved,
                        proposed,
                        notStarted))
                .isEqualTo(
                        InternshipStage.READY);

        Internship inProgress =
                completeInformationInternship(
                        LocalDate.now(CLOCK)
                                .plusDays(1));

        inProgress.changeStatus(
                InternshipStatus.IN_PROGRESS,
                null,
                1L,
                NOW);

        assertThat(
                service.computeStage(
                        approved,
                        proposed,
                        inProgress))
                .isEqualTo(
                        InternshipStage.IN_PROGRESS);

        inProgress.changeStatus(
                InternshipStatus.COMPLETED,
                null,
                1L,
                NOW);

        assertThat(
                service.computeStage(
                        approved,
                        proposed,
                        inProgress))
                .isEqualTo(
                        InternshipStage.COMPLETED);

        Internship terminated =
                internship();

        terminated.changeStatus(
                InternshipStatus.TERMINATED,
                "ยุติการฝึกงาน",
                1L,
                NOW);

        assertThat(
                service.computeStage(
                        approved,
                        proposed,
                        terminated))
                .isEqualTo(
                        InternshipStage.TERMINATED);
    }

    @Test
    @DisplayName(
            "SV-S03 เตือน NO_MATCH เมื่อเกิน 14 วัน")
    void attentionNoMatch() {
        Application day15 =
                application(
                        ApplicationStatus.APPROVED,
                        NOW.minusDays(15));

        Application day14 =
                application(
                        ApplicationStatus.APPROVED,
                        NOW.minusDays(14));

        assertThat(
                service.computeAttention(
                        day15,
                        null,
                        null))
                .isEqualTo(
                        Attention.NO_MATCH);

        assertThat(
                service.computeAttention(
                        day14,
                        null,
                        null))
                .isNull();
    }

    @Test
    @DisplayName(
            "SV-S04 เตือน NOT_CONFIRMED เมื่อเกิน 7 วัน")
    void attentionNotConfirmed() {
        assertThat(
                service.computeAttention(
                        application(
                                ApplicationStatus.APPROVED,
                                NOW),
                        proposedMatch(
                                NOW.minusDays(8)),
                        null))
                .isEqualTo(
                        Attention.NOT_CONFIRMED);

        assertThat(
                service.computeAttention(
                        application(
                                ApplicationStatus.APPROVED,
                                NOW),
                        proposedMatch(
                                NOW.minusDays(7)),
                        null))
                .isNull();
    }

    @Test
    @DisplayName(
            "SV-S05 เตือนข้อมูลไม่ครบและเลยวันเริ่ม")
    void attentionInternship() {
        Internship missing =
                internship();

        assertThat(
                service.computeAttention(
                        null,
                        null,
                        missing))
                .isEqualTo(
                        Attention.MISSING_INFO);

        Internship overdue =
                completeInformationInternship(
                        LocalDate.now(CLOCK)
                                .minusDays(1));

        assertThat(
                service.computeAttention(
                        null,
                        null,
                        overdue))
                .isEqualTo(
                        Attention.START_OVERDUE);

        Internship future =
                completeInformationInternship(
                        LocalDate.now(CLOCK)
                                .plusDays(3));

        assertThat(
                service.computeAttention(
                        null,
                        null,
                        future))
                .isNull();
    }

    @Test
    @DisplayName(
            "SV-S06 อาจารย์ถูกบังคับ AdvisorId เป็นของตน")
    void advisorSearchUsesOwnId() {
        when(queryDao.search(any()))
                .thenReturn(
                        new PageImpl<>(List.of()));

        StudentStatusFilter input =
                new StudentStatusFilter(
                        null,
                        null,
                        null,
                        99L,
                        0,
                        20);

        service.search(2L, input);

        ArgumentCaptor<StudentStatusFilter> captor =
                ArgumentCaptor.forClass(
                        StudentStatusFilter.class);

        verify(queryDao)
                .search(captor.capture());

        assertThat(
                captor.getValue().advisorId())
                .isEqualTo(2L);
    }

    @Test
    @DisplayName(
            "SV-S07 เจ้าหน้าที่ใช้ AdvisorId ที่ส่งมา")
    void staffSearchKeepsAdvisorId() {
        when(queryDao.search(any()))
                .thenReturn(
                        new PageImpl<>(List.of()));

        StudentStatusFilter input =
                new StudentStatusFilter(
                        InternshipStage.APPLYING,
                        null,
                        "6501",
                        2L,
                        0,
                        20);

        service.search(1L, input);

        ArgumentCaptor<StudentStatusFilter> captor =
                ArgumentCaptor.forClass(
                        StudentStatusFilter.class);

        verify(queryDao)
                .search(captor.capture());

        assertThat(
                captor.getValue().advisorId())
                .isEqualTo(2L);

        assertThat(
                captor.getValue().stage())
                .isEqualTo(
                        InternshipStage.APPLYING);
    }

    @Test
    @DisplayName(
            "SV-S08 นักศึกษาเรียกรายการ Summary และ Export ไม่ได้")
    void studentCannotUseStaffFunctions() {
        when(guard.require(
                3L,
                Role.STAFF,
                Role.ADVISOR))
                .thenThrow(
                        ApiException.forbidden());

        StudentStatusFilter filter =
                new StudentStatusFilter(
                        null,
                        null,
                        null,
                        null,
                        0,
                        20);

        assertThatThrownBy(() ->
                service.search(3L, filter))
                .isInstanceOf(
                        ApiException.class)
                .extracting("status")
                .isEqualTo(403);

        assertThatThrownBy(() ->
                service.summary(3L))
                .isInstanceOf(
                        ApiException.class)
                .extracting("status")
                .isEqualTo(403);

        assertThatThrownBy(() ->
                service.exportCsv(
                        3L,
                        null))
                .isInstanceOf(
                        ApiException.class)
                .extracting("status")
                .isEqualTo(403);

        verifyNoInteractions(queryDao);
    }

    @Test
    @DisplayName(
            "SV-S09 ตรวจสิทธิ์นักศึกษาและอาจารย์")
    void checkAccess() {
        assertThatCode(() ->
                service.checkAccess(
                        studentUser,
                        student))
                .doesNotThrowAnyException();

        User anotherStudent =
                user(
                        4L,
                        "6609650002",
                        Role.STUDENT);

        assertThatThrownBy(() ->
                service.checkAccess(
                        anotherStudent,
                        student))
                .isInstanceOf(
                        ApiException.class)
                .extracting("status")
                .isEqualTo(403);

        User anotherAdvisor =
                user(
                        9L,
                        "advisor02",
                        Role.ADVISOR);

        assertThatThrownBy(() ->
                service.checkAccess(
                        anotherAdvisor,
                        student))
                .isInstanceOf(
                        ApiException.class)
                .extracting("status")
                .isEqualTo(403);

        assertThatCode(() ->
                service.checkAccess(
                        advisor,
                        student))
                .doesNotThrowAnyException();

        assertThatCode(() ->
                service.checkAccess(
                        staff,
                        student))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName(
            "SV-S10 Export CSV มี BOM และ Escape เครื่องหมายจุลภาค")
    void exportCsv() {
        StudentStatusResponse first =
                row(
                        5L,
                        "6609650001",
                        "สมชาย, ใจดี",
                        InternshipStage.IN_PROGRESS);

        StudentStatusResponse second =
                row(
                        6L,
                        "6609650002",
                        "สมหญิง รักเรียน",
                        InternshipStage.IN_PROGRESS);

        when(queryDao.findAllForExport(
                InternshipStage.IN_PROGRESS,
                null))
                .thenReturn(
                        List.of(first, second));

        when(applicationRepository
                .findLatestByStudentId(any()))
                .thenReturn(
                        java.util.Optional.empty());

        when(internshipRepository
                .findByStudentId(any()))
                .thenReturn(
                        java.util.Optional.empty());

        byte[] bytes =
                service.exportCsv(
                        1L,
                        InternshipStage.IN_PROGRESS);

        String csv =
                new String(
                        bytes,
                        StandardCharsets.UTF_8);

        assertThat(csv.charAt(0))
                .isEqualTo('\uFEFF');

        assertThat(csv)
                .contains(
                        "\"สมชาย, ใจดี\"");

        assertThat(csv)
                .contains("6609650001");

        assertThat(csv)
                .contains("6609650002");

        verify(queryDao)
                .findAllForExport(
                        InternshipStage.IN_PROGRESS,
                        null);
    }

    private User user(
            Long id,
            String username,
            Role role) {

        User user = new User(
                username,
                username,
                username + "@example.com",
                role);

        ReflectionTestUtils.setField(
                user,
                "id",
                id);

        return user;
    }

    private Application application(
            ApplicationStatus status,
            LocalDateTime updatedAt) {

        Application application =
                new Application(
                        5L,
                        1L,
                        status);

        ReflectionTestUtils.setField(
                application,
                "id",
                40L);

        ReflectionTestUtils.setField(
                application,
                "createdAt",
                updatedAt);

        ReflectionTestUtils.setField(
                application,
                "submittedAt",
                updatedAt);

        ReflectionTestUtils.setField(
                application,
                "updatedAt",
                updatedAt);

        return application;
    }

    private Match proposedMatch(
            LocalDateTime matchedAt) {

        Match match = new Match(
                40L,
                2L,
                1L,
                1,
                1L,
                matchedAt);

        ReflectionTestUtils.setField(
                match,
                "id",
                10L);

        return match;
    }

    private Internship internship() {
        Internship internship =
                new Internship(
                        10L,
                        5L,
                        1L,
                        NOW.minusDays(1));

        ReflectionTestUtils.setField(
                internship,
                "id",
                20L);

        return internship;
    }

    private Internship completeInformationInternship(
            LocalDate startDate) {

        Internship internship =
                internship();

        internship.updateDetails(
                startDate,
                startDate.plusMonths(4),
                "คุณผู้ดูแล",
                "supervisor@example.com",
                "0812345678",
                1L,
                NOW.minusHours(1));

        return internship;
    }

    private StudentStatusResponse row(
            Long studentId,
            String studentCode,
            String studentName,
            InternshipStage stage) {

        return new StudentStatusResponse(
                studentId,
                studentCode,
                studentName,
                "อาจารย์ที่ปรึกษา",
                2L,
                "บริษัท ทดสอบ จำกัด",
                "Software Developer",
                stage,
                InternshipStatus.IN_PROGRESS,
                null,
                LocalDate.of(
                        2026,
                        12,
                        1),
                NOW);
    }
}