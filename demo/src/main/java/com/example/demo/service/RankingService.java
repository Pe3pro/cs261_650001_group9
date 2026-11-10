package com.example.demo.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dao.ApplicationRepository;
import com.example.demo.dao.CompanyRankingRepository;
import com.example.demo.dao.EligibilityRecordDao;
import com.example.demo.dao.StudentRepository;
import com.example.demo.dto.RankingItemResponse;
import com.example.demo.dto.RankingRequest;
import com.example.demo.entity.ApplicationStatus;
import com.example.demo.entity.Company;
import com.example.demo.entity.CompanyRanking;
import com.example.demo.entity.CompanyStatus;
import com.example.demo.entity.EligibilityRecord;
import com.example.demo.entity.Role;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;
import com.example.demo.repository.CompanyRepository;

/**
 * U4: นักศึกษาจัดอันดับบริษัทที่อยากไปฝึก 5 อันดับ
 * เงื่อนไข (ตรวจตามลำดับ): ยังไม่ได้ยื่นคำร้อง (U3) · ผ่านการตรวจคุณสมบัติ (U2) · ครบ 5 อันดับ · ไม่ซ้ำ
 * · บริษัทต้องมีอยู่และเปิดรับ
 * บันทึกใหม่จะแทนที่ชุดเดิมทั้งหมด และถ้าข้อมูลผิดจะไม่บันทึกแม้แต่บางส่วน
 */
@Service
@Transactional
public class RankingService {

    /** จำนวนอันดับที่ต้องเลือก */
    public static final int REQUIRED_RANKS = 5;

    /** สถานะคำร้องที่ถือว่า "ยื่นแล้ว" ห้ามแก้อันดับ (DRAFT / NEED_CORRECTION / REJECTED ยังแก้ได้) */
    private static final Set<ApplicationStatus> LOCKED_STATUSES = EnumSet.of(
            ApplicationStatus.SUBMITTED, ApplicationStatus.UNDER_REVIEW, ApplicationStatus.APPROVED);

    private final CompanyRankingRepository rankingRepository;
    private final CompanyRepository companyRepository;
    private final StudentRepository studentRepository;
    private final EligibilityRecordDao eligibilityRecordDao;
    private final ApplicationRepository applicationRepository;
    private final AccessGuard guard;
    private final Clock clock;

    public RankingService(CompanyRankingRepository rankingRepository, CompanyRepository companyRepository,
            StudentRepository studentRepository, EligibilityRecordDao eligibilityRecordDao,
            ApplicationRepository applicationRepository, AccessGuard guard, Clock clock) {
        this.rankingRepository = rankingRepository;
        this.companyRepository = companyRepository;
        this.studentRepository = studentRepository;
        this.eligibilityRecordDao = eligibilityRecordDao;
        this.applicationRepository = applicationRepository;
        this.guard = guard;
        this.clock = clock;
    }

    /** U4-API-07: ลำดับที่เคยบันทึกไว้ เรียง 1 → 5 (ยังไม่เคยบันทึก = รายการว่าง) */
    @Transactional(readOnly = true)
    public List<RankingItemResponse> getMyRanking(Long userId) {
        Student student = requireStudent(userId);
        List<CompanyRanking> rows = rankingRepository.findByStudentIdOrderByRankNoAsc(student.getId());
        Map<Long, Company> companies = companyRepository
                .findAllById(rows.stream().map(CompanyRanking::getCompanyId).toList()).stream()
                .collect(Collectors.toMap(Company::getId, Function.identity()));
        return rows.stream()
                .map(r -> new RankingItemResponse(r.getRankNo(), r.getCompanyId(),
                        companies.containsKey(r.getCompanyId())
                                ? companies.get(r.getCompanyId()).getCompanyName() : null))
                .toList();
    }

