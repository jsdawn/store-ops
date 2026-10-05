# store-ops-api 项目上下文（WorkBuddy 引用文档）

> 用途：WorkBuddy 会话中快速引用的后端项目上下文。基线：RuoYi-Cloud-Plus（dromara）6.0.0。
> 维护约定：项目结构或关键配置变更时同步更新本文档。

## 1. 项目概览

- **定位**：store-ops 后端基座，基于 RuoYi-Cloud-Plus 6.0.0（微服务通用权限管理系统），在其上扩展 store-ops 业务模块（ops- 前缀）。
- **技术栈版本**（root pom.xml，revision=6.0.0）：
  - JDK **21**（注意：非 17；虚拟线程已开启 `spring.threads.virtual.enabled=true`）
  - Spring Boot **4.1.1** / Spring Cloud **2025.1.2** / Spring AI 2.0.1
  - Sa-Token 1.46（认证）+ MyBatis-Plus 3.5.17（含 mybatis-plus-join 1.5.9）+ MapStruct-Plus
  - Dubbo（服务间 RPC，Nacos 注册）+ Spring Cloud Bus（RabbitMQ，配置刷新广播）
  - Redisson 4.7.0 + lock4j（分布式锁）、SnailJob 2.0.2（任务调度）、Warm-Flow 1.8.9 + LiteFlow（工作流）
  - 中间件基线：MySQL 8.4.9 / Redis 8.6.3 / Nacos v3.2.3 / RabbitMQ / MinIO（见 script/docker/docker-compose.yml）
- **Maven 结构**：`revision` 占位版本号 + flatten-maven-plugin 统一管理；仓库镜像走华为云 `mirrors.huaweicloud.com`；默认 profile `dev`（Nacos 127.0.0.1:8848，账号 nacos/nacos）。

## 2. 目录结构

```
store-ops-api/
├── ruoyi-gateway/          # 网关（8080），唯一对外入口
├── ruoyi-auth/             # 认证中心（9210），token 签发/注销/租户列表
├── ruoyi-api/              # 跨服务契约层（Remote*Service + Remote BO/VO + mock/stub）
│   ├── ruoyi-api-system / ruoyi-api-resource / ruoyi-api-workflow / ruoyi-api-bom
├── ruoyi-modules/          # 业务模块
│   ├── ruoyi-system(9201) / ruoyi-resource(9204) / ruoyi-gen(9202)
│   ├── ruoyi-job(9203) / ruoyi-workflow(9205) / ruoyi-ai(9206)
├── ruoyi-common/           # 40+ 公共能力模块（BOM 见 ruoyi-common-bom）
│   ├── core / web / mybatis / satoken / security / redis / dubbo / seata
│   ├── translation / excel / oss / sms / mail / json / encrypt / sensitive
│   ├── log / logstash / prometheus / skylog / elasticsearch / mqtt / push …
├── ruoyi-visual/           # 独立部署组件：monitor(9100) / snailjob-server(8800) / snailai-server(8900)
├── ruoyi-example/          # demo(9401) / test-mq 示例
├── script/
│   ├── sql/                # 4 个库脚本：ry-cloud / ry-job / ry-ai / ry-workflow（+ oracle/postgres 变体）
│   ├── config/nacos/       # 各服务 Nacos 配置模板（application-common.yml + ruoyi-*.yml）
│   └── docker/             # docker-compose（mysql/nacos/redis/minio/rabbitmq/…）与各组件配置
└── .run/                   # IDEA 运行配置（每个服务一个 xml）
```

## 3. 核心模块职责

| 模块 | 职责 | 关键点 |
|---|---|---|
| ruoyi-gateway | 路由、鉴权入口 | AuthFilter 做 Sa-Token 登录校验 + 客户端校验；ForwardAuthFilter 透传 same-token；白名单在 Nacos `ruoyi-gateway.yml` 的 `security.ignore.whites` |
| ruoyi-auth | 登录/注销/注册 | 签发 Sa-Token JWT；多租户登录接口 `/auth/tenant/list` |
| ruoyi-system | 用户/角色/菜单/部门/租户/字典/配置 | 数据权限（@DataPermission）、MPJ 联表示例最全 |
| ruoyi-api-* | 跨服务契约 | 只放 Remote 接口与远程对象，禁止放业务实现；provider 实现放业务模块 `dubbo` 包 |
| ruoyi-common-mybatis | 数据访问底座 | BaseMapperPlus / QueryBuilder.lambda / lambdaJoin / eqIfText 等条件扩展 |
| ruoyi-common-satoken | 内网调用鉴权 | `check-same-token: true`：不允许绕过网关直访内网服务 |
| ruoyi-common-security | 权限上下文 | LoginHelper / 数据权限上下文透传（DubboDataPermissionFilter） |
| ruoyi-visual/* | 独立组件 | monitor=Spring Boot Admin；snailjob=调度中心；snailai=AI 服务 |

## 4. 配置与接口约定

### 4.1 配置加载链
每个服务的 `application.yml` 只含端口与应用名，实际配置全部在 Nacos：
`spring.config.import = optional:nacos:application-common.yml` + `optional:nacos:${应用名}.yml`。
本地初始化 Nacos 时需导入 `script/config/nacos/` 下全部 yml（application-common + 各服务 + datasource + seata-server.properties）。

### 4.2 数据源（script/config/nacos/datasource.yml）
- `system-master` / `gen` → 库 `ry-cloud`（主库）
- `job` → `ry-job`；`ai` → `ry-ai`；`workflow` → `ry-workflow`
- 主键策略：`idType: ASSIGN_ID`（雪花）；逻辑删除全局开启
- MySQL 连接统一 `lower_case_table_names=1`（compose 已配）、`rewriteBatchedStatements=true`

### 4.3 网关路由（Nacos ruoyi-gateway.yml）
| 前缀 | 目标服务 |
|---|---|
| /auth/** | ruoyi-auth |
| /system/** /monitor/** | ruoyi-system |
| /resource/** | ruoyi-resource |
| /tool/** | ruoyi-gen |
| /workflow/** /warm-flow/** | ruoyi-workflow |
| /demo/** /test-mq/** | example |

白名单（不登录可访问）：/auth/login、/auth/code、/auth/register、/auth/tenant/list、/resource/sms/code、/*/v3/api-docs、/csrf 等。
**store-ops 扩展点：`/mp/**`（小程序顾客端）后续加入此白名单，由各 ops 模块自行校验 member 身份。**

