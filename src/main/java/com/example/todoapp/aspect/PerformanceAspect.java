package com.example.todoapp.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Aspect
@Component
@Order(0)
@Slf4j
public class PerformanceAspect {

    @Around("com.example.todoapp.aspect.LoggingAspect.serviceMethods()")
    public Object measureExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long startedAt = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            double elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0;
            log.info("Method execution time: method={}, durationMs={}",
                    joinPoint.getSignature().toShortString(), elapsedMs);
        }
    }
}
