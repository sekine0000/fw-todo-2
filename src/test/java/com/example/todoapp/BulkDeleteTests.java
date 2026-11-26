package com.example.todoapp;

import java.util.List;
import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BulkDeleteTests {
    @Test
    void selectedIdsAreBoundAsListAndFiltersArePreserved() throws Exception {
        TodoMapper mapper = mock(TodoMapper.class);
        when(mapper.deleteByIds(List.of(12L, 34L))).thenReturn(2);
        var mvc = MockMvcBuilders.standaloneSetup(new HomeController(new TodoService(mapper, org.mockito.Mockito.mock(com.example.todoapp.TodoHistoryMapper.class)))).build();
        mvc.perform(post("/todos/bulk-delete").param("ids", "12", "34")
                .param("keyword", "test").param("category", "work")
                .param("order", "desc").param("showCompleted", "true").param("page", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/todos?keyword=test&category=work&order=desc&showCompleted=true&page=2"))
                .andExpect(flash().attribute("message", "2件削除しました"));
        verify(mapper).deleteByIds(List.of(12L, 34L));
    }

    @Test
    void missingOrInvalidIdsNeverReachMapper() throws Exception {
        TodoMapper mapper = mock(TodoMapper.class);
        TodoService service = new TodoService(mapper, org.mockito.Mockito.mock(com.example.todoapp.TodoHistoryMapper.class));
        var mvc = MockMvcBuilders.standaloneSetup(new HomeController(service)).build();
        mvc.perform(post("/todos/bulk-delete")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/todos/bulk-delete").param("ids", "invalid"))
                .andExpect(status().isBadRequest());
        assertEquals(0, service.deleteByIds(List.of()));
        verifyNoInteractions(mapper);
    }

    @Test
    void sqlOnlySoftDeletesSelectedActiveRowsAndEmptyListIsSafe() throws Exception {
        Configuration configuration = new Configuration();
        try (var xml = getClass().getResourceAsStream("/mapper/TodoMapper.xml")) {
            new XMLMapperBuilder(xml, configuration, "mapper/TodoMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        var statement = configuration.getMappedStatement("com.example.todoapp.TodoMapper.deleteByIds");
        var params = new HashMap<String, Object>();
        params.put("ids", List.of(12L, 34L));
        var bound = statement.getBoundSql(params);
        String sql = bound.getSql().replaceAll("\\s+", " ");
        assertTrue(sql.contains("UPDATE todos SET deleted_at = CURRENT_TIMESTAMP"), sql);
        assertTrue(sql.contains("WHERE deleted_at IS NULL AND id IN"), sql);
        assertEquals(2, bound.getParameterMappings().size());
        assertEquals(12L, bound.getAdditionalParameter(bound.getParameterMappings().get(0).getProperty()));
        assertEquals(34L, bound.getAdditionalParameter(bound.getParameterMappings().get(1).getProperty()));
        params.put("ids", List.of());
        assertTrue(statement.getBoundSql(params).getSql().contains("AND 1 = 0"));
        params.put("ids", null);
        assertTrue(statement.getBoundSql(params).getSql().contains("AND 1 = 0"));
    }
}
