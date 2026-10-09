# 后端约定

## 优先参考的代码来源

- `ruoyi-modules/ruoyi-gen/src/main/resources/vm/java/*.vm`
- `ruoyi-example/ruoyi-demo/...`（标准 CRUD、树表、Excel 导入导出示例）
- `ruoyi-modules/ruoyi-system/...`
- `ruoyi-modules/ruoyi-workflow/...`
- `ruoyi-modules/ruoyi-job/...`
- `ruoyi-modules/ruoyi-resource/...`
- `ruoyi-api/...`
- `ruoyi-auth/...`
- `ruoyi-gateway/...`
- `ruoyi-visual/ruoyi-monitor/...`（监控服务，属于部署单元，不是 `ruoyi-modules`）
- `ruoyi-common/ruoyi-common-mybatis/...`

注意：demo 模块在 5.x 位于 `ruoyi-example/ruoyi-demo`，不在 `ruoyi-modules` 下。写标准 CRUD 时它是最贴近的完整范例（`TestDemo` 单表、`TestTree` 树表、`ExportExcelServiceImpl` 导入导出），优先读它而不是只读 gen 模板。

## 决策顺序

写代码时按下面顺序取样：

1. 当前业务模块下最近似实现。
2. 当前仓库公共能力模块中的统一约定。
3. generator 模板。
4. 通用 Spring / MyBatis-Plus 默认习惯。

如果规则冲突，优先相信当前仓库真实代码。

## 分层结构

标准 CRUD 代码应优先遵循下面这套结构：

- `domain/Entity.java`
- `domain/bo/EntityBo.java`
- `domain/vo/EntityVo.java`
- `mapper/EntityMapper.java`
- `service/IEntityService.java`
- `service/impl/EntityServiceImpl.java`
- `controller/EntityController.java`（复杂模块可再分子包）

## Entity 规则

- 业务实体需要租户隔离时继承 `org.dromara.common.tenant.core.TenantEntity`；不需要租户字段的实体继承 `org.dromara.common.mybatis.core.domain.BaseEntity`。两者都带审计字段和 `params`。
- 使用 Lombok `@Data` 和 `@EqualsAndHashCode(callSuper = true)`。
- 使用 `@TableName("table_name")`。
- 主键使用 `@TableId`。
- 存在 `delFlag` 时保留 `@TableLogic`，存在乐观锁字段时保留 `@Version`。
- `createBy` / `updateBy` / `createDept` / `createTime` / `updateTime` 由 `InjectionMetaObjectHandler` 自动填充，业务代码不要手动赋值。
- 如果附近实体已经使用 `@OrderBy` 等额外注解，应继续保持。

## BO 规则

- 继承 `org.dromara.common.mybatis.core.domain.BaseEntity`（`params`、创建/更新审计字段由父类提供），并加 `@EqualsAndHashCode(callSuper = true)`。
- 添加 `@AutoMapper(target = Entity.class, reverseConvertGenerate = false)`（来自 `io.github.linpeilie.annotations`）。
- 请求专用字段、查询专用字段放在 BO 中。
- 分组校验写法以模板为准：`@NotBlank(message = "xxx不能为空", groups = { AddGroup.class })`；新增与修改都必填的字段写 `groups = { AddGroup.class, EditGroup.class }`。
- `@Xss`、`@Email`、`@Size`、`@NotBlank`、`@NotNull` 要按真实业务语义添加，不要一股脑全套上。
- 查询存在日期范围时依赖 `BaseEntity.params`，不需要重复声明。

## VO 规则

- 实现 `Serializable`（或按同模块现状）。
- 添加 `@AutoMapper(target = Entity.class)`。
- 导出对象带 `@ExcelIgnoreUnannotated`，注解来自 `cn.idev.excel.annotation`（FastExcel，不是 com.alibaba.excel / EasyExcel 旧包名）。
- `@ExcelProperty`、`@ExcelDictFormat`、`ExcelDictConvert`（后两者来自 `org.dromara.common.excel`）只在导入导出场景下使用。
- 如果附近代码会把 ID 翻译成展示字段，沿用 `@Translation(type = TransConstant.XXX, mapper = "createBy")` 这类写法。
- 展示型派生字段放在 VO，不放在 Entity。

