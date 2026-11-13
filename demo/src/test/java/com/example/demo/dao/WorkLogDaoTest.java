package com.example.demo.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.example.demo.entity.Role;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;
import com.example.demo.entity.WorkLog;
import com.example.demo.entity.WorkLogAttachment;
import com.example.demo.entity.WorkLogStatus;

/**
 * U8:T4 Unit test ส่วน DAO: WorkLogDao + WorkLogAttachmentDao
 * ใช้ @DataJpaTest (H2 โหมด MySQL ในหน่วยความจำ สร้างตารางจาก Entity จริง) ทุก test rollback อัตโนมัติ
 *
 * Seed: internship 1 (นักศึกษา A) มีบันทึก 2026-11-09 SUBMITTED 3 ชม., 2026-11-10 DRAFT 4 ชม.,
 * 2026-11-11 APPROVED 8 ชม. · internship 2 (นักศึกษา B) มีบันทึก 2026-11-09 SUBMITTED
 * อาจารย์ X ดูแล A · อาจารย์ Y ดูแล B
 */
@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:u8dao;MODE=MySQL;DB_CLOSE_DELAY=-1")
class WorkLogDaoTest {

    private static final Long INTERNSHIP_A = 1L;
    private static final Long INTERNSHIP_B = 2L;

    @Autowired
    private WorkLogDao dao;

    @Autowired
    private WorkLogAttachmentDao attachmentDao;

    @Autowired
    private UserRepository users;

    @Autowired
    private StudentRepository students;

    private User advisorX;
    private User advisorY;
    private User studentA;
    private WorkLog submittedA;
    private WorkLog draftA;
    private WorkLog submittedB;

    @BeforeEach
    void setUp() {
        advisorX = users.save(new User("advisor01", "A advisor", "advisor01@tu.ac.th", Role.ADVISOR));
        advisorY = users.save(new User("advisor02", "B advisor", "advisor02@tu.ac.th", Role.ADVISOR));
        studentA = student("6609650031", advisorX);
        User studentB = student("6609650032", advisorY);

        submittedA = save(INTERNSHIP_A, studentA, LocalDate.of(2026, 11, 9), "09:00", "12:00", "3.00",
                WorkLogStatus.SUBMITTED, LocalDateTime.of(2026, 11, 9, 18, 0));
        draftA = save(INTERNSHIP_A, studentA, LocalDate.of(2026, 11, 10), "13:00", "17:00", "4.00",
                WorkLogStatus.DRAFT, null);
        save(INTERNSHIP_A, studentA, LocalDate.of(2026, 11, 11), "09:00", "17:00", "8.00",
                WorkLogStatus.APPROVED, LocalDateTime.of(2026, 11, 11, 18, 0));
        submittedB = save(INTERNSHIP_B, studentB, LocalDate.of(2026, 11, 9), "09:00", "18:00", "9.00",
                WorkLogStatus.SUBMITTED, LocalDateTime.of(2026, 11, 9, 8, 0));
    }

    private User student(String username, User advisor) {
        User s = new User(username, "IN_PROGRESS_" + username.substring(8), username + "@dome.tu.ac.th", Role.STUDENT);
        User saved = users.save(s);
        // อาจารย์ที่ปรึกษาเก็บใน students.advisor_id (ไม่ใช่ใน users)
        Student st = new Student(saved.getId(), username, "Science and Technology", "Computer Science", 3.0);
        st.setAdvisorId(advisor.getId());
        students.save(st);
        return saved;
    }

    private WorkLog save(Long internshipId, User student, LocalDate date, String start, String end, String hours,
            WorkLogStatus status, LocalDateTime submittedAt) {
        return dao.saveAndFlush(log(internshipId, student, date, start, end, hours, status, submittedAt));
    }

    private static WorkLog log(Long internshipId, User student, LocalDate date, String start, String end,
            String hours, WorkLogStatus status, LocalDateTime submittedAt) {
        WorkLog w = new WorkLog(internshipId, student.getId(), date);
        w.setStartTime(LocalTime.parse(start));
        w.setEndTime(LocalTime.parse(end));
        w.setHours(new BigDecimal(hours));
        w.setActivity("ประชุมทีม");
        w.setLearning("การวางแผนงาน");
        w.setStatus(status);
        w.setSubmittedAt(submittedAt);
        return w;
    }

