-- Web-only user service migration (one service, one DB)
-- Drops WeChat openid, enforces email/password login, adds token invalidation fields.

ALTER TABLE public.users
    DROP CONSTRAINT IF EXISTS users_openid_key;

DROP INDEX IF EXISTS idx_users_openid;

ALTER TABLE public.users
    DROP COLUMN IF EXISTS openid;

ALTER TABLE public.users
    ALTER COLUMN email SET NOT NULL,
    ALTER COLUMN password SET NOT NULL;

ALTER TABLE public.users
    ADD COLUMN token_version integer DEFAULT 0 NOT NULL,
    ADD COLUMN password_changed_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL;