## Mapper 规则

- 默认形式是 `interface XxxMapper extends BaseMapperPlus<Xxx, XxxVo>`。
- 不要为简单的 entity 转 vo 手写重复代码，优先依赖 `BaseMapperPlus` 的 `selectVoById / selectVoList / selectVoPage / insertVo?` 系列方法。
- 模块已经使用 `@DataPermission` 时，在重写方法和自定义查询上继续保留。
- 只有在 `selectVoList/selectVoPage` 不够用时，才补 XML 或自定义 mapper 方法（如 `SysPostMapper.selectPagePostList`）。
- Mapper 默认方法可以承载短小的 wrapper 查询；涉及复杂业务编排、缓存、事务或跨 mapper 写入时放到 service。
- `ruoyi-system` 的用户、角色、菜单、部门等模块常带数据权限、角色状态过滤，修改前先读对应 mapper/service。
- 当前版本没有 MPJ（MyBatis-Plus-Join）依赖和 `MPJBaseMapper`；联表需求优先自定义 mapper 方法 + XML 或参考同模块现有做法，不要凭空引入新依赖。

### Mapper 建议结构

标准 mapper 一般按这个顺序组织：

1. 接口声明
2. 默认查询方法
3. 自定义分页或列表方法
4. 特殊数据权限重写
5. 辅助构造方法

### 什么时候需要 XML

- 复杂联表 SQL 无法仅靠 wrapper 清晰表达时。
- 需要手写查询列和结果映射时。
- 项目当前模块已经大量使用 XML 时。

如果 `BaseMapperPlus + wrapper` 已足够，优先不要补 XML。

## Service 规则

- 类声明通常是 `@RequiredArgsConstructor`、`@Service`，按需补 `@Slf4j`。
- 主业务 Mapper 注入字段统一命名为 `baseMapper`；模块内出现第二个 Mapper 时才使用业务短名（如 `deptMapper`、`userPostMapper`）。
- 两种方法命名风格并存，跟随所在模块：
  - generator 风格（新模块默认）：`queryById`、`queryPageList`、`queryList`、`insertByBo`、`updateByBo`、`deleteWithValidByIds`，写操作返回 `Boolean`。
  - system 手写风格：`selectPageXxxList`、`selectXxxById`、`insertXxx`、`updateXxx`、`deleteXxxByIds`，写操作返回 `int`。
- 读操作通常返回 `Vo`、`List<Vo>` 或 `TableDataInfo<Vo>`。
- BO 转实体用 `MapstructUtils.convert(bo, Entity.class)`。
- 查询条件集中在私有 `buildQueryWrapper(bo)` 方法内返回 `LambdaQueryWrapper`，使用 `Wrappers.lambdaQuery()` 或 `new LambdaQueryWrapper<>()`。
- 条件谓词直接写在链上：字符串用 `StringUtils.isNotBlank(...)`，对象用 `ObjectUtil.isNotNull(...)` / `!= null`，集合用 `CollUtil.isNotEmpty(...)`；当前版本没有 `eqIfPresent` / `likeIfText` 等链式扩展方法。
- 日期范围从 `bo.getParams()` 取 `beginTime`、`endTime`（或 `begin字段名` / `end字段名`），用 `.between(条件, Entity::getField, begin, end)`。
- 分页查询采用：
  `Page<Vo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);`
  `return TableDataInfo.build(result);`
- generator 风格模块保留 `validEntityBeforeSave(...)` 扩展点。
- 多表写操作使用 `@Transactional(rollbackFor = Exception.class)`。
- 明确的业务失败，尤其是权限、数据完整性、删除校验，使用 `ServiceException`（如 `throw new ServiceException("{}已分配，不能删除!", post.getPostName())`）。
- 不要绕过模块现有的数据权限、角色校验、删除前校验。

### Service 建议结构

标准 service impl 一般按下面顺序组织：

1. 查询单条
2. 分页查询
3. 列表查询
4. 构建查询条件（`buildQueryWrapper`，私有）
5. 新增
6. 修改
7. 保存前校验（`validEntityBeforeSave` 或 `checkXxxUnique`）
8. 删除前校验与删除
9. 其他扩展业务方法

