# 门店管理（ops-business · store 域）· 详细设计与数据库设计

> 版本：v1.0（2026-10-09）
> 依据：《store-ops 架构设计方案》v1.3、《store-ops 产品需求文档 PRD》v1.1 5.0 节、管理台原型 `docs/prototype/admin-prototype.html`
> 状态：已评审定稿（第 8 节决策 1、2 已确认，决策 3 默认执行）
> 对应实施路线：P1 门店与组织

---

## 1. 需求回顾

- 门店不是框架内置实体，为本模块自研；租户/套餐/员工/角色/部门为框架能力，本模块不重复建设
- 定稿规则（PRD 5.0 节）：**先建部门再关联**，1 部门 ↔ 1 门店；门店表单部门下拉仅列本租户未绑定门店的部门
- 字段：门店名称（租户内唯一）、所属部门、地址（选填）、状态、备注；列表操作含小程序码查看
- 状态生命周期：仅启用 ↔ 停用，**本期不提供删除**；停用不清理数据，重新启用即恢复
- 菜单权限：仅租户管理员；店长指定走「员工挂部门 + 角色配置」，不在本模块

## 2. 全局约定（首个域定稿，后续域文档直接引用）

| 项 | 约定 | 说明 |
|---|---|---|
| 表命名 | 业务表统一 `biz_` 前缀（如 `biz_store`、`biz_order`、`biz_member`），避开 `sys_` | 框架表才进租户排除名单；`order` 等 MySQL 保留字问题随前缀自然解决（原 8.2 节决策已定稿） |
| 主键 | 雪花 ID（`IdType.ASSIGN_ID`） | 不用自增，与框架全局一致 |
| 租户隔离 | `tenant_id varchar(20)`，MyBatis-Plus 租户插件自动注入 | 业务代码不手写 tenant_id |
| 门店归属 | 业务表统一带 `store_id bigint` | 架构 4.2 节 |
| 数据权限 | 业务表**冗余 `dept_id bigint`**（= 门店绑定部门） | 店长「本部门」数据权限由框架插件按 dept_id 自动拼过滤，业务代码不写 if（架构 5 节）；冗余值在建档/开单时随 store_id 一起写入 |
| 审计字段 | `create_dept / create_by / create_time / update_by / update_time` | 与 `BaseEntity` 自动填充对齐 |
| 逻辑删除 | `del_flag char(1)` `@TableLogic` | 跟框架约定；本期门店无删除入口，字段预留 |
| 状态取值 | `status char(1)`：`0` 启用 / `1` 停用 | 与 sys_dept 保持一致 |
| 金额/积分 | `decimal(2)`，以「分」为最小单位 | 本模块无金额字段，全局沿用 |
| 接口分端 | controller 层分端：管理台 `controller/`（URL `/store/**`），小程序 `controller/mp/`（URL `/mp/store/**`，类名 `Mp` 前缀） | 网关白名单 `/mp/**` 按 URL 前缀一刀切放行；service/domain/mapper 两端完全共享不复制；mp controller 保持薄（参数转换 + 调共享 service + 归属校验，业务逻辑一律下沉）；管理台走 `@SaCheckPermission` 角色权限，mp 端为顾客身份（loginType=mp，无角色体系），按登录态校验 |

> 顾客端 `/mp/**` 接口不走框架数据权限，手动校验 store_id/tenant_id 归属（PRD 6.6 节），与本约定不冲突。
>
> 目录形态（每域照此复制）：
>
> ```
> org.dromara.business.store/
> ├── controller/
> │   ├── StoreController.java        # 管理台 → /store/**
> │   └── mp/
> │       └── MpStoreController.java  # 小程序 → /mp/store/**
> ├── service/  ├── mapper/  └── domain/   # 两端共享
> ```
>
> mp 端响应字段按需裁剪（不吐管理台 Vo 全量字段，必要时 mp 专属精简 Vo）。P1 动工实测项：`/mp/**` 白名单路径上租户插件仍生效（拦截在 mapper 层），顾客登录前的匿名接口需确认不查库或走安全查询路径。

## 3. 数据库设计

### 3.1 ER 关系

