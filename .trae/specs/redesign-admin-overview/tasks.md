# Tasks

- [x] Task 1: 后端概览聚合接口（五域）
  - [x] SubTask 1.1: 新增 `dto/AdminOverview.java`（嵌套 record：`Cards`、`Sections`、`ContentStats`、`GovernanceStats`、`QaStats`、`UserStats`、`EngagementStats`、`HealthStats`、`ProductSlice`、`OtherBucket`、`RuleHit`、`TrendPoint`、`TrendSeries`、`Meta`）
  - [x] SubTask 1.2: `AdminService.overview(days)` 内容资产域：总数/启用/chunk/均值/`emptyChunkDocs`/products/ origins/docTypes/languages/versions（各 Top 8 + other）/visibility/freshness 分段/avgAgeDays/todayNewDocs/windowNewDocs + 环比
  - [x] SubTask 1.3: 审核合规域：审核状态计数与占比、approvalRate、avgReviewHours、pendingOldestDays、rejectedStale、scan 窗口拦截数/去重标题数/总命中/规则 Top5/动作分布、notificationsUnread
  - [x] SubTask 1.4: 问答检索域：会话/消息总数、窗口问答数与环比、foundRate/noAnswerRate、avgSources、activeAskers、blindspotsTop（问题截断 60 字、不带用户名）、trend 序列（补零）
  - [x] SubTask 1.5: 用户互动域 + 系统健康域：用户总数/角色/窗口新增/窗口活跃/贡献者 Top5、点赞/收藏总数/收藏 Top5、vectorRows/coverageRate/dbSizeBytes/lastIngestedAt/lastScanAt/lastQaAt
  - [x] SubTask 1.6: `AdminController` 新增 `GET /api/admin/overview`（`days` 钳制 7/30），删除 `GET /api/admin/stats` 及 `AdminStats` DTO
  - [x] SubTask 1.7: 确认实现不引入任何缓存层（无 `@Cacheable`/Redis/本地缓存），并用 `EXPLAIN` 验证趋势与窗口查询走 `created_at` 索引；所有窗口指标同时返回上一等长窗口值供环比

- [x] Task 2: 索引与后端测试
  - [x] SubTask 2.1: `schema.sql` 补 `idx_kb_document_created_at (created_at)`、`idx_kb_message_created_at (created_at)`、`idx_kb_user_created (created_at)`（均 `IF NOT EXISTS`）
  - [x] SubTask 2.2: `AdminApiTest` 新增 overview 用例：五域字段完整、`days` 钳制、趋势点数为 7/30 且缺失日期为 0、Top 8 + other 合计 = 总数、空库时比率字段为 0 不为 null、非管理员被拒

- [x] Task 3: 前端数据层
  - [x] SubTask 3.1: `types/document.ts` 以 `AdminOverview` 及子类型替换 `AdminStats`
  - [x] SubTask 3.2: `api/document.ts` 新增 `fetchAdminOverview(days)`，移除 `fetchAdminStats`

- [x] Task 4: 轻量化 Chart.js 图表组件（`components/admin/charts/`）
  - [x] SubTask 4.1: 安装 `chart.js@^4`、`vue-chartjs@^5`（写入 `package.json` 依赖），并新增 `theme.ts`：按需 `Chart.register(ArcElement, BarElement, LineElement, PointElement, CategoryScale, LinearScale, Tooltip, Legend)`，统一色板/字体/网格线/千分位工具回调；组件 props 只接受归一化 `{label,count,pct}[]` / `{date,newDocs,qaCount}[]`，不感知业务字段
  - [x] SubTask 4.2: `DonutChart.vue`（Doughnut，中心总量文本，图例含数量与百分比，同色系明度差）
  - [x] SubTask 4.3: `BarList.vue`（`indexAxis: 'y'` 横向条形，最多 8 行 + 「其他 N 项」聚合行，数值千分位，可选跳转）
  - [x] SubTask 4.4: `TrendChart.vue`（Bar 新增文档 + Line 问答次数混合图，刻度 ≤4，30 天每 5 天一个标签，`maintainAspectRatio: false`）
  - [x] SubTask 4.5: `SplitBar.vue`（CSS 单条堆叠 + 分行图例，用于新鲜度/可见性/角色/动作）
  - [x] SubTask 4.6: 图表容器统一 `role="img"` + `aria-label` + `sr-only` 摘要；空数据不渲染 canvas，显示「暂无数据」

- [x] Task 5: 概览页重写（`views/admin/AdminOverview.vue`）
  - [x] SubTask 5.1: 顶部工具栏：时间窗 7/30 切换、刷新、`数据更新于 HH:mm`
  - [x] SubTask 5.2: 待办条 4 项（待审 / 驳回待改 / 窗口拦截 / 知识盲区），可点击跳转
  - [x] SubTask 5.3: 8 张核心指标卡（含环比文案与口径提示），千分位、百分比格式化，可点击跳转
  - [x] SubTask 5.4: 内容资产分区：技术分布 BarList、来源 BarList、类型/语言 SplitBar、新鲜度 SplitBar
  - [x] SubTask 5.5: 审核合规分区：审核 Donut、审核指标小卡（通过率/平均时长/最久待审）、规则 Top5 BarList、动作 SplitBar
  - [x] SubTask 5.6: 问答检索分区：Trend、命中率 Donut、盲区 Top5 列表、平均引用数小卡
  - [x] SubTask 5.7: 用户互动分区 + 系统健康分区（覆盖率、库体积、最近导入/扫描/问答时间）
  - [x] SubTask 5.8: 骨架屏、失败行内重试、分区内行内空态（如「近 7 天无记录」）、全库空态沿用现有引导
  - [x] SubTask 5.9: 口径角标：建 `METRIC_HINTS` 常量（分子/分母/时间窗），每个面板标题旁渲染 `el-tooltip` 问号；环比文案统一「较上一周期 ▲/▼ N%」，上期为 0 显示「—」

- [x] Task 6: 响应式与可访问性收口
  - [x] SubTask 6.1: 断点：卡片 4×2 → 2 列（≤1180）→ 1 列（≤640）；分区面板 ≤1100 单列；系统健康横跨两列
  - [x] SubTask 6.2: 统一 token（hairline、零阴影、`--brand-600` 小面积强调）、等宽数字
  - [x] SubTask 6.3: 每个图表 `role="img"` + `aria-label`；检查无横向滚动与文字截断

- [x] Task 7: 验证
  - [x] SubTask 7.1: `.\mvnw.cmd test` 全量通过
  - [x] SubTask 7.2: `npm run check`、`npm run build` 通过
  - [ ] SubTask 7.3: 浏览器实测 `/admin`：7/30 切换、环比文案、刷新、卡片跳转、空态与窄屏（≤900px）布局（**阻塞：缺少管理员登录态，访问 /admin 被重定向到 /login，待人工登录后确认**）

- [x] Task 8: 文档与提交
  - [x] SubTask 8.1: `agent.md` 补坑位/进度/日志索引；`docs/devlog/2026-10-27.md` 追加概览重设计小节（含统计口径表与隐私边界）
  - [x] SubTask 8.2: 按约定提交 `feat: 管理端概览页重做为全系统状态看板`（commit 704fd15）

# Task Dependencies

- Task 2 depends on Task 1
- Task 4 与 Task 1 无依赖，可并行；Task 3 depends on Task 1
- Task 5 depends on Task 3、Task 4
- Task 6 depends on Task 5
- Task 7 depends on Task 2、Task 5、Task 6
- Task 8 depends on Task 7
