package com.example.demo.dao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;

import com.example.demo.dto.StudentStatusFilter;
import com.example.demo.dto.StudentStatusResponse;
import com.example.demo.entity.Application;
import com.example.demo.entity.ApplicationStatus;
import com.example.demo.entity.Company;
import com.example.demo.entity.CompanyPosition;
import com.example.demo.entity.CompanyStatus;
import com.example.demo.entity.Internship;
import com.example.demo.entity.InternshipStage;
import com.example.demo.entity.InternshipStatus;
import com.example.demo.entity.Match;
import com.example.demo.entity.PositionStatus;
import com.example.demo.entity.Role;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;
import com.example.demo.repository.CompanyPositionRepository;
import com.example.demo.repository.CompanyRepository;

@DataJpaTest(properties =
        "spring.datasource.url="
        + "jdbc:h2:mem:u7statusquery;"
        + "MODE=MySQL;"
        + "DB_CLOSE_DELAY=-1")
@Import(StudentStatusQueryDao.class)
@DisplayName("U7 Student Status Query DAO")
class StudentStatusQueryDaoTest {

    @Autowired
    private StudentStatusQueryDao queryDao;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private InternshipRepository internshipRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CompanyPositionRepository positionRepository;

    private User staff;
    private User advisorOne;
    private User advisorTwo;

    private Student studentNotApplied;
    private Student studentApplying;
    private Student studentWaitingMatch;
    private Student studentMatching;
    private Student studentInProgress;

    private Company companyOne;
    private Company companyTwo;

    @BeforeEach
    void setUp() {
        staff = saveUser(
                "queryStaff",
                "เจ้าหน้าที่ Query",
                "query.staff@tu.ac.th",
                Role.STAFF
        );

        advisorOne = saveUser(
                "queryAdvisor1",
                "อาจารย์ที่ปรึกษา หนึ่ง",
                "query.advisor1@tu.ac.th",
                Role.ADVISOR
        );

        advisorTwo = saveUser(
                "queryAdvisor2",
                "อาจารย์ที่ปรึกษา สอง",
                "query.advisor2@tu.ac.th",
                Role.ADVISOR
        );

        studentNotApplied = saveStudent(
                "queryStudent1",
                "Alpha Student",
                "alpha@tu.ac.th",
                "6699990101",
                advisorOne.getId()
        );

        studentApplying = saveStudent(
                "queryStudent2",
                "Beta Student",
                "beta@tu.ac.th",
                "6699990102",
                advisorOne.getId()
        );

        studentWaitingMatch = saveStudent(
                "queryStudent3",
                "Gamma Student",
                "gamma@tu.ac.th",
                "6699990103",
                advisorOne.getId()
        );

        studentMatching = saveStudent(
                "queryStudent4",
                "Delta Student",
                "delta@tu.ac.th",
                "6699990104",
                advisorOne.getId()
        );

        studentInProgress = saveStudent(
                "queryStudent5",
                "Epsilon Student",
                "epsilon@tu.ac.th",
                "6699990105",
                advisorTwo.getId()
        );

        Application applyingApplication =
                new Application(
                        studentApplying.getId(),
                        101L,
                        ApplicationStatus.DRAFT
                );

        applyingApplication.submit(
                LocalDateTime.of(
                        2026, 11, 10, 9, 0
                )
        );

        applicationRepository.saveAndFlush(
                applyingApplication
        );

        Application waitingApplication =
                applicationRepository.saveAndFlush(
                        new Application(
                                studentWaitingMatch.getId(),
                                102L,
                                ApplicationStatus.APPROVED
                        )
                );

        Application matchingApplication =
                applicationRepository.saveAndFlush(
                        new Application(
                                studentMatching.getId(),
                                103L,
                                ApplicationStatus.APPROVED
                        )
                );

        Application progressApplication =
                applicationRepository.saveAndFlush(
                        new Application(
                                studentInProgress.getId(),
                                104L,
                                ApplicationStatus.APPROVED
                        )
                );

        companyOne = companyRepository.saveAndFlush(
                new Company(
                        "บริษัท อัลฟ่า จำกัด",
                        CompanyStatus.OPEN
                )
        );

        companyTwo = companyRepository.saveAndFlush(
                new Company(
                        "บริษัท เบต้า จำกัด",
                        CompanyStatus.OPEN
                )
        );

        CompanyPosition positionOne =
                positionRepository.saveAndFlush(
                        new CompanyPosition(
                                companyOne.getId(),
                                "Software Developer",
                                3,
                                PositionStatus.OPEN
                        )
                );

        CompanyPosition positionTwo =
                positionRepository.saveAndFlush(
                        new CompanyPosition(
                                companyTwo.getId(),
                                "Data Analyst",
                                2,
                                PositionStatus.OPEN
                        )
                );

        LocalDateTime matchTime =
                LocalDateTime.of(
                        2026, 11, 11, 10, 0
                );

        matchRepository.saveAndFlush(
                new Match(
                        matchingApplication.getId(),
                        positionOne.getId(),
                        1L,
                        1,
                        staff.getId(),
                        matchTime
                )
        );

        Match confirmedMatch =
                new Match(
                        progressApplication.getId(),
                        positionTwo.getId(),
                        1L,
                        1,
                        staff.getId(),
                        matchTime.plusHours(1)
                );

        confirmedMatch.confirm(
                matchTime.plusHours(2)
        );

        confirmedMatch =
                matchRepository.saveAndFlush(
                        confirmedMatch
                );

        Internship internship =
                new Internship(
                        confirmedMatch.getId(),
                        studentInProgress.getId(),
                        staff.getId(),
                        matchTime.plusHours(3)
                );

        internship.updateDetails(
                LocalDate.of(2026, 11, 15),
                LocalDate.of(2027, 3, 15),
                "สมชาย ผู้ดูแล",
                "supervisor@example.com",
                "0812345678",
                staff.getId(),
                matchTime.plusHours(3)
        );

        internship.changeStatus(
                InternshipStatus.IN_PROGRESS,
                null,
                staff.getId(),
                matchTime.plusHours(4)
        );

        internshipRepository.saveAndFlush(
                internship
        );

        // ป้องกันคำเตือนว่าไม่ได้ใช้งานตัวแปร
        assertThat(waitingApplication.getId())
                .isNotNull();
    }

