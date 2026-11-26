package com.example.todoapp;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/templates")
public class TodoTemplateController {
    private final TodoTemplateService service;

    public TodoTemplateController(TodoTemplateService service) { this.service = service; }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("taskForm", new TemplateTaskForm());
        return listView(model);
    }

    private String listView(Model model) {
        model.addAttribute("templates", service.findAll());
        return "todo-templates";
    }

    @GetMapping("/new")
    public String newTemplate(Model model) {
        model.addAttribute("template", new TodoTemplate());
        return "template-create";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("template") TodoTemplate template,
            BindingResult errors, RedirectAttributes redirect) {
        if (errors.hasErrors()) return "template-create";
        service.create(template);
        redirect.addFlashAttribute("message", "テンプレートを作成しました");
        return "redirect:/templates";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        service.delete(id);
        redirect.addFlashAttribute("message", "テンプレートを削除しました");
        return "redirect:/templates";
    }

    @PostMapping("/apply")
    public String apply(@Valid @ModelAttribute("taskForm") TemplateTaskForm form,
            BindingResult errors, Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) return listView(model);
        int count = service.createTasks(form);
        redirect.addFlashAttribute("message", count + "件のタスクを作成しました");
        return "redirect:/todos";
    }
}
