package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.dao.ApplicationRepository;
import com.example.demo.dao.CompanyRankingRepository;
import com.example.demo.dao.EligibilityRecordDao;
import com.example.demo.dao.StudentRepository;
import com.example.demo.dto.RankingItemResponse;
import com.example.demo.dto.RankingRequest;
import com.example.demo.entity.Application;
import com.example.demo.entity.ApplicationStatus;
import com.example.demo.entity.Company;
import com.example.demo.entity.CompanyRanking;
import com.example.demo.entity.CompanyStatus;
import com.example.demo.entity.EligibilityRecord;
import com.example.demo.entity.Role;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;
import com.example.demo.repository.CompanyRepository;

/** U4:T8 Unit test logic การจัดอันดับบริษัท (RankingService) โดยไม่ต่อฐานข้อมูล */
class RankingServiceTest {

    private static final Long USER_ID = 5L;
    private static final Long STUDENT_PK = 50L;
    private static final String STUDENT_ID = "6609650003";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T04:00:00Z"), ZoneId.of("Asia/Bangkok"));

    private CompanyRankingRepository rankings;
    private CompanyRepository companies;
    private StudentRepository students;
    private EligibilityRecordDao eligibility;
    private ApplicationRepository applications;
    private AccessGuard guard;
    private RankingService service;

    /** บริษัทในระบบ: id 1–6 เปิดรับ, id 7 ปิดรับ */
    private final Map<Long, Company> companyTable = new HashMap<>();

    @BeforeEach
    void setUp() {
        rankings = mock(CompanyRankingRepository.class);
        companies = mock(CompanyRepository.class);
        students = mock(StudentRepository.class);
        eligibility = mock(EligibilityRecordDao.class);
        applications = mock(ApplicationRepository.class);
        guard = mock(AccessGuard.class);
        service = new RankingService(rankings, companies, students, eligibility, applications, guard, CLOCK);

        User user = new User(STUDENT_ID, "สมปอง ตั้งใจ", "sompong@dome.tu.ac.th", Role.STUDENT);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        Student student = new Student(USER_ID, STUDENT_ID, "วิทยาศาสตร์และเทคโนโลยี", "วิทยาการคอมพิวเตอร์", 3.25);
        ReflectionTestUtils.setField(student, "id", STUDENT_PK);
        when(guard.require(USER_ID, Role.STUDENT)).thenReturn(user);
        when(students.findByUserId(USER_ID)).thenReturn(Optional.of(student));

        String[] names = { "SCB TechX", "Agoda Services", "KBTG", "LINE MAN Wongnai", "Bitkub", "True Digital Group" };
        for (int i = 0; i < names.length; i++) {
            addCompany((long) i + 1, names[i], CompanyStatus.OPEN);
        }
        addCompany(7L, "PTT Digital", CompanyStatus.CLOSED);
        when(companies.findAllById(any())).thenAnswer(inv -> {
            List<Company> result = new ArrayList<>();
            for (Object id : (Iterable<?>) inv.getArgument(0)) {
                if (companyTable.containsKey(id)) {
                    result.add(companyTable.get(id));
                }
            }
            return result;
        });

        passedEligibility(true);
        when(applications.findByStudentId(STUDENT_PK)).thenReturn(List.of());
    }

    private void addCompany(Long id, String name, CompanyStatus status) {
        Company c = new Company(name, status);
        ReflectionTestUtils.setField(c, "id", id);
        companyTable.put(id, c);
    }

    private void passedEligibility(boolean eligible) {
        when(eligibility.findByStudentIdOrderByCheckedAtDesc(STUDENT_ID)).thenReturn(List.of(
                new EligibilityRecord(STUDENT_ID, eligible, "-", LocalDateTime.of(2026, 10, 18, 9, 0))));
    }

    private void hasApplication(ApplicationStatus status) {
        when(applications.findByStudentId(STUDENT_PK))
                .thenReturn(List.of(new Application(STUDENT_PK, 1L, status)));
    }

    private static RankingRequest request(Long... ids) {
        return new RankingRequest(Arrays.asList(ids));
    }

