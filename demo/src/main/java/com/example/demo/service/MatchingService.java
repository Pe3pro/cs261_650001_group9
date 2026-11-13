package com.example.demo.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dao.ApplicationRepository;
import com.example.demo.dao.CompanyPreferenceRepository;
import com.example.demo.dao.MatchRepository;
import com.example.demo.dao.MatchingRoundRepository;
import com.example.demo.dao.StudentRepository;
import com.example.demo.dto.AutoMatchResponse;
import com.example.demo.dto.CandidateResponse;
import com.example.demo.dto.MatchDecisionRequest;
import com.example.demo.dto.MatchRequest;
import com.example.demo.dto.MatchResponse;
import com.example.demo.dto.MatchSummaryResponse;
import com.example.demo.dto.MatchingRoundRequest;
import com.example.demo.dto.MatchingRoundResponse;
import com.example.demo.dto.UnmatchedItem;
import com.example.demo.entity.Application;
import com.example.demo.entity.ApplicationStatus;
import com.example.demo.entity.CompanyPosition;
import com.example.demo.entity.CompanyPreference;
import com.example.demo.entity.CompanyStatus;
import com.example.demo.entity.Match;
import com.example.demo.entity.MatchStatus;
import com.example.demo.entity.MatchingRound;
import com.example.demo.entity.Role;
import com.example.demo.entity.RoundStatus;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;
import com.example.demo.repository.CompanyPositionRepository;
import com.example.demo.repository.CompanyRepository;

/**
 * U6: การจับคู่นักศึกษากับตำแหน่งตามอันดับและโควตาเป็นรอบ
 * รอบ: OPEN (จับคู่ได้) → ประกาศผล ANNOUNCED (นักศึกษาตอบ) → CLOSED (ปิดตอนเปิดรอบใหม่หลังเลย confirmDeadline)
 * โควตา: จับคู่ลด 1 · สละสิทธิ์ / ยกเลิก / ไม่ตอบภายในกำหนด คืน 1 · อ่านตำแหน่งแบบล็อกแถวกันโควตาเกิน
 */
@Service
@Transactional
public class MatchingService {

    static final String TIMEOUT_REASON = "ไม่ตอบภายในกำหนด";
    private static final DateTimeFormatter TH_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ApplicationRepository applicationRepository;
    private final StudentRepository studentRepository;
    private final CompanyPreferenceRepository preferenceRepository;
    private final CompanyPositionRepository positionRepository;
    private final CompanyRepository companyRepository;
    private final MatchRepository matchRepository;
    private final MatchingRoundRepository roundRepository;
    private final PreferenceService preferenceService;
    private final NotificationService notificationService;
    private final MatchViews views;
    private final AccessGuard guard;
    private final Clock clock;

    public MatchingService(ApplicationRepository applicationRepository, StudentRepository studentRepository,
            CompanyPreferenceRepository preferenceRepository, CompanyPositionRepository positionRepository,
            CompanyRepository companyRepository, MatchRepository matchRepository,
            MatchingRoundRepository roundRepository, PreferenceService preferenceService,
            NotificationService notificationService, MatchViews views, AccessGuard guard, Clock clock) {
        this.applicationRepository = applicationRepository;
        this.studentRepository = studentRepository;
        this.preferenceRepository = preferenceRepository;
        this.positionRepository = positionRepository;
        this.companyRepository = companyRepository;
        this.matchRepository = matchRepository;
        this.roundRepository = roundRepository;
        this.preferenceService = preferenceService;
        this.notificationService = notificationService;
        this.views = views;
        this.guard = guard;
        this.clock = clock;
    }

    /* ===================== เจ้าหน้าที่ ===================== */

    /** U6-SVC-M01: นักศึกษา (ใบสมัคร APPROVED) ที่เลือกตำแหน่งนี้ เรียงตามอันดับ */
    @Transactional(readOnly = true)
    public List<CandidateResponse> candidates(Long userId, Long positionId) {
        guard.require(userId, Role.STAFF);
        if (positionId == null || positionRepository.findById(positionId).isEmpty()) {
            throw ApiException.notFound("ไม่พบตำแหน่งนี้");
        }
        List<CandidateResponse> result = new ArrayList<>();
        for (CompanyPreference pref : preferenceRepository.findByPositionIdOrderByRankNoAsc(positionId)) {
            applicationRepository.findById(pref.getApplicationId())
                    .filter(a -> a.getStatus() == ApplicationStatus.APPROVED)
                    .ifPresent(app -> {
                        String[] student = views.studentOf(app);
                        boolean matched = matchRepository
                                .findFirstByApplicationIdAndStatusIn(app.getId(), PreferenceService.ACTIVE).isPresent();
                        result.add(new CandidateResponse(app.getId(), student[0], student[1], pref.getRankNo(), matched));
                    });
        }
        return result;
    }