### 写入逻辑建议

- BO 转实体统一走 `MapstructUtils.convert`。
- 批量关系维护时优先拆成私有方法，例如角色、岗位、用户关联。
- 修改前优先保留已有防误删、防越权、防并发覆盖逻辑。

## Controller 规则

- 继承 `BaseController`（`org.dromara.common.web.core`）。
- 类上通常带 `@Validated`、`@RestController`、`@RequiredArgsConstructor`、`@RequestMapping`。
- 类名惯例为 `XxxController`。
- `@RequestMapping("/businessName")` 只写业务段，模块前缀由网关路由承担；权限标识仍带模块前缀 `${module}:${business}:${action}`。
- 标准 CRUD 接口通常是：`GET /list`、`POST /export`、`GET /{id}`、`POST`、`PUT`、`DELETE /{ids}`。
- 分页 list 直接返回 `TableDataInfo<Vo>`（不包 `R`）；树表 list 返回 `R<List<Vo>>`。
- 导出接口返回 `void`，签名 `export(XxxBo bo, HttpServletResponse response)`，内部
  `ExcelUtil.exportExcel(list, "名称", XxxVo.class, response)`，路由 `POST /export`。
- 新增/修改使用 `toAjax(service.xxx(bo))`，唯一性校验失败在 controller 内 `return R.fail("...")` 并给出中文业务提示。
- 写操作、导入导出接口通常加 `@Log(title = "...", businessType = BusinessType.X)`。
- 附近接口已有防重时，写接口继续使用 `@RepeatSubmit`。
- 适合分组校验时，使用 `@Validated(AddGroup.class)` 和 `@Validated(EditGroup.class)`。
- 详情接口主键参数常带 `@NotNull(message = "主键不能为空")`，删除接口用 `@PathVariable Long[] ids`。
- 特殊接口直接复用模块内现成做法，例如 `optionselect`、`deptTree`、导入导出、`@ApiEncrypt`、multipart 上传、写入前唯一性校验。

### Controller 建议结构

标准 controller 一般按下面顺序组织：

1. 列表
2. 导出
3. 详情
4. 新增
5. 修改
6. 删除
7. 特殊接口（下拉选项、树列表等）

### Controller 边界

- controller 负责接参、校验、权限、日志、返回值转换。
- 重业务逻辑尽量放 service，不要在 controller 里堆长逻辑。
- 但前置权限检查、唯一性提示、显式业务失败提示可以留在 controller，前提是同模块已有这种习惯。

## 查询与工具规则

- 分页统一使用 `PageQuery` 和 `TableDataInfo`，不要无故引入新的分页 DTO，也不要使用 `PageResult`（该类不存在）。
- 优先使用项目工具类：`MapstructUtils`、`StringUtils`、`StreamUtils`、`ValidatorUtils`、`SpringUtils`、`RedisUtils`。
- 数组转列表按附近代码习惯使用 `List.of(ids)` 或 `Arrays.asList(ids)`。
- 日期范围查询从 `bo.getParams()` 中读取 `beginTime`、`endTime` 或 `begin字段名`、`end字段名`。
- 不要使用本仓库不存在的 `QueryBuilder`、`LambdaQueryBuilder`、`LambdaCrudChainWrapper` 等工具；查询构造只用 MyBatis-Plus 原生 `Wrappers.lambdaQuery()` / `new LambdaQueryWrapper<>()`。

## Cloud 服务规则

- 涉及 Dubbo、`ruoyi-api` 远程契约、Gateway、Nacos、Seata 或服务间调用时，同时读取 [cloud.md](cloud.md)。
- Cloud 服务间调用优先通过 `RemoteXxxService` + `@DubboReference` / `@DubboService`，不要跨模块直接注入其他服务的 mapper/service。
- 远程接口、远程 BO/VO/domain、mock/stub 放在 `ruoyi-api-*`；业务实现放在对应业务模块 `dubbo` 包。
- 跨服务写入检查 `@GlobalTransactional(rollbackFor = Exception.class)`；弱依赖远程调用检查是否需要 `mock = "true"` 或 `stub = "true"` 降级。

## common-mybatis 规则

