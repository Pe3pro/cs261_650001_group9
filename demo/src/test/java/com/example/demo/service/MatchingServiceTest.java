package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.dao.ApplicationRepository;
import com.example.demo.dao.CompanyPreferenceRepository;
import com.example.demo.dao.MatchRepository;
import com.example.demo.dao.MatchingRoundRepository;
import com.example.demo.dao.StudentRepository;
import com.example.demo.dto.AutoMatchResponse;
import com.example.demo.dto.MatchDecisionRequest;
import com.example.demo.dto.MatchRequest;
import com.example.demo.dto.MatchingRoundRequest;
import com.example.demo.entity.Application;
import com.example.demo.entity.ApplicationStatus;
import com.example.demo.entity.Company;
import com.example.demo.entity.CompanyPosition;
import com.example.demo.entity.CompanyPreference;
import com.example.demo.entity.CompanyStatus;
import com.example.demo.entity.Match;
import com.example.demo.entity.MatchStatus;
import com.example.demo.entity.MatchingRound;
import com.example.demo.entity.PositionStatus;
import com.example.demo.entity.Role;
import com.example.demo.entity.RoundStatus;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;
import com.example.demo.repository.CompanyPositionRepository;
import com.example.demo.repository.CompanyRepository;

/** U6:T4 Unit test logic การจับคู่ รอบ และการตอบรับ (MatchingService) */
class MatchingServiceTest {

