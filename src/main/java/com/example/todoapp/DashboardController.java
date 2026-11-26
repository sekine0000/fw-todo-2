package com.example.todoapp;

import java.time.LocalDate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {
    private final DashboardService service;

    public DashboardController(DashboardService service) { this.service = service; }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        LocalDate today = LocalDate.now();
        var data = service.load(today);
        model.addAttribute("today", today);
        model.addAttribute("summary", data.summary());
        model.addAttribute("dailyLabels", java.util.List.copyOf(data.daily().keySet()));
        model.addAttribute("dailyCounts", java.util.List.copyOf(data.daily().values()));
        model.addAttribute("categories", data.categories());
        return "dashboard";
    }
}
