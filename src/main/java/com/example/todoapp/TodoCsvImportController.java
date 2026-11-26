package com.example.todoapp;

import java.nio.charset.StandardCharsets;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Controller
public class TodoCsvImportController {
    private final TodoCsvImportService service;

    public TodoCsvImportController(TodoCsvImportService service) { this.service = service; }

    @GetMapping("/todos/import")
    public String form() { return "todo-import"; }

    @PostMapping("/todos/import")
    public String upload(@RequestParam(name = "file", required = false) MultipartFile file,
            RedirectAttributes redirect) {
        redirect.addFlashAttribute("result", service.importFile(file));
        return "redirect:/todos/import";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String tooLarge(Model model) {
        model.addAttribute("uploadError", "ファイルサイズが上限を超えています。1MB以下のCSVを選択してください。");
        return "todo-import";
    }

    @GetMapping("/todos/import/template.csv")
    public ResponseEntity<byte[]> template() {
        String csv = "\uFEFF" + String.join(",", TodoCsvImportService.HEADERS) + "\r\n"
                + "サンプルタスク,メモ,プログラミング,2,\r\n";
        return ResponseEntity.ok().header("Content-Type", "text/csv;charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=\"todo-import-template.csv\"")
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
