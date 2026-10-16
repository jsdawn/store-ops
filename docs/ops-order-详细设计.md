# 收银管理（ops-business · order 域）· 详细设计与数据库设计

> 版本：v1.2（v1.2 变更：**小程序员工端接口独立化**——不共用管理台 `/order/**`，走 `/mp/staff/**` 独立接口、仅共用 service 层（member 详设 4.6 定稿 D9–D11），员工端 controller 统一落各域 `controller/mp/`、类名 `MpStaff*` 前缀；**管理台恢复收银开单/收款功能定位**（订单页提供入口，3036/3037 为正式功能权限）；同步纠正第 6/7 节口径；v1.1 变更：落实审查结论 ①券 stub ③两段式 ⑤作废仅店长，新增顾客端「我的消费记录」接口设计，见 4.3 / D9）
> 依据：PRD v1.0 §5.2.3 / §5.3 / §6.3–6.6、管理台原型（订单管理页）、mp 原型（收银开单 / 收银成功 / 今日流水页）、member 域详设与已落地代码
> 对应实施路线：P2 会员与收银（member 域已落地，本详设为 order 域；券（coupon）域属 P3，本设计按「门面预留 + stub 接入」处理，见 4.4 / D3）
> 端范围：后端订单域 + 管理台订单管理页（`/order/**` **管理台专用**，含收银开单/收款入口）。**小程序员工端**（店员/店长收银）走独立 `/mp/staff/**` 接口（`MpStaffOrderController`，本期后置实现，共用 service 层，见 member 详设 4.6 定稿）。顾客端新增**我的消费记录**查询接口（controller/mp/，见 4.3），仅查询、无写路径。

---

## 1. 需求回顾

- 收银为「逐笔累加 → 合并结算」：店员录入明细（单价 × 数量，名称选填）→ 系统合计订单总额 → 选优惠券（可选，每单限一张）→ 选支付方式 → 确认收款完成订单。
- 支付方式四选一：**余额收款**（扣会员余额，同事务）/ **微信 / 支付宝 / 现金**（线下收款标记，平台不经手资金）。
- 会员可选、可跳过：散客单无余额 / 积分 / 券；会员单完成后按**券后实付金额 1:1** 落积分（member 域 `settleOrder` 已实现）。
- 订单状态机：`待收款 → 已完成`、`待收款 → 已作废`；已收款订单**不可作废、不可退款**（本期），错账处理见 5.6。
- 不存在 0 元单：应付金额必须 > 0 才可确认收款。
- 管理台订单管理页：按数据权限过滤（店长本店 / 租户管理员全租户），提供**开单 / 确认收款**（收银功能，见 7.3）与查询 / 详情 / 备注 / 作废 / 余额冲正操作；小程序工作台为同一收银能力的另一端入口（独立接口、共用 service）。

## 2. 全局约定（沿用 store / member 域定稿）

| 项 | 约定 |
|---|---|
| 包结构 | `org.dromara.business.order`：controller / domain / bo / vo / mapper / service / constant，与 member 域并列；跨域只走 service 门面（member 已有，coupon 见 4.4），禁止注入他域 Mapper |
| 网关 | 复用 `/business/**`（StripPrefix=1），无新增路由 |
| 表前缀 | `biz_order` / `biz_order_item`，统一带 `tenant_id + store_id`，租户插件自动隔离 |
| 金额精度 | DECIMAL(12,2)，元；积分同格式（与 member 域一致） |
| 门店校验 | 开单 / 收款前校验门店存在、属本租户、未停用（复用 `IBizStoreService` 既有校验语义，经门面） |
| 数据权限 | `@DataPermission` 挂 `dept_id + create_by`（同 BizMemberMapper 模式）：店长看本店（本部门及以下），租户管理员全量 |
| 幂等 | 框架 `@RepeatSubmit`（Redis token，同参数间隔内拦截）+ `order_no` 唯一约束兜底（PRD 5.3.1；与 member 充值幂等同机制） |

## 3. 数据库设计

### 3.1 ER 关系

