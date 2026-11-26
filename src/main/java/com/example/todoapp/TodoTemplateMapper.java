package com.example.todoapp;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TodoTemplateMapper {
    @Select("SELECT id, name, description FROM todo_templates ORDER BY id DESC")
    List<TodoTemplate> findAll();
    @Select("SELECT id, name, description FROM todo_templates WHERE id = #{id} FOR UPDATE")
    TodoTemplate findForUpdate(Long id);
    @Select("SELECT title FROM todo_template_items WHERE template_id = #{id} ORDER BY position")
    List<String> findItems(Long id);
    @Insert("INSERT INTO todo_templates(name, description) VALUES(#{name}, #{description})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(TodoTemplate template);
    @Insert("INSERT INTO todo_template_items(template_id, position, title) VALUES(#{templateId}, #{position}, #{title})")
    void insertItem(@Param("templateId") Long templateId, @Param("position") int position, @Param("title") String title);
    @Delete("DELETE FROM todo_templates WHERE id = #{id}")
    int delete(Long id);
}