    @Test
    @DisplayName("UT-DAO-01 save ข้อมูลถูกต้อง ได้ id และจำนวนแถวเพิ่ม 1")
    void insert() {
        long before = dao.count();
        WorkLog saved = save(INTERNSHIP_A, studentA, LocalDate.of(2026, 11, 12), "09:00", "12:00", "3.00",
                WorkLogStatus.DRAFT, null);

        assertThat(saved.getId()).isPositive();
        assertThat(dao.count()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("UT-DAO-02 save แล้วอ่านกลับ ทุกฟิลด์ตรง created_at / updated_at ไม่เป็น null")
    void insertThenRead() {
        WorkLog found = dao.findById(submittedA.getId()).orElseThrow();

        assertThat(found.getLogDate()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(found.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(found.getEndTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(found.getHours()).isEqualByComparingTo("3");
        assertThat(found.getActivity()).isEqualTo("ประชุมทีม");
        assertThat(found.getStatus()).isEqualTo(WorkLogStatus.SUBMITTED);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("UT-DAO-03 ฟิลด์ NOT NULL (activity) เป็น null โยน exception")
    void notNull() {
        WorkLog w = log(INTERNSHIP_A, studentA, LocalDate.of(2026, 11, 12), "09:00", "12:00", "3.00",
                WorkLogStatus.DRAFT, null);
        w.setActivity(null);

        assertThatThrownBy(() -> dao.saveAndFlush(w)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("UT-DAO-04 บันทึกวันเดียวกันของการฝึกงานเดียวกันซ้ำ ติด unique (internship_id, log_date)")
    void uniqueDate() {
        WorkLog dup = log(INTERNSHIP_A, studentA, LocalDate.of(2026, 11, 9), "13:00", "14:00", "1.00",
                WorkLogStatus.DRAFT, null);

        assertThatThrownBy(() -> dao.saveAndFlush(dup)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("UT-DAO-05 findById ที่ไม่มี คืน Optional.empty ไม่โยน exception")
    void findMissing() {
        assertThat(dao.findById(9999L)).isEmpty();
    }

    @Test
    @DisplayName("UT-DAO-06 findByInternshipIdAndFilter เรียงตามวันที่ กรองช่วงวันและสถานะได้ ไม่ปนการฝึกงานอื่น")
    void filter() {
        assertThat(dao.findByInternshipIdAndFilter(INTERNSHIP_A, null, null, null))
                .extracting(WorkLog::getLogDate)
                .containsExactly(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11));
        assertThat(dao.findByInternshipIdAndFilter(INTERNSHIP_A, LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 11), null)).hasSize(2);
        assertThat(dao.findByInternshipIdAndFilter(INTERNSHIP_A, null, null, WorkLogStatus.DRAFT))
                .extracting(WorkLog::getId).containsExactly(draftA.getId());
        assertThat(dao.findByInternshipIdAndFilter(INTERNSHIP_A, LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 2), null)).isEmpty();
    }

    @Test
    @DisplayName("UT-DAO-07 existsByInternshipIdAndLogDate มีบันทึกวันนั้นแล้ว = true")
    void existsByDate() {
        assertThat(dao.existsByInternshipIdAndLogDate(INTERNSHIP_A, LocalDate.of(2026, 11, 9))).isTrue();
        assertThat(dao.existsByInternshipIdAndLogDate(INTERNSHIP_A, LocalDate.of(2026, 11, 12))).isFalse();
    }

    @Test
    @DisplayName("UT-DAO-08 findSubmittedByAdvisorId ได้เฉพาะ SUBMITTED ของนักศึกษาในที่ปรึกษา")
    void pendingForAdvisor() {
        assertThat(dao.findSubmittedByAdvisorId(advisorX.getId()))
                .extracting(WorkLog::getId).containsExactly(submittedA.getId());
        assertThat(dao.findSubmittedByAdvisorId(advisorY.getId()))
                .extracting(WorkLog::getId).containsExactly(submittedB.getId());
        assertThat(dao.findSubmittedByAdvisorId(9999L)).isEmpty();
    }

    @Test
    @DisplayName("UT-DAO-09 sumHoursByInternshipId ไม่นับ DRAFT · ไม่มีบันทึก = 0")
    void sumHours() {
        assertThat(dao.sumHoursByInternshipId(INTERNSHIP_A)).isEqualByComparingTo("11");
        assertThat(dao.sumHoursByInternshipId(99L)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("UT-DAO-10 findLogDatesByInternshipId และ countByInternshipIdGroupByStatus")
    void datesAndCounts() {
        assertThat(dao.findLogDatesByInternshipId(INTERNSHIP_A)).containsExactly(LocalDate.of(2026, 11, 9),
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11));

        Map<WorkLogStatus, Long> counts = dao.countByInternshipIdGroupByStatus(INTERNSHIP_A).stream()
                .collect(Collectors.toMap(WorkLogDao.StatusCount::getStatus, WorkLogDao.StatusCount::getCount));
        assertThat(counts).containsEntry(WorkLogStatus.SUBMITTED, 1L).containsEntry(WorkLogStatus.DRAFT, 1L)
                .containsEntry(WorkLogStatus.APPROVED, 1L).doesNotContainKey(WorkLogStatus.NEED_REVISION);
    }

    @Test
    @DisplayName("UT-DAO-11 update ข้อมูล findById ได้ค่าใหม่ · existsByInternshipIdAndStatus ตามสถานะล่าสุด")
    void update() {
        assertThat(dao.existsByInternshipIdAndStatus(INTERNSHIP_A, WorkLogStatus.SUBMITTED)).isTrue();

        WorkLog w = dao.findById(submittedA.getId()).orElseThrow();
        w.setEndTime(LocalTime.of(13, 0));
        w.setHours(new BigDecimal("4.00"));
        w.setStatus(WorkLogStatus.APPROVED);
        dao.saveAndFlush(w);

        WorkLog found = dao.findById(submittedA.getId()).orElseThrow();
        assertThat(found.getEndTime()).isEqualTo(LocalTime.of(13, 0));
        assertThat(found.getHours()).isEqualByComparingTo("4");
        assertThat(dao.existsByInternshipIdAndStatus(INTERNSHIP_A, WorkLogStatus.SUBMITTED)).isFalse();
        assertThat(dao.findAllByIdIn(List.of(submittedA.getId(), submittedB.getId(), 9999L))).hasSize(2);
    }

    @Test
    @DisplayName("UT-DAO-12 delete บันทึก findById ได้ empty รายการอื่นยังอยู่ครบ")
    void delete() {
        long before = dao.count();
        dao.delete(draftA);
        dao.flush();

        assertThat(dao.findById(draftA.getId())).isEmpty();
        assertThat(dao.count()).isEqualTo(before - 1);
        assertThat(dao.findById(submittedA.getId())).isPresent();
    }

    @Test
    @DisplayName("UT-DAO-13 ไฟล์แนบ: findByWorkLogId / findByIdAndWorkLogId / countByWorkLogId")
    void attachments() {
        WorkLogAttachment a = attachmentDao.save(new WorkLogAttachment(draftA.getId(), "a.pdf", "work-logs/1/a.pdf",
                "application/pdf", 100));
        attachmentDao.save(new WorkLogAttachment(draftA.getId(), "b.png", "work-logs/1/b.png", "image/png", 200));
        attachmentDao.save(new WorkLogAttachment(submittedA.getId(), "c.jpg", "work-logs/2/c.jpg", "image/jpeg", 300));

        assertThat(attachmentDao.findByWorkLogId(draftA.getId())).extracting(WorkLogAttachment::getFileName)
                .containsExactly("a.pdf", "b.png");
        assertThat(attachmentDao.countByWorkLogId(draftA.getId())).isEqualTo(2);
        assertThat(attachmentDao.findByIdAndWorkLogId(a.getId(), draftA.getId())).isPresent();
        assertThat(attachmentDao.findByIdAndWorkLogId(a.getId(), submittedA.getId())).isEmpty();
        assertThat(a.getUploadedAt()).isNotNull();
    }
}