```
biz_member (0..1) ──── < biz_order > ──── 1 biz_store
                          │ 1
                          │
                          ▼ N
                     biz_order_item
（member_id 可空 = 散客；order No. 全局唯一；券域 P3 上线后 biz_order.coupon_id → biz_coupon.coupon_id）
```

### 3.2 DDL（随代码交付 `script/sql/ops_order.sql`）

```sql
-- 1. 订单主表（订单为快照性数据：会员昵称/手机号冗余快照，后续改名不影响历史单）
CREATE TABLE biz_order (
  order_id        BIGINT        NOT NULL                   COMMENT '订单ID（雪花）',
  tenant_id       VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户编号',
  store_id        BIGINT        NOT NULL                   COMMENT '门店ID',
  dept_id         BIGINT        NOT NULL                   COMMENT '部门ID（门店对应部门，数据权限用）',
  order_no        VARCHAR(32)   NOT NULL                   COMMENT '订单号（ORD+yyMMdd+雪花尾6，全局唯一）',
  member_id       BIGINT        NULL                       COMMENT '会员ID（NULL=散客）',
  member_name     VARCHAR(50)   NULL                       COMMENT '会员昵称快照（下单时刻）',
  member_mobile   VARCHAR(20)   NULL                       COMMENT '会员手机号快照（下单时刻）',
  total_amount    DECIMAL(12,2) NOT NULL DEFAULT 0.00      COMMENT '订单总额（元，明细合计，券前）',
  coupon_id       BIGINT        NULL                       COMMENT '优惠券实例ID（未用券为 NULL，券域 P3）',
  coupon_amount   DECIMAL(12,2) NOT NULL DEFAULT 0.00      COMMENT '优惠券优惠额（元）',
  pay_amount      DECIMAL(12,2) NOT NULL                   COMMENT '应付金额（元，=总额−优惠额，恒>0）',
  pay_type        CHAR(1)       NULL                       COMMENT '支付方式（order_pay_type：1余额/2微信/3支付宝/4现金，完成时填）',
  status          CHAR(1)       NOT NULL DEFAULT '0'       COMMENT '状态（order_status：0待收款/1已完成/2已作废）',
  earned_point    DECIMAL(12,2) NOT NULL DEFAULT 0.00      COMMENT '本单积分（=应付等额，散客为0，冗余展示）',
  balance_reversed CHAR(1)      NOT NULL DEFAULT '0'       COMMENT '余额收款冲正标志（0否/1是，防重复冲正）',
  void_reason     VARCHAR(200)  NULL                       COMMENT '作废原因（作废时必填）',
  remark          VARCHAR(255)  NULL                       COMMENT '备注（追加式，见 5.7）',
  operator_id     BIGINT        NOT NULL                   COMMENT '开单操作人（sys_user）',
  operator_name   VARCHAR(30)   NOT NULL                   COMMENT '开单操作人姓名快照',
  create_dept     BIGINT        NULL,
  create_by       BIGINT        NULL,
  create_time     DATETIME      NULL,
  update_by       BIGINT        NULL,
  update_time     DATETIME      NULL,
  PRIMARY KEY (order_id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_store_status (tenant_id, store_id, status),
  KEY idx_member (tenant_id, member_id),
  KEY idx_create_time (create_time)
) ENGINE=InnoDB COMMENT='订单主表（门店级，收银）';

-- 2. 订单明细表（逐笔录入，订单完成后不可变）
CREATE TABLE biz_order_item (
  item_id         BIGINT        NOT NULL                   COMMENT '明细ID（雪花）',
  order_id        BIGINT        NOT NULL                   COMMENT '订单ID',
  tenant_id       VARCHAR(20)   NOT NULL DEFAULT '000000',
  store_id        BIGINT        NOT NULL,
  item_name       VARCHAR(50)   NULL                       COMMENT '项目名称（选填，仅展示备注）',
  unit_price      DECIMAL(12,2) NOT NULL                   COMMENT '单价（元）',
  quantity        INT           NOT NULL DEFAULT 1         COMMENT '数量（≥1）',
  line_amount     DECIMAL(12,2) NOT NULL                   COMMENT '行金额（=单价×数量，服务端计算）',
  create_time     DATETIME      NULL,
  PRIMARY KEY (item_id),
  KEY idx_order (order_id)
) ENGINE=InnoDB COMMENT='订单明细表';
```

