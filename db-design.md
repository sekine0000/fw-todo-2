# やること管理アプリ データベース設計書

## 1. 文書の目的

本書は、`requirements.md` と `handout_app_spec.md` をもとに、やること管理アプリで使うデータベースの構造を定めるものです。

- DB（データベース：データを保存・管理する仕組み）には MySQL を使用します。
- 使用するテーブル（表形式の保存場所）は `todos` の1つだけです。
- やること1件を、`todos` テーブルの1行として保存します。
- 画面の配置や見た目は、別の画面設計書で定めます。

## 2. テーブル概要

| 項目 | 内容 |
|---|---|
| テーブル名 | `todos` |
| 用途 | やることの内容、分類、期限、状態、登録・更新日時を保存する |
| 1行が表すもの | やること1件 |
| 主キー | `id` |

主キーとは、各行を重複なく見分けるための項目です。

## 3. カラム設計

カラムとは、テーブルに保存する個々のデータ項目です。`NULL` は「値が入っていない状態」を表します。

| カラム名 | 内容 | データ型 | NULL | 初期値・自動設定 | 制約（保存時の決まり） |
|---|---|---|---|---|---|
| `id` | 1件を見分ける番号 | `BIGINT` | 不可 | 1ずつ自動で増加 | 主キー、符号なしの正の整数 |
| `title` | やること | `VARCHAR(255)` | 不可 | なし | 255文字以内 |
| `detail` | メモ | `VARCHAR(255)` | 可 | `NULL` | 255文字以内 |
| `category` | ジャンル | `VARCHAR(255)` | 不可 | なし | 「デザイン」「マーケティング」「プログラミング」「資格」「就職活動」のいずれか |
| `priority` | 優先度 | `INT` | 不可 | `2` | `1`＝高、`2`＝中、`3`＝低 |
| `due_date` | 期限 | `DATE` | 可 | `NULL` | 日付 |
| `completed` | 完了状態 | `BOOLEAN` | 不可 | `FALSE` | `FALSE`（`0`）＝未完了、`TRUE`（`1`）＝完了 |
| `created_at` | 登録日時 | `DATETIME` | 不可 | 登録時の日時を自動設定 | 利用者は入力しない |
| `updated_at` | 最終更新日時 | `DATETIME` | 不可 | 登録時および更新時の日時を自動設定 | 利用者は入力しない |

補足事項は次のとおりです。

- `VARCHAR(255)` は、最大255文字の文字列を保存する型です。
- `BIGINT UNSIGNED` は、負の値を使わない大きな整数の型です。
- `BOOLEAN` は、真か偽の2状態を保存する型です。MySQLでは実際には `1` または `0` として扱われます。
- `DATETIME` は、日付と時刻を一緒に保存する型です。
- `detail` と `due_date` が未入力のときは `NULL` を保存します。
- `id`、`created_at`、`updated_at` はアプリの入力項目にせず、MySQLが自動で設定します。
- `title` が空白だけかどうかの確認は、アプリ側の入力チェックで行います。DBの制約だけでは半角空白と全角空白の両方を十分に判定しにくいためです。

## 4. 要件とカラムの対応

| 要件・仕様 | 対応するカラム | 対応内容 |
|---|---|---|
| FR-01 一覧 | `title`, `category`, `priority`, `due_date`, `completed`, `id` | 一覧に表示する内容と、編集・削除の対象を見分ける番号を保存する |
| FR-02 登録 | 全カラム | 入力した5項目を保存し、番号・完了状態・日時を自動設定する |
| FR-03 編集 | `id`, `title`, `detail`, `category`, `priority`, `due_date`, `completed`, `updated_at` | `id` で対象を特定し、編集項目と最終更新日時を更新する |
| FR-04 削除 | `id` | `id` で削除対象の1件を特定する |
| FR-05 名前検索 | `title` | 入力文字を一部に含む行を絞り込む |
| FR-05 ジャンル絞り込み | `category` | 選択したジャンルと完全に一致する行を絞り込む |
| FR-06 期限順の並び替え | `due_date` | 期限が近い順または遠い順に並べる |
| やることは必須・255文字以内 | `title` | `NOT NULL` と `VARCHAR(255)` で保存条件を定める |
| メモは任意・255文字以内 | `detail` | `NULL` を許可し、`VARCHAR(255)` で長さを定める |
| ジャンルは指定の5種類 | `category` | `CHECK` 制約で5種類以外を保存できないようにする |
| 優先度は高・中・低、初期値は中 | `priority` | `CHECK` 制約で1～3に限定し、初期値を `2` にする |
| 期限は任意の日付 | `due_date` | `DATE` 型とし、`NULL` を許可する |
| 完了状態の初期値は未完了 | `completed` | 初期値を `FALSE` にする |
| 登録日時を自動設定 | `created_at` | 登録時に現在日時を設定する |
| 最終更新日時を自動設定 | `updated_at` | 登録時と更新時に現在日時を設定する |
| ログには操作と対象番号を記録 | `id` | ログに記録する対象番号として使用する。ログ自体は別テーブルに保存しない |

