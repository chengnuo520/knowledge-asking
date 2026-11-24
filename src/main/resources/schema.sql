CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS rag_documents (
    doc_id UUID PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    mime_type TEXT NOT NULL,
    size BIGINT NOT NULL,
    content_hash TEXT NOT NULL,
    chunk_count INT NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS rag_chunks (
    chunk_id UUID PRIMARY KEY,
    doc_id UUID NOT NULL REFERENCES rag_documents(doc_id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    embedding_text TEXT NOT NULL,
    outline_path TEXT[] NOT NULL DEFAULT '{}',
    source_file TEXT NOT NULL,
    embedding vector(1024),
    CONSTRAINT uk_rag_chunks_doc_index UNIQUE (doc_id, chunk_index)
);

CREATE INDEX IF NOT EXISTS idx_rag_chunks_doc_id ON rag_chunks(doc_id);

-- ===== 账号与会话（功能点三） =====
-- role 用 TEXT 而不是枚举类型：加角色不用改类型，代价是应用层要校验取值
CREATE TABLE IF NOT EXISTS app_users (
    user_id       UUID PRIMARY KEY,
    username      TEXT NOT NULL UNIQUE,          -- 登录名，唯一
    password_hash TEXT NOT NULL,                 -- BCrypt，永不存明文
    role          TEXT NOT NULL,                 -- ADMIN / USER
    display_name  TEXT NOT NULL,
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,  -- 种子账号首次登录必须改密
    created_at    TIMESTAMPTZ NOT NULL,
    last_login_at TIMESTAMPTZ
);

-- 会话表存的是 token 的 SHA-256，不存 token 本身：
-- 库被读走也无法拿来冒充登录
CREATE TABLE IF NOT EXISTS app_sessions (
    token_hash   TEXT PRIMARY KEY,
    user_id      UUID NOT NULL REFERENCES app_users(user_id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_app_sessions_user ON app_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_app_sessions_expires ON app_sessions(expires_at);