    @Test
    @DisplayName(
            "DAO-Q01 ค้นหาทั้งหมดและเรียงตามรหัสนักศึกษา"
    )
    void searchNullFilter_returnsAllStudentsInOrder() {
        Page<StudentStatusResponse> result =
                queryDao.search(null);

        assertThat(result.getTotalElements())
                .isEqualTo(5);
        assertThat(result.getNumber())
                .isZero();
        assertThat(result.getSize())
                .isEqualTo(20);

        assertThat(result.getContent())
                .extracting(
                        StudentStatusResponse::studentCode
                )
                .containsExactly(
                        "6699990101",
                        "6699990102",
                        "6699990103",
                        "6699990104",
                        "6699990105"
                );
    }

    @Test
    @DisplayName(
            "DAO-Q02 กรองรายการตาม Stage"
    )
    void search_filtersByStage() {
        StudentStatusFilter filter =
                new StudentStatusFilter(
                        InternshipStage.APPLYING,
                        null,
                        null,
                        null,
                        0,
                        20
                );

        Page<StudentStatusResponse> result =
                queryDao.search(filter);

        assertThat(result.getContent())
                .hasSize(1);

        StudentStatusResponse row =
                result.getContent().getFirst();

        assertThat(row.studentId())
                .isEqualTo(studentApplying.getId());
        assertThat(row.studentCode())
                .isEqualTo("6699990102");
        assertThat(row.stage())
                .isEqualTo(InternshipStage.APPLYING);
    }

    @Test
    @DisplayName(
            "DAO-Q03 กรองรายการตามบริษัท"
    )
    void search_filtersByCompany() {
        StudentStatusFilter filter =
                new StudentStatusFilter(
                        null,
                        companyOne.getId(),
                        null,
                        null,
                        0,
                        20
                );

        Page<StudentStatusResponse> result =
                queryDao.search(filter);

        assertThat(result.getContent())
                .hasSize(1);

        StudentStatusResponse row =
                result.getContent().getFirst();

        assertThat(row.studentId())
                .isEqualTo(studentMatching.getId());
        assertThat(row.companyId())
                .isEqualTo(companyOne.getId());
        assertThat(row.companyName())
                .isEqualTo("บริษัท อัลฟ่า จำกัด");
        assertThat(row.positionTitle())
                .isEqualTo("Software Developer");
        assertThat(row.stage())
                .isEqualTo(InternshipStage.MATCHING);
    }

    @Test
    @DisplayName(
            "DAO-Q04 ค้นหาด้วยชื่อหรือรหัสนักศึกษา"
    )
    void search_filtersByKeyword() {
        StudentStatusFilter nameFilter =
                new StudentStatusFilter(
                        null,
                        null,
                        "  ALPHA  ",
                        null,
                        0,
                        20
                );

        Page<StudentStatusResponse> nameResult =
                queryDao.search(nameFilter);

        assertThat(nameResult.getContent())
                .extracting(
                        StudentStatusResponse::studentId
                )
                .containsExactly(
                        studentNotApplied.getId()
                );

        StudentStatusFilter codeFilter =
                new StudentStatusFilter(
                        null,
                        null,
                        "0103",
                        null,
                        0,
                        20
                );

        Page<StudentStatusResponse> codeResult =
                queryDao.search(codeFilter);

        assertThat(codeResult.getContent())
                .extracting(
                        StudentStatusResponse::studentId
                )
                .containsExactly(
                        studentWaitingMatch.getId()
                );
    }

