package com.example.todoapp.api;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.example.todoapp.async.TodoReportService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class TodoReportController {
    private final TodoReportService reports;
    private final long timeoutMillis;

    public TodoReportController(TodoReportService reports,
            @Value("${app.async.report-timeout-ms:5000}") long timeoutMillis) {
        if (timeoutMillis <= 0) throw new IllegalArgumentException("Report timeout must be positive");
        this.reports = reports;
        this.timeoutMillis = timeoutMillis;
    }

    @GetMapping("/api/todos/report")
    public TodoReportService.TodoReport report(
            @RequestParam(defaultValue = "", name = "keyword") String keyword,
            @RequestParam(defaultValue = "", name = "category") String category) {
        try {
            var future = reports.generate(keyword, category);
            // A timeout stops waiting; it does not guarantee cancellation of database work.
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Report generation timed out", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Report wait interrupted", e);
        } catch (ExecutionException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Report generation failed", e.getCause());
        } catch (TaskRejectedException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Report executor is busy", e);
        }
    }
}
