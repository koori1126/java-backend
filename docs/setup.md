# 環境構築・ローカルでの実行方法

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

**この方法を使う理由**: 本番のsystemdユニット(`docs/deployment.md`参照)も
`java -jar`でjarを直接起動しているため、ローカルでの確認方法を本番と
完全に一致させています。「ローカルで動いたのと同じ起動方法で本番も動く」
という状態を保つのが狙いです。

**日々のコーディング中(何度も再起動して確認したい場合)**

コードを変更するたびに`mvn clean package`でjarを作り直すのは手間なので、
開発中はVSCodeの「Run」ボタン(`BackendApplication.java`のmainメソッド上に
表示される)、または以下のコマンドを使う方が効率的です。

```bash
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

これはコンパイル済みのクラスを直接実行する方式で、jarの作成を経由しないため
反映が速くなります。アプリとしての動作(プロファイル、DB接続先等)は
`java -jar`と変わりません。ただし、**本番投入前の最終確認では、必ず
`java -jar`の方法で一度動作確認してください**(本番と完全に同じ起動方法で
確認しておくのが安全です)。

## 自動テストについて

テストは3種類に分かれています。

| 種類 | 対象 | DB接続 | 実行コマンド |
|---|---|---|---|
| Service層の単体テスト | `UserServiceImplTest`。ビジネスロジック(email重複チェック、論理削除、CSV検証等) | 不要(`UserMapper`をMockitoでモック化) | `mvn test` |
| Controller層のテスト | `UserControllerTest`。HTTPステータス、バリデーション等 | 不要(`UserService`をモック化) | `mvn test` |
| Mapperの統合テスト | `UserMapperTest`。実際のSQL(`UserMapper.xml`)が正しく動くか | **必要**(ローカルPC上にインストールしたPostgreSQLに接続) | `mvn test -Pintegration-test` |

**通常の`mvn test`では、Mapperの統合テスト(`*MapperTest`という名前のクラス)は実行されません。**
DBが無い環境でも、単体テストだけは常に安全に実行できるようにするための区分けです。

**Mapperの統合テストのセットアップ(初回のみ)**

1. ローカルPCにPostgreSQL(バージョンは本番のEDBに合わせて16系を推奨)をインストールする
2. テスト専用のDBを作成する(**開発用の共有DBとは完全に別にすること**。テストの実行でデータが書き込まれる/削除されるため)
   ```sql
   CREATE DATABASE tesla_test;
   ```
3. `src/main/resources/application-test.yml.example` をコピーして、同じフォルダに
   `application-test.yml` として保存し、接続情報(パスワード等)を記入する
   (このファイルはgit管理対象外です)

テーブルは初回テスト実行時にFlywayが自動的に作成するので、事前にDDLを流す必要はありません。

**Mapperの統合テストを実行する場合**

```bash
mvn test -Pintegration-test
```

各テストメソッドには`@Transactional`が付いており、テスト終了時に自動的にロールバックされます。実行してもDBにテストデータが残り続けることはありません。

**なぜ開発用の共有DBではなく、ローカルPostgreSQLを使うのか**

開発用の共有DB(`192.168.20.246`等)に対して自動テストを実行すると、他の開発者が
入れたデータに影響を与えたり、VPN接続の有無に実行結果が左右されたりします。
自動テストは「いつ・誰が・どの環境から実行しても同じ結果になる」ことが重要なため、
各開発者のローカルPC上の、テスト専用のDBを使う方式にしています。

**新しいMapperを追加した時**

`ProductMapperTest`のように、クラス名の末尾を`MapperTest`にしてください。この命名規則によって、`mvn test`では自動的に除外され、`mvn test -Pintegration-test`でのみ実行される対象になります。

**Docker/Testcontainersについて**

本来、Mapperの統合テストは[Testcontainers](https://testcontainers.com/)(テスト実行時に使い捨てのDBコンテナを自動起動する仕組み)を使うのが、環境差異を気にせずCI環境でもそのまま動作するという点で理想的です。ただし現在の開発環境ではDocker Desktopが使えない制約があるため、ローカルPostgreSQLに直接接続する方式にしています。将来Docker(WSL2上のDocker Engine等)が使える環境が整ったら、Testcontainersへの切り替えを検討してください。

**将来CI(GitHub Actions等)を導入する場合**

CI環境にも同様に「テスト実行中だけPostgreSQLを起動する」設定(GitHub Actionsの`services`機能等)を追加すれば、今回の「ローカルのPostgreSQLに接続する」という前提を変えずにそのままCIでも実行できます。