### 3.3 约束说明

| 约束 | 说明 |
|---|---|
| `uk_order_no` | PRD 5.3.1 雪花 ID + order_no 唯一约束，重复提交兜底 |
| pay_amount > 0 | 服务端强校验（创建与收款双端），杜绝 0 元单 |
| member_id 可空 | 散客单；`member_name/member_mobile` 为下单时刻快照，展示层散客统一显示「散客」 |
| 无 del_flag | 订单不可删除（作废代替），与 biz_recharge_log 同风格，实体不带 @TableLogic |
| 券字段预留 | `coupon_id/coupon_amount` 本期不启用（券域 P3），创建接口暂不接受 couponId 参数 |

### 3.4 实体映射

- `BizOrder`（@AutoMapper → BizOrderVo / BizOrderBo），`BizOrderItem`（→ BizOrderItemVo）
- 状态/支付方式取值集中在 `order.constant.OpsOrderConstants`（status、pay_type、订单号前缀），不散落魔法值

## 4. 接口设计

### 4.1 模块与服务

- `IBizOrderService` + `BizOrderServiceImpl`（开单、收款、作废、余额冲正、查询）
- `BizOrderController`（/order/**，**管理台专用**）
- `MpStaffOrderController`（controller/mp/，/mp/staff/order/**，员工端收银：开单/收款/今日流水，本期后置实现，共用 service 层，鉴权见 member 详设 4.6）
- `MpBizOrderController`（controller/mp/，/mp/order/**，顾客端我的消费记录，见 4.3）
- 菜单/接口权限见第 6 节

### 4.2 管理台 REST（controller/，管理台专用，URL /order/**）

| # | 方法 | URL | 说明 | 权限 |
|---|---|---|---|---|
| 1 | POST | `/order` | 开单（→待收款）。Body：`BizOrderCreateBo`（items[]、memberId?） | `ops:order:add` |
| 2 | POST | `/order/{orderId}/pay` | 确认收款（→已完成）。Body：`BizOrderPayBo`（payType） | `ops:order:pay` |
| 3 | POST | `/order/{orderId}/void` | 作废（仅待收款）。Body：`{reason}` | `ops:order:void` |
| 4 | PUT | `/order/{orderId}/remark` | 追加备注（仅已完成，错账备注用） | `ops:order:remark` |
| 5 | POST | `/order/{orderId}/balance-reversal` | 余额收款冲正（仅已完成 + 余额收款 + 当日 + 未冲正过）。Body：`{reason}` | `ops:order:reversal` |
| 6 | GET | `/order/list` | 分页查询（门店/状态/支付方式/订单号/手机号关键字/日期区间） | `ops:order:list` |
| 7 | GET | `/order/{orderId}` | 详情（含明细行、券信息占位） | `ops:order:query` |
| 8 | GET | `/order/today` | 今日流水汇总：`{营业额合计, 单量, list[]}`（本店当日已完成+待收款） | `ops:order:list` |
| 9 | GET | `/order/coupon-options` | 选券候选（参数 memberId + totalAmount）。**本期 stub 返回空列表**，券域 P3 上线后由 coupon 域实现判定（见 4.4） | `ops:order:pay` |

请求/响应要点：

- **BizOrderCreateBo**：`items: [{itemName?, unitPrice, quantity}]`（1~50 行，unitPrice>0、quantity≥1）、`memberId?`（散客不传）。行金额与订单总额**服务端计算**，前端合计仅作展示。
- **BizOrderVo**：orderNo / createTime / storeName / 会员（快照+散客标记）/ 三段金额（total/coupon/pay）/ couponId+券名（P3 前恒空）/ payType / status / earnedPoint / balanceReversed / operatorName / remark / voidReason。金额三段由后端返回，前端不做减法。
- **GET /order/today** 返回：`{ totalAmount（当日已完成应付合计）, orderCount, list: BizOrderVo[] }`，list 仅当日、倒序、上限 50 条（管理台订单页顶部汇总；员工端工作台由 `/mp/staff/**` 独立接口提供同口径数据，共用 service）。

### 4.3 顾客端 REST（controller/mp/，/mp/order/**）— 我的消费记录

顾客（biz_member，微信 open_id / mp token 登录）在会员中心查看自己的消费记录。**仅查询、无写路径**。

| # | 方法 | URL | 说明 | 鉴权 |
|---|---|---|---|---|
| 1 | GET | `/mp/order/list` | 我的消费记录分页（仅已完成单，倒序；返回订单号尾号/时间/金额/支付方式/积分/明细行数） | Mp-Token |
| 2 | GET | `/mp/order/{orderId}` | 消费详情（含明细行全量；**校验订单 member_id = 当前顾客**，不符报「无权查看」） | Mp-Token |
| 3 | GET | `/mp/order/summary` | 消费汇总（累计消费 / 累计积分 / 消费次数，会员中心头部用；累计消费直接读 `biz_member.total_consume` 冗余，不聚合订单表） | Mp-Token |

请求/响应要点：

- **鉴权机制**：复用 member 详设定稿的 mp token 约定（`OpsMpConstants`：header `Mp-Token`，不透明 token，Redis key `ops:mp:token:{token}` → 值为 memberId，TTL 30 天）。拦截/解析逻辑放 `controller/mp/` 包内轻量工具类（读 Redis 取 memberId），**不进 Sa-Token pc 会话体系**。
- **租户/门店隔离**：mp 无登录态请求租户插件不生效，工具类取 memberId 后查 `biz_member` 反解 tenant_id，查询用 `TenantHelper.dynamic(tenantId)` 显式包裹；memberId 全局唯一（雪花），不存在跨租户歧义。
- **过滤口径**：仅 `status='1'`（已完成）计入消费记录；待收款/已作废单对顾客不可见（待收款是店内过程态，作废单展示会引起困惑）。余额冲正不改变展示（订单仍是已完成，金额语义按实付展示；如需体现冲正，后续在详情页加「已冲正」标记，本期不做）。
- **脱敏**：订单号展示尾 6 位；不返回操作人、备注、作废原因等内部字段——`MpBizOrderVo` 独立于 `BizOrderVo`，仅含顾客侧可见字段（D9）。
- **排期说明**：本组接口随本次 order 域一并实现（纯查询、代码量小），但**实测依赖后置的任务 9/10**（mp token 签发链路 `/auth/mp/login`）；任务 9 上线前接口代码就绪但无法端到端验证。

### 4.4 域间接口（模块内本地调用门面）

**消费 member 域能力**（`IBizMemberService`，已实现）：

| 方法 | 用途 |
|---|---|
| `selectMemberVoById(memberId)` | 开单选会员后校验：存在、属本店（store_id 一致）、状态正常；校验失败拦截并提示 |
| `deductBalance(memberId, payAmount, orderNo)` | 余额收款扣减（条件 UPDATE 原子，不足返回 false → 整单失败） |
| `settleOrder(memberId, payAmount, orderNo)` | 订单完成结算：1:1 加积分 + 累计消费冗余 + 积分流水（biz_no=orderNo） |

**提供给 coupon 域的门面**（P3，本期定义接口 + stub 实现）：

```java
public interface IBizCouponService {
    /** 收银选券候选：返回该会员在本店、未使用未过期、按 totalAmount 判定可用性的券列表（含不可用原因，供置灰） */
    List<CouponOptionVo> listUsableForOrder(Long memberId, Long storeId, BigDecimal totalAmount);
    /** 核销：条件 UPDATE 未使用→已使用，记核销时间与订单号；并发双核销返回 false */
    boolean writeOff(Long couponId, Long orderId);
}
```

- 本期 `BizCouponServiceStubImpl`：`listUsableForOrder` 返回空列表、`writeOff` 抛「券功能未上线」；`BizOrderServiceImpl` 中所有券相关调用走此门面，券域真实实现落地后**无需改动 order 域代码**，替换实现即可。
- 管理台/员工端选券区域：候选为空时显示「优惠券功能上线后开放」（前端按空列表处理，无需特性开关）。

## 5. 业务规则落地

### 5.1 开单（POST /order → 待收款）

1. 门店校验（存在 / 本租户 / 未停用，「门店已停用，不可办理会员业务」同 member 文案口径）。
2. memberId 传了则走 `selectMemberVoById` 校验归属，通过后写 `member_id + name/mobile 快照`；不传 = 散客。
3. 服务端逐行校验（unitPrice>0、quantity∈[1,999]）→ 计算 `line_amount / total_amount`。
4. `pay_amount = total_amount − coupon_amount`（本期 coupon_amount=0）→ 校验 `pay_amount > 0`（总额 0 也拦截：明细金额必须 > 0）。
5. 生成 order_no（`ORD + yyMMdd + 雪花尾6`，与充值号 RC 同构），落 `biz_order(待收款)` + 批量落 `biz_order_item`。
6. `@RepeatSubmit` 幂等。

### 5.2 确认收款（POST /order/{id}/pay → 已完成）— 核心事务

`@Transactional` 本地事务，前置状态校验 + 条件 UPDATE 防并发：

1. 查订单（本租户、数据权限内），校验 `status='0'`（非待收款：已完成报「该订单已收款」，已作废报「该订单已作废」）。
2. `pay_type` 校验字典值；**余额收款**额外要求 member_id 非空（散客不可余额收款）并重查会员余额供提示。
3. 金额复核：以落库 `pay_amount` 为准（待收款后明细不可改；改明细=作废重开）。
4. 券复核（P3 前跳过）：券状态二次校验（PRD 6.4 选券后过期拦截）。
5. 按支付方式执行：
   - **余额收款**：`deductBalance(memberId, payAmount, orderNo)`，返回 false → 抛「余额不足，整单失败」（同事务回滚，杜绝「订单成功余额没扣」，PRD 5.3.1）。
   - **微信 / 支付宝 / 现金**：仅落 pay_type（线下收款标记）。
6. **核销券**（P3 前跳过）：`writeOff` 失败 → 整单失败。
7. `settleOrder`（仅会员单）：1:1 积分 + 累计消费冗余 + 积分流水；散客跳过（earned_point 保持 0）。
8. 条件 UPDATE `biz_order SET status='1', pay_type=#{payType}, earned_point=#{point} WHERE order_id=? AND status='0'`，影响行数=0 说明并发收款 → 抛「订单状态已变化，请刷新」回滚。

