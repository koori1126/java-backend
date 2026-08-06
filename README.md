# backend-api

Spring Boot 製バックエンドAPIの雛形です。DBは EDB Postgres Advanced Server の
Oracle互換モード（Redwoodモード）を想定しています。

## ディレクトリ構成

```
backend-api/
├── pom.xml
├── .env.example                        # ローカル接続情報の雛形(コピーして.envとして使う)
├── deploy/                            # Azure VMへのデプロイ関連ファイル
│   ├── backend-api.service            # systemdユニットファイル
│   └── backend-api.env.example        # 環境変数ファイルの雛形
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
Maven Central から取得可能）を使用しています。Hibernateの方言も
`PostgreSQLDialect` を使用しており、通常のCRUD用途ではこれで問題なく動作します。

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

1. **エンティティ追加**: `model/entity/Product.java` を作成し `@Entity` を付与
2. **マイグレーション追加**: `resources/db/migration/V2__create_products_table.sql` を作成
   （Flywayはファイル名の連番 `V2__xxx.sql` を見てバージョン管理するため、
   既存のマイグレーションファイルは変更せず必ず新規ファイルを追加すること）
3. **リポジトリ追加**: `repository/ProductRepository.java`（`JpaRepository<Product, Long>` を継承。
   `User`と同様、IDは自動インクリメント(`BIGSERIAL` + `Long`)を採用しています）
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

## 接続先の切り替え方法(開発用PostgreSQL ⇔ 開発環境のEDB等)

コードや`application-local.yml`を書き換える必要はありません。接続情報はすべて
環境変数(`DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD`)で
上書きできるようになっており、デフォルトでは開発用に用意されている既存の
PostgreSQL(`192.168.20.246:5432` / db名: `db01`)に接続します。

**手順**

1. `.env.example` をコピーして `.env` を作成する(`.env`はgit管理対象外)
2. デフォルトの開発用PostgreSQLに繋ぐだけなら、パスワードだけ記入すればOK
3. 別の接続先(開発環境のEDB等)に切り替えたい時は、`.env`内の値を書き換える
4. VSCodeで `BackendApplication.java` を開き、実行/デバッグ時に
   `.vscode/launch.json` の構成(`.env`を自動で読み込む設定済み)を選んで起動する

ターミナルから起動する場合は、環境変数を直接指定します(PowerShellの例)。

```powershell
$env:DB_HOST="dev-edb.example.internal"
$env:DB_PORT="5444"
$env:DB_NAME="vppsys_dev"
$env:DB_USERNAME="your_username"
$env:DB_PASSWORD="your_password"
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

`.env`を既存PostgreSQL用の値に戻せば、次回起動時はそちらに戻ります。
接続先を切り替えるたびにYAMLやコードを触る必要はありません。

VPN経由でないと繋がらない接続先(開発環境のEDB等)を使う場合は、
事前にVPN接続を済ませてから上記の環境変数を設定してください。

## ローカルでの動作確認

```bash
# 1. ビルド
mvn clean package

# 2. 起動（application-local.yml が使われる。.envの値を読み込むにはVSCodeのlaunch.jsonか、
#    direnv等で環境変数を読み込んだ状態のターミナルから実行してください）
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

# 配置先ディレクトリを作成
sudo mkdir -p /opt/backend-api
sudo chown backendapp:backendapp /opt/backend-api
```

### 2. jarファイルのビルド

開発機(または社内のビルドサーバー)で以下を実行し、jarファイルを作成します。

```bash
mvn clean package
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

### 4. 環境変数ファイルの配置

`deploy/backend-api.env.example` を参考に、実際の接続情報を記入したファイルを
`/opt/backend-api/backend-api.env` として配置します(このファイルはgit管理対象外)。

```bash
sudo cp deploy/backend-api.env.example /opt/backend-api/backend-api.env
sudo vi /opt/backend-api/backend-api.env   # 実際の値に書き換える
sudo chown backendapp:backendapp /opt/backend-api/backend-api.env
sudo chmod 600 /opt/backend-api/backend-api.env
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

### 8. 環境変数(本番/検証環境)

`application-prod.yml` は以下の環境変数を参照します。`backend-api.env` に記入してください。

| 変数名 | 内容 |
|---|---|
| `DB_HOST` | EDBのホスト名 |
| `DB_PORT` | 通常 5444 |
| `DB_NAME` | データベース名 |
| `DB_USERNAME` | 接続ユーザー |
| `DB_PASSWORD` | 接続パスワード |
| `SPRING_PROFILES_ACTIVE` | `prod` を指定 |

## Flywayマイグレーションについて

`src/main/resources/db/migration/` 配下の `V{番号}__説明.sql` が起動時に自動適用されます。
既に適用済みのファイルの内容は変更せず、変更が必要な場合は新しいバージョン番号の
ファイルを追加してください（変更すると checksum 不一致でエラーになります）。
