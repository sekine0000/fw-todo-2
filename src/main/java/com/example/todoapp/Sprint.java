package com.example.todoapp;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class Sprint {
    private Long id;
    @NotBlank @Size(max = 100)
    private String name;
    @NotNull @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;
    @NotNull @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;
    private List<@NotNull Long> taskIds = new ArrayList<>();
}
