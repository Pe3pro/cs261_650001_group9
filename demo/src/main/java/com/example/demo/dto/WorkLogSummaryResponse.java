package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.example.demo.entity.WorkLogStatus;

/**
 * U8: สรุปบันทึกการทำงาน
 * totalHours = ชั่วโมงรวมที่ไม่ใช่ DRAFT · workingDays = วันจันทร์–ศุกร์ตั้งแต่วันเริ่มถึงวันนี้ (หรือวันสิ้นสุด)
 * missingDates = วันทำงานที่ยังไม่มีบันทึก
 */
public record WorkLogSummaryResponse(BigDecimal totalHours, int daysLogged, int workingDays,
        List<LocalDate> missingDates, Map<WorkLogStatus, Long> countByStatus) {
}
