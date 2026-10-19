-- SpringKB-RAG 用户表（认证模块，幂等建表）
CREATE TABLE IF NOT EXISTS kb_user (
    id            BIGSERIAL PRIMARY KEY,
    username      TEXT        NOT NULL,
    password_hash TEXT        NOT NULL,
    role          TEXT        NOT NULL DEFAULT 'ADMIN',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_user_username ON kb_user (username);
