package com.example.demo.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import com.example.demo.entity.Application;
import com.example.demo.entity.ApplicationStatus;
import com.example.demo.entity.Document;
import com.example.demo.entity.FieldType;
import com.example.demo.entity.FormField;
import com.example.demo.entity.FormResponse;
import com.example.demo.entity.FormTemplate;
import com.example.demo.entity.Student;

/**
 * U3:T9 Unit test ส่วน DAO ตาม T12.md / T9.md (U3-DAO-01 ถึง U3-DAO-11)
 * ชื่อในเอกสาร → ชื่อในโค้ด: RequestRepository = ApplicationRepository, FormAnswerRepository = FormResponseRepository
 * ใช้ @DataJpaTest (H2 โหมด MySQL ในหน่วยความจำ สร้างตารางจาก Entity จริง) ทุก test rollback อัตโนมัติ
 */
@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:u3dao;MODE=MySQL;DB_CLOSE_DELAY=-1")
class U3RequestRepositoryTest {

    private static final LocalDateTime F1_DEADLINE = LocalDateTime.of(2026, 11, 30, 23, 59, 59);

    @Autowired
    private ApplicationRepository requests;
    @Autowired
    private FormResponseRepository answers;
    @Autowired
    private DocumentRepository documents;
    @Autowired
    private FormTemplateRepository templates;
    @Autowired
    private FormFieldRepository fields;
    @Autowired
    private StudentRepository students;
    @Autowired
    private TestEntityManager em;

    private Student a;
    private Student b;
    private FormTemplate f1;
    private List<FormField> f1Fields;
    private Application requestB;

    @BeforeEach
    void setUp() {
        a = students.save(new Student(3L, "6609650001", "วิทยาศาสตร์และเทคโนโลยี", "วิทยาการคอมพิวเตอร์", 3.10));
        b = students.save(new Student(4L, "6609650002", "วิทยาศาสตร์และเทคโนโลยี", "วิทยาการคอมพิวเตอร์", 3.45));
        FormTemplate form = new FormTemplate("คำร้องขอฝึกสหกิจศึกษา 2/2569", 1, 1L);
        form.setDeadline(F1_DEADLINE);
        form.publish(LocalDateTime.of(2026, 10, 1, 9, 0));
        f1 = templates.save(form);
        f1Fields = fields.saveAll(List.of(
                new FormField(f1.getId(), "ชื่อ-นามสกุล", FieldType.TEXT, true, 1),
                new FormField(f1.getId(), "เบอร์โทรศัพท์", FieldType.PHONE, true, 2),
                new FormField(f1.getId(), "อีเมล", FieldType.EMAIL, true, 3),
                new FormField(f1.getId(), "ที่อยู่ปัจจุบัน", FieldType.TEXTAREA, true, 4)));
        Application app = new Application(b.getId(), f1.getId(), ApplicationStatus.DRAFT);
        app.submit(LocalDateTime.of(2026, 10, 18, 9, 0));
        requestB = requests.save(app);
    }

    private void saveFourAnswers(Long applicationId) {
        String[] values = { "B berlin", "0812345678", "berlin@dome.tu.ac.th", "99 ม.ธ. รังสิต" };
        for (int i = 0; i < values.length; i++) {
            answers.save(new FormResponse(applicationId, f1Fields.get(i).getId(), values[i]));
        }
    }

