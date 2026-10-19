# RAG 知识库问答系统 Spec（SpringKB-RAG）

## 项目目标（Why）

做一个**面向生产环境语义的 RAG 知识库问答系统**，解决裸 RAG 在企业组织中两个比模型选型更关键的工程问题：
1. **版本污染**：文档持续迭代时，旧版本向量若不清理，用户问新版本行为会召回旧版答案，产生"看似有据、实则过期"的错误回答。Spring Boot 3.2/3.3 的行为差异构成天然测试集。
2. **来源权威性混杂**：社区答案语义相似度可能高于官方文档，仅按相似度排序会优先返回"高赞但非权威"的答案。

核心价值是**知识治理**（版本隔离 + 权威性排序 + 无答案兜底），而不是模型调用本身，用于面试差异化展示与工程化 RAG 可行性验证。

## 交付物（What Changes）

1. **可运行应用**：Boot 4.0.8 + Spring AI 2.0.0 + PostgreSQL 16/pgvector 的 RAG 问答服务（骨架与假数据链路已完成，`/api/demo/search`、`/api/demo/ask` 可用）。
2. **能力一 · 版本过滤检索**：检索请求携带 `version` 参数，仅在 pgvector 查询层按元数据过滤，不召回其他版本内容。
3. **能力二 · 来源权威性加权**：初检 topK×3 候选，`official` 来源 +boost 重排后取前 5 送生成，官方文档优先于同等社区答案。
4. **能力三 · 无答案兜底 + 引用溯源**：最高相似度低于阈值时不调用 Chat 模型，直接返回"知识库中未找到相关答案"；有答案时回答按 `[1][2]` 标注引用并标明官方/社区来源。
5. **知识库数据**：Spring 3.2/3.3 官方文档（`web`、`data`、`howto` 章节）+ Stack Overflow `spring-boot` 标签高赞问答（评分 ≥5、2023 年后），官方约 50-100 chunk、SO 约 200-500 条。
6. **最简管理界面**：文档列表、文档上传、版本状态切换。
7. **文档与素材**：README（问题背景 → 能力设计与验证 → 架构图 → 已知限制）、测试用例文档（3 条验证用例逐条记录）、演示截图/录屏。
8. **部署**：Docker Compose（app + postgres）一键部署。

## 影响范围（Impact）

- 受影响规格：全部为本项目新规格（无历史 spec）。
- 受影响代码：
  - `pom.xml`：依赖管理（Spring AI 2.0 BOM、DeepSeek/OpenAI 兼容 starter、pgvector、Jsoup reader）
  - `src/main/resources/application*.yml`：模型、向量库、环境变量配置
  - `src/main/java/com/guangqian/springkb/`：检索/重排/兜底/引用逻辑、入库接口、抓取与导入、管理界面
  - `docker-compose.yml`、`docker/postgres/init.sql`：部署环境
  - 数据库：`vector_store` 表（元数据含 `source/version/active/title/section/so_score` 等字段）

## 硬约束

1. 技术栈固定：Spring Boot **4.0.x**（当前 4.0.8）、Spring AI BOM **2.0.0**、PostgreSQL 16 + pgvector、JDK 17+、Maven 3.6.3+。
2. Chat 模型：**DeepSeek**（`deepseek-chat`，官方 starter）；Embedding：**阿里云百炼 OpenAI 兼容模式**（`text-embedding-v4`，维度 **1024 固定**，更换需重建向量表）。
3. 版本过滤必须在**向量库查询层**执行（Spring AI filter expression → pgvector WHERE），不得在应用内存中事后过滤。
4. 检索参数：初检 **topK×3** → 重排 → 取前 **5** 送生成。
5. 初始系数：`boost = 0.15`、兜底相似度阈值 `0.65`（余弦）。
6. 数据源约束：SO 答案评分 **≥5**、**2023 年后**；官方文档仅 `web`、`data`、`howto` 章节；数据量目标官方 50-100 chunk、SO 200-500 条。
7. API Key（`DEEPSEEK_API_KEY`、`DASHSCOPE_API_KEY`）必须来自环境变量，**禁止硬编码**。
8. 部署形态：Docker Compose（app + postgres）。

## 隐含要求

