package com.example.demo.integration;

import com.example.demo.dao.EligibilityRecordDao;
import com.example.demo.dao.StudentRepository;
import com.example.demo.entity.EligibilityRecord;
import com.example.demo.entity.Student;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    properties = {
        "spring.datasource.url=" +
            "jdbc:h2:mem:t18db;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
    }
)
@AutoConfigureMockMvc
class EligibilityApiDaoIntegrationTest {

    private static final String ENDPOINT =
        "/api/eligibility/check";

    private static final long PASS_USER_ID = 900001L;
    private static final long LOW_GPA_USER_ID = 900002L;

    private static final String PASS_STUDENT_ID =
        "6410000001";

    private static final String LOW_GPA_STUDENT_ID =
        "6410000002";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EligibilityRecordDao dao;

    @Autowired
    private StudentRepository studentRepository;

    @BeforeEach
    void prepareDatabase() {
        dao.deleteAll();
        dao.flush();

        studentRepository.findByUserId(PASS_USER_ID)
            .ifPresent(studentRepository::delete);

        studentRepository.findByUserId(LOW_GPA_USER_ID)
            .ifPresent(studentRepository::delete);

        studentRepository.flush();

        studentRepository.saveAll(
            List.of(
                new Student(
                    PASS_USER_ID,
                    PASS_STUDENT_ID,
                    "วิทยาศาสตร์และเทคโนโลยี",
                    "วิทยาการคอมพิวเตอร์",
                    3.10
                ),
                new Student(
                    LOW_GPA_USER_ID,
                    LOW_GPA_STUDENT_ID,
                    "วิทยาศาสตร์และเทคโนโลยี",
                    "วิทยาการคอมพิวเตอร์",
                    2.74
                )
            )
        );

        studentRepository.flush();
    }