并发口径：同一订单并发 pay 由第 8 步互斥；余额并发由 member 域条件 UPDATE 互斥（PRD 6.3，逐笔校验不足即失败）。

### 5.3 作废（POST /order/{id}/void）

权限**仅店长**（审查结论⑤：`ops:order:void` 只授予店长角色，租户管理员按框架惯例全量；店员不授予，避免误操作无监督）。仅 `status='0'` 可作废（已收款报「已收款订单不可作废」，PRD 6.5）；原因必填 ≤200 字（审查采纳建议，④）；条件 UPDATE `WHERE status='0'` 防并发双作废；**不产生任何余额/积分/券变动**。

### 5.4 积分（PRD 5.1.3 落地口径）

- 来源仅订单完成，`积分 = 券后实付（pay_amount）× 1`，两位小数不取整；散客不产生。
- 由 member 域 `settleOrder` 落账（point_log biz_no = order_no，PRD「积分流水关联 order_no」）。
- `biz_order.earned_point` 冗余存本单积分，列表/详情直接展示，不回查积分流水。
- 已收款订单不回退积分（PRD 6.5；余额冲正也不回退，见 5.6）。

### 5.5 门店停用联动

停用门店：开单 / 收款 / 作废接口均拦截（管理台与员工端 `/mp/staff/**` 同口径，文案「门店已停用，不可办理会员业务」同 member 域）；管理台订单**查询不受影响**（历史订单可查，与 store 域停用语义一致）。

