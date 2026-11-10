package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.dto.CompanyResponse;
import com.example.demo.dto.CompanyStatusRequest;
import com.example.demo.entity.Company;
import com.example.demo.entity.CompanyStatus;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.repository.CompanyRepository;

/** U4:T8 Unit test logic รายชื่อบริษัทและการเปิด–ปิดรับ (CompanyService) */
class CompanyServiceTest {

    private CompanyRepository companies;
    private AccessGuard guard;
    private CompanyService service;

    @BeforeEach
    void setUp() {
        companies = mock(CompanyRepository.class);
        guard = mock(AccessGuard.class);
        service = new CompanyService(companies, guard);
        when(guard.require(1L, Role.STAFF)).thenReturn(new User("staff01", "สมศรี ทำงานดี", "staff01@tu.ac.th", Role.STAFF));
    }

    private static Company company(Long id, String name, CompanyStatus status) {
        Company c = new Company(name, status);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    @Test
    @DisplayName("U4-SVC-01 ดึงบริษัทที่เปิดรับ ไม่รวมบริษัทที่ปิดรับ และเรียงตาม id")
    void openCompaniesOnly() {
        when(companies.findByStatus(CompanyStatus.OPEN)).thenReturn(List.of(
                company(2L, "Agoda Services", CompanyStatus.OPEN),
                company(1L, "SCB TechX", CompanyStatus.OPEN)));

        List<CompanyResponse> result = service.getOpenCompanies(3L);

        assertThat(result).extracting(CompanyResponse::name).containsExactly("SCB TechX", "Agoda Services");
        assertThat(result).allSatisfy(c -> assertThat(c.status()).isEqualTo(CompanyStatus.OPEN));
        verify(guard).require(3L);
        verify(companies, never()).findByStatus(CompanyStatus.CLOSED);
        verify(companies, never()).findAll();
    }

    @Test
    @DisplayName("U4-SVC-12 เจ้าหน้าที่ปิดรับบริษัท สถานะเปลี่ยนเป็น CLOSED")
    void staffClosesCompany() {
        Company scb = company(1L, "SCB TechX", CompanyStatus.OPEN);
        when(companies.findById(1L)).thenReturn(Optional.of(scb));
        when(companies.save(scb)).thenReturn(scb);

        CompanyResponse result = service.updateStatus(1L, new CompanyStatusRequest(CompanyStatus.CLOSED), 1L);

        assertThat(result.status()).isEqualTo(CompanyStatus.CLOSED);
        assertThat(scb.getStatus()).isEqualTo(CompanyStatus.CLOSED);
    }

    @Test
    @DisplayName("U4-SVC-12 นักศึกษาเปิด–ปิดรับบริษัทไม่ได้ (403) และไม่บันทึก")
    void studentCannotUpdate() {
        when(guard.require(3L, Role.STAFF)).thenThrow(ApiException.forbidden());

        assertThatThrownBy(() -> service.updateStatus(1L, new CompanyStatusRequest(CompanyStatus.CLOSED), 3L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(403));
        verify(companies, never()).save(any());
    }

    @Test
    @DisplayName("U4-SVC-12 ไม่ระบุสถานะ ได้ 400 / บริษัทไม่มีอยู่ ได้ 404")
    void badInput() {
        when(companies.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(1L, new CompanyStatusRequest(null), 1L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(400));
        assertThatThrownBy(() -> service.updateStatus(999L, new CompanyStatusRequest(CompanyStatus.OPEN), 1L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(404));
    }
}
