package com.example.demo.config;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dao.ApplicationRepository;
import com.example.demo.dao.FormTemplateRepository;
import com.example.demo.dao.InternshipRepository;
import com.example.demo.dao.MatchRepository;
import com.example.demo.dao.MatchingRoundRepository;
import com.example.demo.dao.StudentRepository;
import com.example.demo.dao.UserRepository;
import com.example.demo.entity.Application;
import com.example.demo.entity.ApplicationStatus;
import com.example.demo.entity.CompanyPosition;
import com.example.demo.entity.FormTemplate;
import com.example.demo.entity.Internship;
import com.example.demo.entity.InternshipStatus;
import com.example.demo.entity.Match;
import com.example.demo.entity.MatchStatus;
import com.example.demo.entity.MatchingRound;
import com.example.demo.entity.Role;
import com.example.demo.entity.RoundStatus;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;
import com.example.demo.repository.CompanyPositionRepository;

/**
 * U8: ข้อมูลตัวอย่างสำหรับหน้าบันทึกการทำงาน, System Test / UAT และ Postman
 * ปิดไว้เป็นค่าเริ่มต้น (เหมือน U7) เปิดด้วย app.seed-u8-data=true หรือ env APP_SEED_U8_DATA=true
 *
 * การฝึกงานสร้างตามลำดับจริงของระบบ: ใบสมัคร APPROVED → Match CONFIRMED → Internship (U7) → IN_PROGRESS
 * internships.student_id อ้าง students.id และต้องมี match_id ตาม entity ของ U7
 *
 * ใช้นักศึกษาชุดของ U8 เอง ไม่แตะนักศึกษาของ story อื่น
 * (U1–U5 ใช้ 6609650001–08 · U6 ใช้ 6609650011–14 · U7 ใช้ 6609650021–27)
 *   6609650031 IN_PROGRESS_31 · กำลังฝึกงาน (IN_PROGRESS) เริ่มวันจันทร์เมื่อ 4 สัปดาห์ก่อน ฝึก 16 สัปดาห์
 *   6609650032 IN_PROGRESS_32 · กำลังฝึกงาน (IN_PROGRESS) ช่วงเดียวกัน
 *   6609650033 WAITING_33     · มีข้อมูลนักศึกษาแต่ยังไม่มีการฝึกงาน (ทดสอบกรณีบันทึกไม่ได้ / ไม่ใช่เจ้าของการฝึกงาน)
 * ทั้ง 3 คนมีอาจารย์ที่ปรึกษาเป็น advisor01 · login ด้วยรหัสผ่านทดลอง (app.auth.demo-users)
 * ใส่เฉพาะข้อมูลที่ยังไม่มี จึงรันซ้ำได้
 */
@Component
@ConditionalOnProperty(name = "app.seed-u8-data", havingValue = "true")
public class WorkLogDemoSeeder {

    /** ตำแหน่งที่ U6 ไม่ได้ใช้ในตัวอย่างการเลือกอันดับ จึงไม่กระทบผลจับคู่ของ U6 */
    private static final String POSITION_NAME = "Data Analyst";

    private final ObjectProvider<MatchingDemoSeeder> matchingDemoSeeder;
    private final UserRepository users;
    private final StudentRepository students;
    private final FormTemplateRepository forms;
    private final ApplicationRepository applications;
    private final MatchingRoundRepository rounds;
    private final MatchRepository matches;
    private final CompanyPositionRepository positions;
    private final InternshipRepository internships;
    private final Clock clock;

    public WorkLogDemoSeeder(ObjectProvider<MatchingDemoSeeder> matchingDemoSeeder, UserRepository users,
            StudentRepository students, FormTemplateRepository forms, ApplicationRepository applications,
            MatchingRoundRepository rounds, MatchRepository matches, CompanyPositionRepository positions,
            InternshipRepository internships, Clock clock) {
        this.matchingDemoSeeder = matchingDemoSeeder;
        this.users = users;
        this.students = students;
        this.forms = forms;
        this.applications = applications;
        this.rounds = rounds;
        this.matches = matches;
        this.positions = positions;
        this.internships = internships;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        // ต้องมีบริษัท / ตำแหน่งของ U4 / U6 ก่อน (seed() ของ U6 รันซ้ำได้)
        matchingDemoSeeder.ifAvailable(MatchingDemoSeeder::seed);

        Optional<User> staff = users.findByUsername("staff01");
        Optional<User> advisor = users.findByUsername("advisor01");
        Optional<FormTemplate> form = forms.findTopByOrderByVersionDesc();
        Optional<CompanyPosition> position = pickPosition();
        if (staff.isEmpty() || advisor.isEmpty() || form.isEmpty() || position.isEmpty()) {
            return;   // ยังไม่มีข้อมูลพื้นฐานของ story อื่น
        }

        LocalDate start = LocalDate.now(clock).minusWeeks(4).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = start.plusWeeks(16).minusDays(3);
        Long formId = form.get().getId();

        Student a = ensureStudent("6609650031", "IN_PROGRESS_31", "inprogress31@dome.tu.ac.th", 3.31, advisor.get());
        Student b = ensureStudent("6609650032", "IN_PROGRESS_32", "inprogress32@dome.tu.ac.th", 3.32, advisor.get());
        ensureStudent("6609650033", "WAITING_33", "waiting33@dome.tu.ac.th", 3.33, advisor.get());

        MatchingRound round = ensureRound(staff.get(), start.atStartOfDay());
        for (Student s : List.of(a, b)) {
            Application app = ensureApprovedApplication(s, formId, start.atStartOfDay().minusDays(20));
            Match match = ensureConfirmedMatch(app, position.get(), round, staff.get(), start.atStartOfDay().minusDays(10));
            ensureInProgressInternship(s, match, staff.get(), start, end);
        }
    }

