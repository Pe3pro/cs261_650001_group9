package com.example.demo.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.example.demo.entity.CompanyPreference;
import com.example.demo.entity.CompanyStatus;
import com.example.demo.entity.Match;
import com.example.demo.entity.MatchStatus;
import com.example.demo.entity.MatchingRound;
import com.example.demo.entity.Role;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;
import com.example.demo.repository.CompanyPositionRepository;
import com.example.demo.repository.CompanyRepository;

/**
 * U6: นักศึกษาที่ใบสมัครได้รับอนุมัติ (APPROVED) เลือกตำแหน่งที่สนใจ 1–5 อันดับ
 * เงื่อนไข (ตรวจตามลำดับ): เป็นเจ้าของใบสมัคร · ใบสมัคร APPROVED · ยังไม่มีผลจับคู่ที่ใช้อยู่ · อันดับถูกต้อง
 */
@Service
@Transactional
public class PreferenceService {

    public static final int MAX_RANKS = 5;
    static final List<MatchStatus> ACTIVE = List.of(MatchStatus.PROPOSED, MatchStatus.CONFIRMED);

    private final ApplicationRepository applicationRepository;
    private final StudentRepository studentRepository;
    private final CompanyPreferenceRepository preferenceRepository;
    private final CompanyPositionRepository positionRepository;
    private final CompanyRepository companyRepository;
    private final MatchRepository matchRepository;
    private final MatchingRoundRepository roundRepository;
    private final MatchViews views;
    private final AccessGuard guard;
    private final Clock clock;

    public PreferenceService(ApplicationRepository applicationRepository, StudentRepository studentRepository,
            CompanyPreferenceRepository preferenceRepository, CompanyPositionRepository positionRepository,
            CompanyRepository companyRepository, MatchRepository matchRepository,
            MatchingRoundRepository roundRepository, MatchViews views, AccessGuard guard, Clock clock) {
        this.applicationRepository = applicationRepository;
        this.studentRepository = studentRepository;
        this.preferenceRepository = preferenceRepository;
        this.positionRepository = positionRepository;
        this.companyRepository = companyRepository;
        this.matchRepository = matchRepository;
        this.roundRepository = roundRepository;
        this.views = views;
        this.guard = guard;
        this.clock = clock;
    }

    /** U6-SVC-P01: ดูอันดับที่เลือกไว้ พร้อมผลรายอันดับ (เจ้าของใบสมัคร หรือเจ้าหน้าที่) */
    @Transactional(readOnly = true)
    public List<PreferenceResponse> getPreferences(Long userId, Long applicationId) {
        User user = guard.require(userId, Role.STUDENT, Role.STAFF);
        Application app = findApplication(applicationId);
        if (user.getRole() == Role.STUDENT) {
            requireOwner(user, app);
        }
        return preferencesOf(app);
    }

    /** U6-SVC-P02: บันทึกตำแหน่ง 1–5 อันดับ (แทนที่ของเดิมทั้งหมด) */
    public List<PreferenceResponse> savePreferences(Long userId, Long applicationId, PreferenceRequest request) {
        User user = guard.require(userId, Role.STUDENT);
        Application app = findApplication(applicationId);
        requireOwner(user, app);
        if (app.getStatus() != ApplicationStatus.APPROVED) {
            throw new ApiException(409, "APPLICATION_NOT_APPROVED",
                    "เลือกอันดับได้หลังใบสมัครได้รับการอนุมัติแล้วเท่านั้น");
        }
        if (matchRepository.findFirstByApplicationIdAndStatusIn(app.getId(), ACTIVE).isPresent()) {
            throw new ApiException(409, "ALREADY_MATCHED", "คุณได้รับการจับคู่แล้ว แก้อันดับไม่ได้");
        }
        List<Long> ids = validate(request);

        preferenceRepository.deleteByApplicationId(app.getId());
        LocalDateTime now = LocalDateTime.now(clock);
        List<CompanyPreference> rows = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            rows.add(new CompanyPreference(app.getId(), ids.get(i), i + 1, now));
        }
        preferenceRepository.saveAll(rows);
        return preferencesOf(app);
    }

    /** อันดับของใบสมัคร พร้อมผลจากรอบที่ประกาศแล้วล่าสุด (ใช้ใน MatchingService ด้วย) */
    List<PreferenceResponse> preferencesOf(Application app) {
        List<CompanyPreference> prefs = preferenceRepository.findByApplicationIdOrderByRankNoAsc(app.getId());
        Match announced = matchRepository.findFirstByApplicationIdOrderByIdDesc(app.getId())
                .filter(m -> roundRepository.findById(m.getRoundId()).map(MatchingRound::isAnnounced).orElse(false))
                .orElse(null);
        return views.preferences(prefs, announced);
    }

    /** U6-SVC-P03..P06: 1–5 อันดับ ไม่ว่าง ไม่ซ้ำ ตำแหน่งมีอยู่จริง และเปิดรับ (บริษัทต้องเปิดรับด้วย) */
    private List<Long> validate(PreferenceRequest request) {
        List<Long> ids = request == null ? null : request.positionIds();
        if (ids == null || ids.isEmpty() || ids.size() > MAX_RANKS) {
            throw invalid("กรุณาเลือกตำแหน่ง 1–" + MAX_RANKS + " อันดับ");
        }
        if (ids.stream().anyMatch(Objects::isNull)) {
            throw invalid("มีอันดับที่ยังไม่ได้เลือกตำแหน่ง");
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw invalid("เลือกตำแหน่งเดียวกันซ้ำหลายอันดับไม่ได้");
        }
        Map<Long, CompanyPosition> found = new HashMap<>();
        positionRepository.findAllById(ids).forEach(p -> found.put(p.getId(), p));
        for (Long id : ids) {
            CompanyPosition p = found.get(id);
            if (p == null) {
                throw invalid("ไม่พบตำแหน่งที่เลือก (id " + id + ")");
            }
            boolean companyOpen = companyRepository.findById(p.getCompanyId())
                    .map(Company::getStatus).map(s -> s == CompanyStatus.OPEN).orElse(false);
            if (!p.isOpen() || !companyOpen) {
                throw invalid("ตำแหน่ง " + p.getPositionName() + " ปิดรับแล้ว");
            }
        }
        return ids;
    }

    private Application findApplication(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> ApiException.notFound("ไม่พบใบสมัคร"));
    }

    /** นักศึกษาดู / แก้ได้เฉพาะใบสมัครของตัวเอง */
    private void requireOwner(User user, Application app) {
        Student student = studentRepository.findByUserId(user.getId()).orElseThrow(ApiException::forbidden);
        if (!student.getId().equals(app.getStudentId())) {
            throw ApiException.forbidden();
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(400, "INVALID_PREFERENCE", message);
    }
}