```
sys_dept (框架)                     biz_store（本模块）
─────────────        1 : 1         ──────────────
dept_id      ◄──────────────────── dept_id（唯一）
（租户总部）                          │ 1
（└ 门店部门）                        │ N
                                    biz_member / biz_order / biz_coupon...（后续域包，均带 store_id + dept_id）
```

- dept 承担「组织架构 + 数据权限」，store 承担「经营主体」，`store.dept_id` 关联二者
- 1:1 由唯一约束强制（见 3.3），不是代码约定

### 3.2 DDL

```sql
CREATE TABLE `biz_store` (
  `store_id`    BIGINT       NOT NULL                COMMENT '门店ID（雪花）',
  `tenant_id`   VARCHAR(20)  NOT NULL DEFAULT '000000' COMMENT '租户ID（插件注入）',
  `dept_id`     BIGINT       NOT NULL                COMMENT '绑定部门ID（sys_dept.dept_id，1:1）',
  `store_name`  VARCHAR(30)  NOT NULL                COMMENT '门店名称（租户内唯一）',
  `address`     VARCHAR(100) DEFAULT NULL            COMMENT '门店地址',
  `status`      CHAR(1)      NOT NULL DEFAULT '0'    COMMENT '状态（0启用 1停用）',
  `remark`      VARCHAR(200) DEFAULT NULL            COMMENT '备注',
  `del_flag`    CHAR(1)      NOT NULL DEFAULT '0'    COMMENT '删除标志（0存在 1删除）',
  `create_dept` BIGINT       DEFAULT NULL            COMMENT '创建部门',
  `create_by`   BIGINT       DEFAULT NULL            COMMENT '创建者',
  `create_time` DATETIME     DEFAULT NULL            COMMENT '创建时间',
  `update_by`   BIGINT       DEFAULT NULL            COMMENT '更新者',
  `update_time` DATETIME     DEFAULT NULL            COMMENT '更新时间',
  PRIMARY KEY (`store_id`),
  UNIQUE KEY `uk_tenant_dept` (`tenant_id`, `dept_id`),
  UNIQUE KEY `uk_tenant_name` (`tenant_id`, `store_name`),
  KEY `idx_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店表';
