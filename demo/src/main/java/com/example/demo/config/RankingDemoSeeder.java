package com.example.demo.config;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dao.EligibilityRecordDao;
import com.example.demo.dao.StudentRepository;
import com.example.demo.dao.UserRepository;
import com.example.demo.entity.Company;
import com.example.demo.entity.CompanyStatus;
import com.example.demo.entity.EligibilityRecord;
import com.example.demo.entity.Role;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;
import com.example.demo.repository.CompanyRepository;

/**
 * U4: ข้อมูลตัวอย่างสำหรับทดสอบหน้าจัดอันดับและ Postman (แยกจาก DemoDataSeeder เพื่อไม่ชนกับงาน U อื่น)
 * ทำงานหลัง DemoDataSeeder (ตอนแอปพร้อมใช้งาน) และใส่เฉพาะข้อมูลที่ยังไม่มี จึงรันซ้ำได้
 *
 * · บริษัท 7 แห่ง: เปิดรับ 6 แห่ง ปิดรับ 1 แห่ง (PTT Digital)
 * · นักศึกษา C (6609650003) ผ่านการตรวจคุณสมบัติ ยังไม่ยื่นคำร้อง → จัดอันดับได้
 * · นักศึกษา D (6609650004) ไม่ผ่านการตรวจคุณสมบัติ → จัดอันดับไม่ได้ (403)
 * · นักศึกษา A และ B ยื่นคำร้องแล้วใน DemoDataSeeder → แก้อันดับไม่ได้ (409)
 * userId ของแต่ละคนดูได้จาก GET /api/users/{username}
 */
@Component
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class RankingDemoSeeder {

    private final CompanyRepository companies;
    private final UserRepository users;
    private final StudentRepository students;
    private final EligibilityRecordDao eligibilityRecords;

    public RankingDemoSeeder(CompanyRepository companies, UserRepository users, StudentRepository students,
            EligibilityRecordDao eligibilityRecords) {
        this.companies = companies;
        this.users = users;
        this.students = students;
        this.eligibilityRecords = eligibilityRecords;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        seedCompanies();
        Student c = ensureStudent("6609650003", "สมปอง ตั้งใจ", "sompong@dome.tu.ac.th", 3.25);
        Student d = ensureStudent("6609650004", "สมใจ ขยันยิ่ง", "somjai@dome.tu.ac.th", 2.95);
        ensureEligibility(c, true, "ผ่านเกณฑ์ (ข้อมูลตัวอย่าง U4)");
        ensureEligibility(d, false, "GPAX ต่ำกว่าเกณฑ์ (ข้อมูลตัวอย่าง U4)");
    }

    private void seedCompanies() {
        if (companies.count() > 0) {
            return;
        }
        save("SCB TechX", "กรุงเทพฯ", CompanyStatus.OPEN);
        save("Agoda Services", "กรุงเทพฯ", CompanyStatus.OPEN);
        save("KBTG", "นนทบุรี", CompanyStatus.OPEN);
        save("LINE MAN Wongnai", "กรุงเทพฯ", CompanyStatus.OPEN);
        save("Bitkub", "กรุงเทพฯ", CompanyStatus.OPEN);
        save("True Digital Group", "กรุงเทพฯ", CompanyStatus.OPEN);
        save("PTT Digital", "ระยอง", CompanyStatus.CLOSED);
    }

    private void save(String name, String address, CompanyStatus status) {
        Company company = new Company(name, status);
        company.setAddress(address);
        companies.save(company);
    }

    /** ใช้ของเดิมถ้ามีแล้ว (เช่น DemoDataSeeder ของ U3 สร้างไว้) ไม่อย่างนั้นสร้างใหม่ */
    private Student ensureStudent(String studentId, String name, String email, double gpa) {
        User user = users.findByUsername(studentId)
                .orElseGet(() -> users.save(new User(studentId, name, email, Role.STUDENT)));
        return students.findByUserId(user.getId())
                .orElseGet(() -> students.save(new Student(user.getId(), studentId,
                        "วิทยาศาสตร์และเทคโนโลยี", "วิทยาการคอมพิวเตอร์", gpa)));
    }

    private void ensureEligibility(Student student, boolean eligible, String message) {
        List<EligibilityRecord> existing = eligibilityRecords.findByStudentIdOrderByCheckedAtDesc(student.getStudentId());
        if (existing.isEmpty()) {
            eligibilityRecords.save(new EligibilityRecord(student.getStudentId(), eligible, message,
                    LocalDateTime.now().minusDays(1)));
        }
    }
}
