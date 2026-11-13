package com.example.demo.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.entity.CompanyPreference;

/** DAO ของตาราง COMPANY_PREFERENCES (U6) */
public interface CompanyPreferenceRepository extends JpaRepository<CompanyPreference, Long> {

    /** อันดับของใบสมัคร เรียง 1 → 5 */
    List<CompanyPreference> findByApplicationIdOrderByRankNoAsc(Long applicationId);

    /** ใบสมัครที่เลือกตำแหน่งนี้ เรียงตามอันดับ (GET /api/matching/candidates) */
    List<CompanyPreference> findByPositionIdOrderByRankNoAsc(Long positionId);

    /** ลบอันดับเดิมทั้งหมดก่อนบันทึกชุดใหม่ (ลบทันที ไม่ชน unique constraint ตอน insert) */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CompanyPreference p where p.applicationId = :applicationId")
    int deleteByApplicationId(@Param("applicationId") Long applicationId);
}
