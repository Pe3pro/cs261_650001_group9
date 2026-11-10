package com.example.demo.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.example.demo.entity.Role;
import com.example.demo.entity.User;

/** U5:T9 Unit test ส่วน DAO: UserRepository (ใช้หาอาจารย์ที่ต้องได้รับแจ้งเตือน) */
@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository repository;

    @BeforeEach
    void setUp() {
        repository.save(new User("staff01", "S staff", "staff01@tu.ac.th", Role.STAFF));
        repository.save(new User("advisor01", "A advisor", "advisor01@tu.ac.th", Role.ADVISOR));
        repository.save(new User("advisor02", "อ.มานี", "advisor02@tu.ac.th", Role.ADVISOR));
        User retired = new User("advisor03", "อ.เกษียณ", "advisor03@tu.ac.th", Role.ADVISOR);
        retired.setActive(false);
        repository.save(retired);
    }

    @Test
    @DisplayName("DAO-U01 หาอาจารย์ที่ยังใช้งานอยู่ ไม่รวมบัญชีที่ปิดแล้วและบทบาทอื่น")
    void activeAdvisors() {
        assertThat(repository.findByRoleAndActiveTrue(Role.ADVISOR))
                .extracting(User::getUsername).containsExactlyInAnyOrder("advisor01", "advisor02");
    }

    @Test
    @DisplayName("DAO-U02 ค้นผู้ใช้จาก username")
    void findByUsername() {
        assertThat(repository.findByUsername("staff01")).get()
                .extracting(User::getRole).isEqualTo(Role.STAFF);
        assertThat(repository.findByUsername("nobody")).isEmpty();
    }

    @Test
    @DisplayName("DAO-U03 username ซ้ำ ต้องถูกปฏิเสธ")
    void duplicateUsernameFails() {
        assertThatThrownBy(() -> repository.saveAndFlush(
                new User("staff01", "ซ้ำ", "dup@tu.ac.th", Role.STAFF)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
