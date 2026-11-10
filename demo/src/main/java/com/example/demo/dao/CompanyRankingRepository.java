package com.example.demo.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.entity.CompanyRanking;

/** DAO ของตาราง COMPANY_RANKINGS (U4) */
public interface CompanyRankingRepository extends JpaRepository<CompanyRanking, Long> {

    /** ลำดับบริษัทของนักศึกษา เรียงอันดับ 1 → 5 */
    List<CompanyRanking> findByStudentIdOrderByRankNoAsc(Long studentId);

    /**
     * ลบลำดับเดิมทั้งหมดของนักศึกษา (ใช้ก่อนบันทึกชุดใหม่)
     * ใช้ query ลบทันทีแทนการลบทีละแถว เพื่อให้ลบเสร็จก่อน insert ชุดใหม่ ไม่ชน unique constraint
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CompanyRanking r where r.studentId = :studentId")
    int deleteByStudentId(@Param("studentId") Long studentId);
}
