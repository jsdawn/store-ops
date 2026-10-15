# 会员体系（ops-business · member 域）· 详细设计与数据库设计

> 版本：v1.0（评审稿）　日期：2026-10-12
> 输入：《store-ops 架构设计方案》v1.3（4 节数据模型、8.3 顾客身份与鉴权）、《store-ops 产品需求文档 PRD》（5.1 会员体系、6.1–6.3 边界）、管理台原型 `docs/prototype/admin-prototype.html`（会员管理页、充值流水与冲正页）
> 上游依赖：门店管理详设 v1.0（已实现，store 域）；本文引用其第 2 节全局约定，不再重复
> 对应实施路线：P2 会员与收银（member 域先于 order 域落地，本详设不含收银开单，order 域另见后续文档）

## 1. 需求回顾

- 会员挂**门店级**（member.store_id），各店独立会员池；A 店会员在 B 店是陌生人（PRD 6.1）
- 会员按**门店 + 手机号**唯一标识：唯一约束 `(tenant_id, store_id, mobile)`，手机号建档必填；open_id 仅作微信登录凭证，不作唯一标识，可后补绑定（PRD 5.1.1）
- 会员资产三项：余额（balance）、积分（point_balance）、优惠券（券属独立域，本文仅定义积分账户与流水）
- 充值为记账式：商家线下收款，店员在小程序工作台录入（本金 + 赠送），系统入账并记流水；**不支持撤销**，错账走管理台当日冲正（PRD 5.1.2）
- 积分：订单完成按券后实付金额 1:1 等额转化，两位小数不取整；本期仅可兑券，不可抵现、不可转赠、已收款订单不回退（PRD 5.1.3）
- 顾客端（mp）：微信登录 → 确认手机号 → 按 (门店, 手机号) 匹配已有档案补绑 open_id 或新建；当前店按最近扫码/消费自动切换，可在已注册门店列表手动切换（PRD 5.1.1 / 架构 8.2）

## 2. 全局约定（沿用 store 域定稿，第 2 节补充 member 特有项）

| 项 | 约定 | 说明 |
|---|---|---|
| 通用约定 | 直接引用《ops-store-详细设计》第 2 节 | biz_ 前缀、雪花主键、tenant_id 插件注入、store_id + dept_id 冗余、审计字段、del_flag、decimal(2) 精度、controller 分端（`controller/` 管理台+员工端、`controller/mp/` 顾客端） |
| 金额/积分实现口径 | **以「元」为单位 `decimal(12,2)` 存储**，两位小数即「分为最小单位」的精度要求，展示层无需换算 | 与 PRD 6.4「以分存储、展示转元」字面表述的微小出入见 9 节决策记录 D1 |
| 包结构 | `org.dromara.business.member`：controller / controller/mp / domain / mapper / service | 与 store 域并列；跨域（order / coupon）只允许走 service 门面，禁止注入他域 Mapper |
| URL 前缀 | 员工端 `/member/**`（网关 `/business/member/**`，StripPrefix=1）；顾客端 `/mp/member/**`（网关不剥前缀） | 网关路由已就绪，无需新增 |
| 字典 | `member_pay_type`：`1` 微信收款 / `2` 支付宝收款 / `3` 现金收款；`balance_log_type`：`1` 充值 / `2` 消费 / `3` 冲正；`point_log_type`：`1` 消费获得 / `2` 兑换消耗 | 字典常量类 + sys_dict_data 初始化 |
| 手机号脱敏 | 列表展示统一 `138****6688` 格式，由前端脱敏，接口返回全量手机号 | 管理台/员工端为内部使用者，不脱敏传输 |

## 3. 数据库设计

### 3.1 ER 关系

```
biz_store（store 域，已建）
    │ 1
    │ N（各店独立会员池）
biz_member ──1:N──> biz_recharge_log（充值流水，含冲正状态）
    │ 1
    │ N（每笔余额变动一条）
biz_balance_log（余额流水：充值+/消费−/冲正−）
    │
    │ 1:N（每笔积分变动一条）
biz_point_log（积分流水：消费获得+/兑换消耗−）
```

- biz_member 即「门店会员账户」本体：档案 + 余额 + 积分 + 累计消费冗余，账户余额与流水分离（余额 + 流水模式，架构 4.2）
- order / coupon 域后续引用 member_id，不在本 ER 展开

### 3.2 DDL

