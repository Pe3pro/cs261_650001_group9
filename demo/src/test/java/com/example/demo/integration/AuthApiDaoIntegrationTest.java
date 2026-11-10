package com.example.demo.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.example.demo.dao.StudentRepository;
import com.example.demo.dao.UserRepository;
import com.example.demo.dto.TuVerifyResponse;
import com.example.demo.entity.Role;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;
import com.example.demo.service.ApiException;
import com.example.demo.service.TuApiClient;
import com.jayway.jsonpath.JsonPath;

/**
 * U1:T18 Integration Test ระหว่าง API กับ DAO ตาม T14 (IT-AD-L01–L07)
 *
 * POST /api/auth/login → AuthController → AuthService → UserRepository / StudentRepository → H2
 * - ไม่ใช้หน้าเว็บ เรียก API ผ่าน MockMvc
 * - TU REST API เป็นระบบภายนอก จึงจำลองด้วย @MockitoBean TuApiClient
 * - ปิด DemoDataSeeder และล้างตาราง users / students ก่อนทุกข้อ ให้แต่ละเคสไม่กระทบกัน
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:u1t18db;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.seed-demo-data=false",
        "app.auth.demo-users=staff01,advisor01",
        "app.auth.demo-password=1234"
})
@AutoConfigureMockMvc
class AuthApiDaoIntegrationTest {

    private static final String LOGIN = "/api/auth/login";
    private static final String STUDENT_ID = "6609650099";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private StudentRepository students;

    @MockitoBean
    private TuApiClient tuApiClient;

    @BeforeEach
    void clearTables() {
        students.deleteAll();
        users.deleteAll();
    }

    /* ---------- helpers ---------- */

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post(LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    private static TuVerifyResponse tuStudent(String username, String nameTh) {
        return new TuVerifyResponse(true, "Success", "student", username, nameTh, "Test Student",
                username + "@dome.tu.ac.th", "วิทยาการคอมพิวเตอร์", "วิทยาศาสตร์และเทคโนโลยี");
    }

    private static TuVerifyResponse tuEmployee(String username, String nameTh) {
        return new TuVerifyResponse(true, "Success", "employee", username, nameTh, "Test Employee",
                username + "@tu.ac.th", "ภาควิชาวิทยาการคอมพิวเตอร์", "วิทยาศาสตร์และเทคโนโลยี");
    }

    private static long userIdOf(ResultActions result) throws Exception {
        Number id = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.userId");
        return id.longValue();
    }

    /* ---------- test cases ---------- */

    @Test
    @DisplayName("IT-AD-L01 นักศึกษาใหม่: 200 STUDENT · users +1 (password_hash null) · students +1 (major = department)")
    void newStudentIsSavedToUsersAndStudents() throws Exception {
        when(tuApiClient.verify(STUDENT_ID, "pw")).thenReturn(tuStudent(STUDENT_ID, "สมปอง ทดสอบ"));

        ResultActions result = login(STUDENT_ID, "pw")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.username").value(STUDENT_ID))
                .andExpect(jsonPath("$.fullName").value("สมปอง ทดสอบ"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
        long userId = userIdOf(result);

        assertThat(users.count()).isEqualTo(1);
        User saved = users.findByUsername(STUDENT_ID).orElseThrow();
        assertThat(saved.getId()).isEqualTo(userId);
        assertThat(saved.getRole()).isEqualTo(Role.STUDENT);
        assertThat(saved.getPasswordHash()).isNull();
        assertThat(saved.getEmail()).isEqualTo(STUDENT_ID + "@dome.tu.ac.th");

        assertThat(students.count()).isEqualTo(1);
        Student student = students.findByUserId(userId).orElseThrow();
        assertThat(student.getStudentId()).isEqualTo(STUDENT_ID);
        assertThat(student.getMajor()).isEqualTo("วิทยาการคอมพิวเตอร์");
        assertThat(student.getFaculty()).isEqualTo("วิทยาศาสตร์และเทคโนโลยี");
    }

    @Test
    @DisplayName("IT-AD-L02 login ซ้ำ ชื่อจาก TU เปลี่ยน: 200 userId เดิม fullName ใหม่ · users 1 แถว · students 1 แถว")
    void secondLoginUpdatesNameWithoutNewRows() throws Exception {
        when(tuApiClient.verify(STUDENT_ID, "pw")).thenReturn(tuStudent(STUDENT_ID, "ชื่อเดิม ทดสอบ"));
        long firstId = userIdOf(login(STUDENT_ID, "pw").andExpect(status().isOk()));

        when(tuApiClient.verify(STUDENT_ID, "pw")).thenReturn(tuStudent(STUDENT_ID, "ชื่อใหม่ ทดสอบ"));
        long secondId = userIdOf(login(STUDENT_ID, "pw")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("ชื่อใหม่ ทดสอบ")));

        assertThat(secondId).isEqualTo(firstId);
        assertThat(users.count()).isEqualTo(1);
        assertThat(students.count()).isEqualTo(1);
        assertThat(users.findById(firstId).orElseThrow().getFullName()).isEqualTo("ชื่อใหม่ ทดสอบ");
    }

    @Test
    @DisplayName("IT-AD-L03 อาจารย์ที่มีในระบบ (TU ตอบ employee): 200 ADVISOR · role ไม่ถูกเปลี่ยน · ไม่มีแถว students")
    void existingAdvisorKeepsRole() throws Exception {
        User advisor = users.save(new User("wichai.s", "อ.วิชัย เดิม", "wichai.s@tu.ac.th", Role.ADVISOR));
        when(tuApiClient.verify("wichai.s", "pw")).thenReturn(tuEmployee("wichai.s", "A advisor"));

        login("wichai.s", "pw")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADVISOR"))
                .andExpect(jsonPath("$.userId").value(advisor.getId().intValue()));

        User after = users.findByUsername("wichai.s").orElseThrow();
        assertThat(after.getRole()).isEqualTo(Role.ADVISOR);
        assertThat(after.getFullName()).isEqualTo("A advisor");
        assertThat(users.count()).isEqualTo(1);
        assertThat(students.count()).isZero();
    }

    @Test
    @DisplayName("IT-AD-L04 TU ตอบ status false: 401 INVALID_CREDENTIALS · ไม่มีแถวใหม่")
    void wrongPasswordSavesNothing() throws Exception {
        when(tuApiClient.verify(STUDENT_ID, "wrong")).thenReturn(TuVerifyResponse.invalid("Invalid"));

        login(STUDENT_ID, "wrong")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Username หรือ Password ไม่ถูกต้อง"));

        assertThat(users.count()).isZero();
        assertThat(students.count()).isZero();
    }

    @Test
    @DisplayName("IT-AD-L05 บัญชีถูกปิด (active = false): 403 FORBIDDEN · ชื่อเดิมไม่ถูกเขียนทับ")
    void inactiveAccountIsRejectedAndNotUpdated() throws Exception {
        User closed = new User(STUDENT_ID, "ชื่อเดิม ปิดบัญชี", null, Role.STUDENT);
        closed.setActive(false);
        users.save(closed);
        when(tuApiClient.verify(STUDENT_ID, "pw")).thenReturn(tuStudent(STUDENT_ID, "ชื่อใหม่ ไม่ควรบันทึก"));

        login(STUDENT_ID, "pw")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("บัญชีนี้ถูกปิดการใช้งาน กรุณาติดต่อเจ้าหน้าที่"));

        User after = users.findByUsername(STUDENT_ID).orElseThrow();
        assertThat(after.getFullName()).isEqualTo("ชื่อเดิม ปิดบัญชี");
        assertThat(after.getActive()).isFalse();
        assertThat(students.count()).isZero();
    }

    @Test
    @DisplayName("IT-AD-L06 TU REST API ล่ม: 502 TU_API_UNAVAILABLE · ไม่มีแถวใหม่")
    void tuDownSavesNothing() throws Exception {
        when(tuApiClient.verify(STUDENT_ID, "pw")).thenThrow(
                new ApiException(502, "TU_API_UNAVAILABLE", "ไม่สามารถเชื่อมต่อระบบของมหาวิทยาลัยได้ กรุณาลองใหม่อีกครั้ง"));

        login(STUDENT_ID, "pw")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("TU_API_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("ไม่สามารถเชื่อมต่อระบบของมหาวิทยาลัยได้ กรุณาลองใหม่อีกครั้ง"));

        assertThat(users.count()).isZero();
        assertThat(students.count()).isZero();
    }

    @Test
    @DisplayName("IT-AD-L07 บัญชีทดลอง staff01 / 1234: 200 STAFF · อ่านจาก users · ไม่เรียก TU REST API")
    void demoStaffLogsInFromDatabaseOnly() throws Exception {
        User staff = users.save(new User("staff01", "S staff", "staff01@tu.ac.th", Role.STAFF));

        login("staff01", "1234")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STAFF"))
                .andExpect(jsonPath("$.userId").value(staff.getId().intValue()))
                .andExpect(jsonPath("$.fullName").value("S staff"));

        verify(tuApiClient, never()).verify(anyString(), anyString());
        assertThat(users.count()).isEqualTo(1);
        List<User> all = users.findAll();
        assertThat(all).extracting(User::getUsername).containsExactly("staff01");
        assertThat(students.count()).isZero();
    }
}
