# 管理后台概览页数据统计重设计 Spec（v2）

## Why

现有概览只有 4 张数字卡 + 3 组进度条，只反映「文档有多少」，无法回答管理员真正关心的系统状态：内容是否新鲜、审核积压多少、问答命中率高不高、谁在贡献、向量覆盖率是否正常、最近有没有异常拦截。技术分布还会随数据源增加把卡片越撑越长。

本版把概览定义为**全系统状态看板**，覆盖 5 个数据域（内容资产 / 审核合规 / 问答检索 / 用户互动 / 系统健康），并且每个数字都能追到具体表与聚合口径；图表按信息形态选择，技术分布改为 Top 8 + 其他，不再撑长卡片。

## What Changes

* **BREAKING**：删除 `GET /api/admin/stats`（唯一消费方是概览页），改为 `GET /api/admin/overview?days=7|30`。

* 概览数据模型扩为 `cards + sections{content, governance, qa, users, engagement, health} + trend + todos + meta`，**只返回聚合值，不返回任何文档行或正文**。

* 页面结构重排为「待办条 → 核心指标卡 → 5 个分区面板」，每区 2\~4 个图表；技术/来源/版本一律 Top 8 +「其他 N 项」聚合。

* 图表方案改为**轻量化组件库**：引入 `chart.js@4` + `vue-chartjs@5`，只按需注册 `ArcElement / BarElement / LineElement / PointElement / CategoryScale / LinearScale / Tooltip / Legend`（禁止全量引入，禁止 ECharts）；环形（占比）、水平条形（Top N）、柱+线（趋势）由 Chart.js 渲染；新鲜度等单条堆叠仍用 CSS（无需 canvas）。

* 图表增量预算：构建后主 chunk 因图表库增加的体积 SHALL ≤ **80KB gzip**，并在构建日志中留档。

* 新增时间窗 7/30 天、环比（与上一等长窗口对比）、手动刷新与「数据更新于 HH:mm」。卡片可点击跳转对应管理页。

## Impact

* Affected specs：管理后台概览、管理端聚合接口、审核/脱敏/问答/用户统计口径

* Affected code：

  * 后端：`controller/AdminController.java`、`manage/AdminService.java`、新增 `dto/AdminOverview*.java`（或单文件嵌套 record）、`resources/schema.sql`（趋势查询索引）

  * 前端：`views/admin/AdminOverview.vue`、新增 `components/admin/charts/{DonutChart,BarList,SplitBar,TrendChart}.vue`、`components/admin/charts/theme.ts`（Chart.js 统一主题与按需注册）、`types/document.ts`、`api/document.ts`、`package.json`（新增 `chart.js` / `vue-chartjs`）

  * 测试：`test/.../manage/AdminApiTest.java`

  * 文档：`agent.md`、`docs/devlog/2026-10-27.md`

***

## 统计口径（覆盖全系统状态）

> 原则：每个指标都必须说明「来自哪张表 / 聚合口径 / 无数据时的兜底」，避免出现无法解释的数字。

### A. 内容资产（content）

| 指标                                          | 口径                                                                       | 无数据兜底    |
| ------------------------------------------- | ------------------------------------------------------------------------ | -------- |
| `totalDocs` / `activeDocs` / `inactiveDocs` | `kb_document` count，`active` 分组                                          | 0        |
| `chunks` / `avgChunksPerDoc`                | `sum(chunk_count)`、`avg(chunk_count)`                                    | 0        |
| `emptyChunkDocs`                            | `chunk_count = 0` 的文档数（未成功入库向量）                                          | 0        |
| `products[]`                                | 按 `product` 分组计数，降序；前 8 返回明细，其余 `otherCount` / `otherTotal`              | 空数组      |
| `origins[]`                                 | 按 `origin` 分组，口径同上                                                       | 空数组      |
| `docTypes[]`                                | 按 `doc_type` 分组（guide/api/tutorial/reference/howto…）                     | 空数组      |
| `languages[]`                               | 按 `language` 分组（en/zh…）                                                  | 空数组      |
| `versions[]`                                | 按 `version` 分组，Top 8 + 其他                                                | 空数组      |
| `visibility`                                | `team` / `private` 计数                                                    | 0/0      |
| `freshness`                                 | 依据 `last_modified` 距今：≤30 天 / 31\~180 天 / 181\~365 天 / >365 天 / 未知（NULL） | 全部计入「未知」 |
| `avgAgeDays`                                | `avg(now - coalesce(last_modified, fetched_at))` 天                       | 0        |
| `windowNewDocs` / `prevWindowNewDocs`       | `created_at >= now - days`；上一窗口用于环比                                      | 0        |
| `todayNewDocs`                              | `created_at::date = current_date`                                        | 0        |

### B. 审核与合规（governance）

