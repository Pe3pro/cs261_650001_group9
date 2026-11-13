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

import com.example.demo.dto.CompanyRequest;
import com.example.demo.dto.CompanyResponse;
import com.example.demo.dto.PositionRequest;
import com.example.demo.dto.PositionResponse;
import com.example.demo.entity.Company;
import com.example.demo.entity.CompanyPosition;
import com.example.demo.entity.CompanyStatus;
import com.example.demo.entity.PositionStatus;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.repository.CompanyPositionRepository;
import com.example.demo.repository.CompanyRepository;

/** U6:T4 Unit test logic บริษัท / ตำแหน่ง / โควตา ที่เพิ่มใน CompanyService */
class CompanyServiceU6Test {

    private CompanyRepository companies;
    private CompanyPositionRepository positions;
    private AccessGuard guard;
    private CompanyService service;

    @BeforeEach
    void setUp() {
        companies = mock(CompanyRepository.class);
        positions = mock(CompanyPositionRepository.class);
        guard = mock(AccessGuard.class);
        service = new CompanyService(companies, positions, guard);
        when(guard.require(1L, Role.STAFF)).thenReturn(new User("staff01", "สมศรี ทำงานดี", null, Role.STAFF));
        when(companies.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(positions.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Company company(Long id) {
        Company c = new Company("SCB TechX", CompanyStatus.OPEN);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private static CompanyPosition position(Long id, int quota, int available, PositionStatus status) {
        CompanyPosition p = new CompanyPosition(1L, "Backend Developer", quota, status);
        p.setAvailableSlots(available);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    @Test
    @DisplayName("U6-SVC-C01 รายชื่อบริษัทนับเฉพาะตำแหน่งที่เปิดรับ และรวมที่ว่าง")
    void openCompaniesWithQuota() {
        when(companies.findByStatus(CompanyStatus.OPEN)).thenReturn(List.of(company(1L)));
        when(positions.findByCompanyId(1L)).thenReturn(List.of(
                position(11L, 2, 1, PositionStatus.OPEN), position(12L, 3, 3, PositionStatus.OPEN),
                position(13L, 5, 5, PositionStatus.CLOSED)));

        List<CompanyResponse> result = service.getOpenCompanies(9L);

        assertThat(result.get(0).openPositions()).isEqualTo(2);
        assertThat(result.get(0).remainingQuota()).isEqualTo(4);
    }

    @Test
    @DisplayName("U6-SVC-C02 เพิ่มบริษัทโดยไม่กรอกชื่อ ได้ 400 และไม่บันทึก")
    void createCompanyWithoutName() {
        assertThatThrownBy(() -> service.createCompany(1L, new CompanyRequest(" ", null, null, null, null, null)))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(400);
        verify(companies, never()).save(any());
    }

    @Test
    @DisplayName("U6-SVC-C02 เพิ่มบริษัท ไม่ระบุสถานะ = เปิดรับ")
    void createCompanyDefaultsOpen() {
        CompanyResponse result = service.createCompany(1L, new CompanyRequest("Thai Beverage", "กรุงเทพฯ", null, null, null, null));

        assertThat(result.status()).isEqualTo(CompanyStatus.OPEN);
        assertThat(result.name()).isEqualTo("Thai Beverage");
    }

    @Test
    @DisplayName("U6-SVC-C04 เพิ่มตำแหน่ง โควตา 0 ได้ 400")
    void createPositionZeroQuota() {
        when(companies.findById(1L)).thenReturn(Optional.of(company(1L)));

        assertThatThrownBy(() -> service.createPosition(1L, 1L, new PositionRequest("Data Analyst", null, 0, null)))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(400);
    }

    @Test
    @DisplayName("U6-SVC-C05 ลดโควตาต่ำกว่าจำนวนที่จับคู่แล้ว ได้ 400 QUOTA_BELOW_MATCHED")
    void quotaBelowMatched() {
        when(positions.findByIdForUpdate(11L)).thenReturn(Optional.of(position(11L, 3, 1, PositionStatus.OPEN)));

        assertThatThrownBy(() -> service.updatePosition(1L, 11L, new PositionRequest("Backend Developer", null, 1, null)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("QUOTA_BELOW_MATCHED");
    }

    @Test
    @DisplayName("U6-SVC-C05 เพิ่มโควตา ที่ว่างเพิ่มตาม (จับคู่ไปแล้ว 2 จาก 3 → โควตา 5 เหลือว่าง 3)")
    void raiseQuota() {
        when(positions.findByIdForUpdate(11L)).thenReturn(Optional.of(position(11L, 3, 1, PositionStatus.OPEN)));
        when(companies.findById(1L)).thenReturn(Optional.of(company(1L)));

        PositionResponse result = service.updatePosition(1L, 11L, new PositionRequest("Backend Developer", null, 5, null));

        assertThat(result.quota()).isEqualTo(5);
        assertThat(result.availableSlots()).isEqualTo(3);
        assertThat(result.companyName()).isEqualTo("SCB TechX");
    }
}
