# 設計思想

## ディレクトリ構成

```
backend-api/
├── pom.xml
├── src/main/resources/application-local.yml.example  # ローカル接続情報の雛形
├── deploy/                            # Azure VMへのデプロイ関連ファイル
│   ├── backend-api.service            # systemdユニットファイル
│   └── application-prod.yml.example   # VM配置用の設定ファイルの雛形
└── src/
    ├── main/
    │   ├── java/com/example/backend/
    │   │   ├── BackendApplication.java    # エントリーポイント
    │   │   ├── controller/                # HTTPエンドポイント（入出力のみ）
    │   │   │   └── UserController.java
    │   │   ├── service/                   # ビジネスロジック
    │   │   │   ├── UserService.java        # インターフェース
    │   │   │   └── impl/UserServiceImpl.java
    │   │   ├── mapper/                    # DBアクセス（MyBatis）
    │   │   │   └── UserMapper.java
    │   │   ├── model/
    │   │   │   ├── entity/                 # テーブルに対応するモデル（POJO）
    │   │   │   │   └── User.java
    │   │   │   └── dto/                    # リクエスト/レスポンス用DTO
    │   │   │       ├── UserRequest.java
    │   │   │       └── UserResponse.java
    │   │   ├── exception/                  # 例外・共通エラーハンドリング
    │   │   └── config/                     # 各種設定クラス（OpenAPI等）
    │   └── resources/
    │       ├── application.yml             # 共通設定
    │       ├── application-local.yml       # ローカル開発用
    │       ├── application-prod.yml         # 本番/検証用(Azure VM)
    │       ├── mapper/                     # MyBatisのXML(実際のSQL)
    │       └── db/migration/               # Flyway マイグレーションSQL
    └── test/java/com/example/backend/...   # テスト
```

デプロイ先はAzure VM上にJREを直接インストールし、jarファイルをsystemdサービスとして
起動する構成です(Docker/Kubernetesは使用しません)。

依存の向きは `controller → service → mapper` の一方向のみで、
Controllerが直接Mapperを呼び出すことはしません。

**リソースを追加する際の手順は [docs/features/](./features/) 配下に、
機能ごとにまとめています。** `docs/features/user.md` を参考にしてください。

## DBドライバについて

EDB Postgres Advanced Server はワイヤプロトコルレベルで PostgreSQL 互換のため、
本雛形はデフォルトで標準の **PostgreSQL JDBC ドライバ**（`org.postgresql:postgresql`、
Maven Central から取得可能）を使用しています。MyBatisはSQLを直接記述するため
JPAのような「方言(dialect)」の概念はなく、通常のCRUD用途ではこれで問題なく動作します。

以下のようなEDB固有機能を使う場合は **EDB JDBC Driver**（`com.edb.Driver`、
接続文字列 `jdbc:edb://host:5444/dbname`）への切り替えを検討してください。

- PL/SQLパッケージ（Oracle互換のPACKAGE）の呼び出し、OUTパラメータの受け取り
- Oracle固有の型（`SYS_REFCURSOR` 等）のハンドリング

EDB JDBC Driverは EDBのリポジトリ（要アカウント/ライセンス）から取得する必要があるため、
`pom.xml` に以下のように依存を差し替え、`<repositories>` にEDBのMavenリポジトリを追加してください。

```xml
<dependency>
    <groupId>com.edb</groupId>
    <artifactId>edb-jdbc</artifactId>
    <version>xx.x.x</version>
</dependency>
```

## Oracle互換モードでの識別子（テーブル名/カラム名）の注意

Redwood（Oracle互換）モードでは、クォートしない識別子は **大文字**に畳み込まれます
（標準PostgreSQLは逆に小文字に畳み込みます）。本雛形ではDDL・エンティティ双方で
識別子をダブルクォートし小文字で統一することで、この差異による事故を防いでいます。
既存のEDBスキーマに接続する場合は、実際のテーブル/カラム名の大文字小文字を
`psql`や`SELECT * FROM information_schema.columns`等で確認してから
Mapper XMLの列名を合わせてください。

