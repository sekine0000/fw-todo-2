package com.example.todoapp;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class TodoService {

    private final TodoMapper todoMapper;

    private final TodoHistoryMapper historyMapper;


    public TodoService(TodoMapper todoMapper, TodoHistoryMapper historyMapper) {
        this.todoMapper = todoMapper;
        this.historyMapper = historyMapper;

    }

    @Transactional(readOnly = true)
    public List<Todo> search(String keyword, String category, String order) {
        return search(keyword, category, order, null, null);
    }

    @Transactional(readOnly = true)
    public List<Todo> search(String keyword, String category, String order, LocalDate from, LocalDate to) {
        return todoMapper.search(keyword, category, order, from, to, true);
    }

    @Transactional(readOnly = true)
    public List<Todo> search(String keyword, String category, String order, boolean showCompleted) {
        return todoMapper.search(keyword, category, order, null, null, showCompleted);
    }

    @Transactional(readOnly = true)
    public Todo findById(Long id) {
        return todoMapper.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Todo> searchCsv(String keyword, String category, String order,
            boolean showCompleted, boolean trash) {
        return todoMapper.searchCsv(keyword, category, order, showCompleted, trash);
    }

    @Transactional(readOnly = true)
    public long count(String keyword, String category, boolean showCompleted) {
        return todoMapper.count(keyword, category, showCompleted);
    }

    @Transactional(readOnly = true)
    public List<Todo> searchPage(String keyword, String category, String order, boolean showCompleted,
            long offset) {
        return todoMapper.searchPage(keyword, category, order, showCompleted, offset);
    }

    @Auditable(action = "CREATE")
    public void create(Todo todo) {
        todoMapper.insert(todo);
        historyMapper.insert(todo.getId(), "CREATE");
        log.info("登録: id={}", todo.getId());
    }

    @Transactional(readOnly = true)
    public long countTrash(String keyword, String category) {
        return todoMapper.countTrash(keyword, category);
    }

    @Transactional(readOnly = true)
    public List<Todo> searchTrashPage(String keyword, String category, String order, long offset) {
        return todoMapper.searchTrashPage(keyword, category, order, offset);
    }

    @Auditable(action = "RESTORE")
    public void restore(Long id) {
        todoMapper.restoreById(id);
        historyMapper.insert(id, "RESTORE");
        log.info("復元: id={}", id);
    }

    @Auditable(action = "UPDATE")
    public void update(Todo todo) {
        todoMapper.update(todo);
        historyMapper.insert(todo.getId(), "UPDATE");
        log.info("編集: id={}", todo.getId());
    }

    @Auditable(action = "DELETE")
    public void delete(Long id) {
        todoMapper.deleteById(id);
        historyMapper.insert(id, "DELETE");
        log.info("削除: id={}", id);
    }

    @Auditable(action = "TOGGLE_PINNED")
    public void togglePinned(Long id) {
        todoMapper.togglePinned(id);
        historyMapper.insert(id, "TOGGLE_PINNED");
    }

    @Auditable(action = "DELETE")
    public int deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        int deleted = todoMapper.deleteByIds(ids);
        for (Long id : ids.stream().distinct().toList()) {
            historyMapper.insert(id, "DELETE");
        }
        return deleted;
    }
}
