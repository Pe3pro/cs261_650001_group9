package com.example.demo.dao;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.entity.Notification;
import com.example.demo.entity.NotificationType;

/** DAO ของตาราง NOTIFICATIONS */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** การแจ้งเตือนของผู้ใช้ ใหม่สุดอยู่บน */
    List<Notification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    long countByUserIdAndIsReadFalse(Long userId);

    /** U5: กันเตือนซ้ำ · มีแจ้งเตือนชนิดนี้ของคำร้องนี้หลังเวลาที่กำหนดแล้วหรือยัง */
    boolean existsByApplicationIdAndTypeAndCreatedAtAfter(Long applicationId, NotificationType type,
            LocalDateTime after);
}
