package com.example.todoapp.api;

import com.example.todoapp.ProgressMapper;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProgressController {
    private final ProgressMapper mapper;

    public ProgressController(ProgressMapper mapper) { this.mapper = mapper; }

    public record Progress(String category, long total, long completed, double percentage) {
        static Progress of(String category, long total, long completed) {
            return new Progress(category, total, completed,
                    total == 0 ? 0 : completed * 100.0 / total);
        }
    }
    public record ProgressResponse(Progress overall, List<Progress> categories) {}

    @GetMapping("/api/todos/progress")
    public ResponseEntity<ProgressResponse> progress() {
        var rows = mapper.categoryProgress();
        var categories = rows.stream()
                .map(row -> Progress.of(row.getCategory(), row.getTotal(), row.getCompleted())).toList();
        long total = rows.stream().mapToLong(ProgressMapper.CategoryProgress::getTotal).sum();
        long completed = rows.stream().mapToLong(ProgressMapper.CategoryProgress::getCompleted).sum();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ProgressResponse(Progress.of(null, total, completed), categories));
    }
}
