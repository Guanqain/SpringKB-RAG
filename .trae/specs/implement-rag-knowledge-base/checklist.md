# Checklist

> 验收口径（2026-10-19 修订）：每模块需 **mvn test 通过 + 健壮性/可行性/安全性审查 + 文档同步** 才能勾选。

## 基线
- [x] 项目可启动，假数据链路可用；API Key / JWT_SECRET 走环境变量
- [x] 认证模块（注册/登录/鉴权/前端登录态）实现并验证
- [x] 结构化爬虫（三源适配器 + HtmlToMarkdown）实现，240 篇样本落盘
- [x] 数据库四表（kb_user/kb_document/kb_chunk/vector_store）落地且幂等

## Task 1 · 导入链路
- [x] 分块器按 headings 切、块 500~1000 token、产出 ordinal/heading_path/token_count
- [x] kb_chunk 双字段 + image_refs 写入正确
- [x] vector_store 写入（metadata 按 §4.4 规范，id 与 kb_chunk 对齐）
- [x] 幂等：重复导入文档总量不变
- [x] 测试通过（分块边界/幂等/双字段一致性）；审查结论已记录

## Task 2 · 版本过滤检索（能力一）
- [x] `version` 参数在 pgvector 查询层过滤生效（非内存后过滤）
- [x] 元数据字段规范落地（§4.4）
- [x] 测试通过：version=X 只召回 X；inactive 不召回

## Task 3 · 来源权威性加权（能力二）
- [x] 初检 topK×3 → official +0.15 重排 → 取前 5 实现
- [x] 接口返回体现重排顺序与来源
- [x] 测试通过：official 排前、community 仍在；排序稳定

## Task 4 · 无答案兜底 + 引用溯源（能力三）
- [x] 低于阈值 0.65 不调用 Chat 模型，返回"知识库中未找到相关答案"
- [x] 回答带 [1][2] 引用并标明 official/community 来源
- [x] 引用回源：kb_chunk 原文 + kb_document 元数据（title/version/section/heading_path）
- [x] 测试通过：兜底零模型调用；引用编号与 sources 对齐

## Task 5 · 真实数据灌库与验证用例
- [x] 真实文档入库，规模真实（非假数据）
- [x] 三条验证用例跑通并逐条记录到测试用例文档

## Task 6 · 管理接口与前端
- [x] 文档列表 / 导入 / 版本状态切换接口可用且鉴权（未登录 401）
- [x] 前端三动作联调可用，inactive 文档不被召回

## Task 7 · README 与架构图
- [x] 问题背景、三能力设计验证、架构图、已知限制齐全

## Task 8 · Docker Compose 部署
- [ ] app + postgres 一键启动，接口可访问

## Task 9 · 演示素材
- [ ] 截图/录屏与面试话术就绪

## 全局
- [ ] 所有模块 mvn test 通过
- [ ] 所有改动已同步 docs/详细设计.md、agent.md、docs/devlog/
