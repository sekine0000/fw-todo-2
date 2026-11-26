package com.example.todoapp;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TaskDependencyMapper {
    record Edge(Long prerequisiteId, Long successorId) {}

    @Select("SELECT id FROM task_dependency_lock WHERE id = 1 FOR UPDATE")
    Integer lockGraph();

    // Include deleted tasks when detecting cycles, so restoring a task cannot create a cycle.
    @Select("SELECT prerequisite_id, successor_id FROM task_dependencies ORDER BY prerequisite_id, successor_id")
    List<Edge> edges();

    @Select("SELECT * FROM todos WHERE deleted_at IS NULL ORDER BY id")
    List<Todo> tasks();

    @Insert("INSERT INTO task_dependencies (prerequisite_id, successor_id) VALUES (#{prerequisiteId}, #{successorId})")
    void insert(@Param("prerequisiteId") Long prerequisiteId, @Param("successorId") Long successorId);

    @Delete("DELETE FROM task_dependencies WHERE prerequisite_id = #{prerequisiteId} AND successor_id = #{successorId}")
    int delete(@Param("prerequisiteId") Long prerequisiteId, @Param("successorId") Long successorId);
}