```sql
-- ----------------------------
-- 会员档案（含余额/积分账户与累计消费冗余）
-- ----------------------------
CREATE TABLE `biz_member` (
  `member_id`     BIGINT        NOT NULL                  COMMENT '会员ID（雪花）',
  `tenant_id`     VARCHAR(20)   NOT NULL DEFAULT '000000' COMMENT '租户ID（插件注入）',
  `store_id`      BIGINT        NOT NULL                  COMMENT '门店ID（biz_store.store_id）',
  `dept_id`       BIGINT        NOT NULL                  COMMENT '冗余门店绑定部门（数据权限过滤）',
  `member_no`     VARCHAR(20)   NOT NULL                  COMMENT '会员卡号（门店内唯一）',
  `mobile`        VARCHAR(11)   NOT NULL                  COMMENT '手机号（建档必填，门店内唯一）',
  `open_id`       VARCHAR(64)   DEFAULT NULL              COMMENT '微信open_id（仅登录凭证，未绑定为NULL）',
  `nick_name`     VARCHAR(30)   DEFAULT NULL              COMMENT '昵称（微信昵称，线下登记可空）',
  `avatar`        VARCHAR(255)  DEFAULT NULL              COMMENT '微信头像URL',
  `balance`       DECIMAL(12,2) NOT NULL DEFAULT 0.00     COMMENT '余额（元）',
  `point_balance` DECIMAL(12,2) NOT NULL DEFAULT 0.00     COMMENT '积分（两位小数）',
  `total_amount`  DECIMAL(12,2) NOT NULL DEFAULT 0.00     COMMENT '累计消费（元，已完成订单应付合计）',
  `total_orders`  INT           NOT NULL DEFAULT 0        COMMENT '累计消费次数',
  `del_flag`      CHAR(1)       NOT NULL DEFAULT '0'     COMMENT '逻辑删除预留（本期无删除入口）',
  `create_dept`   BIGINT        DEFAULT NULL,
  `create_by`     BIGINT        DEFAULT NULL,
  `create_time`   DATETIME      DEFAULT NULL,
  `update_by`     BIGINT        DEFAULT NULL,
  `update_time`   DATETIME      DEFAULT NULL,
  `remark`        VARCHAR(255)  DEFAULT NULL              COMMENT '备注',
  PRIMARY KEY (`member_id`),
  UNIQUE KEY `uk_tenant_store_mobile` (`tenant_id`,`store_id`,`mobile`),
  UNIQUE KEY `uk_tenant_store_open` (`tenant_id`,`store_id`,`open_id`),
  UNIQUE KEY `uk_tenant_store_no` (`tenant_id`,`store_id`,`member_no`),
  KEY `idx_store` (`store_id`)
) ENGINE=InnoDB COMMENT='会员档案（门店级会员池）';
```

> `uk_tenant_store_open`：MySQL 唯一索引对 NULL 不生效，天然允许「未绑定」多条；一旦绑定即保证「一个微信在一个门店只有一个档案」，是 open_id 补绑/合并不产生重复档案的**数据库兜底**（PRD 5.1.1）。

