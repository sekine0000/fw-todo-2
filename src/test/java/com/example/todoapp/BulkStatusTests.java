package com.example.todoapp;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BulkStatusTests {
    @Test
    void controllerBindsStatusAndPreservesFilters() throws Exception {
        var service = mock(TodoService.class);
        when(service.updateStatusByIds(List.of(1L, 2L), true)).thenReturn(2);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new HomeController(service)).build();
        mvc.perform(post("/todos/bulk-status").param("ids", "1", "2").param("completed", "true")
                .param("keyword", "test").param("category", "work").param("order", "desc")
                .param("showCompleted", "true").param("page", "2"))
                .andExpect(redirectedUrl("/todos?keyword=test&category=work&order=desc&showCompleted=true&page=2"))
                .andExpect(flash().attribute("message", "2件を完了にしました"));
        mvc.perform(post("/todos/bulk-status").param("completed", "false"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/todos/bulk-status").param("completed", "invalid"))
                .andExpect(status().isBadRequest());
        verify(service).updateStatusByIds(null, false);
    }

    @Test
    void serviceDeduplicatesAndKeepsParentAggregation() {
        var mapper = mock(TodoMapper.class);
        var history = mock(TodoHistoryMapper.class);
        var service = new TodoService(mapper, history);
        assertEquals(0, service.updateStatusByIds(null, true));
        assertEquals(0, service.updateStatusByIds(List.of(), false));
        verifyNoInteractions(mapper, history);
        var todo = new Todo();
        todo.setId(1L);
        todo.setCompleted(false);
        when(mapper.findById(1L)).thenReturn(todo);
        when(mapper.findForUpdate(1L)).thenReturn(todo);
        when(mapper.updateStatusByIds(List.of(1L), true)).thenReturn(1);
        assertEquals(1, service.updateStatusByIds(List.of(1L, 1L, 99L), true));
        verify(mapper).updateStatusByIds(List.of(1L), true);
        verify(history).insert(1L, "UPDATE");
        var child = new Todo();
        child.setCompleted(false);
        when(mapper.findSubtasksForUpdate(1L)).thenReturn(List.of(child));
        assertEquals(0, service.updateStatusByIds(List.of(1L), true));
    }

    @Test
    void actualSqlUpdatesOnlySelectedActiveRowsAndMaintainsCompletionTime() throws Exception {
        var ds = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:h2:mem:bulkstatus;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE todos (id BIGINT PRIMARY KEY, completed BOOLEAN, completed_at TIMESTAMP, deleted_at TIMESTAMP)");
        jdbc.update("INSERT INTO todos VALUES (1, FALSE, NULL, NULL), (2, FALSE, NULL, NULL), (3, FALSE, NULL, CURRENT_TIMESTAMP)");
        var factory = new org.mybatis.spring.SqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setMapperLocations(new org.springframework.core.io.ClassPathResource("mapper/TodoMapper.xml"));
        var mapper = new org.mybatis.spring.SqlSessionTemplate(factory.getObject()).getMapper(TodoMapper.class);
        assertEquals(1, mapper.updateStatusByIds(List.of(1L, 3L, 99L), true));
        var timestamp = jdbc.queryForObject("SELECT completed_at FROM todos WHERE id = 1", java.sql.Timestamp.class);
        assertNotNull(timestamp);
        assertEquals(0, mapper.updateStatusByIds(List.of(1L), true));
        assertEquals(timestamp, jdbc.queryForObject("SELECT completed_at FROM todos WHERE id = 1", java.sql.Timestamp.class));
        assertEquals(0, mapper.updateStatusByIds(List.of(), true));
        assertEquals(0, mapper.updateStatusByIds(null, true));
        assertEquals(1, mapper.updateStatusByIds(List.of(1L), false));
        assertNull(jdbc.queryForObject("SELECT completed_at FROM todos WHERE id = 1", java.sql.Timestamp.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM todos WHERE completed = TRUE", Integer.class));
    }
}