    /**
     * U6-SVC-M02: จับคู่อัตโนมัติในรอบที่เปิดอยู่
     * ไล่ใบสมัคร APPROVED ที่ยังไม่มีผล ตามเวลายื่น · แต่ละใบไล่อันดับ 1 → 5 จนเจอตำแหน่งที่เปิดรับและยังมีที่ว่าง
     */
    public AutoMatchResponse autoMatch(Long userId) {
        User staff = guard.require(userId, Role.STAFF);
        MatchingRound round = requireOpenRound();
        LocalDateTime now = LocalDateTime.now(clock);
        List<MatchResponse> matched = new ArrayList<>();
        List<UnmatchedItem> unmatched = new ArrayList<>();
        for (Application app : applicationRepository.findApprovedWithoutActiveMatch()) {
            List<CompanyPreference> prefs = preferenceRepository.findByApplicationIdOrderByRankNoAsc(app.getId());
            if (prefs.isEmpty()) {
                unmatched.add(new UnmatchedItem(app.getId(), views.studentOf(app)[1], "ยังไม่ได้เลือกอันดับ"));
                continue;
            }
            Match match = null;
            for (CompanyPreference pref : prefs) {
                Optional<CompanyPosition> position = positionRepository.findByIdForUpdate(pref.getPositionId())
                        .filter(this::canTake);
                if (position.isPresent()) {
                    match = take(app, position.get(), round, pref.getRankNo(), staff, now);
                    break;
                }
            }
            if (match == null) {
                unmatched.add(new UnmatchedItem(app.getId(), views.studentOf(app)[1],
                        "ตำแหน่งที่เลือกเต็มหรือปิดรับทั้งหมด"));
            } else {
                matched.add(views.toResponse(match, app));
            }
        }
        return new AutoMatchResponse(round.getRoundNo(), matched, unmatched);
    }

    /** U6-SVC-M03: เจ้าหน้าที่จับคู่เอง 1 ใบในรอบที่เปิดอยู่ */
    public MatchResponse manualMatch(Long userId, MatchRequest request) {
        User staff = guard.require(userId, Role.STAFF);
        if (request == null || request.applicationId() == null || request.positionId() == null) {
            throw ApiException.badRequest("กรุณาเลือกใบสมัครและตำแหน่ง");
        }
        MatchingRound round = requireOpenRound();
        Application app = applicationRepository.findById(request.applicationId())
                .orElseThrow(() -> ApiException.notFound("ไม่พบใบสมัคร"));
        if (app.getStatus() != ApplicationStatus.APPROVED) {
            throw new ApiException(409, "APPLICATION_NOT_APPROVED", "ใบสมัครนี้ยังไม่ได้รับการอนุมัติ");
        }
        if (matchRepository.findFirstByApplicationIdAndStatusIn(app.getId(), PreferenceService.ACTIVE).isPresent()) {
            throw new ApiException(409, "ALREADY_MATCHED", "ใบสมัครนี้มีผลจับคู่อยู่แล้ว");
        }
        CompanyPosition position = positionRepository.findByIdForUpdate(request.positionId())
                .orElseThrow(() -> ApiException.notFound("ไม่พบตำแหน่งนี้"));
        if (!canTake(position)) {
            throw new ApiException(409, "QUOTA_FULL", "ตำแหน่งนี้เต็มหรือปิดรับแล้ว");
        }
        Integer rank = preferenceRepository.findByApplicationIdOrderByRankNoAsc(app.getId()).stream()
                .filter(p -> p.getPositionId().equals(position.getId()))
                .map(CompanyPreference::getRankNo).findFirst().orElse(null);
        Match match = take(app, position, round, rank, staff, LocalDateTime.now(clock));
        return views.toResponse(match, app);
    }

    /** U6-SVC-M04: ผลจับคู่ทั้งหมด กรองตามสถานะ และ/หรือ รอบ */
    @Transactional(readOnly = true)
    public List<MatchResponse> listMatches(Long userId, MatchStatus status, Long roundId) {
        guard.require(userId, Role.STAFF);
        List<Match> rows;
        if (roundId != null) {
            rows = matchRepository.findByRoundIdOrderByIdAsc(roundId).stream()
                    .filter(m -> status == null || m.getStatus() == status).toList();
        } else if (status != null) {
            rows = matchRepository.findByStatusOrderByIdAsc(status);
        } else {
            rows = matchRepository.findAllByOrderByIdAsc();
        }
        return rows.stream()
                .map(m -> views.toResponse(m, applicationRepository.findById(m.getApplicationId()).orElse(null)))
                .toList();
    }

