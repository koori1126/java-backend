# 認証(Okta / OIDC)

## 採用している方式

BFF(フロントエンド専用の中間サーバー)は採用せず、**Spring Boot自身がOAuth2クライアントとしてOktaとやり取りし、セッションCookieでログイン状態を管理する方式**にしています。フロントエンドのJavaScriptは、Oktaのトークンに一切触れません。

理由の詳細は、実装方針を検討した際のやり取りを参照してください。要点は以下の通りです。

- ロードバランサーのセッションアフィニティが有効なため、Spring Bootのデフォルトのセッション管理(サーバーのメモリ上に保持)のままで成立する
- 本番はNginxでフロント・バックエンドを同一オリジンに統合する方針のため、セッションCookieの送受信で複雑な設定が不要

## ① Okta管理画面での設定

概要のみ記載します(実際の画面操作の詳細は別途)。

1. Applications → Create App Integration
2. Sign-in method: `OIDC - OpenID Connect`、Application type: `Web Application`
   (フロントエンドではなく**バックエンドがOAuth2クライアントになる**ため、SPAではなくWeb Applicationを選択する)
3. Sign-in redirect URIs: `http://localhost:8080/login/oauth2/code/okta`(Spring Securityの固定URLパターン。本番用のURLは別途追加する)
4. Sign-out redirect URIs: `http://localhost:3000/`(フロントエンドのトップページ)
5. Assignments: 自分の所属するグループを割り当てる
6. 保存後、`General`タブから**Client ID**・**Client Secret**を控える
7. `Security` → `API` → `Authorization Servers` → `default` の**Issuer URI**を控える

## ② バックエンド実装(実施済み)

### 追加した依存関係

`pom.xml`に`spring-boot-starter-oauth2-client`を追加しています。

### 設定ファイル

`application-local.yml`に、①で控えた3点セット(Client ID / Client Secret / Issuer URI)を記入してください。

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          okta:
            client-id: <Client IDを入力>
            client-secret: <Client Secretを入力>
            scope: openid, profile, email
        provider:
          okta:
            issuer-uri: https://<あなたのOktaドメイン>/oauth2/default
```

**`application-test.yml`にも同じ設定が必要です。** `SecurityConfig`がOAuth2の設定Beanを必要とするため、値が無いとMapperの統合テスト(`mvn test -Pintegration-test`)がコンテキスト起動に失敗します。実際にOktaへログインするテストではないので、`application-local.yml`と同じ値をそのまま使って構いません。

### `SecurityConfig`(`config/SecurityConfig.java`)

- `/actuator/health/**`と`/swagger-ui/**`・`/v3/api-docs/**`は認証不要、それ以外の全リクエストは認証必須にしています
- ログイン処理そのもの(`oauth2Login()`)はSpring Securityの標準機能に任せています
- **CSRF対策**: `CookieCsrfTokenRepository`を使い、CSRFトークンを`XSRF-TOKEN`というCookieで配布する方式にしています。フロントエンド側は、このCookieの値を読み取り、更新系リクエスト(POST/PUT/DELETE)のヘッダー`X-XSRF-TOKEN`に載せて送り返す必要があります(③のフロントエンド実装で対応します)
- **ログアウト**: `OidcClientInitiatedLogoutSuccessHandler`を使い、ログアウト時にOkta側のセッションも一緒に終了させ、`app.post-logout-redirect-uri`(フロントエンドのURL)にブラウザを戻します

### CORS設定の変更(`config/WebConfig.java`)

セッションCookieを別オリジン(フロント`:3000` ⇔ バック`:8080`)で送受信できるよう、`allowCredentials(true)`を追加しています。

### 既存テストへの影響

`UserControllerTest`(`@WebMvcTest`)は、Controller層のマッピング確認が目的であり認証の検証が目的ではないため、`@AutoConfigureMockMvc(addFilters = false)`を追加してSpring Securityのフィルタを無効化しています。

## デプロイ時にセッションが切れる問題(既知の制約)

サーバーを再起動(デプロイ)すると、そのサーバーのメモリ上にあったセッション情報が失われ、その時点でログインしていたユーザーは全員ログアウトされます。現状の運用(サーバー1台、デプロイ頻度もそこまで高くない)であれば実用上問題ありませんが、将来「デプロイ中でもログイン状態を維持したい」という要件が出てきたら、Spring Session JDBC(セッションをDB側に保存する方式)への切り替えを検討してください。

## ③ フロントエンド実装(未実施)

以下を実装する必要があります。

- ログインボタン: `fetch`ではなく、`<a href="/oauth2/authorization/okta">`のような通常のリンク(画面遷移)として実装する
- API呼び出し全般に`credentials: "include"`を追加し、セッションCookieを送信する
- 更新系リクエスト(POST/PUT/DELETE)には、`XSRF-TOKEN`Cookieの値を`X-XSRF-TOKEN`ヘッダーに載せて送信する
- ログアウトボタン: バックエンドのログアウトエンドポイントへの遷移として実装する

## 本番環境向けの追加対応(未実施)

- Azure VMのドメインが決まった時点で、Okta側の「Sign-in redirect URIs」に本番用のコールバックURLを追加する
- `config/application-prod.yml`(VM上の外部ファイル)に、本番用のOkta接続情報を追加する