| 指标                                         | 口径                                                                   |
| ------------------------------------------ | -------------------------------------------------------------------- |
| `reviewStatus`                             | `approved/pending/rejected` 计数与占比                                    |
| `approvalRate`                             | `approved / (approved + rejected)`（pending 不计入，避免污染）                 |
| `avgReviewHours`                           | `avg(reviewed_at - created_at)`，仅 `reviewed_at IS NOT NULL` 的 UGC 文档 |
| `pendingOldestDays`                        | 待审文档中最久未处理的 `now - updated_at` 天数                                    |
| `rejectedStale`                            | `review_status='rejected' AND updated_at < now - 7d`                 |
| `scan.windowBlocks` / `scan.blockedTitles` | `kb_scan_log` 窗口内行数 / `count(distinct doc_title)`                    |
| `scan.hits`                                | `sum(hit_count)`                                                     |
| `scan.byRule[]`                            | 按 `rule_type` 分组 Top 5（**不返回任何命中明文**）                                |
| `scan.byAction[]`                          | 按 `action` 分组（publish/upload/update/visibility/admin\_meta/precheck） |
| `notificationsUnread`                      | `kb_notification` where `read=false`                                 |

### C. 问答与检索（qa）

| 指标                           | 口径                                                                                      |
| ---------------------------- | --------------------------------------------------------------------------------------- |
| `conversations` / `messages` | `kb_conversation` / `kb_message` 总行数                                                    |
| `windowQa` / `prevWindowQa`  | 窗口内 `kb_message` 行数与上一窗口（环比）                                                            |
| `foundRate` / `noAnswerRate` | `found=true` 占比 / `found=false` 占比（窗口内）                                                 |
| `avgSources`                 | `avg(jsonb_array_length(sources))`（窗口内）                                                 |
| `activeAskers`               | 窗口内 `count(distinct kb_conversation.username)`                                          |
| `blindspotsTop[]`            | 窗口内 `found=false` 的 `question` 按原文（截断 60 字）计数 Top 5                                     |
| `trend.points[]`             | 近 N 天每日 `newDocs`（`kb_document.created_at`）与 `qaCount`（`kb_message.created_at`），缺失日期补 0 |

### D. 用户与互动（users / engagement）

| 指标                                   | 口径                                                                  |
| ------------------------------------ | ------------------------------------------------------------------- |
| `users.total` / `admins` / `members` | `kb_user` 计数与 `role` 分组                                             |
| `users.newInWindow`                  | `created_at >= now - days`                                          |
| `users.activeInWindow`               | 窗口内提问（`kb_conversation.username`）或发布 UGC（`kb_document.author`）的去重人数 |
| `users.topContributors[]`            | UGC 文档 `author` 分组 Top 5（文档数）                                       |
| `engagement.likes` / `favorites`     | 两表总行数                                                               |
| `engagement.topFavorites[]`          | 收藏数 Top 5 文档（`doc_id` + 标题 + 计数）                                    |

### E. 系统健康（health）

| 指标               | 口径                                                      |
| ---------------- | ------------------------------------------------------- |
| `vectorRows`     | `vector_store` 行数                                       |
| `coverageRate`   | `vectorRows / chunks`（chunk 为 0 时返回 0，不返回 NaN/Infinity） |
| `dbSizeBytes`    | `pg_database_size(current_database())`                  |
| `lastIngestedAt` | `max(kb_document.fetched_at)`                           |
| `lastScanAt`     | `max(kb_scan_log.created_at)`                           |
| `lastQaAt`       | `max(kb_message.created_at)`                            |

### 暂不纳入（需要新增埋点，避免假数据）

问答响应耗时、单轮 token 消耗、导入失败记录、实时在线人数、单篇文章阅读量。以上当前没有可信数据源，本次不臆造；如确需，后续单独立埋点 spec。

***

## ADDED Requirements

### Requirement: 概览聚合接口

系统 SHALL 提供 `GET /api/admin/overview`（仅 ADMIN），一次返回上述 5 个数据域的全部聚合值，且响应中不含文档正文、逐行文档数据或任何脱敏命中明文。

`days` SHALL 仅接受 7 与 30，默认 7，其余值钳制为 7。所有查询 SHALL 为聚合/JOIN 查询，禁止在 Java 侧遍历全量文档；趋势查询 SHALL 走 `created_at` 索引。

#### Scenario: 正常请求

* **WHEN** 管理员请求 `GET /api/admin/overview?days=30`

* **THEN** 返回 `cards`、`sections.content/governance/qa/users/engagement/health`、`trend.points`（30 个点）、`todos`、`meta.windowDays=30`

#### Scenario: 非法时间窗

* **WHEN** 请求 `days=365`

* **THEN** 按 7 天返回（`meta.windowDays=7`），不报 4xx

