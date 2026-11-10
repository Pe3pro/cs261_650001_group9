package com.example.demo.config;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dao.ApplicationRepository;
import com.example.demo.dao.DocumentRepository;
import com.example.demo.dao.EligibilityRecordDao;
import com.example.demo.dao.FormFieldRepository;
import com.example.demo.dao.FormResponseRepository;
import com.example.demo.dao.FormTemplateRepository;
import com.example.demo.dao.StudentRepository;
import com.example.demo.dao.UserRepository;
import com.example.demo.entity.Application;
import com.example.demo.entity.ApplicationStatus;
import com.example.demo.entity.Document;
import com.example.demo.entity.EligibilityRecord;
import com.example.demo.entity.FieldType;
import com.example.demo.entity.FormField;
import com.example.demo.entity.FormResponse;
import com.example.demo.entity.FormTemplate;
import com.example.demo.entity.Role;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;
import com.example.demo.service.FileStorage;

/**
 * ใส่ข้อมูลตัวอย่างสำหรับ demo และทดสอบด้วย Postman / หน้าเว็บ (เฉพาะตอนตาราง users ยังว่าง)
 * ปิดได้ด้วย app.seed-demo-data=false ใน application.properties
 *
 * ฐานข้อมูลใหม่จะได้ userId ตามนี้: 1 = เจ้าหน้าที่, 2 = อาจารย์, 3 = นักศึกษา A, 4 = นักศึกษา B,
 * 5 = นักศึกษา C, 6 = นักศึกษา D (U3: ยังไม่มีคำร้อง)
 * 7 = นักศึกษา E (6609650005)
 * ผลตรวจ U2: A–D ผ่านคุณสมบัติแล้ว / E ยังไม่เคยตรวจ (ยื่นคำร้องจะได้ 403 NOT_ELIGIBLE)
 */
