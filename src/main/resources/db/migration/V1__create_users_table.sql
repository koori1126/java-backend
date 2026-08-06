-- 【EDB Oracle互換モードの注意点】
-- Redwood(Oracle互換)モードでは、クォートしない識別子は Oracle と同様に
-- 大文字に畳み込まれます（標準PostgreSQLは逆に小文字に畳み込む）。
-- 実行環境の compatibility_mode に関係なく常に小文字のカラム名になるよう、
-- すべての識別子をダブルクォート+小文字で統一しています。

CREATE TABLE "public"."users" (
    "id"         BIGSERIAL     NOT NULL,
    "name"       VARCHAR(50),
    "email"      VARCHAR(100),
    "del_flag"   BOOLEAN,
    "created_at" TIMESTAMP(6)  NOT NULL DEFAULT now(),
    "created_by" VARCHAR(255),
    "updated_at" TIMESTAMP(6)  NOT NULL DEFAULT now(),
    "updated_by" VARCHAR(255),
    "fastname"   VARCHAR(50),
    "familyname" VARCHAR(50),
    PRIMARY KEY ("id")
);

COMMENT ON TABLE "public"."users" IS 'ユーザー情報';
