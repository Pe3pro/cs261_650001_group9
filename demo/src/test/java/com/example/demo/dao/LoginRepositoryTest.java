package com.example.demo.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.example.demo.entity.Role;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;

/** U1:T9 Unit test ส่วน DAO ที่ใช้ตอน login: UserRepository + StudentRepository */
@DataJpaTest
class LoginRepositoryTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private StudentRepository students;

    @Test
    @DisplayName("DAO-L01 บันทึกผู้ใช้ใหม่จากการ login: ได้ id, active = true และ createdAt")
    void saveNewUser() {
        User saved = users.saveAndFlush(new User("6609650099", "ทดสอบ ระบบ", "6609650099@dome.tu.ac.th", Role.STUDENT));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getActive()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(users.findByUsername("6609650099")).get().extracting(User::getRole).isEqualTo(Role.STUDENT);
    }

    @Test
    @DisplayName("DAO-L02 อัปเดตชื่อ/อีเมลของผู้ใช้เดิม: id และ role ไม่เปลี่ยน")
    void updateExistingUser() {
        User advisor = users.saveAndFlush(new User("advisor01", "ชื่อเก่า", "old@tu.ac.th", Role.ADVISOR));

        User found = users.findByUsername("advisor01").orElseThrow();
        found.setFullName("A advisor");
        found.setEmail("wichai@tu.ac.th");
        users.saveAndFlush(found);

        User again = users.findByUsername("advisor01").orElseThrow();
        assertThat(again.getId()).isEqualTo(advisor.getId());
        assertThat(again.getRole()).isEqualTo(Role.ADVISOR);
        assertThat(again.getFullName()).isEqualTo("A advisor");
        assertThat(again.getEmail()).isEqualTo("wichai@tu.ac.th");
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("DAO-L03 Student ผูกกับ User ด้วย userId ค้นได้ทั้งจาก userId และรหัสนักศึกษา")
    void studentLinkedToUser() {
        User u = users.saveAndFlush(new User("6609650099", "ทดสอบ ระบบ", null, Role.STUDENT));
        students.saveAndFlush(new Student(u.getId(), "6609650099", "วิทยาศาสตร์และเทคโนโลยี", "วิทยาการคอมพิวเตอร์", null));

        assertThat(students.findByUserId(u.getId())).get().extracting(Student::getStudentId).isEqualTo("6609650099");
        assertThat(students.findByStudentId("6609650099")).get().extracting(Student::getUserId).isEqualTo(u.getId());
        assertThat(students.findByUserId(999L)).isEmpty();
    }

    @Test
    @DisplayName("DAO-L04 สร้าง Student ซ้ำให้ User เดิม ต้องถูกปฏิเสธ (user_id unique)")
    void duplicateStudentRejected() {
        User u = users.saveAndFlush(new User("6609650099", "ทดสอบ ระบบ", null, Role.STUDENT));
        students.saveAndFlush(new Student(u.getId(), "6609650099", null, null, null));

        assertThatThrownBy(() -> students.saveAndFlush(new Student(u.getId(), "6609650098", null, null, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("DAO-L05 ผู้ใช้ไม่ต้องมีรหัสผ่านในฐานข้อมูล (password_hash ว่างได้)")
    void noPasswordStored() {
        User saved = users.saveAndFlush(new User("somsri.t", "S staff", null, Role.STAFF));

        assertThat(saved.getPasswordHash()).isNull();
    }
}