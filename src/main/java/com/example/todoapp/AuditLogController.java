package com.example.todoapp;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuditLogController {
    private final AuditLogService logs;
    public AuditLogController(AuditLogService logs) { this.logs = logs; }

    @GetMapping("/admin/audit-logs")
    public String index(@RequestParam(defaultValue = "") String action,
            @RequestParam(required = false) Long entityId,
            @RequestParam(defaultValue = "") String userId,
            @RequestParam(defaultValue = "1") int page, Model model) {
        int current = Math.max(1, page);
        var results = logs.search(action, entityId, userId, (current - 1L) * 50);
        model.addAttribute("logs", results.stream().limit(50).toList());
        model.addAttribute("hasNext", results.size() > 50);
        model.addAttribute("page", current);
        model.addAttribute("action", action);
        model.addAttribute("entityId", entityId);
        model.addAttribute("userId", userId);
        return "audit-logs";
    }
}
