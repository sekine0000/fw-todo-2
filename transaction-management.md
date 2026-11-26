# トランザクション管理

`TodoService` の更新処理と `todo_history` の操作履歴は同じトランザクションで保存します。
`rollbackFor = Exception.class` によりチェック例外もロールバックします。
検索・件数取得には `readOnly = true` を指定しています。

監査ログは別Beanの `AuditLogService` が `REQUIRES_NEW` で更新処理前に保存します。
`audit_logs.status = ATTEMPT` は操作の試行を表し、成功の記録ではありません。
新規作成の試行時点ではIDが未採番のため `entity_id` はNULLです（操作後に更新します）。
監査保存が失敗した場合は本体の更新も開始しません。
操作履歴は指定IDへの操作を記録し、対象が存在せず更新件数が0の場合も記録します。

## DBへの適用

新規Docker DBでは `initdb` 内のSQLが初期化時に実行されます。
`02_create_transaction_tables.sql` と `03_create_audit_logs.sql` はアプリに同梱され、起動時に自動実行されます。
`CREATE TABLE IF NOT EXISTS` のため既存の監査ログは保持されます。DBユーザーにはCREATE権限が必要です。
既存のDockerボリュームには初期化SQLは自動で再適用されません。

外側の処理中に別接続で監査を書き込むため、接続プールには同時実行数に加えて
監査用の接続の余裕が必要です。監査テーブルは本体のロールバックやロックに
依存しないよう、ToDoへの外部キーを持ちません。

## 検証

`./mvnw.cmd "-Dtest=TransactionTests" test`

H2と実際のMyBatis・Springトランザクションプロキシを使い、コミット、
本体/履歴の失敗、チェック例外、監査ログの残存、読み取り専用設定を検証します。
## 監査ログ

ToDoの登録・更新・削除・復元・ピン切替・一括削除は `@Auditable` と `AuditAspect` で記録する。
`initdb/03_create_audit_logs.sql` はアプリ起動時にも実行し、既存DBへ監査テーブルを追加する。
Dockerの初期化SQLは新規ボリューム作成時のみ実行されるため、アプリ側の初期化で既存ボリュームも扱う。
旧 `audit_log` は互換用に残し、新しいログは `audit_logs` に保存する。

記録の開始と終了は別Beanの `REQUIRES_NEW` を使用する。開始時に `ATTEMPT` を保存し、
本体トランザクション内で変更前後をJSON化、確定後に `SUCCESS` または `ROLLED_BACK` に更新する。
取り消された操作の変更後JSONは確定データではなく、操作直後の状態を表す。
プロセス中断時は `ATTEMPT` が残る。ログテーブルにはToDoへの外部キーを付けない。
コネクションプールには、本体処理に加え独立したログ保存用の接続が必要。

管理画面は `/admin/audit-logs`。操作・対象ID・ユーザー名の完全一致で検索し、50件ずつ表示する。
`ROLE_ADMIN` のみ閲覧できる。初期管理ユーザー名は `admin`（`AUDIT_ADMIN_USER` で変更可能）、
パスワードはSpring Bootが起動時に生成する。本番では `SPRING_SECURITY_USER_PASSWORD` を設定する。
Docker Composeでは `.env` の `AUDIT_ADMIN_USER` と `SPRING_SECURITY_USER_PASSWORD` がアプリに渡される。
変更後は `docker compose up -d --build --force-recreate app` で反映する。
パスワードを設定していない場合は起動ログの `Using generated security password:` を確認する。
IDEやMavenから直接起動する場合は `.env` は自動で読み込まれないため、起動環境の環境変数に設定する。
既存の公開ToDo/API/MCPのアクセス方式を維持し、ログイン済みの場合はSecurityContextのユーザー名を記録する。
未認証・バックグラウンド処理のユーザーはNULL。IPはHTTPリクエストの直接の接続元を使用する。

アノテーションはSpringプロキシを経由するサービスメソッドに付ける（同一Bean内の自己呼び出しは対象外）。
現在のスナップショット取得はToDo専用で、引数にTodo・ID・IDリストを受け取るメソッドに対応する。

## サブタスク

既存DBは起動時に `SubtaskSchemaInitializer` が不足する `parent_id` とカスケード外部キーを追加します。繰り返し起動してもデータを保持します。手動で事前適用する場合は `migrations/20261126_add_subtasks.sql` を一度実行してください。
新規DBには `initdb/01_create_table.sql` が自己参照外部キーを作成します。

一覧のタスク名から詳細画面を開き、サブタスクを追加・削除・完了切替できます。
サブタスクは1階層で、ジャンルと優先度は追加時に親から引き継ぎます。
進捗率は有効なサブタスクの完了件数÷総件数（整数%、小数点以下切り捨て）。
0件の場合は0%とし、最後のサブタスクを削除した親は未完了に戻ります。
未完了の追加・差し戻し時も親を未完了に戻します。
子がある親の完了状態は編集画面やAPIの指定より子の状態を優先します。

変更はサービスの `@Transactional(rollbackFor = Exception.class)` 内で、親行と子行をロックして行います。
親の状態と履歴を同じトランザクションで更新し、履歴の失敗時も全体をロールバックします。
通常の一覧・検索・ごみ箱は親タスクを表示します。
親の削除は従来どおり論理削除で、復元するとサブタスクも再表示できます。
詳細画面のサブタスク削除は物理削除です。親をDBから物理削除すると、自己参照外部キーの
`ON DELETE CASCADE` により子も削除されます。