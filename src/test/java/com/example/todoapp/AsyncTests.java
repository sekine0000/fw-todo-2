package com.example.todoapp;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.example.todoapp.async.*;
import com.example.todoapp.api.TodoReportController;

class AsyncTests {
    @Test
    void reportRunsOnWorkerAndPropagatesFailure() throws Exception {
        var todos = mock(TodoService.class);
        var thread = new AtomicReference<String>();
        var done = new Todo();
        done.setCompleted(true);
        when(todos.search("", "", "asc")).thenAnswer(invocation -> {
            thread.set(Thread.currentThread().getName());
            return List.of(done, new Todo());
        });
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(AsyncConfiguration.class, TodoReportService.class);
            context.registerBean(TodoService.class, () -> todos);
            context.refresh();
            var reports = context.getBean(TodoReportService.class);
            assertThat(reports.generate("", "").get(2, TimeUnit.SECONDS))
                    .isEqualTo(new TodoReportService.TodoReport(2, 1, 1));
            assertThat(thread.get()).startsWith("task-");
            when(todos.search("", "", "asc")).thenThrow(new IllegalStateException("database failed"));
            assertThatThrownBy(() -> reports.generate("", "").get(2, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void controllerMapsTimeoutAndFailure() {
        var reports = mock(TodoReportService.class);
        var controller = new TodoReportController(reports, 1);
        when(reports.generate("", "")).thenReturn(new CompletableFuture<>());
        assertThatThrownBy(() -> controller.report("", ""))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT));
        when(reports.generate("", "")).thenReturn(CompletableFuture.failedFuture(new IllegalStateException()));
        assertThatThrownBy(() -> controller.report("", ""))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @Test
    void reportRouteReturnsJsonAndRejectsBusyExecutor() throws Exception {
        var reports = mock(TodoReportService.class);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new TodoReportController(reports, 100)).build();
        when(reports.generate("work", "")).thenReturn(CompletableFuture.completedFuture(
                new TodoReportService.TodoReport(3, 1, 2)));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/todos/report").param("keyword", "work"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .json("{\"total\":3,\"completed\":1,\"pending\":2}"));
        when(reports.generate("", "")).thenThrow(new org.springframework.core.task.TaskRejectedException("busy"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/todos/report"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isServiceUnavailable());
    }

    @Test
    void interruptedWaitRestoresInterruptFlag() {
        var reports = mock(TodoReportService.class);
        when(reports.generate("", "")).thenReturn(new CompletableFuture<>());
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> new TodoReportController(reports, 100).report("", ""))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void emailUsesSeparatePoolAndVoidFailureReachesHandler() throws Exception {
        var failure = new CountDownLatch(1);
        var sent = new CountDownLatch(1);
        var thread = new AtomicReference<String>();
        var sender = mock(JavaMailSender.class);
        doAnswer(invocation -> {
            thread.set(Thread.currentThread().getName());
            sent.countDown();
            throw new IllegalStateException("SMTP failed");
        }).when(sender).send(any(SimpleMailMessage.class));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(AsyncConfiguration.class, () -> new AsyncConfiguration() {
                @Override
                public org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
                    var delegate = super.getAsyncUncaughtExceptionHandler();
                    return (error, method, args) -> {
                        delegate.handleUncaughtException(error, method, args);
                        failure.countDown();
                    };
                }
            });
            context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                    "test", java.util.Map.of("app.mail.from", "todo@example.com")));
            context.registerBean(JavaMailSender.class, () -> sender);
            context.register(EmailService.class);
            context.refresh();
            context.getBean(EmailService.class).send("recipient@example.com", "Report", "Ready");
            assertThat(sent.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(failure.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(thread.get()).startsWith("email-");
        }
    }
}