- 公共能力以 `BaseMapperPlus<T, V>`、`PageQuery`、`TableDataInfo`、`BaseEntity` 为主。
- `BaseEntity` 实现 `Serializable`，字段带 `@TableField(fill = FieldFill.INSERT / INSERT_UPDATE)`；`params` 字段是 `@TableField(exist = false)` 的 HashMap，不落库。
- 审计字段由 `InjectionMetaObjectHandler` 自动填充（`createBy`、`updateBy`、`createDept`、`createTime`、`updateTime`）；无登录用户时 `createBy` 默认填 `-1L`。业务代码不要手动 set 这些字段。
- 数据权限注解使用 `@DataPermission({ @DataColumn(key = "deptName", value = "dept_id"), @DataColumn(key = "userName", value = "create_by") })`，注解在 `org.dromara.common.mybatis.annotation` 包。
- 数据权限的实际 SQL 由 `PlusDataPermissionHandler` 通过 SpEL 拼装，数据范围枚举在 `org.dromara.common.mybatis.enums.DataScopeType`（ALL / CUSTOM / DEPT / DEPT_AND_CHILD / OWN 等），自定义数据范围可实现 `SysDataScopeService` 扩展，不要绕过注解直接拼 dept 条件。
- 需要忽略数据权限的最小范围用 `DataPermissionHelper.ignore(...)` 包裹。
- Dubbo 调用链上的数据权限透传由 `common-mybatis` 里的 `DubboDataPermissionFilter` 承担，不要随意绕过。
- 新增派生查询方法时遵循 MP 原生命名，不引入第三方增强包。

## 多租户规则（本项目核心，必读）

本项目是租户级 SaaS，业务表必须同时隔离 `tenant_id` 与 `store_id`，编码时必须清楚租户字段从哪来。

- 多租户开关与排除表在 Nacos `application-common.yml` 的 `tenant:` 节点：`enable` 控制总开关，`excludes` 列出不做租户隔离的表（`sys_menu`、`sys_tenant`、`sys_role_dept` 等框架表）。新增业务表默认走租户隔离，不要随手加进 `excludes`。
- 租户 SQL 注入由 `PlusTenantLineHandler`（实现 MP `TenantLineHandler`）自动追加 `tenant_id` 条件，业务查询不需要手写 `tenant_id = ?`。
- 实体需要 `tenant_id` 字段时，优先继承 `org.dromara.common.tenant.core.TenantEntity`（已继承 `BaseEntity` 并带 `tenantId`），而不是自己 extends `BaseEntity` 再重复声明字段。
- 当前版本没有 `@TenantIgnore` 注解（不要凭记忆写）；跳过租户隔离统一走 `TenantHelper.ignore(...)`。
- 跨租户的系统级操作（平台超管开租户、写租户包配置等）用 `TenantHelper.ignore(...)` 包裹最小范围。
- 需要以指定租户身份执行时用 `TenantHelper.dynamic(tenantId, () -> {...})`，用完确保清理（`clearDynamic`），避免线程复用串租户。
- 当前登录租户 ID 通过 `TenantHelper.getTenantId()` 获取；不要从请求参数里直接取租户 ID 参与查询。
- 门店维度隔离（`store_id`）是业务层自己的责任，框架只管 `tenant_id`；按门店过滤要走本项目自己的数据权限或显式 `store_id` 条件。

## translation 规则

- 翻译实现类实现 `org.dromara.common.translation.core.TranslationInterface<T>` 并标注 `@TranslationType(type = ...)`，只实现 `T translation(Object key, String other)` 一个方法。
- 使用方在 VO 字段上通过 `@Translation(type = ..., mapper = "...", other = "...")` 指定翻译来源。
- 当前版本没有 `translationBatch` 批量接口，也没有 JSON 响应级翻译处理器（`common-json` 只有序列化配置）；新写翻译实现时注意在实现内部控制查询次数（如缓存、批量预查），避免列表页 N+1。
- 翻译失败时保持降级返回原值或 `null` 的现有语义，不要让响应增强中断主流程。

## 缓存与异步/监听规则