    @SuppressWarnings("unchecked")
    private List<CompanyRanking> savedRows() {
        ArgumentCaptor<List<CompanyRanking>> captor = ArgumentCaptor.forClass(List.class);
        verify(rankings).saveAll(captor.capture());
        return captor.getValue();
    }

    private void assertRejected(RankingRequest request, int status, String code) {
        assertThatThrownBy(() -> service.saveMyRanking(USER_ID, request))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(status);
                    assertThat(e.getCode()).isEqualTo(code);
                });
        verify(rankings, never()).deleteByStudentId(anyLong());
        verify(rankings, never()).saveAll(any());
    }

    @Test
    @DisplayName("U4-SVC-02 บันทึก 5 อันดับที่ถูกต้อง ได้อันดับ 1–5 ครบตามลำดับที่ส่ง")
    void saveFiveRanks() {
        List<RankingItemResponse> result = service.saveMyRanking(USER_ID, request(3L, 1L, 5L, 2L, 4L));

        assertThat(result).extracting(RankingItemResponse::rank).containsExactly(1, 2, 3, 4, 5);
        assertThat(result).extracting(RankingItemResponse::companyName)
                .containsExactly("KBTG", "SCB TechX", "Bitkub", "Agoda Services", "LINE MAN Wongnai");
        List<CompanyRanking> rows = savedRows();
        assertThat(rows).extracting(CompanyRanking::getCompanyId).containsExactly(3L, 1L, 5L, 2L, 4L);
        assertThat(rows).extracting(CompanyRanking::getRankNo).containsExactly(1, 2, 3, 4, 5);
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.getStudentId()).isEqualTo(STUDENT_PK);
            assertThat(r.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 19, 11, 0));
        });
    }

    @Test
    @DisplayName("U4-SVC-03 มีบริษัทซ้ำในรายการ ปฏิเสธทั้งชุด ไม่บันทึกบางส่วน")
    void duplicateRejectedWhole() {
        assertRejected(request(1L, 2L, 2L, 3L, 4L), 400, "DUPLICATE_COMPANY");
    }

    @Test
    @DisplayName("U4-SVC-04 มีบริษัทที่ปิดรับแล้ว ปฏิเสธพร้อมระบุชื่อบริษัทที่ใช้ไม่ได้")
    void closedCompanyRejected() {
        assertThatThrownBy(() -> service.saveMyRanking(USER_ID, request(1L, 2L, 3L, 4L, 7L)))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(400);
                    assertThat(e.getCode()).isEqualTo("COMPANY_CLOSED");
                    assertThat(e.getMessage()).contains("PTT Digital");
                });
        verify(rankings, never()).saveAll(any());
    }

    @Test
    @DisplayName("U4-SVC-04 มีบริษัทที่ไม่มีอยู่ ปฏิเสธพร้อมระบุ id")
    void unknownCompanyRejected() {
        assertRejected(request(1L, 2L, 3L, 4L, 99_999L), 400, "COMPANY_NOT_FOUND");
    }

    @Test
    @DisplayName("U4-SVC-05 จำนวนไม่ใช่ 5 อันดับ (4 หรือ 6) ปฏิเสธการบันทึก")
    void wrongCount() {
        assertRejected(request(1L, 2L, 3L, 4L), 400, "INVALID_RANKING");
        assertRejected(request(1L, 2L, 3L, 4L, 5L, 6L), 400, "INVALID_RANKING");
        assertRejected(new RankingRequest(null), 400, "INVALID_RANKING");
    }

    @Test
    @DisplayName("U4-SVC-05 มีอันดับที่เว้นว่าง (null) ปฏิเสธการบันทึก")
    void nullCompany() {
        assertRejected(request(1L, 2L, null, 4L, 5L), 400, "INVALID_RANKING");
    }

    @Test
    @DisplayName("U4-SVC-06 บันทึกใหม่แทนชุดเดิม: ลบชุดเดิมของนักศึกษาก่อนแล้วจึงบันทึกชุดใหม่")
    void replaceOldSet() {
        service.saveMyRanking(USER_ID, request(6L, 5L, 4L, 3L, 2L));

        InOrder order = inOrder(rankings);
        order.verify(rankings).deleteByStudentId(STUDENT_PK);
        order.verify(rankings).saveAll(any());
    }

    @Test
    @DisplayName("U4-SVC-07 แก้อันดับหลังยื่นคำร้องแล้ว (SUBMITTED) ปฏิเสธ 409")
    void lockedAfterSubmit() {
        hasApplication(ApplicationStatus.SUBMITTED);
        assertRejected(request(1L, 2L, 3L, 4L, 5L), 409, "RANKING_LOCKED");
    }

    @Test
    @DisplayName("U4-SVC-07 ยื่นคำร้องแล้วแต่ไม่มีผลตรวจคุณสมบัติ ได้ 409 (ตรวจการยื่นก่อน)")
    void lockedCheckedBeforeEligibility() {
        hasApplication(ApplicationStatus.SUBMITTED);
        when(eligibility.findByStudentIdOrderByCheckedAtDesc(STUDENT_ID)).thenReturn(List.of());
        assertRejected(request(1L, 2L, 3L, 4L, 5L), 409, "RANKING_LOCKED");
    }

    @Test
    @DisplayName("U4-SVC-07 คำร้องที่อนุมัติแล้ว (APPROVED) ก็แก้อันดับไม่ได้")
    void lockedAfterApproved() {
        hasApplication(ApplicationStatus.APPROVED);
        assertRejected(request(1L, 2L, 3L, 4L, 5L), 409, "RANKING_LOCKED");
    }

    @Test
    @DisplayName("U4-SVC-08 คำร้องยังเป็นร่าง (DRAFT) ยังแก้อันดับได้")
    void draftStillEditable() {
        hasApplication(ApplicationStatus.DRAFT);
        service.saveMyRanking(USER_ID, request(1L, 2L, 3L, 4L, 5L));
        verify(rankings).saveAll(any());
    }

    @Test
    @DisplayName("U4-SVC-09 ผลตรวจคุณสมบัติล่าสุดไม่ผ่าน ปฏิเสธ 403")
    void notEligible() {
        passedEligibility(false);
        assertRejected(request(1L, 2L, 3L, 4L, 5L), 403, "NOT_ELIGIBLE");
    }

    @Test
    @DisplayName("U4-SVC-09 ยังไม่เคยตรวจคุณสมบัติเลย ปฏิเสธ 403")
    void neverChecked() {
        when(eligibility.findByStudentIdOrderByCheckedAtDesc(STUDENT_ID)).thenReturn(List.of());
        assertRejected(request(1L, 2L, 3L, 4L, 5L), 403, "NOT_ELIGIBLE");
    }

    @Test
    @DisplayName("U4-SVC-10 ดูลำดับที่บันทึกไว้ ได้ชื่อบริษัทเรียงอันดับ 1–5")
    void getMyRanking() {
        when(rankings.findByStudentIdOrderByRankNoAsc(STUDENT_PK)).thenReturn(List.of(
                new CompanyRanking(STUDENT_PK, 2L, 1, LocalDateTime.of(2026, 10, 19, 11, 0)),
                new CompanyRanking(STUDENT_PK, 1L, 2, LocalDateTime.of(2026, 10, 19, 11, 0))));

        List<RankingItemResponse> result = service.getMyRanking(USER_ID);

        assertThat(result).extracting(RankingItemResponse::rank).containsExactly(1, 2);
        assertThat(result).extracting(RankingItemResponse::companyName).containsExactly("Agoda Services", "SCB TechX");
    }

    @Test
    @DisplayName("U4-SVC-10 ยังไม่เคยจัดอันดับ ได้รายการว่าง")
    void emptyRanking() {
        when(rankings.findByStudentIdOrderByRankNoAsc(STUDENT_PK)).thenReturn(List.of());
        assertThat(service.getMyRanking(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("U4-SVC-11 ผู้ใช้ที่ไม่ใช่นักศึกษาเรียก ได้ 403 จาก AccessGuard")
    void notStudent() {
        when(guard.require(1L, Role.STUDENT)).thenThrow(ApiException.forbidden());
        assertThatThrownBy(() -> service.saveMyRanking(1L, request(1L, 2L, 3L, 4L, 5L)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(403));
        verify(rankings, never()).saveAll(any());
    }
}