```sql
-- ----------------------------
-- 充值流水（冲正状态挂在本表，冲正动作另记余额流水）
-- ----------------------------
CREATE TABLE `biz_recharge_log` (
  `recharge_id`   BIGINT        NOT NULL                  COMMENT '充值流水ID（雪花）',
  `recharge_no`   VARCHAR(32)   NOT NULL                  COMMENT '充值流水号（RC + yyMMdd + 雪花后6位，全局唯一）',
  `tenant_id`     VARCHAR(20)   NOT NULL DEFAULT '000000' COMMENT '租户ID（插件注入）',
  `store_id`      BIGINT        NOT NULL                  COMMENT '门店ID',
  `dept_id`       BIGINT        NOT NULL                  COMMENT '冗余门店绑定部门',
  `member_id`     BIGINT        NOT NULL                  COMMENT '会员ID',
  `amount`        DECIMAL(12,2) NOT NULL                  COMMENT '充值金额（本金）',
  `gift_amount`   DECIMAL(12,2) NOT NULL DEFAULT 0.00     COMMENT '赠送金额',
  `pay_type`      CHAR(1)       NOT NULL                  COMMENT '充值方式（1微信 2支付宝 3现金，仅记录不触发支付）',
  `operator_id`   BIGINT        NOT NULL                  COMMENT '操作店员（sys_user.user_id）',
  `operator_name` VARCHAR(30)   NOT NULL                  COMMENT '操作店员姓名（冗余，防员工离职后不可读）',
  `status`        CHAR(1)       NOT NULL DEFAULT '0'      COMMENT '状态（0有效 1已冲正）',
  `create_dept`   BIGINT        DEFAULT NULL,
  `create_by`     BIGINT        DEFAULT NULL,
  `create_time`   DATETIME      DEFAULT NULL,
  `update_by`     BIGINT        DEFAULT NULL,
  `update_time`   DATETIME      DEFAULT NULL,
  `remark`        VARCHAR(255)  DEFAULT NULL               COMMENT '备注（如「周年庆活动」）',
  PRIMARY KEY (`recharge_id`),
  UNIQUE KEY `uk_recharge_no` (`recharge_no`),
  KEY `idx_member` (`member_id`),
  KEY `idx_store_time` (`store_id`,`create_time`)
) ENGINE=InnoDB COMMENT='充值流水';

-- ----------------------------
-- 余额流水（每笔变动一条，含变动后快照）
-- ----------------------------
CREATE TABLE `biz_balance_log` (
  `log_id`        BIGINT        NOT NULL                  COMMENT '流水ID（雪花）',
  `tenant_id`     VARCHAR(20)   NOT NULL DEFAULT '000000' COMMENT '租户ID（插件注入）',
  `store_id`      BIGINT        NOT NULL                  COMMENT '门店ID',
  `dept_id`       BIGINT        NOT NULL                  COMMENT '冗余门店绑定部门',
  `member_id`     BIGINT        NOT NULL                  COMMENT '会员ID',
  `change`        DECIMAL(12,2) NOT NULL                  COMMENT '变动金额（正入账/负扣减）',
  `after_balance` DECIMAL(12,2) NOT NULL                  COMMENT '变动后余额快照（对账用）',
  `biz_type`      CHAR(1)       NOT NULL                  COMMENT '业务类型（1充值 2消费 3冲正）',
  `biz_no`        VARCHAR(32)   NOT NULL                  COMMENT '关联业务单号（充值流水号/订单号）',
  `create_dept`   BIGINT        DEFAULT NULL,
  `create_by`     BIGINT        DEFAULT NULL,
  `create_time`   DATETIME      DEFAULT NULL,
  `update_by`     BIGINT        DEFAULT NULL,
  `update_time`   DATETIME      DEFAULT NULL,
  `remark`        VARCHAR(255)  DEFAULT NULL              COMMENT '备注（冲正原因记此处）',
  PRIMARY KEY (`log_id`),
  KEY `idx_member_time` (`member_id`,`create_time`),
  KEY `idx_store_time` (`store_id`,`create_time`)
) ENGINE=InnoDB COMMENT='会员余额流水';

-- ----------------------------
-- 积分流水（余额+流水模式，架构 4.2）
-- ----------------------------
CREATE TABLE `biz_point_log` (
  `log_id`        BIGINT        NOT NULL                  COMMENT '流水ID（雪花）',
  `tenant_id`     VARCHAR(20)   NOT NULL DEFAULT '000000' COMMENT '租户ID（插件注入）',
  `store_id`      BIGINT        NOT NULL                  COMMENT '门店ID',
  `dept_id`       BIGINT        NOT NULL                  COMMENT '冗余门店绑定部门',
  `member_id`     BIGINT        NOT NULL                  COMMENT '会员ID',
  `change`        DECIMAL(12,2) NOT NULL                  COMMENT '变动积分（正获得/负消耗）',
  `after_point`   DECIMAL(12,2) NOT NULL                  COMMENT '变动后积分快照',
  `biz_type`      CHAR(1)       NOT NULL                  COMMENT '业务类型（1消费获得 2兑换消耗）',
  `biz_no`        VARCHAR(32)   NOT NULL                  COMMENT '关联业务单号（订单号/券实例ID）',
  `create_dept`   BIGINT        DEFAULT NULL,
  `create_by`     BIGINT        DEFAULT NULL,
  `create_time`   DATETIME      DEFAULT NULL,
  `update_by`     BIGINT        DEFAULT NULL,
  `update_time`   DATETIME      DEFAULT NULL,
  `remark`        VARCHAR(255)  DEFAULT NULL,
  PRIMARY KEY (`log_id`),
  KEY `idx_member_time` (`member_id`,`create_time`),
  KEY `idx_store_time` (`store_id`,`create_time`)
) ENGINE=InnoDB COMMENT='会员积分流水';
```

### 3.3 约束说明

