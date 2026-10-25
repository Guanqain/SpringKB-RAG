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

-- SpringKB-RAG 文档表（文档级元数据）
-- 字段依据真实文档站元数据修订（vuejs.org / docs.spring.io 样本，见 docs/devlog）：
--   多产品线(product) × 来源站点(origin) × 权威性(authority) × 版本(version) × 语言(language) × 文档类型(doc_type)
CREATE TABLE IF NOT EXISTS kb_document (
    id            TEXT PRIMARY KEY,                        -- {product}-{version}-{doc_key 规范化}
    product       TEXT        NOT NULL,                    -- 产品线：spring-boot / spring-ai / vue / ...
    title         TEXT        NOT NULL,
    origin        TEXT        NOT NULL,                    -- 来源站点：spring.io / vuejs.org / stackoverflow
    authority     TEXT        NOT NULL DEFAULT 'official', -- 权威性：official / community
    version       TEXT        NOT NULL,                    -- 版本：4.2-SNAPSHOT / 3.x / latest
    doc_key       TEXT        NOT NULL,                    -- 业务唯一键：URL 相对路径
    doc_type      TEXT        NOT NULL DEFAULT 'reference',-- guide / api / tutorial / reference / howto
    language      TEXT        NOT NULL DEFAULT 'en',
    section       TEXT,                                    -- 章节：actuator / essentials
    active        BOOLEAN     NOT NULL DEFAULT true,
    last_modified TIMESTAMPTZ,                             -- 源站最后修改时间（sitemap lastmod）
    fetched_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    chunk_count   INTEGER     NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_document ON kb_document (product, origin, authority, version, doc_key);

-- ---------------- kb_chunk：文档块级表 ----------------
-- id 与 vector_store.id 同值（chunk-{doc_id}-{ordinal}），检索命中向量后按 id 直取原文（回源溯源）
CREATE TABLE IF NOT EXISTS kb_chunk (
    id               TEXT        PRIMARY KEY,
    doc_id           TEXT        NOT NULL REFERENCES kb_document (id) ON DELETE CASCADE,
    ordinal          INTEGER     NOT NULL,
    heading_path     TEXT        NOT NULL DEFAULT '',
    content_markdown TEXT        NOT NULL,
    content_text     TEXT        NOT NULL,
    token_count      INTEGER     NOT NULL DEFAULT 0,
    image_refs       JSONB       NOT NULL DEFAULT '[]',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_kb_chunk_doc_ordinal UNIQUE (doc_id, ordinal)
);

-- ---------------- 文档作者（UGC 归属）----------------
-- 历史表结构没有 author 列，幂等 ALTER 补齐；爬虫/管理端导入的文档保持 NULL。
ALTER TABLE kb_document ADD COLUMN IF NOT EXISTS author TEXT;

CREATE INDEX IF NOT EXISTS idx_kb_document_author ON kb_document (author, updated_at DESC)
    WHERE author IS NOT NULL;

-- ---------------- kb_conversation：用户会话（问答历史）----------------
-- 会话按登录用户名隔离；ttl 清理由 ConversationService 在读写时顺带执行（30 天无新消息即删）。
CREATE TABLE IF NOT EXISTS kb_conversation (
    id         BIGSERIAL   PRIMARY KEY,
    username   TEXT        NOT NULL,
    title      TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_kb_conversation_user ON kb_conversation (username, updated_at DESC);

-- ---------------- kb_message：会话内问答消息 ----------------
-- 每行 = 一轮问答（question + answer + sources JSONB）；超过窗口大小的旧行由服务层裁剪。
CREATE TABLE IF NOT EXISTS kb_message (
    id              BIGSERIAL   PRIMARY KEY,
    conversation_id BIGINT      NOT NULL REFERENCES kb_conversation (id) ON DELETE CASCADE,
    question        TEXT        NOT NULL,
    answer          TEXT        NOT NULL,
    found           BOOLEAN     NOT NULL DEFAULT true,
    version         TEXT,
    sources         JSONB       NOT NULL DEFAULT '[]',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_kb_message_conversation ON kb_message (conversation_id, id);

-- ---------------- 文档点赞 / 收藏（登录用户级，计数公开）----------------
CREATE TABLE IF NOT EXISTS kb_doc_like (
    doc_id     TEXT        NOT NULL REFERENCES kb_document (id) ON DELETE CASCADE,
    username   TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (doc_id, username)
);

CREATE TABLE IF NOT EXISTS kb_doc_favorite (
    doc_id     TEXT        NOT NULL REFERENCES kb_document (id) ON DELETE CASCADE,
    username   TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (doc_id, username)
);

CREATE INDEX IF NOT EXISTS idx_kb_doc_favorite_user ON kb_doc_favorite (username, created_at DESC);
