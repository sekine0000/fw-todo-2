package com.example.todoapp;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuditLogMapper {
    @Insert("INSERT INTO audit_logs (action, entity_type, entity_id, user_id, old_value, new_value, ip_address, status) VALUES (#{action}, #{entityType}, #{entityId}, #{userId}, #{oldValue}, #{newValue}, #{ipAddress}, #{status})")
    @org.apache.ibatis.annotations.Options(useGeneratedKeys = true, keyProperty = "id")
    void insertLog(AuditLog log);

    @org.apache.ibatis.annotations.Update("UPDATE audit_logs SET entity_id=#{entityId}, new_value=#{newValue}, status=#{status} WHERE id=#{id}")
    void complete(AuditLog log);

    @org.apache.ibatis.annotations.Select("""
        <script>SELECT * FROM audit_logs <where>
        <if test="action != null and action != ''">AND action=#{action}</if>
        <if test="entityId != null">AND entity_id=#{entityId}</if>
        <if test="userId != null and userId != ''">AND user_id=#{userId}</if>
        </where> ORDER BY created_at DESC, id DESC LIMIT 51 OFFSET #{offset}</script>
        """)
    java.util.List<AuditLog> search(@Param("action") String action, @Param("entityId") Long entityId,
            @Param("userId") String userId, @Param("offset") long offset);
}