`NOT NULL` は「値を必ず保存する」という制約、`CHECK` は「決めた範囲の値だけを許可する」という制約です。

## 5. 検索・並び替えの考え方

アプリからDBへ問い合わせるときは、次のカラムを使います。SQLは、DBに検索や更新を指示するための言葉です。

| 処理 | 使用するカラム | 問い合わせの考え方 |
|---|---|---|
| 名前検索 | `title` | `LIKE '%検索文字%'` で、検索文字を一部に含む行を探す |
| ジャンル絞り込み | `category` | `= 選択したジャンル` で、完全に一致する行を探す |
| 期限が近い順 | `due_date` | `ORDER BY due_date ASC` を使う |
| 期限が遠い順 | `due_date` | `ORDER BY due_date DESC` を使う |

期限未入力の行は `due_date` が `NULL` になります。MySQLの通常の並びでは、昇順（近い順）では `NULL` が先、降順（遠い順）では `NULL` が後に並びます。本書では、要件に追加の指定がないため、このMySQLの動作を使用します。

## 6. インデックス設計

インデックスとは、検索や並び替えを速くするための索引です。

| インデックス名 | 対象カラム | 用途 |
|---|---|---|
| `PRIMARY` | `id` | 1件の特定、編集、削除 |
| `idx_todos_category` | `category` | ジャンルによる絞り込み |
| `idx_todos_due_date` | `due_date` | 期限による並び替え |

`title` の検索は文字列の途中も対象にするため、通常のインデックスでは十分な高速化が期待できません。今回の要件にはページ分けがなく、小規模な個人利用を前提としているため、`title` 用の追加インデックスは設けません。

## 7. CREATE TABLE文（DDL）

DDLとは、テーブルの形を作るためのSQLです。次の文をMySQLで実行すると、`todos` テーブルを作成できます。`CHECK` 制約を使用するため、MySQL 8.0.16以降を前提とします。

```sql
CREATE TABLE todos (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    detail VARCHAR(255) NULL,
    category VARCHAR(255) NOT NULL,
    priority INT NOT NULL DEFAULT 2,
    due_date DATE NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT chk_todos_category CHECK (
        category IN (
            'デザイン',
            'マーケティング',
            'プログラミング',
            '資格',
            '就職活動'
        )
    ),
    CONSTRAINT chk_todos_priority CHECK (priority IN (1, 2, 3)),
    CONSTRAINT chk_todos_completed CHECK (completed IN (FALSE, TRUE)),
    INDEX idx_todos_category (category),
    INDEX idx_todos_due_date (due_date)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
```

- `AUTO_INCREMENT` は、登録のたびに番号を1ずつ自動で増やす指定です。
- `CURRENT_TIMESTAMP` は、処理時点の日時を自動で入れる指定です。
- `ON UPDATE CURRENT_TIMESTAMP` は、行を更新したときに最終更新日時を自動で更新する指定です。
- `InnoDB` は、MySQLでデータを安全に保存・更新するための標準的な保存方式です。
- `utf8mb4` は、日本語を含む幅広い文字を保存できる文字コードです。

## 8. 設計上の確認事項

- 使用するテーブルは `todos` だけです。ログ用テーブルなどは追加しません。
- 削除は対象行を実際に削除します。削除済みを表すカラムは設けません。
- 完了した日時を保存するカラムは設けません。
- 利用者を表すカラムは設けません。ログインや複数人での共有は対象外だからです。
- 写真やファイルを保存するカラムは設けません。
- 画面表示専用の文言や検索条件はDBに保存しません。
