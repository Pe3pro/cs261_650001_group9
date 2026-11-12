package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.dao.ApplicationRepository;
import com.example.demo.dao.InternshipRepository;
import com.example.demo.dao.MatchRepository;
import com.example.demo.dao.StudentRepository;
import com.example.demo.dto.InternshipRequest;
import com.example.demo.dto.InternshipResponse;
import com.example.demo.entity.Application;
import com.example.demo.entity.ApplicationStatus;
import com.example.demo.entity.HistoryType;
import com.example.demo.entity.Internship;
import com.example.demo.entity.InternshipStatus;
import com.example.demo.entity.Match;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;

class InternshipServiceTest {

    private static final ZoneId ZONE =
            ZoneId.of("Asia/Bangkok");

    private static final Clock CLOCK =
            Clock.fixed(
                    Instant.parse("2026-11-13T03:00:00Z"),
                    ZONE);

    private static final LocalDateTime NOW =
            LocalDateTime.now(CLOCK);

    private InternshipRepository internships;
    private MatchRepository matches;
    private ApplicationRepository applications;
    private StudentRepository students;
    private StatusHistoryService history;
    private NotificationService notifications;
    private AccessGuard guard;
    private InternshipService service;

    private User staff;

    @BeforeEach
    void setUp() {
        internships =
                mock(InternshipRepository.class);

        matches =
                mock(MatchRepository.class);

        applications =
                mock(ApplicationRepository.class);

        students =
                mock(StudentRepository.class);

        history =
                mock(StatusHistoryService.class);

        notifications =
                mock(NotificationService.class);

        guard =
                mock(AccessGuard.class);

        service = new InternshipService(
                internships,
                matches,
                applications,
                students,
                history,
                notifications,
                guard,
                CLOCK);

        staff = new User(
                "staff01",
                "เจ้าหน้าที่ทดสอบ",
                "staff@example.com",
                Role.STAFF);

        ReflectionTestUtils.setField(
                staff,
                "id",
                1L);

        when(guard.require(
                1L,
                Role.STAFF))
                .thenReturn(staff);

        when(internships.save(any()))
                .thenAnswer(invocation ->
                        invocation.getArgument(0));
    }

    @Test
    @DisplayName(
            "SV-I01 สร้าง Internship จาก Match ที่ยืนยันแล้ว")
    void createFromConfirmedMatch() {
        Match match = confirmedMatch();
        Application application = application();

        when(internships.findByMatchId(10L))
                .thenReturn(Optional.empty());

        when(matches.findById(10L))
                .thenReturn(Optional.of(match));

        when(applications.findById(40L))
                .thenReturn(
                        Optional.of(application));

        InternshipResponse response =
                service.createFromMatch(
                        10L,
                        3L);

        assertThat(response.status())
                .isEqualTo(
                        InternshipStatus.NOT_STARTED);

        assertThat(response.matchId())
                .isEqualTo(10L);

        assertThat(response.studentId())
                .isEqualTo(5L);

        verify(internships)
                .save(any(Internship.class));

        verify(history).record(
                eq(5L),
                eq(HistoryType.INTERNSHIP),
                isNull(),
                eq("NOT_STARTED"),
                eq(3L),
                isNull(),
                eq(NOW));
    }

    @Test
    @DisplayName(
            "SV-I02 Match ที่มี Internship แล้วไม่สร้างซ้ำ")
    void existingInternshipIsReturned() {
        Internship existing =
                new Internship(
                        10L,
                        5L,
                        3L,
                        NOW.minusDays(1));

        ReflectionTestUtils.setField(
                existing,
                "id",
                20L);

        when(internships.findByMatchId(10L))
                .thenReturn(
                        Optional.of(existing));

        InternshipResponse response =
                service.createFromMatch(
                        10L,
                        3L);

        assertThat(response.id())
                .isEqualTo(20L);

        verify(internships, never())
                .save(any());

        verifyNoInteractions(
                matches,
                applications,
                history);
    }

    @Test
    @DisplayName(
            "SV-I03 เส้นทางเปลี่ยนสถานะที่ถูกต้อง")
    void validTransitions() {
        service.validateTransition(
                InternshipStatus.NOT_STARTED,
                InternshipStatus.IN_PROGRESS);

        service.validateTransition(
                InternshipStatus.IN_PROGRESS,
                InternshipStatus.COMPLETED);

        service.validateTransition(
                InternshipStatus.NOT_STARTED,
                InternshipStatus.TERMINATED);

        service.validateTransition(
                InternshipStatus.IN_PROGRESS,
                InternshipStatus.TERMINATED);
    }