- 已有 service 使用 `@Cacheable`、`@CachePut`、`@CacheEvict`、`@Caching` 或 `CacheUtils.evict/clear` 时，新增写操作要同步考虑缓存失效。
- 部门、字典、OSS 配置等模块已有缓存初始化或失效逻辑，不要只改数据库不处理缓存；字典这类模块常同时维护 `CacheNames.SYS_DICT` 与 `CacheNames.SYS_DICT_TYPE`。
- Excel 导入监听器实现 `ExcelListener` 时，保留 `getExcelResult()` 的回执语义和错误聚合方式。
- 定时任务（SnailJob，`ruoyi-job`）、MQTT、SSE、异步回调等框架方法一般按接口覆写语义实现，除非业务不直观，不要添加冗长注释。

## 工作流模块规则

- `ruoyi-workflow`（WarmFlow 体系）带 `@ConditionalOnEnable` 条件注解，新增 workflow bean、controller、service 时检查同包是否需要该条件。
- 流程分类、任务、实例等查询常带分类权限或用户维度过滤，先读同类 mapper/service 再改。
- 工作流的翻译实现可以放在 workflow 模块内，例如流程分类 ID 到名称，仍应遵守 `TranslationInterface` 规则。

## JavaDoc 注释规则

- 公共 API、接口、VO/BO/Entity 字段、Mapper 默认方法、Service/Controller 方法应有简洁 JavaDoc。
- 注释描述"做什么"和关键参数语义，不复述显而易见的实现细节。
- `void` 方法不要写 `@return`；返回布尔值时说明 `true/false` 含义。
- 私有方法只有在业务规则、算法、映射关系不直观时补注释。
- 框架覆写方法如果只是标准回调，可不重复注释；但当前文件已有统一注释风格时保持一致。
- 只改注释时，不重排 import、不格式化全文件、不修改代码行为。

## 前后端联动规则

- 新增后端接口时，路径和权限前缀尽量保持 generator 约定，方便前端目录和 API 命名同步。
- 新增日期范围查询时，记得保留 `bo.params` 结构（`beginXxx` / `endXxx`），避免前端日期控件无法对接。
- 导出接口保持 `POST /export` + `void` + `HttpServletResponse` 风格，便于前端直接复用 `proxy?.download` 下载逻辑。
- 批量删除接口通常使用 `DELETE /{ids}`，便于前端直接传数组或逗号串。

## 生成器优先模式

从零新增 CRUD 时，优先对齐生成器默认方法集合：

- `queryById`
- `queryPageList`
- `queryList`
- `insertByBo`
- `updateByBo`
- `deleteWithValidByIds`

然后再叠加模块内已有增强，例如：

- 唯一性校验（`checkXxxUnique`）
- 数据权限注解
- 缓存注解
- Excel 导入导出监听器
- 关联表维护逻辑

## 什么时候优先看 generator

- 新增一个标准单表 CRUD 时。
- 只有表结构和基本接口需求，没有现成业务模块可参考时。
- 需要快速补齐整套骨架代码时。

## 什么时候优先看现有模块

- 目标模块已经有类似业务。
- 涉及数据权限、联表、缓存、角色岗位关系、导入导出、工作流扩展时。
- 任务是"修改已有模块"而不是"新建模块"时。

## 避免事项

- 不要在 controller 里直接暴露 entity 代替 BO/VO。
- 不要给新的管理接口漏掉权限注解。
- 没有明确必要时，不要从 `BaseMapperPlus` 风格退回手工映射。
- 前端查询页用了日期范围时，不要删掉后端 `params` 相关处理。
- 不要把 `ruoyi-system` 这类复杂逻辑强行简化成生成器式单表 CRUD。
- 不要使用 `PageResult`、`QueryBuilder`、链式 `IfPresent` 条件、`translationBatch`、`JsonFieldProcessor` 这些当前版本不存在的 API。

## 交付前自检

交付前至少检查这些点：

- CRUD 主链路是否完整。
- BO / VO / Entity 职责是否清晰。
- 分页、查询、删除校验是否与前端对得上（`TableDataInfo` 的 `rows` / `total`）。
- 权限、日志、防重、事务是否遗漏。
- 是否只是 generator 裸产物，如果是，需要继续补齐同模块已有增强。
