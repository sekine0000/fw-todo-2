package com.example.todoapp;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
@Transactional(rollbackFor = Exception.class)
public class KanbanService {
    private final KanbanMapper mapper;
    private final TodoMapper todos;
    private final TodoHistoryMapper history;
    public KanbanService(KanbanMapper mapper, TodoMapper todos, TodoHistoryMapper history) {
        this.mapper = mapper; this.todos = todos; this.history = history;
    }
    @Transactional(readOnly = true)
    public List<KanbanTask> list() { return mapper.list(); }

    @Auditable(action = "UPDATE")
    public void move(Long id, String status) {
        if (status == null || !List.of("TODO", "IN_PROGRESS", "DONE").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status");
        }
        Todo task = todos.findForUpdate(id);
        if (task == null || task.getParentId() != null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        boolean completed = "DONE".equals(status);
        var children = todos.findSubtasksForUpdate(id);
        if (!children.isEmpty() && children.stream().allMatch(t -> Boolean.TRUE.equals(t.getCompleted())) != completed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "サブタスクの完了状態と一致しません");
        }
        if (mapper.updateStatus(id, "IN_PROGRESS".equals(status), completed) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        history.insert(id, "UPDATE");
    }
}
