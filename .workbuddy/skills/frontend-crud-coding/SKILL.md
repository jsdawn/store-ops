---
name: frontend-crud-coding
description: 在 store-ops-admin（plus-ui 5.6.2，Vue 3 + TypeScript + Element Plus）前端项目中按真实代码风格生成或修改页面、API、types、字典接入和样式。当用户需要新增或修改标准 CRUD 列表页、树表页、系统管理页、监控页、workflow 页面、demo 页面，补齐与后端接口对应的 src/api、types 和 src/views 代码时使用；基线为 Gitee 仓库 JavaLionLi/plus-ui 的 5.X-Vue 分支（5.6.2），触发后应先读取适用 references，再阅读目标模块真实代码，代码生成模板在后端仓库 ruoyi-gen 的 vm/ 目录。
agent_created: true
---

# 前端编码规范

先对齐当前前端项目里的真实实现，再参考后端仓库 `ruoyi-gen` 的 `vm/` 代码生成模板。不要只套通用 Vue 模板，也不要把 generator 模板原样复制进来而忽略当前项目已经形成的组件、类型入口和下载方式。

## 项目基线

- 基线仓库：`https://gitee.com/JavaLionLi/plus-ui`
- 默认分支：`5.X-Vue`（当前版本 5.6.2，对应后端 RuoYi-Cloud-Plus 2.6.2）
- 远端引用必须同时标记仓库、分支和文件路径，例如 `https://gitee.com/JavaLionLi/plus-ui/blob/5.X-Vue/src/views/demo/demo/index.vue` 或 `branch=5.X-Vue, path=src/views/demo/demo/index.vue`。
- 不要在 skill、reference 或输出文档中写本机绝对路径；本地文件读取以运行时工作目录（`store-ops-admin/`）为准。
- 如果用户指定其他分支，先按用户分支读取同一仓库的对应文件，并在结果中说明使用的分支。
- 代码生成模板（`index.vue.vm`、`index-tree.vue.vm`、`api.ts.vm`、`types.ts.vm`）位于后端仓库 `ruoyi-modules/ruoyi-gen/src/main/resources/vm/`，前端工程内没有 `gen/` 目录。

## 执行流程

1. 判断任务类型：新增标准 CRUD、树表、已有页面增强、复杂业务页、只补 API/types。
2. 按"文档读取规则"读取必要 reference，不一次性展开所有资料。
3. 阅读目标目录下最近似的真实代码：
   - 标准单表优先看 `src/views/demo/demo/index.vue`、`src/api/demo/demo/*`。
   - 树表优先看 `src/views/demo/tree/index.vue`。
   - 系统复杂页优先看 `src/views/system/user/index.vue`、`system/role`、`system/post`、`system/config`。
   - workflow 业务页优先看 `src/views/workflow/*` 同类页面。
   - 监控、工具页看 `src/views/monitor/*`、`src/views/tool/*`。
4. 新增标准页面前，对照后端 `vm/` 模板确认基础骨架：
   - API 模板：`vm/ts/api.ts.vm`
   - types 模板：`vm/ts/types.ts.vm`
   - 标准单表页模板：`vm/vue/index.vue.vm`
   - 树表页模板：`vm/vue/index-tree.vue.vm`
5. 新增代码时通常同步维护 `src/api/<module>/<business>/index.ts`、`types.ts`、`src/views/<module>/<business>/index.vue`。
6. 增强已有页面时只做增量修改，保留原页面的树筛选、导入导出、列显隐、权限、字典、弹窗和路由跳转能力。
7. 修改完成后按影响范围运行验证：优先 `pnpm exec vue-tsc --noEmit`，改动页面或导入时再跑 `pnpm run lint:eslint`，大范围变更再跑 `pnpm run build:prod`。

## 文档读取规则

- 前端 API、types、页面、字典、样式和验证规则，先读 [references/frontend.md](references/frontend.md)。
- 不确定任务边界、需要标准用例或提问方式时，再读 [references/examples.md](references/examples.md)。
- reference 只约束实现方式和自检范围；发生冲突时，以当前模块真实代码和实际调用点为准。

## 优先级规则

发生冲突时按下面顺序决策：

1. 目标目录下最近似页面、API、types 的真实实现。
2. 当前项目公共组件、工具和样式约定（`src/components`、`src/utils`、`src/plugins`）。
3. 后端 `ruoyi-gen` 的 `vm/` 代码生成模板。
4. 通用 Vue 3 / Element Plus 习惯。

也就是说：

- 同模块已有页面怎么写，优先怎么写。
- 没有现成页面时，使用 `vm/` 模板作为骨架，再改成当前项目风格。
- 复杂模块不能为了"标准 CRUD"退化成裸模板页。

## 仓库通用规则

