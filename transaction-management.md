# トランザクション管理

`TodoService` の更新処理と `todo_history` の操作履歴は同じトランザクションで保存します。
`rollbackFor = Exception.class` によりチェック例外もロールバックします。
検索・件数取得には `readOnly = true` を指定しています。

監査ログは別Beanの `AuditLogService` が `REQUIRES_NEW` で更新処理前に保存します。
`audit_log.event_type = ATTEMPT` は操作の試行を表し、成功の記録ではありません。
新規作成の試行時点ではIDが未採番のため `todo_id` はNULLです。
監査保存が失敗した場合は本体の更新も開始しません。
操作履歴は指定IDへの操作を記録し、対象が存在せず更新件数が0の場合も記録します。

## DBへの適用

新規Docker DBでは `initdb/02_create_transaction_tables.sql` が初期化時に実行されます。
既存DBでは同ファイルを対象DBに一度適用してからアプリを起動してください。
既存のDockerボリュームには初期化SQLは自動で再適用されません。

外側の処理中に別接続で監査を書き込むため、接続プールには同時実行数に加えて
監査用の接続の余裕が必要です。監査テーブルは本体のロールバックやロックに
依存しないよう、ToDoへの外部キーを持ちません。

## 検証

`./mvnw.cmd "-Dtest=TransactionTests" test`

H2と実際のMyBatis・Springトランザクションプロキシを使い、コミット、
本体/履歴の失敗、チェック例外、監査ログの残存、読み取り専用設定を検証します。
