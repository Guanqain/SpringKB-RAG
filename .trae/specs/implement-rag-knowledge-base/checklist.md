# Checklist

## 基线
- [x] 项目可启动，假数据链路（入库 → 检索 → 问答）可用
- [x] 数据库由 Docker Compose 托管，API Key 走环境变量

## 能力一 · 版本过滤检索
- [ ] `version` 参数在 pgvector 查询层过滤生效：`version=3.3` 只召回 3.3 文档，不出现 3.2 内容
- [ ] 元数据字段规范落地（source/version/active 等）

## 能力二 · 来源权威性加权
- [ ] 初检 topK×3 → official +0.15 重排 → 取前 5 的逻辑实现
- [ ] 验证用例通过：official 排在 community 之前，两者均在结果中

## 能力三 · 无答案兜底与引用溯源
- [ ] 最高相似度低于 0.65 时不调用 Chat 模型，返回"知识库中未找到相关答案"
- [ ] 回答带 `[1][2]` 引用标注并标明 official/community 来源
- [ ] 引用可溯源到原文片段（元数据含 source_title/section/so_question_id）

## 数据
- [ ] Spring 3.2/3.3 官方文档（web/data/howto）与 SO 问答入库，规模达标（官方 50-100、SO 200-500）
- [ ] 幂等：重复启动/导入不产生重复文档

## 管理界面
- [ ] 文档列表、上传、版本状态切换可用，inactive 文档不被召回

## 文档与部署
- [ ] README 含问题背景、能力设计与验证、架构图、已知限制
- [ ] 测试用例文档记录 3 条验证用例结果
- [ ] `docker compose up -d` 一键部署成功，接口可访问
- [ ] 演示素材（截图/录屏）与面试话术就绪