#### Scenario: 空库

* **WHEN** 库内无文档、无问答

* **THEN** 所有计数字段为 0、数组为空、`answerRate/foundRate/coverageRate` 为 0（不出现 null/NaN），前端展示空态引导

#### Scenario: 非管理员

* **WHEN** MEMBER 或匿名请求

* **THEN** 403 / 401，不泄露任何统计值

### Requirement: 全系统状态分区展示

概览页 SHALL 按 5 个分区展示统计，每区有清晰标题与「口径说明」入口（hover 或小问号提示），分区与图表映射如下：

| 分区    | 图表                                                                       |
| ----- | ------------------------------------------------------------------------ |
| 待办条   | 待审、驳回待改、窗口内拦截、知识盲区（均为可点击数字）                                              |
| 核心指标卡 | 文档总数、Chunk 总数、活跃率、待审、窗口内问答数、问答命中率、用户数、窗口内新增文档                            |
| 内容资产  | 技术分布 BarList（Top 8 + 其他）、来源分布 BarList、文档类型 / 语言 SplitBar、新鲜度 SplitBar    |
| 审核合规  | 审核状态 Donut、审核指标小卡（通过率 / 平均审核时长 / 最久待审）、脱敏规则 Top 5 BarList、拦截动作 SplitBar  |
| 问答检索  | 趋势 Trend（柱：新增文档；线：问答次数）、命中率 Donut（found / no-answer）、盲区 Top 5 列表、平均引用数小卡 |
| 用户互动  | 角色分布 SplitBar、窗口内新增 / 活跃用户小卡、贡献者 Top 5、收藏 Top 5                          |
| 系统健康  | 向量覆盖率小卡（含进度条）、数据库体积、最后导入 / 最后扫描 / 最后问答时间                                 |

#### Scenario: 技术数量超过 8

* **WHEN** 库内有 11 个 product

* **THEN** 只渲染 8 行条形 + 1 行「其他 3 项（合计 M 篇）」，卡片高度不随技术数增长；「其他」行可跳转 `/admin/documents`

#### Scenario: 数据缺失

* **WHEN** 某分区窗口内无数据（如近 7 天无拦截）

* **THEN** 该面板显示「近 7 天无记录」的行内空态，而不是空白卡片或 0 值图表

### Requirement: 轻量化图表组件

系统 SHALL 引入 `chart.js@4` + `vue-chartjs@5` 作为唯一图表库，并封装为 `DonutChart`、`BarList`、`TrendChart` 三个组件；`SplitBar`（单条堆叠）仍用 CSS 实现，不使用 canvas。

**轻量化约束**：

* 仅注册 `ArcElement`、`BarElement`、`LineElement`、`PointElement`、`CategoryScale`、`LinearScale`、`Tooltip`、`Legend`；禁止 `import Chart from 'chart.js/auto'`（会打包全部控制器）。

* 禁止引入 ECharts / Highcharts / ApexCharts 等重量级库；不引入多余插件（datalabels 等）。

* 构建后因图表库增加的主 chunk 体积 SHALL ≤ **80KB gzip**；超出则改用 `SplitBar` 式 CSS 方案或裁剪数据集。

* 统一主题集中在 `components/admin/charts/theme.ts`：颜色从 CSS 变量（`--ink-*` / `--brand-600` / `--hairline`）读取，字体继承 `--font-sans`，网格线 1px 半透明，关闭动画阴影与渐变。

**组件规范**：

* `DonutChart`（Doughnut）：中心显示总量（插件绘制文本），图例含数量与百分比；分段使用同色系明度差（深墨→中灰→浅灰），不依赖红绿。

* `BarList`（横向 Bar，`indexAxis: 'y'`）：最多 8 行 + 「其他 N 项」聚合行；隐藏 y 轴网格、显示数值；工具栏回调将数量格式化为千分位。

* `TrendChart`（混合图：Bar=新增文档、Line=问答次数）：纵轴刻度 ≤ 4（`ticks.maxTicksLimit: 4`），7 天显示全部横轴标签，30 天每 5 天一个（回调过滤）；容器固定高度、`responsive: true` + `maintainAspectRatio: false`。

* 所有图表容器 SHALL 包裹一层 `role="img"` 且带 `aria-label` 的 div，并在其内提供一段 `sr-only` 文本摘要（如「审核状态：通过 9820，待审 41，驳回 17」）。

**空数据处理**：`total = 0` 时不渲染 canvas，直接显示「暂无数据」占位。

#### Scenario: 技术数量超过 8

* **WHEN** 库内有 11 个 product

* **THEN** `BarList` 只渲染前 8 行 + 1 行「其他 3 项（合计 M 篇）」，容器高度不随技术数增长

#### Scenario: 构建体积

