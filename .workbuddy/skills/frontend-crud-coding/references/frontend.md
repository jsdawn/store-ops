# 前端约定

## 优先参考的代码来源

- 默认远端基线：`https://gitee.com/JavaLionLi/plus-ui`，分支 `5.X-Vue`（版本 5.6.2）。引用远端文件时必须同时写明 `branch=5.X-Vue` 和仓库内路径。
- 当前目标目录下最近似页面、API、types。
- 标准单表：`src/views/demo/demo/index.vue`、`src/api/demo/demo/index.ts`、`src/api/demo/demo/types.ts`。
- 树表：`src/views/demo/tree/index.vue`。
- 复杂系统页：`src/views/system/user/index.vue`、`src/views/system/role/index.vue`、`src/views/system/post/index.vue`、`src/views/system/config/index.vue`。
- workflow 页：`src/views/workflow/*`、`src/api/workflow/*`。
- 监控页：`src/views/monitor/*`、`src/api/monitor/*`。
- 公共 hooks：`src/hooks/useDialog.ts`（仅此一个，title + visible 弹窗状态）。
- 代码生成模板位于后端仓库 `ruoyi-modules/ruoyi-gen/src/main/resources/vm/`：
  `vm/ts/api.ts.vm`
  `vm/ts/types.ts.vm`
  `vm/vue/index.vue.vm`
  `vm/vue/index-tree.vue.vm`

## 基础栈与格式

- 技术栈是 Vue 3 + TypeScript + Element Plus + Vite + Pinia + UnoCSS。
- 包管理按仓库现状使用 pnpm。
- `.editorconfig` 要求 UTF-8、LF、2 空格缩进。
- 格式化使用 `pnpm run prettier`（仓库有 `.prettierrc`）；lint 使用 `pnpm run lint:eslint`（`eslint.config.ts`）。当前仓库没有 oxlint / oxfmt。
- 不要在一个页面里混入与仓库不一致的格式和写法。
- 不要把本机绝对路径写进 skill 输出或参考文档；跨环境参考统一使用 Gitee 仓库 URL、分支和仓库内相对路径。

## API 文件规则

- 标准 API 文件放在 `src/api/<module>/<business>/index.ts`，同目录维护 `types.ts`。
- 标准 import 形式（跟随 `src/api/demo/demo/index.ts`）：
  `import request from '@/utils/request';`
  `import { AxiosPromise } from 'axios';`
  `import { XxxVO, XxxForm, XxxQuery } from '@/api/<module>/<business>/types';`
- `AxiosPromise` 从 `axios` 引入；当前版本没有 `@/utils/api-types`。
- 列表分页接口返回 `AxiosPromise<XxxVO[]>`（后端 `TableDataInfo` 序列化为 `{ code, msg, total, rows }`，前端直接取 `res.rows` / `res.total`；当前版本没有 `PageResult` 泛型）。
- 树表列表接口返回 `AxiosPromise<XxxVO[]>`，页面用 `res.data`。
- 详情接口返回 `AxiosPromise<XxxVO>`；复杂详情返回单独的 `InfoVO`。
- 标准函数命名：
  `listXxx` -> `GET /<module>/<business>/list`
  `getXxx` -> `GET /<module>/<business>/{id}`
  `addXxx` -> `POST /<module>/<business>`
  `updateXxx` -> `PUT /<module>/<business>`
  `delXxx` -> `DELETE /<module>/<business>/{id or ids}`
  `changeXxxStatus` -> `PUT /<module>/<business>/changeStatus`
- 函数风格为 `export const listXxx = (query?: XxxQuery): AxiosPromise<XxxVO[]> => request({...});`，跟随 `vm/ts/api.ts.vm` 和相邻模块。
- query string 用 `params`，请求体用 `data`。
- 加密、防重复提交等 headers 直接写在请求配置里，例如 `isEncrypt`、`repeatSubmit`。
- 只有相邻模块已有 `export default { ... }` 聚合时才新增默认导出。

## 类型文件规则

