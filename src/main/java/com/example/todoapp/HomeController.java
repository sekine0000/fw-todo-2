package com.example.todoapp;

import java.util.List;

import org.springframework.validation.BindingResult;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

/**
 * ToDoの画面エンドポイント。画面フォームには {@link Todo} を使用する。
 *
 * @author ToDoアプリ開発チーム
 * @version 0.0.1
 * @since 0.0.1
 * @see com.example.todoapp.TodoService
 */
@Controller
public class HomeController {

    private final TodoService todoService;

    /**
     * 依存するサービスまたはマッパーを受け取って構築する。
     *
     * @param todoService ToDo業務サービス
     */
    public HomeController(TodoService todoService) {
        this.todoService = todoService;
    }

    /** タスクを複製して、一覧画面へ移動する。 */
    @PostMapping("/todo/{id}/duplicate")
    public String duplicate(@PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean includeSubtasks,
            RedirectAttributes redirectAttributes) {
        todoService.duplicate(id, includeSubtasks);
        redirectAttributes.addFlashAttribute("message", "複製しました");
        return "redirect:/todos";
    }

    /**
     * ホーム画面を表示する。
     *
     * @param model 画面に渡す属性
     * @return 表示するテンプレート名またはリダイレクト先
     */
    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("title", "やること管理");
        return "index";
    }

    /**
     * 一覧またはごみ箱を10件単位で表示する。ページ番号を有効範囲に補正する。
     *
     * @param keyword タイトルの部分一致条件。空文字列または {@code null} で指定なし
     * @param category ジャンルの一致条件。空文字列または {@code null} で指定なし
     * @param order 期限の並び順。{@code desc} で降順、それ以外は昇順
     * @param showCompleted 完了済みも含める場合は {@code true}
     * @param page ページ番号（1始まり）
     * @param trash ごみ箱の指定。画面・APIでは {@code 1}、サービスでは {@code true}
     * @param model 画面に渡す属性
     * @return 表示するテンプレート名またはリダイレクト先
     * @throws org.springframework.dao.DataAccessException ToDoのデータベース処理に失敗した場合
     */
    @GetMapping("/todos")
    public String todos(@RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "manual") String order,
            @RequestParam(defaultValue = "false") boolean showCompleted,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "") String trash, Model model) {
        boolean showTrash = "1".equals(trash);
        String selectedOrder = "manual".equals(order) ? "manual" : ("desc".equals(order) ? "desc" : "asc");
        long total = showTrash ? todoService.countTrash(keyword, category)
                : todoService.count(keyword, category, showCompleted);
        long totalPages = Math.max(1, (total + 9) / 10);
        long currentPage = Math.min(Math.max(1, page), totalPages);

        model.addAttribute("trash", showTrash);
        model.addAttribute("todos", showTrash
                ? todoService.searchTrashPage(keyword, category, selectedOrder, (currentPage - 1) * 10)
                : todoService.searchPage(keyword, category, selectedOrder, showCompleted,
                (currentPage - 1) * 10));
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("keyword", keyword);
        model.addAttribute("category", category);
        model.addAttribute("order", selectedOrder);
        model.addAttribute("showCompleted", showCompleted);
        return "todos";
    }

    /**
     * 新規入力画面を表示する。
     *
     * @param model 画面に渡す属性
     * @return 表示するテンプレート名またはリダイレクト先
     */
    @GetMapping("/todos/new")
    public String create(Model model) {
        model.addAttribute("todo", new Todo());
        return "create";
    }

    /**
     * ピン留め状態を反転する。
     *
     * @param id 対象ToDoのID
     * @param keyword タイトルの部分一致条件。空文字列または {@code null} で指定なし
     * @param category ジャンルの一致条件。空文字列または {@code null} で指定なし
     * @param order 期限の並び順。{@code desc} で降順、それ以外は昇順
     * @param showCompleted 完了済みも含める場合は {@code true}
     * @param page ページ番号（1始まり）
     * @param redirectAttributes 遷移先への検索条件と通知メッセージ
     * @return 表示するテンプレート名またはリダイレクト先
     * @throws org.springframework.dao.DataAccessException ToDoのデータベース処理に失敗した場合
     */
    @PostMapping("/todos/{id}/pin")
    public String togglePinned(@PathVariable Long id,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "manual") String order,
            @RequestParam(defaultValue = "false") boolean showCompleted,
            @RequestParam(defaultValue = "1") int page,
            RedirectAttributes redirectAttributes) {
        todoService.togglePinned(id);
        redirectAttributes.addAttribute("keyword", keyword);
        redirectAttributes.addAttribute("category", category);
        redirectAttributes.addAttribute("order", "manual".equals(order) ? "manual" : ("desc".equals(order) ? "desc" : "asc"));
        redirectAttributes.addAttribute("showCompleted", showCompleted);
        redirectAttributes.addAttribute("page", page);
        return "redirect:/todos";
    }

    /**
     * 入力を検証し、登録確認画面または新規入力画面を表示する。
     *
     * @param todo 入力または更新対象の {@link com.example.todoapp.Todo}
     * @param bindingResult フォームの入力検証結果
     * @return 表示するテンプレート名またはリダイレクト先
     */
    @PostMapping("/todos/confirm")
    public String confirm(@Valid @ModelAttribute Todo todo, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "create";
        }

        return "create-confirm";
    }

    /**
     * 入力を保持して新規入力画面に戻る。
     *
     * @param todo 入力または更新対象の {@link com.example.todoapp.Todo}
     * @return 表示するテンプレート名またはリダイレクト先
     */
    @PostMapping("/todos/new")
    public String rewrite(@ModelAttribute Todo todo) {
        return "create";
    }

    /**
     * ToDoを登録し、通知を設定して一覧へ戻る。
     *
     * @param todo 入力または更新対象の {@link com.example.todoapp.Todo}
     * @param redirectAttributes 遷移先への検索条件と通知メッセージ
     * @return 表示するテンプレート名またはリダイレクト先
     * @throws org.springframework.dao.DataAccessException ToDoのデータベース処理に失敗した場合
     */
    @PostMapping("/todos")
    public String insert(@ModelAttribute Todo todo, RedirectAttributes redirectAttributes) {
        todoService.create(todo);
        redirectAttributes.addFlashAttribute("message", "登録しました");
        return "redirect:/todos";
    }

    /**
     * 編集画面を表示する。対象がなければ通知して一覧へ戻る。
     *
     * @param id 対象ToDoのID
     * @param model 画面に渡す属性
     * @param redirectAttributes 遷移先への検索条件と通知メッセージ
     * @return 表示するテンプレート名またはリダイレクト先
     * @throws org.springframework.dao.DataAccessException ToDoのデータベース処理に失敗した場合
     */
    @GetMapping("/todos/{id}/edit")
    public String edit(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Todo todo = todoService.findById(id);

        if (todo == null) {
            redirectAttributes.addFlashAttribute("message", "見つかりませんでした");
            return "redirect:/todos";
        }

        model.addAttribute("todo", todo);
        return "edit";
    }

    /**
     * IDを設定して入力を検証し、編集確認画面または編集画面を表示する。
     *
     * @param id 対象ToDoのID
     * @param todo 入力または更新対象の {@link com.example.todoapp.Todo}
     * @param bindingResult フォームの入力検証結果
     * @return 表示するテンプレート名またはリダイレクト先
     */
    @PostMapping("/todos/{id}/confirm")
    public String editConfirm(@PathVariable Long id, @Valid @ModelAttribute Todo todo,
            BindingResult bindingResult) {
        todo.setId(id);

        if (bindingResult.hasErrors()) {
            return "edit";
        }

        return "edit-confirm";
    }

    /**
     * IDを設定して編集画面に戻る。
     *
     * @param id 対象ToDoのID
     * @param todo 入力または更新対象の {@link com.example.todoapp.Todo}
     * @return 表示するテンプレート名またはリダイレクト先
     */
    @PostMapping("/todos/{id}/edit")
    public String editRewrite(@PathVariable Long id, @ModelAttribute Todo todo) {
        todo.setId(id);
        return "edit";
    }

    /**
     * ToDoを更新する。
     *
     * @param id 対象ToDoのID
     * @param todo 入力または更新対象の {@link com.example.todoapp.Todo}
     * @param redirectAttributes 遷移先への検索条件と通知メッセージ
     * @return 表示するテンプレート名またはリダイレクト先
     * @throws org.springframework.dao.DataAccessException ToDoのデータベース処理に失敗した場合
     */
    @PostMapping("/todos/{id}")
    public String update(@PathVariable Long id, @ModelAttribute Todo todo,
            RedirectAttributes redirectAttributes) {
        todo.setId(id);
        todoService.update(todo);
        redirectAttributes.addFlashAttribute("message", "保存しました");
        return "redirect:/todos";
    }

    /**
     * 削除確認画面を表示する。対象がなければ通知して一覧へ戻る。
     *
     * @param id 対象ToDoのID
     * @param model 画面に渡す属性
     * @param redirectAttributes 遷移先への検索条件と通知メッセージ
     * @return 表示するテンプレート名またはリダイレクト先
     * @throws org.springframework.dao.DataAccessException ToDoのデータベース処理に失敗した場合
     */
    @GetMapping("/todos/{id}/delete")
    public String deleteConfirm(@PathVariable Long id, Model model,
            RedirectAttributes redirectAttributes) {
        Todo todo = todoService.findById(id);

        if (todo == null) {
            redirectAttributes.addFlashAttribute("message", "見つかりませんでした");
            return "redirect:/todos";
        }

        model.addAttribute("todo", todo);
        return "delete";
    }

    /**
     * 選択したToDoを一括で論理削除し、件数を通知して一覧へ戻る。
     *
     * @param ids 削除対象ID一覧。{@code null} または空なら削除しない
     * @param keyword タイトルの部分一致条件。空文字列または {@code null} で指定なし
     * @param category ジャンルの一致条件。空文字列または {@code null} で指定なし
     * @param order 期限の並び順。{@code desc} で降順、それ以外は昇順
     * @param showCompleted 完了済みも含める場合は {@code true}
     * @param page ページ番号（1始まり）
     * @param redirectAttributes 遷移先への検索条件と通知メッセージ
     * @return 表示するテンプレート名またはリダイレクト先
     * @throws org.springframework.dao.DataAccessException ToDoのデータベース処理に失敗した場合
     */
    @PostMapping("/todos/bulk-status")
    public String bulkStatus(@RequestParam(name = "ids", required = false) List<Long> ids,
            @RequestParam boolean completed,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "manual") String order,
            @RequestParam(defaultValue = "false") boolean showCompleted,
            @RequestParam(defaultValue = "1") int page,
            RedirectAttributes redirectAttributes) {
        int updated = todoService.updateStatusByIds(ids, completed);
        redirectAttributes.addFlashAttribute("message", updated > 0
                ? updated + (completed ? "件を完了にしました" : "件を未完了に戻しました")
                : "変更対象がありません（未選択・変更済み・削除済み、またはサブタスクの状態に従うタスクです）");
        redirectAttributes.addAttribute("keyword", keyword);
        redirectAttributes.addAttribute("category", category);
        redirectAttributes.addAttribute("order", "manual".equals(order) ? "manual" : ("desc".equals(order) ? "desc" : "asc"));
        redirectAttributes.addAttribute("showCompleted", showCompleted);
        redirectAttributes.addAttribute("page", page);
        return "redirect:/todos";
    }

    @PostMapping("/todos/bulk-delete")
    public String bulkDelete(@RequestParam(name = "ids", required = false) List<Long> ids,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "manual") String order,
            @RequestParam(defaultValue = "false") boolean showCompleted,
            @RequestParam(defaultValue = "1") int page,
            RedirectAttributes redirectAttributes) {
        int deleted = todoService.deleteByIds(ids);
        redirectAttributes.addFlashAttribute("message", deleted > 0
                ? deleted + "件削除しました" : "削除対象が選択されていません、または削除済みです");
        redirectAttributes.addAttribute("keyword", keyword);
        redirectAttributes.addAttribute("category", category);
        redirectAttributes.addAttribute("order", "manual".equals(order) ? "manual" : ("desc".equals(order) ? "desc" : "asc"));
        redirectAttributes.addAttribute("showCompleted", showCompleted);
        redirectAttributes.addAttribute("page", page);
        return "redirect:/todos";
    }

    /**
     * ToDoを論理削除する。
     *
     * @param id 対象ToDoのID
     * @param redirectAttributes 遷移先への検索条件と通知メッセージ
     * @return 表示するテンプレート名またはリダイレクト先
     * @throws org.springframework.dao.DataAccessException ToDoのデータベース処理に失敗した場合
     */
    @PostMapping("/todos/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        todoService.delete(id);
        redirectAttributes.addFlashAttribute("message", "削除しました");
        return "redirect:/todos";
    }

    /**
     * 論理削除したToDoを復元する。
     *
     * @param id 対象ToDoのID
     * @param redirectAttributes 遷移先への検索条件と通知メッセージ
     * @return 表示するテンプレート名またはリダイレクト先
     * @throws org.springframework.dao.DataAccessException ToDoのデータベース処理に失敗した場合
     */
    @PostMapping(value = "/todos/{id}/restore", params = "trash=1")
    public String restore(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        todoService.restore(id);
        redirectAttributes.addFlashAttribute("message", "戻しました");
        return "redirect:/todos?trash=1";
    }
}
