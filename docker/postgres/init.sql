-- pgvector 镜像已预装扩展；此处显式启用，供 Spring AI PgVectorStore 使用
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
