package com.example.todoapp.async;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class EmailService {
    private final ObjectProvider<JavaMailSender> senders;
    private final String from;

    public EmailService(ObjectProvider<JavaMailSender> senders,
            @Value("${app.mail.from:}") String from) {
        this.senders = senders;
        this.from = from;
    }

    @Async("emailExecutor")
    public void send(String recipient, String subject, String body) {
        var sender = senders.getIfAvailable();
        if (sender == null || from.isBlank()) {
            throw new IllegalStateException("Configure spring.mail.host and app.mail.from before sending email");
        }
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
    }
}
