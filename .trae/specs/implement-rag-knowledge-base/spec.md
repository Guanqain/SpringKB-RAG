# RAG 知识库问答系统 Spec（SpringKB-RAG）

> 本版本为 2026-10-19 修订：对齐真实文档驱动的演进（多产品/多版本/结构化爬虫/三表 schema/认证基线），并纳入「每模块审查 + 测试门禁 + 文档同步」的工程化流程。

## 项目目标（Why）

做一个**面向生产环境语义的 RAG 知识库问答系统**，解决裸 RAG 在企业组织中三个比模型选型更关键的工程问题：
1. **版本污染**：文档持续迭代时，旧版本向量若不清理，用户问新版本行为会召回旧版答案，产生"看似有据、实则过期"的错误回答。
2. **来源权威性混杂**：社区答案语义相似度可能高于官方文档，仅按相似度排序会优先返回"高赞但非权威"的答案。
3. **真实场景复杂度**：企业知识库是多产品 × 多版本 × 多语言 × 多来源站点共存的，需用真实文档结构（标题层级、代码块、表格、图片）验证存储与检索设计，而非两版本假数据。

核心价值是**知识治理**（版本隔离 + 权威性排序 + 无答案兜底），用于面试差异化展示与工程化 RAG 可行性验证。

## 交付物（What Changes）

1. **可运行应用**：Boot 4.0.8 + Spring AI 2.0.0 + PostgreSQL 16/pgvector 的 RAG 问答服务。
2. **已完成基线（2026-10-19）**：
   - 认证模块：注册 / 登录 / JWT / BCrypt / `/api/auth/me`，前端登录态持久化与路由守卫（kb_user 表）
   - 结构化爬虫：`crawler` 包（`DocumentCrawler` 接口 + vuejs / springai / springboot 三源适配器），HtmlToMarkdown 保留标题层级/代码/表格/图片 alt、剔除导航噪声；已爬取 240 篇真实文档样本落盘 `data/raw/`
   - 数据库三表体系：`kb_document`（文档级元数据，product/origin/authority/version/language/doc_type…）、`kb_chunk`（块级双字段 + heading_path + image_refs）、`vector_store`（Spring AI 管理，id 与 kb_chunk 1:1 对齐）
3. **能力一 · 版本过滤检索**：检索请求携带 `version` 参数，仅在 pgvector 查询层按元数据过滤，不召回其他版本内容。
4. **能力二 · 来源权威性加权**：初检 topK×3 候选，`official` 来源 +boost 重排后取前 5 送生成，官方文档优先于同等社区答案。
5. **能力三 · 无答案兜底 + 引用溯源**：最高相似度低于阈值时不调用 Chat 模型，直接返回"知识库中未找到相关答案"；有答案时回答按 `[1][2]` 标注引用，引用可回源到 kb_chunk 原文与文档元数据。
6. **导入链路**：RawDocument → 按 headings 分块（500~1000 token）→ 双字段写 kb_chunk → Embedding 写 vector_store（幂等）。
7. **最简管理界面**：文档列表、文档上传/导入、版本状态切换（含认证登录态）。
8. **文档与素材**：README、测试用例文档（逐条记录）、演示截图/录屏；**所有改动同步** `docs/详细设计.md`、`agent.md`、`docs/devlog/`。
9. **部署**：Docker Compose（app + postgres）一键部署。

## 影响范围（Impact）

- 受影响规格：全部为本项目新规格（无历史 spec）。
- 受影响代码：
  - `pom.xml`：Spring Security + jjwt、Spring AI 2.0 BOM、DeepSeek/百炼 starter、pgvector、Jsoup
  - `src/main/resources/application*.yml`、`schema.sql`：模型/向量库/认证/JWT 配置、三表幂等建表
  - `src/main/java/com/guangqian/springkb/`：`auth/`、`crawler/`、`rag/`（检索/重排/兜底/引用）、`import`（入库）、`manage`（管理接口）、`common/`
  - `frontend/`：Vue 3 + Element Plus（登录页 + 管理后台）
  - `docker-compose.yml`、`docker/postgres/init.sql`：部署环境与初始化 SQL
  - 数据库：`kb_user`、`kb_document`、`kb_chunk`、`vector_store`

## 硬约束

1. 技术栈固定：Spring Boot **4.0.8**、Spring AI BOM **2.0.0**、PostgreSQL 16 + pgvector、JDK 17+、Maven 3.6.3+、Vue 3 + Vite + Element Plus。
2. Chat 模型：**DeepSeek**（`deepseek-chat`）；Embedding：**阿里云百炼 OpenAI 兼容模式**（`text-embedding-v4`，维度 **1024 固定**，更换需重建向量表）。
3. 版本过滤必须在**向量库查询层**执行（Spring AI filter expression → pgvector WHERE），不得在应用内存中事后过滤。
4. 检索参数：初检 **topK×3** → 重排 → 取前 **5** 送生成；初始系数 `boost = 0.15`、兜底相似度阈值 `0.65`（余弦）。
5. 分块策略：**按 headings 切**，块 **500~1000 token**，每块带 `heading_path`；禁止按字符数硬切。
6. 元数据规范：`kb_document` 唯一键 `(product, origin, authority, version, doc_key)`；`kb_chunk.id` = `vector_store.id`（1:1 对齐回源）。
7. API Key（`DEEPSEEK_API_KEY`、`DASHSCOPE_API_KEY`）与 `JWT_SECRET` 必须来自环境变量，**禁止硬编码**。
8. **测试门禁**：每个功能模块必须编写自动化测试（`src/test`，mvn test 通过），并审查**健壮性 / 可行性 / 安全性**通过后，才能进入下一个模块。
9. **文档同步**：每个模块完成后必须同步 `docs/详细设计.md`、`agent.md`、`docs/devlog/`，以文档驱动开发。
10. 部署形态：Docker Compose（app + postgres）。