- 标准类型定义 `VO`、`Form`、`Query`，必要时补 `InfoVO`、`TreeVO`、`ResetPwdForm` 等扩展类型。
- `Form` 继承 `BaseEntity`，`Query` 继承 `PageQuery`（两者在 `src/types/global.d.ts` 全局 declare，文件内无需 import）。
- 树表 `Query` 通常不继承 `PageQuery`。
- ID 字段通常使用 `string | number`，批量删除参数使用 `string | number | Array<string | number>`。
- Java 数值类型映射为 `number`，Boolean 映射为 `boolean`，日期/文本默认 `string`。
- 日期范围查询保留 `params?: any`，不要因为它看起来宽松就删掉。
- 列表对象、表单对象、查询对象职责分开；字段不一致时不要强行复用一个接口。
- 能明确写出类型时不要用 `any`；组件库、字典或历史接口确实无法收窄时再保留。
- 类型 import 形式跟随相邻文件：demo 模块用 `import { DemoVO, DemoForm, DemoQuery } from '...'`（值导入形式），新页面优先跟随所在目录现状。

## Vue 页面结构规则

- 页面使用 `<script setup name="Xxx" lang="ts">`。
- 通过 `const { proxy } = getCurrentInstance() as ComponentInternalInstance;` 获取全局代理；`getCurrentInstance`、`ref`、`reactive`、`toRefs`、`onMounted` 由 unplugin 自动导入，无需手写 import。
- 标准根节点是 `<div class="p-2">`。
- 标准列表页结构：
  搜索区 `transition` + `v-show="showSearch"` + `el-card shadow="hover"` 内 `el-form :inline="true"`
  表格区 `el-card shadow="hover"`，header 内 `el-row.mb8` 工具栏 + `right-toolbar`
  `el-table` + `pagination`
  新增/编辑 `el-dialog` + `dialog-footer`
- 当前版本没有 `search-panel` / `table-panel` / `toolbar-shell` / `data-table` / `page-shell` 等 6.x 样式体系，新页面不要引入。
- 搜索区显隐由 `const showSearch = ref(true)` 控制，与 `right-toolbar v-model:show-search` 双向绑定。
- 列表 loading 是普通 `const loading = ref(true)`，手动在 `getList` 前后维护。
- 选择状态是普通 ref：`ids`、`single`、`multiple`，在 `handleSelectionChange` 里手动维护。
- 弹窗状态使用 `const dialog = reactive<DialogOption>({ visible: false, title: '' })`；仅当已有页面使用 `src/hooks/useDialog.ts` 时跟随。
- 查询和表单状态放在 `reactive<PageData<XxxForm, XxxQuery>>({ form: { ...initFormData }, queryParams, rules })`，再 `toRefs(data)`。
- 表单引用命名为 `queryFormRef` 和 `<business>FormRef`，类型 `ref<ElFormInstance>()`。

## 页面行为规则

- `getList` 设置 `loading`、调用列表接口、回填 `xxxList.value = res.rows; total.value = res.total`。
- `handleQuery` 先把 `queryParams.value.pageNum = 1`，再调用 `getList()`；树表无分页时只调用 `getList()`。
- `resetQuery` 调用 `queryFormRef.value?.resetFields()` 后 `handleQuery()`；页面有日期范围等额外状态时手动清空。
- `handleAdd` 先 `reset()`，再 `dialog.visible = true; dialog.title = '添加xxx'`。
- `handleUpdate` 先 `reset()`，再按行或 `ids.value[0]` 查详情，`Object.assign(form.value, res.data)`，最后打开弹窗。
- `submitForm` 表单校验通过后设置 `buttonLoading`，根据主键判断新增或修改，成功后 `proxy?.$modal.msgSuccess('操作成功')`、关闭弹窗、刷新列表。
- `handleDelete` 使用 `await proxy?.$modal.confirm('是否确认删除...')` 二次确认，再调用删除接口，成功提示并刷新。
- `handleExport` 使用 `proxy?.download('<module>/<business>/export', { ...queryParams.value }, '<business>_${new Date().getTime()}.xlsx')`。
- 状态切换失败时要把 switch 值回滚，参考 generator 模板和现有 `system/user`、`system/role`。
- 导入上传使用 `globalHeaders()`（`import { globalHeaders } from '@/utils/request'`）、`import.meta.env.VITE_APP_BASE_API`、`el-upload`，优先参考 `system/user` 或流程定义页面。

