# 非同期処理

`AsyncConfiguration` の `@EnableAsync` で有効化しています。

| Bean | 用途 | core / max | キュー上限 |
| --- | --- | --- | --- |
| taskExecutor | レポート・既定の @Async | 2 / 4 | 100 |
| emailExecutor | メール送信 | 1 / 2 | 50 |

満杯時は呼び出し元へ TaskRejectedException を返します。終了時は最大30秒待機します。

## レポート

`GET /api/todos/report?keyword=&category=` はゴミ箱以外のToDoの総数・完了数・未完了数をJSONで返します。
例: `{"total":3,"completed":1,"pending":2}`

別Beanの `TodoReportService.generate` を呼び出して taskExecutor 上で集計します。
結果を CompletableFuture で受け、コントローラーで `future.get(timeout, TimeUnit.MILLISECONDS)` を使用します。
待機時間は `app.async.report-timeout-ms`（既定5000、正数）です。
タイムアウトは504、実行失敗は500、受付拒否・割り込みは503です。割り込みフラグは復元します。
待機中はHTTPスレッドを使用します。タイムアウト後も処理は継続する可能性があります。
DBの読み取りトランザクションはワーカースレッドで開始します。

## メール

他のBeanへ EmailService を注入し、`emailService.send(recipient, subject, body)` を呼び出します。
emailExecutor 上で実際にSMTP送信します。HTTP送信APIや自動通知は追加していません。
SMTP利用時は `spring.mail.host`、`spring.mail.port`、`spring.mail.username`、
`spring.mail.password`、`app.mail.from` を環境に合わせて設定してください。
認証・TLSが必要なら `spring.mail.properties.mail.smtp.auth=true` と
`spring.mail.properties.mail.smtp.starttls.enable=true` も設定します。資格情報は環境変数等で渡します。
SMTPの接続・読み取り・書き込みタイムアウトは既定5000msです。
SMTP未設定でもアプリは起動できますが、送信時は例外を記録します。

void の非同期メソッドの例外は AsyncUncaughtExceptionHandler がメソッド名とスタックトレースを記録します。
Future の例外は呼び出し側で ExecutionException として取得します。
`@Async` はSpring管理Beanを別Beanから呼び出してください（同じインスタンス内の直接呼び出しは対象外）。

参考: [Spring Async](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
