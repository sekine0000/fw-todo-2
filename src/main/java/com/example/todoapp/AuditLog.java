package com.example.todoapp;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AuditLog {
    private Long id;
    private String action;
    private String entityType;
    private Long entityId;
    private String userId;
    private String oldValue;
    private String newValue;
    private String ipAddress;
    private String status;
    private LocalDateTime createdAt;
}
