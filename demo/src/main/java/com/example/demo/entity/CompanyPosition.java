package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Entity ตาราง COMPANY_POSITIONS (U3: ดูตำแหน่งที่เปิดรับ) */
@Entity
@Table(name = "company_positions")
public class CompanyPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "position_name", nullable = false)
    private String positionName;

    private String description;

    @Column(nullable = false)
    private Integer quota;

    @Column(name = "available_slots", nullable = false)
    private Integer availableSlots;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PositionStatus status;

    protected CompanyPosition() {
        // สำหรับ JPA
    }

    public CompanyPosition(Long companyId, String positionName, Integer quota, PositionStatus status) {
        this.companyId = companyId;
        this.positionName = positionName;
        this.quota = quota;
        this.availableSlots = quota;
        this.status = status;
    }

    public boolean isOpen() {
        return status == PositionStatus.OPEN;
    }

    public boolean hasAvailableSlot() {
        return availableSlots != null && availableSlots > 0;
    }

    /** U6: จับคู่แล้วกินโควตา 1 ที่ */
    public void takeSlot() {
        if (!hasAvailableSlot()) {
            throw new IllegalStateException("ตำแหน่งนี้เต็มแล้ว");
        }
        this.availableSlots = this.availableSlots - 1;
    }

    /** U6: สละสิทธิ์ / ยกเลิกแล้วคืนโควตา 1 ที่ (ไม่เกินโควตาทั้งหมด) */
    public void releaseSlot() {
        if (this.availableSlots < this.quota) {
            this.availableSlots = this.availableSlots + 1;
        }
    }

    /** U6: จำนวนที่จับคู่ไปแล้ว */
    public int usedSlots() {
        return this.quota - this.availableSlots;
    }

    /** U6: แก้ชื่อตำแหน่ง */
    public void setPositionName(String positionName) { this.positionName = positionName; }

    /** เปลี่ยนโควตา และปรับจำนวนที่ว่างตามจำนวนที่ถูกใช้ไปแล้ว */
    public void updateQuota(Integer newQuota) {
        if (newQuota == null || newQuota < 0) {
            throw new IllegalArgumentException("quota ต้องไม่ติดลบ");
        }
        int used = this.quota - this.availableSlots;
        if (newQuota < used) {
            throw new IllegalArgumentException("quota ต้องไม่น้อยกว่าจำนวนที่รับไปแล้ว (" + used + ")");
        }
        this.quota = newQuota;
        this.availableSlots = newQuota - used;
    }

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public String getPositionName() { return positionName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getQuota() { return quota; }
    public Integer getAvailableSlots() { return availableSlots; }
    public void setAvailableSlots(Integer availableSlots) { this.availableSlots = availableSlots; }
    public PositionStatus getStatus() { return status; }
    public void setStatus(PositionStatus status) { this.status = status; }
}
