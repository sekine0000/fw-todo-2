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
        todoMapper.insert(todo);
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
        todoMapper.restoreById(id);
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
        todoMapper.update(todo);
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
        todoMapper.deleteById(id);
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

        int deleted = todoMapper.deleteByIds(ids);
        for (Long id : ids.stream().distinct().toList()) {
            historyMapper.insert(id, "DELETE");
        }
        return deleted;
    }
}
