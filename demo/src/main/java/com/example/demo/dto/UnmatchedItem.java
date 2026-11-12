package com.example.demo.dto;

/** U6: ใบสมัครที่จับคู่อัตโนมัติไม่ได้ พร้อมเหตุผล */
public record UnmatchedItem(Long applicationId, String studentName, String reason) {
}
