package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.dto.ApplicationReviewResponse;
import com.example.demo.entity.ApplicationStatus;
import com.example.demo.entity.Role;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;

/** U5: ใครเห็นคำร้องของนักศึกษาคนไหนได้ (ApplicationReviewService.canSee) */
class ApplicationReviewServiceTest {

    private static User user(Long id, Role role) {
        User u = new User("u" + id, "ชื่อ " + id, null, role);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private static Student studentOf(Long advisorId) {
        Student s = new Student(30L, "6609650001", null, null, null);
        s.setAdvisorId(advisorId);
        return s;
    }

    @Test
    @DisplayName("RV-01 อาจารย์ที่ปรึกษาเห็นคำร้องของนักศึกษาตนเอง")
    void ownAdvisorSees() {
        assertThat(ApplicationReviewService.canSee(user(2L, Role.ADVISOR), studentOf(2L))).isTrue();
    }

    @Test
    @DisplayName("RV-02 อาจารย์คนอื่นไม่เห็น และนักศึกษาที่ยังไม่มีอาจารย์ที่ปรึกษาก็ไม่แสดงให้อาจารย์")
    void otherAdvisorDoesNotSee() {
        assertThat(ApplicationReviewService.canSee(user(4L, Role.ADVISOR), studentOf(2L))).isFalse();
        assertThat(ApplicationReviewService.canSee(user(2L, Role.ADVISOR), studentOf(null))).isFalse();
    }

    @Test
    @DisplayName("RV-03 เจ้าหน้าที่เห็นทุกคำร้อง")
    void staffSeesAll() {
        assertThat(ApplicationReviewService.canSee(user(1L, Role.STAFF), studentOf(null))).isTrue();
    }

    // ---------- U5: ป้ายเตือน / เทียบฉบับแก้ / CSV ----------

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 10, 0);
    private static final LocalDateTime FAR_DEADLINE = LocalDateTime.of(2026, 12, 31, 23, 59);

    @Test
    @DisplayName("RV-04 รอตรวจนาน 3 วันขึ้นไป → OVERDUE · เพิ่งส่ง → ไม่มีป้าย")
    void overdue() {
        assertThat(ApplicationReviewService.attention(ApplicationStatus.SUBMITTED, NOW.minusDays(3), FAR_DEADLINE, NOW))
                .isEqualTo(ApplicationReviewResponse.OVERDUE);
        assertThat(ApplicationReviewService.attention(ApplicationStatus.SUBMITTED, NOW.minusDays(2), FAR_DEADLINE, NOW))
                .isNull();
    }

    @Test
    @DisplayName("RV-05 แบบฟอร์มหมดเขตภายใน 2 วัน → DEADLINE_SOON (สำคัญกว่า OVERDUE) · หมดเขตแล้ว → ไม่ใช่ DEADLINE_SOON")
    void deadlineSoon() {
        assertThat(ApplicationReviewService.attention(ApplicationStatus.UNDER_REVIEW, NOW.minusDays(5),
                NOW.plusDays(2), NOW)).isEqualTo(ApplicationReviewResponse.DEADLINE_SOON);
        assertThat(ApplicationReviewService.attention(ApplicationStatus.SUBMITTED, NOW.minusDays(5),
                NOW.minusHours(1), NOW)).isEqualTo(ApplicationReviewResponse.OVERDUE);
    }

    @Test
    @DisplayName("RV-06 คำร้องที่ตัดสินแล้ว / รอนักศึกษาแก้: ไม่มีป้ายเตือน")
    void noAttentionWhenNotWaitingForReviewer() {
        for (ApplicationStatus s : List.of(ApplicationStatus.APPROVED, ApplicationStatus.REJECTED,
                ApplicationStatus.NEED_CORRECTION)) {
            assertThat(ApplicationReviewService.attention(s, NOW.minusDays(10), NOW.plusDays(1), NOW)).isNull();
        }
    }

    @Test
    @DisplayName("RV-07 เทียบฉบับแก้: ช่องที่เปลี่ยนคืนค่าเดิม · ไม่เปลี่ยน (ต่างแค่ช่องว่าง) / ไม่เคยถูกตีกลับ → null")
    void changedFrom() {
        Map<Long, String> before = Map.of(101L, "0811111111", 102L, "สมชาย");
        assertThat(ApplicationReviewService.changedFrom(before, 101L, "0822222222")).isEqualTo("0811111111");
        assertThat(ApplicationReviewService.changedFrom(before, 102L, " สมชาย ")).isNull();
        assertThat(ApplicationReviewService.changedFrom(before, 103L, "ใหม่")).isEqualTo("");
        assertThat(ApplicationReviewService.changedFrom(Map.of(), 101L, "0822222222")).isNull();
    }

    @Test
    @DisplayName("RV-08 ค่าใน CSV: ใส่เครื่องหมายคำพูดเมื่อมี , หรือ \" และกันสูตร Excel")
    void csvCell() {
        assertThat(ApplicationReviewService.csvCell(null)).isEmpty();
        assertThat(ApplicationReviewService.csvCell("สมชาย ใจดี")).isEqualTo("สมชาย ใจดี");
        assertThat(ApplicationReviewService.csvCell("a,b")).isEqualTo("\"a,b\"");
        assertThat(ApplicationReviewService.csvCell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(ApplicationReviewService.csvCell("=SUM(A1)")).isEqualTo("'=SUM(A1)");
    }
}

