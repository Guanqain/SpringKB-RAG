# Agent.md — SpringKB-RAG 项目记忆锚点

> **本文件是 AI 协作开发的核心记忆文件（Memory Anchor）**。每次会话开始、切换任务、或处理与本项目相关的请求时，AI 应优先阅读本文件以获得上下文。
> **问题解决记录规则**：凡遇到问题并解决，AI 必须把「问题描述 / 根因 / 解决方案 / 耗时」追加到 [开发日志](./docs/devlog/)，并更新第 9 节日志索引。日志按日期分文件，命名为 `YYYY-MM-DD.md`。

---

## 1. 项目一句话

Spring 生态 + RAG 的知识库问答系统：多版本文档管理、**版本过滤**、**权威性加权**（official / community）、**无答案兜底**。

## 2. 技术栈速览

| 层 | 技术 | 版本 / 说明 |
| --- | --- | --- |
| 后端 | Spring Boot | 4.0.8（Java 17） |
| AI 框架 | Spring AI | 2.0.0（BOM 管理） |
| Chat | DeepSeek | `deepseek-chat` |
| Embedding | 阿里云百炼（OpenAI 兼容） | `text-embedding-v4` / 1024 维 |
| 向量库 | PostgreSQL 16 + pgvector | HNSW / COSINE_DISTANCE |
| 认证 | Spring Security 7 + JWT | jjwt 0.12.6 / HS256 / 无状态 |
| 前端 | Vue 3 + Vite 5 + Element Plus | Pinia + Vue Router + axios |
| 部署编排 | docker-compose | `rag-postgres` 容器 : `63892` |

## 3. 目录结构

```
springKB/
├── src/main/java/com/guangqian/springkb/
│   ├── controller/      # 接口层（Auth / Admin / RagDemo）
│   ├── service/         # 业务层（AuthService）
│   ├── repository/      # 数据访问层（UserRepository，JdbcTemplate）
│   ├── entity/          # 实体（User）
│   ├── dto/             # 请求/响应对象（LoginRequest / AuthResponse / UserDto ...）
│   ├── security/        # JWT 工具与过滤器（JwtUtil / JwtAuthFilter）
│   ├── config/          # Spring 配置（SecurityConfig / ChatClientConfig）
│   ├── crawler/         # 文档爬虫（source adapter：Vue / SpringAI / SpringBoot）
│   ├── common/          # 通用（ApiResponse 统一响应）
│   └── demo/            # 种子数据（FakeDocumentSeeder）
├── src/main/resources/  # application.yml / application-dev.yml / schema.sql
├── frontend/            # Vue 3 管理端（Vite dev : 5173，/api 代理 → 8080）
└── docs/                # 详细设计.md / 项目计划书.md / devlog/ 开发日志
```

## 4. 关键决策（ADR 简表）

| # | 决策 | 背景 / 理由 |
| --- | --- | --- |
| D1 | JSON 统一使用 **Jackson 3（tools.jackson）** | Spring Boot 4 内置 Jackson 3，不再自动提供 Jackson 2 的 `ObjectMapper` bean；`spring-boot-starter-json` 是 Jackson 3 的唯一来源，**勿删** |
| D2 | PostgreSQL 取自增主键用 `INSERT ... RETURNING id` | `KeyHolder.getKey()` 在多列返回键时抛 `InvalidDataAccessApiUsageException` |
| D3 | 认证：JWT 无状态 + BCrypt，token 存前端 localStorage | 登录态保留与刷新恢复 |
| D4 | 百炼 Embedding 走 OpenAI 兼容模式（`spring-ai-starter-model-openai`） | dashscope starter 存在缺类问题 |
| D5 | 容器手动 / compose 启动参数统一为 `rag-postgres:63892` | 避免数据卷与端口冲突 |
| D6 | 后端按 controller / service / repository / entity / dto / security / config 分层 | 便于开发人员理解与维护 |
| D7 | **数据驱动 schema**：先爬真实文档再定表结构 | kb_document 字段依据 vuejs.org / docs.spring.io 真实元数据修订：`product`/`origin`+`authority`/`version`/`language`/`doc_type`/`last_modified` |
| D8 | 爬虫按 source adapter 模式（`DocumentCrawler` 接口 + 每站一个实现） | 便于扩展任意数据源（工具文档、社区问答等） |

