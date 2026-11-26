package com.example.todoapp;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.example.todoapp.TaskDependencyMapper.Edge;

@Service
public class TaskDependencyService {
    private final TaskDependencyMapper mapper;
    public TaskDependencyService(TaskDependencyMapper mapper) { this.mapper = mapper; }

    public record Graph(List<Todo> tasks, List<Edge> edges, List<Long> blockedIds) {}

    public Graph get(Long id) {
        var tasks = mapper.tasks();
        requireTask(tasks, id);
        var active = new HashSet<Long>();
        tasks.forEach(t -> active.add(t.getId()));
        var edges = mapper.edges().stream().filter(e -> active.contains(e.prerequisiteId())
                && active.contains(e.successorId())).toList();
        var incomplete = new HashSet<Long>();
        tasks.stream().filter(t -> !Boolean.TRUE.equals(t.getCompleted())).forEach(t -> incomplete.add(t.getId()));
        var blocked = edges.stream().filter(e -> incomplete.contains(e.prerequisiteId()))
                .map(Edge::successorId).distinct().toList();
        return new Graph(tasks, edges, blocked);
    }

    @Transactional
    public void add(Long id, Long prerequisiteId) {
        mapper.lockGraph(); // Serialize graph mutations across application instances.
        var tasks = mapper.tasks();
        requireTask(tasks, id);
        requireTask(tasks, prerequisiteId);
        var edges = mapper.edges();
        if (edges.contains(new Edge(prerequisiteId, id))) return;
        var adjacency = new HashMap<Long, List<Long>>();
        edges.forEach(e -> adjacency.computeIfAbsent(e.prerequisiteId(), k -> new ArrayList<>()).add(e.successorId()));
        var queue = new ArrayDeque<Long>();
        var visited = new HashSet<Long>();
        queue.add(id);
        while (!queue.isEmpty()) {
            Long current = queue.remove();
            if (current.equals(prerequisiteId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "循環依存になるため追加できません");
            }
            if (visited.add(current)) queue.addAll(adjacency.getOrDefault(current, List.of()));
        }
        mapper.insert(prerequisiteId, id);
    }

    @Transactional
    public void remove(Long id, Long prerequisiteId) {
        mapper.lockGraph();
        requireTask(mapper.tasks(), id);
        mapper.delete(prerequisiteId, id);
    }

    private void requireTask(List<Todo> tasks, Long id) {
        if (id == null || id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "タスクIDが不正です");
        if (tasks.stream().noneMatch(t -> id.equals(t.getId())))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "タスクが見つかりません");
    }
}
