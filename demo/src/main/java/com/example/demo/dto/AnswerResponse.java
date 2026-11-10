package com.example.demo.dto;

/**
 * คำตอบ 1 ช่องในคำร้อง พร้อมชื่อช่องที่อ่านเข้าใจได้
 * fieldId ใช้ระบุช่องที่ต้องแก้ตอนตีกลับ · previousValue = คำตอบก่อนถูกตีกลับ (มีเฉพาะช่องที่นักศึกษาแก้)
 */
public record AnswerResponse(Long fieldId, String label, String value, String previousValue) {

    public AnswerResponse(String label, String value) {
        this(null, label, value, null);
    }
}
