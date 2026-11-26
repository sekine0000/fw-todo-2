package com.example.todoapp;

import java.util.ArrayList;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TodoTemplate {
    private Long id;
    @NotBlank(message = "名前を入力してください")
    @Size(max = 255, message = "名前は255文字以内で入力してください")
    private String name;
    @Size(max = 2000, message = "説明は2000文字以内で入力してください")
    private String description;
    @jakarta.validation.constraints.NotNull(message = "項目を入力してください")
    @Size(min = 1, max = 100, message = "項目は1〜100件で入力してください")
    private List<@NotBlank(message = "タスクタイトルを入力してください")
            @Size(max = 255, message = "タスクタイトルは255文字以内で入力してください") String> items = new ArrayList<>(List.of(""));
}
