package com.example.demo.config;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dao.InternshipDao;
import com.example.demo.dao.UserRepository;
import com.example.demo.entity.Internship;
import com.example.demo.entity.InternshipStatus;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;

/**
 * U8: ข้อมูลตัวอย่างสำหรับหน้าบันทึกการทำงานและ Postman (แยกจาก DemoDataSeeder เหมือน RankingDemoSeeder)
 * ทำงานหลัง DemoDataSeeder และใส่เฉพาะข้อมูลที่ยังไม่มี จึงรันซ้ำได้
 *
 * ใช้นักศึกษาชุดของ U8 เอง ไม่แตะนักศึกษาของ story อื่น
 * (U1–U5 ใช้ 6609650001–08 · U6 ใช้ 6609650011–14 · U7 ใช้ 6609650021–27)
 *   6609650031 วรเมธ บันทึกดี    · กำลังฝึกงาน (IN_PROGRESS) เริ่มวันจันทร์เมื่อ 4 สัปดาห์ก่อน ฝึก 16 สัปดาห์
 *   6609650032 ปิยะนุช ใส่ใจงาน  · กำลังฝึกงาน (IN_PROGRESS) ช่วงเดียวกัน
 *   6609650033 กิตติพัฒน์ รอฝึก  · ยังไม่มีการฝึกงาน (ใช้ทดสอบกรณีบันทึกไม่ได้ / ไม่ใช่เจ้าของการฝึกงาน)
 * ทั้ง 3 คนมีอาจารย์ที่ปรึกษาเป็น advisor01 · login ด้วยรหัสผ่านทดลอง (app.auth.demo-users)
 */
@Component
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class WorkLogDemoSeeder {

    private final UserRepository users;
    private final InternshipDao internships;
    private final Clock clock;

    public WorkLogDemoSeeder(UserRepository users, InternshipDao internships, Clock clock) {
        this.users = users;
        this.internships = internships;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        User advisor = users.findByUsername("advisor01").orElse(null);
        if (advisor == null) {
            return;
        }
        LocalDate start = LocalDate.now(clock).minusWeeks(4).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        User a = ensureStudent("6609650031", "วรเมธ บันทึกดี", "worramet@dome.tu.ac.th", advisor);
        User b = ensureStudent("6609650032", "ปิยะนุช ใส่ใจงาน", "piyanuch@dome.tu.ac.th", advisor);
        ensureStudent("6609650033", "กิตติพัฒน์ รอฝึก", "kittipat@dome.tu.ac.th", advisor);
        ensureInternship(a, start);
        ensureInternship(b, start);
    }

    private User ensureStudent(String username, String fullName, String email, User advisor) {
        User student = users.findByUsername(username)
                .orElseGet(() -> users.save(new User(username, fullName, email, Role.STUDENT)));
        if (student.getAdvisorId() == null) {
            student.setAdvisorId(advisor.getId());
            student = users.save(student);
        }
        return student;
    }

    private void ensureInternship(User student, LocalDate start) {
        if (internships.findByStudentId(student.getId()).isEmpty()) {
            internships.save(new Internship(student.getId(), start, start.plusWeeks(16).minusDays(3),
                    InternshipStatus.IN_PROGRESS));
        }
    }
}
