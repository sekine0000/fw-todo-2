# タスク間の依存関係

タスク詳細 `/todos/{id}` の「タスクの依存関係」で前提タスクを選び、追加・削除できます。
前提タスクに未完了のものがあると「ブロック中」と表示します。完了操作そのものは制限しません。
SVGは現在のタスクにつながるタスクを表示し、前提から後続へ矢印を描きます。

| メソッド | パス | 内容 |
| --- | --- | --- |
| GET | `/api/todos/{id}/dependencies` | 有効なタスク一覧 `tasks`、依存関係 `edges`、ブロック中のID `blockedIds` |
| POST | `/api/todos/{id}/dependencies` | JSON `{"prerequisiteId": 1}` で前提を追加 |
| DELETE | `/api/todos/{id}/dependencies/{prerequisiteId}` | 前提を解除 |

追加・削除の成功は204、入力不正は400、存在しない・削除済みタスクは404、自己参照・循環は409です。
同じ前提の再追加と解除済み関係の再削除は成功します。
`edges` は `prerequisiteId` と `successorId` を持ちます。

`task_dependencies` は前提ID・後続IDの複合主キーと外部キーで多対多を管理します。
起動時に既存DBへテーブルを追加します。手動適用用SQLは `migrations/20261126_add_task_dependencies.sql` にあります。
追加・削除はトランザクション内で `task_dependency_lock` の行をロックし、同時更新を直列化します。
追加前に後続からBFSで探索し、前提へ到達すれば循環として拒否します。
論理削除したタスクの関係は保存し、画面・ブロック判定から除外します。
復元による循環を防ぐため、循環判定には論理削除したタスクも含めます。物理削除では関係も削除します。

検証: `./mvnw.cmd -Dtest=TaskDependencyTests test`