### 5.6 余额收款错账冲正（PRD 6.5 落地，仅管理台）

PRD：已收款订单不可作废不可退款；余额收款错账由有冲正权限的员工当日冲正（负向余额流水）。落地为独立动作，**不动订单状态**：

1. 校验：订单 `status='1'` 且 `pay_type='1'`（余额收款）且 `balance_reversed='0'` 且收款时间在当日（`DateUtil.beginOfDay`，与充值冲正同口径）。
2. 动作：给会员**加回** `pay_amount`（member 域新增门面 `reversalConsume(memberId, payAmount, orderNo)`：原子 `balance = balance + ?` + 余额流水 `biz_type='冲正'`、`change_amount=+pay_amount`、`biz_no=order_no`）。
3. 条件 UPDATE `biz_order SET balance_reversed='1' WHERE order_id=? AND balance_reversed='0'`，0 行 → 「该订单已冲正过」回滚（防双冲）。
4. **券不恢复、积分不回退**（PRD 6.5 简化规则）；订单备注自动追加冲正记录。
5. 权限 `ops:order:reversal`，与充值冲正（3022）同级，仅授予租户管理员（店长按需勾选）。

### 5.7 备注（PUT /order/{id}/remark）

仅已完成订单可备注（错账场景，PRD 6.5「追加备注」）；追加式：新备注以 `[MM-dd HH:mm 操作人] 内容` 拼接进 `remark`（服务端拼接，单字段上限 255，超出报错），保留历史痕迹且不建备注表（决策 D6）。

