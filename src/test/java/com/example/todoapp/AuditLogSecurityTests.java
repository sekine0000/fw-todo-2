package com.example.todoapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuditLogController.class)
@Import(AuditSecurityConfiguration.class)
class AuditLogSecurityTests {
    @Autowired MockMvc mvc;
    @MockitoBean AuditLogService logs;
    @Autowired org.springframework.web.context.WebApplicationContext context;

    @org.junit.jupiter.api.BeforeEach
    void configureSecurity() {
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test void anonymousMustLogin() throws Exception {
        mvc.perform(get("/admin/audit-logs")).andExpect(status().is3xxRedirection());
        verifyNoInteractions(logs);
    }

    @Test @WithMockUser(roles = "USER")
    void ordinaryUserCannotReadLogs() throws Exception {
        mvc.perform(get("/admin/audit-logs")).andExpect(status().isForbidden());
        verifyNoInteractions(logs);
    }

    @Test @WithMockUser(roles = "ADMIN")
    void administratorCanSearchAndRenderEscapedJson() throws Exception {
        var entry = new AuditLog();
        entry.setAction("UPDATE");
        entry.setNewValue("{\"title\":\"<script>alert(1)</script>\"}");
        when(logs.search("UPDATE", 1L, "admin", 0L)).thenReturn(java.util.List.of(entry));
        mvc.perform(get("/admin/audit-logs").param("action", "UPDATE")
                .param("entityId", "1").param("userId", "admin"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")));
    }
}