| 约束 | 内容 | 来源 |
|---|---|---|
| uk_tenant_store_mobile | 同店同手机号仅一档案；跨店各自独立 | PRD 5.1.1 / 6.1 |
| uk_tenant_store_open | 同店同微信仅一档案（NULL 不受约束，未绑定可多条） | open_id 补绑不重复的库级兜底 |
| uk_recharge_no | 充值流水号全局唯一 | 幂等与对账 |
| 无余额/积分负数约束 | 由条件 UPDATE 原子保证（见 5.4），非 CHECK 约束 | MySQL CHECK 兼容性考虑 |

### 3.4 实体映射

`BizMember / BizRechargeLog / BizBalanceLog / BizPointLog` extends `TenantEntity`（tenant_id 自动注入），`@TableLogic` 挂 del_flag（仅 member 有）。Vo 出参 `BizMemberVo` 附 `storeName`（同模块调用 store 域门面补齐）与 `wxBound`（open_id 非空）；mp 端精简 Vo `MpMemberVo`（卡号/余额/积分/昵称/门店，不吐 dept_id、total 明细等内部字段）。

## 4. 接口设计

### 4.1 模块与服务

- 落点：`ruoyi-modules/ops-business` 新增 `org.dromara.business.member` 包，服务与配置沿用 ops-business（9206），无新服务
- 改动点（跨模块，同 store 域改 RemoteDeptService 先例）：`ruoyi-auth` 新增微信登录接口（架构 8.3，详见 4.5）

### 4.2 管理台 REST（controller/，管理台专用，URL /member/**）

本节接口**仅供管理台（PC）调用**；小程序员工端走独立的 `/mp/staff/**` 接口（见 4.6 定稿方案），与下表接口**共用 service 层**实现，与 store 域同构。

| # | 方法 | 路径 | 权限标识 | 说明 |
|---|---|---|---|---|
| 1 | GET | `/member/list` | `ops:member:list` | 分页查询；筛选 storeId / mobile / memberNo / nickName（模糊）；数据权限自动过滤（店长本店、租户管理员全部）；列表带 storeName、wxBound |
| 2 | GET | `/member/{memberId}` | `ops:member:query` | 详情（档案 + 余额 + 积分 + 累计消费） |
| 3 | POST | `/member` | `ops:member:add` | 线下登记建档：mobile 必填本店唯一，open_id 留空；重复时返回已存在档案 memberId 供前端跳转（PRD 6.1） |
| 4 | GET | `/member/search` | `ops:member:list` | 收银/充值前选会员：keyword（手机号/卡号/昵称模糊）+ storeId，仅返回本店前 20 条精简项（昵称、手机号、卡号、余额、积分） |
| 5 | POST | `/member/recharge` | `ops:member:recharge` | 充值入账：校验见 5.2；幂等 token 防重复提交 |
| 6 | GET | `/member/recharge/list` | `ops:recharge:list` | 充值流水分页；筛选 storeId / mobile / payType / 时间区间；数据权限过滤 |
| 7 | POST | `/member/recharge/{rechargeId}/reversal` | `ops:recharge:reversal` | 冲正：仅当日、未冲正、余额充足；reason 必填（见 5.3） |

管理台**不提供**编辑会员档案接口（手机号/卡号不可改，改名仅微信侧自动带出；见 8 节待确认 T3）。

### 4.3 顾客端 REST（controller/mp/，URL /mp/member/**）

网关白名单放行框架登录校验，走 Sa-Token `mp` 端自定义鉴权；**所有接口手动校验** member 归属（tenant_id + store_id 显式比对，禁止依赖框架数据权限，PRD 6.6）。

| # | 方法 | 路径 | 说明 |
|---|---|---|---|
| 1 | POST | `/mp/member/bind-mobile` | 确认手机号：按 (当前店, 手机号) 匹配 → 已有档案补绑/覆盖 open_id；无档案新建（见 5.1） |
| 2 | GET | `/mp/member/me` | 当前店资产：卡号、余额、积分、优惠券张数（券域 P3 前返回 0） |
| 3 | GET | `/mp/member/stores` | 我的门店列表：仅已注册门店，按最近消费时间倒序 |
| 4 | GET | `/mp/member/balance-log` | 余额流水（本人，当前店，分页） |
| 5 | GET | `/mp/member/point-log` | 积分流水（本人，当前店，分页） |

