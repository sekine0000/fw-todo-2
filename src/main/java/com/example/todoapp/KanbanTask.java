package com.example.todoapp;

@lombok.Data
public class KanbanTask {
    private Long id;
    private String title;
    private String category;
    private java.time.LocalDate dueDate;
    private String status;
}
