package com.example.demo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.entity.CompanyPosition;

import jakarta.persistence.LockModeType;

/** DAO ของตาราง COMPANY_POSITIONS */

public interface CompanyPositionRepository extends JpaRepository<CompanyPosition, Long> {

    /** ดึงทุกตำแหน่งของบริษัท */
    List<CompanyPosition> findByCompanyId(Long companyId);

    /** ดึงเฉพาะตำแหน่งที่เปิดรับและยังมีที่ว่าง (GET /api/companies/{id}/positions) */
    @Query("""
            SELECT p FROM CompanyPosition p
            WHERE p.companyId = :companyId
              AND p.status = com.example.demo.entity.PositionStatus.OPEN
              AND p.availableSlots > 0
            ORDER BY p.positionName
            """)
    List<CompanyPosition> findOpenPositions(@Param("companyId") Long companyId);

    /** U6: อ่านตำแหน่งพร้อมล็อกแถว กันจับคู่พร้อมกันจนโควตาเกิน (SELECT ... FOR UPDATE) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM CompanyPosition p WHERE p.id = :id")
    Optional<CompanyPosition> findByIdForUpdate(@Param("id") Long id);
}
