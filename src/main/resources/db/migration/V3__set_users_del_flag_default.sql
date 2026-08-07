-- del_flag を NOT NULL DEFAULT false に統一する。
-- (MyBatis化に伴い、JPAのライフサイクルコールバックでの自動セットができなくなるため、
--  DB側のDEFAULTに一本化する。既存でNULLのレコードがあれば先にfalseへ寄せる)
UPDATE "public"."users" SET "del_flag" = false WHERE "del_flag" IS NULL;

ALTER TABLE "public"."users"
    ALTER COLUMN "del_flag" SET DEFAULT false,
    ALTER COLUMN "del_flag" SET NOT NULL;
