package com.example.todoapp;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * ToDoエンティティ兼画面入力フォーム。Bean Validationで入力を検証する。アクセサーと等価比較、文字列表現はLombokで生成する。
 *
 * @author ToDoアプリ開発チーム
 * @version 0.0.1
 * @since 0.0.1
 * @see com.example.todoapp.TodoService
 */
@Data
public class Todo {

    /** 登録時に採番される識別子。 */
    private Long id;

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
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate dueDate;

    /** {@code true} で完了。 */
    private Boolean completed;
    /** ピン留め状態。初期値は {@code false}。 */
    private Boolean pinned = false;
    /** 論理削除日時。{@code null} は未削除。 */
    private LocalDateTime deletedAt;
    /** 完了日時。 */
    private LocalDateTime completedAt;
    /** 作成日時。 */
    private LocalDateTime createdAt;
    /** 最終更新日時。 */
    private LocalDateTime updatedAt;
}
