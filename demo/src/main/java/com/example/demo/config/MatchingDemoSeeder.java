package com.example.demo.config;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dao.ApplicationRepository;
import com.example.demo.dao.CompanyPreferenceRepository;
import com.example.demo.dao.FormTemplateRepository;
import com.example.demo.dao.StudentRepository;
import com.example.demo.dao.UserRepository;
import com.example.demo.entity.Application;
import com.example.demo.entity.ApplicationStatus;
import com.example.demo.entity.Company;
import com.example.demo.entity.CompanyPosition;
import com.example.demo.entity.CompanyPreference;
import com.example.demo.entity.FormTemplate;
import com.example.demo.entity.PositionStatus;
import com.example.demo.entity.Role;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;
import com.example.demo.repository.CompanyPositionRepository;
import com.example.demo.repository.CompanyRepository;

/**
 * U6: ข้อมูลตัวอย่างสำหรับทดสอบการจับคู่ (แยกนักศึกษาจาก U3 / U4 / U5 เพื่อไม่ให้ผลของ Story อื่นมากระทบ)
 * ทำงานหลัง RankingDemoSeeder (เรียก seed() ของ U4 ก่อน ซึ่งรันซ้ำได้) และใส่เฉพาะข้อมูลที่ยังไม่มี
 *
 * · ตำแหน่งงานของบริษัทที่ U4 seed ไว้ (บริษัทที่ยังไม่มีตำแหน่ง)
 * · นักศึกษา U6 4 คน ใบสมัคร APPROVED แล้ว (login ด้วยรหัสผ่านทดลอง)
 *   6609650011 ยังไม่เลือกอันดับ (ใช้ทดลองหน้าเลือกอันดับ)
 *   6609650012–6609650014 เลือกอันดับไว้แล้ว (ใช้ทดลองจับคู่อัตโนมัติ / ประกาศผล / ยืนยัน–สละสิทธิ์)
 */
