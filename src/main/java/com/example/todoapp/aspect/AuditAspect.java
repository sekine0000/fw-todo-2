package com.example.todoapp.aspect;

import com.example.todoapp.*;
import java.util.*;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.transaction.support.*;
import org.springframework.web.context.request.*;
import tools.jackson.databind.json.JsonMapper;

@Aspect
@Component
@Order(Integer.MAX_VALUE)
public class AuditAspect {
    private final TodoMapper todos;
    private final AuditLogService logs;
    private final JsonMapper json = JsonMapper.builder().build();

    public AuditAspect(TodoMapper todos, AuditLogService logs) {
        this.todos = todos;
        this.logs = logs;
    }

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint point, Auditable auditable) throws Throwable {
        Object argument = point.getArgs()[0];
        List<Long> ids = new ArrayList<>();
        if (argument instanceof Todo todo) ids.add(todo.getId());
        else if (argument instanceof Long id) ids.add(id);
        else if (argument instanceof List<?> list) {
            for (Object id : list) if (id instanceof Long value && !ids.contains(value)) ids.add(value);
        }
        List<AuditLog> entries = new ArrayList<>();
        for (Long id : ids) {
            AuditLog entry = new AuditLog();
            entry.setAction(auditable.action());
            entry.setEntityType(auditable.entityType());
            entry.setEntityId(id);
            entry.setOldValue(json.writeValueAsString(id == null ? null : todos.findForAudit(id)));
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken))
                entry.setUserId(auth.getName());
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes request)
                entry.setIpAddress(request.getRequest().getRemoteAddr());
            entry.setStatus("ATTEMPT");
            logs.record(entry);
            entries.add(entry);
        }
        boolean[] failed = {false};
        boolean synchronizedTransaction = TransactionSynchronizationManager.isSynchronizationActive();
        if (synchronizedTransaction) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    finish(entries, status == STATUS_COMMITTED ? "SUCCESS" : "ROLLED_BACK");
                }
            });
        }
        try {
            Object result = point.proceed();
            return result;
        } catch (Throwable error) {
            failed[0] = true;
            throw error;
        } finally {
            for (AuditLog entry : entries) {
                if (argument instanceof Todo todo) entry.setEntityId(todo.getId());
                entry.setNewValue(json.writeValueAsString(entry.getEntityId() == null ? null
                        : todos.findForAudit(entry.getEntityId())));
            }
            if (!synchronizedTransaction) finish(entries, failed[0] ? "FAILED" : "SUCCESS");
        }
    }

    private void finish(List<AuditLog> entries, String status) {
        for (AuditLog entry : entries) {
            entry.setStatus(status);
            logs.complete(entry);
        }
    }
}
