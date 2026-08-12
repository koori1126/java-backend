# デプロイ方法(Azure VM + JRE直接実行)

Docker/Kubernetesは使用せず、Azure VM(RHEL 10想定)にJREを直接インストールし、
jarファイルをsystemdサービスとして起動する構成です。

## 1. VM側の事前準備(初回のみ)

```bash
# JRE(JDKではなくJRE=実行専用でよい)をインストール
sudo dnf install java-21-openjdk-headless

# アプリ専用ユーザーを作成(root運用を避ける)
sudo useradd --system --no-create-home backendapp

# 配置先ディレクトリと、設定ファイル用ディレクトリを作成
sudo mkdir -p /opt/backend-api/config /opt/backend-api/logs
sudo chown -R backendapp:backendapp /opt/backend-api
```

## 2. jarファイルのビルド

開発機(または社内のビルドサーバー)で以下を実行し、jarファイルを作成します。

**重要:** 必ず`-Pexclude-local-config`を付けてビルドしてください。これを付けないと、
開発用DBのパスワードが書かれた`application-local.yml`がjarの中にそのまま
同梱されてしまいます(設定として使われることはありませんが、jarを展開すれば
中身が見えてしまうため、本番配布用のjarには含めるべきではありません)。

```bash
mvn clean package -Pexclude-local-config
# target/backend-api-0.0.1-SNAPSHOT.jar が生成される
```

## 3. VMへの配置

```bash
# jarをVMへ転送(scpの例。社内の配布手順に合わせて置き換えてください)
scp target/backend-api-0.0.1-SNAPSHOT.jar user@<VMのIP>:/tmp/

# VM上でリネームして配置場所へ移動
ssh user@<VMのIP>
sudo mv /tmp/backend-api-0.0.1-SNAPSHOT.jar /opt/backend-api/backend-api.jar
sudo chown backendapp:backendapp /opt/backend-api/backend-api.jar
```

## 4. DB接続情報を含む設定ファイルの配置

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

## 5. systemdサービスとして登録・起動

```bash
sudo cp deploy/backend-api.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now backend-api

# 状態確認
sudo systemctl status backend-api

# ログ確認
sudo journalctl -u backend-api -f
```

## 6. 2回目以降のデプロイ(更新)

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

## 7. Azure側での確認事項

- **NSG(ネットワークセキュリティグループ)**でアプリのポート(デフォルト8080)への
  インバウンドを許可する必要があります
- 外部公開する場合は、VM上に直接ポートを開けるのではなく、**Nginx等のリバースプロキシ
  を前段に置いてTLS終端する**構成が一般的です(必要であれば別途構成をご用意します)
- VMの再起動時も自動的にアプリが立ち上がるよう、`systemctl enable`をしておくこと
  (上記手順に含まれています)

## 8. 設定ファイル(`config/application-prod.yml`)について

`SPRING_PROFILES_ACTIVE=prod`はsystemdユニット内に直接設定済みです(機密情報では
ないため、ユニットファイル自体にコード管理されています)。

DB接続情報は`/opt/backend-api/config/application-prod.yml`(手順4で作成)にのみ
存在し、jar・gitリポジトリのどちらにも含まれません。接続情報を変更したい場合は、
このファイルを直接編集して`systemctl restart backend-api`するだけで反映されます
(jarの再ビルド・再配置は不要です)。