@Component
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class MatchingDemoSeeder {

    private final RankingDemoSeeder rankingDemoSeeder;
    private final CompanyRepository companies;
    private final CompanyPositionRepository positions;
    private final UserRepository users;
    private final StudentRepository students;
    private final ApplicationRepository applications;
    private final FormTemplateRepository forms;
    private final CompanyPreferenceRepository preferences;

    public MatchingDemoSeeder(RankingDemoSeeder rankingDemoSeeder, CompanyRepository companies,
            CompanyPositionRepository positions, UserRepository users, StudentRepository students,
            ApplicationRepository applications, FormTemplateRepository forms, CompanyPreferenceRepository preferences) {
        this.rankingDemoSeeder = rankingDemoSeeder;
        this.companies = companies;
        this.positions = positions;
        this.users = users;
        this.students = students;
        this.applications = applications;
        this.forms = forms;
        this.preferences = preferences;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        rankingDemoSeeder.seed();   // ต้องมีบริษัทของ U4 ก่อน
        seedPositions();
        Optional<FormTemplate> form = forms.findTopByOrderByVersionDesc();
        if (form.isEmpty()) {
            return;   // ไม่มีแบบฟอร์ม (ยังไม่ได้ seed ของ U3/U5) จึงสร้างใบสมัครไม่ได้
        }
        Long formId = form.get().getId();
        ensureApprovedApplication("6609650011", "ณัฐพล เลือกงาน", "nattapon@dome.tu.ac.th", 3.40, formId, 5);
        Application b = ensureApprovedApplication("6609650012", "พิมพ์ชนก ตั้งใจ", "pimchanok@dome.tu.ac.th", 3.75, formId, 4);
        Application c = ensureApprovedApplication("6609650013", "ธนกร ขยันดี", "thanakorn@dome.tu.ac.th", 3.20, formId, 3);
        Application d = ensureApprovedApplication("6609650014", "กมลชนก ใฝ่รู้", "kamonchanok@dome.tu.ac.th", 2.90, formId, 2);
        // ตำแหน่งที่มีโควตา 1 ที่ (Backend ของ SCB) ถูกเลือกเป็นอันดับ 1 โดย 2 คน เพื่อให้เห็นการเลื่อนไปอันดับถัดไป
        ensurePreferences(b, "SCB TechX:Backend Developer", "Agoda Services:Software Engineer", "KBTG:QA Engineer");
        ensurePreferences(c, "SCB TechX:Backend Developer", "LINE MAN Wongnai:Frontend Developer");
        ensurePreferences(d, "Bitkub:Blockchain Developer Intern", "True Digital Group:Data Engineer");
    }

    private void seedPositions() {
        position("SCB TechX", "Backend Developer", "พัฒนา REST API ด้วย Java / Spring Boot", 1);
        position("SCB TechX", "Data Analyst", "วิเคราะห์ข้อมูลลูกค้าด้วย SQL และ Python", 2);
        position("Agoda Services", "Software Engineer", "พัฒนาระบบจองที่พัก", 2);
        position("KBTG", "QA Engineer", "ทดสอบระบบ Mobile Banking", 1);
        position("LINE MAN Wongnai", "Frontend Developer", "พัฒนาเว็บด้วย React", 1);
        position("Bitkub", "Blockchain Developer Intern", "พัฒนา Smart Contract", 1);
        position("True Digital Group", "Data Engineer", "ดูแล Data Pipeline", 2);
    }

    /** เพิ่มตำแหน่งเฉพาะบริษัทที่มีอยู่และยังไม่มีตำแหน่งชื่อนี้ */
    private void position(String companyName, String positionName, String description, int quota) {
        companyByName(companyName).ifPresent(company -> {
            boolean exists = positions.findByCompanyId(company.getId()).stream()
                    .anyMatch(p -> p.getPositionName().equals(positionName));
            if (!exists) {
                CompanyPosition p = new CompanyPosition(company.getId(), positionName, quota, PositionStatus.OPEN);
                p.setDescription(description);
                positions.save(p);
            }
        });
    }

    private Optional<Company> companyByName(String name) {
        return companies.findByCompanyNameContainingIgnoreCase(name).stream()
                .filter(c -> c.getCompanyName().equals(name)).findFirst();
    }

    /** นักศึกษาและใบสมัคร APPROVED (ถ้ามีใบสมัครแล้วใช้ของเดิม) */
    private Application ensureApprovedApplication(String studentId, String name, String email, double gpa,
            Long formId, int daysAgo) {
        User user = users.findByUsername(studentId)
                .orElseGet(() -> users.save(new User(studentId, name, email, Role.STUDENT)));
        Student student = students.findByUserId(user.getId())
                .orElseGet(() -> students.save(new Student(user.getId(), studentId,
                        "วิทยาศาสตร์และเทคโนโลยี", "วิทยาการคอมพิวเตอร์", gpa)));
        List<Application> existing = applications.findByStudentIdOrderByIdDesc(student.getId());
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        Application app = new Application(student.getId(), formId, ApplicationStatus.APPROVED);
        app.setSubmittedAt(LocalDateTime.now().minusDays(daysAgo));
        return applications.save(app);
    }

    /** อันดับตัวอย่าง "บริษัท:ตำแหน่ง" (ใส่เฉพาะใบสมัครที่ยังไม่เคยเลือก) */
    private void ensurePreferences(Application app, String... picks) {
        if (app == null || !preferences.findByApplicationIdOrderByRankNoAsc(app.getId()).isEmpty()) {
            return;
        }
        List<CompanyPreference> rows = new ArrayList<>();
        for (String pick : picks) {
            String[] parts = pick.split(":", 2);
            companyByName(parts[0]).flatMap(c -> positions.findByCompanyId(c.getId()).stream()
                    .filter(p -> p.getPositionName().equals(parts[1])).findFirst())
                    .ifPresent(p -> rows.add(new CompanyPreference(app.getId(), p.getId(), rows.size() + 1,
                            LocalDateTime.now())));
        }
        preferences.saveAll(rows);
    }
}
