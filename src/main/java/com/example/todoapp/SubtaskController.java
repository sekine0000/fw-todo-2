package com.example.todoapp;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Controller
public class SubtaskController {
    private final TodoService service;
    public SubtaskController(TodoService service) { this.service = service; }

    @GetMapping("/todos/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Todo todo = service.findById(id);
        if (todo == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (todo.getParentId() != null) return "redirect:/todos/" + todo.getParentId();
        var children = service.subtasks(id);
        long completed = children.stream().filter(t -> Boolean.TRUE.equals(t.getCompleted())).count();
        model.addAttribute("todo", todo);
        model.addAttribute("subtasks", children);
        model.addAttribute("completedCount", completed);
        model.addAttribute("progress", children.isEmpty() ? 0 : completed * 100 / children.size());
        return "detail";
    }

    @PostMapping("/todos/{parentId}/subtasks")
    public String add(@PathVariable Long parentId, @RequestParam String title) {
        service.addSubtask(parentId, title);
        return "redirect:/todos/" + parentId;
    }

    @PostMapping("/todos/{parentId}/subtasks/{id}/toggle")
    public String toggle(@PathVariable Long parentId, @PathVariable Long id) {
        service.toggleSubtask(parentId, id);
        return "redirect:/todos/" + parentId;
    }

    @PostMapping("/todos/{parentId}/subtasks/{id}/delete")
    public String delete(@PathVariable Long parentId, @PathVariable Long id) {
        service.deleteSubtask(parentId, id);
        return "redirect:/todos/" + parentId;
    }
}