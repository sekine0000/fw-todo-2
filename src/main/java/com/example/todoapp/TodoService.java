package com.example.todoapp;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TodoService {

    private final TodoMapper todoMapper;

    public TodoService(TodoMapper todoMapper) {
        this.todoMapper = todoMapper;
    }

    public List<Todo> search(String keyword, String category, String order) {
        return search(keyword, category, order, null, null);
    }

    public List<Todo> search(String keyword, String category, String order, LocalDate from, LocalDate to) {
        return todoMapper.search(keyword, category, order, from, to, true);
    }

    public List<Todo> search(String keyword, String category, String order, boolean showCompleted) {
        return todoMapper.search(keyword, category, order, null, null, showCompleted);
    }

    public Todo findById(Long id) {
        return todoMapper.findById(id);
    }

    public long count(String keyword, String category, boolean showCompleted) {
        return todoMapper.count(keyword, category, showCompleted);
    }

    public List<Todo> searchPage(String keyword, String category, String order, boolean showCompleted,
            long offset) {
        return todoMapper.searchPage(keyword, category, order, showCompleted, offset);
    }

    public void create(Todo todo) {
        todoMapper.insert(todo);
        log.info("登録: id={}", todo.getId());
    }

    public void update(Todo todo) {
        todoMapper.update(todo);
        log.info("編集: id={}", todo.getId());
    }

    public void delete(Long id) {
        todoMapper.deleteById(id);
        log.info("削除: id={}", id);
    }
}
