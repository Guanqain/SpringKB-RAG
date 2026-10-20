# Tasks

> 执行规则（用户要求）：
> 1. 每个模块完成后**审查健壮性 / 可行性 / 安全性**，审查结论记入 `docs/devlog/`
> 2. 每个模块必须编写**自动化测试用例**，`mvn test` 通过后才能进入下一模块
> 3. 每模块改动同步 `docs/详细设计.md`、`agent.md`、`docs/devlog/`（文档驱动）

## 已完成基线（2026-10-19 前）
- [x] Task 0: 项目骨架与基础设施
  - [x] Boot 4.0.8 + Spring AI 2.0.0 项目可启动，依赖解析无误
  - [x] Docker Compose 托管 pgvector（rag-postgres:63892），API Key 走环境变量
  - [x] 数据库三表体系落地：kb_user / kb_document / kb_chunk / vector_store（schema.sql + docker init.sql 幂等）
  - [x] 认证模块：JWT + BCrypt + /api/auth/register|login|me，前端登录态持久化
  - [x] 结构化爬虫：crawler 包（三源适配器 + HtmlToMarkdown），240 篇真实文档样本落盘 data/raw/
  - [x] 设计文档同步：docs/详细设计.md、agent.md、docs/devlog/2026-10-19.md（含 RAG 坑点 §6.6）

## 未完成（按依赖顺序执行）

- [x] Task 1: 导入链路（真实数据入库）——RAG 可用性的地基
  - [x] 1.1 分块器 ChunkSplitter：按 headings 切，块 500~1000 token，产出 chunk_ordinal / heading_path / token_count
  - [x] 1.2 kb_chunk 写入服务（双字段：content_markdown / content_text；image_refs JSONB）
  - [x] 1.3 vector_store 写入（Embedding text-embedding-v4，metadata 按 §4.4 规范，id 与 kb_chunk 对齐）
  - [x] 1.4 幂等：按 (product, origin, authority, version, doc_key) 已存在则跳过；重复导入总量不变
  - [x] 1.5 审查：健壮性（空文档/超大文档/无 headings 兜底）、可行性（真实 Embedding API 调用）、安全性（无 SQL 注入、密钥环境变量）
  - [x] 1.6 测试用例：分块边界（超长块/空内容/代码块截断）、幂等重复导入、双字段一致性
  - [x] 1.7 同步文档：详细设计.md §4/§7、agent.md、devlog

- [x] Task 2: 版本过滤检索（能力一）
  - [x] 2.1 检索接口增加 `version` 参数，Spring AI filter expression → pgvector WHERE（metadata ->> 'version' + 'active'）
  - [x] 2.2 元数据规范落地（§4.4：doc_id/product/origin/authority/version/active/doc_type/language/title/section）
  - [x] 2.3 审查：过滤必须下推（禁止内存后过滤）、参数注入安全
  - [x] 2.4 测试用例：version=X 只召回 X；inactive 不召回；边界（空版本默认值）
  - [x] 2.5 同步文档

- [x] Task 3: 来源权威性加权（能力二）
  - [x] 3.1 检索逻辑：初检 topK×3 → official +boost(0.15) 重排 → 取前 5
  - [x] 3.2 接口返回体现重排顺序与来源（authority 字段）
  - [x] 3.3 审查：boost 溢出/排序稳定性、空候选安全
  - [x] 3.4 测试用例：official 排前且 community 仍在；同分稳定性
  - [x] 3.5 同步文档

- [x] Task 4: 无答案兜底 + 引用溯源（能力三）
  - [x] 4.1 兜底：最高相似度 < 0.65 直接返回"知识库中未找到相关答案"，不调用 Chat 模型
  - [x] 4.2 Prompt 强制 [1][2] 引用标注 + official/community 来源说明
  - [x] 4.3 引用回源：命中 chunk id → kb_chunk 原文 + kb_document 元数据（title/version/section/heading_path）
  - [x] 4.4 审查：阈值参数化、模型调用短路（兜底零调用）、引用越界保护
  - [x] 4.5 测试用例：无关问题触发兜底（Mock ChatClient 断言零调用）；命中问题带引用；引用编号与 sources 对齐
  - [x] 4.6 同步文档

- [x] Task 5: 真实数据灌库 + 三条验证用例
  - [x] 5.1 用 data/raw/ 真实文档跑导入链路，库内达到真实规模
  - [x] 5.2 版本过滤用例：仅召回目标版本
  - [x] 5.3 权威性加权用例：official 排前、community 均在
  - [x] 5.4 无答案兜底用例：返回"知识库中未找到相关答案"
  - [x] 5.5 输出测试用例文档（docs/，逐条记录提问/召回/回答/结论）

- [x] Task 6: 管理接口 + 前端联调
  - [x] 6.1 /api/admin/documents 列表（分页/过滤）、/api/admin/import（触发导入）、/api/admin/documents/{id}/active（版本状态切换）
  - [x] 6.2 管理接口鉴权（JWT）+ 权限校验
  - [x] 6.3 前端文档列表页 / 导入按钮 / 状态切换开关联调
  - [x] 6.4 测试用例：未登录 401、切换 inactive 后检索不召回
  - [x] 6.5 同步文档

- [x] Task 7: README 与架构图
  - [x] 问题背景 → 三能力设计与验证 → 架构图 → 已知限制
  - [x] 同步 agent.md

- [x] Task 8: Docker Compose 部署（app + postgres）
  - [x] Dockerfile 应用容器化，compose 一键启动
  - [x] 部署验收：接口可访问、数据可导入

- [ ] Task 9: 演示素材与面试话术
  - [ ] 截图/录屏（版本过滤、权威性排序、兜底三个演示）
  - [ ] 面试话术（每个能力 STAR 故事、"和 Dify/扣子区别"）

# Task Dependencies

- Task 0（基线）先行，Task 1/2/3/4 依赖 Task 0
- Task 1（导入链路）为 Task 2/3/4 提供真实数据；Task 2/3/4 可并行（各自依赖 Task 1 的元数据规范）
- Task 5 依赖 Task 1、2、3、4（真实数据 + 三能力）
- Task 6 依赖 Task 1 与 Task 5（需真实数据与版本元数据）
- Task 7、Task 9 依赖 Task 5
- Task 8 依赖 Task 0 与 Task 5
