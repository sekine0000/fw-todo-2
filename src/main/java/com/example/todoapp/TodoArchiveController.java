package com.example.todoapp;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class TodoArchiveController {
    private final TodoArchiveService service;
    public TodoArchiveController(TodoArchiveService service) { this.service = service; }
    @GetMapping("/todo/archive")
    public String list(Model model) {
        model.addAttribute("todos", service.list());
        return "todo-archive";
    }
    @PostMapping("/todo/{id}/archive")
    public String archive(@PathVariable Long id) {
        service.archive(id);
        return "redirect:/todos";
    }
    @PostMapping("/todo/{id}/unarchive")
    public String unarchive(@PathVariable Long id) {
        service.unarchive(id);
        return "redirect:/todo/archive";
    }
    @PostMapping("/todo/{id}/archive/delete")
    public String delete(@PathVariable Long id) {
        service.delete(id);
        return "redirect:/todo/archive";
    }
}
