# SpringKB 前端 v2 重建 Spec

**change-id**：`rebuild-frontend-v2`
**基准原型**：项目根目录 `demo.html`（8 视图单文件高保真原型）
**关联文档**：[v2产品规划.md](../../../docs/v2产品规划.md)、[详细设计_v2.md](../../../docs/详细设计_v2.md)、[agent.md](../../../agent.md)

---

## Why

现有前端是「管理端优先」的形态（Element Plus 侧边栏壳 + 独立的深色玻璃问答页），与 v2 定稿定位（**私有化单实例团队知识库 + RAG**）不匹配：信息架构缺少首页 / 知识库导航 / 文档详情 / 账户体系；问答页视觉与 `demo.html` 原型脱节；管理端入口散落在顶层路由。

用户要求：**以 demo.html 为设计基准，废弃旧前端方案，重建 v2 前端**（复用可复用代码并减少代码量），技术栈沿用（Vue 3 + Vite + Element Plus + Pinia + Vue Router + axios + TS）；**先迁移并测试原有已实现功能，确认迁移成功后**再进入 v2 新功能开发；迁移过程中**保持前后端接口一致**；与 v2 目标冲突的业务逻辑**只列出、不擅自修改**。

---

## What Changes

- **前端设计文档升级为 v2**：`docs/用户端前端设计.md` 由「问答页 v1.1」升级为覆盖**全前端（用户端 + 管理端）**的「前端界面设计 v2」。
- **视觉基座重建**：采用 demo 的暖橘 token 体系（`--brand-50..800` / ink 中性阶 / `--grad-brand` 渐变 / 暖色投影 / 圆角 & 间距阶梯 / 暖白底 `#FDFCFA`）；**保留 Element Plus**，通过全局 CSS 变量覆盖（主色 → `--brand-500`）使之贴合 demo 风格。
- **应用外壳重建**：主导航收敛为 **首页 / 问答 / 知识库** 三项；账户类入口（个人中心 / 写文档 / 我的收藏 / 管理后台 / 退出登录）收于右上**头像菜单**；新增站点 Footer。
- **路由重构**：
  - 用户端：`/`（首页）、`/qa`（问答）、`/kb`（知识库左树右卡）、`/kb/doc/:id`（文档详情）、`/login`
  - 账户/后端未就绪页（外壳占位）：`/setup`、`/me`、`/editor`
  - 管理端：`/admin`（深色侧边栏独立形态）+ 子路由（概览 / 文档管理 / 数据导入 / 成员管理 / 问答统计 / 知识盲区 / 待复审）
  - 原顶层 `/documents`、`/documents/import` 迁移为 `/admin/documents`、`/admin/import`
- **迁移已实现功能（消费现有接口，契约不变）**：问答、登录/注册、管理端文档列表 / 导入 / active 切换 / 删除 / chunks。
- **搭建外壳（UI 骨架，未就绪接口处为空态/占位，不 mock 业务逻辑）**：首页统计、知识库、文档详情、Setup 向导、个人中心、写文档、管理端成员/统计/盲区/复审。
- **代码减量**：删除旧侧边栏布局、顶层文档路由、深色玻璃问答视觉等被取代实现；合并重复组件与重复样式；清理未使用代码与依赖引用。
- **标注待确认业务/后端变更清单**（见下方 §待确认 —— 不可擅自修改）。

---

## Impact

- **Affected specs（能力层）**：前端信息架构与路由、问答页（`用户端前端设计.md` → v2）、管理端页面归属、设计 token 体系。
- **Affected code（关键文件）**：
  - 前端改造：`frontend/src/App.vue`、`main.ts`、`router/index.ts`、`styles/theme.css`、`views/*`、`components/*`、`api/*`、`stores/*`、`types/*`
  - 前端新增：`components/layout/{NavBar,UserMenu,AppFooter,PageHead}.vue`、`components/admin/AdminLayout.vue`、`views/{Home,KbHome,KbDoc,Setup,Me,Editor}.vue`、`views/admin/*`
  - 前端删除/替换：旧 `App.vue` 侧边栏布局、`views/{Dashboard,DocumentList,ImportDocument}.vue`（迁移进 admin）、`views/QaHome.vue` 深色玻璃视觉
  - 后端：**本变更不修改后端**；仅消费现有接口（见 §接口契约基线）。所需后端变更全部列入 §待确认。
- **其他文档**：`agent.md`（进度/D 决策）、`docs/devlog/YYYY-MM-DD.md` 需同步。

---

