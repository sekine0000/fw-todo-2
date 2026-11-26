package com.example.todoapp.api;

import java.time.LocalDate;

import com.example.todoapp.Todo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * ToDoの登録・更新APIの入力フォーム。画面フォームは {@link Todo} を使用する。
 *
 * @author ToDoアプリ開発チーム
 * @version 0.0.1
 * @since 0.0.1
 * @see com.example.todoapp.TodoService
 */
@Data
public class TodoRequest {

    /** 必須の名称。最大255文字。 */
    @NotBlank(message = "やることを入力してください")
    @Size(max = 255, message = "やることは255文字以内で入力してください")
    private String title;

    /** 任意のメモ。最大255文字。 */
    @Size(max = 255, message = "メモは255文字以内で入力してください")
    private String detail;

    /** 必須のジャンル。 */
    @NotBlank(message = "ジャンルを選んでください")
    private String category;

    /** 必須の優先度。{@code 1} は高、{@code 2} は中、{@code 3} は低。 */
    @NotNull(message = "優先度を選んでください")
    private Integer priority;

    /** 任意の期限日。 */
    private LocalDate dueDate;
    /** {@code true} で完了。 */
    private Boolean completed;

    /**
     * 入力項目を新しい {@link Todo} に転記する。IDや監査日時は設定しない。
     *
     * @return 入力項目を転記した新しいToDo
     */
    public Todo toTodo() {
        Todo todo = new Todo();
        todo.setTitle(title);
        todo.setDetail(detail);
        todo.setCategory(category);
        todo.setPriority(priority);
        todo.setDueDate(dueDate);
        todo.setCompleted(completed);
        return todo;
    }
}
