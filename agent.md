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
| Embedding | 本地 Ollama（OpenAI 兼容） | `qwen3-embedding:0.6b` / 1024 维 / 中英跨语言 |
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
│   ├── rag/             # 检索问答（RagSearchService / Reranker / QaService / RagTraceService 诊断）
│   ├── kb/              # 知识库导航与 UGC（KbService 分页/详情/我的文档/收藏，UgcService 真实入库）
│   ├── me/              # 个人中心能力（ConversationService 会话持久化，InteractionService 点赞收藏）
│   ├── crawler/         # 文档爬虫（source adapter：Vue / SpringAI / SpringBoot）
│   ├── ingestion/       # 导入链路（ChunkSplitter 分块 + IngestionService 幂等入库 + ImportRunner）
│   ├── common/          # 通用（ApiResponse 统一响应）
│   └── demo/            # 种子数据（FakeDocumentSeeder）
├── src/main/resources/  # application.yml / application-dev.yml / schema.sql
├── frontend/            # Vue 3 前端（v2：暖橘设计 + 用户端 NavBar 外壳 + 管理端 /admin 深色侧边栏；Vite dev : 5173，/api 代理 → 8080）
└── docs/                # 详细设计.md / 项目计划书.md / devlog/ 开发日志
```

## 4. 关键决策（ADR 简表）

| # | 决策 | 背景 / 理由 |
| --- | --- | --- |
| D1 | JSON 统一使用 **Jackson 3（tools.jackson）** | Spring Boot 4 内置 Jackson 3，不再自动提供 Jackson 2 的 `ObjectMapper` bean；`spring-boot-starter-json` 是 Jackson 3 的唯一来源，**勿删** |
| D2 | PostgreSQL 取自增主键用 `INSERT ... RETURNING id` | `KeyHolder.getKey()` 在多列返回键时抛 `InvalidDataAccessApiUsageException` |
| D3 | 认证：JWT 无状态 + BCrypt，token 存前端 localStorage | 登录态保留与刷新恢复 |
| D4 | Embedding：**本地 Ollama `qwen3-embedding:0.6b`** 走 OpenAI 兼容模式（`spring-ai-starter-model-openai`，base-url 含 `/v1`） | 原百炼 `text-embedding-v4` 中英向量未对齐，中文搜不到英文文档；换跨语言模型覆盖中英混合语料（2026-10-25，见坑 #33）。百炼 dashscope starter 另有缺类问题 |
| D5 | 容器手动 / compose 启动参数统一为 `rag-postgres:63892` | 避免数据卷与端口冲突 |
| D6 | 后端按 controller / service / repository / entity / dto / security / config 分层 | 便于开发人员理解与维护 |
| D7 | **数据驱动 schema**：先爬真实文档再定表结构 | kb_document 字段依据 vuejs.org / docs.spring.io 真实元数据修订：`product`/`origin`+`authority`/`version`/`language`/`doc_type`/`last_modified` |
| D8 | 爬虫按 source adapter 模式（`DocumentCrawler` 接口 + 每站一个实现） | 便于扩展任意数据源（工具文档、社区问答等） |
| D9 | **v2 定位：私有化单实例知识库，隔离在部署边界，不做多租户**（2026-10-20 定稿） | 企业不愿机密上 SaaS；私有化下 tenant_id 是伪需求；物理隔离强于行级逻辑隔离（同 GitLab/Gitea 模型）。不做：多租户/平台公共区/关注评论/协同编辑/对外分享；做：admin·member 两角色 + 文档 private/team 两级可见 + UGC 权威加权（official 1.0 > ugc 0.9 > community 0.8）+ 新鲜度降权。详见 docs/v2产品规划.md + docs/详细设计_v2.md（v2.2） |
| D10 | v2 前端视觉基准由「暖橘 SaaS」**修订为「档案馆 / 书房」**（2026-10-20 v2.1 定稿） | 暖橘渐变/光斑/网格/卡片彩色投影与「私有化团队知识库」气质不符。v2.1 定：纸面 `#F7F5F0` + `1px` hairline 分层（卡片零阴影，阴影仅浮层）+ 深墨实底主操作 + 沉陶土 `#B45309` 极小面积强调（仅导航点/链接 hover/focus ring/信号点），高饱和亮橘只用于引用脉冲单点高光；问答页为**对话流**、管理端为**高密度中性控制台**。详见 docs/用户端前端设计.md v2.1 |
| D11 | 角色体系目标（**待裁决，未实施**）：首个注册者 = 主管理员 → 其余注册 = MEMBER → 仅 ADMIN 可改角色 | 现状 `AuthService.DEFAULT_ROLE = "ADMIN"`（任何人注册即管理员）且 `/api/admin/**` 不校验角色。角色写入与鉴权必须**同批发布**，否则形成「普通成员仍可进管理端」的假安全。方案见 docs/用户端前端设计.md §9.1 |
| D12 | 原文 URL 不落库，用纯函数 `OriginalUrlResolver` 在读取时重建（2026-10-25） | `kb_document` 无 url 列；`origin` 只存站点域名、`doc_key` 已去版本段与 `.html`。重建失败（manual/ugc/未知站点）返回 `null`，前端据此隐藏「打开原文」，避免垃圾 URL。载体：来源聚合的 `SourceRef.originalUrl` 与知识库详情 `KbDocDetail.originalUrl` |
| D13 | 来源聚合粒度 = **文档级**；UGC 上传 = **真实入库**（2026-10-25） | 问答来源「一条 = 一篇文档」，同文档多 chunk 按 docId 合并（score 取最大、markdown `\n\n` 拼接、首次出现顺序编号）；UGC 走现有 `IngestionService`（`source="ugc"` → `authority=community` / `origin=team`），非前端假提交 |
| D14 | 文档列表分页**不引入 PageHelper**，改用项目既有 `PageResult` + `LIMIT/OFFSET`（2026-10-25） | Spring Boot 4 无官方兼容的 PageHelper starter，引入第三方分页插件收益低且风险高；`KbService.docsPage` 与全量 `docs` 共用同一 WHERE，`total` 用同参数 count 查询，`page≥1` / `size∈[1,100]` 服务端钳制 |
| D15 | 问答历史会话 **PostgreSQL 持久化**：窗口 10 轮 + TTL 30 天（2026-10-25） | 表 `kb_conversation` / `kb_message`（级联删除）；每轮追加后按自增 id 保留最近 `app.conversation.window-size`（默认 10）条，超出删最旧；`list`/`create` 时清理 `updated_at` 超过 `app.conversation.ttl-days`（默认 30）的会话，防僵尸会话。所有读写都带 `username` 条件，跨用户一律 404 |
| D16 | 点赞 / 收藏：**登录可操作、计数公开**；幂等写（2026-10-25） | 复合主键 `(doc_id, username)` + `ON CONFLICT DO NOTHING`，重复点赞不报错也不重复计数；`/api/kb/doc/{id}` 对匿名只返回计数（`liked`/`favorited` 为 false），登录后返回该用户状态 |
| D17 | UGC 文档归属用 `kb_document.author` 列（幂等 `ALTER ... ADD COLUMN IF NOT EXISTS`，2026-10-25） | 原表无作者字段，导致「写入成功但个人中心看不到」；UGC 发布在同一事务内回写 `author`，失败整体回滚；爬虫/管理端导入保持 `NULL` |

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
7. **Embedding 维度一锤子买卖**：`text-embedding-v4`=1024 维、现行 `qwen3-embedding:0.6b` 同为 1024 维，入库后维度不可变；换模型必须重建向量库；query 与文档必须同一模型。
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
20. **Docker Hub 国内直连超时**（registry-1.docker.io:443 无响应）：构建镜像连续失败先查 daemon 状态（`npipe dockerDesktopLinuxEngine` 连不上=Docker Desktop 未启动）再查网络；对策为 Dockerfile 基础镜像加 `dockerproxy.net` 前缀（标准 Docker Hub 代理，任意网络可用；已实测 busybox/node/maven 可拉取）。
21. **受控 el-switch 提示文案**：异步切换反馈文案必须基于切换**目标值**/服务端返回值计算，读取操作前旧状态会导致「启用提示已停用」的反向提示（DocumentList.vue 已修复）。
22. **search 能召回 ≠ ask 能命中**：ask 兜底需初检 topScore ≥ `app.rag.similarity-threshold`（0.65）；中文短问法对英文文档语料相似度普遍偏低（如「createApp 是做什么的？」触发兜底），完整英文问法可命中（0.68~0.85）。演示/示例问法必须先用真实库验证过 topScore。
23. **CSS animation 重触发**：Vue 对相同 class 的重复赋值不会重播 CSS animation（diff 视为无变化）。需「重置→nextTick→设值」让类经历移除→添加（QaHome.onCite 引用定位脉冲已修复）；另注意脉冲动画结束后 box-shadow 回落 none，自动化测试必须在动画窗口内检测 animationName 而非结束态样式。
24. **Rollup AST 对正则字面量的 `//` 误判**：ES module 里写 `/^(?:https?:\/\/|\./i` 这类正则，Rollup 会把 `//` 当作注释起始符解析，报 `Invalid regular expression: Unterminated group`。对策：避免正则字面量里出现 `//`，改用 `new RegExp('...')` 字符串构造或用 DOMPurify 内置的 `ALLOWED_URI_PROTOCOLS`（默认已拒绝 javascript:/data:/vbscript:）。
25. **TrustedHTML 与 string 类型不兼容**：`DOMPurify.sanitize()` 返回全局安全类型 `TrustedHTML`，与我们代码里 `interface RenderedPiece { html?: string }` 冲突。`as unknown as string` 显式转换即可——TrustedHTML 运行时就是字符串，v-html 也接受。
26. **打字机逐字效果与 Markdown 不兼容**：逐字截断的 markdown 产生残缺 HTML（未闭合标签、表格断裂），无法正确渲染。对策：改为整段 fade-in 入场（opacity transition），保留视觉节奏但不破坏结构。
27. **登录态残留导致回归误报**：浏览器 session 会保留上一会话的 `localStorage` token，未清空就测「未登录应跳转 /login」会得到「守卫失效」的假结论。**登录态相关回归项必须先 `localStorage.clear()` 重置**。同类误报：Vue scoped 样式会把 `@keyframes` 重命名为 `qa-pulse-<hash>`，断言动画名时须按**前缀**匹配而非全等；DevTools 自身的 highlighting 也会污染 `[class*=hl]` 类查询。
28. **Markdown 中的引用角标必须整段渲染后内联替换**：按 `[n]` 切段再逐段 `marked.parse()`，每段都会被 `<p>` 包裹成**块级元素**，角标与后文被迫各占一行（`display:inline` 覆盖不了 `<p>`）。正确做法：**整段** `marked` → `DOMPurify`，再在 HTML 上把 `[n]` 替换为行内 `<sup>`；替换前用带捕获组的 `split` 切出 `<pre>`/`<code>` 段并原样跳过；注入发生在净化**之后**且只拼接数字常量，无新增注入面。v-html 内无法直接绑定事件 —— 用**事件委托**在容器上处理 click。
29. **`role` 枚举大小写**：后端 JWT/`UserDto` 返回 `ADMIN` / `MEMBER`（大写），前端若写 `role === 'admin'` **永不命中**，表现为「已是管理员但不显示徽标」。一律归一化：`role?.toUpperCase() === 'ADMIN'`。另：当前注册接口对**所有注册者**返回 `ADMIN`（`AuthService.DEFAULT_ROLE`），属待裁决 C1/C3，前端不得据此做权限判断。
30. **短查询天然低分，不是检索链路故障**：实测 `Spring AI` topScore 0.5563（<0.65 兜底）、`How to use ChatClient in Spring AI` 0.8535（通过）。1~3 个词的查询与长文档 chunk 的余弦相似度偏低是 Embedding 模型特性，排查「搜不到」先看 `/api/qa/debug` 的 `threshold` 段，别先怀疑 pgvector 或维度。另：`section` / `heading_path` **不在 vector_store.metadata**（只有 title/active/doc_id/origin/product/version/doc_type/language/authority），前端或诊断接口需要标题路径时必须回源 `kb_chunk`。
31. **docs.spring.io 的 sitemap 混合两类 URL**：同一个 sitemap 里既有 `/{version}/...`（7.1-SNAPSHOT / 6.2 等多版本），也有**不带版本目录的当前页**（如 `.../reference/web/webflux-cors.html`）。把首段一律当版本号会得到 `version=web` / `docKey=webflux-cors` 这类脏元数据，且「取最大版本」会选中字典序更大的单词。对策：版本目录必须匹配 `^\d+(\.\d+)*(-[A-Za-z0-9.]+)?$`，只保留版本化页面并取版本号最大的一版（`AbstractSpringReferenceCrawler`）；导入 id 为 `{product}-{version}-{docKey}`，版本错了会整批算新文档。
32. **模型调用无超时会挂死请求线程**：Spring AI 的 `RestClient` 默认无连接/读取超时，外网（`api.deepseek.com:443`）不可达时线程永久阻塞在 `QaService#generate`，表现为「控制层 return 卡住 + 前端超时 + 后端日志看似处理完成」。对策已落地：`spring.http.client.connect-timeout=5s` / `read-timeout=60s`、`spring.ai.retry.max-attempts=1`，模型异常转 `ModelUnavailableException` → `code=503`（区别于兜底 `found=false`），前端 `/qa/ask` 单独 90s 超时。排查手法：连续两次 `jcmd <pid> Thread.print`，栈不变且 CPU 不涨 = 阻塞在外部 IO，不是死循环。
33. **跨语言检索的方向选择（中文问题匹配英文文档 / 中英混合语料）**：根因是向量模型 `text-embedding-v4` 的中英向量**不在同一语义空间对齐**（中文查询与英文 chunk 余弦相似度普遍偏低，坑 #22 即此表象）。曾先落地「查询翻译」（`QueryTranslator` 中文→英文再向量化），但用户提出「知识库同时有中英文档怎么办」后暴露**前提崩塌**：翻译只能对齐到单一目标语言，混合语料会反向丢掉另一语种的召回。终版改为**跨语言向量模型**（对齐向量空间）：本地 Ollama `qwen3-embedding:0.6b`（1024 维/32K，实测中英互译句 0.9578、无关中文句 0.3758），查询期零翻译，`QueryTranslator` 已删除。切换要点：①`spring.ai.openai.base-url` 必须**含 `/v1`**（Spring AI 2.0 走官方 OpenAI SDK，`http://127.0.0.1:11434/v1`）；②删掉 `embedding.options.dimensions`（Ollama 不认该参数，模型原生 1024 维）；③`api-key` 用占位 `ollama`；④容器内需 `host.docker.internal:11434` + `extra_hosts: host-gateway`；⑤**换模型必须清表重灌**（旧向量与新空间不可混用）。⚠️ 调试陷阱：PowerShell 5.1 用 `Invoke-RestMethod` 传中文 JSON 会按非 UTF-8 编码把中文压成 `?`，导致同长度中文串向量恰好相同（相似度 1.0000）——必须用 `[Text.Encoding]::UTF8.GetBytes()` 传字节；**出现「恰好 1.0000」这类完美数值先怀疑采集工具**。Ollama 模型默认落 C 盘 `~/.ollama/models`，本次 C 盘写满导致拉取失败，已用 `OLLAMA_MODELS` 迁到 `D:\ollama\models`。
34. **`docKey` 存储不含 `.html` 扩展名**：真实爬虫（`SpringBootDocsCrawler`）入库前已 `replaceFirst("\\.html$", "")` 去掉 `.html` 与版本段，`OriginalUrlResolver` 重建时再按产品线补回 `.html`（spring-boot/framework/security/data-jpa）或原样拼接（spring-ai 无 `.html`）。**凡写 URL 重建相关测试种子，`doc_key` 必须对齐真实格式（无版本段、无 `.html`、无前导 `/`）**，否则重建出 `/qa/x.html.html` 这类双重后缀。手动导入源（`manual-official/manual-community`）与 UGC（`origin=team`）无可靠原文地址，`OriginalUrlResolver` 一律返回 `null`。
35. **分页 total 查询不能只复用 WHERE 片段**：把 `META_SELECT + WHERE + ORDER BY` 里的 WHERE 子串直接当 count SQL 会同时犯两个错——①缺 `SELECT count(*) FROM kb_document`；②丢掉了 WHERE 里的 `?` 绑定参数（PostgreSQL 报「未设定参数值」）。正确做法：count SQL = `SELECT count(*) FROM kb_document` + WHERE 片段，并传**同一组** `args`（`KbService.docsPage` 已修复，`KbServiceTest#docsPageReturnsTotalAndSlicesByLimitOffset` 锁定）。
36. **树高亮必须比较完整路径而非叶子 key**：产品线/版本/来源三级树中 `official`、`community`、`latest` 会在多个父节点下重名；前端只比较 `selected.source === s.key` 会让所有 `official` 同时点亮。高亮判定必须带全部上级键（product + version + source），折叠状态的 Set 键同理用 `product::version` 组合键。

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
- ✅ **v1 收尾优化（2026-10-20）**：AnswerCard 改 Markdown 渲染（marked + DOMPurify 白名单净化，打字机改 fade-in）；SourceCard 视觉降权（低饱和描边 + 近白标题）；示例问法替换为实测命中问法；联调 5 项全过；新坑 #24~#26（Rollup 正则注释误判 / TrustedHTML 类型 / 打字机与 Markdown 不兼容）。
- ✅ **v2 方向定稿（2026-10-20，决策见 D9/D10）**：经多轮讨论从「类 CSDN 社区 + RAG」→「多租户 SaaS」→ 收敛为 **私有化单实例团队知识库 + RAG**。两份 v2 文档重写至 v2.2：docs/v2产品规划.md（定位定稿 / 不做清单 / 权威三档加权 + 新鲜度公式 / V2.0~V2.4 阶段表 / 验收场景）、docs/详细设计_v2.md（无 tenant_id 的 schema 演进 / setup 向导 / 可见性统一谓词 / 分阶段门禁）。社交砍到点赞+收藏，关注/评论/多租户/公共区明确不做。
- ✅ **demo.html 前端原型（2026-10-20）**：根目录单文件高保真原型，8 视图（首页/问答/知识库左树右卡/文档详情/Setup 向导/个人中心/写文档/管理后台），暖橘 token 体系 + 暖白底 #FDFCFA；头像菜单承载账户入口，管理后台深色侧边栏独立形态。作为 v2 全部前端页面的设计基准。
- ✅ **v2 前端重建 + 迁移回归验收（2026-10-20，spec `rebuild-frontend-v2` Task 1~11 全过）**：以 `demo.html` 为基准**废弃 v1 管理端形态**——`theme.css` 重建暖橘 token（`--brand-500 #F97316`、暖白底 `#FDFCFA`、暖色投影）+ 覆盖 Element Plus 变量；`App.vue` 改为 NavBar + Footer 外壳（账户入口收头像菜单），管理端独立深色侧边栏；路由收敛为 `/`、`/qa`、`/kb`、`/kb/doc/:id`、`/login`、`/setup`、`/me`、`/editor`、`/admin/*`；迁移问答页 / 登录页 / 管理端（概览·文档管理·数据导入，接口与行为不变，active 提示文案仍取目标值），其余页面为**空态外壳（不伪造数据）**；删除 3 个旧视图 + `--kb-*` 兼容 token 块。回归 11 项浏览器实测全过、`npm run build` 通过。新坑 #27（登录态残留/动画名 hash 导致回归误报）。
- ✅ **视觉基调 v2.1「档案馆 / 书房」+ 用户反馈修复（2026-10-20 晚）**：`theme.css` 重写为纸面 token（`--page-bg #F7F5F0` / `--hairline #E7E2D8` / 深墨 `--ink-900` / 沉陶土 `--brand-600`），**删除全部渐变与卡片阴影 token**（阴影仅 `--shadow-overlay*` 浮层）；问答页改**对话流**（右气泡 + 纸面答案 + 横向来源卡 + sticky 底部输入 + 三行灰条骨架）；管理端改中性控制台（220px 深墨侧边栏 / 4 统计卡 tabular-nums / 表格无竖线无斑马纹）；外壳页空态统一「一句话 + 动作按钮」。用户反馈 5 项已修：角标换行（整段渲染后内联 `<sup>` 替换，坑 #28）、`（官方）` 括号并入角标、历史序号改正序、Footer 仅首页、管理员徽标大小写（坑 #29）。回归 9 项浏览器实测全过 + `npm run build` 通过。
- ⚠️ **待裁决：角色体系（C1/C3，ADR D11）**：现状注册即 `ADMIN`、`/api/admin/**` 不校验角色。建议方案（首注册者 = 主管理员 → 其余 MEMBER → 仅 ADMIN 改角色 → `/api/admin/**` 收敛 `hasRole('ADMIN')`）已写入 [docs/用户端前端设计.md](docs/用户端前端设计.md) §9.1，**本轮未改后端**。
- ✅ **检索链路可观测性（2026-10-20 晚）**：新增 `rag/RagTraceService` + `GET /api/qa/debug`（五段：向量化 → 初检 → 阈值 → 重排 → 回源，逐段 status/elapsedMs/detail，`traceId` 对齐日志，**不调用 Chat 模型**）+ 管理端 `/admin/rag-debug`「检索诊断」页。诊断结论：库内 235 文档/854 chunk/856 向量、维度 1024 正常；`Spring AI` topScore **0.5563 < 0.65** 触发兜底，完整问句 **0.8535** 通过 —— 链路无故障，断在阈值策略（坑 #30）。测试 `RagTraceServiceTest` 3/3 通过；前端 build 通过。
- ✅ **数据源扩容（2026-10-21）**：爬虫从 3 源扩到 **10 源**——新增 `springframework` 465 / `springsecurity` 156 / `springdatajpa` 35 / `vite` 39 / `vuerouter` 167 / `pinia` 97（均 official）+ `stackoverflow` 251（community，StackExchange API 高票问答）。抽 `AbstractSitemapCrawler`（sitemap 递归/限速/重试/进度）与 `AbstractSpringReferenceCrawler`（Antora 版本目录只取最新版，坑 #31）、`AbstractVitePressCrawler`；`--crawl-limit=N` 可调每源篇数，`--import=all` 自动扫描 `data/raw/*.json`。入库后 **1445 篇 / 3263 chunks / 3265 向量行**（扩容前 235/854/856），7 源导入 0 失败；验证 `password storage`（7.2-SNAPSHOT，0.7299）与 `vite dependency pre-bundling`（0.7589）均 top1 命中，`ask` 端到端 found=true。新增 7 例爬虫单测，`mvn test` 54/54 通过。详见 [docs/devlog/2026-10-21.md](./docs/devlog/2026-10-21.md)。
- ✅ **问答超时加固（2026-10-22）**：排查「后端处理完不回显 + 前端超时」——根因为外网不可达 + Spring AI `RestClient` 无超时导致请求线程永久阻塞（坑 #32）。落地 `spring.http.client` 连接 5s / 读取 60s、`spring.ai.retry.max-attempts=1`；模型异常转 `ModelUnavailableException` → 控制器 503「模型服务暂时不可用」；前端 `/qa/ask` 单独 90s 超时。新增单测 `QaServiceTest#modelFailureMapsTo503`，`mvn test` 55/55、`npm run check` 通过。详见 [docs/devlog/2026-10-22.md](./docs/devlog/2026-10-22.md)。
- ✅ **检索基线测评（2026-10-25）**：新建 `eval/` 包（`BaselineEvaluator` 纯函数指标 + `BaselineRunner` `--baseline` Runner + `CuratedQuery` + `resources/eval/queries.json` 25 条金标集），复用线上 `RagSearchService`/`Reranker` 同一路径实测 265 条查询（真实百炼 Embedding，seed=42 分层抽样）。基线：合成自检索 Hit@10 **0.9167**/MRR@10 **0.7414**，精选问句 Hit@10 **0.8696**/MRR@10 **0.7685**，版本过滤 leakRate **0.0000**，可答兜底率 0 / 负例正确兜底率 1.0，official 平均名次提升 +1.03。`BaselineEvaluatorTest` 8/8 通过。产出 [docs/检索基线测评报告.md](docs/检索基线测评报告.md) + `data/baseline/result-*.json`。详见 [docs/devlog/2026-10-25.md](./docs/devlog/2026-10-25.md)。
- ✅ **双语检索 · 跨语言向量模型（2026-10-25，终版）**：Embedding 从百炼 `text-embedding-v4` 换为**本地 Ollama `qwen3-embedding:0.6b`**（1024 维 / 32K 上下文，中英共用同一语义空间）—— 中文查询直接向量化即可命中英文文档，且**中英混合语料同样覆盖**。演进：第一阶段「查询翻译」（`QueryTranslator`，仅单语料成立）因用户追问混合语料而废弃并删除该类及其测试；终版方案对比见 devlog（①查询翻译 ②跨语言模型【采用】③多路召回+RRF ④索引期双语化）。配置：`spring.ai.openai.base-url=http://127.0.0.1:11434/v1`（**必须含 `/v1`**）、`model=qwen3-embedding:0.6b`、**删除 `dimensions`**、`api-key` 占位 `ollama`；compose 内改 `host.docker.internal:11434/v1` + `extra_hosts: host-gateway`。模型目录经 `OLLAMA_MODELS=D:\ollama\models` 迁至 D 盘（C 盘已满）。换模型必须清表全量重灌：1443 文档 / 3260 chunk / 3260 向量，10 源 0 失败。验收 4 组中英文查询 Top1 全部命中（`如何配置 CDS` 0.5696、`Spring Boot 自动配置原理` 0.6745、`Vue Router 导航守卫怎么用` 0.7498）。`mvn test` **77/77** 通过。详见 [docs/devlog/2026-10-25.md](./docs/devlog/2026-10-25.md)（双语检索 · 跨语言向量模型章节）。
- ✅ **V1.0 升级（2026-10-25，决策见 D12/D13）**：三功能落地 —— ①知识库导航页后端 `kb/KbService` + `KbController`（`GET /api/kb/overview|tree|docs|doc/:id`，product▸version▸source 三级树 + 卡片过滤 + 详情 markdown 拼接）；②来源聚合（文档级）+ 原文跳转：`OriginalUrlResolver` 纯函数重建原文 URL、`SourceRef` 加 `docId`/`originalUrl`、`QaService` 按 docId 合并同文档多 chunk；③模拟 UGC 上传 `kb/UgcService` + `UgcController`（`POST /api/ugc/documents`，真实入库 `IngestionService`，`source=ugc`→`community`/`team`）。`IngestionService` 加 ugc 映射、`SecurityConfig` 放行 `/api/kb/**`；前端接 `KbHome`/`KbDoc`/`Editor`/`SourceCard` + `types|api|utils`。测试 `OriginalUrlResolverTest` 8/8 + `KbServiceTest` 5/5 + `QaServiceTest` 7/7（新增文档聚合用例）通过（坑 #34）。详见 [docs/devlog/2026-10-25.md](./docs/devlog/2026-10-25.md)（V1.0 升级章节）。
- ✅ **V1.1 体验补完（2026-10-25 晚，决策见 D14~D17）**：①知识库文档**分页**（`KbService.docsPage`，`PageResult` + LIMIT/OFFSET，默认 12 条/页，与全量 `docs` 共用 WHERE）+ 前端左树**两级折叠**（产品线/版本，`+ / −`）与分页条；②修复「选中 official 树节点时所有 official 同时点亮」（高亮改完整 product+version+source 路径比较，坑 #36）；③**问答历史会话持久化 + 会话管理**（`kb_conversation`/`kb_message`，`/api/me/conversations` 列表/新建/详情/重命名/删除/追加，窗口默认 10 轮裁剪 + 30 天 TTL，跨用户 404），前端 `/qa` 会话下拉切换/新建/重命名/删除，切换页面后自动恢复最近会话；④**点赞/收藏**（`kb_doc_like`/`kb_doc_favorite` 复合主键幂等，`/api/me/docs/{id}/like|favorite`，详情返回公开计数 + 当前用户状态，`KbDoc` 按钮联动，`Me` 我的收藏列表）；⑤修复 UGC 文档**未绑定作者**（`kb_document.author` 幂等 ALTER + `UgcService` 事务内回写，`/api/me/documents` 个人中心可见）。新增 `MeConversationApiTest` 4 例 + `KbServiceTest` 分页 1 例，`mvn test` **82/82** 通过、`npm run check` / `npm run build` 通过（坑 #35）。详见 [docs/devlog/2026-10-25.md](./docs/devlog/2026-10-25.md)（V1.1 体验补完章节）。
- ⏭️ **下一步**：**C1~C8 待确认清单待用户逐条裁决**（开放注册 / 问答免登录 / 无角色区分 / 文档可见性 / 权威三档 / 来源粒度 / DTO 字段 / 管理端能力，见 [docs/用户端前端设计.md](docs/用户端前端设计.md) §9）；裁决通过后进入 V2.0 编码（KbController overview/tree/docs/doc/:id + QaService 来源聚合 SourceRefV2 + /kb 左树右卡 + 文档详情 + 问答页来源卡文档级改造），门禁：聚合单测 + mvn test 全量 + 浏览器联调。另：阈值双条件改造（绝对下限 + 相对落差）仍待执行。