「当前店」切换为纯前端状态（本地存储 + 按 stores 列表选择），服务端不落当前店字段；`me / balance-log / point-log` 均以 header 或参数显式携带 storeId 并校验该会员在此店的归属。

### 4.4 域间接口（模块内本地调用门面）

order / coupon 域消费 member 能力一律走 `IBizMemberService`，禁止注入 Mapper：

```java
public interface IBizMemberService {
    /** 按 ID 查会员（含余额/积分/状态），供收银与兑券前校验归属 */
    BizMemberVo queryById(Long memberId);
    /** 原子扣减余额（余额不足返回 false），同事务内由 order 域调用并记余额消费流水 */
    boolean deductBalance(Long memberId, BigDecimal amount, String orderNo);
    /** 原子加积分（订单完成 1:1 落账）+ 累计消费冗余更新 */
    void settleOrder(Long memberId, BigDecimal payAmount, String orderNo);
    /** 原子扣积分（兑券，coupon 域调用） */
    boolean deductPoints(Long memberId, BigDecimal points, Long couponId);
}
```

跨域联动（开单收款 → 扣余额/加积分/累计消费）在 ops-business 单模块内走**本地事务**（架构 9 节 P2），无 Seata。

### 4.5 顾客登录（ruoyi-auth 改动点）

- 新增 `POST /auth/mp/login`：入参 `{ code, storeId }`，`code2session` 换 open_id → 按 (tenant, storeId, open_id) 查档案：命中直接颁发 mp 端 token；未命中返回「需确认手机号」标记，前端引导调 `/mp/member/bind-mobile` 完成建档/补绑后重颁 token
- Sa-Token `loginType = mp`，与 pc 端会话完全隔离；mp 端 token 不进 sys_client 体系，网关 `/mp/**` 走白名单 + 自定义拦截器校验
- 小程序 appid/secret 存 Nacos（`ops-business.yml` 或 auth 配置），**开发期无真实小程序时提供 mock 开关**（固定 open_id 注入，见 T5）

### 4.6 MP 员工端（小程序工作台）· 定稿方案（2026-10-15，决策 D9–D11）

**身份识别：自动匹配，无手动绑定流程（D9）**

- 前提：**门店与部门一一对应**（store 域定稿）；管理用户（sys_user）经 `dept_id` 归属门店
- 判定时机：`/auth/mp/login` 颁发 token、以及切店重签 token 时，以会员 `mobile` 匹配「当前门店对应部门下、状态正常」的管理用户手机号（`sys_user.phonenumber`）
- 命中 → 该会员即本店员工，token payload 附 `employeeId`（= sys_user_id），会员中心出现「员工工作台」入口；未命中 → 纯顾客 token（无 employeeId）
- 匹配到多条同手机号管理用户时取 `user_id` 最小的一条；部门内手机号唯一性作为管理台录入约定
- 误绑知情与兜底：顾客手机号与员工手机号一致即现入口（概率低），管理员停用对应管理用户或修正手机号即可即时摘除

**接口与鉴权：不接管理台 token，员工端接口独立（D10）**

- 员工端接口落 `controller/mp/`，Controller 类名加 `MpStaff` 前缀（如 `MpStaffMemberController`），URL 仍为 `/mp/staff/**`；不与 4.2 管理台接口共用 URL，仅**共用 service 层**
- 鉴权沿用 mp token（header `Mp-Token`），payload 扩展为 `{memberId, employeeId?}`；**不建** sa-token pc 会话、不接 `Authorization`、不进 sys_client 体系
- 权限不做细分：token 含 `employeeId` 即可调用全部 `/mp/staff/**` 接口；管理台 RBAC 权限标识（4.2 表 `ops:member:*` 等）**仅作用于管理台接口**
- 拦截器扩展：mp 拦截器解析出 `employeeId` 后注入 request attribute（`OpsMpConstants` 新增 `MP_EMPLOYEE_ID_ATTR`），并校验 employeeId 对应管理用户的部门与当前请求门店一致（防切店残留身份）

**操作日志：记管理用户 + 来源区分（D11）**

- mp 员工端操作的记录口径与管理台一致：操作人 = `employeeId`（会员对应的管理用户），**不记 memberId**
- 实现：mp 员工拦截器将 `employeeId` 构造 `LoginUser` 注入 `LoginHelper` 上下文，`@Log` 记录机制直接复用 PC 端
- 日志增加**来源标记**（PC / MP 员工端）：mp 员工请求由拦截器透传来源，随操作日志落库，审计时区分渠道
- 边界：会员本人操作（`/mp/member/**`）仍记 memberId，员工上下文注入只发生在 `/mp/staff/**` 拦截器内，两个链路互不影响

