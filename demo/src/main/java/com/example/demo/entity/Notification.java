package com.example.demo.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Entity ตาราง NOTIFICATIONS: การแจ้งเตือนในระบบ */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ผู้รับการแจ้งเตือน (USERS.id) */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String message;

    /**
     * U8: Hibernate สร้างคอลัมน์เป็น ENUM ของ MySQL (ค่าตาม NotificationType ตอนสร้าง) ซึ่งเพิ่มค่าใหม่ไม่ได้
     * จึงให้ LegacySchemaFixer เปลี่ยนเป็น VARCHAR(50) ตอนเริ่มระบบ (เฉพาะ MySQL)
     * ไม่ใช้ @JdbcTypeCode(VARCHAR) เพราะ Hibernate จะสร้าง CHECK (type in ...) ซึ่ง H2 2.4 ใน @DataJpaTest
     * ตรวจไม่ได้ (Check constraint invalid / The database has been closed)
     */
    @Enumerated(EnumType.STRING)
    private NotificationType type;

    /** คำร้องที่เกี่ยวข้อง (ถ้ามี) */
    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    protected Notification() {
        // สำหรับ JPA
    }

    public Notification(Long userId, String title, String message, NotificationType type, Long applicationId) {
        this.userId = userId;
        this.title = title;
        this.message = message;
        this.type = type;
        this.applicationId = applicationId;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public void markAsRead() {
        this.isRead = true;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public NotificationType getType() { return type; }
    public Long getApplicationId() { return applicationId; }
    public Boolean getIsRead() { return isRead; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
