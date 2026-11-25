package com.example.todoapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class TodoappApplicationTests {

    @Autowired WebApplicationContext context;
    @Autowired TodoMapper mapper;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired com.example.todoapp.mcp.TodoTools tools;

    @Test
    @Transactional
    void deletedTodoIsHiddenEverywhereAndCanBeRestored() throws Exception {
        Todo todo = new Todo();
        todo.setTitle("trash-test-" + System.nanoTime());
        todo.setCategory("プログラミング");
        todo.setPriority(2);
        todo.setCompleted(true);
        mapper.insert(todo);
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/todos/{id}/delete", todo.getId()))
                .andExpect(status().is3xxRedirection());
        assertNotNull(jdbc.queryForObject("SELECT deleted_at FROM todos WHERE id = ?",
                LocalDateTime.class, todo.getId()));
        assertNull(mapper.findById(todo.getId()));
        assertEquals(0, mapper.count(todo.getTitle(), "", true));
        assertTrue(mapper.searchPage(todo.getTitle(), "", "asc", true, 0).isEmpty());
        assertTrue(tools.listTodos(todo.getTitle(), null, null, null).isEmpty());
        assertNull(tools.getTodo(todo.getId()));
        mvc.perform(get("/api/todos").param("keyword", todo.getTitle()))
                .andExpect(content().json("[]"));
        mvc.perform(get("/api/todos/{id}", todo.getId())).andExpect(status().isNotFound());
        for (String trash : List.of("", "0", "true", "2")) {
            mvc.perform(get("/todos").param("keyword", todo.getTitle()).param("trash", trash)
                    .param("showCompleted", "true"))
                    .andExpect(model().attribute("todos", org.hamcrest.Matchers.empty()));
        }
        mvc.perform(get("/todos").param("keyword", todo.getTitle()).param("trash", "1"))
                .andExpect(model().attribute("todos", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("戻す")));
        todo.setTitle(todo.getTitle() + "-changed");
        mapper.update(todo);
        assertEquals(1, mapper.countTrash(todo.getTitle().replace("-changed", ""), ""));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/todos/{id}/restore", todo.getId()).param("trash", "1"))
                .andExpect(redirectedUrl("/todos?trash=1"));
        assertNull(jdbc.queryForObject("SELECT deleted_at FROM todos WHERE id = ?",
                LocalDateTime.class, todo.getId()));
        assertNotNull(tools.getTodo(todo.getId()));
        assertEquals(0, mapper.countTrash(todo.getTitle().replace("-changed", ""), ""));
    }

    @Test
    @Transactional
    void completionDateFollowsStateChangesAndAppearsInListAndApi() throws Exception {
        Todo todo = new Todo();
        todo.setTitle("completion-test-" + System.nanoTime());
        todo.setCategory("プログラミング");
        todo.setPriority(2);
        todo.setCompleted(false);
        todo.setCompletedAt(LocalDateTime.of(2000, 1, 1, 0, 0));
        mapper.insert(todo);
        assertNull(mapper.findById(todo.getId()).getCompletedAt());

        todo.setTitle(todo.getTitle() + "-edited");
        mapper.update(todo);
        assertNull(mapper.findById(todo.getId()).getCompletedAt());

        LocalDateTime before = LocalDateTime.now().minusSeconds(2);
        todo.setCompleted(true);
        mapper.update(todo);
        LocalDateTime completedAt = mapper.findById(todo.getId()).getCompletedAt();
        assertNotNull(completedAt);
        assertFalse(completedAt.isBefore(before));
        assertFalse(completedAt.isAfter(LocalDateTime.now().plusSeconds(2)));

        todo.setTitle(todo.getTitle() + "-completed-edit");
        mapper.update(todo);
        assertEquals(completedAt, mapper.findById(todo.getId()).getCompletedAt());

        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        mvc.perform(get("/todos").param("keyword", todo.getTitle()).param("showCompleted", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(completedAt.toLocalDate().toString())));
        mvc.perform(get("/api/todos").param("keyword", todo.getTitle()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].completedAt").isNotEmpty());

        todo.setCompleted(false);
        mapper.update(todo);
        assertNull(mapper.findById(todo.getId()).getCompletedAt());
        mvc.perform(get("/api/todos/{id}", todo.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedAt").value(org.hamcrest.Matchers.nullValue()));

        todo.setCompleted(true);
        mapper.update(todo);
        assertNotNull(mapper.findById(todo.getId()).getCompletedAt());

        Todo createdCompleted = new Todo();
        createdCompleted.setTitle("created-completed-test");
        createdCompleted.setCategory("デザイン");
        createdCompleted.setPriority(2);
        createdCompleted.setCompleted(true);
        mapper.insert(createdCompleted);
        assertNull(mapper.findById(createdCompleted.getId()).getCompletedAt());
        mapper.update(createdCompleted);
        assertNull(mapper.findById(createdCompleted.getId()).getCompletedAt());
    }

    @Test
    @Transactional
    void listPagesKeepFiltersAndOrderAndRenderNavigation() throws Exception {
        String keyword = "pagination-test-" + System.nanoTime();
        for (int i = 1; i <= 15; i++) {
            Todo todo = new Todo();
            todo.setTitle(keyword + String.format("%02d", i));
            todo.setCategory("プログラミング");
            todo.setPriority(2);
            todo.setDueDate(LocalDate.of(2026, 12, i));
            todo.setCompleted(i == 15);
            mapper.insert(todo);
        }
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        var first = mvc.perform(get("/todos").param("keyword", keyword)
                .param("category", "プログラミング").param("order", "desc")
                .param("showCompleted", "true"))
                .andExpect(status().isOk()).andExpect(model().attribute("totalPages", 2L))
                .andReturn();
        @SuppressWarnings("unchecked")
        List<Todo> firstTodos = (List<Todo>) first.getModelAndView().getModel().get("todos");
        assertEquals(10, firstTodos.size());
        assertEquals(keyword + "15", firstTodos.getFirst().getTitle());
        String html = first.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(html.contains("showCompleted=true&amp;page=2"));
        assertTrue(html.contains("order=desc"));
        assertTrue(html.contains("aria-current=\"page\""));

        var second = mvc.perform(get("/todos").param("keyword", keyword)
                .param("category", "プログラミング").param("order", "desc")
                .param("showCompleted", "true").param("page", "2"))
                .andExpect(status().isOk()).andExpect(model().attribute("currentPage", 2L))
                .andReturn();
        @SuppressWarnings("unchecked")
        List<Todo> secondTodos = (List<Todo>) second.getModelAndView().getModel().get("todos");
        assertEquals(5, secondTodos.size());
        assertEquals(keyword + "05", secondTodos.getFirst().getTitle());
        assertTrue(secondTodos.stream().noneMatch(firstTodos::contains));
        assertEquals(14, mapper.count(keyword, "プログラミング", false));
        assertEquals(0, mapper.count(keyword, "デザイン", true));
        assertEquals(15, mapper.search(keyword, "プログラミング", "asc", null, null, true).size());

        mvc.perform(get("/todos").param("keyword", keyword).param("page", "999"))
                .andExpect(model().attribute("currentPage", 2L));
        mvc.perform(get("/todos").param("keyword", keyword).param("page", "0"))
                .andExpect(model().attribute("currentPage", 1L));
        mvc.perform(get("/todos").param("keyword", keyword + "absent"))
                .andExpect(model().attribute("totalPages", 1L))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("aria-label=\"ページ選択\""))));
    }

	@Test
	void contextLoads() {
	}

}
