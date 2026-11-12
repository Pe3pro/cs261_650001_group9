package com.example.demo.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

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
import com.example.demo.entity.RoundStatus;
import com.example.demo.entity.Student;
import com.example.demo.repository.CompanyPositionRepository;
import com.example.demo.repository.CompanyRepository;

/** U6:T4 Unit test ส่วน DAO: CompanyPreferenceRepository, MatchRepository, MatchingRoundRepository และ query ที่เพิ่ม */
@DataJpaTest
class MatchingRepositoryTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 10, 9, 0);

    @Autowired
    private ApplicationRepository applications;
    @Autowired
    private StudentRepository students;
    @Autowired
    private CompanyRepository companies;
    @Autowired
    private CompanyPositionRepository positions;
    @Autowired
    private CompanyPreferenceRepository preferences;
    @Autowired
    private MatchRepository matches;
    @Autowired
    private MatchingRoundRepository rounds;

    private Application first;
    private Application second;
    private Application third;
    private CompanyPosition backend;
    private CompanyPosition analyst;
    private MatchingRound round1;

    @BeforeEach
    void setUp() {
        Long scb = companies.save(new Company("SCB TechX", CompanyStatus.OPEN)).getId();
        backend = positions.save(new CompanyPosition(scb, "Backend Developer", 1, PositionStatus.OPEN));
        analyst = positions.save(new CompanyPosition(scb, "Data Analyst", 2, PositionStatus.OPEN));
        first = approved(student(101L, "6609650012"), T0.minusDays(3));
        second = approved(student(102L, "6609650013"), T0.minusDays(2));
        third = approved(student(103L, "6609650014"), T0.minusDays(1));
        Application pending = new Application(student(104L, "6609650015").getId(), 1L, ApplicationStatus.UNDER_REVIEW);
        applications.save(pending);
        round1 = rounds.save(new MatchingRound(1, 1L, T0, T0.plusDays(7)));
    }

    private Student student(Long userId, String code) {
        return students.save(new Student(userId, code, "วิทยาศาสตร์และเทคโนโลยี", "วิทยาการคอมพิวเตอร์", 3.0));
    }

    private Application approved(Student s, LocalDateTime submittedAt) {
        Application a = new Application(s.getId(), 1L, ApplicationStatus.APPROVED);
        a.setSubmittedAt(submittedAt);
        return applications.save(a);
    }

    @Test
    @DisplayName("DAO-U6-01 บันทึกอันดับแล้วอ่านคืนเรียง 1 → n")
    void preferencesOrdered() {
        preferences.saveAll(List.of(new CompanyPreference(first.getId(), analyst.getId(), 2, T0),
                new CompanyPreference(first.getId(), backend.getId(), 1, T0)));

        List<CompanyPreference> rows = preferences.findByApplicationIdOrderByRankNoAsc(first.getId());

        assertThat(rows).extracting(CompanyPreference::getRankNo).containsExactly(1, 2);
        assertThat(rows.get(0).getPositionId()).isEqualTo(backend.getId());
    }

    @Test
    @DisplayName("DAO-U6-02 ใบสมัครเดียวใช้อันดับซ้ำไม่ได้ (unique application_id, rank_no)")
    void duplicateRankRejected() {
        preferences.saveAndFlush(new CompanyPreference(first.getId(), backend.getId(), 1, T0));

        assertThatThrownBy(() -> preferences.saveAndFlush(new CompanyPreference(first.getId(), analyst.getId(), 1, T0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("DAO-U6-03 ลบอันดับเดิมของใบสมัครเดียว ไม่กระทบใบอื่น")
    void deleteByApplication() {
        preferences.saveAllAndFlush(List.of(new CompanyPreference(first.getId(), backend.getId(), 1, T0),
                new CompanyPreference(second.getId(), backend.getId(), 1, T0)));

        int deleted = preferences.deleteByApplicationId(first.getId());

        assertThat(deleted).isEqualTo(1);
        assertThat(preferences.findByApplicationIdOrderByRankNoAsc(first.getId())).isEmpty();
        assertThat(preferences.findByPositionIdOrderByRankNoAsc(backend.getId())).hasSize(1);
    }

    @Test
    @DisplayName("DAO-U6-04 ใบสมัคร APPROVED ที่ยังไม่มีผลที่ใช้อยู่ เรียงตามเวลายื่น (ผล DECLINED ไม่นับ)")
    void approvedWithoutActiveMatch() {
        matches.save(new Match(first.getId(), backend.getId(), round1.getId(), 1, 1L, T0));
        Match declined = new Match(second.getId(), analyst.getId(), round1.getId(), 1, 1L, T0);
        declined.decline("ได้ที่อื่นแล้ว", T0.plusHours(1));
        matches.saveAndFlush(declined);

        List<Application> result = applications.findApprovedWithoutActiveMatch();

        assertThat(result).extracting(Application::getId).containsExactly(second.getId(), third.getId());
    }

    @Test
    @DisplayName("DAO-U6-05 หาผลที่ใช้อยู่ของใบสมัคร และผลล่าสุด")
    void activeAndLatestMatch() {
        Match old = new Match(first.getId(), backend.getId(), round1.getId(), 1, 1L, T0);
        old.cancel();
        matches.save(old);
        Match latest = matches.saveAndFlush(new Match(first.getId(), analyst.getId(), round1.getId(), 2, 1L, T0.plusHours(1)));

        assertThat(matches.findFirstByApplicationIdAndStatusIn(first.getId(), List.of(MatchStatus.PROPOSED, MatchStatus.CONFIRMED)))
                .get().extracting(Match::getId).isEqualTo(latest.getId());
        assertThat(matches.findFirstByApplicationIdOrderByIdDesc(first.getId())).get()
                .extracting(Match::getPositionId).isEqualTo(analyst.getId());
        assertThat(matches.findByRoundIdAndStatus(round1.getId(), MatchStatus.CANCELLED)).hasSize(1);
    }

    @Test
    @DisplayName("DAO-U6-05b ใบสมัครเดียวมีผล PROPOSED / CONFIRMED 2 รายการไม่ได้ (uk_match_active_application) แต่ผลที่ยกเลิกแล้วซ้ำได้")
    void oneActiveMatchPerApplication() {
        Match cancelled = new Match(second.getId(), backend.getId(), round1.getId(), 1, 1L, T0);
        cancelled.cancel();
        matches.saveAndFlush(cancelled);
        Match declined = new Match(second.getId(), analyst.getId(), round1.getId(), 2, 1L, T0);
        declined.decline("ได้ที่อื่นแล้ว", T0);
        matches.saveAndFlush(declined);
        matches.saveAndFlush(new Match(second.getId(), backend.getId(), round1.getId(), 1, 1L, T0.plusHours(1)));

        assertThatThrownBy(() -> matches.saveAndFlush(
                new Match(second.getId(), analyst.getId(), round1.getId(), 2, 1L, T0.plusHours(2))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(e.getMessage().toLowerCase()).contains(Match.UK_ACTIVE_APPLICATION));
    }

    @Test
    @DisplayName("DAO-U6-06 รอบล่าสุด และรอบที่ประกาศผลแล้วล่าสุด")
    void rounds() {
        round1.announce(T0.plusDays(1));
        rounds.save(round1);
        MatchingRound round2 = rounds.saveAndFlush(new MatchingRound(2, 1L, T0.plusDays(8), T0.plusDays(14)));

        assertThat(rounds.findFirstByOrderByRoundNoDesc()).get().extracting(MatchingRound::getRoundNo).isEqualTo(2);
        assertThat(rounds.findFirstByStatus(RoundStatus.OPEN)).get().extracting(MatchingRound::getId).isEqualTo(round2.getId());
        assertThat(rounds.findFirstByStatusInOrderByRoundNoDesc(List.of(RoundStatus.ANNOUNCED, RoundStatus.CLOSED)))
                .get().extracting(MatchingRound::getRoundNo).isEqualTo(1);
    }

    @Test
    @DisplayName("DAO-U6-07 เลขรอบซ้ำไม่ได้ (unique round_no)")
    void duplicateRoundNo() {
        assertThatThrownBy(() -> rounds.saveAndFlush(new MatchingRound(1, 1L, T0, T0.plusDays(3))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("DAO-U6-08 อ่านตำแหน่งแบบล็อกแถว แล้วกินโควตา / คืนโควตาได้")
    void lockAndTakeSlot() {
        CompanyPosition p = positions.findByIdForUpdate(analyst.getId()).orElseThrow();
        p.takeSlot();
        positions.saveAndFlush(p);

        CompanyPosition reloaded = positions.findById(analyst.getId()).orElseThrow();
        assertThat(reloaded.getAvailableSlots()).isEqualTo(1);
        reloaded.releaseSlot();
        reloaded.releaseSlot();
        assertThat(reloaded.getAvailableSlots()).isEqualTo(2);
    }
}
