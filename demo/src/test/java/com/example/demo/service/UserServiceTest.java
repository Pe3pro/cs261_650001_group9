package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.demo.dao.UserRepository;
import com.example.demo.dto.UserResponse;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;

/** U5: หาผู้ใช้จาก username (ใช้เชื่อมกับหน้า login ของ U1) */
class UserServiceTest {

    private final UserRepository repo = mock(UserRepository.class);
    private final UserService service = new UserService(repo);

    @Test
    @DisplayName("SV-U01 username มีในระบบ คืน role และชื่อ (ตัดช่องว่างหน้า-หลังให้)")
    void found() {
        when(repo.findByUsername("advisor01")).thenReturn(Optional.of(new User("advisor01", "A advisor", "a@tu.ac.th", Role.ADVISOR)));

        UserResponse r = service.findByUsername("  advisor01 ");

        assertThat(r.role()).isEqualTo(Role.ADVISOR);
        assertThat(r.name()).isEqualTo("A advisor");
    }

    @Test
    @DisplayName("SV-U02 username ไม่มีในระบบ หรือถูกปิดใช้งาน ได้ 404")
    void notFoundOrInactive() {
        User off = new User("staff09", "ปิดแล้ว", "x@tu.ac.th", Role.STAFF);
        off.setActive(false);
        when(repo.findByUsername("staff09")).thenReturn(Optional.of(off));
        when(repo.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByUsername("nobody")).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(404);
        assertThatThrownBy(() -> service.findByUsername("staff09")).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("SV-U03 username ว่าง ได้ 400")
    void blank() {
        assertThatThrownBy(() -> service.findByUsername("  ")).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(400);
    }
}
