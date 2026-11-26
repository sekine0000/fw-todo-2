package com.example.todoapp;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuditLogMapper {
    @Insert("INSERT INTO audit_log (todo_id, operation, event_type) VALUES (#{todoId}, #{operation}, 'ATTEMPT')")
    void insert(@Param("todoId") Long todoId, @Param("operation") String operation);
}
