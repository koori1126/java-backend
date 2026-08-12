# User機能

`User`リソースは、このプロジェクトにおける**サンプル的な実装**です。新しい
リソース(`Product`等)を追加する際は、まずこのドキュメントとコードをコピー元として
参考にしてください。

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
8. `src/test` にController/Serviceのテストを追加(`docs/setup.md`の
   「自動テストについて」を参照)

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