### 5.8 order_no 与幂等

- `order_no = ORD + yyMMdd + 雪花尾6`（如 `ORD261012347008`），与充值号 RC 同构，全局唯一约束兜底；展示层（员工端工作台 / 管理台列表）脱敏为 `订单 …7008` 尾号（同原型）。
- 幂等：创建与收款均挂 `@RepeatSubmit`（Redis，同参数 + 间隔拦截）；「同一 token 只落一笔」由「order_no 唯一 + 状态条件 UPDATE」保证，无需独立取 token 接口（决策 D5，待确认项②）。

## 6. 菜单与权限初始化（SQL 随代码交付 `script/sql/ops_order.sql`）

菜单沿用 3000 段（store 3000~3004、member 3010~3022），order 取 **3030~3035**，挂在 3000「门店经营」目录下：

| menu_id | 名称 | 类型 | 权限字符 | 说明 |
|---|---|---|---|---|
| 3030 | 订单管理 | C（`ops/order/index`） | — | 管理台订单管理页 |
| 3031 | 订单查询 | F | `ops:order:list` | 列表 + 今日流水 |
| 3032 | 订单详情 | F | `ops:order:query` | |
| 3033 | 订单作废 | F | `ops:order:void` | 仅待收款 |
| 3034 | 订单备注 | F | `ops:order:remark` | 错账备注 |
| 3035 | 余额收款冲正 | F | `ops:order:reversal` | 仅管理台，当日有效 |

**开单/收款权限码（`ops:order:add` / `ops:order:pay`）**：管理台收银功能的正式功能权限（管理台订单页提供开单/收款入口，见 7.3）；小程序员工端**不接 RBAC**（D10，`/mp/staff/**` 独立鉴权），不依赖这两个权限码：

| menu_id | 名称 | 权限字符 | 说明 |
|---|---|---|---|
| 3036 | 收银开单 | `ops:order:add` | 管理台开单入口按钮权限 |
| 3037 | 确认收款 | `ops:order:pay` | 管理台收款按钮权限 |

角色绑定（管理台手工勾选）：**店长**勾 3031/3032/3033（含作废，**仅店长**，店员不可作废）/ 3034 / 3036 / 3037（管理台收银闭环）；**租户管理员**全量 + 3035（余额冲正，与充值冲正 3022 同级）；**店员**管理台无需订单菜单（收银走员工端 `/mp/staff/**`，不走 pc 权限体系）。顾客端 `/mp/order/**` 与员工端 `/mp/staff/**` 均不设菜单权限（Mp-Token 鉴权）。