## 5. 业务规则落地

### 5.1 建档与 open_id 绑定（防重复档案，PRD 5.1.1）

```
顾客微信授权登录（open_id）
  └─ 首次使用会员能力（查余额/兑券）→ 确认手机号
       ├─ (当前店, 手机号) 已有档案
       │    ├─ 该档案 open_id 为空      → 补绑 open_id，登录 ✓
       │    └─ 该档案 open_id 是别人    → 覆盖更新 open_id，登录 ✓（「补绑后自动更新」）
       └─ 无档案                       → 新建（open_id + mobile + 微信昵称/头像），登录 ✓
```

- 覆盖更新走 `uk_tenant_store_open` 唯一索引：若新 open_id 已被同店另一档案占用（换微信场景）→ DuplicateKeyException 兜底，提示走原手机号档案（边界 E5，待确认 T2）
- 线下登记（员工端）：仅 mobile + 可选昵称，open_id NULL；顾客日后微信登录按上表自动合并
- 建档前校验门店 `status = 0`（停用门店不可新增会员/充值，见 5.6）
- 卡号生成：`member_no = 门店序号(4位) + 店内序列(4位)`；店内序列用 Redis `INCR ops:member:no:{storeId}`，撞号（重置/漂移）时唯一索引兜底 + 重试一次；门店序号取 biz_store 在租户内的创建顺序（见 T4）

### 5.2 充值（员工端，小程序工作台录入）

校验链（按序）：

1. 会员存在且属当前店（员工数据权限 + 显式 storeId 比对）
2. 门店启用中（5.6）
3. `amount > 0` 且 ≤ 99999.99，两位小数；`0 ≤ gift_amount ≤ amount`（PRD 5.1.2）
4. 幂等 token 一次有效（Redis，复用订单机制）

入账（同一本地事务）：

```
biz_member.balance += (amount + gift_amount)
biz_recharge_log 插入（status=0, pay_type, operator）
biz_balance_log 插入（change = +（amount+gift）, biz_type=充值, biz_no=recharge_no, after_balance 快照）
```

充值即时生效不支持撤销；本金与赠送合并入同一账户，消费不区分扣减顺序（PRD 5.1.2）。充值方式仅记录，不触发任何支付动作。

### 5.3 冲正（仅管理台，PRD 5.1.2 / 6.2）

| 规则 | 实现 |
|---|---|
| 仅管理台入口 | 小程序工作台无冲正功能，权限标识 `ops:recharge:reversal` 按 RuoYi 菜单权限授予，不限店长、无需审批 |
| 仅限当日 | `recharge_log.create_time` ≥ 当日 00:00:00（自然日，按服务器时区） |
| 仅一次 | `status = 0` 才可冲正；已冲正再冲返回业务异常 |
| 余额充足 | 冲正金额 = 该笔到账合计（amount + gift），条件 UPDATE 保证冲后余额 ≥ 0；不足则失败提示 |
| 动作（同一事务） | `balance −= 到账合计`；`recharge_log.status → 1`；`balance_log` 插入负向流水（biz_type=冲正, biz_no=recharge_no, remark=冲正原因必填 ≤ 100 字） |

### 5.4 余额/积分并发（PRD 6.3）

- 余额扣减（余额收款、冲正）用**条件 UPDATE 原子语句**，不用乐观锁 version 列：

```sql
UPDATE biz_member SET balance = balance - #{amount}
 WHERE member_id = #{memberId} AND balance >= #{amount}
```

影响行数 = 0 即余额不足，调用方回滚整单（「扣余额失败则整单失败」，PRD 5.3.1）。积分扣减同构。
- 充值入账为 `balance = balance + (amount + gift)` 无条件加，不校验上限以外约束
- 会员主表单行更新天然串行（InnoDB 行锁），不出现超扣/负数

### 5.5 积分（PRD 5.1.3）

- 获得：订单完成（确认收款）时按券后实付 1:1 等额落账，`settleOrder` 同事务内：`point_balance += payAmount` + `biz_point_log`（biz_type=消费获得, biz_no=order_no）+ `total_amount/total_orders` 冗余更新
- 消耗：兑券 `deductPoints`（coupon 域 P3）原子扣减，流水 biz_no=券实例 ID
- 无回退：已收款订单不作废不退款（PRD 6.5），本期不存在积分扣回；规则为系统固定默认，无配置表无配置页

