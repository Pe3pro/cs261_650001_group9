package com.example.demo.config;

import java.sql.Connection;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * U8: แก้ schema ของฐานข้อมูลเดิมที่สร้างไว้ก่อน US8
 *
 * Hibernate สร้างคอลัมน์ notifications.type เป็น ENUM ของ MySQL ตามค่า NotificationType ตอนนั้น
 * และ ddl-auto=update ไม่แก้คอลัมน์ที่มีอยู่แล้ว ค่าใหม่ WORK_LOG_SUBMITTED / WORK_LOG_REVIEWED จึงบันทึกไม่ได้
 * (POST /api/work-logs/{id}/submit ได้ 500) คลาสนี้เปลี่ยนคอลัมน์เป็น VARCHAR(50) ครั้งเดียวตอนเริ่มระบบ
 * ทำงานเฉพาะ MySQL (H2 ที่ใช้ใน test ไม่กระทบ)
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LegacySchemaFixer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacySchemaFixer.class);

    private final DataSource dataSource;

    public LegacySchemaFixer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String product;
            try (Connection c = dataSource.getConnection()) {
                product = c.getMetaData().getDatabaseProductName();
            }
            if (product == null || !product.toLowerCase().contains("mysql")) {
                return;
            }
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            List<String> types = jdbc.queryForList(
                    "SELECT DATA_TYPE FROM information_schema.COLUMNS"
                            + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notifications' AND COLUMN_NAME = 'type'",
                    String.class);
            if (!types.isEmpty() && "enum".equalsIgnoreCase(types.get(0))) {
                jdbc.execute("ALTER TABLE notifications MODIFY type VARCHAR(50)");
                log.info("U8: เปลี่ยน notifications.type จาก ENUM เป็น VARCHAR(50) แล้ว");
            }
        } catch (Exception e) {
            log.warn("U8: ตรวจ / แก้คอลัมน์ notifications.type ไม่สำเร็จ: {}", e.getMessage());
        }
    }
}