## 隐含要求

1. **幂等入库**：重复启动/重复导入不得产生重复文档。
2. **中文支持**：验证用例为中文提问，回答须中文且可读。
3. **来源标识**：回答需标明信息来自 `official`（官方）还是 `community`（社区）。
4. **引用溯源**：回答带 `[1][2]` 标注，接口附引用原文（kb_chunk.content_markdown）与文档元数据（title/version/section/heading_path）。
5. **不硬编答案**：检索质量不足（低于阈值）时不得调用模型、不得编造，明确返回"知识库中未找到相关答案"。
6. **依赖依据**：Spring AI 2.0 API 与 1.x 存在差异，以官方参考文档与 Javadoc 为准，不套用 1.x 教程代码。
7. **数据获取**：结构化爬虫（HtmlToMarkdown）+ 限速/重试/UA；图片 alt 保留（嵌入文本中替换为描述），原图 src 进 image_refs。
8. **界面最简**：管理界面仅满足"文档列表、上传/导入、版本状态切换"三个动作，不追求完整后台。
9. **可验证性**：三个差异化能力各对应一条可复现的验证用例，结果逐条记录。
10. **模块审查**：每个模块交付前审查健壮性（边界输入）、可行性（真实 API 可用）、安全性（认证鉴权、密钥管理、SQL 注入面）。

## ADDED Requirements

### Requirement: 版本过滤检索
系统 SHALL 在检索时按用户指定版本过滤，仅从该版本活跃文档召回（过滤发生在 pgvector 查询层）。

#### Scenario: 指定版本只召回对应版本
- **WHEN** 用户以 `version=3.5` 查询该版本相关行为
- **THEN** 仅召回 `version=3.5` 的文档，结果中不出现其他版本内容；过滤在向量库层生效。

### Requirement: 来源权威性加权
系统 SHALL 对 `official` 来源候选施加权重加成后重排，使官方文档优先于语义相似度略高的社区答案。

#### Scenario: 官方文档优先于社区答案
- **WHEN** 查询同时命中 official 与 community 候选
- **THEN** official 文档排前、community 文档仍在结果中（重排生效，两者均返回）。

### Requirement: 无答案兜底
系统 SHALL 在检索结果最高相似度低于阈值时，不调用 Chat 模型，直接返回"知识库中未找到相关答案"。

#### Scenario: 知识库外问题不硬答
- **WHEN** 用户提问内容在知识库中无对应章节
- **THEN** 返回"知识库中未找到相关答案"，不调用 Chat 模型。

### Requirement: 引用溯源与来源标注
系统 SHALL 要求模型在回答中按 `[1][2]` 标注引用编号，标明来源类型，并支持回源到原文片段。

#### Scenario: 回答带引用与来源
- **WHEN** 知识库命中且回答生成
- **THEN** 关键结论带 `[1][2]` 引用标注，接口返回引用原文与文档元数据（title/version/section/heading_path）。

### Requirement: 真实数据导入与幂等
系统 SHALL 支持将真实文档（RawDocument）分块、双字段入库 kb_chunk、Embedding 写 vector_store，重复执行不产生重复文档。

#### Scenario: 重复启动不重复入库
- **WHEN** 应用重启或重复触发导入
- **THEN** 已存在文档被跳过，库内文档总量不变。

### Requirement: 认证基线
系统 SHALL 提供注册 / 登录 / 鉴权（JWT）能力，管理接口仅登录用户可访问。

#### Scenario: 未登录访问管理接口被拒
- **WHEN** 未携带有效 JWT 访问 `/api/admin/*`
- **THEN** 返回 401，且登录后访问成功。

### Requirement: 管理界面
系统 SHALL 提供最简界面支持文档列表查看、文档导入、版本状态（active/inactive）切换。

#### Scenario: 切换版本状态影响检索
- **WHEN** 管理员将某版本文档置为 inactive
- **THEN** 该版本文档不再被检索召回。

### Requirement: 模块测试门禁与文档同步
系统开发流程 SHALL 保证每个功能模块交付前：自动化测试通过（mvn test）+ 健壮性/可行性/安全性审查通过 + 设计文档同步完成。

#### Scenario: 模块完成标准
- **WHEN** 某模块功能实现完成
- **THEN** 该模块的测试用例通过、审查结论记录、`docs/详细设计.md` 与 `agent.md` 与 `docs/devlog/` 已同步，方可进入下一模块。

### Requirement: 部署
系统 SHALL 支持通过 `docker compose up` 一键启动 postgres 与应用。

#### Scenario: compose 一键部署
- **WHEN** 执行 `docker compose up -d`
- **THEN** postgres（含 pgvector 扩展）与应用启动，接口可访问。

## 验证用例（验收口径）

| 用例 | 问题 | 预期结果 | 验证点 |
|---|---|---|---|
| 版本过滤 | "某 API 在版本 X 如何配置？" | 仅召回版本 X 文档，无其他版本内容 | filter 在向量库层生效 |
| 权威性加权 | "`@ConfigurationProperties` 的变化" | 官方文档排在社区答案之前，两者均返回 | 重排序生效 |
| 无答案兜底 | 知识库未收录章节的问题 | 返回"知识库中未找到相关答案" | 阈值兜底生效 |

每个模块另有自动化测试（单元 + 集成），纳入 mvn test 门禁。
