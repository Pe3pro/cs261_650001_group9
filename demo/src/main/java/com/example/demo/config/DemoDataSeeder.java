package com.example.demo.config;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dao.ApplicationRepository;
import com.example.demo.dao.DocumentRepository;
import com.example.demo.dao.FormFieldRepository;
import com.example.demo.dao.FormResponseRepository;
import com.example.demo.dao.FormTemplateRepository;
import com.example.demo.dao.StudentRepository;
import com.example.demo.dao.UserRepository;
import com.example.demo.entity.Application;
import com.example.demo.entity.ApplicationStatus;
import com.example.demo.entity.Document;
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

    public DemoDataSeeder(UserRepository users, StudentRepository students, FormTemplateRepository templates,
            FormFieldRepository fields, ApplicationRepository applications, FormResponseRepository responses,
            DocumentRepository documents, FileStorage fileStorage) {
        this.users = users;
        this.students = students;
        this.templates = templates;
        this.fields = fields;
        this.applications = applications;
        this.responses = responses;
        this.documents = documents;
        this.fileStorage = fileStorage;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (users.count() > 0) {
            return;
        }
        User staff = users.save(new User("staff01", "สมศรี ทำงานดี", "staff01@tu.ac.th", Role.STAFF));
        User advisor = users.save(new User("advisor01", "อ.ดร.วิชัย สอนดี", "advisor01@tu.ac.th", Role.ADVISOR));
        User a = users.save(new User("6609650001", "สมชาย ใจดี", "somchai@dome.tu.ac.th", Role.STUDENT));
        User b = users.save(new User("6609650002", "สมหญิง รักเรียน", "somying@dome.tu.ac.th", Role.STUDENT));
        Student sa = students.save(withAdvisor(new Student(a.getId(), "6609650001", "วิทยาศาสตร์และเทคโนโลยี",
                "วิทยาการคอมพิวเตอร์", 3.10), advisor));
        Student sb = students.save(withAdvisor(new Student(b.getId(), "6609650002", "วิทยาศาสตร์และเทคโนโลยี",
                "วิทยาการคอมพิวเตอร์", 3.45), advisor));

        // U3: นักศึกษา C และ D ยังไม่มีคำร้อง ใช้ทดลองยื่นคำร้องใหม่ (login 6609650003 / 6609650004 ด้วยรหัสผ่านทดลอง)
        User c = users.save(new User("6609650003", "สมปอง ตั้งใจ", "sompong@dome.tu.ac.th", Role.STUDENT));
        students.save(withAdvisor(new Student(c.getId(), "6609650003", "วิทยาศาสตร์และเทคโนโลยี", "วิทยาการคอมพิวเตอร์", 3.25),
                advisor));
        User d = users.save(new User("6609650004", "สมใจ ขยันยิ่ง", "somjai@dome.tu.ac.th", Role.STUDENT));
        students.save(withAdvisor(new Student(d.getId(), "6609650004", "วิทยาศาสตร์และเทคโนโลยี", "วิทยาการคอมพิวเตอร์", 2.95),
                advisor));
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

        seedApplication(sa, form, formFields, "สมชาย ใจดี", "0812345678", "somchai@dome.tu.ac.th",
                "99 ม.ธรรมศาสตร์ ศูนย์รังสิต", LocalDateTime.now().minusDays(2));
        seedApplication(sb, form, formFields, "สมหญิง รักเรียน", "0898765432", "somying@dome.tu.ac.th",
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