### 5.6 门店停用联动（沿用 store 域 5.5 的联动语义）

- 停用门店：**不可新增会员、不可充值、不可开单**（建档/充值校验 store.status=0）；已有会员、流水全部保留，重新启用后原样恢复
- 会员查询/流水查询不受门店停用影响（历史可查）

## 6. 菜单与权限初始化（SQL 随代码交付 `script/sql/ops_member.sql`）

沿用 store 域 menu_id 分段规范（store 用 3000~3004，业务菜单固定 3000 段避开框架 0~1623）：

| 层级 | menu_id | 名称 | 权限标识 | 类型 |
|---|---|---|---|---|
| 1（3000 下） | 3010 | 会员管理 | — | 菜单（页面） |
| 2 | 3011 | 会员查询 | `ops:member:list` | 按钮 |
| 2 | 3012 | 会员详情 | `ops:member:query` | 按钮 |
| 2 | 3013 | 线下登记会员 | `ops:member:add` | 按钮 |
| 2 | 3014 | 充值入账 | `ops:member:recharge` | 按钮 |
| 1（3000 下） | 3020 | 充值流水 | — | 菜单（页面） |
| 2 | 3021 | 充值流水查询 | `ops:recharge:list` | 按钮 |
| 2 | 3022 | 充值冲正 | `ops:recharge:reversal` | 按钮 |

配套 sys_dict_data：`member_pay_type`（3 项）、`balance_log_type`（3 项）、`point_log_type`（2 项）。租户套餐勾选「门店经营」目录即随目录生效，店长角色按需勾按钮（门店管理菜单本身仅租户管理员，store 域同款约束）。

## 7. 页面字段映射（原型 → 接口 → 表）

**会员管理页**（原型 pg-member，轻量字段验证页）：

| 原型列 | 接口字段（BizMemberVo） | 表列 |
|---|---|---|
| 会员（昵称 + 138****6688） | nickName + mobile（前端脱敏） | nick_name, mobile |
| 门店 | storeName（store 域门面补齐） | store_id |
| 卡号 | memberNo | member_no |
| 余额 | balance | balance |
| 积分 | pointBalance | point_balance |
| 微信绑定 | wxBound（open_id 非空 → 已绑定标签） | open_id |
| 累计消费（¥1,286.00 / 12 次） | totalAmount + totalOrders | total_amount, total_orders |
| 开卡时间 | createTime | create_time |

筛选：门店下拉（数据权限范围内门店）/ 手机号/卡号/昵称模糊。底部规则提示与原型 notice 一致。

**充值流水页**（原型 pg-recharge）：

| 原型列 | 接口字段 | 表列/来源 |
|---|---|---|
| 流水号 / 时间 | rechargeNo + createTime | recharge_no, create_time |
| 门店 | storeName | store_id → 补齐 |
| 会员 | nickName + mobile（脱敏） | join biz_member |
| 充值金额 / 赠送金额 / 到账合计 | amount / giftAmount / 两者和（计算列） | amount, gift_amount |
| 充值方式 | payType（字典翻译） | pay_type |
| 操作店员 | operatorName | operator_name |
| 状态 | status（0 有效 / 1 已冲正） | status |
| 操作：冲正 | 仅 status=0 且当日显示；已冲正显示原因（负向流水 remark） | — |

冲正弹窗按原型：流水信息、冲正动作（余额 −到账合计）、限制说明、冲正原因必填。

## 8. 边界情况与待确认事项

### 8.1 边界情况（已定，PRD 6.1–6.3 落地对照）

| # | 场景 | 处理 |
|---|---|---|
| E1 | A 店会员到 B 店消费/查资产 | 查不到、不可用；须在 B 店重新注册（uk 含 store_id 天然隔离） |
| E2 | 同手机号在多店注册 | 允许，各店独立记录 |
| E3 | 店员录入手机号重复 | 返回已存在档案 + 前端跳转，不重复建档 |
| E4 | 顾客微信手机号与线下档案不一致 | 按填写手机号匹配/新建，可形成双档案，本期不合并（非本期范围） |
| E5 | 同店一个 open_id 对应档案的 mobile 被另一档案占用（换手机号确认） | uk 冲突拦截，提示联系店家；库层兜底不产生脏数据 |
| E6 | 充值确认时会员正在消费 | 行级原子更新串行生效，不超扣 |
| E7 | 冲正时余额已被消费不足 | 冲正失败并提示余额（原型已标注） |
| E8 | 录入赠送 > 本金 | 前端拦截 + 后端校验拒绝 |
| E9 | 门店停用后顾客扫码/员工建档充值 | 建档与充值校验门店启用，全部拦截；存量资产保留 |

