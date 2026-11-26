package com.example.todoapp;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BurndownService {
    private final SprintMapper mapper;
    public BurndownService(SprintMapper mapper) { this.mapper = mapper; }

    @Transactional
    public Long create(Sprint sprint) {
        if (sprint.getName() == null || sprint.getName().isBlank() || sprint.getName().length() > 100
                || sprint.getStartDate() == null || sprint.getEndDate() == null
                || sprint.getEndDate().isBefore(sprint.getStartDate())
                || ChronoUnit.DAYS.between(sprint.getStartDate(), sprint.getEndDate()) > 365) {
            throw new IllegalArgumentException("名前と期間を確認してください。期間は開始日から最大366日です。");
        }
        mapper.insert(sprint);
        for (Long id : new LinkedHashSet<>(sprint.getTaskIds())) {
            if (id == null || mapper.addTask(sprint.getId(), id) != 1) {
                throw new IllegalArgumentException("選択したタスクが見つかりません。再選択してください。");
            }
        }
        return sprint.getId();
    }

    public Data load(Sprint sprint, LocalDate today) {
        var tasks = mapper.tasks(sprint.getId());
        long days = ChronoUnit.DAYS.between(sprint.getStartDate(), sprint.getEndDate()) + 1;
        var labels = new ArrayList<String>();
        var ideal = new ArrayList<Double>();
        var actual = new ArrayList<Long>();
        // The baseline is the beginning of the sprint; subsequent points are end-of-day counts.
        labels.add(sprint.getStartDate().minusDays(1).toString());
        ideal.add((double) tasks.size());
        actual.add(today.isBefore(sprint.getStartDate()) ? null : remaining(tasks, sprint.getStartDate().minusDays(1)));
        for (int i = 0; i < days; i++) {
            LocalDate date = sprint.getStartDate().plusDays(i);
            labels.add(date.toString());
            ideal.add(tasks.size() * (days - i - 1.0) / days);
            actual.add(date.isAfter(today) ? null : remaining(tasks, date));
        }
        int index = (int) Math.max(0, Math.min(days, ChronoUnit.DAYS.between(sprint.getStartDate(), today) + 1));
        long left = actual.get(index) == null ? tasks.size() : actual.get(index);
        double delta = left - ideal.get(index);
        String status = today.isBefore(sprint.getStartDate()) ? "開始前"
                : Math.abs(delta) < 0.000001 ? "順調" : delta > 0 ? "遅延" : "前倒し";
        String tone = status.equals("遅延") ? "late" : status.equals("前倒し") ? "ahead" : "normal";
        return new Data(labels, ideal, actual, tasks.size(), left, status, tone);
    }

    private long remaining(List<SprintMapper.Task> tasks, LocalDate date) {
        return tasks.stream().filter(t -> t.getCompletedAt() == null
                || t.getCompletedAt().toLocalDate().isAfter(date)).count();
    }

    public record Data(List<String> labels, List<Double> ideal, List<Long> actual,
                       long total, long remaining, String status, String tone) {}
}
