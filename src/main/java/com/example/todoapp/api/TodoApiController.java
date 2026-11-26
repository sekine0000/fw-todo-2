package com.example.todoapp.api;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.todoapp.Todo;
import com.example.todoapp.TodoService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * ToDoのCRUDとCSV出力のREST APIを提供する。入力には {@link TodoRequest} を使用する。
 *
 * @author ToDoアプリ開発チーム
 * @version 0.0.1
 * @since 0.0.1
 * @see com.example.todoapp.TodoService
 */
@RestController
public class TodoApiController {

    private final TodoService todoService;

    /**
     * 依存するサービスまたはマッパーを受け取って構築する。
     *
     * @param todoService ToDo業務サービス
     */
    public TodoApiController(TodoService todoService) {
        this.todoService = todoService;
    }

    /**
     * 検索条件に一致するToDo一覧を取得する。 期限の範囲で絞り込み、完了済みも含む。
     *
     * @param keyword タイトルの部分一致条件。空文字列または {@code null} で指定なし
     * @param category ジャンルの一致条件。空文字列または {@code null} で指定なし
     * @param order 期限の並び順。{@code desc} で降順、それ以外は昇順
     * @param from 期限の下限（当日を含む）。{@code null} で指定なし
     * @param to 期限の上限（当日を含む）。{@code null} で指定なし
     * @return 検索結果。該当しない場合は空リスト
     * @throws org.springframework.dao.DataAccessException ToDoのデータベース処理に失敗した場合
     */
    @GetMapping("/api/todos")
    public List<TodoDto> todos(
            @RequestParam(name = "keyword", defaultValue = "") String keyword,
            @RequestParam(name = "category", defaultValue = "") String category,
            @RequestParam(name = "order", defaultValue = "asc") String order,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return todoService.search(keyword, category, order, from, to).stream()
                .map(TodoDto::from)
                .toList();
    }

