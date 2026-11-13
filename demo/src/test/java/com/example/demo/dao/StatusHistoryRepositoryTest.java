package com.example.demo.dao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.example.demo.entity.HistoryType;
import com.example.demo.entity.Role;
import com.example.demo.entity.StatusHistory;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;

@DataJpaTest(properties =
        "spring.datasource.url="
        + "jdbc:h2:mem:u7history;"
        + "MODE=MySQL;"
        + "DB_CLOSE_DELAY=-1")
@DisplayName("U7 Status History Repository")
class StatusHistoryRepositoryTest {

    @Autowired
    private StatusHistoryRepository historyRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private UserRepository userRepository;

    private User staff;
    private Student student;

    @BeforeEach
    void setUp() {
        staff = userRepository.save(
                new User(
                        "historyStaff",
                        "เจ้าหน้าที่ประวัติ",
                        "history.staff@tu.ac.th",
                        Role.STAFF
                )
        );

        User studentUser = userRepository.save(
                new User(
                        "historyStudent",
                        "สมชาย ประวัติดี",
                        "history.student@tu.ac.th",
                        Role.STUDENT
                )
        );

        student = studentRepository.save(
                new Student(
                        studentUser.getId(),
                        "6699990002",
                        "วิทยาศาสตร์และเทคโนโลยี",
                        "วิทยาการคอมพิวเตอร์",
                        3.20
                )
        );
    }

    @Test
    @DisplayName(
            "DAO-H01 แสดงประวัติตามเวลาเก่าไปใหม่"
    )
    void findByStudentId_returnsOrderedHistory() {
        LocalDateTime firstTime =
                LocalDateTime.of(
                        2026, 11, 12, 9, 0
                );

        LocalDateTime secondTime =
                LocalDateTime.of(
                        2026, 11, 12, 10, 0
                );

        // บันทึกรายการที่เกิดทีหลังเข้า DB ก่อน
        historyRepository.save(
                new StatusHistory(
                        student.getId(),
                        HistoryType.INTERNSHIP,
                        "NOT_STARTED",
                        "IN_PROGRESS",
                        staff.getId(),
                        secondTime,
                        "เริ่มฝึกงาน"
                )
        );

        historyRepository.saveAndFlush(
                new StatusHistory(
                        student.getId(),
                        HistoryType.INTERNSHIP,
                        null,
                        "NOT_STARTED",
                        staff.getId(),
                        firstTime,
                        "สร้างข้อมูลการฝึกงาน"
                )
        );

        List<StatusHistory> result =
                historyRepository
                        .findByStudentIdOrderByChangedAt(
                                student.getId()
                        );

        assertThat(result).hasSize(2);

        assertThat(result)
                .extracting(StatusHistory::getToStatus)
                .containsExactly(
                        "NOT_STARTED",
                        "IN_PROGRESS"
                );

        assertThat(result)
                .extracting(StatusHistory::getChangedAt)
                .containsExactly(
                        firstTime,
                        secondTime
                );
    }

    @Test
    @DisplayName(
            "DAO-H02 นักศึกษาที่ไม่มีประวัติคืน List ว่าง"
    )
    void findByUnknownStudent_returnsEmptyList() {
        List<StatusHistory> result =
                historyRepository
                        .findByStudentIdOrderByChangedAt(
                                999999L
                        );

        assertThat(result).isEmpty();
    }
}