## 接口契约基线（迁移期间必须保持不变）

以下为前端消费的**现有后端接口**，迁移过程中**不得更改请求/响应结构**：

| 接口 | 方法/路径 | 契约要点 |
| --- | --- | --- |
| 注册 | `POST /api/auth/register` | `{username,password}` → `AuthResponse{token,user{id,username,role}}` |
| 登录 | `POST /api/auth/login` | 同上 |
| 当前用户 | `GET /api/auth/me` | → `UserDto` |
| 版本列表 | `GET /api/qa/versions` | → `string[]`（公开） |
| 问答 | `POST /api/qa/ask` | `{question,version?,topK?}` → `QaResult{found,answer,sources[]}` |
| 检索 | `GET /api/qa/search` | `?q&version&topK` → `List<Map>` |
| 文档列表 | `GET /api/admin/documents` | `?source&version&active&keyword&page&size` → 全量数组或分页 `PageResult` |
| 导入 | `POST /api/admin/documents/import` | `ImportRequest` → `ImportResult{imported,skipped,docId}` |
| active 切换 | `PATCH /api/admin/documents/{id}/active` | `{active}` → `DocumentDto` |
| 删除 | `DELETE /api/admin/documents/{id}` | → `null` |
| chunk 列表 | `GET /api/admin/documents/{id}/chunks` | → `Chunk[]` |

---

## 待确认（业务逻辑冲突 —— 只列出，**不擅自修改**）

> 以下为 v1 现有业务逻辑/接口与 v2 目标冲突之处。**本变更不实施**，待用户逐条裁决后再单独开变更处理。前端迁移期间保持现状行为不变。

| # | 冲突点 | v1 现状 | v2 目标 | 影响面 |
| --- | --- | --- | --- | --- |
| C1 | 开放注册 | `POST /api/auth/register` permitAll，任何人可注册成成员 | setup 向导创建首个 admin，成员由 admin 邀请/管理（私有化团队） | SecurityConfig、AuthController、登录页注册 tab、路由 |
| C2 | 问答免登录 | `/qa` public + `/api/qa/**` permitAll | 默认全站登录；`allow_anonymous` 由 setup 决定是否放行 /qa、/kb 只读 | SecurityConfig、路由守卫、导航 |
| C3 | 无角色区分 | 任意登录用户即可访问 `/api/admin/*` | admin/member 两角色，管理端仅 admin | SecurityConfig、管理端路由守卫 |
| C4 | 文档可见性缺失 | 无 `visibility`/`owner_id`，检索无可见性过滤 | private/team 两级可见，检索强制过滤 | 后端 schema/检索、知识库与详情页展示 |
| C5 | 权威性仅两档 | `source ∈ {official, community}` | 三档 official/ugc/community（badge 与加权） | DocumentDto、检索加权、前端 badge |
| C6 | 来源为 chunk 级 | `SourceRef` 为 chunk 粒度，无原文跳转 | 文档级聚合 `sourcesV2` + `originalUrl` 原文跳转 | QaService、QaResult、问答页来源卡 |
| C7 | 文档 DTO 字段不足 | 仅 source/version/title/active/chunkCount/时间 | 需 product/origin/authority/language/doc_type/last_modified | DocumentDto、知识库/详情页 |
| C8 | 管理端能力缺口 | 仅列表/导入/active/删除/chunks | 需 成员管理/问答统计/盲区/待复审/历史与反馈 | 后端接口新增、管理端页面 |

> **处理约定**：C1~C8 的裁决结论落地前，前端对相关页面（注册 tab、免登录 /qa、知识库/详情数据、管理端占位项）一律**保持现状或空态占位**，不引入新的业务判断。

---

## ADDED Requirements

### Requirement: 前端界面设计 v2 文档
项目 SHALL 将 `docs/用户端前端设计.md` 升级为覆盖全前端的「前端界面设计 v2」，包含：设计 token、信息架构与路由表、导航/头像菜单结构、8 视图清单与阶段归属、组件库策略（保留 Element Plus + demo 定制层）、复用与删减清单、迁移验收清单、§待确认（C1~C8）。

#### Scenario: 评审可依
- **WHEN** 文档产出后
- **THEN** 用户可据此确认视觉与信息架构，并逐条裁决 C1~C8

### Requirement: 暖橘设计基座与 Element Plus 定制
前端 SHALL 以 demo 的暖橘 token 体系重建全局样式（`styles/theme.css`），并覆盖 Element Plus 主题变量使组件主色/圆角/字体贴合 demo。

