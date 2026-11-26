package com.example.todoapp;

import com.example.todoapp.api.TodoApiController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TodoApiController.class)
@Import(AuditSecurityConfiguration.class)
class QuickAddTests {
    @Autowired WebApplicationContext context;
    @MockitoBean TodoService service;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity()).build();
    }

    @Test void requiresCsrf() throws Exception {
        mvc.perform(post("/api/todo").contentType("application/json").content("{\"title\":\"Task\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void rejectsInvalidTitles() throws Exception {
        for (String title : new String[]{"", "   ", "x".repeat(256)}) {
            mvc.perform(post("/api/todo").with(csrf().asHeader()).contentType("application/json")
                            .content("{\"title\":\"" + title + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("title"));
        }
        verifyNoInteractions(service);
    }

    @Test void createsWithTitleOnly() throws Exception {
        doAnswer(invocation -> {
            Todo todo = invocation.getArgument(0);
            todo.setId(42L);
            when(service.findById(42L)).thenReturn(todo);
            return null;
        }).when(service).create(any(Todo.class));
        mvc.perform(post("/api/todo").with(csrf().asHeader()).contentType("application/json")
                        .content("{\"title\":\" Task \"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/todos/42"))
                .andExpect(jsonPath("$.title").value("Task"))
                .andExpect(jsonPath("$.category").value("デザイン"))
                .andExpect(jsonPath("$.priority").value(2))
                .andExpect(jsonPath("$.completed").value(false));
    }
}
