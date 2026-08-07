-- 同時リクエストによるemail重複登録を防ぐため、DB側にも一意制約を追加する。
-- (アプリ側のチェックだけでは、並列リクエストのタイミングによってすり抜ける可能性があるため)
ALTER TABLE "public"."users"
    ADD CONSTRAINT "users_email_unique" UNIQUE ("email");
