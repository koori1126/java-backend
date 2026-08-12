# backend-api

Spring Boot 製バックエンドAPIの雛形です。DBは EDB Postgres Advanced Server の
Oracle互換モード（Redwoodモード）を想定しています。

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
    │   │   ├── repository/                # DBアクセス（Spring Data JPA）
    │   │   │   └── UserRepository.java
    │   │   ├── model/
    │   │   │   ├── entity/                 # JPAエンティティ（テーブル対応）
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
    │       └── db/migration/               # Flyway マイグレーションSQL
    └── test/java/com/example/backend/...   # テスト
```

デプロイ先はAzure VM上にJREを直接インストールし、jarファイルをsystemdサービスとして
起動する構成です(Docker/Kubernetesは使用しません)。

依存の向きは `controller → service → repository` の一方向のみで、
Controllerが直接Repositoryを呼び出すことはしません。

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
`@Table`/`@Column`の`name`属性を合わせてください。

## エンドポイントの追加方法

`UserController` を例に、新しいリソース（例: `Product`）を追加する手順です。

1. **モデル追加**: `model/entity/Product.java` を作成(プレーンなPOJO)。
   `created_at`/`created_by`/`updated_at`/`updated_by`の4カラムを持つテーブルであれば、
   `AuditableModel`を継承するだけでこれらのフィールドが引き継がれる
   （`public class Product extends AuditableModel { ... }`）。JPAとは異なり
   アノテーションでのテーブル定義は不要(マッピングはMapper XML側で行う)
2. **マイグレーション追加**: `resources/db/migration/V4__create_products_table.sql` を作成
   （Flywayはファイル名の連番 `V{番号}__xxx.sql` を見てバージョン管理するため、
   既存のマイグレーションファイルは変更せず必ず新規ファイルを追加すること。
   次の番号は既存ファイルの最大値+1を確認して使う）
3. **Mapper追加**: `mapper/ProductMapper.java`（`@Mapper`を付与したインターフェース）と、
   `resources/mapper/ProductMapper.xml`(実際のSQL)を作成。`UserMapper`/`UserMapper.xml`を
   コピーして名前を置き換えるのが早い
4. **DTO追加**: `model/dto/ProductRequest.java` / `ProductResponse.java`
5. **サービス追加**: `service/ProductService.java`（インターフェース）と
   `service/impl/ProductServiceImpl.java`（実装、`@Service` を付与）
6. **コントローラー追加**: `controller/ProductController.java` を作成し、
   `@RestController` + `@RequestMapping("/api/v1/products")` を付与して
   GET/POST/PUT/DELETEのハンドラーを実装
7. 必要に応じて `exception` にリソース固有の例外を追加し、
   `GlobalExceptionHandler` にハンドラーを追加
8. `src/test` にController/Serviceのテストを追加

このパターンに従えば、既存の `User` 関連ファイル一式をほぼそのままコピーして
名前を置き換えるだけで新しいリソースを追加できます。

## CSVによるユーザー一括登録

`POST /api/v1/users/import` に、`multipart/form-data`形式で`file`というキーで
CSVファイルを送ると、複数ユーザーを一括登録できます。

**CSVの形式(1行目はヘッダー)**

```csv
name,email,firstname,familyname
山田太郎,taro@example.com,Taro,Yamada
鈴木花子,hanako@example.com,Hanako,Suzuki
```

列の順序は自由(列名でマッピングしているため)。文字コードはUTF-8を想定しています。

**Postmanでの送信方法**

- Method: `POST`
- URL: `http://localhost:8080/api/v1/users/import`
- Body: `form-data` を選択 → キー`file`(型は`File`に変更) → 値としてCSVファイルを選択

**レスポンス例**

1行ごとに検証し、エラーがあった行はスキップして処理を続行します（1行のエラーで
全体を失敗させない方式）。結果は以下のような形式で返ります。

```json
{
  "totalCount": 3,
  "successCount": 2,
  "failureCount": 1,
  "errors": [
    { "lineNumber": 3, "reason": "email: email の形式が不正です" }
  ]
}
```

`lineNumber`はCSVファイル上の行番号(ヘッダー行を1行目として数える)です。

