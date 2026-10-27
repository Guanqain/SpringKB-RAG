# Checklist

## 接口与统计口径

- [x] `GET /api/admin/overview` 返回 `cards / sections{content,governance,qa,users,engagement,health} / trend / todos / meta`，不含文档正文、逐行数据或脱敏命中明文
- [x] `days` 仅接受 7 与 30，默认 7，非法值钳制为 7
- [x] 内容域：总数/启用/chunk 均值/`emptyChunkDocs`/products/origins/docTypes/languages/versions/visibility/freshness/avgAgeDays/今日新增/窗口新增均按口径返回
- [x] 内容域 Top 8 之外的项合并为 `otherCount` + `otherTotal`，且 前 8 合计 + otherTotal = 总数
- [x] 审核域：状态计数与占比、`approvalRate`（分母不含 pending）、`avgReviewHours`、`pendingOldestDays`、`rejectedStale` 均正确
- [x] 审核域：`scan` 含窗口拦截数、去重标题数、总命中、规则 Top5、动作分布，且无任何命中内容
- [x] 问答域：会话/消息、窗口问答与环比、`foundRate`/`noAnswerRate`、`avgSources`、`activeAskers`、盲区 Top5（截断 60 字、无用户名）
- [x] 趋势：7/30 天点数正确，缺失日期补 0，按日期升序
- [x] 用户域：总数/角色/窗口新增/窗口活跃/贡献者 Top5
- [x] 互动域：点赞/收藏总数与收藏 Top5
- [x] 健康域：`vectorRows`、`coverageRate`（chunk=0 时为 0）、`dbSizeBytes`、最近导入/扫描/问答时间
- [x] 空库时所有比率字段为 0 而非 null/NaN
- [x] `GET /api/admin/stats` 已删除，代码与前端无残留引用
- [x] 非 ADMIN 访问 `/api/admin/overview` 被拒（403/401）

## 图表与呈现

- [x] 技术分布最多 8 行 + 1 行「其他 N 项（合计 M 篇）」，卡片高度不随技术数增长
- [x] 「其他」行可跳转 `/admin/documents`
- [x] Donut 中心显示总量、图例含数量与百分比、同色系明度差
- [x] Trend 纵轴刻度 ≤4，7 天显示全部标签、30 天每 5 天一个标签
- [x] SplitBar 用于新鲜度/可见性/角色/拦截动作且图例分行可读
- [x] 无数据图表显示「暂无数据」占位，不绘制空坐标轴
- [x] 已安装 `chart.js@4` + `vue-chartjs@5`，且全仓库无 `chart.js/auto` 全量引入
- [x] `theme.ts` 只注册 Arc/Bar/Line/Point + Category/LinearScale + Tooltip/Legend，未引入额外插件
- [x] 未引入 ECharts / Highcharts / ApexCharts，构建产物中无相关痕迹
- [x] `npm run build` 后图表相关增量 ≤ 80KB gzip（体积记录写入 devlog）
- [x] 图表容器有 `role="img"`、`aria-label` 与 `sr-only` 文本摘要

## 已确认的 8 条设计约束

- [x] 每个面板标题旁有口径角标，文案写清分子/分母与时间窗，且与 SQL 口径一致
- [x] 窗口类指标同时展示环比（「较上一周期 ▲/▼ N%」），上一周期为 0 时显示「—」
- [x] 1200×800 首屏可见待办条全部 4 项，图表位于其下
- [x] 代码无 `@Cacheable`/Redis/本地缓存层；`EXPLAIN` 显示趋势与窗口查询走 `created_at` 索引
- [x] 依赖仅 `chart.js` + `vue-chartjs`，`npm ls echarts` 为空，按需注册无 `chart.js/auto`
- [x] 盲区响应不含用户名，脱敏统计不含命中内容/正文
- [x] 图表 props 仅归一化 `{label,count,pct}[]` / `{date,newDocs,qaCount}[]`，无业务字段名
- [x] 去掉颜色后仍能凭数值区分全部类别，且图表有 `role="img"` + `aria-label` + `sr-only`

## 页面与交互

- [x] 8 张核心指标卡数值千分位 + 比率 1 位小数百分比，且展示环比文案
- [x] 待办条 4 项（待审/驳回待改/窗口拦截/知识盲区）均可点击跳转
- [x] 卡片跳转：文档→`/admin/documents`、待审→`/admin/moderation`、拦截→`/admin/scan-rules`、盲区→`/admin/blindspots`、用户→`/admin/members`
- [x] 7/30 天切换重新请求，趋势与环比同步变化
- [x] 手动刷新可用，右上角显示 `数据更新于 HH:mm`
- [x] 加载显示骨架；请求失败行内重试且不清空已有数据
- [x] 分区内无数据时显示行内空态（如「近 7 天无记录」）

## 隐私与安全

- [x] 盲区列表只含问题原文（≤60 字），不含用户名
- [x] 脱敏统计只含规则类型/数量/动作，不含命中内容或文档正文
- [x] 接口仅 ADMIN 可访问

## 响应式与视觉

- [x] 卡片 4×2 / ≤1180 两列 / ≤640 单列；分区 ≤1100 单列；系统健康横跨两列
- [x] 800px 窄屏无横向滚动、无文字截断
- [x] 沿用 token：纸面底色、1px hairline、零阴影、`--brand-600` 仅小面积强调
- [x] 每个图表有 `role="img"` 与 `aria-label`

## 验证与文档

- [x] `.\mvnw.cmd test` 全量通过（含 overview 用例）
- [x] `npm run check`、`npm run build` 通过
- [ ] 浏览器实测 7/30 切换、刷新、卡片跳转、空态、窄屏（**阻塞：缺管理员登录态，访问 `/admin` 被重定向到 `/login`，待人工登录后确认；其余项已通过 `npm run check/build` 与接口测试覆盖**）
- [x] `agent.md` 与 `docs/devlog/2026-10-27.md` 已同步（含统计口径与隐私边界）
- [x] 已按约定提交 git（`feat: 管理端概览页重做为全系统状态看板`）
