package com.example.demo.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dao.ApplicationRepository;
import com.example.demo.dao.FormTemplateRepository;
import com.example.demo.dao.InternshipRepository;
import com.example.demo.dao.MatchRepository;
import com.example.demo.dao.MatchingRoundRepository;
import com.example.demo.dao.StatusHistoryRepository;
import com.example.demo.dao.StudentRepository;
import com.example.demo.dao.UserRepository;
import com.example.demo.entity.Application;
import com.example.demo.entity.ApplicationStatus;
import com.example.demo.entity.CompanyPosition;
import com.example.demo.entity.HistoryType;
import com.example.demo.entity.Internship;
import com.example.demo.entity.InternshipStatus;
import com.example.demo.entity.Match;
import com.example.demo.entity.MatchStatus;
import com.example.demo.entity.MatchingRound;
import com.example.demo.entity.Role;
import com.example.demo.entity.RoundStatus;
import com.example.demo.entity.StatusHistory;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;
import com.example.demo.repository.CompanyPositionRepository;

/**
 * U7 demo data for System Test and UAT.
 *
 * advisor02 supervises students 6609650021-6609650027.
 *
 * 21 = NOT_APPLIED
 * 22 = APPLYING
 * 23 = WAITING_MATCH
 * 24 = MATCHING
 * 25 = READY
 * 26 = IN_PROGRESS
 * 27 = COMPLETED
 */
@Component
@ConditionalOnProperty(
        name = "app.seed-demo-data",
        havingValue = "true"
)
public class U7DemoSeeder {

    private final MatchingDemoSeeder matchingDemoSeeder;
    private final UserRepository users;
    private final StudentRepository students;
    private final FormTemplateRepository forms;
    private final ApplicationRepository applications;
    private final MatchingRoundRepository rounds;
    private final MatchRepository matches;
    private final CompanyPositionRepository positions;
    private final InternshipRepository internships;
    private final StatusHistoryRepository histories;

