package com.example.todoapp.api;

import com.example.todoapp.SmartDateParser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DateParseController {
    private final SmartDateParser parser;

    public DateParseController(SmartDateParser parser) {
        this.parser = parser;
    }

    @GetMapping("/api/date/parse")
    public ResponseEntity<Result> parse(@RequestParam(name = "input", required = false) String input) {
        try {
            return ResponseEntity.ok(new Result(parser.parse(input).toString(), null));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new Result(null, ex.getMessage()));
        }
    }

    public record Result(String date, String error) {}
}
