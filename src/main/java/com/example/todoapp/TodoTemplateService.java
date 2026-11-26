package com.example.todoapp;

import java.util.List;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(rollbackFor = Exception.class)
public class TodoTemplateService {
    private final TodoTemplateMapper mapper;
    private final TodoService todos;
    private final Validator validator;

    public TodoTemplateService(TodoTemplateMapper mapper, TodoService todos, Validator validator) {
        this.mapper = mapper;
        this.todos = todos;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public List<TodoTemplate> findAll() {
        var templates = mapper.findAll();
        templates.forEach(t -> t.setItems(mapper.findItems(t.getId())));
        return templates;
    }

    public void create(TodoTemplate template) {
        validate(template);
        mapper.insert(template);
        for (int i = 0; i < template.getItems().size(); i++) {
            mapper.insertItem(template.getId(), i, template.getItems().get(i).strip());
        }
    }

    public void delete(Long id) {
        if (mapper.delete(id) == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    public int createTasks(TemplateTaskForm form) {
        validate(form);
        // Serialize deletion against applying the template so its items remain available.
        if (mapper.findForUpdate(form.getTemplateId()) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "テンプレートが見つかりません");
        }
        var items = mapper.findItems(form.getTemplateId());
        if (items.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "テンプレートに項目がありません");
        for (String title : items) {
            var todo = new Todo();
            todo.setTitle(title);
            todo.setCategory(form.getCategory());
            todo.setPriority(form.getPriority());
            todo.setDueDate(form.getDueDate());
            todo.setCompleted(false);
            todos.create(todo);
        }
        return items.size();
    }

    private void validate(Object value) {
        if (!validator.validate(value).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "入力内容を確認してください");
        }
    }
}
