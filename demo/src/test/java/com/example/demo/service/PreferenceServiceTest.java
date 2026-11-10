package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
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
import com.example.demo.dto.PreferenceRequest;
import com.example.demo.dto.PreferenceResponse;
import com.example.demo.entity.Application;
import com.example.demo.entity.ApplicationStatus;
import com.example.demo.entity.Company;
import com.example.demo.entity.CompanyPosition;
import com.example.demo.entity.CompanyStatus;
import com.example.demo.entity.Match;
import com.example.demo.entity.PositionStatus;
import com.example.demo.entity.Role;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;
import com.example.demo.repository.CompanyPositionRepository;
import com.example.demo.repository.CompanyRepository;

/** U6:T4 Unit test logic การเลือกตำแหน่ง 1–5 อันดับ (PreferenceService) */
class PreferenceServiceTest {

    private ApplicationRepository applications;
    private StudentRepository students;
    private CompanyPreferenceRepository preferences;
    private CompanyPositionRepository positions;
    private CompanyRepository companies;
    private MatchRepository matches;
    private MatchingRoundRepository rounds;
    private MatchViews views;
    private AccessGuard guard;
    private PreferenceService service;

    private final User studentUser = user(9L, "6609650012", Role.STUDENT);
    private Application app;

    @BeforeEach
    void setUp() {
        applications = mock(ApplicationRepository.class);
        students = mock(StudentRepository.class);
        preferences = mock(CompanyPreferenceRepository.class);
        positions = mock(CompanyPositionRepository.class);
        companies = mock(CompanyRepository.class);
        matches = mock(MatchRepository.class);
        rounds = mock(MatchingRoundRepository.class);
        views = mock(MatchViews.class);
        guard = mock(AccessGuard.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-10T03:00:00Z"), ZoneId.of("Asia/Bangkok"));
        service = new PreferenceService(applications, students, preferences, positions, companies, matches, rounds,
                views, guard, clock);

        when(guard.require(9L, Role.STUDENT)).thenReturn(studentUser);
        when(guard.require(9L, Role.STUDENT, Role.STAFF)).thenReturn(studentUser);
        Student student = new Student(9L, "6609650012", "วิทยาศาสตร์และเทคโนโลยี", "วิทยาการคอมพิวเตอร์", 3.75);
        ReflectionTestUtils.setField(student, "id", 20L);
        when(students.findByUserId(9L)).thenReturn(Optional.of(student));
        app = application(40L, 20L, ApplicationStatus.APPROVED);
        when(applications.findById(40L)).thenReturn(Optional.of(app));
        when(matches.findFirstByApplicationIdAndStatusIn(anyLong(), any())).thenReturn(Optional.empty());
        when(matches.findFirstByApplicationIdOrderByIdDesc(anyLong())).thenReturn(Optional.empty());
        when(companies.findById(1L)).thenReturn(Optional.of(company(1L, CompanyStatus.OPEN)));
        when(positions.findAllById(any())).thenReturn(List.of(
                position(11L, 1L, PositionStatus.OPEN), position(12L, 1L, PositionStatus.OPEN),
                position(13L, 1L, PositionStatus.CLOSED)));
        when(views.preferences(any(), any())).thenReturn(List.of(
                new PreferenceResponse(1, 12L, "Data Analyst", "SCB TechX", PreferenceResponse.PENDING)));
    }

    private static User user(Long id, String username, Role role) {
        User u = new User(username, "ผู้ใช้ทดสอบ", null, role);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private static Application application(Long id, Long studentId, ApplicationStatus status) {
        Application a = new Application(studentId, 1L, status);
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    private static Company company(Long id, CompanyStatus status) {
        Company c = new Company("SCB TechX", status);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private static CompanyPosition position(Long id, Long companyId, PositionStatus status) {
        CompanyPosition p = new CompanyPosition(companyId, "ตำแหน่ง " + id, 2, status);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    @Test
    @DisplayName("U6-SVC-P02 บันทึก 2 อันดับ ลบของเดิมก่อน แล้วบันทึกตามลำดับที่ส่ง")
    void savePreferences() {
        List<PreferenceResponse> result = service.savePreferences(9L, 40L, new PreferenceRequest(List.of(12L, 11L)));

        assertThat(result).hasSize(1);
        verify(preferences).deleteByApplicationId(40L);
        verify(preferences).saveAll(any());
    }

    @Test
    @DisplayName("U6-SVC-P03 ใบสมัครไม่ใช่ของตัวเอง ได้ 403 และไม่บันทึก")
    void notOwner() {
        when(applications.findById(41L)).thenReturn(Optional.of(application(41L, 99L, ApplicationStatus.APPROVED)));

        assertThatThrownBy(() -> service.savePreferences(9L, 41L, new PreferenceRequest(List.of(11L))))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(403);
        verify(preferences, never()).saveAll(any());
    }

    @Test
    @DisplayName("U6-SVC-P04 ใบสมัครยังไม่ APPROVED ได้ 409 APPLICATION_NOT_APPROVED")
    void notApproved() {
        when(applications.findById(42L)).thenReturn(Optional.of(application(42L, 20L, ApplicationStatus.UNDER_REVIEW)));

        assertThatThrownBy(() -> service.savePreferences(9L, 42L, new PreferenceRequest(List.of(11L))))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("APPLICATION_NOT_APPROVED");
    }

    @Test
    @DisplayName("U6-SVC-P05 มีผลจับคู่ที่ใช้อยู่แล้ว ได้ 409 ALREADY_MATCHED")
    void alreadyMatched() {
        when(matches.findFirstByApplicationIdAndStatusIn(anyLong(), any()))
                .thenReturn(Optional.of(new Match(40L, 11L, 5L, 1, 1L, null)));

        assertThatThrownBy(() -> service.savePreferences(9L, 40L, new PreferenceRequest(List.of(11L))))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("ALREADY_MATCHED");
        verify(preferences, never()).deleteByApplicationId(anyLong());
    }

    @Test
    @DisplayName("U6-SVC-P06 ไม่เลือกเลย ได้ 400 INVALID_PREFERENCE")
    void empty() {
        assertThatThrownBy(() -> service.savePreferences(9L, 40L, new PreferenceRequest(List.of())))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_PREFERENCE");
    }

    @Test
    @DisplayName("U6-SVC-P06 เลือกเกิน 5 อันดับ ได้ 400 INVALID_PREFERENCE")
    void moreThanFive() {
        assertThatThrownBy(() -> service.savePreferences(9L, 40L,
                new PreferenceRequest(List.of(11L, 12L, 13L, 14L, 15L, 16L))))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_PREFERENCE");
    }

    @Test
    @DisplayName("U6-SVC-P06 เลือกตำแหน่งซ้ำ ได้ 400 INVALID_PREFERENCE")
    void duplicate() {
        assertThatThrownBy(() -> service.savePreferences(9L, 40L, new PreferenceRequest(List.of(11L, 11L))))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_PREFERENCE");
    }

    @Test
    @DisplayName("U6-SVC-P06 เลือกตำแหน่งที่ปิดรับ ได้ 400 INVALID_PREFERENCE")
    void closedPosition() {
        assertThatThrownBy(() -> service.savePreferences(9L, 40L, new PreferenceRequest(List.of(11L, 13L))))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_PREFERENCE");
        verify(preferences, never()).saveAll(any());
    }

    @Test
    @DisplayName("U6-SVC-P01 เจ้าของใบสมัครดูอันดับที่เลือกไว้ได้")
    void getPreferences() {
        List<PreferenceResponse> result = service.getPreferences(9L, 40L);

        assertThat(result).hasSize(1);
        verify(preferences).findByApplicationIdOrderByRankNoAsc(40L);
    }
}
