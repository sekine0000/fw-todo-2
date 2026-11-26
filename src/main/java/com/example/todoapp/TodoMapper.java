package com.example.todoapp;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TodoMapper {

    List<Todo> searchCsv(@Param("keyword") String keyword, @Param("category") String category,
            @Param("order") String order, @Param("showCompleted") boolean showCompleted,
            @Param("trash") boolean trash);

    List<Todo> search(@Param("keyword") String keyword, @Param("category") String category,
            @Param("order") String order, @Param("from") LocalDate from, @Param("to") LocalDate to,
            @Param("showCompleted") boolean showCompleted);

    Todo findById(Long id);
    Todo findForUpdate(Long id);
    List<Todo> findSubtasks(Long parentId);
    List<Todo> findSubtasksForUpdate(Long parentId);
    void removeSubtask(@Param("parentId") Long parentId, @Param("id") Long id);
    @org.apache.ibatis.annotations.Select("SELECT * FROM todos WHERE id = #{id}")
    Todo findForAudit(Long id);

    long count(@Param("keyword") String keyword, @Param("category") String category,
            @Param("showCompleted") boolean showCompleted);

    List<Todo> searchPage(@Param("keyword") String keyword, @Param("category") String category,
            @Param("order") String order, @Param("showCompleted") boolean showCompleted,
            @Param("offset") long offset);

    void insert(Todo todo);
    int insertBatch(@Param("todos") List<Todo> todos);
    List<Long> findOrderedIdsForUpdate();
    int updateDisplayOrder(@Param("ids") List<Long> ids);

    void update(Todo todo);

    void togglePinned(Long id);

    void deleteById(Long id);

    int deleteByIds(@Param("ids") List<Long> ids);

    int updateStatusByIds(@Param("ids") List<Long> ids, @Param("completed") boolean completed);

    long countTrash(@Param("keyword") String keyword, @Param("category") String category);

    List<Todo> searchTrashPage(@Param("keyword") String keyword, @Param("category") String category,
            @Param("order") String order, @Param("offset") long offset);

    void restoreById(Long id);
}
