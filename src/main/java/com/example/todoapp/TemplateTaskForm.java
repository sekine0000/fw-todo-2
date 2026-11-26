package com.example.todoapp;

import java.time.LocalDate;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

@Data
public class TemplateTaskForm {
    @NotNull(message = "テンプレートを選択してください")
    private Long templateId;
    @NotBlank(message = "ジャンルを選択してください")
    @Pattern(regexp = "デザイン|マーケティング|プログラミング|資格|就職活動", message = "ジャンルを選択してください")
    private String category;
    @NotNull(message = "優先度を選択してください")
    @Min(1) @Max(3)
    private Integer priority = 2;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate dueDate;
}
