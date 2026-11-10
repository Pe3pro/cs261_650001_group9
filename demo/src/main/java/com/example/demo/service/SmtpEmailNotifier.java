package com.example.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * U5: ส่ง Email จริงผ่าน SMTP (เปิดด้วย app.mail.enabled=true และตั้ง spring.mail.host)
 * ถ้าไม่เปิด ระบบใช้ LoggingEmailNotifier ที่เขียนลง log แทน
 */
@Component
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
public class SmtpEmailNotifier implements EmailNotifier {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailNotifier(JavaMailSender mailSender,
            @Value("${app.mail.from:no-reply@coop.tu.ac.th}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("[ระบบสหกิจศึกษา] " + subject);
        message.setText(body);
        mailSender.send(message);
    }
}
