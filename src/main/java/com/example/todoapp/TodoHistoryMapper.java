package com.example.todoapp;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TodoHistoryMapper {
    @Insert("INSERT INTO todo_history (todo_id, operation) VALUES (#{todoId}, #{operation})")
    void insert(@Param("todoId") Long todoId, @Param("operation") String operation);
}
