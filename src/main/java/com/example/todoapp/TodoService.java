package com.example.todoapp;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

/**
 * ToDoの検索・更新と操作履歴の記録を行うサービス。
 *
 * @author ToDoアプリ開発チーム
 * @version 0.0.1
 * @since 0.0.1
 * @see TodoMapper
 */
@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class TodoService {

    private final TodoMapper todoMapper;

    private final TodoHistoryMapper historyMapper;


    /**
     * 依存するサービスまたはマッパーを受け取って構築する。
     *
     * @param todoMapper ToDo永続化マッパー
     * @param historyMapper 操作履歴マッパー
     */
    public TodoService(TodoMapper todoMapper, TodoHistoryMapper historyMapper) {
        this.todoMapper = todoMapper;
        this.historyMapper = historyMapper;

    }

    /**
     * 未削除のToDoを検索する。期限条件を省略すると日付で絞り込まない。
     *
     * @param keyword タイトルの部分一致条件。空文字列または {@code null} で指定なし
     * @param category ジャンルの一致条件。空文字列または {@code null} で指定なし
     * @param order 期限の並び順。{@code desc} で降順、それ以外は昇順
     * @return 検索結果。該当しない場合は空リスト
     * @throws org.springframework.dao.DataAccessException データベース処理に失敗した場合
     */
    @Transactional(readOnly = true)
    public List<Todo> search(String keyword, String category, String order) {
        return search(keyword, category, order, null, null);
    }

    /**
     * 未削除のToDoを検索する。期限条件を省略すると日付で絞り込まない。
     *
     * @param keyword タイトルの部分一致条件。空文字列または {@code null} で指定なし
     * @param category ジャンルの一致条件。空文字列または {@code null} で指定なし
     * @param order 期限の並び順。{@code desc} で降順、それ以外は昇順
     * @param from 期限の下限（当日を含む）。{@code null} で指定なし
     * @param to 期限の上限（当日を含む）。{@code null} で指定なし
     * @return 検索結果。該当しない場合は空リスト
     * @throws org.springframework.dao.DataAccessException データベース処理に失敗した場合
     */
    @Transactional(readOnly = true)
    public List<Todo> search(String keyword, String category, String order, LocalDate from, LocalDate to) {
        return todoMapper.search(keyword, category, order, from, to, true);
    }

    /**
     * 未削除のToDoを検索する。期限条件を省略すると日付で絞り込まない。
     *
     * @param keyword タイトルの部分一致条件。空文字列または {@code null} で指定なし
     * @param category ジャンルの一致条件。空文字列または {@code null} で指定なし
     * @param order 期限の並び順。{@code desc} で降順、それ以外は昇順
     * @param showCompleted 完了済みも含める場合は {@code true}
     * @return 検索結果。該当しない場合は空リスト
     * @throws org.springframework.dao.DataAccessException データベース処理に失敗した場合
     */
    @Transactional(readOnly = true)
    public List<Todo> search(String keyword, String category, String order, boolean showCompleted) {
        return todoMapper.search(keyword, category, order, null, null, showCompleted);
    }

    /**
     * 未削除のToDoをIDで取得する。
     *
     * @param id 対象ToDoのID
     * @return 該当ToDo。存在しない場合は {@code null}
     * @throws org.springframework.dao.DataAccessException データベース処理に失敗した場合
     */
    @Transactional(readOnly = true)
    public Todo findById(Long id) {
        return todoMapper.findById(id);
    }

    /**
     * CSV出力対象を全件検索する。ごみ箱では完了状態で絞り込まない。
     *
     * @param keyword タイトルの部分一致条件。空文字列または {@code null} で指定なし
     * @param category ジャンルの一致条件。空文字列または {@code null} で指定なし
     * @param order 期限の並び順。{@code desc} で降順、それ以外は昇順
     * @param showCompleted 完了済みも含める場合は {@code true}
     * @param trash ごみ箱を検索する場合は {@code true}
     * @return 検索結果。該当しない場合は空リスト
     * @throws org.springframework.dao.DataAccessException データベース処理に失敗した場合
     */
    @Transactional(readOnly = true)
    public List<Todo> searchCsv(String keyword, String category, String order,
            boolean showCompleted, boolean trash) {
        return todoMapper.searchCsv(keyword, category, order, showCompleted, trash);
    }

    /**
     * 未削除のToDoの該当件数を取得する。
     *
     * @param keyword タイトルの部分一致条件。空文字列または {@code null} で指定なし
     * @param category ジャンルの一致条件。空文字列または {@code null} で指定なし
     * @param showCompleted 完了済みも含める場合は {@code true}
     * @return 条件に一致する件数
     * @throws org.springframework.dao.DataAccessException データベース処理に失敗した場合
     */
    @Transactional(readOnly = true)
    public long count(String keyword, String category, boolean showCompleted) {
        return todoMapper.count(keyword, category, showCompleted);
    }

    /**
     * 未削除ToDoをピン留め優先で最大10件取得する。
     *
     * @param keyword タイトルの部分一致条件。空文字列または {@code null} で指定なし
     * @param category ジャンルの一致条件。空文字列または {@code null} で指定なし
     * @param order 期限の並び順。{@code desc} で降順、それ以外は昇順
     * @param showCompleted 完了済みも含める場合は {@code true}
     * @param offset 先頭から読み飛ばす件数
     * @return 検索結果。該当しない場合は空リスト
     * @throws org.springframework.dao.DataAccessException データベース処理に失敗した場合
     */
    @Transactional(readOnly = true)
    public List<Todo> searchPage(String keyword, String category, String order, boolean showCompleted,
            long offset) {
        return todoMapper.searchPage(keyword, category, order, showCompleted, offset);
    }

    /**
     * ToDoを新規作成する。操作履歴も記録する。採番IDを入力オブジェクトに設定する。
     *
     * @param todo 入力または更新対象の {@link com.example.todoapp.Todo}
     * @throws org.springframework.dao.DataAccessException データベース処理に失敗した場合
     */
    @Auditable(action = "CREATE")
    public void create(Todo todo) {
        if (todo.getParentId() != null) lockParent(todo.getParentId());
        todoMapper.insert(todo);
        if (todo.getParentId() != null) refreshParent(todo.getParentId());
        historyMapper.insert(todo.getId(), "CREATE");
        log.info("登録: id={}", todo.getId());
    }

    /**
     * ごみ箱の該当件数を取得する。
     *
     * @param keyword タイトルの部分一致条件。空文字列または {@code null} で指定なし
     * @param category ジャンルの一致条件。空文字列または {@code null} で指定なし
     * @return 条件に一致する件数
     * @throws org.springframework.dao.DataAccessException データベース処理に失敗した場合
     */
    @Transactional(readOnly = true)
    public long countTrash(String keyword, String category) {
        return todoMapper.countTrash(keyword, category);
    }

    /**
     * ごみ箱から最大10件取得する。
     *
     * @param keyword タイトルの部分一致条件。空文字列または {@code null} で指定なし
     * @param category ジャンルの一致条件。空文字列または {@code null} で指定なし
     * @param order 期限の並び順。{@code desc} で降順、それ以外は昇順
     * @param offset 先頭から読み飛ばす件数
     * @return 検索結果。該当しない場合は空リスト
     * @throws org.springframework.dao.DataAccessException データベース処理に失敗した場合
     */
    @Transactional(readOnly = true)
    public List<Todo> searchTrashPage(String keyword, String category, String order, long offset) {
        return todoMapper.searchTrashPage(keyword, category, order, offset);
    }

    /**
     * 論理削除したToDoを復元する。操作履歴も記録する。
     *
     * @param id 対象ToDoのID
     * @throws org.springframework.dao.DataAccessException データベース処理に失敗した場合
     */
    @Auditable(action = "RESTORE")
    public void restore(Long id) {
        Todo stored = todoMapper.findForAudit(id);
        if (stored != null && stored.getParentId() != null) lockParent(stored.getParentId());
        todoMapper.restoreById(id);
        if (stored != null && stored.getParentId() != null) refreshParent(stored.getParentId());
        historyMapper.insert(id, "RESTORE");
        log.info("復元: id={}", id);
    }

    /**
     * ToDoを更新する。操作履歴も記録する。
     *
     * @param todo 入力または更新対象の {@link com.example.todoapp.Todo}
     * @throws org.springframework.dao.DataAccessException データベース処理に失敗した場合
     */
    @Auditable(action = "UPDATE")
    public void update(Todo todo) {
        Todo stored = todoMapper.findById(todo.getId());
        if (stored != null) {
            if (stored.getParentId() != null) lockParent(stored.getParentId());
            else {
                todoMapper.findForUpdate(stored.getId());
                List<Todo> children = todoMapper.findSubtasksForUpdate(stored.getId());
                if (!children.isEmpty()) todo.setCompleted(children.stream().allMatch(t -> Boolean.TRUE.equals(t.getCompleted())));
            }
        }
        todoMapper.update(todo);
        if (stored != null && stored.getParentId() != null) refreshParent(stored.getParentId());
        historyMapper.insert(todo.getId(), "UPDATE");
        log.info("編集: id={}", todo.getId());
    }

    /**
     * ToDoを論理削除する。操作履歴も記録する。
     *
     * @param id 対象ToDoのID
     * @throws org.springframework.dao.DataAccessException データベース処理に失敗した場合
     */
    @Auditable(action = "DELETE")
    public void delete(Long id) {
        Todo stored = todoMapper.findById(id);
        if (stored != null && stored.getParentId() != null) lockParent(stored.getParentId());
        else if (stored != null) todoMapper.findForUpdate(id);
        todoMapper.deleteById(id);
        if (stored != null && stored.getParentId() != null) refreshParent(stored.getParentId());
        historyMapper.insert(id, "DELETE");
        log.info("削除: id={}", id);
    }

    /**
     * ピン留め状態を反転する。操作履歴も記録する。
     *
     * @param id 対象ToDoのID
     * @throws org.springframework.dao.DataAccessException データベース処理に失敗した場合
     */
    @Auditable(action = "TOGGLE_PINNED")
    public void togglePinned(Long id) {
        todoMapper.togglePinned(id);
        historyMapper.insert(id, "TOGGLE_PINNED");
    }

    /**
     * ToDoを一括で論理削除し、重複IDを除いて履歴を記録する。
     *
     * @param ids 削除対象ID一覧。{@code null} または空なら削除しない
     * @return 実際に論理削除した件数。ID未指定または空の場合は {@code 0}
     * @throws org.springframework.dao.DataAccessException データベース処理に失敗した場合
     */
    @Auditable(action = "DELETE")
    public int deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        var parents = new java.util.TreeSet<Long>();
        for (Long id : ids.stream().distinct().sorted().toList()) {
            Todo stored = todoMapper.findById(id);
            if (stored != null && stored.getParentId() != null) parents.add(stored.getParentId());
        }
        parents.forEach(this::lockParent);
        int deleted = todoMapper.deleteByIds(ids);
        for (Long parentId : parents) {
            if (todoMapper.findForUpdate(parentId) != null) refreshParent(parentId);
        }
        for (Long id : ids.stream().distinct().toList()) {
            historyMapper.insert(id, "DELETE");
        }
        return deleted;
    }
    @Transactional(readOnly = true)
    public List<Todo> subtasks(Long parentId) {
        return todoMapper.findSubtasks(parentId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void reorder(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(id -> id == null || id <= 0)
                || new java.util.HashSet<>(ids).size() != ids.size()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid task IDs");
        }
        var ordered = new java.util.ArrayList<>(todoMapper.findOrderedIdsForUpdate());
        if (!ordered.containsAll(ids)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Tasks changed; reload the list");
        }
        var selected = new java.util.HashSet<>(ids);
        var replacement = ids.iterator();
        for (int i = 0; i < ordered.size(); i++) {
            if (selected.contains(ordered.get(i))) ordered.set(i, replacement.next());
        }
        todoMapper.updateDisplayOrder(ordered);
    }

    /** 親と選択されたサブタスクを、未完了の独立したタスクとして一括複製する。 */
    @Transactional(rollbackFor = Exception.class)
    public Todo duplicate(Long id, boolean includeSubtasks) {
        Todo source = todoMapper.findForUpdate(id);
        if (source == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        }
        Todo copy = copyTask(source);
        create(copy);
        if (includeSubtasks) {
            for (Todo child : todoMapper.findSubtasksForUpdate(id)) {
                Todo childCopy = copyTask(child);
                childCopy.setParentId(copy.getId());
                create(childCopy);
            }
        }
        return copy;
    }

    private Todo copyTask(Todo source) {
        Todo copy = new Todo();
        String suffix = "（コピー）";
        String title = source.getTitle();
        // DBの255文字制限内に接尾辞を収める（サロゲートペアも分割しない）。
        int length = Math.min(title.codePointCount(0, title.length()), 255 - suffix.length());
        copy.setTitle(title.substring(0, title.offsetByCodePoints(0, length)) + suffix);
        copy.setDetail(source.getDetail());
        copy.setCategory(source.getCategory());
        copy.setPriority(source.getPriority());
        copy.setDueDate(source.getDueDate());
        copy.setCompleted(false);
        return copy;
    }

    private Todo lockParent(Long id) {
        Todo parent = todoMapper.findForUpdate(id);
        if (parent == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        if (parent.getParentId() != null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "サブタスクの下には追加できません");
        return parent;
    }

    private void refreshParent(Long parentId) {
        Todo parent = todoMapper.findForUpdate(parentId);
        List<Todo> children = todoMapper.findSubtasksForUpdate(parentId);
        boolean completed = !children.isEmpty() && children.stream().allMatch(t -> Boolean.TRUE.equals(t.getCompleted()));
        if (parent != null && completed != Boolean.TRUE.equals(parent.getCompleted())) {
            parent.setCompleted(completed);
            todoMapper.update(parent);
            historyMapper.insert(parentId, "UPDATE");
        }
    }

    @Auditable(action = "CREATE")
    public void addSubtask(Long parentId, String title) {
        Todo parent = lockParent(parentId);
        if (title == null || title.isBlank() || title.length() > 255) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "名前は1〜255文字で入力してください");
        }
        Todo child = new Todo();
        child.setParentId(parentId);
        child.setTitle(title.strip());
        child.setCategory(parent.getCategory());
        child.setPriority(parent.getPriority());
        child.setCompleted(false);
        create(child);
    }

    @Auditable(action = "UPDATE")
    public void toggleSubtask(Long parentId, Long id) {
        lockParent(parentId);
        Todo child = requireSubtask(parentId, id);
        child.setCompleted(!Boolean.TRUE.equals(child.getCompleted()));
        update(child);
    }

    @Auditable(action = "DELETE")
    public void deleteSubtask(Long parentId, Long id) {
        lockParent(parentId);
        requireSubtask(parentId, id);
        todoMapper.removeSubtask(parentId, id);
        historyMapper.insert(id, "DELETE");
        refreshParent(parentId);
    }

    private Todo requireSubtask(Long parentId, Long id) {
        Todo child = todoMapper.findById(id);
        if (child == null || !parentId.equals(child.getParentId())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        }
        return child;
    }
}
