package com.example.todoapp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import lombok.Data;

@Mapper
public interface DashboardMapper {
    String SCOPE = " deleted_at IS NULL AND archived = FALSE AND parent_id IS NULL ";

    @Select("SELECT COUNT(*) AS total, COALESCE(SUM(CASE WHEN completed = TRUE THEN 1 ELSE 0 END), 0) AS completed, "
            + "COALESCE(SUM(CASE WHEN completed = FALSE AND due_date < #{today} THEN 1 ELSE 0 END), 0) AS overdue "
            + "FROM todos WHERE " + SCOPE)
    Summary summary(LocalDate today);

    @Select("SELECT CAST(completed_at AS DATE) AS completion_date, COUNT(*) AS task_count FROM todos WHERE " + SCOPE
            + "AND completed = TRUE AND completed_at >= #{start} AND completed_at < #{end} "
            + "GROUP BY CAST(completed_at AS DATE) ORDER BY completion_date")
    List<DailyCount> dailyCounts(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COALESCE(NULLIF(TRIM(category), ''), '未分類') AS category, COUNT(*) AS task_count "
            + "FROM todos WHERE " + SCOPE
            + "GROUP BY COALESCE(NULLIF(TRIM(category), ''), '未分類') ORDER BY task_count DESC, category ASC")
    List<CategoryCount> categoryCounts();

    @Data
    class Summary {
        private long total;
        private long completed;
        private long overdue;
        public long getIncomplete() { return total - completed; }
    }

    @Data
    class DailyCount {
        private LocalDate completionDate;
        private long taskCount;
    }

    @Data
    class CategoryCount {
        private String category;
        private long taskCount;
    }
}
