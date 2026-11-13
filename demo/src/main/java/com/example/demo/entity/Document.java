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

/** Entity ตาราง DOCUMENTS: ไฟล์ที่แนบกับคำร้อง เช่น Transcript (U3 อัปโหลด, U5 เปิดดู) */
@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "storage_key")
    private String storageKey;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    /** U5: ช่องชนิดไฟล์ในแบบฟอร์มที่ไฟล์นี้ตอบ (null = ไฟล์ Transcript แบบเดิมที่ไม่ได้ระบุช่อง) */
    @Column(name = "form_field_id")
    private Long formFieldId;

    protected Document() {
        // สำหรับ JPA
    }

    public Document(Long applicationId, String fileName, String storageKey, Long fileSize) {
        this.applicationId = applicationId;
        this.fileName = fileName;
        this.storageKey = storageKey;
        this.fileSize = fileSize;
        this.contentType = "application/pdf";
        this.status = DocumentStatus.UPLOADED;
    }

    public Document(Long applicationId, String fileName, String storageKey, Long fileSize, Long formFieldId) {
        this(applicationId, fileName, storageKey, fileSize);
        this.formFieldId = formFieldId;
    }

    @PrePersist
    void onCreate() {
        if (uploadedAt == null) uploadedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getApplicationId() { return applicationId; }
    public String getFileName() { return fileName; }
    public String getStorageKey() { return storageKey; }
    public String getContentType() { return contentType; }
    public Long getFileSize() { return fileSize; }
    public DocumentStatus getStatus() { return status; }
    public void setStatus(DocumentStatus status) { this.status = status; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public Long getFormFieldId() { return formFieldId; }
}