    /** เวลาปัจจุบันของ test = 10 พ.ย. 2026 10:00 (เวลาไทย) */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 10, 0);

    private ApplicationRepository applications;
    private StudentRepository students;
    private CompanyPreferenceRepository preferences;
    private CompanyPositionRepository positions;
    private CompanyRepository companies;
    private MatchRepository matches;
    private MatchingRoundRepository rounds;
    private NotificationService notifications;
    private MatchViews views;
    private AccessGuard guard;
    private MatchingService service;

    private final User staff = user(1L, Role.STAFF);
    private final User studentUser = user(9L, Role.STUDENT);

    @BeforeEach
    void setUp() {
        applications = mock(ApplicationRepository.class);
        students = mock(StudentRepository.class);
        preferences = mock(CompanyPreferenceRepository.class);
        positions = mock(CompanyPositionRepository.class);
        companies = mock(CompanyRepository.class);
        matches = mock(MatchRepository.class);
        rounds = mock(MatchingRoundRepository.class);
        notifications = mock(NotificationService.class);
        views = mock(MatchViews.class);
        guard = mock(AccessGuard.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-10T03:00:00Z"), ZoneId.of("Asia/Bangkok"));
        PreferenceService preferenceService = mock(PreferenceService.class);
        service = new MatchingService(applications, students, preferences, positions, companies, matches, rounds,
                preferenceService, notifications, views, guard, clock);

        when(guard.require(1L, Role.STAFF)).thenReturn(staff);
        when(guard.require(9L, Role.STUDENT)).thenReturn(studentUser);
        when(guard.require(9L, Role.STAFF)).thenThrow(ApiException.forbidden());
        when(views.studentOf(any())).thenReturn(new String[] { "6609650012", "พิมพ์ชนก ตั้งใจ" });
        when(views.placeOf(anyLong())).thenReturn("ตำแหน่ง Backend Developer บริษัท SCB TechX");
        when(companies.findById(1L)).thenReturn(Optional.of(company(1L)));
        when(matches.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(matches.findFirstByApplicationIdAndStatusIn(anyLong(), any())).thenReturn(Optional.empty());
        Student student = new Student(9L, "6609650012", "วิทยาศาสตร์และเทคโนโลยี", "วิทยาการคอมพิวเตอร์", 3.75);
        ReflectionTestUtils.setField(student, "id", 20L);
        when(students.findByUserId(9L)).thenReturn(Optional.of(student));
    }

    private static User user(Long id, Role role) {
        User u = new User("user" + id, "ผู้ใช้ " + id, null, role);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private static Company company(Long id) {
        Company c = new Company("SCB TechX", CompanyStatus.OPEN);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private static CompanyPosition position(Long id, int quota, int available) {
        CompanyPosition p = new CompanyPosition(1L, "ตำแหน่ง " + id, quota, PositionStatus.OPEN);
        p.setAvailableSlots(available);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private static Application application(Long id, Long studentId) {
        Application a = new Application(studentId, 1L, ApplicationStatus.APPROVED);
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    private static MatchingRound round(Long id, int no, RoundStatus status, LocalDateTime deadline) {
        MatchingRound r = new MatchingRound(no, 1L, NOW.minusDays(5), deadline);
        ReflectionTestUtils.setField(r, "id", id);
        ReflectionTestUtils.setField(r, "status", status);
        return r;
    }

    private static Match match(Long id, Long appId, Long positionId, Long roundId) {
        Match m = new Match(appId, positionId, roundId, 1, 1L, NOW.minusDays(1));
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    /* ---------- จับคู่อัตโนมัติ ---------- */

    @Test
    @DisplayName("U6-SVC-M02 ยังไม่เปิดรอบ จับคู่อัตโนมัติได้ 409 NO_OPEN_ROUND")
    void autoMatchWithoutRound() {
        when(rounds.findFirstByStatus(RoundStatus.OPEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.autoMatch(1L))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("NO_OPEN_ROUND");
    }

    @Test
    @DisplayName("U6-SVC-M02 อันดับ 1 เต็ม เลื่อนไปอันดับ 2 และกินโควตา 1 ที่")
    void autoMatchFallsBackToNextRank() {
        when(rounds.findFirstByStatus(RoundStatus.OPEN)).thenReturn(Optional.of(round(5L, 1, RoundStatus.OPEN, NOW.plusDays(7))));
        Application app = application(40L, 20L);
        when(applications.findApprovedWithoutActiveMatch()).thenReturn(List.of(app));
        when(preferences.findByApplicationIdOrderByRankNoAsc(40L)).thenReturn(List.of(
                new CompanyPreference(40L, 11L, 1, NOW), new CompanyPreference(40L, 21L, 2, NOW)));
        CompanyPosition full = position(11L, 1, 0);
        CompanyPosition open = position(21L, 2, 2);
        when(positions.findByIdForUpdate(11L)).thenReturn(Optional.of(full));
        when(positions.findByIdForUpdate(21L)).thenReturn(Optional.of(open));

        AutoMatchResponse result = service.autoMatch(1L);

        assertThat(result.matched()).hasSize(1);
        assertThat(result.unmatched()).isEmpty();
        assertThat(open.getAvailableSlots()).isEqualTo(1);
        assertThat(full.getAvailableSlots()).isEqualTo(0);
        verify(matches).save(any());
    }

    @Test
    @DisplayName("U6-SVC-M02 ไม่ได้เลือกอันดับ และตำแหน่งเต็มทุกอันดับ คืนในรายการที่จับไม่ได้พร้อมเหตุผล")
    void autoMatchUnmatchedReasons() {
        when(rounds.findFirstByStatus(RoundStatus.OPEN)).thenReturn(Optional.of(round(5L, 1, RoundStatus.OPEN, NOW.plusDays(7))));
        when(applications.findApprovedWithoutActiveMatch()).thenReturn(List.of(application(40L, 20L), application(41L, 21L)));
        when(preferences.findByApplicationIdOrderByRankNoAsc(40L)).thenReturn(List.of());
        when(preferences.findByApplicationIdOrderByRankNoAsc(41L)).thenReturn(List.of(new CompanyPreference(41L, 11L, 1, NOW)));
        when(positions.findByIdForUpdate(11L)).thenReturn(Optional.of(position(11L, 1, 0)));

        AutoMatchResponse result = service.autoMatch(1L);

        assertThat(result.matched()).isEmpty();
        assertThat(result.unmatched()).hasSize(2);
        assertThat(result.unmatched().get(0).reason()).isEqualTo("ยังไม่ได้เลือกอันดับ");
        assertThat(result.unmatched().get(1).reason()).isEqualTo("ตำแหน่งที่เลือกเต็มหรือปิดรับทั้งหมด");
        verify(matches, never()).save(any());
    }

    @Test
    @DisplayName("U6-SVC-M02 นักศึกษาสั่งจับคู่อัตโนมัติไม่ได้ (403)")
    void studentCannotAutoMatch() {
        assertThatThrownBy(() -> service.autoMatch(9L))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(403);
    }

    /* ---------- จับคู่เอง / ยกเลิก ---------- */

    @Test
    @DisplayName("U6-SVC-M03 จับคู่เองกับตำแหน่งที่เต็ม ได้ 409 QUOTA_FULL")
    void manualMatchQuotaFull() {
        when(rounds.findFirstByStatus(RoundStatus.OPEN)).thenReturn(Optional.of(round(5L, 1, RoundStatus.OPEN, NOW.plusDays(7))));
        when(applications.findById(40L)).thenReturn(Optional.of(application(40L, 20L)));
        when(positions.findByIdForUpdate(11L)).thenReturn(Optional.of(position(11L, 1, 0)));

        assertThatThrownBy(() -> service.manualMatch(1L, new MatchRequest(40L, 11L)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("QUOTA_FULL");
    }

    @Test
    @DisplayName("U6-SVC-M05 ยกเลิกผล PROPOSED เปลี่ยนเป็น CANCELLED และคืนโควตา")
    void cancelReleasesSlot() {
        Match m = match(70L, 40L, 11L, 5L);
        CompanyPosition p = position(11L, 1, 0);
        when(matches.findById(70L)).thenReturn(Optional.of(m));
        when(positions.findByIdForUpdate(11L)).thenReturn(Optional.of(p));
        when(rounds.findById(5L)).thenReturn(Optional.of(round(5L, 1, RoundStatus.OPEN, NOW.plusDays(7))));

        service.cancel(1L, 70L);

        assertThat(m.getStatus()).isEqualTo(MatchStatus.CANCELLED);
        assertThat(p.getAvailableSlots()).isEqualTo(1);
        verify(notifications, never()).notifyMatchCancelled(any(), anyString());
    }

    @Test
    @DisplayName("U6-SVC-M05 ยกเลิกผลที่สละสิทธิ์ไปแล้ว ได้ 409")
    void cancelDeclined() {
        Match m = match(70L, 40L, 11L, 5L);
        m.decline("ได้ที่อื่นแล้ว", NOW);
        when(matches.findById(70L)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> service.cancel(1L, 70L))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(409);
    }

    /* ---------- รอบ ---------- */

    @Test
    @DisplayName("U6-SVC-R02 รอบล่าสุดยัง OPEN เปิดรอบใหม่ได้ 409 ROUND_NOT_CLOSED")
    void openRoundWhileOpen() {
        when(rounds.findFirstByOrderByRoundNoDesc()).thenReturn(Optional.of(round(5L, 1, RoundStatus.OPEN, NOW.plusDays(7))));

        assertThatThrownBy(() -> service.openRound(1L, new MatchingRoundRequest(NOW.plusDays(14))))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("ROUND_NOT_CLOSED");
    }

    @Test
    @DisplayName("U6-SVC-R02 วันหมดเขตเป็นอดีต ได้ 400")
    void openRoundPastDeadline() {
        assertThatThrownBy(() -> service.openRound(1L, new MatchingRoundRequest(NOW.minusHours(1))))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(400);
    }

    @Test
    @DisplayName("U6-SVC-R02 รอบก่อนประกาศผลและเลยกำหนดแล้ว ปิดรอบ ผลที่ไม่ตอบเป็น DECLINED คืนโควตา และเปิดรอบ 2")
    void openRoundClosesExpired() {
        MatchingRound prev = round(5L, 1, RoundStatus.ANNOUNCED, NOW.minusDays(1));
        when(rounds.findFirstByOrderByRoundNoDesc()).thenReturn(Optional.of(prev));
        Match pending = match(70L, 40L, 11L, 5L);
        when(matches.findByRoundIdAndStatus(5L, MatchStatus.PROPOSED)).thenReturn(List.of(pending));
        CompanyPosition p = position(11L, 1, 0);
        when(positions.findByIdForUpdate(11L)).thenReturn(Optional.of(p));
        when(rounds.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.openRound(1L, new MatchingRoundRequest(NOW.plusDays(14)));

        assertThat(prev.getStatus()).isEqualTo(RoundStatus.CLOSED);
        assertThat(pending.getStatus()).isEqualTo(MatchStatus.DECLINED);
        assertThat(pending.getDeclineReason()).isEqualTo(MatchingService.TIMEOUT_REASON);
        assertThat(p.getAvailableSlots()).isEqualTo(1);
        assertThat(result.roundNo()).isEqualTo(2);
        assertThat(result.status()).isEqualTo(RoundStatus.OPEN);
    }

    @Test
    @DisplayName("U6-SVC-R03 ประกาศผลรอบที่ประกาศไปแล้ว ได้ 409")
    void announceTwice() {
        when(rounds.findById(5L)).thenReturn(Optional.of(round(5L, 1, RoundStatus.ANNOUNCED, NOW.plusDays(7))));

        assertThatThrownBy(() -> service.announce(1L, 5L))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(409);
        verify(notifications, never()).notifyMatchAnnounced(any(), any(), any());
    }

    @Test
    @DisplayName("U6-SVC-R03 ประกาศผลรอบที่เปิดอยู่ เปลี่ยนเป็น ANNOUNCED และแจ้งเตือนนักศึกษา")
    void announceNotifies() {
        MatchingRound r = round(5L, 1, RoundStatus.OPEN, NOW.plusDays(7));
        when(rounds.findById(5L)).thenReturn(Optional.of(r));
        when(matches.findByRoundIdOrderByIdAsc(5L)).thenReturn(List.of(match(70L, 40L, 11L, 5L)));
        when(applications.findById(40L)).thenReturn(Optional.of(application(40L, 20L)));
        when(applications.findApprovedWithoutActiveMatch()).thenReturn(List.of());

        service.announce(1L, 5L);

        assertThat(r.getStatus()).isEqualTo(RoundStatus.ANNOUNCED);
        verify(notifications).notifyMatchAnnounced(any(), any(), any());
    }

    /* ---------- นักศึกษาตอบรับ ---------- */

    private Match announcedMatchOfStudent(LocalDateTime deadline) {
        Match m = match(70L, 40L, 11L, 5L);
        when(matches.findById(70L)).thenReturn(Optional.of(m));
        when(applications.findById(40L)).thenReturn(Optional.of(application(40L, 20L)));
        when(rounds.findById(5L)).thenReturn(Optional.of(round(5L, 1, RoundStatus.ANNOUNCED, deadline)));
        return m;
    }

    @Test
    @DisplayName("U6-SVC-S03 ยืนยันสิทธิ์ เปลี่ยนเป็น CONFIRMED และแจ้งเจ้าหน้าที่")
    void confirm() {
        Match m = announcedMatchOfStudent(NOW.plusDays(3));

        service.respond(9L, 70L, new MatchDecisionRequest(true, null));

        assertThat(m.getStatus()).isEqualTo(MatchStatus.CONFIRMED);
        verify(notifications).notifyMatchResponded(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("U6-SVC-S03 สละสิทธิ์โดยไม่มีเหตุผล ได้ 400 REASON_REQUIRED")
    void declineWithoutReason() {
        Match m = announcedMatchOfStudent(NOW.plusDays(3));

        assertThatThrownBy(() -> service.respond(9L, 70L, new MatchDecisionRequest(false, "  ")))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("REASON_REQUIRED");
        assertThat(m.getStatus()).isEqualTo(MatchStatus.PROPOSED);
    }

    @Test
    @DisplayName("U6-SVC-S03 สละสิทธิ์พร้อมเหตุผล เปลี่ยนเป็น DECLINED และคืนโควตา")
    void declineReleasesSlot() {
        Match m = announcedMatchOfStudent(NOW.plusDays(3));
        CompanyPosition p = position(11L, 1, 0);
        when(positions.findByIdForUpdate(11L)).thenReturn(Optional.of(p));

        service.respond(9L, 70L, new MatchDecisionRequest(false, "ได้ที่ฝึกงานอื่นแล้ว"));

        assertThat(m.getStatus()).isEqualTo(MatchStatus.DECLINED);
        assertThat(m.getDeclineReason()).isEqualTo("ได้ที่ฝึกงานอื่นแล้ว");
        assertThat(p.getAvailableSlots()).isEqualTo(1);
    }

    @Test
    @DisplayName("U6-SVC-S03 เลยกำหนดยืนยันสิทธิ์ ได้ 409 DEADLINE_PASSED")
    void deadlinePassed() {
        announcedMatchOfStudent(NOW.minusMinutes(1));

        assertThatThrownBy(() -> service.respond(9L, 70L, new MatchDecisionRequest(true, null)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("DEADLINE_PASSED");
    }

    @Test
    @DisplayName("U6-SVC-S03 ตอบผลของคนอื่น ได้ 403")
    void respondNotOwner() {
        Match m = match(71L, 41L, 11L, 5L);
        when(matches.findById(71L)).thenReturn(Optional.of(m));
        when(applications.findById(41L)).thenReturn(Optional.of(application(41L, 99L)));

        assertThatThrownBy(() -> service.respond(9L, 71L, new MatchDecisionRequest(true, null)))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(403);
    }

    @Test
    @DisplayName("U6-SVC-S01 รอบยังไม่ประกาศผล นักศึกษาดูผลของตัวเองไม่ได้ (404)")
    void myMatchHiddenBeforeAnnounce() {
        when(applications.findByStudentIdOrderByIdDesc(20L)).thenReturn(List.of(application(40L, 20L)));
        when(matches.findFirstByApplicationIdOrderByIdDesc(40L)).thenReturn(Optional.of(match(70L, 40L, 11L, 5L)));
        when(rounds.findById(5L)).thenReturn(Optional.of(round(5L, 1, RoundStatus.OPEN, NOW.plusDays(7))));

        assertThatThrownBy(() -> service.myMatch(9L))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(404);
    }
}
