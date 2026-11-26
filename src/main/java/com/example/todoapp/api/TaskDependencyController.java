package com.example.todoapp.api;

import com.example.todoapp.TaskDependencyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/todos/{id}/dependencies")
public class TaskDependencyController {
    private final TaskDependencyService service;
    public TaskDependencyController(TaskDependencyService service) { this.service = service; }
    public record Request(@NotNull @Positive Long prerequisiteId) {}

    @GetMapping
    public TaskDependencyService.Graph get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(@PathVariable Long id, @Valid @RequestBody Request request) {
        service.add(id, request.prerequisiteId());
    }

    @DeleteMapping("/{prerequisiteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long id, @PathVariable Long prerequisiteId) {
        service.remove(id, prerequisiteId);
    }
}
