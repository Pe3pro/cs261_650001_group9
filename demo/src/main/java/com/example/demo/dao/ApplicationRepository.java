package com.example.demo.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.demo.entity.Application;
import com.example.demo.entity.ApplicationStatus;

/** DAO ของตาราง APPLICATIONS ใช้ร่วมกับ U3, U5, U6 และ U7 */
public interface ApplicationRepository
        extends JpaRepository<Application, Long> {

    /** U5: คำร้องตามสถานะ เรียงตามเวลายื่น */
    List<Application>
            findByStatusOrderBySubmittedAtAsc(
                    ApplicationStatus status
            );

    /** U3: คำร้องทั้งหมดของนักศึกษา */
    List<Application> findByStudentId(Long studentId);

    long countByStatus(ApplicationStatus status);

    /** U3: คำร้องของนักศึกษา ฉบับล่าสุดก่อน */
    List<Application>
            findByStudentIdOrderByIdDesc(Long studentId);

    /** U7: อ่านใบสมัครล่าสุดของนักศึกษา */
    Optional<Application>
            findFirstByStudentIdOrderByIdDesc(
                    Long studentId
            );

    /** U7: ชื่อเมธอดตาม DAO Design */
    default Optional<Application> findLatestByStudentId(
            Long studentId
    ) {
        return findFirstByStudentIdOrderByIdDesc(
                studentId
        );
    }

    /**
     * U6: ใบสมัคร APPROVED ที่ยังไม่มีผลจับคู่
     * ที่ใช้อยู่ เรียงตามเวลายื่น
     */
    @Query("""
            SELECT a FROM Application a
            WHERE a.status =
                com.example.demo.entity.ApplicationStatus.APPROVED
              AND NOT EXISTS (
                SELECT m.id FROM MatchResult m
                WHERE m.applicationId = a.id
                  AND m.status IN (
                    com.example.demo.entity.MatchStatus.PROPOSED,
                    com.example.demo.entity.MatchStatus.CONFIRMED
                  )
              )
            ORDER BY a.submittedAt ASC, a.id ASC
            """)
    List<Application> findApprovedWithoutActiveMatch();
}