# Tasks

> 交付节奏：**先迁移可用功能并测试通过，再进入 v2 新功能开发**。
> 全程保持后端接口契约不变（见 spec.md §接口契约基线）；**不改后端与业务逻辑**，冲突项仅列入 §待确认。
> 外壳页（依赖未就绪后端）只做 UI 骨架 + 空态，不得用 mock 伪造业务数据。

- [x] Task 1: 产出「前端界面设计 v2」文档
  - [x] 将 `docs/用户端前端设计.md` 升级为覆盖全前端的「前端界面设计 v2」
  - [x] 含：设计 token（沿用 demo 暖橘体系）、信息架构与路由表、导航/头像菜单、8 视图清单与阶段归属、组件库策略（保留 Element Plus + demo 定制层）、复用/删减清单、迁移验收清单
  - [x] 含 §待确认（C1~C8）业务/后端变更清单（只列不改）
  - [x] 门禁：文档评审通过（作为后续实现依据）

- [x] Task 2: 重建暖橘设计基座
  - [x] 重写 `frontend/src/styles/theme.css`：demo token 体系（`--brand-*`、ink 中性阶、`--grad-brand`、暖色投影、圆角/间距阶梯、暖白底 `#FDFCFA`）
  - [x] 覆盖 Element Plus 主题变量（主色 → `--brand-500`、圆角/字体协调）
  - [x] 保留/整理基础 reset 与通用类（badge、page-head、btn）
  - [x] 门禁：`npm run build`（vue-tsc）通过

- [x] Task 3: 重建应用外壳与路由骨架
  - [x] 新增 `components/layout/{NavBar,UserMenu,AppFooter,PageHead}.vue`（主导航 首页/问答/知识库 + 头像菜单 + Footer）
  - [x] 重构 `App.vue`：公开/独立页与主布局分流，移除旧侧边栏结构
  - [x] 重构 `router/index.ts`：`/`、`/qa`、`/kb`、`/kb/doc/:id`、`/login`、`/setup`、`/me`、`/editor`、`/admin/*`
  - [x] 门禁：所有路由可直达/刷新不 404；`npm run build` 通过

- [x] Task 4: 迁移问答页到 demo 视觉
  - [x] `QaHome.vue` 按 demo 布局重绘（暖白底 + 居中卡片 + 来源卡 + 相关推荐/追问 chips 演示位）
  - [x] 复用 `AskInput`/`AnswerCard`/`SourceCard`/`FallbackCard`/`HistoryList` 逻辑并适配新 token
  - [x] 保持 `ask`/`versions` 接口与 `[n]` 角标联动不变
  - [x] 门禁：命中态与兜底态浏览器联调通过

- [x] Task 5: 搭建知识库与文档详情外壳
  - [x] `/kb`：左体系树 + 右文档卡 骨架（数据区空态）
  - [x] `/kb/doc/:id`：详情头 + Markdown 正文区 + 相关推荐位 骨架（数据区空态）
  - [x] 复用现有 Markdown 渲染（marked + DOMPurify）能力
  - [x] 门禁：页面结构完整、空态清晰，不展示伪造数据

- [x] Task 6: 迁移管理端到新管理后台
  - [x] 新增 `components/layout/AdminLayout.vue`（深色侧边栏：概览/文档管理/数据导入 + 成员/问答统计/盲区/待复审占位项）
  - [x] 迁移 `Dashboard`→`/admin`（概览）、`DocumentList`→`/admin/documents`、`ImportDocument`→`/admin/import`
  - [x] 保持 `/api/admin/*` 契约与 v1 行为（过滤、active 提示文案、删除、chunks 抽屉）
  - [x] 门禁：管理端全链路浏览器联调通过（登录后可操作）

- [x] Task 7: 迁移登录页到新视觉
  - [x] `Login.vue` 重绘为 demo 暖橘风，保持 `/api/auth/*` 契约
  - [x] 注册 tab / 免登录 /qa 等与 v2 冲突项按 §待确认保持现状，不擅自更改
  - [x] 门禁：登录 → 进入主页/管理端，刷新保持登录态

- [x] Task 8: 首页 `/`（Home）
  - [x] Hero + 搜索入口 + 统计 + 产品线卡 + 功能区，按 demo 布局
  - [x] 统计数据优先复用现有 `admin` 聚合（登录态）；不可得处空态占位
  - [x] 门禁：首页可用且不伪造数据

- [x] Task 9: 代码减量与清理
  - [x] 删除被取代的旧布局/视觉/路由相关代码与样式（含 `--kb-*` 兼容 token 块）
  - [x] 合并重复组件与重复样式，清理未使用引用
  - [x] 门禁：全量 `npm run build` 通过，无未使用导入告警

- [x] Task 10: 迁移回归测试
  - [x] 前端自检：`npm run build`（vue-tsc）通过
  - [x] 浏览器联调清单：免登录/登录、路由直达、问答命中+兜底、管理端列表/导入/active/删除/chunks、首页、外壳页空态
  - [x] 输出迁移验收记录（见 devlog 2026-10-20 迁移回归表）
  - [x] 门禁：**已实现功能全部迁移成功**（作为进入 v2 开发的前置条件）

- [x] Task 11: 输出待确认清单并等待裁决
  - [x] 整理 C1~C8（见 spec.md §待确认）为可裁决条目（现状/目标/影响/建议）
  - [x] **不实施任何后端或业务逻辑修改**，仅呈报待用户确认
  - [x] 门禁：用户逐条裁决通过后，方可开启 v2 新功能（后端）开发

# Task Dependencies

- Task 2 depends on Task 1（依设计文档）
- Task 3 depends on Task 2
- Task 4 / Task 5 / Task 6 / Task 7 / Task 8 depend on Task 3（可并行）
- Task 9 depends on Task 4、5、6、7、8
- Task 10 depends on Task 9
- Task 11 depends on Task 1（清单已含于设计文档），在 Task 10 后正式呈报
- **v2 新功能（后端）开发** depends on Task 11 用户裁决通过