### 4.4 标准 CRUD 接口约定（generator 体系）
- 路由：`GET /list`、`GET /{id}`、`POST`、`PUT`、`DELETE /{ids}`、`POST /export`（新）/ `GET /export`（旧 demo）
- 权限标识：`${module}:${business}:${action}`，注解 `@SaCheckPermission`
- 返回：`R<T>` / `PageResult<Vo>`；分页入参 `PageQuery`（pageNum/pageSize + params 时间范围）
- 写接口惯例：`@Log(businessType=...)`、`@RepeatSubmit` 防重、`@Validated(AddGroup/EditGroup)` 分组校验

## 5. 关键技术实现

- **认证体系**：Sa-Token JWT（token-name=Authorization）+ 多客户端（pc 端 sys_user）。`is-concurrent=true` 允许并发登录；same-token 机制保证内网服务间调用不可绕过网关。store-ops 计划的 mp 端（顾客 member）复用 Sa-Token 多端能力。
- **服务间调用**：全部走 Dubbo（`RemoteXxxService` + `@DubboReference/@DubboService`），弱依赖配 `mock="true"`/`stub="true"` 降级；禁止跨模块直接注入对方 mapper/service。元数据中心走 Redis（common-dubbo.yml 内置）。
- **分布式事务**：Seata 默认关闭（`seata.enabled=false`），跨服务写用 `@GlobalTransactional`，单服务本地 `@Transactional(rollbackFor=Exception.class)`。
- **接口加解密**：`api-decrypt.enabled=true`，RSA 公私钥对在 application-common.yml（前端配套密钥注释同文件）；上传/表单上限 1GB。
- **XSS 防护**：全局开启，排除 /system/notice、/warm-flow/save-json。
- **多租户**：MyBatis-Plus 租户插件（TenantLineHandler），sys_user 体系；store-ops 业务表自带 tenant_id + store_id 并手动校验归属（架构方案定稿）。
- **监控**：actuator 全端点暴露 + Spring Boot Admin（monitor 模块，账号在 Nacos metadata：ruoyi/123456）+ Prometheus/Grafana 配置模板。

## 6. 开发注意事项

1. **JDK 必须 21**（虚拟线程 + SB4）；本机 springLearningProject 的 JDK 8 环境变量不要混入。
2. **启动顺序**：MySQL → Redis → Nacos（先导入 script/config/nacos 配置）→ 按需起 gateway/auth/system + 当前开发模块；别全量起（项目约定）。
3. **改配置优先改 Nacos**（本地 script/config/nacos 只是初始化模板）；`application-common.yml` 是全服务共享配置，改动影响所有服务。
4. **新增服务/模块**：保持 `application.yml` 仅端口+应用名、配置进 Nacos、启动类按需 `@EnableDubbo`、路由与白名单同步 Nacos 网关配置。
5. **新增 CRUD**：遵循 generator 模板分层（domain/bo/vo/mapper/service/controller），已适配 WorkBuddy skill `ruoyi-plus-ai-coding`（见 `.workbuddy/skills/`），编码前先读该 skill。
6. **安全红线**：不要把 NACOS_AUTH_TOKEN、jwt-secret-key、RSA 密钥等默认值带上生产；`.gitignore` 已拦截 application-local.yml / *.pem / .env。
7. **SQL 脚本**：`script/sql/*.sql` 按 4 库划分，导入顺序无依赖；store-ops 业务库脚本后续另建（对齐 db/migration 惯例）。
8. **前端配套**：管理台在 `store-ops-admin/`（plus-ui，Vue3+TS+Element Plus，pnpm），配套 skill `frontend-crud-coding`。

## 7. 相关文档

- 架构方案：`docs/store-ops-架构设计方案.md`（v1.3）
- 产品需求：`docs/store-ops-产品需求文档PRD.md`（v1.0）
- 上游文档：https://plus-doc.dromara.org （RuoYi-Cloud-Plus 官方）
- 仓库内 AI 编码规范原始文件：`store-ops-api/.codex/skills/`、`store-ops-api/.claude/agents/`、`store-ops-admin/.codex/skills/`（已适配为 WorkBuddy skill，内容以 `.workbuddy/skills/` 为准）
