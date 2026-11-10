package com.example.demo.dao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.example.demo.entity.Application;
import com.example.demo.entity.ApplicationStatus;
import com.example.demo.entity.Document;
import com.example.demo.entity.FormResponse;
import com.example.demo.entity.Student;

/** U5:T9 Unit test ส่วน DAO: Repository ที่ใช้ตอนเจ้าหน้าที่รับคำร้อง */
@DataJpaTest
class ApplicationReviewRepositoryTest {

    private static final Long FORM_ID = 10L;

    @Autowired
    private ApplicationRepository applications;
    @Autowired
    private DocumentRepository documents;
    @Autowired
    private FormResponseRepository responses;
    @Autowired
    private StudentRepository students;

    private Application early;
    private Application late;
    private Application underReview;

    @BeforeEach
    void setUp() {
        // U3 เพิ่ม Foreign Key applications.student_id → students.id จึงต้องมีนักศึกษาจริงก่อนบันทึกคำร้อง
        Long[] ids = new Long[4];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = students.save(new Student(900L + i, "660965090" + i, "วิทยาศาสตร์และเทคโนโลยี",
                    "วิทยาการคอมพิวเตอร์", 3.0)).getId();
        }
        early = submitted(ids[0], LocalDateTime.of(2026, 10, 15, 10, 24));
        late = submitted(ids[1], LocalDateTime.of(2026, 10, 16, 9, 15));
        underReview = submitted(ids[2], LocalDateTime.of(2026, 10, 14, 8, 0));
        underReview.changeStatus(ApplicationStatus.UNDER_REVIEW);
        underReview = applications.save(underReview);
        applications.save(new Application(ids[3], FORM_ID, ApplicationStatus.DRAFT));
    }

    private Application submitted(Long studentId, LocalDateTime at) {
        Application a = new Application(studentId, FORM_ID, ApplicationStatus.DRAFT);
        a.submit(at);
        return applications.save(a);
    }

    @Test
    @DisplayName("DAO-A01 คำร้องรอตรวจ (SUBMITTED) เรียงตามเวลายื่น ยื่นก่อนอยู่บน")
    void submittedOrderedBySubmitTime() {
        assertThat(applications.findByStatusOrderBySubmittedAtAsc(ApplicationStatus.SUBMITTED))
                .extracting(Application::getId).containsExactly(early.getId(), late.getId());
    }

    @Test
    @DisplayName("DAO-A02 ค้นสถานะ UNDER_REVIEW ได้เฉพาะคำร้องที่รออาจารย์")
    void underReviewOnly() {
        assertThat(applications.findByStatusOrderBySubmittedAtAsc(ApplicationStatus.UNDER_REVIEW))
                .extracting(Application::getId).containsExactly(underReview.getId());
    }

    @Test
    @DisplayName("DAO-A03 ไม่มีคำร้องสถานะนั้น ได้รายการว่าง")
    void noneWithStatus() {
        assertThat(applications.findByStatusOrderBySubmittedAtAsc(ApplicationStatus.APPROVED)).isEmpty();
    }

    @Test
    @DisplayName("DAO-A04 เปลี่ยนสถานะคำร้องแล้วบันทึก ค่าใหม่ถูกเก็บ และย้ายไปอยู่รายการสถานะใหม่")
    void changeStatus() {
        early.changeStatus(ApplicationStatus.NEED_CORRECTION);
        applications.saveAndFlush(early);

        assertThat(applications.findById(early.getId()).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.NEED_CORRECTION);
        assertThat(applications.countByStatus(ApplicationStatus.SUBMITTED)).isEqualTo(1);
    }

    @Test
    @DisplayName("DAO-A05 อ่านเอกสารแนบของคำร้อง")
    void documentsOfApplication() {
        documents.save(new Document(early.getId(), "transcript.pdf", "k/1.pdf", 512_000L));

        assertThat(documents.findByApplicationId(early.getId()))
                .extracting(Document::getFileName).containsExactly("transcript.pdf");
        assertThat(documents.findByApplicationId(late.getId())).isEmpty();
    }

    @Test
    @DisplayName("DAO-A06 อ่านคำตอบในแบบฟอร์มของคำร้อง")
    void answersOfApplication() {
        responses.save(new FormResponse(early.getId(), 100L, "A arizona"));
        responses.save(new FormResponse(early.getId(), 101L, "0812345678"));
        responses.save(new FormResponse(late.getId(), 100L, "B berlin"));

        assertThat(responses.findByApplicationId(early.getId()))
                .extracting(FormResponse::getValue).containsExactlyInAnyOrder("A arizona", "0812345678");
    }

    @Test
    @DisplayName("DAO-A07 ค้นนักศึกษาจาก userId และรหัสนักศึกษา")
    void findStudent() {
        students.save(new Student(30L, "6609650001", "วิทยาศาสตร์และเทคโนโลยี", "วิทยาการคอมพิวเตอร์", 3.10));

        assertThat(students.findByUserId(30L)).get().extracting(Student::getStudentId).isEqualTo("6609650001");
        assertThat(students.findByStudentId("6609650001")).isPresent();
        assertThat(students.findByStudentId("0000000000")).isEmpty();
    }
}
