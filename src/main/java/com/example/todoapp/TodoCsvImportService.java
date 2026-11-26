package com.example.todoapp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TodoCsvImportService {
    static final List<String> HEADERS = List.of("title", "detail", "category", "priority", "dueDate");
    private static final Set<String> CATEGORIES = Set.of("デザイン", "マーケティング", "プログラミング", "資格", "就職活動");
    private final TodoService todos;

    public TodoCsvImportService(TodoService todos) {
        this.todos = todos;
    }

    public record RowError(int line, String message) {}
    public record ImportResult(int importedCount, List<RowError> errors) {}
    private record CsvRow(int line, List<String> cells, String error) {}

    public ImportResult importFile(MultipartFile file) {
        var errors = new ArrayList<RowError>();
        if (file == null || file.isEmpty()) {
            return new ImportResult(0, List.of(new RowError(1, "CSVファイルを選択してください")));
        }
        List<CsvRow> rows;
        var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try (var reader = new BufferedReader(new InputStreamReader(file.getInputStream(), decoder))) {
            rows = parse(reader);
        } catch (IOException e) {
            return new ImportResult(0, List.of(new RowError(1, "ファイルをUTF-8のCSVとして読み込めません")));
        }
        if (rows.isEmpty()) return new ImportResult(0, List.of(new RowError(1, "ヘッダー行がありません")));
        CsvRow header = rows.getFirst();
        var columns = new HashMap<String, Integer>();
        for (int i = 0; i < header.cells().size(); i++) {
            String name = header.cells().get(i).strip();
            if (!HEADERS.contains(name) || columns.put(name, i) != null) {
                errors.add(new RowError(header.line(), "不明または重複したヘッダー: " + name));
            }
        }
        if (header.error() != null) errors.add(new RowError(header.line(), header.error()));
        for (String required : List.of("title", "category", "priority")) {
            if (!columns.containsKey(required)) errors.add(new RowError(header.line(), "必須ヘッダーがありません: " + required));
        }
        if (!errors.isEmpty()) return new ImportResult(0, List.copyOf(errors));
        var valid = new ArrayList<Todo>();
        for (CsvRow row : rows.subList(1, rows.size())) {
            if (row.error() != null) {
                errors.add(new RowError(row.line(), row.error()));
                continue;
            }
            if (row.cells().size() != header.cells().size()) {
                errors.add(new RowError(row.line(), "列数がヘッダーと一致しません"));
                continue;
            }
            int before = errors.size();
            var todo = new Todo();
            todo.setTitle(cell(row, columns, "title").strip());
            todo.setDetail(cell(row, columns, "detail"));
            todo.setCategory(cell(row, columns, "category").strip());
            if (todo.getTitle().isBlank()) errors.add(new RowError(row.line(), "titleは必須です"));
            if (todo.getTitle().length() > 255) errors.add(new RowError(row.line(), "titleは255文字以内です"));
            if (todo.getDetail().length() > 255) errors.add(new RowError(row.line(), "detailは255文字以内です"));
            if (!CATEGORIES.contains(todo.getCategory())) errors.add(new RowError(row.line(), "categoryは必須です。指定のジャンルを入力してください"));
            String priority = cell(row, columns, "priority").strip();
            if (!priority.matches("[123]")) errors.add(new RowError(row.line(), "priorityは必須です。1・2・3のいずれかを入力してください"));
            else todo.setPriority(Integer.valueOf(priority));
            String date = cell(row, columns, "dueDate").strip();
            if (!date.isEmpty()) {
                try {
                    if (!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new DateTimeParseException("format", date, 0);
                    LocalDate dueDate = LocalDate.parse(date);
                    if (dueDate.getYear() < 1000) throw new DateTimeParseException("range", date, 0);
                    todo.setDueDate(dueDate);
                } catch (DateTimeParseException e) {
                    errors.add(new RowError(row.line(), "dueDateは実在する日付（yyyy-MM-dd、1000〜9999年）を入力してください"));
                }
            }
            todo.setCompleted(false);
            if (errors.size() == before) valid.add(todo);
        }
        todos.createBatch(valid);
        return new ImportResult(valid.size(), List.copyOf(errors));
    }

    private static String cell(CsvRow row, Map<String, Integer> columns, String name) {
        Integer index = columns.get(name);
        return index == null ? "" : row.cells().get(index);
    }

    /** 物理行の開始番号を保持し、引用符内の改行・カンマ・二重引用符を扱う。 */
    private static List<CsvRow> parse(BufferedReader reader) throws IOException {
        var rows = new ArrayList<CsvRow>();
        var cells = new ArrayList<String>();
        var value = new StringBuilder();
        boolean quoted = false, closed = false, first = true, touched = false;
        String error = null;
        int line = 1, start = 1;
        int ch;
        while ((ch = reader.read()) != -1) {
            if (first) {
                first = false;
                if (ch == '\uFEFF') continue;
            }
            char c = (char) ch;
            if (c == '\r') {
                reader.mark(1);
                if (reader.read() != '\n') reader.reset();
                c = '\n';
            }
            if (quoted) {
                if (c == '"') {
                    reader.mark(1);
                    if (reader.read() == '"') value.append('"');
                    else { reader.reset(); quoted = false; closed = true; }
                } else value.append(c);
                if (c == '\n') line++;
                continue;
            }
            if (c == ',' || c == '\n') {
                cells.add(value.toString());
                value.setLength(0);
                closed = false;
                if (c == '\n') {
                    if (touched || cells.size() > 1) rows.add(new CsvRow(start, List.copyOf(cells), error));
                    cells.clear();
                    touched = false;
                    error = null;
                    start = ++line;
                } else touched = true;
            } else {
                touched = true;
                if (c == '"' && value.isEmpty() && !closed) quoted = true;
                else {
                    if (c == '"' || closed) error = "ダブルクォートの形式が不正です";
                    value.append(c);
                }
            }
        }
        if (touched || !cells.isEmpty() || !value.isEmpty()) {
            cells.add(value.toString());
            rows.add(new CsvRow(start, List.copyOf(cells), quoted ? "ダブルクォートが閉じられていません" : error));
        }
        return rows;
    }
}