#### Scenario: 视觉一致
- **WHEN** 任一页面渲染
- **THEN** 主色为 `--brand-500`、底色暖白 `#FDFCFA`、卡片圆角与暖色投影与 demo 一致，Element Plus 组件（按钮/表格/标签/弹窗）风格协调

### Requirement: 应用导航外壳与头像菜单
主导航 SHALL 仅含 首页/问答/知识库 三项并高亮当前项；账户类入口 SHALL 收于右上头像菜单（个人中心、写文档、我的收藏、管理后台、退出登录）；页面 SHALL 含统一 Footer。

#### Scenario: 导航收敛
- **WHEN** 用户浏览任意用户端页面
- **THEN** 顶部为品牌 + 三项主导航 + 头像菜单，管理端入口不出现在主导航

### Requirement: v2 路由与页面骨架
路由 SHALL 收敛为 `/`、`/qa`、`/kb`、`/kb/doc/:id`、`/login`、`/setup`、`/me`、`/editor`、`/admin/*`；管理端 SHALL 采用独立深色侧边栏形态。

#### Scenario: 路由可直达
- **WHEN** 直接访问任一路径（含刷新）
- **THEN** 由 SPA 回退正确渲染对应视图（不落 404）

### Requirement: 迁移已实现功能（契约不变）
系统 SHALL 将现有已实现功能按 v2 视觉重建，同时**保持 §接口契约基线 不变**：问答、登录/注册、管理端文档列表 / 导入 / active 切换 / 删除 / chunks。

#### Scenario: 问答迁移成功
- **WHEN** 用户在 `/qa` 提问（命中与兜底两种情形）
- **THEN** 复用现有 `ask`/`versions` 接口，答案卡、来源卡、`[n]` 角标联动、兜底卡行为与 v1 一致，仅视觉更新为 demo 风格

#### Scenario: 管理端迁移成功
- **WHEN** 登录用户进入 `/admin/documents`
- **THEN** 过滤/列表/active 切换（含提示文案正确）/删除/chunks 抽屉行为与 v1 一致，接口仍为 `/api/admin/*`

### Requirement: 外壳页占位
对依赖未就绪后端的页面（首页统计、知识库、文档详情、Setup、个人中心、写文档、管理端成员/问答统计/知识盲区/待复审），系统 SHALL 提供**最小 UI 骨架 + 明确空态**（如「功能开发中 / 待后端就绪」），**不得**用 mock 假数据伪造业务逻辑。

#### Scenario: 不伪造能力
- **WHEN** 用户访问上述任一页面
- **THEN** 页面结构可见但数据区展示空态提示，不展示编造的统计/文档/成员数据

---

## MODIFIED Requirements

### Requirement: 问答页（QaHome）
由「独立深色玻璃（`--qa-bg #171210`）单栏卡片」视觉，改为 demo 的**暖白底 + 居中卡片 + 文档级来源卡演示位 + 相关推荐演示位 + 追问 chips** 视觉；`[n]` 角标联动与 `ask`/`versions` 数据流保持不变。来源卡在 C6 裁决前维持 chunk 级（`SourceRef`），相关推荐在具备后端能力前为占位。

### Requirement: 管理端页面归属
原顶层路由 `/`(仪表盘)、`/documents`、`/documents/import` 迁入 `/admin` 命名空间，并纳入 demo 的**深色侧边栏**布局；页面逻辑与 `/api/admin/*` 契约不变。

---

## REMOVED Requirements

### Requirement: 旧侧边栏管理端布局
**Reason**：被 demo 的顶部导航 + 头像菜单 + 独立管理端深色侧边栏取代。
**Migration**：`App.vue` 侧边栏结构删除；管理端三页迁入 `/admin` 并套用 `AdminLayout`。

### Requirement: 顶层 `/documents`、`/documents/import` 路由
**Reason**：信息架构收敛，管理端统一收进 `/admin`。
**Migration**：路由与代码内跳转目标改为 `/admin/documents`、`/admin/import`；后端 `SecurityConfig` 中 `/documents/**` 放行若需同步，列入 §待确认（后端改动）。

### Requirement: 深色玻璃问答视觉（`用户端前端设计.md` §8 v1.1）
**Reason**：用户要求废弃旧前端方案，统一 demo 视觉语言。
**Migration**：`QaHome.vue` 视觉重绘为 demo 暖橘风；`AnswerCard`/`SourceCard`/`AskInput`/`FallbackCard`/`HistoryList` 复用逻辑、样式适配新 token。
