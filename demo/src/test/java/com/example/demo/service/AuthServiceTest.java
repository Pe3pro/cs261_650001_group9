package com.example.demo.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.dao.StudentRepository;
import com.example.demo.dao.UserRepository;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.dto.TuVerifyResponse;
import com.example.demo.entity.Role;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;

/** U1:T8 Unit test logic การ login (AuthService) โดยไม่ต่อ TU REST API และฐานข้อมูลจริง */
class AuthServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T02:00:00Z"), ZoneId.of("Asia/Bangkok"));

    private TuApiClient tu;
    private UserRepository users;
    private StudentRepository students;
    private AuthService service;

    @BeforeEach
    void setUp() {
        tu = mock(TuApiClient.class);
        users = mock(UserRepository.class);
        students = mock(StudentRepository.class);
        service = new AuthService(tu, users, students, CLOCK, "1234", "staff01, advisor01");
        when(users.findByUsername(anyString())).thenReturn(Optional.empty());
        when(students.findByUserId(any())).thenReturn(Optional.empty());
        when(students.findByStudentId(anyString())).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) {
                ReflectionTestUtils.setField(u, "id", 10L);
            }
            return u;
        });
    }

    private static TuVerifyResponse tuOk(String type, String username, String nameTh) {
        return new TuVerifyResponse(true, "Success", type, username, nameTh, "Test User",
                username + "@dome.tu.ac.th", "วิทยาการคอมพิวเตอร์", "คณะวิทยาศาสตร์และเทคโนโลยี");
    }

    private static User existing(Long id, String username, String name, Role role) {
        User u = new User(username, name, username + "@tu.ac.th", role);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    @Test
    @DisplayName("SV-L01 นักศึกษา login ครั้งแรก: สร้าง User role STUDENT และ Student จากข้อมูล TU")
    void firstLoginStudent() {
        when(tu.verify("6609650099", "secret")).thenReturn(tuOk("student", "6609650099", "ทดสอบ ระบบ"));

        LoginResponse r = service.login(new LoginRequest(" 6609650099 ", "secret"));

        assertThat(r.userId()).isEqualTo(10L);
        assertThat(r.role()).isEqualTo(Role.STUDENT);
        assertThat(r.fullName()).isEqualTo("ทดสอบ ระบบ");
        assertThat(r.tokenType()).isEqualTo("Bearer");
        assertThat(r.accessToken()).isNotBlank();
        assertThat(r.expiresAt()).isEqualTo(Instant.parse("2026-10-19T10:00:00Z")); // +8 ชั่วโมง
        ArgumentCaptor<Student> st = ArgumentCaptor.forClass(Student.class);
        verify(students).save(st.capture());
        assertThat(st.getValue().getUserId()).isEqualTo(10L);
        assertThat(st.getValue().getStudentId()).isEqualTo("6609650099");
        assertThat(st.getValue().getMajor()).isEqualTo("วิทยาการคอมพิวเตอร์");
    }

    @Test
    @DisplayName("SV-L02 บุคลากร (employee) login ครั้งแรก: role STAFF และไม่สร้าง Student")
    void firstLoginEmployee() {
        when(tu.verify("somsri.t", "secret")).thenReturn(tuOk("employee", "somsri.t", "S staff"));

        LoginResponse r = service.login(new LoginRequest("somsri.t", "secret"));

        assertThat(r.role()).isEqualTo(Role.STAFF);
        verify(students, never()).save(any());
    }

    @Test
    @DisplayName("SV-L03 ผู้ใช้เดิมที่เป็น ADVISOR: ใช้ role เดิม และอัปเดตชื่อ/อีเมลจาก TU")
    void existingUserKeepsRole() {
        User advisor = existing(2L, "wichai.s", "ชื่อเก่า", Role.ADVISOR);
        when(users.findByUsername("wichai.s")).thenReturn(Optional.of(advisor));
        when(tu.verify("wichai.s", "secret")).thenReturn(tuOk("employee", "wichai.s", "A advisor"));

        LoginResponse r = service.login(new LoginRequest("wichai.s", "secret"));

        assertThat(r.userId()).isEqualTo(2L);
        assertThat(r.role()).isEqualTo(Role.ADVISOR);
        assertThat(advisor.getFullName()).isEqualTo("A advisor");
        assertThat(advisor.getEmail()).isEqualTo("wichai.s@dome.tu.ac.th");
    }

    @Test
    @DisplayName("SV-L04 นักศึกษาเดิมที่มี Student อยู่แล้ว ไม่สร้างซ้ำ")
    void existingStudentNotDuplicated() {
        when(users.findByUsername("6609650001")).thenReturn(Optional.of(existing(3L, "6609650001", "A arizona", Role.STUDENT)));
        when(students.findByUserId(3L)).thenReturn(Optional.of(new Student(3L, "6609650001", "วิทย์", "คอม", 3.1)));
        when(tu.verify("6609650001", "secret")).thenReturn(tuOk("student", "6609650001", "A arizona"));

        service.login(new LoginRequest("6609650001", "secret"));

        verify(students, never()).save(any());
    }

    @Test
    @DisplayName("SV-L05 TU ตอบ status false: 401 INVALID_CREDENTIALS และไม่บันทึกอะไร")
    void wrongPassword() {
        when(tu.verify("6609650001", "wrong")).thenReturn(TuVerifyResponse.invalid("Password incorrect"));

        assertThatThrownBy(() -> service.login(new LoginRequest("6609650001", "wrong")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    assertThat(((ApiException) e).getStatus()).isEqualTo(401);
                    assertThat(((ApiException) e).getCode()).isEqualTo("INVALID_CREDENTIALS");
                });
        verify(users, never()).save(any());
    }

    @Test
    @DisplayName("SV-L06 ไม่กรอก Username หรือ Password: 400 และไม่เรียก TU REST API")
    void blankInput() {
        for (LoginRequest req : new LoginRequest[] { new LoginRequest("", "x"), new LoginRequest("6609650001", ""),
                new LoginRequest("   ", "x"), new LoginRequest(null, null), null }) {
            assertThatThrownBy(() -> service.login(req)).isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(400);
        }
        verify(tu, never()).verify(anyString(), anyString());
    }

    @Test
    @DisplayName("SV-L07 บัญชีถูกปิดใช้งาน: 403 FORBIDDEN และไม่อัปเดตข้อมูล")
    void inactive() {
        User off = existing(9L, "advisor03", "อ.เกษียณ", Role.ADVISOR);
        off.setActive(false);
        when(users.findByUsername("advisor03")).thenReturn(Optional.of(off));
        when(tu.verify("advisor03", "secret")).thenReturn(tuOk("employee", "advisor03", "ชื่อใหม่"));

        assertThatThrownBy(() -> service.login(new LoginRequest("advisor03", "secret")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(403);
        assertThat(off.getFullName()).isEqualTo("อ.เกษียณ");
        verify(users, never()).save(any());
    }

    @Test
    @DisplayName("SV-L08 TU REST API ล่ม: ส่งต่อ 502 TU_API_UNAVAILABLE")
    void tuDown() {
        when(tu.verify(anyString(), anyString())).thenThrow(TuApiClient.unavailable());

        assertThatThrownBy(() -> service.login(new LoginRequest("6609650001", "secret")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(502);
    }

    @Test
    @DisplayName("SV-L09 บัญชีทดลอง staff01 + รหัสทดลอง: login ได้โดยไม่เรียก TU REST API")
    void demoAccount() {
        when(users.findByUsername("staff01")).thenReturn(Optional.of(existing(1L, "staff01", "S staff", Role.STAFF)));

        LoginResponse r = service.login(new LoginRequest("staff01", "1234"));

        assertThat(r.userId()).isEqualTo(1L);
        assertThat(r.role()).isEqualTo(Role.STAFF);
        verify(tu, never()).verify(anyString(), anyString());
    }

    @Test
    @DisplayName("SV-L10 รหัสทดลองกับบัญชีที่ไม่ใช่บัญชีทดลอง: ต้องตรวจกับ TU REST API ตามปกติ")
    void demoPasswordOnRealAccount() {
        when(tu.verify("6609650099", "1234")).thenReturn(TuVerifyResponse.invalid(null));

        assertThatThrownBy(() -> service.login(new LoginRequest("6609650099", "1234")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(401);
        verify(tu).verify("6609650099", "1234");
    }

    @Test
    @DisplayName("SV-L11 ปิดบัญชีทดลอง (demo-password ว่าง): staff01 ต้องผ่าน TU REST API")
    void demoDisabled() {
        AuthService noDemo = new AuthService(tu, users, students, CLOCK, "", "staff01");
        when(tu.verify("staff01", "1234")).thenReturn(TuVerifyResponse.invalid(null));

        assertThatThrownBy(() -> noDemo.login(new LoginRequest("staff01", "1234")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("SV-L12 แปลง type เป็น Role: student/employee ได้, ประเภทอื่นได้ 403")
    void toRole() {
        assertThat(AuthService.toRole("student")).isEqualTo(Role.STUDENT);
        assertThat(AuthService.toRole("Employee")).isEqualTo(Role.STAFF);
        assertThatThrownBy(() -> AuthService.toRole("alumni")).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(403);
    }
}