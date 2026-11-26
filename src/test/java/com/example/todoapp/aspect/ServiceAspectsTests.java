package com.example.todoapp.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import com.example.todoapp.TodoMapper;
import com.example.todoapp.TodoService;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class ServiceAspectsTests {

    @Configuration
    @EnableAspectJAutoProxy
    static class AopConfiguration {
    }

    private AnnotationConfigApplicationContext context;
    private TodoMapper mapper;
    private TodoService service;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        logs = new ListAppender<>();
        logs.start();
        logger(LoggingAspect.class).addAppender(logs);
        logger(PerformanceAspect.class).addAppender(logs);
        mapper = mock(TodoMapper.class);
        context = new AnnotationConfigApplicationContext();
        context.register(AopConfiguration.class, LoggingAspect.class, PerformanceAspect.class);
        context.registerBean(TodoService.class, () -> new TodoService(mapper));
        context.refresh();
        service = context.getBean(TodoService.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
        logger(LoggingAspect.class).detachAppender(logs);
        logger(PerformanceAspect.class).detachAppender(logs);
        logs.stop();
    }

    @Test
    void logsArgumentsReturnValueAndDurationThroughServiceProxy() {
        when(mapper.count("sample", "work", true)).thenReturn(3L);

        assertThat(service.count("sample", "work", true)).isEqualTo(3L);

        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message)
                        .contains("Method started", "TodoService.count", "args=[sample, work, true]"))
                .anySatisfy(message -> assertThat(message)
                        .contains("Method completed", "TodoService.count", "result=3"))
                .anySatisfy(message -> assertThat(message)
                        .contains("Method execution time", "TodoService.count", "durationMs="));
    }

    @Test
    void logsVoidReturnValue() {
        service.togglePinned(1L);

        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message)
                        .contains("Method completed", "TodoService.togglePinned", "result=null"));
    }

    @Test
    void logsExceptionAndDurationAndPreservesOriginalException() {
        var failure = new IllegalStateException("Database unavailable");
        when(mapper.findById(7L)).thenThrow(failure);

        assertThat(assertThrows(IllegalStateException.class, () -> service.findById(7L)))
                .isSameAs(failure);

        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message)
                        .contains("Method started", "TodoService.findById", "args=[7]"))
                .anySatisfy(message -> assertThat(message)
                        .contains("Method failed", "TodoService.findById", "error=Database unavailable"))
                .anySatisfy(message -> assertThat(message)
                        .contains("Method execution time", "TodoService.findById", "durationMs="))
                .noneMatch(message -> message.contains("Method completed"));
    }

    private Logger logger(Class<?> type) {
        return (Logger) LoggerFactory.getLogger(type);
    }
}
