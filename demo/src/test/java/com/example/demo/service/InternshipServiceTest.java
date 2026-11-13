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
                "à¹€à¸ˆà¹‰à¸²à¸«à¸™à¹‰à¸²à¸—à¸µà¹ˆà¸—à¸”à¸ªà¸­à¸š",
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
            "SV-I01 à¸ªà¸£à¹‰à¸²à¸‡ Internship à¸ˆà¸²à¸ Match à¸—à¸µà¹ˆà¸¢à¸·à¸™à¸¢à¸±à¸™à¹à¸¥à¹‰à¸§")
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
            "SV-I02 Match à¸—à¸µà¹ˆà¸¡à¸µ Internship à¹à¸¥à¹‰à¸§à¹„à¸¡à¹ˆà¸ªà¸£à¹‰à¸²à¸‡à¸‹à¹‰à¸³")
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
            "SV-I03 à¹€à¸ªà¹‰à¸™à¸—à¸²à¸‡à¹€à¸›à¸¥à¸µà¹ˆà¸¢à¸™à¸ªà¸–à¸²à¸™à¸°à¸—à¸µà¹ˆà¸–à¸¹à¸à¸•à¹‰à¸­à¸‡")
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
            "SV-I04 à¹€à¸ªà¹‰à¸™à¸—à¸²à¸‡à¹€à¸›à¸¥à¸µà¹ˆà¸¢à¸™à¸ªà¸–à¸²à¸™à¸°à¸—à¸µà¹ˆà¸œà¸´à¸”à¹„à¸”à¹‰ INVALID_TRANSITION")
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
            "SV-I05 à¹€à¸£à¸´à¹ˆà¸¡à¸à¸¶à¸à¸‡à¸²à¸™ à¸šà¸±à¸™à¸—à¸¶à¸à¸›à¸£à¸°à¸§à¸±à¸•à¸´à¹à¸¥à¸°à¹à¸ˆà¹‰à¸‡à¹€à¸•à¸·à¸­à¸™")
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
            "SV-I06 à¸‚à¹‰à¸­à¸¡à¸¹à¸¥à¹„à¸¡à¹ˆà¸„à¸£à¸š à¹€à¸£à¸´à¹ˆà¸¡à¸à¸¶à¸à¸‡à¸²à¸™à¹„à¸¡à¹ˆà¹„à¸”à¹‰")
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
            "SV-I07 à¹à¸à¹‰à¹„à¸‚à¸§à¸±à¸™à¸—à¸µà¹ˆà¹à¸¥à¸°à¸‚à¹‰à¸­à¸¡à¸¹à¸¥à¸œà¸¹à¹‰à¸”à¸¹à¹à¸¥à¸ªà¸³à¹€à¸£à¹‡à¸ˆ")
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
                        "à¸„à¸¸à¸“à¸œà¸¹à¹‰à¸”à¸¹à¹à¸¥",
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
                .isEqualTo("à¸„à¸¸à¸“à¸œà¸¹à¹‰à¸”à¸¹à¹à¸¥");

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
                "à¸„à¸¸à¸“à¸œà¸¹à¹‰à¸”à¸¹à¹à¸¥",
                "supervisor@example.com",
                "0812345678",
                1L,
                NOW.minusHours(1));

        return internship;
    }

    @Test
    @DisplayName(
            "SV-I08 receives MatchConfirmedEvent and creates Internship")
    void matchConfirmedEventCreatesInternship() {
        InternshipService spyService =
                org.mockito.Mockito.spy(service);

        com.example.demo.event.MatchConfirmedEvent event =
                new com.example.demo.event.MatchConfirmedEvent(
                        31L,
                        50L,
                        7L,
                        1L
                );

        org.mockito.Mockito.doReturn(null)
                .when(spyService)
                .createFromMatch(31L);

        spyService.onMatchConfirmed(
                event
        );

        org.mockito.Mockito.verify(spyService)
                .createFromMatch(31L);
    }

    @Test
    @DisplayName(
            "SV-I09 propagates exception for transaction rollback")
    void matchConfirmedEventPropagatesException() {
        InternshipService spyService =
                org.mockito.Mockito.spy(service);

        com.example.demo.event.MatchConfirmedEvent event =
                new com.example.demo.event.MatchConfirmedEvent(
                        31L,
                        50L,
                        7L,
                        1L
                );

        RuntimeException failure =
                new RuntimeException(
                        "Cannot create internship"
                );

        org.mockito.Mockito.doThrow(failure)
                .when(spyService)
                .createFromMatch(31L);

        assertThatThrownBy(() ->
                spyService.onMatchConfirmed(
                        event
                ))
                .isSameAs(failure);

        org.mockito.Mockito.verify(spyService)
                .createFromMatch(31L);
    }
}