### 8.2 待确认事项（开发前需拍板）

| # | 事项 | 建议 | 影响 |
|---|---|---|---|
| T1 | 会员卡号格式 | `门店序号(4位) + 店内序列(4位)`，如 `8800 0021`（对齐原型示意）；Redis INR 序列 + 唯一索引兜底 | 若要「全租户全局唯一」需改含租户段；PRD 仅要求门店内唯一 |
| T2 | open_id 覆盖绑定的碰撞策略（E5） | 本期拦截报错，人工走线下处理 | 若要「自动合并档案」需引入合并流程，非本期 |
| T3 | 会员档案编辑 | 本期不提供编辑接口（手机号=身份不可改；昵称头像微信侧自动带出）；remark 可后续放开 | 档案录错只能停用？——本期连停用都没有，**录错手机号只能弃用重建**，确认接受 |
| T4 | 门店序号取值来源 | biz_store 创建顺序（租户内第 N 家店）；或直接用 store_id 后 4 位（简单但可能撞重） | 卡号展示友好度 |
| T5 | mp 登录开发期 mock | 无真实 appid 时 `ops-business.yml` 配 `mock-open-id` 开关注入固定 open_id | 联调依赖 |
| T6 | 余额/积分流水是否暴露管理台查询页 | 本期仅 mp 端本人可查（4.3 #4/#5）；管理台只有充值流水页 | 若对账需求强，P2 迭代加管理台流水页 |
| T7 | 累计消费是否含「已冲正充值」 | 不含：累计消费只统计已完成订单应付；充值/冲正只动余额不动累计 | 口径写进报表域引用 |

## 9. 决策记录

| # | 决策 | 理由 |
|---|---|---|
| D1 | 金额/积分以元、decimal(12,2) 存储，展示不换算 | PRD 6.4「以分存储、展示转元」的意图是不丢精度（两位小数=分粒度）；存分再转元徒增换算层。数值上与 PRD 意图等价，建议 PRD 措辞回读时同步为「decimal 两位小数，最小粒度分」 |
| D2 | 不建 level_id 等级字段 | 架构 4.3 初稿表清单含 level_id，但 PRD 5.1 无等级功能（无等级规则/权益差异），本期无消费场景；升级时加列即可（预留升级路径同 open_id 跨店识别） |
| D3 | 累计消费冗余在 biz_member（total_amount/total_orders），订单完成本地事务内更新 | 列表页每行展示，实时聚合 order 表代价高；架构「口径按 store_id 聚合不依赖会员主表」指报表口径，由报表域自行聚合，两者不冲突 |
| D4 | 余额/积分扣减用条件 UPDATE，不用 @Version 乐观锁 | 单行单字段原子语句最简且天然防超扣；乐观锁需读改写两次且失败重试逻辑复杂 |
| D5 | 充值流水号 RC + yyMMdd + 雪花后 6 位 | 对齐原型 `RC20260925…007` 形态，全局唯一无需序列表 |
| D6 | 冲正「当日」= 服务器时区自然日 00:00 起 | PRD「仅限当日」的唯一合理解释；跨零点不可冲 |
| D7 | mp 端「当前店」不落库 | 前端状态 + 按最近消费排序的门店列表即可满足 PRD 交互，服务端零状态 |
| D8 | 操作店员姓名冗余于充值流水 | 员工删除/改名后流水仍可读（参照 dept_name 冗余先例） |
| D9 | MP 员工身份**自动识别**、无绑定流程（2026-10-15） | 会员 mobile 匹配当前门店对应部门下管理用户手机号即员工；零操作体验优先，误绑靠管理台手机号唯一录入 + 停用兜底（详见 4.6） |
| D10 | mp 员工端**不接管理台 token / RBAC 注解**，接口独立仅共用 service（2026-10-15） | 命中员工身份即全功能开放（PRD 无员工细分权限诉求）；管理台权限标识只作用管理台，两套鉴权语义互不渗透（详见 4.6） |
| D11 | mp 员工端操作日志记 sysUserId 并加来源标记（PC / MP 员工端）（2026-10-15） | 审计口径与管理台一致（记管理用户而非会员），来源区分防渠道混淆（详见 4.6） |
