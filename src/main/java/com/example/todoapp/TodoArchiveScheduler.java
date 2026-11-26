package com.example.todoapp;

import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TodoArchiveScheduler {
    private final TodoArchiveService service;
    private final int days;
    private final ZoneId zone;
    public TodoArchiveScheduler(TodoArchiveService service,
            @Value("${todo.archive.days:30}") int days,
            @Value("${todo.archive.zone:Asia/Tokyo}") String zone) {
        if (days < 1) throw new IllegalArgumentException("todo.archive.days must be positive");
        this.service = service;
        this.days = days;
        this.zone = ZoneId.of(zone);
    }
    @Scheduled(cron = "0 0 2 * * *", zone = "${todo.archive.zone:Asia/Tokyo}")
    public void archiveCompleted() {
        LocalDateTime now = LocalDateTime.now(zone);
        service.archiveExpired(now.minusDays(days), now);
    }
}
