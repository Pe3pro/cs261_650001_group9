package com.example.demo.dao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.entity.WorkLog;
import com.example.demo.entity.WorkLogStatus;

/** U8: DAO ของตาราง WORK_LOG (findById, save, delete มาจาก JpaRepository) */
public interface WorkLogDao extends JpaRepository<WorkLog, Long> {

    /** จำนวนบันทึกต่อสถานะ ใช้กับ countByInternshipIdGroupByStatus */
    interface StatusCount {
        WorkLogStatus getStatus();

        Long getCount();
    }

    /** บันทึกทั้งหมดของการฝึกงาน เรียงตามวันที่ */
    List<WorkLog> findByInternshipIdOrderByLogDateAscIdAsc(Long internshipId);

    /** บันทึกของการฝึกงาน กรองช่วงวัน / สถานะ (ค่า null = ไม่กรอง) เรียงตามวันที่ */
    default List<WorkLog> findByInternshipIdAndFilter(Long internshipId, LocalDate from, LocalDate to,
            WorkLogStatus status) {
        return findByInternshipIdOrderByLogDateAscIdAsc(internshipId).stream()
                .filter(w -> from == null || !w.getLogDate().isBefore(from))
                .filter(w -> to == null || !w.getLogDate().isAfter(to))
                .filter(w -> status == null || w.getStatus() == status)
                .toList();
    }

    /** มีบันทึกของวันนี้แล้วหรือยัง (1 วันได้ 1 บันทึก) */
    boolean existsByInternshipIdAndLogDate(Long internshipId, LocalDate logDate);

    /** บันทึกสถานะที่กำหนดของนักศึกษาในที่ปรึกษา เรียงตามเวลาที่ส่ง */
    @Query("select w from WorkLog w where w.status = :status and w.studentId in "
            + "(select s.userId from Student s where s.advisorId = :advisorId) order by w.submittedAt asc, w.id asc")
    List<WorkLog> findByStatusAndAdvisorId(@Param("status") WorkLogStatus status,
            @Param("advisorId") Long advisorId);

    /** บันทึกที่รอตรวจ (SUBMITTED) ของนักศึกษาในที่ปรึกษา */
    default List<WorkLog> findSubmittedByAdvisorId(Long advisorId) {
        return findByStatusAndAdvisorId(WorkLogStatus.SUBMITTED, advisorId);
    }

    List<WorkLog> findAllByIdIn(Collection<Long> ids);

    /** ชั่วโมงรวมที่ไม่ใช่ DRAFT (ไม่มีบันทึก = 0) */
    @Query("select coalesce(sum(w.hours), 0) from WorkLog w where w.internshipId = :internshipId and w.status <> :draft")
    BigDecimal sumHoursExcludingStatus(@Param("internshipId") Long internshipId,
            @Param("draft") WorkLogStatus draft);

    default BigDecimal sumHoursByInternshipId(Long internshipId) {
        BigDecimal sum = sumHoursExcludingStatus(internshipId, WorkLogStatus.DRAFT);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    /** วันที่ที่มีบันทึกแล้ว (ทุกสถานะ) */
    @Query("select w.logDate from WorkLog w where w.internshipId = :internshipId order by w.logDate")
    List<LocalDate> findLogDatesByInternshipId(@Param("internshipId") Long internshipId);

    @Query("select w.status as status, count(w) as count from WorkLog w "
            + "where w.internshipId = :internshipId group by w.status")
    List<StatusCount> countByInternshipIdGroupByStatus(@Param("internshipId") Long internshipId);

    /** มีบันทึกสถานะนี้ค้างอยู่ไหม (เช่น SUBMITTED ค้างตรวจ) */
    boolean existsByInternshipIdAndStatus(Long internshipId, WorkLogStatus status);
}