    public U7DemoSeeder(
            MatchingDemoSeeder matchingDemoSeeder,
            UserRepository users,
            StudentRepository students,
            FormTemplateRepository forms,
            ApplicationRepository applications,
            MatchingRoundRepository rounds,
            MatchRepository matches,
            CompanyPositionRepository positions,
            InternshipRepository internships,
            StatusHistoryRepository histories
    ) {
        this.matchingDemoSeeder = matchingDemoSeeder;
        this.users = users;
        this.students = students;
        this.forms = forms;
        this.applications = applications;
        this.rounds = rounds;
        this.matches = matches;
        this.positions = positions;
        this.internships = internships;
        this.histories = histories;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        /*
         * Ensure U6 companies and positions exist before U7 creates matches.
         * MatchingDemoSeeder is idempotent.
         */
        matchingDemoSeeder.seed();

        Optional<User> staffOptional =
                users.findByUsername("staff01");

        Optional<com.example.demo.entity.FormTemplate> formOptional =
                forms.findTopByOrderByVersionDesc();

        List<CompanyPosition> positionRows =
                positions.findAll();

        if (staffOptional.isEmpty()
                || formOptional.isEmpty()
                || positionRows.isEmpty()) {
            return;
        }

        User staff = staffOptional.get();
        Long formId = formOptional.get().getId();
        LocalDateTime now = LocalDateTime.now();

        User advisor =
                ensureUser(
                        "advisor02",
                        "U7 Advisor",
                        "advisor02@tu.ac.th",
                        Role.ADVISOR
                );

        Student student21 =
                ensureStudent(
                        "6609650021",
                        "U7 Student 21",
                        "u7student21@dome.tu.ac.th",
                        3.01,
                        advisor
                );

        Student student22 =
                ensureStudent(
                        "6609650022",
                        "U7 Student 22",
                        "u7student22@dome.tu.ac.th",
                        3.12,
                        advisor
                );

        Student student23 =
                ensureStudent(
                        "6609650023",
                        "U7 Student 23",
                        "u7student23@dome.tu.ac.th",
                        3.23,
                        advisor
                );

        Student student24 =
                ensureStudent(
                        "6609650024",
                        "U7 Student 24",
                        "u7student24@dome.tu.ac.th",
                        3.34,
                        advisor
                );

        Student student25 =
                ensureStudent(
                        "6609650025",
                        "U7 Student 25",
                        "u7student25@dome.tu.ac.th",
                        3.45,
                        advisor
                );

        Student student26 =
                ensureStudent(
                        "6609650026",
                        "U7 Student 26",
                        "u7student26@dome.tu.ac.th",
                        3.56,
                        advisor
                );

        Student student27 =
                ensureStudent(
                        "6609650027",
                        "U7 Student 27",
                        "u7student27@dome.tu.ac.th",
                        3.67,
                        advisor
                );

        /*
         * Student 21 intentionally has no application.
         * The computed stage is NOT_APPLIED.
         */
        removeNothingForNotApplied(student21);

        Application application22 =
                ensureApplication(
                        student22,
                        formId,
                        ApplicationStatus.SUBMITTED,
                        now.minusDays(2)
                );

        Application application23 =
                ensureApplication(
                        student23,
                        formId,
                        ApplicationStatus.APPROVED,
                        now.minusDays(5)
                );

        Application application24 =
                ensureApplication(
                        student24,
                        formId,
                        ApplicationStatus.APPROVED,
                        now.minusDays(6)
                );

        Application application25 =
                ensureApplication(
                        student25,
                        formId,
                        ApplicationStatus.APPROVED,
                        now.minusDays(7)
                );

        Application application26 =
                ensureApplication(
                        student26,
                        formId,
                        ApplicationStatus.APPROVED,
                        now.minusDays(8)
                );

        Application application27 =
                ensureApplication(
                        student27,
                        formId,
                        ApplicationStatus.APPROVED,
                        now.minusDays(9)
                );

        MatchingRound round =
                ensureAnnouncedRound(
                        staff,
                        now
                );

        CompanyPosition position24 =
                positionRows.get(
                        0 % positionRows.size()
                );

        CompanyPosition position25 =
                positionRows.get(
                        1 % positionRows.size()
                );

        CompanyPosition position26 =
                positionRows.get(
                        2 % positionRows.size()
                );

        CompanyPosition position27 =
                positionRows.get(
                        3 % positionRows.size()
                );

        Match match24 =
                ensureMatch(
                        application24,
                        position24,
                        round,
                        staff,
                        false,
                        now.minusDays(3)
                );

        Match match25 =
                ensureMatch(
                        application25,
                        position25,
                        round,
                        staff,
                        true,
                        now.minusDays(4)
                );

        Match match26 =
                ensureMatch(
                        application26,
                        position26,
                        round,
                        staff,
                        true,
                        now.minusDays(5)
                );

        Match match27 =
                ensureMatch(
                        application27,
                        position27,
                        round,
                        staff,
                        true,
                        now.minusDays(6)
                );

        Internship internship25 =
                ensureInternship(
                        student25,
                        match25,
                        staff,
                        InternshipStatus.NOT_STARTED,
                        now
                );

        Internship internship26 =
                ensureInternship(
                        student26,
                        match26,
                        staff,
                        InternshipStatus.IN_PROGRESS,
                        now
                );

        Internship internship27 =
                ensureInternship(
                        student27,
                        match27,
                        staff,
                        InternshipStatus.COMPLETED,
                        now
                );

        ensureHistory(
                student22,
                HistoryType.APPLICATION,
                null,
                ApplicationStatus.SUBMITTED.name(),
                staff,
                now.minusDays(2),
                "U7 demo application submitted"
        );

        ensureHistory(
                student23,
                HistoryType.APPLICATION,
                null,
                ApplicationStatus.APPROVED.name(),
                staff,
                now.minusDays(5),
                "U7 demo application approved"
        );

        ensureHistory(
                student24,
                HistoryType.APPLICATION,
                null,
                ApplicationStatus.APPROVED.name(),
                staff,
                now.minusDays(6),
                "U7 demo application approved"
        );

        ensureHistory(
                student24,
                HistoryType.MATCH,
                null,
                MatchStatus.PROPOSED.name(),
                staff,
                now.minusDays(3),
                "U7 demo match proposed"
        );

        ensureHistory(
                student25,
                HistoryType.MATCH,
                MatchStatus.PROPOSED.name(),
                MatchStatus.CONFIRMED.name(),
                staff,
                now.minusDays(3),
                "U7 demo match confirmed"
        );

        ensureHistory(
                student25,
                HistoryType.INTERNSHIP,
                null,
                InternshipStatus.NOT_STARTED.name(),
                staff,
                internship25.getCreatedAt(),
                "U7 demo internship ready"
        );

        ensureHistory(
                student26,
                HistoryType.INTERNSHIP,
                InternshipStatus.NOT_STARTED.name(),
                InternshipStatus.IN_PROGRESS.name(),
                staff,
                internship26.getUpdatedAt(),
                "U7 demo internship started"
        );

        ensureHistory(
                student27,
                HistoryType.INTERNSHIP,
                InternshipStatus.IN_PROGRESS.name(),
                InternshipStatus.COMPLETED.name(),
                staff,
                internship27.getUpdatedAt(),
                "U7 demo internship completed"
        );

        /*
         * Prevent unused-variable warnings from hiding the intended
         * stage mapping in this demo seeder.
         */
        if (application22 == null
                || application23 == null
                || match24 == null) {
            throw new IllegalStateException(
                    "U7 demo data could not be created"
            );
        }
    }