    private Optional<CompanyPosition> pickPosition() {
        List<CompanyPosition> rows = positions.findAll();
        return rows.stream().filter(p -> POSITION_NAME.equals(p.getPositionName())).findFirst()
                .or(() -> rows.stream().max(Comparator.comparing(CompanyPosition::getId)));
    }

    private User ensureUser(String username, String fullName, String email, Role role) {
        return users.findByUsername(username)
                .orElseGet(() -> users.save(new User(username, fullName, email, role)));
    }

    private Student ensureStudent(String code, String fullName, String email, double gpa, User advisor) {
        User user = ensureUser(code, fullName, email, Role.STUDENT);
        Student student = students.findByUserId(user.getId())
                .orElseGet(() -> students.save(new Student(user.getId(), code, "Science and Technology",
                        "Computer Science", gpa)));
        if (!advisor.getId().equals(student.getAdvisorId())) {
            student.setAdvisorId(advisor.getId());
            student = students.save(student);
        }
        return student;
    }

    private Application ensureApprovedApplication(Student student, Long formId, LocalDateTime submittedAt) {
        List<Application> existing = applications.findByStudentIdOrderByIdDesc(student.getId());
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        Application app = new Application(student.getId(), formId, ApplicationStatus.APPROVED);
        app.setSubmittedAt(submittedAt);
        return applications.save(app);
    }

    /**
     * ใช้รอบที่ประกาศผลแล้วล่าสุดถ้ามี · ถ้าไม่มี สร้างรอบที่ประกาศผลและปิดไปแล้ว (ก่อนวันเริ่มฝึก)
     * รอบที่ปิดแล้วไม่ขวางการเปิดรอบใหม่ของ U6
     */
    private MatchingRound ensureRound(User staff, LocalDateTime internshipStart) {
        Optional<MatchingRound> existing = rounds.findFirstByStatusInOrderByRoundNoDesc(
                List.of(RoundStatus.ANNOUNCED, RoundStatus.CLOSED));
        if (existing.isPresent()) {
            return existing.get();
        }
        int next = rounds.findFirstByOrderByRoundNoDesc().map(MatchingRound::getRoundNo).orElse(0) + 1;
        MatchingRound round = new MatchingRound(next, staff.getId(), internshipStart.minusDays(21),
                internshipStart.minusDays(7));
        round.announce(internshipStart.minusDays(14));
        round.close();
        return rounds.save(round);
    }

    private Match ensureConfirmedMatch(Application app, CompanyPosition position, MatchingRound round, User staff,
            LocalDateTime matchedAt) {
        Match match = matches.findFirstByApplicationIdOrderByIdDesc(app.getId())
                .orElseGet(() -> new Match(app.getId(), position.getId(), round.getId(), 1, staff.getId(), matchedAt));
        if (match.getStatus() == MatchStatus.PROPOSED) {
            match.confirm(matchedAt.plusDays(1));
        }
        return matches.save(match);
    }

    private void ensureInProgressInternship(Student student, Match match, User staff, LocalDate start, LocalDate end) {
        Internship internship = internships.findByStudentId(student.getId())
                .orElseGet(() -> internships.save(new Internship(match.getId(), student.getId(), staff.getId(),
                        start.atStartOfDay().minusDays(7))));
        if (internship.getStatus() != InternshipStatus.NOT_STARTED) {
            return;   // มีอยู่แล้วและเริ่มฝึกแล้ว ไม่เขียนทับ
        }
        internship.updateDetails(start, end, "U8 Supervisor", "u8supervisor@example.com", "0812345678",
                staff.getId(), start.atStartOfDay().minusDays(3));
        internship.changeStatus(InternshipStatus.IN_PROGRESS, null, staff.getId(), start.atStartOfDay());
        internships.save(internship);
    }
}
