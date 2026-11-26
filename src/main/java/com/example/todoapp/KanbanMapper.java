package com.example.todoapp;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface KanbanMapper {
    @Select("SELECT id, title, category, due_date, CASE WHEN completed THEN 'DONE' WHEN in_progress THEN 'IN_PROGRESS' ELSE 'TODO' END AS status FROM todos WHERE deleted_at IS NULL AND archived = FALSE AND parent_id IS NULL ORDER BY display_order, id")
    List<KanbanTask> list();

    @Update("UPDATE todos SET in_progress = #{inProgress}, completed_at = CASE WHEN #{completed} = FALSE THEN NULL WHEN completed = FALSE THEN CURRENT_TIMESTAMP ELSE completed_at END, completed = #{completed} WHERE id = #{id} AND deleted_at IS NULL AND archived = FALSE AND parent_id IS NULL")
    int updateStatus(@Param("id") Long id, @Param("inProgress") boolean inProgress, @Param("completed") boolean completed);
}
