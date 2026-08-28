# backend-api

Spring Boot 製バックエンドAPIの雛形です。DBは EDB Postgres Advanced Server の
Oracle互換モード（Redwoodモード）を想定しています。

## 技術構成

- **Java 21 + Spring Boot 3.5.15**
- **MyBatis**(DBアクセス。JPA/Hibernateではない)
- **Log4j2**(ロギング。Logbackではない)
- **PostgreSQL JDBC ドライバ**(本番はEDB Postgres Advanced Server。ワイヤプロトコル互換のため標準ドライバで接続)
- **Flyway**(DBマイグレーション)
- **Spotless(google-java-format)** / **SpotBugs** / **Checkstyle**(フォーマッター・静的解析。詳細はdocs/setup.md)
- **Spring Security(OAuth2 Client)**: Okta(OIDC)によるログイン認証。BFF不採用、セッションCookie方式(詳細はdocs/features/auth.md)
- デプロイ先: Azure VM(Docker/Kubernetesは使用せず、JREを直接インストールしてsystemdで運用)

## ドキュメント

詳細は用途別に `docs/` 配下にまとめています。

| ドキュメント | 内容 |
|---|---|
| [docs/setup.md](./docs/setup.md) | 環境構築・ローカルでの実行方法・自動テストの実行方法 |
| [docs/architecture.md](./docs/architecture.md) | 設計思想(ディレクトリ構成、DBドライバ選定理由、Oracle互換モードの注意、ログ/並行処理/エラーハンドリングの設計、Flywayについて) |
| [docs/deployment.md](./docs/deployment.md) | Azure VMへのデプロイ手順 |
| [docs/features/user.md](./docs/features/user.md) | Userリソースの機能詳細(エンドポイント追加方法、CSV入出力)。新しいリソースを追加する際のコピー元 |
| [docs/features/auth.md](./docs/features/auth.md) | Okta(OIDC)によるログイン認証の設定・実装内容 |

## クイックスタート

```bash
# 1. ローカル用の接続情報を用意する(初回のみ。詳細は docs/setup.md)
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
# → application-local.yml を編集して接続情報を記入

# 2. ビルド・起動
mvn clean package
java -jar target/backend-api-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

起動後、以下にアクセスできます。

- Swagger UI: http://localhost:8080/swagger-ui.html
- ヘルスチェック: http://localhost:8080/actuator/health

新しいリソース(Product等)を追加する場合は、[docs/features/user.md](./docs/features/user.md)の手順に沿って、`User`関連ファイルをコピーするところから始めてください。
