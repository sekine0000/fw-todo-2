package com.example.todoapp;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TodoArchiveMapper {
    @Select("SELECT * FROM todos WHERE archived = TRUE AND deleted_at IS NULL AND parent_id IS NULL ORDER BY archived_at DESC, id DESC")
    List<Todo> findArchived();

    @Select("SELECT * FROM todos WHERE id = #{id} AND deleted_at IS NULL FOR UPDATE")
    Todo lock(Long id);

    @Select("SELECT * FROM todos WHERE parent_id = #{id} AND deleted_at IS NULL ORDER BY id FOR UPDATE")
    List<Todo> lockChildren(Long id);

    @Update("UPDATE todos SET archived = #{archived}, archived_at = #{archivedAt} WHERE (id = #{id} OR parent_id = #{id}) AND deleted_at IS NULL")
    int setArchived(@Param("id") Long id, @Param("archived") boolean archived,
            @Param("archivedAt") LocalDateTime archivedAt);

    @Select("SELECT id FROM todos WHERE parent_id IS NULL AND deleted_at IS NULL AND archived = FALSE AND completed = TRUE AND completed_at <= #{cutoff} ORDER BY id")
    List<Long> findExpired(LocalDateTime cutoff);

    @Delete("DELETE FROM todos WHERE id = #{id} AND archived = TRUE AND deleted_at IS NULL AND parent_id IS NULL")
    int deleteArchived(Long id);
}
