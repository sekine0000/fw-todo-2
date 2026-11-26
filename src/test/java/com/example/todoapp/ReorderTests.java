package com.example.todoapp;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.example.todoapp.api.TodoApiController;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReorderTests {
    @Test
    void apiPreservesUnselectedSlotsAndRejectsInvalidIds() throws Exception {
        var mapper = mock(TodoMapper.class);
        when(mapper.findOrderedIdsForUpdate()).thenReturn(List.of(1L, 2L, 3L, 4L));
        var service = new TodoService(mapper, mock(TodoHistoryMapper.class));
        var mvc = MockMvcBuilders.standaloneSetup(new TodoApiController(service)).build();
        mvc.perform(put("/api/todo/reorder").contentType("application/json")
                .content("{\"ids\":[4,2]}" )).andExpect(status().isNoContent());
        verify(mapper).updateDisplayOrder(List.of(1L, 4L, 3L, 2L));
        clearInvocations(mapper);
        for (String json : List.of("{}", "{\"ids\":[]}", "{\"ids\":[1,1]}",
                "{\"ids\":[null]}", "{\"ids\":[0]}")) {
            mvc.perform(put("/api/todo/reorder").contentType("application/json").content(json))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(mapper);
        mvc.perform(put("/api/todo/reorder").contentType("application/json")
                .content("{\"ids\":[99]}" )).andExpect(status().isConflict());
        verify(mapper, never()).updateDisplayOrder(anyList());
    }

    @Test
    void mapperPersistsOrderAndTransactionRollsBack() throws Exception {
        var ds = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:h2:mem:reorder;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE todos (id BIGINT PRIMARY KEY, parent_id BIGINT, deleted_at TIMESTAMP, display_order INT DEFAULT 2147483647)");
        jdbc.update("INSERT INTO todos(id) VALUES (1), (2), (3)");
        var factory = new org.mybatis.spring.SqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setMapperLocations(new org.springframework.core.io.ClassPathResource("mapper/TodoMapper.xml"));
        var mapper = new org.mybatis.spring.SqlSessionTemplate(factory.getObject()).getMapper(TodoMapper.class);
        var tx = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
        tx.executeWithoutResult(s -> mapper.updateDisplayOrder(List.of(3L, 1L, 2L)));
        assertEquals(List.of(3L, 1L, 2L), mapper.findOrderedIdsForUpdate());
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s -> {
            mapper.updateDisplayOrder(List.of(2L, 1L, 3L));
            throw new IllegalStateException("rollback");
        }));
        assertEquals(List.of(3L, 1L, 2L), mapper.findOrderedIdsForUpdate());
    }
}
