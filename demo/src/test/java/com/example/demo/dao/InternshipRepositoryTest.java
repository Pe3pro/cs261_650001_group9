package com.example.demo.dao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.example.demo.entity.Internship;
import com.example.demo.entity.Match;
import com.example.demo.entity.Role;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;

@DataJpaTest(properties =
        "spring.datasource.url="
        + "jdbc:h2:mem:u7internship;"
        + "MODE=MySQL;"
        + "DB_CLOSE_DELAY=-1")
@DisplayName("U7 Internship Repository")
class InternshipRepositoryTest {

    @Autowired
    private InternshipRepository internshipRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private UserRepository userRepository;

    private User staff;
    private Student student;
    private Match match;
    private Internship internship;

    @BeforeEach
    void setUp() {
        staff = userRepository.save(
                new User(
                        "u7staff",
                        "เจ้าหน้าที่ U7",
                        "u7staff@tu.ac.th",
                        Role.STAFF
                )
        );

        User studentUser = userRepository.save(
                new User(
                        "u7student",
                        "สมชาย ใจดี",
                        "u7student@tu.ac.th",
                        Role.STUDENT
                )
        );

        student = studentRepository.save(
                new Student(
                        studentUser.getId(),
                        "6699990001",
                        "วิทยาศาสตร์และเทคโนโลยี",
                        "วิทยาการคอมพิวเตอร์",
                        3.10
                )
        );

        LocalDateTime now =
                LocalDateTime.of(
                        2026, 11, 12, 10, 0
                );

        match = matchRepository.save(
                new Match(
                        100L,
                        200L,
                        1L,
                        1,
                        staff.getId(),
                        now
                )
        );

        internship = internshipRepository.saveAndFlush(
                new Internship(
                        match.getId(),
                        student.getId(),
                        staff.getId(),
                        now
                )
        );
    }

    @Test
    @DisplayName(
            "DAO-I01 ค้นข้อมูลการฝึกงานด้วย Student ID"
    )
    void findByStudentId_returnsInternship() {
        Optional<Internship> result =
                internshipRepository.findByStudentId(
                        student.getId()
                );

        assertThat(result).isPresent();
        assertThat(result.get().getId())
                .isEqualTo(internship.getId());
        assertThat(result.get().getStudentId())
                .isEqualTo(student.getId());
    }

    @Test
    @DisplayName(
            "DAO-I02 ค้นข้อมูลการฝึกงานด้วย Match ID"
    )
    void findByMatchId_returnsInternship() {
        Optional<Internship> result =
                internshipRepository.findByMatchId(
                        match.getId()
                );

        assertThat(result).isPresent();
        assertThat(result.get().getId())
                .isEqualTo(internship.getId());
        assertThat(result.get().getMatchId())
                .isEqualTo(match.getId());
    }

    @Test
    @DisplayName(
            "DAO-I03 ไม่พบข้อมูลคืนค่า Optional ว่าง"
    )
    void findByUnknownIds_returnsEmpty() {
        assertThat(
                internshipRepository.findByStudentId(
                        999999L
                )
        ).isEmpty();

        assertThat(
                internshipRepository.findByMatchId(
                        999999L
                )
        ).isEmpty();
    }
}