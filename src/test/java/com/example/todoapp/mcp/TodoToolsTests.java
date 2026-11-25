package com.example.todoapp.mcp;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.provider.tool.SyncMcpToolProvider;

import com.example.todoapp.Todo;
import com.example.todoapp.TodoService;
import com.example.todoapp.api.HolidayClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TodoToolsTests {
    private final TodoService service = mock(TodoService.class);
    private final TodoTools tools = new TodoTools(service, mock(HolidayClient.class));

    @Test
    void mcpRegistersEightToolsAndAcceptsOmittedDates() {
        when(service.search(null, null, "asc", null, null)).thenReturn(List.of());
        var specifications = new SyncMcpToolProvider(List.of(tools)).getToolSpecifications();
        assertEquals(8, specifications.size());
        var summary = specifications.stream()
                .filter(spec -> spec.tool().name().equals("summarize_week")).findFirst().orElseThrow();
        var required = summary.tool().inputSchema().get("required");
        assertTrue(required == null || ((List<?>) required).isEmpty());
        var result = summary.callHandler().apply(null,
                new io.modelcontextprotocol.spec.McpSchema.CallToolRequest("summarize_week", Map.of()));
        assertFalse(Boolean.TRUE.equals(result.isError()));
        verify(service).search(null, null, "asc", null, null);
    }

    @Test
    void countsOnlyUnfinishedPastDueItemsAsOverdue() {
        LocalDate today = LocalDate.now();
        when(service.search(null, null, "asc", null, null)).thenReturn(List.of(
                todo("デザイン", today.minusDays(1), false),
                todo("デザイン", today.minusDays(1), true),
                todo("資格", today, false),
                todo("資格", today.plusDays(1), false),
                todo("資格", null, false)));
        var result = tools.summarizeWeek(null, null);
        assertEquals(5, result.totalCount());
        assertEquals(2L, result.byCategory().get("デザイン"));
        assertEquals(3L, result.byCategory().get("資格"));
        assertEquals(0L, result.byCategory().get("就職活動"));
        assertEquals(1, result.overdueCount());
    }

    @Test
    void passesInclusiveDateBoundsAndRejectsInvalidRanges() {
        LocalDate from = LocalDate.of(2026, 4, 6);
        LocalDate to = LocalDate.of(2026, 4, 12);
        when(service.search(null, null, "asc", from, to)).thenReturn(List.of());
        when(service.search(null, null, "asc", from, null)).thenReturn(List.of());
        when(service.search(null, null, "asc", null, to)).thenReturn(List.of());
        assertEquals(0, tools.summarizeWeek("2026-04-06", "2026-04-12").totalCount());
        tools.summarizeWeek("2026-04-06", null);
        tools.summarizeWeek(null, "2026-04-12");
        verify(service).search(null, null, "asc", from, to);
        verify(service).search(null, null, "asc", from, null);
        verify(service).search(null, null, "asc", null, to);
        assertThrows(IllegalArgumentException.class,
                () -> tools.summarizeWeek("2026-04-12", "2026-04-06"));
        assertThrows(java.time.format.DateTimeParseException.class,
                () -> tools.summarizeWeek("invalid", null));
    }

    private Todo todo(String category, LocalDate dueDate, boolean completed) {
        Todo todo = new Todo();
        todo.setCategory(category);
        todo.setDueDate(dueDate);
        todo.setCompleted(completed);
        return todo;
    }
}