    @Test
    @DisplayName(
            "DAO-Q05 กรองรายการตามอาจารย์ที่ปรึกษา"
    )
    void search_filtersByAdvisor() {
        StudentStatusFilter filter =
                new StudentStatusFilter(
                        null,
                        null,
                        null,
                        advisorTwo.getId(),
                        0,
                        20
                );

        Page<StudentStatusResponse> result =
                queryDao.search(filter);

        assertThat(result.getContent())
                .hasSize(1);

        StudentStatusResponse row =
                result.getContent().getFirst();

        assertThat(row.studentId())
                .isEqualTo(studentInProgress.getId());
        assertThat(row.advisorName())
                .isEqualTo("อาจารย์ที่ปรึกษา สอง");
        assertThat(row.stage())
                .isEqualTo(
                        InternshipStage.IN_PROGRESS
                );
        assertThat(row.internshipStatus())
                .isEqualTo(
                        InternshipStatus.IN_PROGRESS
                );
    }

    @Test
    @DisplayName(
            "DAO-Q06 แบ่งหน้ารายการถูกต้อง"
    )
    void search_appliesPagination() {
        StudentStatusFilter filter =
                new StudentStatusFilter(
                        null,
                        null,
                        null,
                        null,
                        1,
                        2
                );

        Page<StudentStatusResponse> result =
                queryDao.search(filter);

        assertThat(result.getNumber())
                .isEqualTo(1);
        assertThat(result.getSize())
                .isEqualTo(2);
        assertThat(result.getTotalElements())
                .isEqualTo(5);
        assertThat(result.getTotalPages())
                .isEqualTo(3);

        assertThat(result.getContent())
                .extracting(
                        StudentStatusResponse::studentCode
                )
                .containsExactly(
                        "6699990103",
                        "6699990104"
                );
    }

    @Test
    @DisplayName(
            "DAO-Q07 นับจำนวนแต่ละ Stage ตาม Advisor"
    )
    void countByStage_returnsAllStageCounts() {
        Map<InternshipStage, Long> result =
                queryDao.countByStage(
                        advisorOne.getId()
                );

        assertThat(result)
                .containsEntry(
                        InternshipStage.NOT_APPLIED,
                        1L
                )
                .containsEntry(
                        InternshipStage.APPLYING,
                        1L
                )
                .containsEntry(
                        InternshipStage.WAITING_MATCH,
                        1L
                )
                .containsEntry(
                        InternshipStage.MATCHING,
                        1L
                )
                .containsEntry(
                        InternshipStage.READY,
                        0L
                )
                .containsEntry(
                        InternshipStage.IN_PROGRESS,
                        0L
                )
                .containsEntry(
                        InternshipStage.COMPLETED,
                        0L
                )
                .containsEntry(
                        InternshipStage.REJECTED,
                        0L
                )
                .containsEntry(
                        InternshipStage.TERMINATED,
                        0L
                );
    }

    @Test
    @DisplayName(
            "DAO-Q08 อ่านรายการทั้งหมดสำหรับ Export"
    )
    void findAllForExport_filtersWithoutPagination() {
        List<StudentStatusResponse> matchingRows =
                queryDao.findAllForExport(
                        InternshipStage.MATCHING,
                        advisorOne.getId()
                );

        assertThat(matchingRows)
                .hasSize(1);

        assertThat(matchingRows.getFirst()
                .studentId())
                .isEqualTo(studentMatching.getId());

        assertThat(matchingRows.getFirst()
                .companyId())
                .isEqualTo(companyOne.getId());

        List<StudentStatusResponse> advisorTwoRows =
                queryDao.findAllForExport(
                        null,
                        advisorTwo.getId()
                );

        assertThat(advisorTwoRows)
                .extracting(
                        StudentStatusResponse::studentId
                )
                .containsExactly(
                        studentInProgress.getId()
                );
    }

    private User saveUser(
            String username,
            String fullName,
            String email,
            Role role
    ) {
        return userRepository.saveAndFlush(
                new User(
                        username,
                        fullName,
                        email,
                        role
                )
        );
    }

    private Student saveStudent(
            String username,
            String fullName,
            String email,
            String studentCode,
            Long advisorId
    ) {
        User user = saveUser(
                username,
                fullName,
                email,
                Role.STUDENT
        );

        Student student =
                new Student(
                        user.getId(),
                        studentCode,
                        "วิทยาศาสตร์และเทคโนโลยี",
                        "วิทยาการคอมพิวเตอร์",
                        3.25
                );

        student.setAdvisorId(advisorId);

        return studentRepository.saveAndFlush(
                student
        );
    }
}