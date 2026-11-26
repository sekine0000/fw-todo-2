package com.example.todoapp;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class TodoArchiveService {
    private final TodoArchiveMapper mapper;
    public TodoArchiveService(TodoArchiveMapper mapper) { this.mapper = mapper; }

    @Transactional(readOnly = true)
    public List<Todo> list() { return mapper.findArchived(); }

    public void archive(Long id) {
        Todo todo = requireRoot(id);
        var children = mapper.lockChildren(id);
        if (!Boolean.TRUE.equals(todo.getCompleted())
                || children.stream().anyMatch(t -> !Boolean.TRUE.equals(t.getCompleted()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "未完了タスクはアーカイブできません");
        }
        if (!Boolean.TRUE.equals(todo.getArchived())) mapper.setArchived(id, true, LocalDateTime.now());
    }

    public void unarchive(Long id) {
        requireRoot(id);
        mapper.lockChildren(id);
        mapper.setArchived(id, false, null);
    }

    public void delete(Long id) {
        Todo todo = requireRoot(id);
        if (!Boolean.TRUE.equals(todo.getArchived())) throw new ResponseStatusException(HttpStatus.CONFLICT);
        mapper.lockChildren(id);
        mapper.deleteArchived(id);
    }

    public int archiveExpired(LocalDateTime cutoff, LocalDateTime now) {
        int count = 0;
        for (Long id : mapper.findExpired(cutoff)) {
            Todo todo = mapper.lock(id);
            if (todo == null || Boolean.TRUE.equals(todo.getArchived()) || !Boolean.TRUE.equals(todo.getCompleted())
                    || todo.getCompletedAt() == null || todo.getCompletedAt().isAfter(cutoff)) continue;
            var children = mapper.lockChildren(id);
            if (children.stream().anyMatch(t -> !Boolean.TRUE.equals(t.getCompleted())
                    || t.getCompletedAt() == null || t.getCompletedAt().isAfter(cutoff))) continue;
            mapper.setArchived(id, true, now);
            count++;
        }
        return count;
    }

    private Todo requireRoot(Long id) {
        Todo todo = mapper.lock(id);
        if (todo == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (todo.getParentId() != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "親タスクから操作してください");
        return todo;
    }
}