    /** U4-API-02..06 / U4-SVC-02..07: บันทึกลำดับใหม่แทนชุดเดิมทั้งหมด */
    public List<RankingItemResponse> saveMyRanking(Long userId, RankingRequest request) {
        Student student = requireStudent(userId);
        requireNotSubmitted(student); // ยื่นคำร้องแล้ว = ล็อกอันดับ ตรวจก่อนเรื่องอื่น
        requireEligible(student);
        List<Company> chosen = validate(request);

        rankingRepository.deleteByStudentId(student.getId());
        LocalDateTime now = LocalDateTime.now(clock);
        List<CompanyRanking> rows = new ArrayList<>();
        List<RankingItemResponse> result = new ArrayList<>();
        for (int i = 0; i < chosen.size(); i++) {
            Company c = chosen.get(i);
            rows.add(new CompanyRanking(student.getId(), c.getId(), i + 1, now));
            result.add(new RankingItemResponse(i + 1, c.getId(), c.getCompanyName()));
        }
        rankingRepository.saveAll(rows);
        return result;
    }

    /** ผู้เรียกต้องเป็นนักศึกษาที่มีข้อมูลในตาราง STUDENTS */
    private Student requireStudent(Long userId) {
        User user = guard.require(userId, Role.STUDENT);
        return studentRepository.findByUserId(user.getId()).orElseThrow(ApiException::forbidden);
    }

    /** U4-API-05: ผลตรวจคุณสมบัติล่าสุดต้องผ่าน (ยังไม่เคยตรวจ = ไม่ผ่าน) */
    private void requireEligible(Student student) {
        List<EligibilityRecord> records = eligibilityRecordDao
                .findByStudentIdOrderByCheckedAtDesc(student.getStudentId());
        if (records.isEmpty() || !records.get(0).isEligible()) {
            throw new ApiException(403, "NOT_ELIGIBLE",
                    "คุณยังไม่ผ่านการตรวจสอบคุณสมบัติ จึงยังจัดอันดับบริษัทไม่ได้");
        }
    }

    /** U4-API-06 / U4-SVC-07: ยื่นคำร้องไปแล้ว ห้ามแก้อันดับ */
    private void requireNotSubmitted(Student student) {
        boolean submitted = applicationRepository.findByStudentId(student.getId()).stream()
                .anyMatch(a -> LOCKED_STATUSES.contains(a.getStatus()));
        if (submitted) {
            throw new ApiException(409, "RANKING_LOCKED",
                    "คุณยื่นคำร้องแล้ว ไม่สามารถแก้ไขอันดับบริษัทได้");
        }
    }

    /** U4-SVC-03..05: ตรวจทั้งชุดก่อนบันทึก คืนบริษัทเรียงตามอันดับ */
    private List<Company> validate(RankingRequest request) {
        List<Long> ids = request == null ? null : request.companyIds();
        int count = ids == null ? 0 : ids.size();
        if (count != REQUIRED_RANKS) {
            throw new ApiException(400, "INVALID_RANKING",
                    "ต้องเลือกบริษัทให้ครบ " + REQUIRED_RANKS + " อันดับ (เลือกมา " + count + " อันดับ)");
        }
        if (ids.stream().anyMatch(Objects::isNull)) {
            throw new ApiException(400, "INVALID_RANKING", "มีอันดับที่ยังไม่ได้เลือกบริษัท");
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new ApiException(400, "DUPLICATE_COMPANY", "เลือกบริษัทเดียวกันซ้ำหลายอันดับไม่ได้");
        }

        Map<Long, Company> found = companyRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Company::getId, Function.identity()));
        List<Long> missing = ids.stream().filter(id -> !found.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new ApiException(400, "COMPANY_NOT_FOUND", "ไม่พบบริษัท id: " + join(missing));
        }
        List<String> closed = ids.stream().map(found::get)
                .filter(c -> c.getStatus() != CompanyStatus.OPEN)
                .map(Company::getCompanyName).toList();
        if (!closed.isEmpty()) {
            throw new ApiException(400, "COMPANY_CLOSED", "บริษัทต่อไปนี้ปิดรับแล้ว: " + String.join(", ", closed));
        }
        return ids.stream().map(found::get).toList();
    }

    private static String join(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }
}
