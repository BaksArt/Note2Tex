package com.baksart.Note2TexBack.auth;

import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.*;
import org.springframework.stereotype.Service;
@Service
public class MailService {
    private final JavaMailSender sender;

    @Value("${spring.mail.username}")
    String from;

    public MailService(JavaMailSender sender){ this.sender = sender; }

    public void send(String to, String subject, String html) {
        var helper = new MimeMessageHelper(sender.createMimeMessage(), "UTF-8");
        try {
            helper.setFrom(from, "Note2Tex");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(helper.getMimeMessage());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
