package com.example.demo.dto;

import com.example.demo.entity.Document;
import com.example.demo.entity.DocumentStatus;

/** ส่งข้อมูลเอกสารโดยไม่เปิดเผยตำแหน่งจัดเก็บจริง (ใช้ร่วมกับ U3) */
public record DocumentResponse(Long id, String fileName, DocumentStatus status, Long formFieldId) {

    public DocumentResponse(Long id, String fileName, DocumentStatus status) {
        this(id, fileName, status, null);
    }

    public static DocumentResponse from(Document d) {
        return new DocumentResponse(d.getId(), d.getFileName(), d.getStatus(), d.getFormFieldId());
    }
}