**字典**：新增 `order_status`（0 待收款/1 已完成/2 已作废）与 `order_pay_type`（1 余额收款/2 微信收款/3 支付宝收款/4 现金收款），插 000000 行后**管理台点「同步租户字典」**（见 docs/development/多租户字典与参数配置机制说明.md；注意先于页面使用，避免 NullValue 空缓存）。

## 7. 页面字段映射（原型 → 接口 → 表）

### 7.1 mp 工作台 · 收银开单页（p-cashier）

> 员工端接口均为 `/mp/staff/**` 独立接口（`MpStaffOrderController` 等，本期后置实现），**共用下表所列 service 逻辑**，不走管理台 `/order/**`（D10）。

| 原型区块 | 接口 | 表/来源 |
|---|---|---|
| 1·会员（可选，含余额/积分/券摘要、散客项） | 员工端会员搜索（独立接口，共用 member 域搜索逻辑） | biz_member |
| 2·消费明细（逐笔 + 添加一笔 + 自动合计） | 前端草稿态；提交走员工端开单接口（共用 5.1 校验链） | biz_order_item（落库后不可变） |
| 订单总额（自动合计） | 前端实时展示；服务端复算 | total_amount |
| 3·优惠券（限一张，不可用置灰） | 员工端选券候选（共用券门面 stub，本期空列表 → 区块显示「上线后开放」） | coupon 域 P3 |
| 应付金额 / 本单积分预估 | 前端 = 总额 − 优惠额（本期无券即总额）；积分 = 应付等额，落账以服务端为准 | pay_amount / earned_point |
| 4·支付方式（余额不足置灰） | pay 前按会员余额前端置灰；服务端 deductBalance 最终校验 | pay_type |
| 确认收款（防重：按钮置灰+幂等） | 员工端收款接口（共用 5.2 事务） | status 条件 UPDATE |

交互口径：单页内「开单 → 收款」连续完成；中断 / 收款失败留下**待收款单**，工作台今日流水与管理台可见，可作废重开。

### 7.2 mp 工作台 · 收银成功页（p-order-done）+ 今日流水页（p-orders）

| 原型元素 | 接口/来源 |
|---|---|
| 收款成功摘要（订单号尾号、支付方式、三段金额、券核销、积分） | 员工端收款接口返回的 BizOrderVo（员工端详情接口兜底） |
| 今日营业额 / 单量 / 流水列表（总额−券·支付方式·攒分） | 员工端今日流水独立接口（共用 biz_order 聚合逻辑，同 4.2 #8 口径） |

### 7.3 管理台 · 订单管理页（pg-order）

| 原型列/控件 | 接口/表 |
|---|---|
| 筛选：门店（租户视角）/ 状态 / 支付方式 / 订单号·手机号 | GET /order/list（字典 order_status、order_pay_type） |
| 列：订单号/时间、门店、会员（散客）、总额、优惠额（券名）、应付、支付方式 tag、状态 tag、本单积分 | BizOrderVo（storeName 门面补齐同 member；券名 P3 前恒 —） |
| 操作：开单（按钮，弹窗：选会员/散客 + 明细逐笔 + 支付方式，连续完成开单→收款） | POST /order（共用 5.1）→ POST /order/{id}/pay（共用 5.2） |
| 操作：详情 | GET /order/{id}（弹窗含明细行） |
| 操作：备注（已完成） | PUT /order/{id}/remark |
| 操作：作废（仅待收款，红色） | POST /order/{id}/void（原因必填弹窗） |
| 操作：余额冲正（pay_type=余额 + 当日 + 未冲正） | POST /order/{id}/balance-reversal（确认弹窗：加回金额展示、原因必填） |
| 规则提示条 | 原型文案保留（不可作废/退款、数据权限口径） |

## 8. 边界情况与待确认事项

### 8.1 边界情况（PRD 6.3–6.5 对照，已在规则章节落地）

