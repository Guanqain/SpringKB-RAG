# Tasks

- [x] Task 0: 项目骨架与假数据链路（已完成，基线）
  - [x] Boot 4.0.8 + Spring AI 2.0.0 项目可启动，依赖解析无误
  - [x] 两段假文档（official/community）入库 → 检索 → 问答链路跑通（`/api/demo/search`、`/api/demo/ask`）
  - [x] 环境：pgvector 由 `docker compose up -d` 托管，API Key 走环境变量

- [ ] Task 1: 实现版本过滤检索（能力一）
  - [ ] 确定向量库元数据字段规范（`source/version/active/title/section/so_score`）
  - [ ] 检索接口增加 `version` 参数，通过 Spring AI filter expression 在 pgvector 层过滤
  - [ ] 验证：`version=3.3` 查询只召回 3.3 文档

- [ ] Task 2: 实现来源权威性加权（能力二）
  - [ ] 检索逻辑：初检 topK×3 → `official` 来源 +boost(0.15) 重排 → 取前 5
  - [ ] 在检索/问答接口返回中体现重排后的顺序与来源
  - [ ] 验证：official 排前、community 仍在结果中

- [ ] Task 3: 实现无答案兜底与引用溯源（能力三）
  - [ ] 兜底：最高相似度 < 0.65 时直接返回"知识库中未找到相关答案"，不调用 Chat 模型
  - [ ] Prompt 强制 `[1][2]` 引用标注 + official/community 来源说明
  - [ ] 回答中附加引用对应的原文片段/元数据信息
  - [ ] 验证：无关问题触发兜底，命中问题回答带引用

- [ ] Task 4: 真实数据获取与导入
  - [ ] Jsoup 抓取 Spring 3.2 与 3.3 的 `web`、`data`、`howto` 章节 → 纯文本 → 分块（固定 token 窗口）
  - [ ] SEDE 导出 `spring-boot` 标签高赞问答（评分 ≥5、2023 年后）CSV → 清洗 HTML → 分块
  - [ ] 内部入库接口/runner 灌入真实数据（幂等，chunk 元数据完整）
  - [ ] 验证：库内文档达到目标规模（官方 50-100 chunk、SO 200-500 条）

- [ ] Task 5: 三条验证用例跑通并记录
  - [ ] 版本过滤用例：仅召回 3.3，无 3.2 内容
  - [ ] 权威性加权用例：official 排前、community 均在结果中
  - [ ] 无答案兜底用例：返回"知识库中未找到相关答案"
  - [ ] 输出测试用例文档（每条记录提问、召回、回答、结论）

- [ ] Task 6: 最简管理界面
  - [ ] 文档列表页（来源/版本/状态/chunk 数）
  - [ ] 文档上传/导入入口
  - [ ] 版本状态切换（active/inactive），切换后影响检索召回

- [ ] Task 7: README 与架构图
  - [ ] 问题背景 → 三个能力的设计与验证 → 架构图 → 已知限制（含 Spring AI 2.0 生态、chunking 粗、boost 未系统调优、pgvector 上限）

- [ ] Task 8: Docker Compose 部署
  - [ ] 应用容器化（Dockerfile），compose 编排 app + postgres
  - [ ] `docker compose up -d` 一键启动，接口可访问

- [ ] Task 9: 演示素材与面试话术
  - [ ] 截图/录屏素材（版本过滤、权威性排序、兜底三个演示）
  - [ ] 面试话术整理（每个能力对应 STAR 故事、"和 Dify/扣子区别"的回答）

# Task Dependencies

- Task 0（基线）先行，Task 1/2/3 依赖 Task 0
- Task 1（版本过滤）为 Task 5 前置；Task 4（真实数据）为验证用例 Task 5 提供数据
- Task 2、Task 3 可与 Task 4 并行
- Task 5 依赖 Task 1、2、3、4
- Task 6 依赖 Task 1 与 Task 4（需真实数据与版本元数据）
- Task 7、Task 9 依赖 Task 5（验证结果素材）
- Task 8 依赖 Task 0 与 Task 5（部署验收口径）
