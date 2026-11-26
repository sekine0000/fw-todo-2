package com.example.todoapp;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import lombok.Data;

@Mapper
public interface ProgressMapper {
    @Select("SELECT COALESCE(NULLIF(TRIM(category), ''), '未分類') AS category, "
            + "COUNT(*) AS total, SUM(CASE WHEN completed = TRUE THEN 1 ELSE 0 END) AS completed "
            + "FROM todos WHERE deleted_at IS NULL AND archived = FALSE AND parent_id IS NULL "
            + "GROUP BY COALESCE(NULLIF(TRIM(category), ''), '未分類') ORDER BY category")
    List<CategoryProgress> categoryProgress();

    @Data
    class CategoryProgress {
        private String category;
        private long total;
        private long completed;
    }
}
