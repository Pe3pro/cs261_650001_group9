package com.example.demo.dao;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dto.StudentStatusFilter;
import com.example.demo.dto.StudentStatusResponse;
import com.example.demo.entity.Application;
import com.example.demo.entity.ApplicationStatus;
import com.example.demo.entity.Company;
import com.example.demo.entity.CompanyPosition;
import com.example.demo.entity.Internship;
import com.example.demo.entity.InternshipStage;
import com.example.demo.entity.InternshipStatus;
import com.example.demo.entity.Match;
import com.example.demo.entity.MatchStatus;
import com.example.demo.entity.Role;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;
import com.example.demo.repository.CompanyPositionRepository;
import com.example.demo.repository.CompanyRepository;

/**
 * U7: รวมข้อมูลนักศึกษา ใบสมัคร ผลจับคู่ บริษัท และการฝึกงาน
 * เพื่อแสดงรายการสถานะ กรองข้อมูล แบ่งหน้า และ export CSV
 */
@Repository
@Transactional(readOnly = true)
public class StudentStatusQueryDao {

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;
    private final MatchRepository matchRepository;
    private final InternshipRepository internshipRepository;
    private final CompanyPositionRepository positionRepository;
    private final CompanyRepository companyRepository;

    public StudentStatusQueryDao(
            StudentRepository studentRepository,
            UserRepository userRepository,
            ApplicationRepository applicationRepository,
            MatchRepository matchRepository,
            InternshipRepository internshipRepository,
            CompanyPositionRepository positionRepository,
            CompanyRepository companyRepository) {

        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
        this.matchRepository = matchRepository;
        this.internshipRepository = internshipRepository;
        this.positionRepository = positionRepository;
        this.companyRepository = companyRepository;
    }

    /** ค้นหาและแบ่งหน้า */
    public Page<StudentStatusResponse> search(
            StudentStatusFilter filter) {

        StudentStatusFilter safeFilter =
                filter == null
                        ? new StudentStatusFilter(
                                null,
                                null,
                                null,
                                null,
                                0,
                                20)
                        : filter;

        List<StudentStatusResponse> rows =
                buildRows(safeFilter);

        int page = Math.max(safeFilter.page(), 0);
        int size = safeFilter.size() <= 0
                ? 20
                : safeFilter.size();

        int fromIndex = Math.min(
                page * size,
                rows.size());

        int toIndex = Math.min(
                fromIndex + size,
                rows.size());

        List<StudentStatusResponse> content =
                rows.subList(fromIndex, toIndex);

        return new PageImpl<>(
                new ArrayList<>(content),
                PageRequest.of(page, size),
                rows.size());
    }

    /** นับจำนวนนักศึกษาในแต่ละขั้น */
    public Map<InternshipStage, Long> countByStage(
            Long advisorId) {

        StudentStatusFilter filter =
                new StudentStatusFilter(
                        null,
                        null,
                        null,
                        advisorId,
                        0,
                        Integer.MAX_VALUE);

        List<StudentStatusResponse> rows =
                buildRows(filter);

        Map<InternshipStage, Long> result =
                new EnumMap<>(InternshipStage.class);

        for (InternshipStage stage :
                InternshipStage.values()) {
            result.put(stage, 0L);
        }

        for (StudentStatusResponse row : rows) {
            result.compute(
                    row.stage(),
                    (stage, count) ->
                            count == null ? 1L : count + 1L);
        }

        return result;
    }

    /** อ่านรายการทั้งหมดสำหรับ export โดยไม่แบ่งหน้า */
    public List<StudentStatusResponse> findAllForExport(
            InternshipStage stage,
            Long advisorId) {

        StudentStatusFilter filter =
                new StudentStatusFilter(
                        stage,
                        null,
                        null,
                        advisorId,
                        0,
                        Integer.MAX_VALUE);

        return buildRows(filter);
    }

    private List<StudentStatusResponse> buildRows(
            StudentStatusFilter filter) {

        List<StudentStatusResponse> result =
                new ArrayList<>();

        String keyword = normalizeKeyword(filter.q());

        for (Student student : studentRepository.findAll()) {
            Optional<User> studentUser =
                    userRepository.findById(
                            student.getUserId());

            /*
             * ตาราง students ควรมีเฉพาะนักศึกษาอยู่แล้ว
             * แต่ตรวจ Role ซ้ำเพื่อไม่ให้ STAFF/ADVISOR
             * หลุดเข้ารายการ
             */
            if (studentUser.isEmpty()
                    || studentUser.get().getRole()
                            != Role.STUDENT) {
                continue;
            }

            if (filter.advisorId() != null
                    && !filter.advisorId()
                            .equals(student.getAdvisorId())) {
                continue;
            }

            StudentStatusResponse row =
                    buildRow(
                            student,
                            studentUser.get());

            if (filter.stage() != null
                    && row.stage() != filter.stage()) {
                continue;
            }

            if (filter.companyId() != null
                    && !filter.companyId()
                            .equals(row.companyId())) {
                continue;
            }

            if (keyword != null
                    && !matchesKeyword(row, keyword)) {
                continue;
            }

            result.add(row);
        }

        result.sort(
                Comparator.comparing(
                        StudentStatusResponse::studentCode,
                        Comparator.nullsLast(
                                String.CASE_INSENSITIVE_ORDER)));

        return result;
    }

