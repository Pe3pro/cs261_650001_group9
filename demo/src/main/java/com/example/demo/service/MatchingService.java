package com.example.demo.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
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
 * U7: สร้างข้อมูลการฝึกงานอัตโนมัติเมื่อยืนยันผลจับคู่
 */
@Service
@Transactional
public class MatchingService {

    static final String TIMEOUT_REASON =
            "ไม่ตอบภายในกำหนด";

    private static final DateTimeFormatter TH_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

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

    /**
     * U7: ใช้สร้างข้อมูลการฝึกงานหลังนักศึกษายืนยันผลจับคู่
     * ใช้ setter injection เพื่อไม่กระทบ constructor และ test เดิมของ U6
     */
    private InternshipService internshipService;

    @Autowired(required = false)
    public void setInternshipService(
            InternshipService internshipService) {
        this.internshipService = internshipService;
    }

    public MatchingService(
            ApplicationRepository applicationRepository,
            StudentRepository studentRepository,
            CompanyPreferenceRepository preferenceRepository,
            CompanyPositionRepository positionRepository,
            CompanyRepository companyRepository,
            MatchRepository matchRepository,
            MatchingRoundRepository roundRepository,
            PreferenceService preferenceService,
            NotificationService notificationService,
            MatchViews views,
            AccessGuard guard,
            Clock clock) {

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

    /**
     * U6-SVC-M01: นักศึกษาที่เลือกตำแหน่งนี้
     * เรียงตามอันดับ
     */
    @Transactional(readOnly = true)
    public List<CandidateResponse> candidates(
            Long userId,
            Long positionId) {

        guard.require(userId, Role.STAFF);

        if (positionId == null
                || positionRepository.findById(positionId).isEmpty()) {
            throw ApiException.notFound("ไม่พบตำแหน่งนี้");
        }

        List<CandidateResponse> result = new ArrayList<>();

        for (CompanyPreference preference :
                preferenceRepository
                        .findByPositionIdOrderByRankNoAsc(positionId)) {

            applicationRepository
                    .findById(preference.getApplicationId())
                    .filter(application ->
                            application.getStatus()
                                    == ApplicationStatus.APPROVED)
                    .ifPresent(application -> {
                        String[] student =
                                views.studentOf(application);

                        boolean matched =
                                matchRepository
                                        .findFirstByApplicationIdAndStatusIn(
                                                application.getId(),
                                                PreferenceService.ACTIVE)
                                        .isPresent();

                        result.add(new CandidateResponse(
                                application.getId(),
                                student[0],
                                student[1],
                                preference.getRankNo(),
                                matched));
                    });
        }

        return result;
    }

    /**
     * U6-SVC-M02: จับคู่อัตโนมัติในรอบที่เปิดอยู่
     */
    public AutoMatchResponse autoMatch(Long userId) {
        User staff = guard.require(userId, Role.STAFF);
        MatchingRound round = requireOpenRound();
        LocalDateTime now = LocalDateTime.now(clock);

        List<MatchResponse> matched = new ArrayList<>();
        List<UnmatchedItem> unmatched = new ArrayList<>();

        for (Application application :
                applicationRepository
                        .findApprovedWithoutActiveMatch()) {

            List<CompanyPreference> preferences =
                    preferenceRepository
                            .findByApplicationIdOrderByRankNoAsc(
                                    application.getId());

            if (preferences.isEmpty()) {
                unmatched.add(new UnmatchedItem(
                        application.getId(),
                        views.studentOf(application)[1],
                        "ยังไม่ได้เลือกอันดับ"));
                continue;
            }

            Match match = null;

            for (CompanyPreference preference : preferences) {
                Optional<CompanyPosition> position =
                        positionRepository
                                .findByIdForUpdate(
                                        preference.getPositionId())
                                .filter(this::canTake);

                if (position.isPresent()) {
                    match = take(
                            application,
                            position.get(),
                            round,
                            preference.getRankNo(),
                            staff,
                            now);
                    break;
                }
            }

            if (match == null) {
                unmatched.add(new UnmatchedItem(
                        application.getId(),
                        views.studentOf(application)[1],
                        "ตำแหน่งที่เลือกเต็มหรือปิดรับทั้งหมด"));
            } else {
                matched.add(
                        views.toResponse(match, application));
            }
        }

        return new AutoMatchResponse(
                round.getRoundNo(),
                matched,
                unmatched);
    }

    /**
     * U6-SVC-M03: เจ้าหน้าที่จับคู่เอง
     */
    public MatchResponse manualMatch(
            Long userId,
            MatchRequest request) {

        User staff = guard.require(userId, Role.STAFF);

        if (request == null
                || request.applicationId() == null
                || request.positionId() == null) {
            throw ApiException.badRequest(
                    "กรุณาเลือกใบสมัครและตำแหน่ง");
        }

        MatchingRound round = requireOpenRound();

        Application application =
                applicationRepository
                        .findById(request.applicationId())
                        .orElseThrow(() ->
                                ApiException.notFound(
                                        "ไม่พบใบสมัคร"));

        if (application.getStatus()
                != ApplicationStatus.APPROVED) {
            throw new ApiException(
                    409,
                    "APPLICATION_NOT_APPROVED",
                    "ใบสมัครนี้ยังไม่ได้รับการอนุมัติ");
        }

        boolean alreadyMatched =
                matchRepository
                        .findFirstByApplicationIdAndStatusIn(
                                application.getId(),
                                PreferenceService.ACTIVE)
                        .isPresent();

        if (alreadyMatched) {
            throw new ApiException(
                    409,
                    "ALREADY_MATCHED",
                    "ใบสมัครนี้มีผลจับคู่อยู่แล้ว");
        }

        CompanyPosition position =
                positionRepository
                        .findByIdForUpdate(request.positionId())
                        .orElseThrow(() ->
                                ApiException.notFound(
                                        "ไม่พบตำแหน่งนี้"));

        if (!canTake(position)) {
            throw new ApiException(
                    409,
                    "QUOTA_FULL",
                    "ตำแหน่งนี้เต็มหรือปิดรับแล้ว");
        }

        Integer rank =
                preferenceRepository
                        .findByApplicationIdOrderByRankNoAsc(
                                application.getId())
                        .stream()
                        .filter(preference ->
                                preference.getPositionId()
                                        .equals(position.getId()))
                        .map(CompanyPreference::getRankNo)
                        .findFirst()
                        .orElse(null);

        Match match = take(
                application,
                position,
                round,
                rank,
                staff,
                LocalDateTime.now(clock));

        return views.toResponse(match, application);
    }

    /**
     * U6-SVC-M04: ผลจับคู่ทั้งหมด
     */
    @Transactional(readOnly = true)
    public List<MatchResponse> listMatches(
            Long userId,
            MatchStatus status,
            Long roundId) {

        guard.require(userId, Role.STAFF);

        List<Match> rows;

        if (roundId != null) {
            rows = matchRepository
                    .findByRoundIdOrderByIdAsc(roundId)
                    .stream()
                    .filter(match ->
                            status == null
                                    || match.getStatus() == status)
                    .toList();

        } else if (status != null) {
            rows = matchRepository
                    .findByStatusOrderByIdAsc(status);

        } else {
            rows = matchRepository.findAllByOrderByIdAsc();
        }

        return rows.stream()
                .map(match -> views.toResponse(
                        match,
                        applicationRepository
                                .findById(match.getApplicationId())
                                .orElse(null)))
                .toList();
    }

    /**
     * U6-SVC-M05: เจ้าหน้าที่ยกเลิกผลจับคู่
     */
    public MatchResponse cancel(
            Long userId,
            Long matchId) {

        guard.require(userId, Role.STAFF);

        Match match = matchRepository.findById(matchId)
                .orElseThrow(() ->
                        ApiException.notFound(
                                "ไม่พบผลการจับคู่"));

        if (!match.getStatus().isActive()) {
            throw ApiException.conflict(
                    "ยกเลิกได้เฉพาะผลที่ยังรอตอบหรือยืนยันแล้ว");
        }

        match.cancel();
        releaseSlot(match.getPositionId());
        matchRepository.save(match);

        Application application =
                applicationRepository
                        .findById(match.getApplicationId())
                        .orElse(null);

        boolean announced =
                roundRepository
                        .findById(match.getRoundId())
                        .map(MatchingRound::isAnnounced)
                        .orElse(false);

        if (announced && application != null) {
            notificationService.notifyMatchCancelled(
                    application,
                    views.placeOf(match.getPositionId()));
        }

        return views.toResponse(match, application);
    }

    /* ===================== รอบการจับคู่ ===================== */

    /**
     * U6-SVC-R01: รอบล่าสุด
     */
    @Transactional(readOnly = true)
    public MatchingRoundResponse currentRound(Long userId) {
        guard.require(userId);

        MatchingRound round =
                roundRepository
                        .findFirstByOrderByRoundNoDesc()
                        .orElseThrow(() ->
                                ApiException.notFound(
                                        "ยังไม่มีรอบการจับคู่"));

        return toRoundResponse(round);
    }

    /**
     * U6-SVC-R02: เปิดรอบใหม่
     */
    public MatchingRoundResponse openRound(
            Long userId,
            MatchingRoundRequest request) {

        User staff = guard.require(userId, Role.STAFF);
        LocalDateTime now = LocalDateTime.now(clock);

        if (request == null
                || request.confirmDeadline() == null) {
            throw ApiException.badRequest(
                    "กรุณากำหนดวันหมดเขตยืนยันสิทธิ์");
        }

        if (!request.confirmDeadline().isAfter(now)) {
            throw ApiException.badRequest(
                    "วันหมดเขตยืนยันสิทธิ์ต้องเป็นเวลาในอนาคต");
        }

        Optional<MatchingRound> latest =
                roundRepository
                        .findFirstByOrderByRoundNoDesc();

        if (latest.isPresent()) {
            MatchingRound previous = latest.get();

            if (previous.getStatus() == RoundStatus.OPEN
                    || (previous.getStatus()
                            == RoundStatus.ANNOUNCED
                            && !previous.isDeadlinePassed(now))) {

                throw new ApiException(
                        409,
                        "ROUND_NOT_CLOSED",
                        "รอบที่ "
                                + previous.getRoundNo()
                                + " ยังไม่จบ "
                                + "(ต้องประกาศผลและเลยวันหมดเขต"
                                + "ยืนยันสิทธิ์ก่อน)");
            }

            if (previous.getStatus()
                    == RoundStatus.ANNOUNCED) {
                closeExpiredRound(previous, now);
            }
        }

        int nextNumber = latest
                .map(round -> round.getRoundNo() + 1)
                .orElse(1);

        MatchingRound round =
                roundRepository.save(new MatchingRound(
                        nextNumber,
                        staff.getId(),
                        now,
                        request.confirmDeadline()));

        return toRoundResponse(round);
    }

    /**
     * U6-SVC-R03: ประกาศผลการจับคู่
     */
    public MatchingRoundResponse announce(
            Long userId,
            Long roundId) {

        guard.require(userId, Role.STAFF);

        MatchingRound round =
                roundRepository.findById(roundId)
                        .orElseThrow(() ->
                                ApiException.notFound(
                                        "ไม่พบรอบการจับคู่"));

        if (round.getStatus() != RoundStatus.OPEN) {
            throw ApiException.conflict(
                    "รอบนี้ประกาศผลไปแล้ว");
        }

        round.announce(LocalDateTime.now(clock));
        roundRepository.save(round);

        Map<Application, String> matched =
                new LinkedHashMap<>();

        for (Match match :
                matchRepository
                        .findByRoundIdOrderByIdAsc(round.getId())) {

            if (match.getStatus().isActive()) {
                applicationRepository
                        .findById(match.getApplicationId())
                        .ifPresent(application ->
                                matched.put(
                                        application,
                                        views.placeOf(
                                                match.getPositionId())));
            }
        }

        List<Application> notMatched =
                applicationRepository
                        .findApprovedWithoutActiveMatch()
                        .stream()
                        .filter(application ->
                                !preferenceRepository
                                        .findByApplicationIdOrderByRankNoAsc(
                                                application.getId())
                                        .isEmpty())
                        .toList();

        notificationService.notifyMatchAnnounced(
                round,
                matched,
                notMatched);

        return toRoundResponse(round);
    }

    /* ===================== นักศึกษา ===================== */

    /**
     * U6-SVC-S01: ผลจับคู่ล่าสุดของนักศึกษา
     */
    @Transactional(readOnly = true)
    public MatchResponse myMatch(Long userId) {
        User user = guard.require(userId, Role.STUDENT);

        Application application =
                requireApprovedApplication(user);

        Match match = announcedMatch(application)
                .orElseThrow(() ->
                        ApiException.notFound(
                                "ยังไม่มีผลการจับคู่ที่ประกาศแล้ว"));

        return views.toResponse(match, application);
    }

    /**
     * U6-SVC-S02: ข้อมูลหน้าผลการจับคู่
     */
    @Transactional(readOnly = true)
    public MatchSummaryResponse mySummary(Long userId) {
        User user = guard.require(userId, Role.STUDENT);

        Application application =
                requireApprovedApplication(user);

        Optional<Match> match =
                announcedMatch(application);

        MatchingRound round = match
                .flatMap(value ->
                        roundRepository.findById(
                                value.getRoundId()))
                .or(() ->
                        roundRepository
                                .findFirstByStatusInOrderByRoundNoDesc(
                                        List.of(
                                                RoundStatus.ANNOUNCED,
                                                RoundStatus.CLOSED)))
                .orElseThrow(() ->
                        ApiException.notFound(
                                "ยังไม่ประกาศผลการจับคู่"));

        MatchResponse matchResponse = match
                .map(value ->
                        views.toResponse(value, application))
                .orElse(null);

        Optional<CompanyPosition> position = match
                .flatMap(value ->
                        positionRepository.findById(
                                value.getPositionId()));

        return new MatchSummaryResponse(
                toRoundResponse(round),
                preferenceService.preferencesOf(application),
                matchResponse,
                position.map(CompanyPosition::getQuota)
                        .orElse(null),
                position.map(
                                CompanyPosition::getAvailableSlots)
                        .orElse(null),
                summaryText(matchResponse, round));
    }

    /**
     * U6-SVC-S03: ยืนยันหรือสละสิทธิ์
     * U7: สร้างข้อมูลการฝึกงานหลังยืนยันสิทธิ์
     */
    public MatchResponse respond(
            Long userId,
            Long matchId,
            MatchDecisionRequest request) {

        User user = guard.require(userId, Role.STUDENT);

        Match match = matchRepository.findById(matchId)
                .orElseThrow(() ->
                        ApiException.notFound(
                                "ไม่พบผลการจับคู่"));

        Application application =
                applicationRepository
                        .findById(match.getApplicationId())
                        .orElseThrow(() ->
                                ApiException.notFound(
                                        "ไม่พบใบสมัคร"));

        Student student =
                studentRepository
                        .findByUserId(user.getId())
                        .orElseThrow(ApiException::forbidden);

        if (!student.getId()
                .equals(application.getStudentId())) {
            throw ApiException.forbidden();
        }

        if (request == null || request.accept() == null) {
            throw ApiException.badRequest(
                    "กรุณาเลือกยืนยันสิทธิ์หรือสละสิทธิ์");
        }

        MatchingRound round =
                roundRepository
                        .findById(match.getRoundId())
                        .orElseThrow(() ->
                                ApiException.notFound(
                                        "ไม่พบรอบการจับคู่"));

        LocalDateTime now = LocalDateTime.now(clock);

        if (round.getStatus() != RoundStatus.ANNOUNCED
                || match.getStatus()
                        != MatchStatus.PROPOSED) {

            throw ApiException.conflict(
                    "ผลการจับคู่นี้ตอบไม่ได้แล้ว");
        }

        if (round.isDeadlinePassed(now)) {
            throw new ApiException(
                    409,
                    "DEADLINE_PASSED",
                    "เลยกำหนดยืนยันสิทธิ์แล้ว");
        }

        if (Boolean.TRUE.equals(request.accept())) {
            match.confirm(now);

        } else {
            String reason = request.reason() == null
                    ? ""
                    : request.reason().trim();

            if (reason.isEmpty()) {
                throw new ApiException(
                        400,
                        "REASON_REQUIRED",
                        "กรุณากรอกเหตุผลที่สละสิทธิ์");
            }

            match.decline(reason, now);
            releaseSlot(match.getPositionId());
        }

        matchRepository.save(match);

        /*
         * U7: สร้างข้อมูลการฝึกงานเฉพาะตอนยืนยันสิทธิ์
         * createFromMatch() จะไม่สร้างซ้ำหาก match นี้มี
         * Internship อยู่แล้ว
         */
        if (match.getStatus() == MatchStatus.CONFIRMED
                && internshipService != null) {

            internshipService.createFromMatch(
                    match.getId(),
                    user.getId());
        }

        notificationService.notifyMatchResponded(
                match,
                views.studentOf(application)[1],
                views.placeOf(match.getPositionId()));

        return views.toResponse(match, application);
    }

    /* ===================== ตัวช่วย ===================== */

    private MatchingRound requireOpenRound() {
        return roundRepository
                .findFirstByStatus(RoundStatus.OPEN)
                .orElseThrow(() ->
                        new ApiException(
                                409,
                                "NO_OPEN_ROUND",
                                "ยังไม่ได้เปิดรอบการจับคู่"));
    }

    /**
     * ตำแหน่งต้องเปิดรับ มีที่ว่าง และบริษัทเปิดรับ
     */
    private boolean canTake(CompanyPosition position) {
        boolean companyOpen =
                companyRepository
                        .findById(position.getCompanyId())
                        .map(company ->
                                company.getStatus()
                                        == CompanyStatus.OPEN)
                        .orElse(false);

        return position.isOpen()
                && position.hasAvailableSlot()
                && companyOpen;
    }

    private Match take(
            Application application,
            CompanyPosition position,
            MatchingRound round,
            Integer rank,
            User staff,
            LocalDateTime now) {

        position.takeSlot();
        positionRepository.save(position);

        return matchRepository.save(new Match(
                application.getId(),
                position.getId(),
                round.getId(),
                rank,
                staff.getId(),
                now));
    }

    private void releaseSlot(Long positionId) {
        positionRepository
                .findByIdForUpdate(positionId)
                .ifPresent(position -> {
                    position.releaseSlot();
                    positionRepository.save(position);
                });
    }

    /**
     * ปิดรอบที่เลยกำหนดยืนยันสิทธิ์
     */
    private void closeExpiredRound(
            MatchingRound round,
            LocalDateTime now) {

        List<Match> expiredMatches =
                matchRepository
                        .findByRoundIdAndStatus(
                                round.getId(),
                                MatchStatus.PROPOSED);

        for (Match match : expiredMatches) {
            match.decline(TIMEOUT_REASON, now);
            releaseSlot(match.getPositionId());
            matchRepository.save(match);
        }

        round.close();
        roundRepository.save(round);
    }

    /**
     * ใบสมัครล่าสุดที่ได้รับการอนุมัติ
     */
    private Application requireApprovedApplication(
            User user) {

        Student student =
                studentRepository
                        .findByUserId(user.getId())
                        .orElseThrow(() ->
                                ApiException.notFound(
                                        "ไม่พบข้อมูลนักศึกษา"));

        return applicationRepository
                .findByStudentIdOrderByIdDesc(student.getId())
                .stream()
                .filter(application ->
                        application.getStatus()
                                == ApplicationStatus.APPROVED)
                .findFirst()
                .orElseThrow(() ->
                        ApiException.notFound(
                                "ยังไม่มีใบสมัครที่ได้รับการอนุมัติ"));
    }

    /**
     * ผลล่าสุดที่ประกาศแล้วและไม่ถูกยกเลิก
     */
    private Optional<Match> announcedMatch(
            Application application) {

        return matchRepository
                .findFirstByApplicationIdOrderByIdDesc(
                        application.getId())
                .filter(match ->
                        match.getStatus()
                                != MatchStatus.CANCELLED)
                .filter(match ->
                        roundRepository
                                .findById(match.getRoundId())
                                .map(MatchingRound::isAnnounced)
                                .orElse(false));
    }

    private MatchingRoundResponse toRoundResponse(
            MatchingRound round) {

        long count = round.getId() == null
                ? 0
                : matchRepository
                        .findByRoundIdOrderByIdAsc(round.getId())
                        .stream()
                        .filter(match ->
                                match.getStatus().isActive())
                        .count();

        return MatchingRoundResponse.from(round, count);
    }

    private static String summaryText(
            MatchResponse match,
            MatchingRound round) {

        if (match == null) {
            return "รอบที่ "
                    + round.getRoundNo()
                    + " คุณยังไม่ได้รับการจับคู่ "
                    + "สามารถแก้อันดับเพื่อรอรอบถัดไปได้";
        }

        String where =
                "ตำแหน่ง "
                        + match.positionName()
                        + " บริษัท "
                        + match.companyName()
                        + (match.rank() == null
                                ? ""
                                : " (อันดับ "
                                        + match.rank()
                                        + ")");

        return switch (match.status()) {
            case PROPOSED ->
                    "คุณได้รับการจับคู่กับ "
                            + where
                            + " กรุณายืนยันภายใน "
                            + round.getConfirmDeadline()
                                    .format(TH_TIME)
                            + " น.";

            case CONFIRMED ->
                    "คุณยืนยันสิทธิ์ "
                            + where
                            + " แล้ว";

            case DECLINED ->
                    "คุณสละสิทธิ์ "
                            + where
                            + " แล้ว ("
                            + match.declineReason()
                            + ")";

            case CANCELLED ->
                    "ผลการจับคู่ถูกยกเลิก";
        };
    }
}