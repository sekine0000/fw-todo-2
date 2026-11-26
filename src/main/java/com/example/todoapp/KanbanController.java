package com.example.todoapp;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

@Controller
public class KanbanController {
    private final KanbanService service;
    public KanbanController(KanbanService service) { this.service = service; }
    @GetMapping("/kanban")
    public String board(Model model) {
        model.addAttribute("tasks", service.list());
        model.addAttribute("statuses", java.util.List.of("TODO", "IN_PROGRESS", "DONE"));
        return "kanban";
    }
    public record StatusRequest(String status) {}
    @PatchMapping("/api/todos/{id}/status")
    @ResponseBody
    public ResponseEntity<Void> move(@PathVariable("id") Long id, @RequestBody StatusRequest request) {
        service.move(id, request.status());
        return ResponseEntity.noContent().build();
    }
}
