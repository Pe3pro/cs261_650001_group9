package com.example.demo.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.entity.WorkLogAttachment;

/** U8: DAO ของตาราง WORK_LOG_ATTACHMENT (save, delete มาจาก JpaRepository) */
public interface WorkLogAttachmentDao extends JpaRepository<WorkLogAttachment, Long> {

    List<WorkLogAttachment> findByWorkLogIdOrderByIdAsc(Long workLogId);

    default List<WorkLogAttachment> findByWorkLogId(Long workLogId) {
        return findByWorkLogIdOrderByIdAsc(workLogId);
    }

    Optional<WorkLogAttachment> findByIdAndWorkLogId(Long id, Long workLogId);

    long countByWorkLogId(Long workLogId);
}