    /**
     * 検索結果をUTF-8のBOM付きCSVで返す。数式として解釈され得る値を保護する。
     *
     * @param keyword タイトルの部分一致条件。空文字列または {@code null} で指定なし
     * @param category ジャンルの一致条件。空文字列または {@code null} で指定なし
     * @param order 期限の並び順。{@code desc} で降順、それ以外は昇順
     * @param showCompleted 完了済みも含める場合は {@code true}
     * @param trash ごみ箱の指定。画面・APIでは {@code 1}、サービスでは {@code true}
     * @return 処理結果とHTTPステータスを含むレスポンス
     */
    @GetMapping(value = "/api/todos.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> csv(
            @RequestParam(name = "keyword", defaultValue = "") String keyword,
            @RequestParam(name = "category", defaultValue = "") String category,
            @RequestParam(name = "order", defaultValue = "asc") String order,
            @RequestParam(name = "showCompleted", defaultValue = "false") boolean showCompleted,
            @RequestParam(name = "trash", defaultValue = "") String trash) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        appendCsvRow(csv, "やること", "ジャンル", "優先度", "期限", "状態");
        for (Todo todo : todoService.searchCsv(keyword, category,
                "desc".equals(order) ? "desc" : "asc", showCompleted, "1".equals(trash))) {
            String priority = Integer.valueOf(1).equals(todo.getPriority()) ? "高"
                    : Integer.valueOf(2).equals(todo.getPriority()) ? "中"
                    : Integer.valueOf(3).equals(todo.getPriority()) ? "低" : "";
            String state = Boolean.TRUE.equals(todo.getCompleted()) ? "完了" : "未完了";
            if (Boolean.TRUE.equals(todo.getCompleted()) && todo.getCompletedAt() != null) {
                state += " " + todo.getCompletedAt().toLocalDate();
            }
            appendCsvRow(csv, todo.getTitle(), todo.getCategory(), priority,
                    todo.getDueDate() == null ? "" : todo.getDueDate().toString(), state);
        }
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv;charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=\"todos.csv\"")
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 数式対策と引用符のエスケープを行いCSV行を追記する。
     *
     * @param csv 出力先バッファー
     * @param values CSVに出力する値。{@code null} は空文字列に変換する
     */
    private static void appendCsvRow(StringBuilder csv, String... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) csv.append(',');
            String value = values[i] == null ? "" : values[i];
            // Guard spreadsheet formulas, including leading whitespace/control characters.
            String leading = value.stripLeading();
            if (!leading.isEmpty() && "=+-@".indexOf(leading.charAt(0)) >= 0
                    || !value.isEmpty() && "\t\r\n".indexOf(value.charAt(0)) >= 0) {
                value = "'" + value;
            }
            csv.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        csv.append("\r\n");
    }

    /**
     * 未削除のToDoをIDで取得する。 対象が存在しない場合はHTTP 404を返す。
     *
     * @param id 対象ToDoのID
     * @return 処理結果とHTTPステータスを含むレスポンス
     * @throws org.springframework.dao.DataAccessException ToDoのデータベース処理に失敗した場合
     */
    @GetMapping("/api/todos/{id}")
    public ResponseEntity<?> findById(@PathVariable("id") Long id) {
        Todo todo = todoService.findById(id);
        if (todo == null) {
            return notFound(id);
        }
        return ResponseEntity.ok(TodoDto.from(todo));
    }

    /**
     * ToDoを新規作成する。 作成先URIとHTTP 201を返す。検証エラーは {@link #badRequest} で処理する。
     *
     * @param request リクエストデータ
     * @return 処理結果とHTTPステータスを含むレスポンス
     * @throws org.springframework.dao.DataAccessException ToDoのデータベース処理に失敗した場合
     */
    @PostMapping("/api/todos")
    public ResponseEntity<TodoDto> create(@Valid @RequestBody TodoRequest request) {
        Todo todo = request.toTodo();
        todoService.create(todo);
        Todo created = todoService.findById(todo.getId());
        return ResponseEntity.created(URI.create("/api/todos/" + todo.getId()))
                .body(TodoDto.from(created));
    }

    /**
     * ToDoを更新する。 対象が存在しない場合はHTTP 404を返す。
     *
     * @param id 対象ToDoのID
     * @param request リクエストデータ
     * @return 処理結果とHTTPステータスを含むレスポンス
     * @throws org.springframework.dao.DataAccessException ToDoのデータベース処理に失敗した場合
     */
    @PutMapping("/api/todos/{id}")
    public ResponseEntity<?> update(@PathVariable("id") Long id, @Valid @RequestBody TodoRequest request) {
        Todo existing = todoService.findById(id);
        if (existing == null) {
            return notFound(id);
        }
        Todo todo = request.toTodo();
        todo.setId(id);
        todoService.update(todo);
        return ResponseEntity.ok(TodoDto.from(todoService.findById(id)));
    }

    /**
     * ToDoを論理削除する。 対象が存在しない場合はHTTP 404を返す。
     *
     * @param id 対象ToDoのID
     * @return 処理結果とHTTPステータスを含むレスポンス
     * @throws org.springframework.dao.DataAccessException ToDoのデータベース処理に失敗した場合
     */
    @DeleteMapping("/api/todos/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") Long id) {
        Todo existing = todoService.findById(id);
        if (existing == null) {
            return notFound(id);
        }
        todoService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 入力検証エラーを項目別の詳細を含むHTTP 400に変換する。
     *
     * @param exception 入力検証で発生した例外
     * @param request リクエストデータ
     * @return 処理結果とHTTPステータスを含むレスポンス
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> badRequest(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "入力に誤りがあります");
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Bad Request");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errors", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(), "message", error.getDefaultMessage()))
                .toList());
        return ResponseEntity.badRequest().body(problem);
    }

    /**
     * 対象が存在しないことを示すHTTP 404を構築する。
     *
     * @param id 対象ToDoのID
     * @return 処理結果とHTTPステータスを含むレスポンス
     */
    private ResponseEntity<ProblemDetail> notFound(Long id) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "Todo with id " + id + " was not found.");
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Not Found");
        problem.setInstance(URI.create("/api/todos/" + id));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }
}
