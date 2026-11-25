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
