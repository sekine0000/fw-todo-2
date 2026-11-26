package com.example.todoapp;

import java.text.Normalizer;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

@Service
public class SmartDateParser {
    private static final Pattern RELATIVE = Pattern.compile("([0-9]+)日(後|前)");
    private static final Pattern WEEKDAY = Pattern.compile("(今週|来週|再来週)([月火水木金土日])曜(?:日)?");
    private static final Pattern JAPANESE_DATE = Pattern.compile("(?:([0-9]{4})年)?([0-9]{1,2})月([0-9]{1,2})日");
    private final Clock clock;

    public SmartDateParser() {
        this(Clock.system(ZoneId.of("Asia/Tokyo")));
    }

    public SmartDateParser(Clock clock) {
        this.clock = clock;
    }

    /** Uses Japan time; weeks start on Monday and omitted years mean the current year. */
    public LocalDate parse(String input) {
        if (input == null || input.isBlank() || input.length() > 100) {
            throw new IllegalArgumentException("日付を100文字以内で入力してください。");
        }
        String value = Normalizer.normalize(input, Normalizer.Form.NFKC).strip();
        LocalDate today = LocalDate.now(clock);
        try {
            switch (value) {
                case "今日": return today;
                case "明日": return today.plusDays(1);
                case "明後日", "あさって": return today.plusDays(2);
                case "昨日": return today.minusDays(1);
                default: break;
            }
            Matcher relative = RELATIVE.matcher(value);
            if (relative.matches()) {
                long days = Long.parseLong(relative.group(1));
                return today.plusDays(relative.group(2).equals("後") ? days : -days);
            }
            Matcher weekday = WEEKDAY.matcher(value);
            if (weekday.matches()) {
                int weeks = switch (weekday.group(1)) {
                    case "来週" -> 1;
                    case "再来週" -> 2;
                    default -> 0;
                };
                DayOfWeek day = DayOfWeek.of("月火水木金土日".indexOf(weekday.group(2)) + 1);
                return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .plusWeeks(weeks).with(TemporalAdjusters.nextOrSame(day));
            }
            Matcher date = JAPANESE_DATE.matcher(value);
            if (date.matches()) {
                return LocalDate.of(date.group(1) == null ? today.getYear() : Integer.parseInt(date.group(1)),
                        Integer.parseInt(date.group(2)), Integer.parseInt(date.group(3)));
            }
            if (value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                return LocalDate.parse(value);
            }
        } catch (DateTimeException | ArithmeticException | NumberFormatException ex) {
            throw new IllegalArgumentException("存在しない日付、または範囲外の日付です。");
        }
        throw new IllegalArgumentException("日付を解釈できません。例：明日、3日後、来週金曜、12月25日");
    }
}