    /** U6-SVC-M05: เจ้าหน้าที่ยกเลิกผลที่ยังใช้อยู่ → CANCELLED คืนโควตา (แจ้งนักศึกษาถ้าประกาศผลไปแล้ว) */
    public MatchResponse cancel(Long userId, Long matchId) {
        guard.require(userId, Role.STAFF);
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> ApiException.notFound("ไม่พบผลการจับคู่"));
        if (!match.getStatus().isActive()) {
            throw ApiException.conflict("ยกเลิกได้เฉพาะผลที่ยังรอตอบหรือยืนยันแล้ว");
        }
        match.cancel();
        releaseSlot(match.getPositionId());
        matchRepository.save(match);
        Application app = applicationRepository.findById(match.getApplicationId()).orElse(null);
        boolean announced = roundRepository.findById(match.getRoundId()).map(MatchingRound::isAnnounced).orElse(false);
        if (announced && app != null) {
            notificationService.notifyMatchCancelled(app, views.placeOf(match.getPositionId()));
        }
        return views.toResponse(match, app);
    }

    /* ===================== รอบการจับคู่ ===================== */

    /** U6-SVC-R01: รอบล่าสุด (ทุกบทบาท) */
    @Transactional(readOnly = true)
    public MatchingRoundResponse currentRound(Long userId) {
        guard.require(userId);
        MatchingRound round = roundRepository.findFirstByOrderByRoundNoDesc()
                .orElseThrow(() -> ApiException.notFound("ยังไม่มีรอบการจับคู่"));
        return toRoundResponse(round);
    }

    /**
     * U6-SVC-R02: เปิดรอบใหม่ (ทีละรอบ)
     * รอบก่อนต้องประกาศผลและเลย confirmDeadline แล้ว → ปิดรอบเก่า ผลที่ยังไม่ตอบเป็น DECLINED และคืนโควตา
     */
    public MatchingRoundResponse openRound(Long userId, MatchingRoundRequest request) {
        User staff = guard.require(userId, Role.STAFF);
        LocalDateTime now = LocalDateTime.now(clock);
        if (request == null || request.confirmDeadline() == null) {
            throw ApiException.badRequest("กรุณากำหนดวันหมดเขตยืนยันสิทธิ์");
        }
        if (!request.confirmDeadline().isAfter(now)) {
            throw ApiException.badRequest("วันหมดเขตยืนยันสิทธิ์ต้องเป็นเวลาในอนาคต");
        }
        Optional<MatchingRound> latest = roundRepository.findFirstByOrderByRoundNoDesc();
        if (latest.isPresent()) {
            MatchingRound prev = latest.get();
            if (prev.getStatus() == RoundStatus.OPEN
                    || (prev.getStatus() == RoundStatus.ANNOUNCED && !prev.isDeadlinePassed(now))) {
                throw new ApiException(409, "ROUND_NOT_CLOSED",
                        "รอบที่ " + prev.getRoundNo() + " ยังไม่จบ (ต้องประกาศผลและเลยวันหมดเขตยืนยันสิทธิ์ก่อน)");
            }
            if (prev.getStatus() == RoundStatus.ANNOUNCED) {
                closeExpiredRound(prev, now);
            }
        }
        int nextNo = latest.map(r -> r.getRoundNo() + 1).orElse(1);
        MatchingRound round = roundRepository.save(new MatchingRound(nextNo, staff.getId(), now, request.confirmDeadline()));
        return toRoundResponse(round);
    }

    /** U6-SVC-R03: ประกาศผลรอบ OPEN → ANNOUNCED แล้วแจ้งเตือนนักศึกษาทุกคนในรอบ */
    public MatchingRoundResponse announce(Long userId, Long roundId) {
        guard.require(userId, Role.STAFF);
        MatchingRound round = roundRepository.findById(roundId)
                .orElseThrow(() -> ApiException.notFound("ไม่พบรอบการจับคู่"));
        if (round.getStatus() != RoundStatus.OPEN) {
            throw ApiException.conflict("รอบนี้ประกาศผลไปแล้ว");
        }
        round.announce(LocalDateTime.now(clock));
        roundRepository.save(round);

        Map<Application, String> matched = new LinkedHashMap<>();
        for (Match m : matchRepository.findByRoundIdOrderByIdAsc(round.getId())) {
            if (m.getStatus().isActive()) {
                applicationRepository.findById(m.getApplicationId())
                        .ifPresent(app -> matched.put(app, views.placeOf(m.getPositionId())));
            }
        }
        // ใบสมัครที่เลือกอันดับไว้แต่ยังไม่ได้รับการจับคู่ในรอบนี้
        List<Application> notMatched = applicationRepository.findApprovedWithoutActiveMatch().stream()
                .filter(app -> !preferenceRepository.findByApplicationIdOrderByRankNoAsc(app.getId()).isEmpty())
                .toList();
        notificationService.notifyMatchAnnounced(round, matched, notMatched);
        return toRoundResponse(round);
    }

    /* ===================== นักศึกษา ===================== */

    /** U6-SVC-S01: ผลจับคู่ล่าสุดของตนเอง (404 ถ้ายังไม่มี หรือรอบยังไม่ประกาศผล) */
    @Transactional(readOnly = true)
    public MatchResponse myMatch(Long userId) {
        User user = guard.require(userId, Role.STUDENT);
        Application app = requireApprovedApplication(user);
        Match match = announcedMatch(app).orElseThrow(() -> ApiException.notFound("ยังไม่มีผลการจับคู่ที่ประกาศแล้ว"));
        return views.toResponse(match, app);
    }

    /** U6-SVC-S02: ข้อมูลหน้าผลการจับคู่ทั้งหน้า (404 ถ้ายังไม่ประกาศผล) */
    @Transactional(readOnly = true)
    public MatchSummaryResponse mySummary(Long userId) {
        User user = guard.require(userId, Role.STUDENT);
        Application app = requireApprovedApplication(user);
        Optional<Match> match = announcedMatch(app);
        MatchingRound round = match.flatMap(m -> roundRepository.findById(m.getRoundId()))
                .or(() -> roundRepository.findFirstByStatusInOrderByRoundNoDesc(
                        List.of(RoundStatus.ANNOUNCED, RoundStatus.CLOSED)))
                .orElseThrow(() -> ApiException.notFound("ยังไม่ประกาศผลการจับคู่"));
        MatchResponse view = match.map(m -> views.toResponse(m, app)).orElse(null);
        Optional<CompanyPosition> position = match.flatMap(m -> positionRepository.findById(m.getPositionId()));
        return new MatchSummaryResponse(toRoundResponse(round), preferenceService.preferencesOf(app), view,
                position.map(CompanyPosition::getQuota).orElse(null),
                position.map(CompanyPosition::getAvailableSlots).orElse(null),
                summaryText(view, round));
    }

    /** U6-SVC-S03: ยืนยัน / สละสิทธิ์ (สละสิทธิ์ต้องมีเหตุผล คืนโควตา) ก่อน confirmDeadline */
    public MatchResponse respond(Long userId, Long matchId, MatchDecisionRequest request) {
        User user = guard.require(userId, Role.STUDENT);
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> ApiException.notFound("ไม่พบผลการจับคู่"));
        Application app = applicationRepository.findById(match.getApplicationId())
                .orElseThrow(() -> ApiException.notFound("ไม่พบใบสมัคร"));
        Student student = studentRepository.findByUserId(user.getId()).orElseThrow(ApiException::forbidden);
        if (!student.getId().equals(app.getStudentId())) {
            throw ApiException.forbidden();
        }
        if (request == null || request.accept() == null) {
            throw ApiException.badRequest("กรุณาเลือกยืนยันสิทธิ์หรือสละสิทธิ์");
        }
        MatchingRound round = roundRepository.findById(match.getRoundId())
                .orElseThrow(() -> ApiException.notFound("ไม่พบรอบการจับคู่"));
        LocalDateTime now = LocalDateTime.now(clock);
        if (round.getStatus() != RoundStatus.ANNOUNCED || match.getStatus() != MatchStatus.PROPOSED) {
            throw ApiException.conflict("ผลการจับคู่นี้ตอบไม่ได้แล้ว");
        }
        if (round.isDeadlinePassed(now)) {
            throw new ApiException(409, "DEADLINE_PASSED", "เลยกำหนดยืนยันสิทธิ์แล้ว");
        }
        if (Boolean.TRUE.equals(request.accept())) {
            match.confirm(now);
        } else {
            String reason = request.reason() == null ? "" : request.reason().trim();
            if (reason.isEmpty()) {
                throw new ApiException(400, "REASON_REQUIRED", "กรุณากรอกเหตุผลที่สละสิทธิ์");
            }
            match.decline(reason, now);
            releaseSlot(match.getPositionId());
        }
        matchRepository.save(match);
        notificationService.notifyMatchResponded(match, views.studentOf(app)[1], views.placeOf(match.getPositionId()));
        return views.toResponse(match, app);
    }

    /* ===================== ตัวช่วย ===================== */

    private MatchingRound requireOpenRound() {
        return roundRepository.findFirstByStatus(RoundStatus.OPEN)
                .orElseThrow(() -> new ApiException(409, "NO_OPEN_ROUND", "ยังไม่ได้เปิดรอบการจับคู่"));
    }

    /** ตำแหน่งเปิดรับ มีที่ว่าง และบริษัทเปิดรับ */
    private boolean canTake(CompanyPosition p) {
        boolean companyOpen = companyRepository.findById(p.getCompanyId())
                .map(c -> c.getStatus() == CompanyStatus.OPEN).orElse(false);
        return p.isOpen() && p.hasAvailableSlot() && companyOpen;
    }

    private Match take(Application app, CompanyPosition position, MatchingRound round, Integer rank, User staff,
            LocalDateTime now) {
        position.takeSlot();
        positionRepository.save(position);
        return matchRepository.save(new Match(app.getId(), position.getId(), round.getId(), rank, staff.getId(), now));
    }

    private void releaseSlot(Long positionId) {
        positionRepository.findByIdForUpdate(positionId).ifPresent(p -> {
            p.releaseSlot();
            positionRepository.save(p);
        });
    }

    /** ปิดรอบที่เลยกำหนดยืนยันสิทธิ์: ผลที่ยังไม่ตอบถือว่าสละสิทธิ์ และคืนโควตา */
    private void closeExpiredRound(MatchingRound round, LocalDateTime now) {
        for (Match m : matchRepository.findByRoundIdAndStatus(round.getId(), MatchStatus.PROPOSED)) {
            m.decline(TIMEOUT_REASON, now);
            releaseSlot(m.getPositionId());
            matchRepository.save(m);
        }
        round.close();
        roundRepository.save(round);
    }

    /** ใบสมัครล่าสุดของนักศึกษาที่ได้รับการอนุมัติแล้ว */
    private Application requireApprovedApplication(User user) {
        Student student = studentRepository.findByUserId(user.getId())
                .orElseThrow(() -> ApiException.notFound("ไม่พบข้อมูลนักศึกษา"));
        return applicationRepository.findByStudentIdOrderByIdDesc(student.getId()).stream()
                .filter(a -> a.getStatus() == ApplicationStatus.APPROVED)
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("ยังไม่มีใบสมัครที่ได้รับการอนุมัติ"));
    }

    /** ผลล่าสุดของใบสมัคร ที่รอบประกาศผลแล้ว (ผลที่ถูกยกเลิกไม่นับ) */
    private Optional<Match> announcedMatch(Application app) {
        return matchRepository.findFirstByApplicationIdOrderByIdDesc(app.getId())
                .filter(m -> m.getStatus() != MatchStatus.CANCELLED)
                .filter(m -> roundRepository.findById(m.getRoundId()).map(MatchingRound::isAnnounced).orElse(false));
    }

    private MatchingRoundResponse toRoundResponse(MatchingRound round) {
        long count = round.getId() == null ? 0
                : matchRepository.findByRoundIdOrderByIdAsc(round.getId()).stream()
                        .filter(m -> m.getStatus().isActive()).count();
        return MatchingRoundResponse.from(round, count);
    }

    private static String summaryText(MatchResponse match, MatchingRound round) {
        if (match == null) {
            return "รอบที่ " + round.getRoundNo() + " คุณยังไม่ได้รับการจับคู่ สามารถแก้อันดับเพื่อรอรอบถัดไปได้";
        }
        String where = "ตำแหน่ง " + match.positionName() + " บริษัท " + match.companyName()
                + (match.rank() == null ? "" : " (อันดับ " + match.rank() + ")");
        return switch (match.status()) {
            case PROPOSED -> "คุณได้รับการจับคู่กับ " + where + " กรุณายืนยันภายใน "
                    + round.getConfirmDeadline().format(TH_TIME) + " น.";
            case CONFIRMED -> "คุณยืนยันสิทธิ์ " + where + " แล้ว";
            case DECLINED -> "คุณสละสิทธิ์ " + where + " แล้ว (" + match.declineReason() + ")";
            case CANCELLED -> "ผลการจับคู่ถูกยกเลิก";
        };
    }
}
