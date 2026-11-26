package com.example.todoapp;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;
import lombok.Data;

@Mapper
public interface SprintMapper {
    @Select("SELECT id, name, start_date, end_date FROM sprint ORDER BY start_date DESC, id DESC")
    List<Sprint> findAll();

    @Select("SELECT id, name, start_date, end_date FROM sprint WHERE id = #{id}")
    Sprint findById(Long id);

    @Insert("INSERT INTO sprint (name, start_date, end_date) VALUES (#{name}, #{startDate}, #{endDate})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Sprint sprint);

    @Select("SELECT id, title FROM todos WHERE parent_id IS NULL AND deleted_at IS NULL AND archived = FALSE ORDER BY id")
    List<Task> availableTasks();

    @Insert("INSERT INTO sprint_task (sprint_id, todo_id) SELECT #{sprintId}, id FROM todos "
            + "WHERE id = #{todoId} AND parent_id IS NULL AND deleted_at IS NULL AND archived = FALSE")
    int addTask(@Param("sprintId") Long sprintId, @Param("todoId") Long todoId);

    // Keep the membership even when a task is archived or deleted, so the denominator stays fixed.
    @Select("SELECT st.todo_id AS id, t.title, CASE WHEN t.completed = TRUE THEN t.completed_at ELSE NULL END AS completed_at "
            + "FROM sprint_task st LEFT JOIN todos t ON t.id = st.todo_id WHERE st.sprint_id = #{id}")
    List<Task> tasks(Long id);

    @Data
    class Task {
        private Long id;
        private String title;
        private LocalDateTime completedAt;
    }
}
