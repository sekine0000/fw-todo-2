package com.example.todoapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(KanbanController.class)
@Import(AuditSecurityConfiguration.class)
class KanbanSecurityTests {
    @MockitoBean KanbanService service;
    @Autowired org.springframework.web.context.WebApplicationContext context;
    MockMvc mvc;
    @org.junit.jupiter.api.BeforeEach void setup() {
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
    }
    @Test void patchRequiresValidCsrf() throws Exception {
        mvc.perform(patch("/api/todos/1/status").contentType("application/json").content("{\"status\":\"DONE\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/todos/1/status").with(csrf().useInvalidToken()).contentType("application/json").content("{\"status\":\"DONE\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
        mvc.perform(patch("/api/todos/1/status").with(csrf()).contentType("application/json").content("{\"status\":\"DONE\"}"))
                .andExpect(status().isNoContent());
        verify(service).move(1L, "DONE");
    }
    @Test void boardRendersEscapedCardsAndCsrfMetadata() throws Exception {
        var task = new KanbanTask(); task.setId(1L); task.setTitle("<script>unsafe</script>"); task.setStatus("TODO");
        when(service.list()).thenReturn(java.util.List.of(task));
        mvc.perform(get("/kanban")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")));
    }
}
