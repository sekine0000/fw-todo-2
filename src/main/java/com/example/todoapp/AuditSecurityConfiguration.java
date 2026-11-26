package com.example.todoapp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
@org.springframework.transaction.annotation.EnableTransactionManagement(order = Integer.MAX_VALUE - 1)
public class AuditSecurityConfiguration {
    @Bean
    @org.springframework.core.annotation.Order(1)
    SecurityFilterChain auditSecurity(HttpSecurity http) throws Exception {
        // Scope security to the new administration area; existing app routes retain their behavior.
        return http.securityMatcher("/admin/**", "/login", "/logout")
                .authorizeHttpRequests(auth -> auth.requestMatchers("/login").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .formLogin(form -> form.defaultSuccessUrl("/admin/audit-logs", true))
                .build();
    }

    @Bean
    @org.springframework.core.annotation.Order(2)
    SecurityFilterChain applicationSecurity(HttpSecurity http) throws Exception {
        // Existing public API/MCP clients have no CSRF tokens. Still load the login session
        // so ToDo changes made by signed-in users carry their identity in the audit log.
        return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.requireCsrfProtectionMatcher(request ->
                        "POST".equals(request.getMethod())
                                && (request.getContextPath() + "/api/todo").equals(request.getRequestURI())))
                .build();
    }
}
