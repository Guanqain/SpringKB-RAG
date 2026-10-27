-- SpringKB-RAG 用户表（认证模块，幂等建表）
-- 角色：首个注册者（或 Setup 初始化管理员）为 ADMIN，其余注册者为 MEMBER；
-- 默认值取 MEMBER，避免历史 DEFAULT 'ADMIN' 让后续注册自动获得管理权限。
CREATE TABLE IF NOT EXISTS kb_user (
    id            BIGSERIAL PRIMARY KEY,
    username      TEXT        NOT NULL,
    email         TEXT,
    password_hash TEXT        NOT NULL,
    role          TEXT        NOT NULL DEFAULT 'MEMBER',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE kb_user ADD COLUMN IF NOT EXISTS email TEXT;
ALTER TABLE kb_user ALTER COLUMN role SET DEFAULT 'MEMBER';

CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_user_username ON kb_user (username);
-- 邮箱唯一（忽略大小写；历史用户 email 为 NULL 不参与唯一约束）
CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_user_email ON kb_user (lower(email)) WHERE email IS NOT NULL;

-- ---------------- kb_email_code：邮箱验证码（注册 / 验证码登录 / 绑定邮箱）----------------
-- 明文保存 6 位验证码，5 分钟过期，使用后置 used=true；同邮箱 60 秒内只允许发送一次（服务层校验）。
CREATE TABLE IF NOT EXISTS kb_email_code (
    id         BIGSERIAL   PRIMARY KEY,
    email      TEXT        NOT NULL,
    code       TEXT        NOT NULL,
    purpose    TEXT        NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used       BOOLEAN     NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_kb_email_code_email ON kb_email_code (lower(email), created_at DESC);

-- ---------------- app_setting：初始化向导等键值配置 ----------------
CREATE TABLE IF NOT EXISTS app_setting (
    key        TEXT        PRIMARY KEY,
    value      TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------- kb_category：技术分类注册表 ----------------
-- 允许先建分类、后写文档；分类下的版本不单独存储，由 kb_document.version 动态聚合（可为空 → 展示「未标注」）。
CREATE TABLE IF NOT EXISTS kb_category (
    product      TEXT        PRIMARY KEY,
    display_name TEXT        NOT NULL,
    description  TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

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

-- ---------------- 文档可见性（team / private）----------------
-- team：团队可见，可进入问答引用；private：仅作者可见，且永不作为问答引用。
-- DEFAULT 'team' 同时完成历史数据回填（ADD COLUMN 时按默认值填充已有行）。
ALTER TABLE kb_document ADD COLUMN IF NOT EXISTS visibility TEXT NOT NULL DEFAULT 'team';

CREATE INDEX IF NOT EXISTS idx_kb_document_visibility ON kb_document (visibility, author);

-- ---------------- 内容审核（UGC 团队文档）----------------
-- review_status：approved（无需审核/已通过，可进入知识库与问答引用）/ pending（待管理员审核）
-- / rejected（已驳回，仅作者可见）。历史文档与官方导入默认 approved，不影响现有数据。
-- 私有文档跳过审核，写入时直接 approved；从私有切换为团队可见时重新进入 pending。
ALTER TABLE kb_document ADD COLUMN IF NOT EXISTS review_status TEXT NOT NULL DEFAULT 'approved';
ALTER TABLE kb_document ADD COLUMN IF NOT EXISTS review_note TEXT;
ALTER TABLE kb_document ADD COLUMN IF NOT EXISTS reviewed_by TEXT;
ALTER TABLE kb_document ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_kb_document_review ON kb_document (review_status, updated_at DESC)
    WHERE review_status <> 'approved';

-- ---------------- 管理端文档列表排序 ----------------
-- 列表固定 ORDER BY updated_at DESC，并常叠加 product 过滤，补齐对应的排序 / 组合扫描索引。
CREATE INDEX IF NOT EXISTS idx_kb_document_updated_at ON kb_document (updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_kb_document_product_updated ON kb_document (product, updated_at DESC);

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

-- ---------------- kb_scan_rule：脱敏扫描规则（内置规则可开关，自定义规则可增删）----------------
-- 内置规则由 ScanRuleService 启动时幂等播种（code 固定，pattern 以代码为准，避免升级时被旧库覆盖）；
-- builtin=false 的规则可由管理员自由改正则 / 删除。
CREATE TABLE IF NOT EXISTS kb_scan_rule (
    code       TEXT        PRIMARY KEY,
    label      TEXT        NOT NULL,
    pattern    TEXT        NOT NULL,
    builtin    BOOLEAN     NOT NULL DEFAULT false,
    enabled    BOOLEAN     NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------- kb_scan_log：脱敏拦截留痕（只记类型与数量，绝不记命中明文）----------------
CREATE TABLE IF NOT EXISTS kb_scan_log (
    id         BIGSERIAL   PRIMARY KEY,
    username   TEXT,
    action     TEXT        NOT NULL,   -- publish / upload / update / visibility / admin_meta / precheck
    doc_title  TEXT,
    rule_type  TEXT        NOT NULL,
    hit_count  INTEGER     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_kb_scan_log_created ON kb_scan_log (created_at DESC);

-- ---------------- kb_notification：站内通知（目前用于审核结果）----------------
-- kind：review_approved（审核通过）/ review_rejected（审核驳回，body 为理由）。
CREATE TABLE IF NOT EXISTS kb_notification (
    id         BIGSERIAL   PRIMARY KEY,
    username   TEXT        NOT NULL,
    kind       TEXT        NOT NULL,
    title      TEXT        NOT NULL,
    body       TEXT,
    doc_id     TEXT,
    read       BOOLEAN     NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_kb_notification_user ON kb_notification (username, read, created_at DESC);
