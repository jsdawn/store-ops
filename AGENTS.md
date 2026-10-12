## 项目 Skills 路由（.workbuddy/skills/）

开发任务命中下表场景时，**动手前必须先读取对应 Skill 的 SKILL.md 并遵循其工作流**（按 skill 指引读取 references 与目标模块真实代码），不要凭通用知识直接写代码：

| 子项目 / 任务 | Skill | 触发时机 |
| --- | --- | --- |
| `store-ops-api`（后端） | `ruoyi-plus-ai-coding` | 新增/修改 CRUD 模块、controller/service/mapper/BO/VO/entity、MyBatis-Plus 查询、数据权限、缓存、翻译注解、common 公共模块能力、JavaDoc |
| `store-ops-admin`（管理台） | `frontend-crud-coding` | 新增/修改 CRUD 列表页、树表页、系统管理/监控/workflow 页面，配套 `src/api` 与 types、字典接入 |
| `store-ops-mp`（小程序）框架层 | `uniapp-project`、`uniapp-mini-guide`、`uni-app` | uni-app 组件/API 用法、跨端兼容、小程序配置与生命周期 |
| `store-ops-mp`（小程序）UI 组件 | `wot-ui-v2` | wot-ui 组件选型、`wd-*` 组件用法、主题定制、常见坑排查（可配 wot-ui MCP 查询版本准确 API） |

使用规则：

1. **多端任务依次加载**：如「后端接口 + 管理台页面」或「后端接口 + 小程序页面」这类同域双端开发，先加载后端 skill，再加载对应前端 skill；两端共享的 service 约定以后端 skill 与详细设计文档为准。
2. **Skill 与事实冲突**：SKILL.md 描述与代码/配置实际不符时，以项目事实为准，并在回复中指出冲突位置。
3. **新增 skill**：放入 `.workbuddy/skills/<skill-name>/SKILL.md`（含 `name`/`description` frontmatter），并同步本表。
4. 本表按当前 monorepo 划分维护；子项目目录调整或 skill 增删时保持更新。
