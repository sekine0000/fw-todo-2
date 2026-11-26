package com.example.todoapp;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.web.server.ResponseStatusException;

class KanbanTests {
    @Test void migrationAndStatusPersistencePreserveCompletionDatesAndHideInactiveTasks() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:kanban;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE todos (id BIGINT PRIMARY KEY, parent_id BIGINT, title VARCHAR(255), category VARCHAR(255), due_date DATE, completed BOOLEAN DEFAULT FALSE, completed_at TIMESTAMP, deleted_at TIMESTAMP, archived BOOLEAN DEFAULT FALSE, display_order INT DEFAULT 0)");
        var initializer = new KanbanSchemaInitializer(ds);
        initializer.afterPropertiesSet();
        initializer.afterPropertiesSet();
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(ds);
        var sf = factory.getObject();
        sf.getConfiguration().setMapUnderscoreToCamelCase(true);
        sf.getConfiguration().addMapper(KanbanMapper.class);
        var mapper = new SqlSessionTemplate(sf).getMapper(KanbanMapper.class);
        jdbc.update("INSERT INTO todos (id, title) VALUES (1, 'task'), (2, 'archived'), (3, 'deleted'), (4, 'child')");
        jdbc.update("UPDATE todos SET archived = TRUE WHERE id = 2");
        jdbc.update("UPDATE todos SET deleted_at = CURRENT_TIMESTAMP WHERE id = 3");
        jdbc.update("UPDATE todos SET parent_id = 1 WHERE id = 4");
        assertThat(mapper.list()).extracting(KanbanTask::getStatus).containsExactly("TODO");
        assertThat(mapper.updateStatus(1L, true, false)).isOne();
        assertThat(mapper.list().getFirst().getStatus()).isEqualTo("IN_PROGRESS");
        mapper.updateStatus(1L, false, true);
        assertThat(mapper.list().getFirst().getStatus()).isEqualTo("DONE");
        assertThat(jdbc.queryForObject("SELECT completed_at FROM todos WHERE id = 1", java.sql.Timestamp.class)).isNotNull();
        mapper.updateStatus(1L, false, false);
        assertThat(jdbc.queryForObject("SELECT completed_at FROM todos WHERE id = 1", java.sql.Timestamp.class)).isNull();
        assertThat(mapper.updateStatus(2L, true, false)).isZero();
    }

    @Test void invalidMissingAndInconsistentParentMovesAreRejected() {
        var mapper = mock(KanbanMapper.class);
        var todos = mock(TodoMapper.class);
        var history = mock(TodoHistoryMapper.class);
        var service = new KanbanService(mapper, todos, history);
        assertThatThrownBy(() -> service.move(1L, "invalid")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.move(1L, null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.move(1L, "DONE")).isInstanceOf(ResponseStatusException.class);
        var task = new Todo(); task.setId(1L);
        when(todos.findForUpdate(1L)).thenReturn(task);
        var child = new Todo(); child.setCompleted(false);
        when(todos.findSubtasksForUpdate(1L)).thenReturn(java.util.List.of(child));
        assertThatThrownBy(() -> service.move(1L, "DONE")).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(mapper, history);
        when(mapper.updateStatus(1L, true, false)).thenReturn(1);
        service.move(1L, "IN_PROGRESS");
        verify(history).insert(1L, "UPDATE");
    }
}