## 字典、权限与公共工具

- 字典使用全局代理：
  `const { sys_normal_disable } = toRefs<any>(proxy?.useDict('sys_normal_disable'));`
  （`useDict` 挂在 proxy 上，不需要单独 import；底层实现在 `src/utils/dict.ts`。）
- 新增代码默认使用 `v-hasPermi`。
- 如果正在修改的文件已使用其他写法，保持同文件现状，不为统一写法而重排无关代码。
- `el-dropdown-item` 延迟加载导致权限指令不可靠时，使用 `v-if="checkPermi([...])"`，`checkPermi` 从 `@/utils/permission` 引入，参考 `system/user`。
- 常用工具：
  `proxy?.$modal`（confirm / msgSuccess / msgError / loading）
  `proxy?.download` / `import { globalHeaders } from '@/utils/request'`
  `proxy?.parseTime`、`proxy?.handleTree`（实现在 `src/utils/ruoyi.ts`）
  `checkPermi` from `@/utils/permission`

## 组件与样式规则

- 优先复用公共组件：`right-toolbar`、`pagination`、`dict-tag`、`image-preview`、`image-upload`、`file-upload`、`editor` 等（均在 `src/components`，部分已全局注册）。
- 标准页面使用 5.x 默认结构（`p-2` + `el-card`），不堆大量内联样式。
- 需要自定义样式时在页面内加 `<style lang="scss" scoped>`，跟随相邻页面写法。
- 不要为了单页需求修改全局组件样式。

## 树表规则

- 树表列表接口返回数组，页面通过 `proxy?.handleTree<TreeVO>(res.data, 'id', 'parentId')` 组树，参考 `src/views/demo/tree/index.vue`。
- 使用 `row-key`、`:tree-props="{ children: 'children', hasChildren: 'hasChildren' }"`。
- 当前版本没有 `useTreeTableExpand`，展开/折叠按现有页面写法实现。
- 表单中上级节点使用 `el-tree-select`，选项树同样用 `proxy?.handleTree` 构造。
- 新增子节点时从当前行回填 `parentId`。
- 删除确认文案优先使用业务名称，而不是批量 ID 文案。

## 与代码生成模板的关系

- 生成模板位于后端仓库 `ruoyi-modules/ruoyi-gen/src/main/resources/vm/`；远端对照可看 `https://gitee.com/JavaLionLi/plus-ui` 对应版本，但本仓库以 `vm/` 目录为准。
- 新增标准单表页面时读取 `vm/vue/index.vue.vm`、`vm/ts/api.ts.vm`、`vm/ts/types.ts.vm`。
- 新增树表页面时读取 `vm/vue/index-tree.vue.vm`、`vm/ts/api.ts.vm`、`vm/ts/types.ts.vm`。
- 模板是标准骨架，不是最终答案；落地时仍要对照目标模块真实页面和现有工具。
- 修改已有页面时，不要把现有强业务逻辑替换回模板的简化逻辑。

## 验证规则

- 只改文档或 skill：运行 skill 基础校验即可。
- 改前端 TS/Vue/API/types：优先运行 `pnpm exec vue-tsc --noEmit`。
- 改页面模板、import、权限或较多文件：再运行 `pnpm run lint:eslint`。
- 改公共组件、构建相关或大范围页面：再运行 `pnpm run build:prod`。
- 如果验证因为环境、依赖或权限失败，交付时说明失败命令和原因。

## 避免事项

- 不要从 `@/utils/api-types` 或 `@/api/types` 引入 `AxiosPromise` / `PageResult`，这些入口在当前版本不存在。
- 不要引入 `useLoading`、`useFormDialog`、`useSearchReset`、`useTableSelection`、`useDateRangeQuery`、`useTreeTableExpand` 等 6.x hooks。
- 不要绕开 `request` 或 `proxy?.download` 自造请求/下载封装。
- 不要跳过 `types.ts`，把类型全写在页面里。
- 不要删除日期范围 `params`、权限指令、导出、导入、树筛选、列显隐等现有能力。
- 不要为了"更整洁"重写复杂页面的大块业务逻辑。
- 不要在新增标准页里使用与仓库不一致的 UI 壳或状态管理方式。
