package com.example.todoapp;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {
    private final AuditLogMapper mapper;

    public AuditLogService(AuditLogMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(AuditLog log) { mapper.insertLog(log); }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void complete(AuditLog log) { mapper.complete(log); }

    @Transactional(readOnly = true)
    public java.util.List<AuditLog> search(String action, Long entityId, String userId, long offset) {
        return mapper.search(action, entityId, userId, offset);
    }

}