## 5. 开发约定

- **统一响应**：所有接口返回 `ApiResponse { code, message, data }`，`code = 0` 表示成功；前端 axios 拦截器自动拆包。
- **建表**：`schema.sql` 幂等（`CREATE TABLE IF NOT EXISTS`），应用启动自动执行。
- **密码**：仅存 BCrypt 哈希（`password_hash`），绝不存明文。
- **鉴权**：`/api/auth/register`、`/api/auth/login`、`/api/demo/**`、`/api/qa/**` 放行，其余需认证。
- **前端登录态**：token 存 `localStorage`；请求拦截器注入 `Authorization: Bearer`；401 时清 token 并跳 `/login`；路由守卫读同一 TOKEN_KEY。

## 6. 常用命令

```bash
# 数据库（PostgreSQL 16 + pgvector）
docker compose up -d

# 后端（端口 8080，dev profile 连本地容器）
mvnw spring-boot:run

# 前端（端口 5173，/api 代理到 8080）
cd frontend && npm run dev

# 认证接口冒烟
curl -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" -d '{"username":"a","password":"123456"}'
```

## 7. 已知坑与规避（踩坑速查）

> 详细排查过程见开发日志；这里只留速查。

1. **Jackson 2 / 3 混用**：`com.fasterxml` 的 ObjectMapper 不会被 Spring Boot 4 自动注册；注入 `tools.jackson.databind.json.JsonMapper`。
2. **异常被 Security 转 401**：Spring Security 7 会把未认证上下文里冒泡的 Runtime 异常转为 401——后端接口抛异常时，前端会表现为「登录失效弹回登录页」，先查后端日志再怀疑前端。
3. **`/api/admin/*` 曾经缺失**：前端管理页加载会 401；接口未实现前必须有 stub（当前 `AdminController` 为桩）。
4. **容器端口冲突**：务必统一用 `63892`（compose 与手动 `docker run` 保持一致）。
5. **PowerShell 调 curl 引号问题**：`\"` 会被破坏导致 JSON 解析失败；调试用 `Invoke-RestMethod` 或单引号。
6. **RAG 分块（最高危）**：必须按 headings 切，块 500~1000 token，禁止按字符数硬切（会截断表格/代码/句子）；每块带 heading_path。
7. **Embedding 维度一锤子买卖**：text-embedding-v4=1024 维，入库后维度不可变；换模型必须重建向量库；query 与文档必须同一模型。
8. **metadata 过滤必须下推**：active/version 过滤写在 pgvector 的 SQL WHERE 层，禁止先召回后过滤（会漏掉最相关的）。
9. **检索兜底**：相似度低于阈值要答「知识库未收录」，禁止模型硬编。
10. **幻觉与溯源**：prompt 约束「只依据检索内容回答」；回答必须带 chunk 来源引用；检索块要精炼防超长上下文。
11. **爬虫反爬**：限速 + 重试 + UA；sitemap 结构会随站点改版变化（spring-boot 已踩坑，见 devlog P6）。

## 8. 进度状态

- ✅ **认证壳子**：注册 / 登录 / `/me` / JWT 鉴权 / 前端守卫 / 登录态保留（注册→登录→进后台不再弹回）。
- ✅ **文档爬虫（样本）**：`crawler/` 三个源适配器（Vue / SpringAI / SpringBoot），每源 80 篇样本落盘 `data/raw/*.json`（已 gitignore）。
- ✅ **表结构数据驱动修订**：`kb_document` 依据真实元数据新增 product / language / doc_type / last_modified，拆分 origin + authority（schema.sql 与详细设计.md 已同步）。
- ⏳ **管理端接口**：`AdminController` 为 stub（documents 列表/导入/启停/删除），待接真实导入。
- ⏳ **导入链路**：RawDocument → 分块 → Embedding → 写 vector_store + kb_document（幂等）。
- ⏳ **RAG 核心**：版本过滤、权威性加权、无答案兜底 —— `RagDemoController`（search/ask）已有雏形，待演进为 `qa` 正式接口。

## 9. 开发日志索引

| 日期 | 日志文件 | 主题 |
| --- | --- | --- |
| 2026-10-19 | [devlog/2026-10-19.md](./docs/devlog/2026-10-19.md) | 认证壳子打通；文档爬虫（3 源 adapter）；kb_document 数据驱动修订 |
