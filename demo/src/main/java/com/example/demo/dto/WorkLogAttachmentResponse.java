package com.example.demo.dto;

import java.time.LocalDateTime;

import com.example.demo.entity.WorkLogAttachment;

/** U8: ไฟล์แนบ 1 ไฟล์ */
public record WorkLogAttachmentResponse(Long id, String fileName, String contentType, long size,
        LocalDateTime uploadedAt) {

    public static WorkLogAttachmentResponse from(WorkLogAttachment a) {
        return new WorkLogAttachmentResponse(a.getId(), a.getFileName(), a.getContentType(), a.getSize(),
                a.getUploadedAt());
    }
}