| 场景 | 处理 |
|---|---|
| 重复点击确认收款 | @RepeatSubmit + status 条件 UPDATE 互斥 |
| 余额收款与充值并发 | member 域条件 UPDATE 行级原子，逐笔校验不足即失败（PRD 6.3） |
| 券选后过期 / 明细变化 | 券复核在 pay 事务内（P3 前无券；P3 落地时明细仅可在待收款前改） |
| 待收款订单不买了 | 作废，无资产变动 |
| 已收款要求退款 | 不支持；余额错账走 5.6 冲正，线下收款错账走备注 |
| 券已核销订单出错 | 券不恢复；金额层面按上条处理 |
| 并发双冲正 / 双作废 / 双收款 | 三者均为「标志位 / 状态」条件 UPDATE，0 行即回滚报友好提示 |

### 8.2 待确认事项 → 审查结论（2026-10-15 已拍板，转入第 9 节决策）

| # | 事项 | 审查结论 |
|---|---|---|
| ① | 券域依赖 | **stub**（本次预留，券域 P3 再做）→ D3 |
| ② | 幂等机制 | 按建议默认：`@RepeatSubmit` + order_no 唯一 → D5 |
| ③ | 两段式 vs 一步单 | **两段式**（create 待收款 + pay 完成）→ D10 |
| ④ | 作废原因 | 按建议默认：必填 ≤200 字 → 5.3 |
| ⑤ | 作废权限 | **仅店长可作废**（店员不授予）→ 5.3 / 第 6 节 |
| ⑥ | 备注上限 | 按建议默认：累计超 255 拒绝并提示 → D6 |
| ⑦ | today 口径 | 按建议默认：营业额/单量仅算已完成，列表含待收款带状态 tag → 4.2 #8 |
| ⑧（新增需求） | 顾客端消费记录 | **需要**：新增 4.3 三个 mp 查询接口 → D9 |

## 9. 决策记录

| # | 决策 | 理由 |
|---|---|---|
| D1 | 明细独立表 `biz_order_item`，行金额服务端计算 | 逐笔明细需在订单详情完整还原；前端算钱不可信（PRD「店员只录入不计算」由服务端保证） |
| D2 | 会员昵称/手机号以快照列冗余进订单 | 订单是历史凭证，会员改名/换号不影响历史单展示；避免联表 |
| D3 | 券字段（coupon_id/coupon_amount）本期预留不启用，走 `IBizCouponService` 门面 stub | 券域 P3 才落地；收银先行不阻塞，接入时 order 域零改动 |
| D4 | 余额收款冲正独立于充值冲正：加回余额 + `balance_reversed` 标志防重，订单状态不变 | PRD 6.5 明确「不可作废不可退款 + 当日冲正」；复用充值冲正会使两种冲正在流水/标志上混淆 |
| D5 | 幂等用框架 `@RepeatSubmit`，不做显式 token 接口 | 与充值幂等同机制、无新增接口；order_no 唯一约束是最终兜底 |
| D6 | 备注为单字段追加式（服务端拼时间戳+操作人），不建备注表 | 错账备注低频、纯追溯用途，独立表过度设计 |
| D7 | 支付方式独立字典 `order_pay_type`（1余额/2微信/3支付宝/4现金），不复用 member_pay_type | 语义不同（充值方式无余额选项），避免租户改充值字典波及订单展示 |
| D8 | earned_point 冗余落订单表 | 列表/今日流水高频展示，免回查积分流水；与 total_amount 等冗余同风格 |
| D9 | 顾客端新增「我的消费记录」3 个只读 mp 接口（4.3），独立 MpBizOrderVo 只吐顾客侧字段 | 审查新增需求：顾客需查看自己的消费记录；只读无写路径，不触碰收银事务；mp token 机制复用 member 详设定稿，实测依赖任务 9/10 |
| D10 | 收银两段式（create 待收款 → pay 已完成） | 审查确认；支持状态机、作废兜底与中断续收，前端单页连续调用体验与一步单无差 |

---

> **变更记录**：v1.0（2026-10-15 初稿）；v1.1（2026-10-15 审查结论落实：①券 stub / ③两段式 / ⑤作废仅店长，新增顾客端消费记录 4.3 + D9/D10，8.2 转结论表）。