    private StudentStatusResponse buildRow(
            Student student,
            User studentUser) {

        Application application =
                applicationRepository
                        .findLatestByStudentId(student.getId())
                        .orElse(null);

        Match match = null;

        if (application != null) {
            match = matchRepository
                    .findActiveWithPositionByApplicationId(
                            application.getId())
                    .orElse(null);
        }

        Internship internship =
                internshipRepository
                        .findByStudentId(student.getId())
                        .orElse(null);

        CompanyPosition position = null;
        Company company = null;

        if (match != null) {
            position = positionRepository
                    .findById(match.getPositionId())
                    .orElse(null);

            if (position != null) {
                company = companyRepository
                        .findById(position.getCompanyId())
                        .orElse(null);
            }
        }

        String advisorName = null;

        if (student.getAdvisorId() != null) {
            advisorName = userRepository
                    .findById(student.getAdvisorId())
                    .map(User::getFullName)
                    .orElse(null);
        }

        InternshipStage stage =
                computeStage(
                        application,
                        match,
                        internship);

        LocalDateTime updatedAt =
                latestUpdatedAt(
                        application,
                        match,
                        internship);

        return new StudentStatusResponse(
                student.getId(),
                student.getStudentId(),
                studentUser.getFullName(),
                advisorName,
                company == null ? null : company.getId(),
                company == null
                        ? null
                        : company.getCompanyName(),
                position == null
                        ? null
                        : position.getPositionName(),
                stage,
                internship == null
                        ? null
                        : internship.getStatus(),
                null,
                internship == null
                        ? null
                        : internship.getStartDate(),
                updatedAt);
    }

    private InternshipStage computeStage(
            Application application,
            Match match,
            Internship internship) {

        if (internship != null) {
            return switch (internship.getStatus()) {
                case NOT_STARTED ->
                        InternshipStage.READY;
                case IN_PROGRESS ->
                        InternshipStage.IN_PROGRESS;
                case COMPLETED ->
                        InternshipStage.COMPLETED;
                case TERMINATED ->
                        InternshipStage.TERMINATED;
            };
        }

        if (application == null) {
            return InternshipStage.NOT_APPLIED;
        }

        if (application.getStatus()
                == ApplicationStatus.REJECTED) {
            return InternshipStage.REJECTED;
        }

        if (application.getStatus()
                != ApplicationStatus.APPROVED) {
            return InternshipStage.APPLYING;
        }

        if (match == null) {
            return InternshipStage.WAITING_MATCH;
        }

        if (match.getStatus()
                == MatchStatus.PROPOSED) {
            return InternshipStage.MATCHING;
        }

        if (match.getStatus()
                == MatchStatus.CONFIRMED) {
            return InternshipStage.READY;
        }

        return InternshipStage.WAITING_MATCH;
    }

    private LocalDateTime latestUpdatedAt(
            Application application,
            Match match,
            Internship internship) {

        LocalDateTime latest = null;

        if (application != null) {
            latest = newest(
                    latest,
                    application.getUpdatedAt());

            latest = newest(
                    latest,
                    application.getSubmittedAt());

            latest = newest(
                    latest,
                    application.getCreatedAt());
        }

        if (match != null) {
            latest = newest(
                    latest,
                    match.getMatchedAt());

            latest = newest(
                    latest,
                    match.getRespondedAt());
        }

        if (internship != null) {
            latest = newest(
                    latest,
                    internship.getCreatedAt());

            latest = newest(
                    latest,
                    internship.getUpdatedAt());
        }

        return latest;
    }

    private LocalDateTime newest(
            LocalDateTime first,
            LocalDateTime second) {

        if (first == null) {
            return second;
        }

        if (second == null) {
            return first;
        }

        return second.isAfter(first)
                ? second
                : first;
    }

    private boolean matchesKeyword(
            StudentStatusResponse row,
            String keyword) {

        String studentCode = row.studentCode() == null
                ? ""
                : row.studentCode()
                        .toLowerCase(Locale.ROOT);

        String studentName = row.studentName() == null
                ? ""
                : row.studentName()
                        .toLowerCase(Locale.ROOT);

        return studentCode.contains(keyword)
                || studentName.contains(keyword);
    }

    private String normalizeKeyword(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim()
                .toLowerCase(Locale.ROOT);
    }
}