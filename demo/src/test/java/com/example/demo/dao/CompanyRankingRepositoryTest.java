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

import com.example.demo.entity.Company;
import com.example.demo.entity.CompanyRanking;
import com.example.demo.entity.CompanyStatus;
import com.example.demo.repository.CompanyRepository;

/** U4:T9 Unit test ส่วน DAO: CompanyRepository และ CompanyRankingRepository */
@DataJpaTest
class CompanyRankingRepositoryTest {

    private static final Long STUDENT = 50L;
    private static final Long OTHER_STUDENT = 51L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 11, 0);

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CompanyRankingRepository rankingRepository;

    private Company scb;
    private Company agoda;
    private Company kbtg;
    private Company lineman;
    private Company bitkub;

    @BeforeEach
    void setUp() {
        scb = companyRepository.save(new Company("SCB TechX", CompanyStatus.OPEN));
        agoda = companyRepository.save(new Company("Agoda Services", CompanyStatus.OPEN));
        kbtg = companyRepository.save(new Company("KBTG", CompanyStatus.OPEN));
        lineman = companyRepository.save(new Company("LINE MAN Wongnai", CompanyStatus.OPEN));
        bitkub = companyRepository.save(new Company("Bitkub", CompanyStatus.OPEN));
        companyRepository.save(new Company("PTT Digital", CompanyStatus.CLOSED));
    }

    private CompanyRanking rank(Long student, Company company, int rankNo) {
        return new CompanyRanking(student, company.getId(), rankNo, NOW);
    }

    private void saveFive(Long student) {
        rankingRepository.saveAllAndFlush(List.of(
                rank(student, kbtg, 1), rank(student, scb, 2), rank(student, bitkub, 3),
                rank(student, agoda, 4), rank(student, lineman, 5)));
    }

    @Test
    @DisplayName("U4-DAO-01 findByStatus(OPEN) คืนเฉพาะบริษัทที่เปิดรับ")
    void openCompaniesOnly() {
        assertThat(companyRepository.findByStatus(CompanyStatus.OPEN))
                .extracting(Company::getCompanyName)
                .containsExactlyInAnyOrder("SCB TechX", "Agoda Services", "KBTG", "LINE MAN Wongnai", "Bitkub");
    }

    @Test
    @DisplayName("U4-DAO-02 findById ด้วย ID ที่ไม่มีอยู่ คืน Optional.empty")
    void companyNotFound() {
        assertThat(companyRepository.findById(999_999L)).isEmpty();
    }

    @Test
    @DisplayName("U4-DAO-03 บันทึก 5 อันดับ ทุกแถวผูกกับนักศึกษาและบริษัทถูกต้อง")
    void saveFiveRanks() {
        saveFive(STUDENT);

        List<CompanyRanking> rows = rankingRepository.findByStudentIdOrderByRankNoAsc(STUDENT);
        assertThat(rows).hasSize(5);
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.getId()).isNotNull();
            assertThat(r.getStudentId()).isEqualTo(STUDENT);
            assertThat(companyRepository.findById(r.getCompanyId())).isPresent();
        });
    }

    @Test
    @DisplayName("U4-DAO-04 findByStudentIdOrderByRankNoAsc คืนเรียงอันดับ 1–5 แม้บันทึกสลับลำดับ")
    void orderedByRank() {
        rankingRepository.saveAllAndFlush(List.of(
                rank(STUDENT, agoda, 4), rank(STUDENT, kbtg, 1), rank(STUDENT, lineman, 5),
                rank(STUDENT, scb, 2), rank(STUDENT, bitkub, 3)));

        assertThat(rankingRepository.findByStudentIdOrderByRankNoAsc(STUDENT))
                .extracting(CompanyRanking::getRankNo).containsExactly(1, 2, 3, 4, 5);
        assertThat(rankingRepository.findByStudentIdOrderByRankNoAsc(STUDENT))
                .extracting(CompanyRanking::getCompanyId)
                .containsExactly(kbtg.getId(), scb.getId(), bitkub.getId(), agoda.getId(), lineman.getId());
    }

    @Test
    @DisplayName("U4-DAO-05 บันทึกอันดับเดียวกันซ้ำของนักศึกษาคนเดียว Unique (student_id, rank_no) ปฏิเสธ")
    void duplicateRank() {
        rankingRepository.saveAndFlush(rank(STUDENT, scb, 1));

        assertThatThrownBy(() -> rankingRepository.saveAndFlush(rank(STUDENT, agoda, 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("U4-DAO-06 บันทึกบริษัทเดียวกันซ้ำของนักศึกษาคนเดียว Unique (student_id, company_id) ปฏิเสธ")
    void duplicateCompany() {
        rankingRepository.saveAndFlush(rank(STUDENT, scb, 1));

        assertThatThrownBy(() -> rankingRepository.saveAndFlush(rank(STUDENT, scb, 2)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("U4-DAO-07 ค้นหาของนักศึกษาที่ยังไม่เคยจัดอันดับ คืนรายการว่าง")
    void neverRanked() {
        saveFive(STUDENT);

        assertThat(rankingRepository.findByStudentIdOrderByRankNoAsc(OTHER_STUDENT)).isEmpty();
    }

    @Test
    @DisplayName("U4-DAO-08 ค้นหาชื่อบริษัทด้วยอักขระพิเศษ ไม่เกิด SQL Injection")
    void sqlInjection() {
        assertThat(companyRepository.findByCompanyNameContainingIgnoreCase("' OR '1'='1")).isEmpty();
        assertThat(companyRepository.findByCompanyNameContainingIgnoreCase("'; DROP TABLE companies; --")).isEmpty();
        assertThat(companyRepository.count()).isEqualTo(6);
    }

    @Test
    @DisplayName("U4-DAO-09 deleteByStudentId ลบเฉพาะของนักศึกษาคนนั้น แล้วบันทึกอันดับเดิมซ้ำได้")
    void deleteThenReinsert() {
        saveFive(STUDENT);
        saveFive(OTHER_STUDENT);

        int deleted = rankingRepository.deleteByStudentId(STUDENT);
        saveFive(STUDENT);

        assertThat(deleted).isEqualTo(5);
        assertThat(rankingRepository.findByStudentIdOrderByRankNoAsc(STUDENT)).hasSize(5);
        assertThat(rankingRepository.findByStudentIdOrderByRankNoAsc(OTHER_STUDENT)).hasSize(5);
    }
}
