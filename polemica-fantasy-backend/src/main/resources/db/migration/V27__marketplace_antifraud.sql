ALTER TABLE telegram_user
    ADD COLUMN marketplace_banned BOOLEAN NOT NULL DEFAULT FALSE;
