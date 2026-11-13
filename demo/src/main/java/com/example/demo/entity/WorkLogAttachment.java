package com.example.demo.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** U8: Entity ตาราง WORK_LOG_ATTACHMENT ไฟล์แนบของบันทึก (ไม่เกิน 5 ไฟล์ต่อบันทึก) */
@Entity
@Table(name = "work_log_attachment")
public class WorkLogAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_log_id", nullable = false)
    private Long workLogId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    /** storageKey ที่ได้จาก FileStorage */
    @Column(name = "file_path", nullable = false)
    private String filePath;

    /** image/* หรือ application/pdf */
    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    /** ไม่เกิน 10 MB */
    @Column(nullable = false)
    private long size;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    protected WorkLogAttachment() {
        // สำหรับ JPA
    }

    public WorkLogAttachment(Long workLogId, String fileName, String filePath, String contentType, long size) {
        this.workLogId = workLogId;
        this.fileName = fileName;
        this.filePath = filePath;
        this.contentType = contentType;
        this.size = size;
    }

    @PrePersist
    void onCreate() {
        if (uploadedAt == null) uploadedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getWorkLogId() { return workLogId; }
    public String getFileName() { return fileName; }
    public String getFilePath() { return filePath; }
    public String getContentType() { return contentType; }
    public long getSize() { return size; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
}