* **WHEN** 执行 `npm run build`

* **THEN** 图表相关增量 gzip ≤ 80KB，且构建产物中不存在 `echarts` 依赖痕迹

#### Scenario: 无数据

* **WHEN** 某图表数据集为空

* **THEN** 不绘制空白坐标轴，显示「暂无数据」占位

### Requirement: 视觉、响应式与可访问性

页面 SHALL 沿用现有设计 token：纸面底色、`1px` hairline、卡片零阴影、沉陶土 `--brand-600` 仅用于极小面积强调；所有数字使用等宽字体并经 `Intl.NumberFormat('zh-CN')` 千分位格式化（比率保留 1 位小数百分比）。

* 核心指标卡：桌面 4×2，≤1180px 2 列，≤640px 单列。

* 分区面板：桌面两列（内容资产与审核合规在上，问答检索与用户互动在下，系统健康横跨两列），≤1100px 全部单列。

* 每个图表带 `role="img"` 与一句 `aria-label`；颜色不作为唯一信息载体。

#### Scenario: 窄屏

* **WHEN** 视口 800px

* **THEN** 单列堆叠、无横向滚动、无文字截断

### Requirement: 交互与刷新

* 时间窗切换（7/30 天）SHALL 重新请求；环比文案（如「较上一周期 ▲12%」）随窗口变化。

* 提供手动刷新；右上角显示 `数据更新于 HH:mm`。

* 核心卡片与待办项 SHALL 可点击跳转：文档 → `/admin/documents`、待审 → `/admin/moderation`、拦截 → `/admin/scan-rules`、盲区 → `/admin/blindspots`、用户 → `/admin/members`。

* 加载显示骨架；请求失败显示行内重试，不清空已有数据。

## MODIFIED Requirements

### Requirement: 管理端聚合统计接口（原 /api/admin/stats）

原 `stats` 字段（`total/active/pending/chunks/official/community/products`）SHALL 并入 overview 的 `cards` 与 `sections.content`；原接口删除，`AdminApiTest` 断言迁移。

## REMOVED Requirements

### Requirement: 原「来源分布 / 技术分布 / 启用状态」三列进度条面板

**Reason**：信息密度低、技术项多时撑长卡片，且完全未覆盖审核、问答、用户与系统健康。
**Migration**：占比计算逻辑保留，呈现改为分区图表。

## 已确认的设计约束（原补充建议，用户确认后转为必须满足的 SHALL）

以下 8 条不再是「建议」，而是本次实现必须满足的验收项：

1. **每个数字带口径（SHALL）**：每个面板标题旁提供口径角标（`el-tooltip` 或小问号），文案写清分子/分母与时间窗，例如「通过率 = 通过 ÷（通过 + 驳回），不含待审」。验收：随机抽查 5 个面板均有口径提示且文案与 SQL 一致。
2. **环比而不是孤值（SHALL）**：窗口类指标（新增文档、问答次数、新增用户、活跃用户、拦截次数）同时返回上一等长窗口值与变化率，页面显示「较上一周期 ▲/▼ N%」，上一周期为 0 时显示「—」而非 ∞。验收：7/30 天切换后环比同步变化。
3. **待办优先（SHALL）**：页面第一屏第一行必须是待办条（待审 / 驳回待改 / 窗口拦截 / 知识盲区），图表在其下。验收：1200×800 视口首屏可见待办条全部 4 项。
4. **不做聚合缓存（SHALL）**：不引入 `@Cacheable`、Redis 或本地缓存层；只补 `created_at` 趋势索引。验收：代码中无缓存注解/缓存组件；趋势查询走索引（`EXPLAIN` 无全表扫描）。
5. **选 Chart.js 而不是 ECharts（SHALL）**：依赖仅 `chart.js@4` + `vue-chartjs@5`，按需注册；若实测增量超过 80KB gzip，改用 CSS 方案而非换更重的库。验收：`npm ls echarts` 为空、构建体积达标。
6. **隐私边界（SHALL）**：盲区只展示问题原文（截断 60 字）与出现次数，不展示用户名；脱敏统计只展示规则类型、命中数量、动作，绝不回显命中内容或文档正文。验收：接口响应字段中不存在 username/命中明文。
7. **图表与查询解耦（SHALL）**：所有图表组件只接收归一化的 `{ label, count, pct }[]`（趋势图接收 `{date, newDocs, qaCount}[]`），不感知业务字段名；后端改口径时只改数据映射层。验收：组件 props 中不出现 `pendingReview`、`hitCount` 等业务字段。
8. **可访问性与色盲友好（SHALL）**：同色系明度差 + 数值文字双编码；每个图表 `role="img"` + `aria-label` + `sr-only` 摘要。验收：移除颜色后仍能通过数值区分全部类别。