    @Test
    @DisplayName("U3-DAO-01 บันทึกคำร้องใหม่ของ นศ. A ฟอร์ม F1: ได้ ID สถานะ DRAFT และ student_id ถูกต้อง")
    void saveNewRequest() {
        Application saved = requests.saveAndFlush(new Application(a.getId(), f1.getId(), ApplicationStatus.DRAFT));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(ApplicationStatus.DRAFT);
        assertThat(saved.getStudentId()).isEqualTo(a.getId());
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("U3-DAO-02 findById ด้วย ID ที่มีอยู่: คืนคำร้องพร้อมคำตอบแบบฟอร์ม")
    void findByIdWithAnswers() {
        saveFourAnswers(requestB.getId());
        em.flush();
        em.clear();

        Application found = requests.findById(requestB.getId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
        assertThat(answers.findByApplicationId(found.getId())).hasSize(4);
    }

    @Test
    @DisplayName("U3-DAO-03 findById ด้วย ID ที่ไม่มีอยู่ (999999): คืน Optional.empty")
    void findByIdNotFound() {
        assertThat(requests.findById(999999L)).isEmpty();
    }

    @Test
    @DisplayName("U3-DAO-04 findByStudentId: คืนเฉพาะคำร้องของนักศึกษาคนนั้น")
    void findByStudentId() {
        requests.save(new Application(a.getId(), f1.getId(), ApplicationStatus.DRAFT));

        assertThat(requests.findByStudentId(b.getId()))
                .extracting(Application::getId).containsExactly(requestB.getId());
        assertThat(requests.findByStudentIdOrderByIdDesc(b.getId())).allMatch(r -> r.getStudentId().equals(b.getId()));
    }

    @Test
    @DisplayName("U3-DAO-05 บันทึกคำร้องที่อ้าง student_id 999 ที่ไม่มีอยู่: Foreign Key ปฏิเสธ")
    void foreignKeyRejectsUnknownStudent() {
        assertThatThrownBy(() -> requests.saveAndFlush(new Application(999L, f1.getId(), ApplicationStatus.DRAFT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("U3-DAO-06 บันทึกคำตอบ 4 ช่องแล้วอ่านกลับ: ได้คำตอบครบและตรงกับช่องของแบบฟอร์ม")
    void saveAndReadAnswers() {
        saveFourAnswers(requestB.getId());
        em.flush();
        em.clear();

        List<FormResponse> read = answers.findByApplicationId(requestB.getId());

        assertThat(read).hasSize(4);
        assertThat(read).extracting(FormResponse::getFormFieldId)
                .containsExactlyInAnyOrderElementsOf(f1Fields.stream().map(FormField::getId).toList());
        assertThat(read).extracting(FormResponse::getValue)
                .contains("B berlin", "0812345678", "berlin@dome.tu.ac.th", "99 ม.ธ. รังสิต");
    }

    @Test
    @DisplayName("U3-DAO-07 บันทึก metadata ของ transcript.pdf: มีชื่อไฟล์ ขนาด ที่เก็บไฟล์ และผูกกับคำร้องถูกต้อง")
    void saveDocumentMetadata() {
        Document saved = documents.saveAndFlush(
                new Document(requestB.getId(), "transcript.pdf", "transcripts/abc.pdf", 512_000L));
        em.clear();

        Document read = documents.findById(saved.getId()).orElseThrow();
        assertThat(read.getFileName()).isEqualTo("transcript.pdf");
        assertThat(read.getFileSize()).isEqualTo(512_000L);
        assertThat(read.getStorageKey()).isEqualTo("transcripts/abc.pdf");
        assertThat(read.getContentType()).isEqualTo("application/pdf");
        assertThat(read.getApplicationId()).isEqualTo(requestB.getId());
        assertThat(documents.findByApplicationId(requestB.getId())).hasSize(1);
    }

    @Test
    @DisplayName("U3-DAO-08 ค้นหาด้วยค่าที่มีอักขระพิเศษ: ไม่เกิด SQL Injection ได้ผลว่าง และตาราง applications ยังอยู่ครบ")
    void noSqlInjection() {
        long before = requests.count();

        assertThat(students.findByStudentId("' OR '1'='1")).isEmpty();
        assertThat(students.findByStudentId("1; DROP TABLE applications;--")).isEmpty();
        em.flush();
        em.clear();

        assertThat(requests.count()).isEqualTo(before);
        assertThat(requests.findById(requestB.getId())).isPresent();
    }

    @Test
    @DisplayName("U3-DAO-09 บันทึกการแก้ไขคำร้องที่ถูกส่งกลับ (NEED_CORRECTION → SUBMITTED): ID เดิม สถานะใหม่ updated_at เปลี่ยน")
    void saveCorrectedRequest() throws InterruptedException {
        Application app = requests.saveAndFlush(new Application(a.getId(), f1.getId(), ApplicationStatus.NEED_CORRECTION));
        Long id = app.getId();
        LocalDateTime firstUpdate = app.getUpdatedAt();
        Thread.sleep(20);

        app.submit(LocalDateTime.of(2026, 10, 20, 10, 0));
        requests.saveAndFlush(app);
        em.clear();

        Application read = requests.findById(id).orElseThrow();
        assertThat(read.getId()).isEqualTo(id);
        assertThat(read.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
        assertThat(read.getUpdatedAt()).isAfter(firstUpdate);
    }

    @Test
    @DisplayName("U3-DAO-10 แก้ไขคำตอบ (เบอร์โทรเป็น 0899999999): ค่าใหม่ถูกบันทึก ไม่เกิดรายการซ้ำ")
    void updateAnswerWithoutDuplicate() {
        saveFourAnswers(requestB.getId());
        Long phoneField = f1Fields.get(1).getId();

        FormResponse phone = answers.findByApplicationIdAndFormFieldId(requestB.getId(), phoneField).orElseThrow();
        phone.setValue("0899999999");
        answers.saveAndFlush(phone);
        em.clear();

        assertThat(answers.findByApplicationId(requestB.getId())).hasSize(4);
        assertThat(answers.findByApplicationIdAndFormFieldId(requestB.getId(), phoneField).orElseThrow().getValue())
                .isEqualTo("0899999999");
    }

    @Test
    @DisplayName("U3-DAO-11 อ่านวันหมดเขตของฟอร์ม F1: ได้ 30 พ.ย. 2569 23:59:59 ตรงตามที่บันทึก ไม่เพี้ยนเพราะ timezone")
    void readDeadline() {
        em.flush();
        em.clear();

        FormTemplate read = templates.findById(f1.getId()).orElseThrow();
        assertThat(read.getDeadline()).isEqualTo(F1_DEADLINE);
    }
}