```

### 3.3 约束说明

| 约束 | 目的 |
|---|---|
| `uk_tenant_dept` | 数据库级强制「1 部门 1 门店」，并发新增绑定同一部门时兜底 |
| `uk_tenant_name` | 租户内门店名唯一；并发兜底 |
| `idx_status` | 列表按状态筛选；后续消费方按状态批量过滤 |

逻辑删除与唯一约束的冲突：本期无删除入口，不构成问题；将来开放删除时需将唯一键改造为含 `del_flag` 的组合键或改应用层校验，届时再议。

小程序码不落库：由 `store_id` 按需生成（scene 参数），无字段、无表。

### 3.4 实体映射

`Store extends TenantEntity`，`@TableName("biz_store")`，`@TableId(store_id)` + `@TableLogic(del_flag)`，与 `SysDept` 同款写法；Bo/Vo 按 `domain/bo/StoreBo`、`domain/vo/StoreVo` 分包。

## 4. 接口设计

### 4.1 模块与服务

- Maven 模块：`ruoyi-modules/ops-business`（业务聚合模块，2026-10-09 定案，域按包划分），store 域包名 `org.dromara.business.store`（跟随框架包结构，便于复用 common 能力）
- Nacos 服务名：`ops-business`，服务端口 `9206`（框架模块 9201~9205 顺延），配置 dataId `ops-business.yaml`（端口、数据源等，全部业务域共用一份）
- 网关路由：`/store/** → lb://ops-business`，URL 前缀仍按域划分，无需白名单（管理台接口，需登录）
- 开发期固定起 gateway + auth + system + ops-business 四个服务，后续里程碑不再新增服务进程

### 4.2 管理台 REST 接口（pc 端）

统一返回 `R<T>`，登录端 `pc`，权限注解 `@SaCheckPermission`，写操作带 `@Log` 操作日志与 `@RepeatSubmit` 防重。

> mp 端接口（`/mp/store/**`）目录与鉴权约定见第 2 节「接口分端」；接口清单在 P1 小程序 API 开发步骤按需补充。

| # | 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|---|
| 1 | GET | `/store/list` | `ops:store:list` | 分页列表，筛选：名称模糊、状态；租户插件自动隔离 |
| 2 | GET | `/store/{id}` | `ops:store:query` | 详情 |
| 3 | POST | `/store` | `ops:store:add` | 新增（校验链见 5.1 节） |
| 4 | PUT | `/store` | `ops:store:edit` | 修改：名称/地址/状态/备注；**dept_id 只读**（见 5.3 节） |
| 5 | PUT | `/store/changeStatus` | `ops:store:edit` | 启停切换，入参 `{storeId, status}` |
| 6 | GET | `/store/unbound-depts` | `ops:store:add` | 未绑定门店的部门下拉（新增弹窗数据源） |
| 7 | GET | `/store/optionselect` | `ops:store:list` | 门店精简下拉（订单/优惠券/充值页筛选共用，仅启用门店） |
| 8 | GET | `/store/{id}/qrcode` | `ops:store:query` | 小程序码，P1 返回 scene 值与占位图（见 5.4 节） |

无 DELETE 接口（本期不提供删除，PRD 5.0 节）。

**关键接口详述**

- `POST /store` 请求体（StoreBo）：

| 字段 | 类型 | 必填 | 校验 |
|---|---|---|---|
| storeName | string(≤20) | 是 | 租户内唯一 |
| deptId | long | 是 | 存在、属本租户、未被占用 |
| address | string(≤50) | 否 | |
| status | char | 是 | 0/1，默认 0 |
| remark | string(≤100) | 否 | |

- `GET /store/unbound-depts`：Dubbo 调 `RemoteDeptService.selectDeptsByList()` 取本租户部门 → 本地查 biz_store 表已占用 dept_id 集合 → 差集返回 `[{deptId, deptName}]`。仅返回租户总部下一层的「门店部门」形态由前端树形下拉自然表达，接口不做层级过滤。
- `GET /store/list` 返回 StoreVo 额外带 `deptName`（join 或查后补齐），供列表「绑定部门」列展示。

### 4.3 域间接口（模块内本地调用）

业务合并为单模块后，member/order 等域消费门店能力是**同模块本地方法调用**，无需 Dubbo、无需 Remote 接口与 ruoyi-api-ops 工程（若将来按域拆模块再引入）：

```java
// store 域对外门面：org.dromara.business.store.service.IStoreService
// 跨域消费只允许走 service 接口，禁止跨包注入 Mapper（守拆分演进纪律）
public interface IStoreService {
    /** 按 ID 查门店（含 status/tenantId），供 member/order 域校验门店归属与状态 */
    StoreVo queryStoreById(Long storeId);
    /** 按部门 ID 查门店（员工登录后定位所属门店） */
    StoreVo queryStoreByDeptId(Long deptId);
}
```

消费方（后续域包）：顾客建档校验门店启用、收银校验门店归属、员工工作台定位当前店——直接注入 `IStoreService`，无网络开销，跨域联动走本地事务。

跨模块调用仅剩 ruoyi-system（部门/用户）：Dubbo 走框架 `ruoyi-api-system` 的 `RemoteDeptService`，需补一个 `selectDeptById(Long deptId)`（现有接口无单查方法，框架侧小改）。

## 5. 业务规则落地

### 5.1 新增校验链（按序）

1. Bo 参数校验（名称/部门必填、长度）
2. Dubbo 查部门：存在且 `tenant_id` 匹配当前租户（防跨租户传 deptId 攻击）
3. 部门未被占用：`select count(*) from biz_store where dept_id = ?`（租户插件已隔离）
4. 名称唯一：`select count(*) from biz_store where store_name = ?`
5. 写入（tenant_id、审计字段由插件/填充器注入；**dept_id 同时冗余到后续业务表**的责任在消费方建档/开单时）
6. 并发兜底：捕获 `DuplicateKeyException`，按命中唯一键转为「部门已被绑定」/「门店名称已存在」友好提示

### 5.2 修改

- dept_id 不接收（编辑表单部门字段只读）；名称可改，重走唯一校验（排除自身）
- 启停也可在编辑表单改，与 `changeStatus` 接口等效

### 5.3 设计决策：本期禁止换绑部门（已定稿，PRD 5.0 节已同步修订）

PRD 5.0 节原允许「换绑时选未占用部门」，但全局约定业务表冗余 dept_id 后，**换绑将导致历史业务数据（biz_member/biz_order/biz_coupon…）的 dept_id 全量漂移**，需跨模块批量刷新，复杂度与收益不成比例。

本期收敛为：**门店一经创建，绑定部门不可更换**（用户已确认，2026-10-09）。建错部门的替代路径：停用该门店 → 新建部门与门店 → 员工迁移。换绑功能留待后续迭代（届时需设计历史数据 dept_id 批量刷新方案）。

### 5.4 小程序码

- scene 参数 = `store_id`（单值，满足微信 getUnlimitedQRCode 32 字符限制）
- P1 交付：`GET /store/{id}/qrcode` 返回 scene 值 + 占位二维码图，验证接口契约与前端「查看/下载」入口
- 真实小程序码生成依赖平台小程序主体认证与 access_token 管理，**随 P2 小程序端实装**（建议放 ruoyi-resource 或独立 weixin 通道模块，届时另行详设）；接口路径与入参在本期定死，后续不破坏契约

### 5.5 停用联动

停用**不写任何联动数据**，消费方实时校验（域内调 `IStoreService.queryStoreById` 读 status）：

| 消费方 | 行为 |
|---|---|
| 顾客端（/mp/**） | 扫码进入提示「门店暂停营业」，不可建档、不可兑券（member/point 模块落地） |
| 商家工作台 | 员工定位到的门店为停用时，收银/充值/核销入口置灰（order/member 模块落地） |
| 管理台 | 历史数据正常查询，小程序码入口置灰 |

启用即时生效，无延迟任务。

## 6. 菜单与权限初始化

`sql/ops_store.sql`（框架 sys_menu 脚本风格，`@parentId` 变量串联）：

| 层级 | 名称 | 权限标识 | 类型 |
|---|---|---|---|
| 目录 | 门店经营 | — | M（若已存在则复用） |
| 菜单 | 门店管理 | `ops:store:list` | C，路由 `store`，组件 `ops/store/index` |
| 按钮 | 门店查询 | `ops:store:query` | F |
| 按钮 | 门店新增 | `ops:store:add` | F |
| 按钮 | 门店修改 | `ops:store:edit` | F |

配套：租户套餐（sys_tenant_package）勾选「门店经营」目录，租户管理员角色默认授权；店长角色不授。

## 7. 页面字段映射（原型 → 接口 → 表）

| 原型元素（admin-prototype.html 门店管理页） | 接口 | 表字段 | 备注 |
|---|---|---|---|
| 列表：门店名称 | GET /store/list | store_name | |
| 列表：绑定部门 | GET /store/list（Vo.deptName） | dept_id → sys_dept.dept_name | |
| 列表：地址 | 同上 | address | |
| 列表：小程序码「查看」 | GET /store/{id}/qrcode | —（store_id 生成） | 停用行置灰 |
| 列表：状态 tag | 同上 | status | 0/1 |
| 列表：创建时间 | 同上 | create_time | |
| 操作：编辑 | PUT /store | store_name/address/status/remark | 部门字段只读（5.3 节） |
| 操作：停用/启用 | PUT /store/changeStatus | status | 弹窗四条影响说明已在原型 |
| 新建抽屉：所属部门下拉 | GET /store/unbound-depts | dept_id | 空列表时前端提示先去部门管理建部门 |
| 新建抽屉：名称/地址/状态/备注 | POST /store | 对应字段 | 校验 5.1 节 |

## 8. 决策记录

1. ~~禁止换绑部门~~ 已定稿（2026-10-09 用户确认）：本期禁止换绑，PRD 5.0 节已同步修订；后续迭代开放时需设计历史业务表 dept_id 批量刷新方案
2. ~~`order` 表名保留字~~ 已定稿（2026-10-09）：业务表统一 `biz_` 前缀（biz_store / biz_order / biz_member …），保留字问题随前缀自然解决，已回写全局约定 2 节与架构方案 4.2 节
3. 小程序码 P1 占位 / P2 实装的阶段划分（5.4 节），默认按此执行
