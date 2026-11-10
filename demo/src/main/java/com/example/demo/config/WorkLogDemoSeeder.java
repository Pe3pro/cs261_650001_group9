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
 * · นักศึกษาทุกคนที่ยังไม่มีอาจารย์ที่ปรึกษา → ผูกกับ advisor01
 * · นักศึกษา A (6609650001) และ B (6609650002) กำลังฝึกงาน (IN_PROGRESS)
 *   เริ่มวันจันทร์เมื่อ 4 สัปดาห์ก่อน ฝึก 16 สัปดาห์
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
        for (User student : users.findByRoleAndActiveTrue(Role.STUDENT)) {
            if (student.getAdvisorId() == null) {
                student.setAdvisorId(advisor.getId());
                users.save(student);
            }
        }
        LocalDate start = LocalDate.now(clock).minusWeeks(4).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        ensureInternship("6609650001", start);
        ensureInternship("6609650002", start);
    }

    private void ensureInternship(String username, LocalDate start) {
        users.findByUsername(username).ifPresent(student -> {
            if (internships.findFirstByStudentIdOrderByStartDateDescIdDesc(student.getId()).isEmpty()) {
                internships.save(new Internship(student.getId(), start, start.plusWeeks(16).minusDays(3),
                        InternshipStatus.IN_PROGRESS));
            }
        });
    }
}
