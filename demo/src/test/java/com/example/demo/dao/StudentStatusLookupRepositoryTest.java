package com.example.demo.dao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.example.demo.entity.Application;
import com.example.demo.entity.ApplicationStatus;
import com.example.demo.entity.Match;
import com.example.demo.entity.MatchStatus;
import com.example.demo.entity.Role;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;

@DataJpaTest(properties =
        "spring.datasource.url="
        + "jdbc:h2:mem:u7lookup;"
        + "MODE=MySQL;"
        + "DB_CLOSE_DELAY=-1")
@DisplayName("U7 Student Status Lookup Repository")
class StudentStatusLookupRepositoryTest {

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private UserRepository userRepository;

    private User staff;
    private Student student;
    private Application latestApplication;

    @BeforeEach
    void setUp() {
        staff = userRepository.save(
                new User(
                        "lookupStaff",
                        "เจ้าหน้าที่ค้นหา",
                        "lookup.staff@tu.ac.th",
                        Role.STAFF
                )
        );

        User studentUser = userRepository.save(
                new User(
                        "lookupStudent",
                        "สมชาย ค้นหา",
                        "lookup.student@tu.ac.th",
                        Role.STUDENT
                )
        );

        student = studentRepository.save(
                new Student(
                        studentUser.getId(),
                        "6699990003",
                        "วิทยาศาสตร์และเทคโนโลยี",
                        "วิทยาการคอมพิวเตอร์",
                        3.30
                )
        );

        Application firstApplication =
                new Application(
                        student.getId(),
                        100L,
                        ApplicationStatus.DRAFT
                );

        applicationRepository.saveAndFlush(
                firstApplication
        );

        latestApplication =
                new Application(
                        student.getId(),
                        101L,
                        ApplicationStatus.DRAFT
                );

        latestApplication.submit(
                LocalDateTime.of(
                        2026, 11, 12, 10, 0
                )
        );

        latestApplication =
                applicationRepository.saveAndFlush(
                        latestApplication
                );
    }

    @Test
    @DisplayName(
            "DAO-L01 ค้นหาใบสมัครล่าสุดของนักศึกษา"
    )
    void findLatestByStudentId_returnsLatestApplication() {
        Optional<Application> result =
                applicationRepository
                        .findLatestByStudentId(
                                student.getId()
                        );

        assertThat(result).isPresent();
        assertThat(result.get().getId())
                .isEqualTo(latestApplication.getId());
        assertThat(result.get().getStatus())
                .isEqualTo(ApplicationStatus.SUBMITTED);
        assertThat(result.get().getFormTemplateId())
                .isEqualTo(101L);
    }

    @Test
    @DisplayName(
            "DAO-L02 นักศึกษาที่ไม่มีใบสมัครคืน Optional ว่าง"
    )
    void findLatestByUnknownStudent_returnsEmpty() {
        Optional<Application> result =
                applicationRepository
                        .findLatestByStudentId(
                                999999L
                        );

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName(
            "DAO-L03 ค้นหาผลจับคู่ที่ยังใช้งานอยู่"
    )
    void findActiveMatch_returnsProposedOrConfirmed() {
        LocalDateTime now =
                LocalDateTime.of(
                        2026, 11, 12, 11, 0
                );

        Match cancelledMatch =
                new Match(
                        latestApplication.getId(),
                        200L,
                        1L,
                        2,
                        staff.getId(),
                        now
                );

        cancelledMatch.cancel();

        matchRepository.saveAndFlush(
                cancelledMatch
        );

        Match activeMatch =
                new Match(
                        latestApplication.getId(),
                        201L,
                        1L,
                        1,
                        staff.getId(),
                        now.plusMinutes(1)
                );

        activeMatch = matchRepository.saveAndFlush(
                activeMatch
        );

        Optional<Match> result =
                matchRepository
                        .findActiveWithPositionByApplicationId(
                                latestApplication.getId()
                        );

        assertThat(result).isPresent();
        assertThat(result.get().getId())
                .isEqualTo(activeMatch.getId());
        assertThat(result.get().getStatus())
                .isEqualTo(MatchStatus.PROPOSED);
        assertThat(result.get().getPositionId())
                .isEqualTo(201L);
    }
}