**新しいCSV列を追加したくなったら**

`UserServiceImpl`内の`CSV_IMPORT_HEADERS`配列と、CSV読み込み処理(`importFromCsv`メソッド)を、
対象のフィールドに合わせて修正してください。

## CSVによるユーザー一覧の出力(エクスポート)

`GET /api/v1/users/export` にアクセスすると、現在登録されているユーザー
(論理削除済みを除く)をCSVファイルとしてダウンロードできます。

**出力される列**

```csv
id,name,email,firstname,familyname,created_at,created_by,updated_at,updated_by
```

取り込み用(`import`)の4列より多く、システム管理列(`id`/`created_at`等)も
含めた全項目を出力します(参照・バックアップ用途を想定)。このファイルを
そのまま`import`に使う場合は、`name`/`email`/`firstname`/`familyname`の
4列だけを抜き出して使ってください。

**ブラウザ/Postmanでの取得方法**

- ブラウザで直接 `http://localhost:8080/api/v1/users/export` を開くとダウンロードされます
- Postmanの場合: Method `GET`、URLは同上。レスポンスの「Save Response」→
  「Save to a file」でファイルとして保存できます

文字コードはUTF-8(BOM付き)で出力しているため、Excelでそのまま開いても
日本語が文字化けしません。

**出力する列を変更したくなったら**

`UserServiceImpl`内の`CSV_EXPORT_HEADERS`配列と、`exportToCsv`メソッドを、
対象のフィールドに合わせて修正してください。

## 接続先の切り替え方法(開発用PostgreSQL ⇔ 開発環境のEDB等)

`application-local.yml`はgit管理対象外です。接続先を切り替えたい場合は、
このファイルの`spring.datasource`配下を直接書き換えてください
(コードの変更は不要です)。

**初回セットアップ**

1. `src/main/resources/application-local.yml.example` をコピーして、
   同じフォルダに `application-local.yml` という名前で保存する
2. 接続先(`url`/`username`/`password`)を実際の値に書き換える

**別の接続先(開発環境のEDB等)に切り替えたい時**

`application-local.yml`内の`url`/`username`/`password`を書き換えるだけです。

```yaml
spring:
  datasource:
    url: jdbc:postgresql://dev-edb.example.internal:5444/vppsys_dev
    username: your_username
    password: your_password
```

普段の開発用PostgreSQLに戻す時は、また元の値に書き換えてください。

VPN経由でないと繋がらない接続先(開発環境のEDB等)を使う場合は、
事前にVPN接続を済ませてから起動してください。

**取り扱いに関する注意**

`application-local.yml`には実際のパスワードが平文で書かれます。このファイル自体は
`.gitignore`で除外済みなので誤ってコミットされる心配はありませんが、他の人に共有する
(チャットに貼る、他のPCにコピーする等)際はパスワードが含まれている点に注意してください。

## ローカルでの動作確認

```bash
# 1. ビルド
mvn clean package

# 2. 起動(application-local.yml が使われる)
java -jar target/backend-api-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

起動後、以下にアクセスできます。

- Swagger UI: http://localhost:8080/swagger-ui.html
- ヘルスチェック: http://localhost:8080/actuator/health

## デプロイ方法(Azure VM + JRE直接実行)

Docker/Kubernetesは使用せず、Azure VM(RHEL 10想定)にJREを直接インストールし、
jarファイルをsystemdサービスとして起動する構成です。

### 1. VM側の事前準備(初回のみ)

```bash
# JRE(JDKではなくJRE=実行専用でよい)をインストール
sudo dnf install java-21-openjdk-headless

# アプリ専用ユーザーを作成(root運用を避ける)
sudo useradd --system --no-create-home backendapp