@Component
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    private final UserRepository users;
    private final StudentRepository students;
    private final FormTemplateRepository templates;
    private final FormFieldRepository fields;
    private final ApplicationRepository applications;
    private final FormResponseRepository responses;
    private final DocumentRepository documents;
    private final FileStorage fileStorage;
    private final EligibilityRecordDao eligibility;

    public DemoDataSeeder(UserRepository users, StudentRepository students, FormTemplateRepository templates,
            FormFieldRepository fields, ApplicationRepository applications, FormResponseRepository responses,
            DocumentRepository documents, FileStorage fileStorage, EligibilityRecordDao eligibility) {
        this.users = users;
        this.students = students;
        this.templates = templates;
        this.fields = fields;
        this.applications = applications;
        this.responses = responses;
        this.documents = documents;
        this.fileStorage = fileStorage;
        this.eligibility = eligibility;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (users.count() > 0) {
            return;
        }
        User staff = users.save(new User("staff01", "S staff", "staff01@tu.ac.th", Role.STAFF));
        User advisor = users.save(new User("advisor01", "A advisor", "advisor01@tu.ac.th", Role.ADVISOR));
        User a = users.save(new User("6609650001", "A arizona", "arizona@dome.tu.ac.th", Role.STUDENT));
        User b = users.save(new User("6609650002", "B berlin", "berlin@dome.tu.ac.th", Role.STUDENT));
        Student sa = students.save(withAdvisor(new Student(a.getId(), "6609650001", "วิทยาศาสตร์และเทคโนโลยี",
                "วิทยาการคอมพิวเตอร์", 3.10), advisor));
        Student sb = students.save(withAdvisor(new Student(b.getId(), "6609650002", "วิทยาศาสตร์และเทคโนโลยี",
                "วิทยาการคอมพิวเตอร์", 3.45), advisor));

        // นักศึกษา C และ D ยังไม่มีคำร้อง · ผลตรวจคุณสมบัติของ C/D ให้ RankingDemoSeeder (U4) เป็นคนใส่
        // (C ผ่าน → จัดอันดับได้ · D ไม่ผ่าน → 403) U3 จึงไม่ใช้ C/D ยื่นคำร้อง เพื่อไม่ให้ demo ของ U4 พัง
        User c = users.save(new User("6609650003", "C center", "center@dome.tu.ac.th", Role.STUDENT));
        students.save(withAdvisor(new Student(c.getId(), "6609650003", "วิทยาศาสตร์และเทคโนโลยี", "วิทยาการคอมพิวเตอร์", 3.25),
                advisor));
        User d = users.save(new User("6609650004", "D dubai", "dubai@dome.tu.ac.th", Role.STUDENT));
        students.save(withAdvisor(new Student(d.getId(), "6609650004", "วิทยาศาสตร์และเทคโนโลยี", "วิทยาการคอมพิวเตอร์", 2.95),
                advisor));

        // U3: นักศึกษา E ยังไม่เคยตรวจคุณสมบัติ (U2) → ยื่นคำร้องได้ 403 NOT_ELIGIBLE (ยังไม่เคยตรวจ = ไม่ผ่าน เหมือน U4)
        User e = users.save(new User("6609650005", "E essen", "essen@dome.tu.ac.th", Role.STUDENT));
        students.save(withAdvisor(new Student(e.getId(), "6609650005", "วิทยาศาสตร์และเทคโนโลยี", "วิทยาการคอมพิวเตอร์", 3.00),
                advisor));
        // U3: นักศึกษาตัวอย่างของ U3 โดยเฉพาะ (ผ่านคุณสมบัติ ยังไม่มีคำร้อง) ใช้ใน Postman และ tools/u3-t17/t19/t20
        // F (userId 8) ยื่นสำเร็จ / ส่งกลับแก้ · G (userId 9) ยื่นหลังหมดเขต · H (userId 10) ยื่นใหม่หลังถูกปฏิเสธ (UAT)
        User f = users.save(new User("6609650006", "F frankfurt", "frankfurt@dome.tu.ac.th", Role.STUDENT));
        students.save(withAdvisor(new Student(f.getId(), "6609650006", "วิทยาศาสตร์และเทคโนโลยี", "วิทยาการคอมพิวเตอร์", 3.30),
                advisor));
        User g = users.save(new User("6609650007", "G geneva", "geneva@dome.tu.ac.th", Role.STUDENT));
        students.save(withAdvisor(new Student(g.getId(), "6609650007", "วิทยาศาสตร์และเทคโนโลยี", "วิทยาการคอมพิวเตอร์", 3.05),
                advisor));
        User h = users.save(new User("6609650008", "H hamburg", "hamburg@dome.tu.ac.th", Role.STUDENT));
        students.save(withAdvisor(new Student(h.getId(), "6609650008", "วิทยาศาสตร์และเทคโนโลยี", "วิทยาการคอมพิวเตอร์", 3.15),
                advisor));

        // U2 → U3: ต้องผ่านการตรวจสอบคุณสมบัติก่อนจึงยื่นคำร้องได้ (RequestService.requireEligible)
        // A, B (มีคำร้องจาก seed) และ F, G, H (นักศึกษาของ U3) ผ่านแล้ว · ไม่ใส่ให้ C/D/E
        LocalDateTime checkedAt = LocalDateTime.now().minusDays(3);
        for (String sid : List.of("6609650001", "6609650002", "6609650006", "6609650007", "6609650008")) {
            eligibility.save(new EligibilityRecord(sid, true, "ผ่านคุณสมบัติ", checkedAt));
        }

        FormTemplate form = new FormTemplate("คำร้องขอฝึกสหกิจศึกษา 2/2569", 1, staff.getId());
        form.setDescription("กรอกข้อมูลให้ครบและแนบ Transcript (PDF)");
        form.setDeadline(LocalDateTime.now().plusDays(30).withHour(23).withMinute(59).withSecond(59).withNano(0));
        form = templates.save(form);
        List<FormField> formFields = List.of(
                new FormField(form.getId(), "ชื่อ-นามสกุล", FieldType.TEXT, true, 1),
                new FormField(form.getId(), "เบอร์โทรศัพท์", FieldType.PHONE, true, 2),
                new FormField(form.getId(), "อีเมล", FieldType.EMAIL, true, 3),
                new FormField(form.getId(), "ที่อยู่ปัจจุบัน", FieldType.TEXTAREA, true, 4),
                new FormField(form.getId(), "Transcript", FieldType.FILE, true, 5));
        formFields = fields.saveAll(formFields);
        form.publish(LocalDateTime.now());
        templates.save(form);

        seedApplication(sa, form, formFields, "A arizona", "0812345678", "arizona@dome.tu.ac.th",
                "99 ม.ธรรมศาสตร์ ศูนย์รังสิต", LocalDateTime.now().minusDays(2));
        seedApplication(sb, form, formFields, "B berlin", "0898765432", "berlin@dome.tu.ac.th",
                "1 ถ.พหลโยธิน ปทุมธานี", LocalDateTime.now().minusDays(1));
    }

    private void seedApplication(Student student, FormTemplate form, List<FormField> formFields, String name,
            String tel, String email, String address, LocalDateTime submittedAt) {
        Application app = new Application(student.getId(), form.getId(), ApplicationStatus.DRAFT);
        app.submit(submittedAt);
        app = applications.save(app);
        String[] values = { name, tel, email, address, "transcript.pdf" };
        for (int i = 0; i < values.length; i++) {
            responses.save(new FormResponse(app.getId(), formFields.get(i).getId(), values[i]));
        }
        // U3: เก็บไฟล์ PDF ตัวอย่างลงที่เก็บไฟล์จริง เพื่อให้เจ้าหน้าที่กด "ดูไฟล์" ได้
        byte[] pdf = ("%PDF-1.4\n% demo transcript " + student.getStudentId() + "\n%%EOF\n")
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        String key = fileStorage.store("transcript.pdf", pdf);
        documents.save(new Document(app.getId(), "transcript.pdf", key, (long) pdf.length));
    }

    /** U5: นักศึกษาทดลองทุกคนอยู่ในความดูแลของ advisor01 */
    private static Student withAdvisor(Student student, User advisor) {
        student.setAdvisorId(advisor.getId());
        return student;
    }
}
