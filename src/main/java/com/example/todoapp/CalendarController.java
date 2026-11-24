package com.example.todoapp;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class CalendarController {

    @GetMapping("/calendar")
    public String calendar(@RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month, Model model) {
        YearMonth now = YearMonth.now();
        YearMonth selected;
        try {
            selected = YearMonth.of(year == null ? now.getYear() : year,
                    month == null ? now.getMonthValue() : month);
        } catch (DateTimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "年月が正しくありません", exception);
        }

        LocalDate firstDay = selected.atDay(1);
        LocalDate lastDay = selected.atEndOfMonth();
        int offset = firstDay.getDayOfWeek().getValue() % 7;
        int cellCount = ((offset + selected.lengthOfMonth() + 6) / 7) * 7;
        List<List<LocalDate>> weeks = new ArrayList<>();
        for (int index = 0; index < cellCount; index += 7) {
            List<LocalDate> week = new ArrayList<>();
            for (int column = 0; column < 7; column++) {
                int day = index + column - offset + 1;
                week.add(day >= 1 && day <= selected.lengthOfMonth() ? selected.atDay(day) : null);
            }
            weeks.add(week);
        }

        model.addAttribute("year", selected.getYear());
        model.addAttribute("month", selected.getMonthValue());
        model.addAttribute("firstDay", firstDay);
        model.addAttribute("lastDay", lastDay);
        model.addAttribute("weeks", weeks);
        model.addAttribute("previousMonth", selected.minusMonths(1));
        model.addAttribute("nextMonth", selected.plusMonths(1));
        return "calendar";
    }
}