# 配置先ディレクトリと、設定ファイル用ディレクトリを作成
sudo mkdir -p /opt/backend-api/config /opt/backend-api/logs
sudo chown -R backendapp:backendapp /opt/backend-api
```

### 2. jarファイルのビルド

開発機(または社内のビルドサーバー)で以下を実行し、jarファイルを作成します。

**重要:** 必ず`-Pexclude-local-config`を付けてビルドしてください。これを付けないと、
開発用DBのパスワードが書かれた`application-local.yml`がjarの中にそのまま
同梱されてしまいます(設定として使われることはありませんが、jarを展開すれば
中身が見えてしまうため、本番配布用のjarには含めるべきではありません)。

```bash
mvn clean package -Pexclude-local-config
# target/backend-api-0.0.1-SNAPSHOT.jar が生成される
```

### 3. VMへの配置

```bash
# jarをVMへ転送(scpの例。社内の配布手順に合わせて置き換えてください)
scp target/backend-api-0.0.1-SNAPSHOT.jar user@<VMのIP>:/tmp/

# VM上でリネームして配置場所へ移動
ssh user@<VMのIP>
sudo mv /tmp/backend-api-0.0.1-SNAPSHOT.jar /opt/backend-api/backend-api.jar
sudo chown backendapp:backendapp /opt/backend-api/backend-api.jar
```

### 4. DB接続情報を含む設定ファイルの配置

**この手順は初回のみ、かつVM上でのみ行います。**この設定ファイルはjarにもgit
リポジトリにも含まれません。DB接続情報を含む機密ファイルなので、VM上に直接作成し、
それ以降のデプロイ(jarの更新)ではこのファイルには触れません。

Spring Bootは起動時、jarと同じ階層の`config/`フォルダを自動的に読みに行くため、
`/opt/backend-api/config/application-prod.yml`を配置しておくだけで、
コードやjarを一切変更せずにDB接続情報を注入できます。

```bash
sudo vi /opt/backend-api/config/application-prod.yml
```

内容は`deploy/application-prod.yml.example`を参考に、実際の値を記入してください。

```yaml
spring:
  datasource:
    url: jdbc:postgresql://<EDBのホスト名>:<ポート>/<DB名>
    username: <ユーザー名>
    password: <パスワード>
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 30000
```

権限を絞っておきます。

```bash
sudo chown backendapp:backendapp /opt/backend-api/config/application-prod.yml
sudo chmod 600 /opt/backend-api/config/application-prod.yml
```

### 5. systemdサービスとして登録・起動

```bash
sudo cp deploy/backend-api.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now backend-api

# 状態確認
sudo systemctl status backend-api

# ログ確認
sudo journalctl -u backend-api -f
```

### 6. 2回目以降のデプロイ(更新)

```bash
# 新しいjarをVMへ転送・配置
scp target/backend-api-0.0.1-SNAPSHOT.jar user@<VMのIP>:/tmp/
ssh user@<VMのIP>
sudo systemctl stop backend-api
sudo mv /tmp/backend-api-0.0.1-SNAPSHOT.jar /opt/backend-api/backend-api.jar
sudo chown backendapp:backendapp /opt/backend-api/backend-api.jar
sudo systemctl start backend-api
```

手作業が多い場合は、この一連の流れ(ビルド→転送→再起動)をシェルスクリプトや
CI/CDパイプライン(社内のツールに合わせて)に置き換えて自動化することをおすすめします。

### 7. Azure側での確認事項

- **NSG(ネットワークセキュリティグループ)**でアプリのポート(デフォルト8080)への
  インバウンドを許可する必要があります
- 外部公開する場合は、VM上に直接ポートを開けるのではなく、**Nginx等のリバースプロキシ
  を前段に置いてTLS終端する**構成が一般的です(必要であれば別途構成をご用意します)
- VMの再起動時も自動的にアプリが立ち上がるよう、`systemctl enable`をしておくこと
  (上記手順に含まれています)

### 8. 設定ファイル(`config/application-prod.yml`)について

`SPRING_PROFILES_ACTIVE=prod`はsystemdユニット内に直接設定済みです(機密情報では
ないため、ユニットファイル自体にコード管理されています)。

DB接続情報は`/opt/backend-api/config/application-prod.yml`(手順4で作成)にのみ
存在し、jar・gitリポジトリのどちらにも含まれません。接続情報を変更したい場合は、
このファイルを直接編集して`systemctl restart backend-api`するだけで反映されます
(jarの再ビルド・再配置は不要です)。

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