    private User ensureUser(
            String username,
            String fullName,
            String email,
            Role role
    ) {
        return users.findByUsername(username)
                .orElseGet(() ->
                        users.save(
                                new User(
                                        username,
                                        fullName,
                                        email,
                                        role
                                )
                        )
                );
    }

    private Student ensureStudent(
            String studentCode,
            String fullName,
            String email,
            double gpa,
            User advisor
    ) {
        User user =
                ensureUser(
                        studentCode,
                        fullName,
                        email,
                        Role.STUDENT
                );

        Student student =
                students.findByUserId(
                        user.getId()
                ).orElseGet(() ->
                        students.save(
                                new Student(
                                        user.getId(),
                                        studentCode,
                                        "Science and Technology",
                                        "Computer Science",
                                        gpa
                                )
                        )
                );

        if (!advisor.getId().equals(
                student.getAdvisorId())) {

            student.setAdvisorId(
                    advisor.getId()
            );

            student =
                    students.save(student);
        }

        return student;
    }

    private Application ensureApplication(
            Student student,
            Long formId,
            ApplicationStatus status,
            LocalDateTime submittedAt
    ) {
        List<Application> existing =
                applications
                        .findByStudentIdOrderByIdDesc(
                                student.getId()
                        );

        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        Application application =
                new Application(
                        student.getId(),
                        formId,
                        status
                );

        application.setSubmittedAt(
                submittedAt
        );

        return applications.save(
                application
        );
    }

    private MatchingRound ensureAnnouncedRound(
            User staff,
            LocalDateTime now
    ) {
        Optional<MatchingRound> existing =
                rounds.findAll()
                        .stream()
                        .filter(round ->
                                round.getStatus()
                                        == RoundStatus.ANNOUNCED)
                        .filter(round ->
                                round.getConfirmDeadline()
                                        .isAfter(now))
                        .max(
                                Comparator.comparing(
                                        MatchingRound::getRoundNo
                                )
                        );

        if (existing.isPresent()) {
            return existing.get();
        }

        int nextRoundNumber =
                rounds.findAll()
                        .stream()
                        .map(
                                MatchingRound::getRoundNo
                        )
                        .max(Integer::compareTo)
                        .orElse(0)
                        + 1;

        MatchingRound round =
                new MatchingRound(
                        nextRoundNumber,
                        staff.getId(),
                        now.minusDays(2),
                        now.plusDays(14)
                );

        round.announce(
                now.minusDays(1)
        );

        return rounds.save(round);
    }