1. **幂等入库**：重复启动/重复导入不得产生重复文档（参考现有 `FakeDocumentSeeder` 的幂等逻辑）。
2. **中文支持**：验证用例为中文提问，回答须中文且可读。
3. **来源标识**：回答需标明信息来自 `official`（官方）还是 `community`（社区）。
4. **引用溯源**：chunk 元数据保留 `source_title`、`section`、`so_question_id`，prompt 强制要求标注 `[1][2]`，便于前端展开原文。
5. **不硬编答案**：检索质量不足（低于阈值）时不得调用模型、不得编造，明确返回"知识库中未找到相关答案"。
6. **依赖依据**：Spring AI 2.0 API 与 1.x 存在差异，以官方参考文档与 Javadoc 为准，不套用 1.x 教程代码。
7. **数据获取方式**：Spring 文档用 Jsoup（官方无 PDF）；SO 用 SEDE 导出 CSV（免爬虫反爬）。
8. **界面最简**：管理界面仅满足"文档列表、上传、版本状态切换"三个动作，不追求完整后台。
9. **可验证性**：三个差异化能力各对应一条可复现的验证用例（见第六节验证用例），结果逐条记录。

## ADDED Requirements

### Requirement: 版本过滤检索
系统 SHALL 在检索时按用户指定版本（3.2/3.3）过滤，仅从该版本活跃文档召回。

#### Scenario: 指定版本只召回对应版本
- **WHEN** 用户以 `version=3.3` 查询 "CDS 在 Spring Boot 3.3 中如何配置？"
- **THEN** 仅召回 `version=3.3` 的文档，结果中不出现 3.2 内容；过滤在向量库层生效。

### Requirement: 来源权威性加权
系统 SHALL 对 `official` 来源候选施加权重加成后重排，使官方文档优先于语义相似度略高的社区答案。

#### Scenario: 官方文档优先于社区答案
- **WHEN** 查询 "`@ConfigurationProperties` 在 3.3 中的变化"，且 official 与 community 候选同时命中
- **THEN** official 文档排前、community 文档仍在结果中（重排生效，两者均返回）。

### Requirement: 无答案兜底
系统 SHALL 在检索结果最高相似度低于阈值时，不调用 Chat 模型，直接返回"知识库中未找到相关答案"。

#### Scenario: 知识库外问题不硬答
- **WHEN** 用户提问内容在知识库中无对应章节（如未抓取的 native-image 章节问题）
- **THEN** 返回"知识库中未找到相关答案"，不调用 Chat 模型。

### Requirement: 引用溯源与来源标注
系统 SHALL 要求模型在回答中按 `[1][2]` 标注引用编号，并标明每条信息的来源类型（official/community）。

#### Scenario: 回答带引用与来源
- **WHEN** 知识库命中且回答生成
- **THEN** 关键结论带 `[1][2]` 引用标注，且明确说明来自官方还是社区。

### Requirement: 数据导入与幂等
系统 SHALL 支持将官方文档（Jsoup 抓取）与 SO 问答（SEDE 导出）清洗、分块、入库，且重复执行不产生重复文档。

#### Scenario: 重复启动不重复入库
- **WHEN** 应用重启或重复触发导入
- **THEN** 已存在文档被跳过，库内文档总量不变。

### Requirement: 管理界面
系统 SHALL 提供最简界面支持文档列表查看、文档上传、版本状态（active/inactive）切换。

#### Scenario: 切换版本状态影响检索
- **WHEN** 管理员将某版本文档置为 inactive
- **THEN** 该版本文档不再被检索召回。

### Requirement: 部署
系统 SHALL 支持通过 `docker compose up` 一键启动 postgres 与应用。

#### Scenario: compose 一键部署
- **WHEN** 执行 `docker compose up -d`
- **THEN** postgres（含 pgvector 扩展）与应用启动，接口可访问。

## 验证用例（验收口径）

| 用例 | 问题 | 预期结果 | 验证点 |
|---|---|---|---|
| 版本过滤 | "CDS 在 Spring Boot 3.3 中如何配置？" | 仅召回 3.3 官方文档，无 3.2 内容 | filter 在向量库层生效 |
| 权威性加权 | "`@ConfigurationProperties` 在 3.3 中的变化" | 官方文档排在社区答案之前，两者均返回 | 重排序生效 |
| 无答案兜底 | "Spring Boot 3.3 支持 GraalVM 哪个版本？"（未抓取 native-image） | 返回"知识库中未找到相关答案" | 阈值兜底生效 |
