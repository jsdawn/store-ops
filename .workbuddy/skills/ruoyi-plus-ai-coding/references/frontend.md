# 前端约定（后端仓库视角）

## 优先参考的代码来源

- `ruoyi-modules/ruoyi-gen/src/main/resources/vm/vue/*.vm`（标准单表页、树表页模板）
- `ruoyi-modules/ruoyi-gen/src/main/resources/vm/ts/*.vm`（api.ts、types.ts 模板）
- 前端工程（`store-ops-admin`，plus-ui 5.6.2）中与目标模块最接近的现有页面：
  - 标准单表：`src/views/demo/demo/index.vue`、`src/api/demo/demo/*`
  - 树表：`src/views/demo/tree/index.vue`
  - 系统页：`src/views/system/*`、`src/views/monitor/*`、`src/views/workflow/*`、`src/views/tool/*`

当前结构下，代码生成模板只存在于后端 `ruoyi-gen` 的 `vm/` 目录（Velocity 模板）；前端工程内没有 `gen/` 目录。生成器产出的前端代码与 `src/views/demo/*` 实际页面同源。

## API 文件规则

- 从 `axios` 引入 `AxiosPromise`（当前版本没有 `@/utils/api-types`）。
- 从 `@/utils/request` 默认引入 `request`。
- 本模块类型从 `@/api/<module>/<business>/types` 引入。
- 列表分页接口返回 `AxiosPromise<XxxVO[]>`（`TableDataInfo` 序列化后前端直接取 `res.rows` / `res.total`，不存在 `PageResult` 泛型）。
- 常规接口命名和路由保持：
  `listXxx` -> `GET /<module>/<business>/list`
  `getXxx` -> `GET /<module>/<business>/{id}`
  `addXxx` -> `POST /<module>/<business>`
  `updateXxx` -> `PUT /<module>/<business>`
  `delXxx` -> `DELETE /<module>/<business>/{id or ids}`

## 类型文件规则

- 定义 `VO`、`Form`、`Query`。
- `Form` 继承全局声明的 `BaseEntity`，`Query` 继承全局声明的 `PageQuery`（两者在 `src/types/global.d.ts` 全局声明，无需 import）。
- 树表页面的 `Query` 不继承 `PageQuery`。
- 各类 ID 字段通常用 `string | number`。
- Java 数值类型通常映射为 `number`。
- Boolean 映射为 `boolean`。
- 其他生成字段默认多为 `string`。
- 存在日期范围查询时保留 `params?: any`。

## Vue 页面规则

- 使用 `<script setup name="Xxx" lang="ts">`，`getCurrentInstance` 等 Vue API 由 unplugin 自动导入。
- 通过 `const { proxy } = getCurrentInstance() as ComponentInternalInstance;` 拿全局代理。
- 页面状态为普通 ref：`xxxList`、`loading`、`buttonLoading`、`showSearch`、`ids`、`single`、`multiple`、`total`。
- 查询和表单状态放在 `reactive<PageData<Form, Query>>({ form, queryParams, rules })`，再 `toRefs(data)`。
- 弹窗状态使用 `const dialog = reactive<DialogOption>({ visible: false, title: '' })`；项目里另有轻量 `src/hooks/useDialog.ts`（title + visible），已有页面用到时跟随，但 generator 产物默认用 `DialogOption`。
- 表单引用命名为 `queryFormRef` 和 `<business>FormRef`，类型 `ref<ElFormInstance>()`。
- 字典通过 `const { sys_normal_disable } = toRefs<any>(proxy?.useDict('sys_normal_disable'));` 获取。
- 当前版本没有 `useLoading`、`useSearchToggle`、`useSearchReset`、`useTableSelection`、`useFormDialog`、`useDateRangeQuery`、`useTreeTableExpand` 等 hooks，不要在 5.x 页面里引入这套写法。

## 页面行为规则

- `getList` 直接设置 `loading`、调用列表接口、回填 `xxxList.value = res.rows` 和 `total.value = res.total`。
- `handleQuery` 先把 `queryParams.value.pageNum = 1`，再重新查询。
- `resetQuery` 调用 `queryFormRef.value?.resetFields()` 后 `handleQuery()`；页面存在额外日期范围等状态时手动清空。
- `handleSelectionChange(selection: XxxVO[])` 手动维护 `ids`、`single`、`multiple`。
- `handleAdd` 先 `reset()`，再 `dialog.visible = true; dialog.title = '添加xxx'`。
- `handleUpdate` 先 `reset()`，按行或 `ids.value[0]` 查详情，`Object.assign(form.value, res.data)`，再打开弹窗。
- `submitForm` 校验表单、切换 `buttonLoading`、根据主键判断调用新增还是更新、`proxy?.$modal.msgSuccess`、关闭弹窗并刷新列表。
- `handleDelete` 使用 `await proxy?.$modal.confirm(...)` 确认，再调用删除接口并刷新。
- `handleExport` 使用 `proxy?.download('<module>/<business>/export', { ...queryParams.value }, 'xxx_${new Date().getTime()}.xlsx')`。
- 树表页面通过 `proxy?.handleTree<TreeVO>(res.data, 'id', 'parentId')` 组树，参考 `src/views/demo/tree/index.vue`。

## 模板结构规则

- 保持 plus-ui 5.x 标准页面结构：外层 `div.p-2`，搜索卡片用 `transition` + `proxy?.animate.searchAnimate.*` 包裹 `el-card shadow="hover"`，表格卡片同样 `el-card shadow="hover"` + header 工具栏 `el-row.mb8`，底部 `pagination`，弹窗 `el-dialog` + `dialog-footer`。
- 保留 `v-hasPermi="['module:business:add']"` 这类权限指令。
- 继续使用仓库已有组件：`right-toolbar`、`pagination`、`dict-tag`、`image-preview`、`image-upload`、`file-upload`、`editor`。
- 已有页面对时间列使用 `parseTime`（`proxy?.parseTime`）时，新页面保持一致。
- BETWEEN 日期查询继续使用 `el-date-picker` + 页面内 `dateRange` ref，查询时通过 `addDateRange(queryParams.value, dateRange.value)` 之类现有方式拼接。
- 不要引入 6.x 的 `search-panel` / `toolbar-shell` / `data-table` / `page-shell` SCSS 体系，当前前端不存在这些类名。

## 避免事项

- 生成器风格页面不要突然换成完全不同的状态管理方式，除非该前端目录本身已经这么做。
- 模块已使用字典时，不要把选项文案硬编码到页面里。
- 不要让 API 函数名和路由段偏离后端约定。
- 后端 BO/service 依赖 begin/end 参数时，不要从查询对象里删掉 `params` 和日期范围处理。
- 不要从 `@/utils/api-types` 或 `@/api/types` 引入 `AxiosPromise` / `PageResult`，这些入口在当前版本不存在。
