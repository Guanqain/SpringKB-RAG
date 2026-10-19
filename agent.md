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
│   ├── ingestion/       # 导入链路（ChunkSplitter 分块 + IngestionService 幂等入库 + ImportRunner）
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
3. **`/api/admin/*` 管理接口**：Task 6 已实现（列表/导入/active 双写/删除/chunks）；未登录访问仍会 401（SecurityConfig 统一保护）。手动导入 URL 抓取仅限 `vuejs.org`/`spring.io` 白名单（防 SSRF）。
4. **容器端口冲突**：务必统一用 `63892`（compose 与手动 `docker run` 保持一致）。
5. **PowerShell 调 curl 引号问题**：`\"` 会被破坏导致 JSON 解析失败；调试用 `Invoke-RestMethod` 或单引号。
6. **RAG 分块（最高危）**：必须按 headings 切，块 500~1000 token，禁止按字符数硬切（会截断表格/代码/句子）；每块带 heading_path。
7. **Embedding 维度一锤子买卖**：text-embedding-v4=1024 维，入库后维度不可变；换模型必须重建向量库；query 与文档必须同一模型。
8. **metadata 过滤必须下推**：active/version 过滤写在 pgvector 的 SQL WHERE 层，禁止先召回后过滤（会漏掉最相关的）。
9. **检索兜底**：相似度低于阈值要答「知识库未收录」，禁止模型硬编。
10. **幻觉与溯源**：prompt 约束「只依据检索内容回答」；回答必须带 chunk 来源引用；检索块要精炼防超长上下文。
11. **爬虫反爬**：限速 + 重试 + UA；sitemap 结构会随站点改版变化（spring-boot 已踩坑，见 devlog P6）。
12. **分块实现要点（Task 1）**：标题栈维护 headingPath（`^#{1,6} ` 识别）；贪心打包 500~1000 token；代码围栏 ``` 不可切开（围栏计数成对判断）；token 估算用 `max(1, round(字符数/4))` 确定性纯函数；`content_text` 去语法、`content_markdown` 保结构。
13. **集成测试不调真实 AI API**：EmbeddingModel 用 `@MockitoBean` 返回确定性 1024 维向量。注意 Mockito 对接口 default 方法（如 `embed(List, EmbeddingOptions, BatchingStrategy)`）默认返回空，PgVectorStore 依赖它，必须显式 stub，否则 IndexOutOfBoundsException。测试连独立库 `springkb_test`（application-test.yml），`@MockitoBean` 包名是 `org.springframework.test.context.bean.override.mockito.MockitoBean`（Boot 4 已弃用 @MockBean）。
14. **幂等与事务**：导入单文档全程 `@Transactional`（PgVectorStore 复用同一 DataSource 会加入当前事务）；批量导入需 `@Lazy` 自引用代理保证每个文档独立事务、单文档失败不中断批次。
15. **`Matcher.replaceAll(Function)` 二次解释 `$`/`\`**：JDK 走 appendReplacement，返回串中的 `$refs`/`$emit`/`\frac` 等触发 `Illegal group reference`。凡替换内容来自文档/用户，必须 `Matcher.quoteReplacement(...)`（ChunkSplitter 三处）。
16. **爬虫快照字段缺口**：老版 `data/raw/*.json` 可能缺 `contentMarkdown`（分块依赖）→ 导入全跳过。代码演进后必须重爬；"全跳过"先查源 JSON 字段。
17. **百炼 Embedding 批量上限**：text-embedding-v4 单请求输入 ≤ **10 条**；超大文档必须分批，`EMBEDDING_BATCH_SIZE = 8` 留余量。
18. **boost 是微调非覆盖**：community 相似度差 > 0.15 时 official 无法反超，属设计边界（vue reactive array 场景）。
19. **PowerShell curl 传 JSON**：`-d '{"a":1}'` 的双引号会被吞导致 parse error；用 `--data-binary "@文件"` 传 JSON 文件，或 curl.exe 单独调用。

## 8. 进度状态

- ✅ **认证壳子**：注册 / 登录 / `/me` / JWT 鉴权 / 前端守卫 / 登录态保留（注册→登录→进后台不再弹回）。
- ✅ **文档爬虫（样本）**：`crawler/` 三个源适配器（Vue / SpringAI / SpringBoot），每源 80 篇样本落盘 `data/raw/*.json`（已 gitignore）。
- ✅ **表结构数据驱动修订**：`kb_document` 依据真实元数据新增 product / language / doc_type / last_modified，拆分 origin + authority（schema.sql 与详细设计.md 已同步）。
- ✅ **管理端接口 + 前端联调（Task 6）**：`manage/AdminService` + `AdminController`（原 stub 替换）—— 文档列表（过滤 + 可选分页）、单文档/`data/raw` 批量导入、**active 双写**（`kb_document.active` + `vector_store.metadata` 切换后检索即时不召回）、删除、chunk 列表；`manual-official/manual-community` source 映射保证手动导入权威性正确。前端版本选项硬编码 3.2/3.3 → 动态提取修复。`mvn test` 42/42 通过；API 冒烟 235 篇真实库全链路通。
- ✅ **导入链路（Task 1）**：`ingestion/` 包落地 —— ChunkSplitter 按标题栈分块（500~1000 token、围栏保护、超长/空文档兜底）+ IngestionService 幂等导入（单事务、双字段 kb_chunk、vector_store 按 §4.4 metadata、id 1:1 对齐）+ ImportRunner（`--import=all`）。测试 12/12 通过（分块边界 7 + 集成 4 + context 1）。
- ✅ **版本过滤检索（Task 2，能力一）**：`rag/` 包 `RagSearchService` + `QaController#GET /api/qa/search` —— filter expression 下推 pgvector WHERE（恒 `active=='true'`，version 可选），version 注入防护（拒绝 `'`/换行/分号/`&&`/`||`）；测试 7 例（版本隔离/下推证明/inactive 不召回/空版本/注入/元数据 schema/参数校验），累计 19/19 通过。
- ✅ **权威性加权（Task 3，能力二）**：`Reranker`（`app.rag.boost` 默认 0.15）—— 初检 topK×3 → official +boost 稳定排序重排 → 取前 topK；`search` 输出重排后结果、`searchDocuments` 保留初检候选；测试 6 例（official 排前/同分稳定/溢出/空候选/截取/集成同分排序），累计 25/25 通过。
- ✅ **无答案兜底 + 引用溯源（Task 4，能力三）**：`QaService#ask` + `QaController#POST /api/qa/ask` —— 兜底短路（最高相似度 < `app.rag.similarity-threshold` 默认 0.65 → found=false，零模型调用）+ Prompt 强制 [1]..[n] 引用标注与 official/community 来源说明 + 按 chunk id 批量回源 kb_chunk JOIN kb_document；测试 6 例（兜底零调用/命中带引用/编号对齐/越界保护/阈值参数化/控制器契约），累计 31/31 通过。
- ✅ **RAG 三能力全部落地**（Task 2/3/4）。
- ✅ **真实数据灌库 + 三条验证用例（Task 5）**：`--import=all` 入库 235 篇真实文档（233 official：vue 80 / spring-ai 80 / spring-boot 73 + 2 community 注入样本），854 chunks / 856 向量行。三条验证用例全部通过（版本过滤 / 权威性加权 / 无答案兜底），引用溯源正向用例带 [1][2][3] 引用；逐条记录见 [docs/测试用例文档.md](docs/测试用例文档.md)。`mvn test` 33/33 通过。真实数据暴露 3 个坑（$ 转义 / 爬虫字段缺口 / 百炼批量上限）已修复并回归。
- ✅ **README 与架构图（Task 7）**：根目录 [README.md](README.md) —— 问题背景（版本割裂 / 来源混杂 / 无答案硬编）→ 三能力设计与验证（235 篇真实数据 + 42/42 测试）→ mermaid 架构图 → 技术栈 / 快速启动 / 目录结构 / 已知限制 6 条。所有产品级文档（README / 详细设计 / 测试用例 / devlog）齐备。

## 9. 开发日志索引

| 日期 | 日志文件 | 主题 |
| --- | --- | --- |
| 2026-10-19 | [devlog/2026-10-19.md](./docs/devlog/2026-10-19.md) | 认证壳子打通；文档爬虫（3 源 adapter）；kb_document 数据驱动修订 |
| 2026-10-20 | [devlog/2026-10-20.md](./docs/devlog/2026-10-20.md) | Task 1 导入链路：分块器 + 幂等入库 + 测试门禁 + 文档同步 ｜ Task 5 真实数据灌库 + 三条验证用例（$ 转义 / 爬虫字段缺口 / 百炼批量上限 3 坑） ｜ **Task 6 管理接口 + 前端联调（active 双写、URL 白名单防 SSRF、前端版本动态化）** ｜ **Task 7 README 与架构图** |