- 技术栈：Vue 3 + TypeScript + Element Plus + Vite + Pinia + UnoCSS，包管理 pnpm。
- 遵循前端仓库根的 `.editorconfig`：UTF-8、LF、2 空格缩进；Markdown 例外。
- 格式化使用 `pnpm run prettier`（`.prettierrc`），lint 使用 `pnpm run lint:eslint`（`eslint.config.ts`）；当前仓库没有 oxlint/oxfmt。
- 页面使用 `<script setup name="Xxx" lang="ts">`；`getCurrentInstance`、`ref`、`reactive` 等 Vue API 由 unplugin 自动导入。
- 全局类型 `BaseEntity`、`PageQuery`、`PageData`、`DialogOption`、`DictDataOption` 等在 `src/types/global.d.ts` 中 declare，使用时无需 import。
- 请求统一通过 `@/utils/request` 默认导出；导出下载使用 `proxy?.download(url, params, fileName)`；导入上传使用 `globalHeaders()`。
- 新页面不要无故引入另一套状态管理、请求封装、样式体系或权限写法。
- 当前版本没有 `useLoading`、`useFormDialog`、`useSearchReset`、`useTableSelection`、`useDateRangeQuery` 等 6.x hooks；只有轻量 `src/hooks/useDialog.ts`（title + visible 弹窗状态），已有页面用到时跟随。

## 目录映射规则

通常按下面关系组织代码：

- 后端 `/system/user/*` 对应 `src/api/system/user/*` 与 `src/views/system/user/*`
- 后端 `/monitor/xxx/*` 对应 `src/api/monitor/xxx/*` 与 `src/views/monitor/xxx/*`
- 后端 `/workflow/xxx/*` 对应 `src/api/workflow/xxx/*` 与 `src/views/workflow/xxx/*`
- 后端 `/demo/xxx/*` 对应 `src/api/demo/xxx/*` 与 `src/views/demo/xxx/*`

标准新增通常至少包含：

- `src/api/<module>/<business>/index.ts`
- `src/api/<module>/<business>/types.ts`
- `src/views/<module>/<business>/index.vue`

按业务复杂度，可能继续补：

- 导入弹窗
- 详情抽屉或详情页
- 树筛选面板
- 列显隐配置
- 分配/授权子页面
- 自定义 SCSS 样式

## 任务分型

### 1. 标准单表 CRUD

以后端 `vm/vue/index.vue.vm`、`vm/ts/api.ts.vm`、`vm/ts/types.ts.vm` 和 `src/views/demo/demo/index.vue` 为主要起点，补齐列表、搜索、分页、新增、编辑、删除、导出、权限、类型和验证。

### 2. 树表 CRUD

以 `src/views/demo/tree/index.vue` 为主要起点。列表接口返回数组而不是分页结构，页面使用 `proxy?.handleTree` 组树，`Query` 不继承 `PageQuery`。

### 3. 强业务页面

如果页面包含树筛选、导入导出、更多菜单、状态切换、角色分配、详情抽屉、复杂校验、联动选择或独立路由，优先增量修改现有页面。不要重写成简单 CRUD。

### 4. 工作流页面

workflow 目录优先参考 `src/views/workflow/*`。流程定义、流程实例、任务列表、请假申请等页面通常有业务按钮、弹窗和路由跳转，不要硬套 system 模块。

### 5. 只补 API 和 types

只维护 `src/api/<module>/<business>/index.ts` 与 `types.ts`，但仍要与后端路由、返回结构、当前模块导入方式和类型入口一致。

## 输出要求

使用本 skill 时，默认期望产出应满足：

- 类型完整，不把页面逻辑大量写成 `any`。
- API 路径、函数名、权限标识与后端接口保持一致。
- 标准页查询、重置、分页、弹窗、提交、删除、导出流程闭环完整。
- 复杂页面保留原有交互能力和业务约束。
- 代码体现当前项目的 `proxy` 全局用法、`DialogOption` 弹窗和 `proxy?.download` 下载方式，而不是模板裸输出。
- 交付前说明运行过的验证命令；如果无法验证，说明原因。

## 快速检查清单

- `AxiosPromise` 是否从 `axios` 引入（当前版本没有 `@/utils/api-types`）。
- 列表接口是否返回 `AxiosPromise<XxxVO[]>`，页面是否取 `res.rows` / `res.total`（当前版本没有 `PageResult` 类型）。
- API `params` 和 `data` 是否与后端方法一致。
- 日期范围是否通过页面内 `dateRange` ref + `proxy?.addDateRange` 处理。
- 列表 loading、多选、弹窗是否按 5.x 标准 ref / `reactive<DialogOption>` 方式维护。
- 权限指令是否保持同文件一致，默认使用 `v-hasPermi`。
- 导出是否使用 `proxy?.download('<module>/<business>/export', { ...queryParams.value }, '<name>_<timestamp>.xlsx')`。
- 字典是否通过 `proxy?.useDict(...)` + `toRefs` 解构。

## 推荐提问方式

推荐把请求描述到下面粒度：

- 目标模块和业务名
- 后端接口前缀
- 是新增页面、修改页面，还是只补 API/types
- 是否需要导入、导出、树筛选、树表、状态切换、字典、权限按钮
- 希望参考哪个现有页面

例如：

- 使用 `frontend-crud-coding` skill 为 `/system/client` 补一套标准 CRUD 页面，参考后端 vm 模板、`demo/demo` 和 `system/client`。
- 使用 `frontend-crud-coding` skill 修改 `workflow/category` 列表页，增加导出按钮和状态筛选，保持当前 workflow 风格。
