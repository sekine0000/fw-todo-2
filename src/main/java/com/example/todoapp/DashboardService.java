package com.example.todoapp;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {
    private final DashboardMapper mapper;

    public DashboardService(DashboardMapper mapper) { this.mapper = mapper; }

    @Transactional(readOnly = true)
    public Data load(LocalDate today) {
        Map<String, Long> daily = new LinkedHashMap<>();
        for (int days = 6; days >= 0; days--) daily.put(today.minusDays(days).toString(), 0L);
        for (var row : mapper.dailyCounts(today.minusDays(6).atStartOfDay(), today.plusDays(1).atStartOfDay())) {
            daily.put(row.getCompletionDate().toString(), row.getTaskCount());
        }
        return new Data(mapper.summary(today), daily, mapper.categoryCounts());
    }

    public record Data(DashboardMapper.Summary summary, Map<String, Long> daily,
            List<DashboardMapper.CategoryCount> categories) { }
}