## 9. 开发日志索引

| 日期 | 日志文件 | 主题 |
| --- | --- | --- |
| 2026-10-19 | [devlog/2026-10-19.md](./docs/devlog/2026-10-19.md) | 认证壳子打通；文档爬虫（3 源 adapter）；kb_document 数据驱动修订 |
| 2026-10-20 | [devlog/2026-10-20.md](./docs/devlog/2026-10-20.md) | Task 1 导入链路：分块器 + 幂等入库 + 测试门禁 + 文档同步 ｜ Task 5 真实数据灌库 + 三条验证用例（$ 转义 / 爬虫字段缺口 / 百炼批量上限 3 坑） ｜ **Task 6 管理接口 + 前端联调（active 双写、URL 白名单防 SSRF、前端版本动态化、状态开关提示文案修复）** ｜ **Task 7 README 与架构图** ｜ **Task 8 Docker 一体化部署（dockerproxy 镜像源 + 8 项验收全过）** ｜ **用户端问答页 /qa（versions 端点 + 暖橘玻璃视觉 + 角标脉冲 + 问法阈值坑 #22/#23）** ｜ **v1 收尾：答案卡 Markdown 渲染 + 来源卡视觉降权（坑 #24~#26）** ｜ **v2 方向定稿（私有化单实例，ADR D9/D10）+ v2 文档重写 v2.2 + demo.html 8 视图原型** ｜ **v2 前端重建（暖橘 token + NavBar 外壳 + /admin 深色侧边栏）+ 迁移回归 11 项全过（坑 #27 回归误报）** ｜ **视觉基调 v2.1「档案馆 / 书房」（纸面 + hairline + 深墨 + 沉陶土）与用户反馈 5 修（角标换行/官方括号/序号正序/Footer 仅首页/管理员徽标，坑 #28/#29）** |
| 2026-10-21 | [devlog/2026-10-21.md](./docs/devlog/2026-10-21.md) | **数据源扩容：3 源 → 10 源（Spring Framework/Security/Data JPA + Vite/Vue Router/Pinia + Stack Overflow API）**，235→1445 篇 / 854→3263 chunks，7 源导入 0 失败（坑 #31 sitemap 混合版本页） |
| 2026-10-22 | [devlog/2026-10-22.md](./docs/devlog/2026-10-22.md) | **问答不回显 + 前端超时排查**：外网不可达 + RestClient 无超时挂死线程；落地出站超时、503 降级、前端 90s 超时（坑 #32） |
| 2026-10-25 | [devlog/2026-10-25.md](./docs/devlog/2026-10-25.md) | **检索基线测评**：eval 包落地（纯函数指标 + `--baseline` Runner + 25 条金标集），实测四维指标（IR / 版本过滤 / 兜底 / 权威重排），产出一份可信基线报告 ｜ **双语检索（跨语言向量模型 · 终版）**：换本地 Ollama `qwen3-embedding:0.6b`（1024 维，中英同一语义空间，混合语料全覆盖），迁模型目录至 D 盘，清表全量重灌 3260 向量，中文查询 Top1 命中验证通过（坑 #33） ｜ **V1.0 升级**：知识库导航页后端（/api/kb/**）+ 文档级来源聚合与原文跳转（`OriginalUrlResolver`/`SourceRef`/`QaService`）+ 模拟 UGC 上传（/api/ugc/**，真实入库，坑 #34，决策 D12/D13） ｜ **V1.1 体验补完**：文档分页与左树折叠、树高亮路径修复（坑 #36）、会话持久化与会话管理（10 轮窗口 + 30 天 TTL）、点赞收藏、UGC 作者归属（决策 D14~D17，坑 #35） |
