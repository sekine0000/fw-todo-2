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

    @Transactional(readOnly = true)
    public Heatmap heatmap(LocalDate today) {
        LocalDate start = today.minusDays(364);
        Map<String, Long> daily = new LinkedHashMap<>();
        for (int day = 0; day < 365; day++) daily.put(start.plusDays(day).toString(), 0L);
        for (var row : mapper.dailyCounts(start.atStartOfDay(), today.plusDays(1).atStartOfDay())) {
            String date = row.getCompletionDate().toString();
            if (daily.containsKey(date)) daily.put(date, row.getTaskCount());
        }
        return new Heatmap(daily, daily.values().stream().mapToLong(Long::longValue).sum(),
                daily.values().stream().filter(count -> count > 0).count(),
                daily.values().stream().mapToLong(Long::longValue).max().orElse(0));
    }

    public record Heatmap(Map<String, Long> daily, long total, long activeDays, long maxDaily) { }
}
