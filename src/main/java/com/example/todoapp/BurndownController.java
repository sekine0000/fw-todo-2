package com.example.todoapp;

import java.time.LocalDate;
import java.time.ZoneId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class BurndownController {
    private final SprintMapper mapper;
    private final BurndownService service;
    public BurndownController(SprintMapper mapper, BurndownService service) {
        this.mapper = mapper;
        this.service = service;
    }

    @GetMapping("/burndown")
    public String chart(@RequestParam(required = false) Long sprintId, Model model) {
        var sprints = mapper.findAll();
        model.addAttribute("sprints", sprints);
        Sprint selected = sprintId == null ? (sprints.isEmpty() ? null : sprints.getFirst()) : mapper.findById(sprintId);
        if (sprintId != null && selected == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        model.addAttribute("selected", selected);
        if (selected != null) model.addAttribute("data", service.load(selected, LocalDate.now(ZoneId.of("Asia/Tokyo"))));
        return "burndown";
    }

    @GetMapping("/sprints/new")
    public String newSprint(Model model) {
        model.addAttribute("sprint", new Sprint());
        model.addAttribute("tasks", mapper.availableTasks());
        return "sprint-create";
    }

    @PostMapping("/sprints")
    public String create(@Valid @ModelAttribute Sprint sprint, BindingResult binding, Model model) {
        if (!binding.hasErrors()) {
            try { return "redirect:/burndown?sprintId=" + service.create(sprint); }
            catch (IllegalArgumentException e) { binding.reject("sprint.invalid", e.getMessage()); }
        }
        model.addAttribute("tasks", mapper.availableTasks());
        return "sprint-create";
    }
}
