-- Maps each Telegram user to a forum topic in the support supergroup (Bot API message_thread_id).
CREATE TABLE telegram_support_topic (
    id                      BIGSERIAL PRIMARY KEY,
    telegram_user_id        BIGINT NOT NULL UNIQUE REFERENCES telegram_user (id),
    forum_message_thread_id INTEGER NOT NULL UNIQUE,
    created_at              TIMESTAMP NOT NULL
);