## ログ・並行処理・エラーハンドリングについて

### ファイルログ

`src/main/resources/log4j2-spring.xml`(Log4j2)で以下を設定しています。

- コンソール出力(従来通り。`journalctl`で確認可能)
- `logs/application.log`: 全レベルのログ。日次 + 100MBごとにローテーション、直近30日分を保持
- `logs/error.log`: ERRORレベルのみを抽出した別ファイル(障害調査用)

出力先は環境変数`LOG_PATH`で変更可能です(未指定時はカレントディレクトリ配下の`logs/`)。
Azure VM上ではsystemdユニットの`WorkingDirectory=/opt/backend-api`により、
自動的に`/opt/backend-api/logs/`に出力されます。

### 同時リクエストの扱い

Spring Boot(組み込みTomcat)は、リクエストごとにスレッドプールから
スレッドを割り当てて**並列に処理**します(1リクエストずつ順番に処理する
わけではありません)。同時実行数の上限は`application.yml`の
`server.tomcat.threads.max`(デフォルト200)で調整できます。

並列処理される以上、「複数のリクエストが同じデータを同時に更新する」
競合が起こり得ます。今回、`email`の重複についてはアプリ側のチェックに加え、
DB側にも`UNIQUE`制約(`V2__add_users_email_unique_constraint.sql`)を追加し、
競合時の最終的な整合性を保証しています。同様の一意性が必要な項目を
追加する際は、アプリ側のチェックだけに頼らず、DB制約も合わせて設定してください。

### エラーコード体系

`exception/ErrorCode.java`にアプリ全体のエラーを一元管理しています。

| コード | 内容 | HTTPステータス |
|---|---|---|
| `USR-001` | ユーザーが見つからない | 404 |
| `USR-002` | メールアドレス重複 | 409 |
| `VAL-001` | 入力値検証エラー | 400 |
| `SYS-001` | 想定外のエラー | 500 |
| `SYS-002` | ファイルアップロードエラー | 400 |

新しいエラーの種類を追加する際は、既存のコードを変更・削除せず、
末尾に新しい連番を追加してください(過去のログとの整合性を保つため)。

### 例外処理の構成

- `BusinessException`: 業務上想定される例外の基底クラス。`ErrorCode`を持つ
- `ResourceNotFoundException` / `DuplicateEmailException`: `BusinessException`を継承した具体的な例外
- `GlobalExceptionHandler`: 例外の種類ごとに適切なHTTPステータス・ログレベルで処理を一元化
  - `BusinessException`: `warn`ログ(想定内のため、スタックトレースは出力しない)
  - 想定外の`Exception`: `error`ログ(スタックトレースまで出力)

### トレースID(問い合わせ対応用)

`TraceIdFilter`が全リクエストに一意なID(`traceId`)を発行し、以下すべてに同じ値を載せます。

- サーバーログの各行(`[traceId=xxxxx]`)
- レスポンスヘッダー(`X-Trace-Id`)
- エラーレスポンスのボディ(`traceId`フィールド)

利用者から「このエラーが出た」と問い合わせを受けた際、レスポンスの
`traceId`(またはヘッダー`X-Trace-Id`)を教えてもらえば、ログファイルから
`grep`で該当のリクエストのログを特定できます。

```bash
grep "traceId=なんとか" logs/application.log
```

新しいエンティティ(`Product`等)を追加する際も、同様の考え方
(業務エラーは`BusinessException`を継承、DB制約で最終防衛、`ErrorCode`に追記)
を踏襲してください。

## Flywayマイグレーションについて

`src/main/resources/db/migration/` 配下の `V{番号}__説明.sql` が起動時に自動適用されます。
既に適用済みのファイルの内容は変更せず、変更が必要な場合は新しいバージョン番号の
ファイルを追加してください（変更すると checksum 不一致でエラーになります）。