    private Match ensureMatch(
            Application application,
            CompanyPosition position,
            MatchingRound round,
            User staff,
            boolean confirmed,
            LocalDateTime matchedAt
    ) {
        Optional<Match> existing =
                matches
                        .findFirstByApplicationIdOrderByIdDesc(
                                application.getId()
                        );

        if (existing.isPresent()) {
            Match match = existing.get();

            if (confirmed
                    && match.getStatus()
                    == MatchStatus.PROPOSED) {

                match.confirm(
                        matchedAt.plusHours(1)
                );

                match =
                        matches.save(match);
            }

            return match;
        }

        Match match =
                new Match(
                        application.getId(),
                        position.getId(),
                        round.getId(),
                        1,
                        staff.getId(),
                        matchedAt
                );

        if (confirmed) {
            match.confirm(
                    matchedAt.plusHours(1)
            );
        }

        return matches.save(match);
    }

    private Internship ensureInternship(
            Student student,
            Match match,
            User staff,
            InternshipStatus targetStatus,
            LocalDateTime now
    ) {
        Internship internship =
                internships
                        .findByStudentId(
                                student.getId()
                        )
                        .orElseGet(() ->
                                internships.save(
                                        new Internship(
                                                match.getId(),
                                                student.getId(),
                                                staff.getId(),
                                                now.minusDays(2)
                                        )
                                )
                        );

        LocalDate startDate =
                LocalDate.now()
                        .minusDays(1);

        LocalDate endDate =
                startDate.plusMonths(4);

        internship.updateDetails(
                startDate,
                endDate,
                "U7 Supervisor",
                "u7supervisor@example.com",
                "0812345678",
                staff.getId(),
                now.minusDays(1)
        );

        if (targetStatus
                == InternshipStatus.IN_PROGRESS
                && internship.getStatus()
                == InternshipStatus.NOT_STARTED) {

            internship.changeStatus(
                    InternshipStatus.IN_PROGRESS,
                    null,
                    staff.getId(),
                    now.minusHours(12)
            );
        }

        if (targetStatus
                == InternshipStatus.COMPLETED) {

            if (internship.getStatus()
                    == InternshipStatus.NOT_STARTED) {

                internship.changeStatus(
                        InternshipStatus.IN_PROGRESS,
                        null,
                        staff.getId(),
                        now.minusDays(1)
                );
            }

            if (internship.getStatus()
                    == InternshipStatus.IN_PROGRESS) {

                internship.changeStatus(
                        InternshipStatus.COMPLETED,
                        null,
                        staff.getId(),
                        now.minusHours(1)
                );
            }
        }

        return internships.save(
                internship
        );
    }

    private void ensureHistory(
            Student student,
            HistoryType type,
            String fromStatus,
            String toStatus,
            User changedBy,
            LocalDateTime changedAt,
            String note
    ) {
        boolean exists =
                histories
                        .findByStudentIdOrderByChangedAt(
                                student.getId()
                        )
                        .stream()
                        .anyMatch(history ->
                                history.getType() == type
                                && history.getToStatus()
                                        .equals(toStatus)
                        );

        if (exists) {
            return;
        }

        histories.save(
                new StatusHistory(
                        student.getId(),
                        type,
                        fromStatus,
                        toStatus,
                        changedBy.getId(),
                        changedAt,
                        note
                )
        );
    }

    /**
     * Student 21 intentionally has no application.
     */
    private void removeNothingForNotApplied(
            Student student
    ) {
        if (student == null) {
            throw new IllegalStateException(
                    "U7 student 21 was not created"
            );
        }
    }
}