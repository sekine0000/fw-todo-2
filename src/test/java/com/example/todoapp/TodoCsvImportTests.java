package com.example.todoapp;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TodoCsvImportTests {
    private MockMultipartFile csv(String data) {
        return new MockMultipartFile("file", "tasks.csv", "text/csv", data.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void importsOnlyValidRowsWithBomReorderedHeadersAndQuotedMultilineCells() {
        TodoService todos = mock(TodoService.class);
        var result = new TodoCsvImportService(todos).importFile(csv(
                "\uFEFFpriority,category,title,detail,dueDate\r\n"
                + "1,資格,\"試験,準備\",\"一行目\r\n二行目\"\"引用\"\"\",2028-02-29\r\n"
                + "2,資格,,メモ,2026-02-30\r\n"
                + "3,デザイン,次のタスク,,\r\n"));
        assertEquals(2, result.importedCount());
        assertEquals(2, result.errors().size());
        assertTrue(result.errors().stream().allMatch(e -> e.line() == 4));
        org.mockito.ArgumentCaptor<List<Todo>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(todos).createBatch(captor.capture());
        assertEquals("試験,準備", captor.getValue().getFirst().getTitle());
        assertEquals("一行目\n二行目\"引用\"", captor.getValue().getFirst().getDetail());
        assertFalse(captor.getValue().getFirst().getCompleted());
    }

    @Test
    void rejectsInvalidHeadersBeforeWriting() {
        for (String data : List.of("", "\uFEFF", "title,title,priority\nx,x,1", "title,category\nx,資格")) {
            TodoService todos = mock(TodoService.class);
            assertEquals(0, new TodoCsvImportService(todos).importFile(csv(data)).importedCount());
            verifyNoInteractions(todos);
        }
    }

    @Test
    void reportsSyntaxColumnAndValueErrorsAndDoesNotInsertEmptyBatch() {
        TodoMapper mapper = mock(TodoMapper.class);
        var todos = new TodoService(mapper, mock(TodoHistoryMapper.class));
        var result = new TodoCsvImportService(todos).importFile(csv(
                "title,category,priority,dueDate\n"
                + "\"bad\"x,資格,1,\n"
                + "bad,資格,1\n"
                + "bad,不明,4,2026-02-30\n"
                + "\"unclosed,資格,1,\n"));
        assertEquals(0, result.importedCount());
        assertEquals(List.of(2, 3, 4, 4, 4, 5), result.errors().stream().map(TodoCsvImportService.RowError::line).toList());
        verifyNoInteractions(mapper);
    }

    @Test
    void uploadRedirectsAndDownloadedTemplateCanBeImported() throws Exception {
        TodoService todos = mock(TodoService.class);
        var service = new TodoCsvImportService(todos);
        var mvc = MockMvcBuilders.standaloneSetup(new TodoCsvImportController(service)).build();
        byte[] template = mvc.perform(get("/todos/import/template.csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"todo-import-template.csv\""))
                .andReturn().getResponse().getContentAsByteArray();
        mvc.perform(multipart("/todos/import").file(new MockMultipartFile("file", template)))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/todos/import"))
                .andExpect(flash().attributeExists("result"));
        verify(todos).createBatch(argThat(list -> list.size() == 1));
    }

    @Test
    void importPageRendersResultAndEscapesErrorText() throws Exception {
        var resolver = new org.thymeleaf.templateresolver.ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setCharacterEncoding("UTF-8");
        var engine = new org.thymeleaf.spring6.SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        var views = new org.thymeleaf.spring6.view.ThymeleafViewResolver();
        views.setTemplateEngine(engine); views.setCharacterEncoding("UTF-8");
        var mvc = MockMvcBuilders.standaloneSetup(new TodoCsvImportController(mock(TodoCsvImportService.class)))
                .setViewResolvers(views).build();
        mvc.perform(get("/todos/import").flashAttr("result", new TodoCsvImportService.ImportResult(2,
                List.of(new TodoCsvImportService.RowError(4, "<script>")))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("2件を登録しました")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")));
    }

    @Test
    void batchMapperInsertsRowsAndReturnsIdsForHistory() throws Exception {
        var dataSource = new org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder()
                .generateUniqueName(true).setType(org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType.H2).build();
        try {
            var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
            jdbc.execute("CREATE TABLE todos (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, title VARCHAR(255), detail VARCHAR(255), category VARCHAR(255), priority INT, due_date DATE, completed BOOLEAN)");
            var factory = new org.mybatis.spring.SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setMapperLocations(new org.springframework.core.io.ClassPathResource("mapper/TodoMapper.xml"));
            try (var session = factory.getObject().openSession(true)) {
                var mapper = session.getMapper(TodoMapper.class);
                var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(i -> {
                    var todo = new Todo();
                    todo.setTitle("タスク" + i); todo.setCategory("資格"); todo.setPriority(2);
                    return todo;
                }).toList();
                assertEquals(2, mapper.insertBatch(tasks));
                assertNotNull(tasks.get(0).getId());
                assertNotNull(tasks.get(1).getId());
                assertNotEquals(tasks.get(0).getId(), tasks.get(1).getId());
                assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM todos WHERE completed = FALSE", Integer.class));
            }
        } finally { dataSource.shutdown(); }
    }
}
