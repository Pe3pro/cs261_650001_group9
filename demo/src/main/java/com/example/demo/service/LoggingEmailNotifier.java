package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * U3: บันทึก Email ลง log ของ server แทนการส่งจริง (บรรทัดขึ้นต้นด้วย [EMAIL])
 * ใช้เมื่อไม่ได้เปิด app.mail.enabled=true (ถ้าเปิด จะใช้ SmtpEmailNotifier ส่งจริงแทน)
 */
@Component
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingEmailNotifier implements EmailNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailNotifier.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("[EMAIL] to={} subject={} body={}", to, subject, body);
    }
}
