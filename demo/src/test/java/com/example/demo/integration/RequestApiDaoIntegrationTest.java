package com.example.demo.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.demo.dao.ApplicationRepository;
import com.example.demo.dao.ApprovalRepository;
import com.example.demo.dao.DocumentRepository;
import com.example.demo.dao.EligibilityRecordDao;
import com.example.demo.dao.FormFieldRepository;
import com.example.demo.dao.FormResponseRepository;
import com.example.demo.dao.FormTemplateRepository;
import com.example.demo.dao.NotificationRepository;
import com.example.demo.dao.StudentRepository;
import com.example.demo.dao.UserRepository;
import com.example.demo.entity.Application;
import com.example.demo.entity.ApplicationStatus;
import com.example.demo.entity.FieldType;
import com.example.demo.entity.FormField;
import com.example.demo.entity.FormTemplate;
import com.example.demo.entity.Role;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;
import com.example.demo.service.EmailNotifier;
import com.example.demo.service.FileStorage;
import com.jayway.jsonpath.JsonPath;

/**
 * U3:T18 Integration Test ระหว่าง API กับ DAO ตาม T14.md
 * (U3-INT-01 ถึง U3-INT-04 และ U3-INT-09 ถึง U3-INT-14)
 *
 * RequestController → RequestService / DocumentService → ApplicationRepository / FormResponseRepository /
 * DocumentRepository → ฐานข้อมูล H2 (โหมด MySQL สร้างตารางจาก Entity จริง)
 * - เรียก API ผ่าน MockMvc ไม่ใช้หน้าเว็บ · ล้างข้อมูลทุกตารางก่อนทุกข้อ
 * - จำลองเฉพาะ Email (@MockitoBean EmailNotifier) และ Clock (กำหนดเวลา server เองได้)
 * - ที่เก็บไฟล์ใช้ของจริง (LocalFileStorage โฟลเดอร์ target/u3-it-uploads) และ spy ไว้ให้ INT-03 ทำให้ล้มเหลว
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:u3t18db;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.seed-demo-data=false",
        "app.upload-dir=target/u3-it-uploads"
})
@AutoConfigureMockMvc
class RequestApiDaoIntegrationTest {

    private static final ZoneId BKK = ZoneId.of("Asia/Bangkok");
    private static final byte[] PDF = "%PDF-1.4\n% U3 test transcript\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    private static final String ANSWERS = "{\"answers\":{\"field_1\":\"สมชาย ใจดี\",\"field_2\":\"0812345678\","
            + "\"field_3\":\"somchai@dome.tu.ac.th\",\"field_4\":\"99 ม.ธ. รังสิต\"}}";

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private StudentRepository students;
    @Autowired private FormTemplateRepository templates;
    @Autowired private FormFieldRepository fields;
    @Autowired private ApplicationRepository applications;
    @Autowired private FormResponseRepository responses;
    @Autowired private DocumentRepository documents;
    @Autowired private ApprovalRepository approvals;
    @Autowired private NotificationRepository notifications;
    @Autowired private EligibilityRecordDao eligibility;

    @MockitoBean private EmailNotifier email;
    @MockitoBean private Clock clock;
    @MockitoSpyBean private FileStorage storage;

    private final AtomicReference<Instant> now = new AtomicReference<>();

    private Long staffS;
    private Long userA;
    private Long userB;
    private Long userC;
    private Long studentA;
    private Long studentB;
    private Long studentC;
    private Long f1;
    private Long f2;
    private Long requestB;
    private Long requestCRejected;

    @BeforeEach
    void setUp() {
        documents.deleteAll();
        responses.deleteAll();
        approvals.deleteAll();
        notifications.deleteAll();
        applications.deleteAll();
        fields.deleteAll();
        templates.deleteAll();
        students.deleteAll();
        users.deleteAll();
        eligibility.deleteAll();

        setServerTime(LocalDateTime.of(2026, 10, 20, 10, 0));
        when(clock.instant()).thenAnswer(inv -> now.get());
        when(clock.getZone()).thenReturn(BKK);

        staffS = users.save(new User("staff01", "สมศรี ทำงานดี", "staff01@tu.ac.th", Role.STAFF)).getId();
        userA = users.save(new User("6609650001", "สมชาย ใจดี", "somchai@dome.tu.ac.th", Role.STUDENT)).getId();
        userB = users.save(new User("6609650002", "สมหญิง รักเรียน", "somying@dome.tu.ac.th", Role.STUDENT)).getId();
        userC = users.save(new User("6609650003", "สมปอง ตั้งใจ", "sompong@dome.tu.ac.th", Role.STUDENT)).getId();
        studentA = students.save(new Student(userA, "6609650001", "วิทยาศาสตร์และเทคโนโลยี", "วิทยาการคอมพิวเตอร์", 3.10)).getId();
        studentB = students.save(new Student(userB, "6609650002", "วิทยาศาสตร์และเทคโนโลยี", "วิทยาการคอมพิวเตอร์", 3.45)).getId();
        studentC = students.save(new Student(userC, "6609650003", "วิทยาศาสตร์และเทคโนโลยี", "วิทยาการคอมพิวเตอร์", 3.25)).getId();

        f1 = publishedForm("คำร้องขอฝึกสหกิจศึกษา 2/2569 (F1)", 1, LocalDateTime.of(2026, 11, 30, 23, 59, 59));
        f2 = publishedForm("คำร้องขอฝึกสหกิจศึกษา 1/2569 (F2)", 2, LocalDateTime.of(2026, 10, 1, 23, 59, 59));

        Application b = new Application(studentB, f1, ApplicationStatus.DRAFT);
        b.submit(LocalDateTime.of(2026, 10, 18, 9, 0));
        requestB = applications.save(b).getId();
        Application c = new Application(studentC, f1, ApplicationStatus.DRAFT);
        c.changeStatus(ApplicationStatus.REJECTED);
        requestCRejected = applications.save(c).getId();
    }

    private void setServerTime(LocalDateTime t) {
        now.set(t.atZone(BKK).toInstant());
    }

    private Long publishedForm(String name, int version, LocalDateTime deadline) {
        FormTemplate f = new FormTemplate(name, version, 1L);
        f.setDeadline(deadline);
        f.publish(LocalDateTime.of(2026, 9, 1, 9, 0));
        Long id = templates.save(f).getId();
        fields.saveAll(List.of(
                new FormField(id, "ชื่อ-นามสกุล", FieldType.TEXT, true, 1),
                new FormField(id, "เบอร์โทรศัพท์", FieldType.PHONE, true, 2),
                new FormField(id, "อีเมล", FieldType.EMAIL, true, 3),
                new FormField(id, "ที่อยู่ปัจจุบัน", FieldType.TEXTAREA, true, 4),
                new FormField(id, "Transcript", FieldType.FILE, true, 5)));
        return id;
    }

    // ---------- ขั้นตอนเรียก API ----------

    private Long create(Long user, Long formId, int expected) throws Exception {
        String body = mvc.perform(post("/api/requests").header("X-User-Id", user)
                .contentType(MediaType.APPLICATION_JSON).content("{\"formTemplateId\":" + formId + "}"))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return expected == 201 ? ((Number) JsonPath.read(body, "$.id")).longValue() : null;
    }

    private void fill(Long user, Long id) throws Exception {
        mvc.perform(put("/api/requests/" + id + "/form").header("X-User-Id", user)
                .contentType(MediaType.APPLICATION_JSON).content(ANSWERS))
                .andExpect(status().isOk());
    }

    private void upload(Long user, Long id, int expected) throws Exception {
        mvc.perform(multipart("/api/requests/" + id + "/documents")
                .file(new MockMultipartFile("file", "transcript.pdf", "application/pdf", PDF))
                .header("X-User-Id", user))
                .andExpect(status().is(expected));
    }

    private Application reload(Long id) {
        return applications.findById(id).orElseThrow();
    }

    // ---------- Test cases ----------

    @Test
    @DisplayName("U3-INT-01 นศ. A สร้างคำร้อง → กรอกฟอร์ม → แนบ PDF → ส่ง ได้ 201, 200, 201, 200 และข้อมูลผูกกับคำร้องเดียวกัน")
    void fullSubmitFlow() throws Exception {
        Long id = create(userA, f1, 201);
        fill(userA, id);
        upload(userA, id, 201);
        mvc.perform(post("/api/requests/" + id + "/submit").header("X-User-Id", userA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.id").value(id));

        Application saved = reload(id);
        assertThat(saved.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
        assertThat(saved.getSubmittedAt()).isEqualTo(LocalDateTime.of(2026, 10, 20, 10, 0));
        assertThat(saved.getStudentId()).isEqualTo(studentA);
        assertThat(responses.findByApplicationId(id)).hasSize(4);
        assertThat(documents.findByApplicationId(id)).singleElement()
                .satisfies(d -> assertThat(storage.load(d.getStorageKey())).isEqualTo(PDF));
        verify(email).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("U3-INT-02 กรอกฟอร์มครบแต่ไม่แนบ PDF แล้วส่ง ได้ 400 และสถานะยังเป็น DRAFT")
    void submitWithoutPdf() throws Exception {
        Long id = create(userA, f1, 201);
        fill(userA, id);

        mvc.perform(post("/api/requests/" + id + "/submit").header("X-User-Id", userA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_DOCUMENT"));

        assertThat(reload(id).getStatus()).isEqualTo(ApplicationStatus.DRAFT);
        assertThat(reload(id).getSubmittedAt()).isNull();
        verify(email, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("U3-INT-03 ที่เก็บไฟล์ล้มเหลวขณะอัปโหลด ได้ 500 STORAGE_ERROR และไม่มี metadata ค้างในตาราง documents")
    void storageFailure() throws Exception {
        Long id = create(userA, f1, 201);
        doThrow(new FileStorage.StorageException("disk full", null)).when(storage).store(anyString(), any());

        mvc.perform(multipart("/api/requests/" + id + "/documents")
                .file(new MockMultipartFile("file", "transcript.pdf", "application/pdf", PDF))
                .header("X-User-Id", userA))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("STORAGE_ERROR"));

        assertThat(documents.findByApplicationId(id)).isEmpty();
        assertThat(documents.count()).isZero();
    }

    @Test
    @DisplayName("U3-INT-04 หลัง INT-01 เจ้าหน้าที่ S เปิดรายการคำร้อง (API ของ U5) เห็นคำร้องพร้อมข้อมูลตรงกับฐานข้อมูล")
    void staffSeesSubmittedRequest() throws Exception {
        Long id = create(userA, f1, 201);
        fill(userA, id);
        upload(userA, id, 201);
        mvc.perform(post("/api/requests/" + id + "/submit").header("X-User-Id", userA)).andExpect(status().isOk());

        mvc.perform(get("/api/applications").param("status", "SUBMITTED").header("X-User-Id", staffS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].studentId").value("6609650001"))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].studentName").value("สมชาย ใจดี"))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].documents[0].fileName").value("transcript.pdf"));
        mvc.perform(get("/api/applications/" + id + "/detail").header("X-User-Id", staffS))
                .andExpect(status().isOk())
                // U5: แสดงทุกช่องในแบบฟอร์ม รวมช่องแนบไฟล์ (ค่า = ชื่อไฟล์ที่แนบ)
                .andExpect(jsonPath("$.answers.length()").value(5))
                .andExpect(jsonPath("$.answers[1].value").value("0812345678"))
                .andExpect(jsonPath("$.answers[2].value").value("somchai@dome.tu.ac.th"))
                .andExpect(jsonPath("$.answers[4].label").value("Transcript"))
                .andExpect(jsonPath("$.answers[4].value").value("transcript.pdf"));
    }

    @Test
    @DisplayName("U3-INT-09 นศ. B มีคำร้อง SUBMITTED อยู่แล้ว สร้างใหม่ ได้ 409 คำร้องเดิมไม่เปลี่ยน ไม่มีคำร้องใหม่")
    void duplicateRequest() throws Exception {
        LocalDateTime before = reload(requestB).getUpdatedAt();

        mvc.perform(post("/api/requests").header("X-User-Id", userB)
                .contentType(MediaType.APPLICATION_JSON).content("{\"formTemplateId\":" + f1 + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_REQUEST"));

        assertThat(applications.findByStudentId(studentB)).singleElement()
                .satisfies(a -> {
                    assertThat(a.getId()).isEqualTo(requestB);
                    assertThat(a.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
                    assertThat(a.getUpdatedAt()).isEqualTo(before);
                });
    }

    @Test
    @DisplayName("U3-INT-10 นศ. C เคยถูกปฏิเสธ (REJECTED) ยื่นใหม่ได้ 201 แล้ว 200 SUBMITTED คำร้องเดิมยังเป็น REJECTED")
    void resubmitAfterRejected() throws Exception {
        Long id = create(userC, f1, 201);
        fill(userC, id);
        upload(userC, id, 201);
        mvc.perform(post("/api/requests/" + id + "/submit").header("X-User-Id", userC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        assertThat(id).isNotEqualTo(requestCRejected);
        assertThat(reload(requestCRejected).getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(applications.findByStudentId(studentC)).hasSize(2);
    }

    @Test
    @DisplayName("U3-INT-11 นศ. A กดส่งพร้อมกัน 2 request: ครั้งหนึ่ง 200 อีกครั้ง 409 มีคำร้อง SUBMITTED แค่ 1 แถว")
    void concurrentSubmit() throws Exception {
        Long id = create(userA, f1, 201);
        fill(userA, id);
        upload(userA, id, 201);

        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> submit = () -> {
            start.await();
            return mvc.perform(post("/api/requests/" + id + "/submit").header("X-User-Id", userA))
                    .andReturn().getResponse().getStatus();
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Integer>> results = new ArrayList<>(List.of(pool.submit(submit), pool.submit(submit)));
        start.countDown();
        List<Integer> codes = new ArrayList<>();
        for (Future<Integer> r : results) {
            codes.add(r.get());
        }
        pool.shutdown();

        assertThat(codes).containsExactlyInAnyOrder(200, 409);
        assertThat(applications.findByStudentId(studentA))
                .filteredOn(a -> a.getStatus() == ApplicationStatus.SUBMITTED).hasSize(1);
        verify(email, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("U3-INT-12 เวลา server 2 ต.ค. 2569 00:00 ส่งคำร้องฟอร์ม F2 (หมดเขต 1 ต.ค.) ได้ 409 DEADLINE_PASSED คำร้องยังเป็น DRAFT")
    void submitAfterF2Deadline() throws Exception {
        setServerTime(LocalDateTime.of(2026, 10, 2, 0, 0));
        Long id = create(userA, f2, 201);
        fill(userA, id);
        upload(userA, id, 201);

        mvc.perform(post("/api/requests/" + id + "/submit").header("X-User-Id", userA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEADLINE_PASSED"))
                .andExpect(jsonPath("$.message").value("เกินระยะเวลาที่กำหนด ไม่สามารถยื่นคำร้องได้"));

        assertThat(reload(id).getStatus()).isEqualTo(ApplicationStatus.DRAFT);
        assertThat(reload(id).getSubmittedAt()).isNull();
    }

    @Test
    @DisplayName("U3-INT-13 เวลา server 30 พ.ย. 2569 23:59:59 (ตรงเวลาหมดเขต F1 พอดี) ส่งได้ 200 SUBMITTED")
    void submitExactlyAtDeadline() throws Exception {
        Long id = create(userA, f1, 201);
        fill(userA, id);
        upload(userA, id, 201);
        setServerTime(LocalDateTime.of(2026, 11, 30, 23, 59, 59));

        mvc.perform(post("/api/requests/" + id + "/submit").header("X-User-Id", userA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        assertThat(reload(id).getSubmittedAt()).isEqualTo(LocalDateTime.of(2026, 11, 30, 23, 59, 59));
    }

    @Test
    @DisplayName("U3-INT-14 เวลา server 1 ธ.ค. 2569 00:00:00 (เกินหมดเขต F1 1 วินาที) ได้ 409 DEADLINE_PASSED ไม่บันทึก")
    void submitOneSecondLate() throws Exception {
        Long id = create(userA, f1, 201);
        fill(userA, id);
        upload(userA, id, 201);
        setServerTime(LocalDateTime.of(2026, 12, 1, 0, 0, 0));

        mvc.perform(post("/api/requests/" + id + "/submit").header("X-User-Id", userA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEADLINE_PASSED"));

        assertThat(reload(id).getStatus()).isEqualTo(ApplicationStatus.DRAFT);
        assertThat(reload(id).getSubmittedAt()).isNull();
    }
}