    @Test
    @DisplayName(
            "SV-I04 เส้นทางเปลี่ยนสถานะที่ผิดได้ INVALID_TRANSITION")
    void invalidTransitions() {
        assertInvalidTransition(
                InternshipStatus.NOT_STARTED,
                InternshipStatus.COMPLETED);

        assertInvalidTransition(
                InternshipStatus.COMPLETED,
                InternshipStatus.IN_PROGRESS);

        assertInvalidTransition(
                InternshipStatus.TERMINATED,
                InternshipStatus.IN_PROGRESS);

        assertInvalidTransition(
                InternshipStatus.IN_PROGRESS,
                InternshipStatus.NOT_STARTED);
    }

    @Test
    @DisplayName(
            "SV-I05 เริ่มฝึกงาน บันทึกประวัติและแจ้งเตือน")
    void startInternship() {
        Internship internship =
                completeInternshipInformation();

        when(internships.findById(20L))
                .thenReturn(
                        Optional.of(internship));

        InternshipResponse response =
                service.start(1L, 20L);

        assertThat(response.status())
                .isEqualTo(
                        InternshipStatus.IN_PROGRESS);

        assertThat(internship.getUpdatedBy())
                .isEqualTo(1L);

        verify(internships)
                .save(internship);

        verify(history).record(
                eq(5L),
                eq(HistoryType.INTERNSHIP),
                eq("NOT_STARTED"),
                eq("IN_PROGRESS"),
                eq(1L),
                isNull(),
                eq(NOW));

        verify(notifications)
                .notifyInternshipStatusChanged(
                        internship);
    }

    @Test
    @DisplayName(
            "SV-I06 ข้อมูลไม่ครบ เริ่มฝึกงานไม่ได้")
    void missingInformationCannotStart() {
        Internship internship =
                new Internship(
                        10L,
                        5L,
                        3L,
                        NOW.minusDays(1));

        ReflectionTestUtils.setField(
                internship,
                "id",
                20L);

        when(internships.findById(20L))
                .thenReturn(
                        Optional.of(internship));

        assertThatThrownBy(() ->
                service.start(1L, 20L))
                .isInstanceOf(
                        ApiException.class)
                .extracting("code")
                .isEqualTo("MISSING_INFO");

        verify(internships, never())
                .save(any());

        verifyNoInteractions(
                history,
                notifications);
    }

    @Test
    @DisplayName(
            "SV-I07 แก้ไขวันที่และข้อมูลผู้ดูแลสำเร็จ")
    void updateInternship() {
        Internship internship =
                new Internship(
                        10L,
                        5L,
                        3L,
                        NOW.minusDays(1));

        ReflectionTestUtils.setField(
                internship,
                "id",
                20L);

        when(internships.findById(20L))
                .thenReturn(
                        Optional.of(internship));

        InternshipRequest request =
                new InternshipRequest(
                        LocalDate.of(
                                2026,
                                12,
                                1),
                        LocalDate.of(
                                2027,
                                3,
                                31),
                        "คุณผู้ดูแล",
                        "supervisor@example.com",
                        "0812345678");

        InternshipResponse response =
                service.update(
                        1L,
                        20L,
                        request);

        assertThat(response.startDate())
                .isEqualTo(
                        LocalDate.of(
                                2026,
                                12,
                                1));

        assertThat(response.endDate())
                .isEqualTo(
                        LocalDate.of(
                                2027,
                                3,
                                31));

        assertThat(response.supervisorName())
                .isEqualTo("คุณผู้ดูแล");

        assertThat(response.status())
                .isEqualTo(
                        InternshipStatus.NOT_STARTED);

        verify(internships)
                .save(internship);

        verify(notifications)
                .notifyInternshipUpdated(
                        internship);

        verifyNoInteractions(history);
    }

    private void assertInvalidTransition(
            InternshipStatus current,
            InternshipStatus target) {

        assertThatThrownBy(() ->
                service.validateTransition(
                        current,
                        target))
                .isInstanceOf(
                        ApiException.class)
                .extracting("code")
                .isEqualTo(
                        "INVALID_TRANSITION");
    }

    private Match confirmedMatch() {
        Match match = new Match(
                40L,
                2L,
                1L,
                1,
                1L,
                NOW.minusDays(2));

        ReflectionTestUtils.setField(
                match,
                "id",
                10L);

        match.confirm(
                NOW.minusDays(1));

        return match;
    }

    private Application application() {
        Application application =
                new Application(
                        5L,
                        1L,
                        ApplicationStatus.APPROVED);

        ReflectionTestUtils.setField(
                application,
                "id",
                40L);

        return application;
    }

    private Internship completeInternshipInformation() {
        Internship internship =
                new Internship(
                        10L,
                        5L,
                        3L,
                        NOW.minusDays(1));

        ReflectionTestUtils.setField(
                internship,
                "id",
                20L);

        internship.updateDetails(
                LocalDate.of(
                        2026,
                        12,
                        1),
                LocalDate.of(
                        2027,
                        3,
                        31),
                "คุณผู้ดูแล",
                "supervisor@example.com",
                "0812345678",
                1L,
                NOW.minusHours(1));

        return internship;
    }
}