    @Test
    void shouldSaveEligibleRecordWhenRequestPasses()
            throws Exception {

        long beforeCount = dao.count();

        mockMvc.perform(
                post(ENDPOINT)
                    .header("X-User-Id", PASS_USER_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        validRequest(
                            PASS_STUDENT_ID,
                            "3.10"
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.eligible").value(true))
            .andExpect(
                jsonPath("$.message")
                    .value("ผ่านคุณสมบัติ")
            );

        assertThat(dao.count()).isEqualTo(beforeCount + 1);

        List<EligibilityRecord> records =
            dao.findByStudentIdOrderByCheckedAtDesc(
                PASS_STUDENT_ID
            );

        assertThat(records).hasSize(1);
        assertThat(records.get(0).getStudentId())
            .isEqualTo(PASS_STUDENT_ID);
        assertThat(records.get(0).isEligible()).isTrue();
    }

    @Test
    void shouldUseDatabaseGpaAndSaveNotEligibleRecord()
            throws Exception {

        mockMvc.perform(
                post(ENDPOINT)
                    .header("X-User-Id", LOW_GPA_USER_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        validRequest(
                            LOW_GPA_STUDENT_ID,
                            "2.74"
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.eligible").value(false))
            .andExpect(
                jsonPath("$.reasons")
                    .value(
                        hasItem(
                            "GPA ต้องไม่ต่ำกว่า 2.75"
                        )
                    )
            );

        List<EligibilityRecord> records =
            dao.findByStudentIdOrderByCheckedAtDesc(
                LOW_GPA_STUDENT_ID
            );

        assertThat(records).hasSize(1);
        assertThat(records.get(0).isEligible()).isFalse();
    }

    @Test
    void shouldNotSaveWhenRequestIsIncomplete()
            throws Exception {

        long beforeCount = dao.count();

        mockMvc.perform(
                post(ENDPOINT)
                    .header("X-User-Id", PASS_USER_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.eligible").isEmpty())
            .andExpect(jsonPath("$.errors").isNotEmpty());

        assertThat(dao.count()).isEqualTo(beforeCount);
    }

    @Test
    void shouldNotSaveWhenRequestFormatIsInvalid()
            throws Exception {

        long beforeCount = dao.count();

        mockMvc.perform(
                post(ENDPOINT)
                    .header("X-User-Id", PASS_USER_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidRequest())
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(
                jsonPath("$.errors")
                    .value(
                        hasItem(
                            "รหัสนักศึกษาต้องเป็นตัวเลข 10 หลัก"
                        )
                    )
            )
            .andExpect(
                jsonPath("$.errors")
                    .value(
                        hasItem(
                            "GPA ต้องเป็นตัวเลข 0.00–4.00 " +
                            "และมีทศนิยมไม่เกิน 2 ตำแหน่ง"
                        )
                    )
            );

        assertThat(dao.count()).isEqualTo(beforeCount);
    }

    @Test
    void shouldSaveExactlyOneRecordPerRequest()
            throws Exception {

        long beforeCount = dao.count();

        mockMvc.perform(
                post(ENDPOINT)
                    .header("X-User-Id", PASS_USER_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        validRequest(
                            PASS_STUDENT_ID,
                            "3.10"
                        )
                    )
            )
            .andExpect(status().isOk());

        assertThat(dao.count() - beforeCount).isEqualTo(1);
    }

    @Test
    void shouldSaveRecordMatchingApiResponse()
            throws Exception {

        MvcResult result =
            mockMvc.perform(
                    post(ENDPOINT)
                        .header("X-User-Id", PASS_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            validRequest(
                                PASS_STUDENT_ID,
                                "3.10"
                            )
                        )
                )
                .andExpect(status().isOk())
                .andReturn();

        String responseJson =
            result.getResponse().getContentAsString();

        Boolean apiEligible =
            JsonPath.read(responseJson, "$.eligible");

        String apiMessage =
            JsonPath.read(responseJson, "$.message");

        List<EligibilityRecord> records =
            dao.findByStudentIdOrderByCheckedAtDesc(
                PASS_STUDENT_ID
            );

        assertThat(records).hasSize(1);
        assertThat(records.get(0).isEligible())
            .isEqualTo(apiEligible);
        assertThat(records.get(0).getMessage())
            .isEqualTo(apiMessage);
    }

    @Test
    void shouldRejectRequestWithoutUserIdHeader()
            throws Exception {

        mockMvc.perform(
                post(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        validRequest(
                            PASS_STUDENT_ID,
                            "3.10"
                        )
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.errors")
                    .value(hasItem("กรุณาส่ง X-User-Id"))
            );

        assertThat(dao.count()).isZero();
    }

    @Test
    void shouldRejectUnknownUserId()
            throws Exception {

        mockMvc.perform(
                post(ENDPOINT)
                    .header("X-User-Id", 999999L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        validRequest(
                            PASS_STUDENT_ID,
                            "3.10"
                        )
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.errors")
                    .value(
                        hasItem(
                            "ไม่พบข้อมูลนักศึกษาของผู้ใช้ที่เข้าสู่ระบบ"
                        )
                    )
            );

        assertThat(dao.count()).isZero();
    }

    @Test
    void shouldRejectStudentIdThatDoesNotBelongToUser()
            throws Exception {

        mockMvc.perform(
                post(ENDPOINT)
                    .header("X-User-Id", PASS_USER_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        validRequest(
                            LOW_GPA_STUDENT_ID,
                            "3.10"
                        )
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.errors")
                    .value(
                        hasItem(
                            "รหัสนักศึกษาไม่ตรงกับผู้ใช้ที่เข้าสู่ระบบ"
                        )
                    )
            );

        assertThat(dao.count()).isZero();
    }

    @Test
    void shouldRejectGpaThatDoesNotMatchDatabase()
            throws Exception {

        mockMvc.perform(
                post(ENDPOINT)
                    .header("X-User-Id", PASS_USER_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        validRequest(
                            PASS_STUDENT_ID,
                            "3.99"
                        )
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.errors")
                    .value(
                        hasItem(
                            "GPA ไม่ตรงกับข้อมูลในฐานข้อมูล"
                        )
                    )
            );

        assertThat(dao.count()).isZero();
    }

    @Test
    void shouldRejectCoursesOutsideStudentCurriculum()
            throws Exception {

        mockMvc.perform(
                post(ENDPOINT)
                    .header("X-User-Id", PASS_USER_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestWithOutsideCourses())
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.errors")
                    .value(
                        hasItem(
                            "รายวิชา คพ.999 ไม่อยู่ในหลักสูตร 61"
                        )
                    )
            )
            .andExpect(
                jsonPath("$.errors")
                    .value(
                        hasItem(
                            "รายวิชา คพ.888 ไม่อยู่ในหลักสูตร 61"
                        )
                    )
            );

        assertThat(dao.count()).isZero();
    }

    private String validRequest(
        String studentId,
        String gpax
    ) {
        return """
            {
              "studentId": "%s",
              "curriculum": "61",
              "gpax": "%s",
              "grades": {
                "คพ.101": "B",
                "คพ.102": "B",
                "คพ.111": "B",
                "คพ.213": "B",
                "คพ.251": "B",
                "คพ.264": "B"
              },
              "courseStatuses": {
                "คพ.384": "กำลังศึกษา",
                "คพ.266": "กำลังศึกษา"
              },
              "acknowledgements": [true, true, true]
            }
            """.formatted(studentId, gpax);
    }

    private String invalidRequest() {
        return """
            {
              "studentId": "ABC123",
              "curriculum": "61",
              "gpax": "3.10abc",
              "grades": {
                "คพ.101": "Z",
                "คพ.102": "B",
                "คพ.111": "B",
                "คพ.213": "B",
                "คพ.251": "B",
                "คพ.264": "B"
              },
              "courseStatuses": {
                "คพ.384": "เรียนอยู่",
                "คพ.266": "กำลังศึกษา"
              },
              "acknowledgements": [true, true, true]
            }
            """;
    }

    private String requestWithOutsideCourses() {
        return """
            {
              "studentId": "6410000001",
              "curriculum": "61",
              "gpax": "3.10",
              "grades": {
                "คพ.101": "B",
                "คพ.102": "B",
                "คพ.111": "B",
                "คพ.213": "B",
                "คพ.251": "B",
                "คพ.264": "B",
                "คพ.999": "B"
              },
              "courseStatuses": {
                "คพ.384": "กำลังศึกษา",
                "คพ.266": "กำลังศึกษา",
                "คพ.888": "กำลังศึกษา"
              },
              "acknowledgements": [true, true, true]
            }
            """;
    }
}
