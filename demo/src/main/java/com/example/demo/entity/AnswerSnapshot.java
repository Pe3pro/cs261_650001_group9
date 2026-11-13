package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * U5: สำเนาคำตอบ ณ ตอนที่เจ้าหน้าที่/อาจารย์ตีกลับ
 * ใช้เทียบกับคำตอบที่นักศึกษาแก้แล้วส่งใหม่ เพื่อให้ผู้ตรวจเห็นว่าช่องไหนเปลี่ยนไป
 */
@Entity
@Table(name = "answer_snapshots")
public class AnswerSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** การพิจารณาแบบ RETURN ที่สร้างสำเนานี้ */
    @Column(name = "approval_id", nullable = false)
    private Long approvalId;

    @Column(name = "form_field_id", nullable = false)
    private Long formFieldId;

    @Column(name = "field_value", length = 2000)
    private String value;

    protected AnswerSnapshot() {
        // สำหรับ JPA
    }

    public AnswerSnapshot(Long approvalId, Long formFieldId, String value) {
        this.approvalId = approvalId;
        this.formFieldId = formFieldId;
        this.value = value;
    }

    public Long getId() { return id; }
    public Long getApprovalId() { return approvalId; }
    public Long getFormFieldId() { return formFieldId; }
    public String getValue() { return value; }
}
