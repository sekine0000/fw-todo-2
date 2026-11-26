package com.example.todoapp.async;

import java.util.concurrent.CompletableFuture;
import com.example.todoapp.TodoService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class TodoReportService {
    private final TodoService todos;

    public TodoReportService(TodoService todos) {
        this.todos = todos;
    }

    public record TodoReport(long total, long completed, long pending) {}

    @Async("taskExecutor")
    public CompletableFuture<TodoReport> generate(String keyword, String category) {
        // The read transaction starts inside TodoService on the worker thread.
        var items = todos.search(keyword, category, "asc");
        long completed = items.stream().filter(todo -> Boolean.TRUE.equals(todo.getCompleted())).count();
        return CompletableFuture.completedFuture(new TodoReport(items.size(), completed, items.size() - completed));
    }
}
