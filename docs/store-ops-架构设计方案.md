# store-ops 多租户门店经营系统 · 架构设计方案

> 版本：v1.4（2026-10-09：业务表统一 biz_ 前缀）
> 状态：设计完成，待搭建骨架
> v1.1 变更：补充小程序 appid 模式、微信支付通道、顾客身份鉴权三大遗漏；修正 member 唯一约束、预约时段模型、订单幂等、开发环境最小化
> v1.2 变更：预约模块与真实微信支付推迟到上线后迭代；首版 MVP 收敛为 会员 + 记账收银 + 积分 + 报表
> v1.3 变更：会员唯一标识改为门店 + 手机号（open_id 降级为登录凭证）；顾客端支持手动切换已注册门店；核心表清单补充余额/充值/优惠券四表，member 增加 balance，point_balance 改 decimal，order 增加优惠券字段（对齐 PRD v1.0）
> v1.4 变更：业务表统一 `biz_` 前缀（与框架 sys_ 表区分、解决 order 保留字），核心表清单同步更名

---

## 1. 项目概述

**store-ops** 是一套面向实体门店的多租户经营 SaaS 系统，覆盖四大核心业务：

| 功能模块 | 说明 |
|---|---|
| 会员管理 | 门店会员池、等级、开卡归属 |
| 积分兑换 | 消费攒分、积分商城兑换、流水记账 |
| 智能收银 | 小程序开单收款、订单流水 |
| 服务预约 | 顾客在线预约、门店核销 |

**商业形态**：平台方（超管）向商家售卖系统使用权。租户 = 品牌方或单店商家：

- 连锁店：1 个租户管理 N 个门店
- 单体店：1 个租户管理 1 个门店（同一套模型，自然退化，无需区分）

---

## 2. 总体架构

```
store-ops/
├── docs/                     # 需求、表设计、原型
├── ops-cloud/                # 后端（RuoYi-Cloud-Plus）
│   ├── ruoyi-gateway/        # 网关
│   ├── ruoyi-auth/           # 认证中心（含微信登录扩展）
│   ├── ruoyi-api/            # 服务间 API
│   ├── ruoyi-common/         # 公共模块
│   ├── ruoyi-modules/
│   │   ├── ruoyi-system/     # 框架自带：租户、用户、权限
│   │   ├── ops-store/        # 门店管理
│   │   ├── ops-member/       # 会员（门店级）
│   │   ├── ops-point/        # 积分（门店级）
│   │   ├── ops-order/        # 收银订单（门店级）
│   │   └── ops-booking/      # 服务预约（门店级）
│   └── sql/
├── ops-admin/                # 管理台（Vue3 + TS + Element Plus）
└── ops-mp/                   # 微信小程序（uni-app Vue3）
    └── src/pages/
        ├── customer/         # 顾客端页面
        ├── staff/            # 商家工作台页面（收银/核销/查询）
        └── common/           # 登录、公共页
```

**命名约定**：框架模块保留 ruoyi 原名（便于跟随官方升级），业务模块统一 ops- 前缀（一眼区分框架代码与自研代码）。

---

## 3. 技术选型

| 项 | 选型 | 说明 |
|---|---|---|
| 后端框架 | RuoYi-Cloud-Plus | dromara 组织维护，微服务 + 多租户开箱即用 |
| 技术栈 | JDK 17/21 + Spring Boot 3 | Spring Cloud Alibaba（Nacos + Gateway）、Sa-Token、MyBatis-Plus |
| 多租户 | MyBatis-Plus 租户拦截器 | tenant_id 自动注入，业务代码无感 |
| 管理台前端 | RuoYi-Cloud-Plus-UI | Vue3 + TS + Element Plus + Vite |
| 小程序 | uni-app (Vue3) | 顾客 + 店员双角色同一个小程序 |
| 数据库 | MySQL 8.x | |
| 缓存 | Redis | |
| 注册/配置中心 | Nacos 2.2+ | |

**仓库地址**

- 后端：https://gitee.com/dromara/RuoYi-Cloud-Plus
- 文档：https://plus-doc.dromara.org/
- 前端：RuoYi-Cloud-Plus-UI（链接见仓库 README）

**选型说明**：官方 y_project/RuoYi-Cloud 无多租户，不可用；yudao-cloud 功能全但体量过重，二次开发心智负担大。RuoYi-Cloud-Plus 结构贴近若依、多租户内建，是"每一行都改得动"的最优解。注意 JavaLionLi 原仓库已迁移至 dromara 组织。

---

## 4. 多租户与数据模型

### 4.1 两级归属模型

```
tenant 租户（品牌方 / 单店商家）        ← tenant_id 框架自动隔离
 └── store 门店（1 对 N）
      ├── staff 员工（店长/店员/收银员）
      ├── member 会员（门店级会员池）
      ├── point 积分（余额挂门店账户）
      ├── order 收银订单（门店级）
      └── booking 服务预约（门店级）
```

### 4.2 表设计约定

- 业务表统一 `biz_` 前缀（biz_store / biz_member / biz_order …），与框架 `sys_` 表一眼区分，规避 `order` 等 MySQL 保留字
- 所有业务表同时带 `tenant_id`（框架注入）+ `store_id`（业务归属）+ `dept_id`（冗余，供框架数据权限过滤）
- **会员挂门店级**（member.store_id）：各店独立会员池，A 店会员在 B 店是陌生人
- **预留升级路径**：member 表存 `open_id`（微信）、`mobile`，将来做连锁跨店识别（"顾客主体 + 门店会员关系"模型）时零改表升级
- 积分采用**余额 + 流水**模式：余额在门店积分账户上，`point_log` 每笔记 store_id + biz_order_id

### 4.3 核心表清单（初稿）

| 表 | 归属 | 关键字段 |
|---|---|---|
| biz_store | 租户 | store_name, address, status, dept_id |
| biz_member | 门店 | store_id, mobile(必填, 唯一键), open_id, level_id, balance(decimal), point_balance(decimal) |
| biz_recharge_log | 门店 | member_id, store_id, amount(本金), gift_amount(赠送), pay_type, operator_id |
| biz_balance_log | 门店 | member_id, store_id, change, biz_order_id, type(充值/消费/冲正) |
| biz_point_log | 门店 | member_id, store_id, change, biz_order_id, type |
| biz_coupon_template | 门店 | store_id, name, type(满减/折扣), threshold, value, discount, cap, cost_point, total, per_limit, valid_days, status |
| biz_coupon | 门店 | template_id, member_id, store_id, status(未使用/已使用/已过期), expire_at, used_order_id |
| biz_order | 门店 | store_id, member_id, amount(总额), coupon_id, discount_amount(优惠额), pay_amount(应付), pay_type(余额/微信/支付宝/现金), status |
| biz_order_item | 订单 | order_id, sku, qty, price |
| biz_booking | 门店 | store_id, member_id, service_id, staff_id, time, status |
| biz_service | 门店 | store_id, name, price, duration |
| biz_booking_slot | 门店 | store_id, date, time_range, capacity, used |

**金额与积分精度**：金额（balance、amount 等）与积分（point_balance、cost_point 等）统一以「分」为最小单位存储（decimal，两位小数），PRD 定稿要求积分不取整、等额转化。

**member 唯一约束**：`(tenant_id, store_id, mobile)` 唯一，手机号为建档必填；open_id 仅作微信登录凭证（一个档案最多绑一个，可后补绑定），不作唯一标识。顾客微信授权登录后确认手机号，按 (门店, 手机号) 匹配——线下登记过的补绑 open_id 登录，无档案则新建，避免「先线下建档、后微信扫码」产生重复档案。

**预约可约时段模型**：`booking_slot` 为时段容量表（营业时间 × 每时段 N 人），小程序按日查询可约时段；按员工排班的复杂模型后续阶段再扩展。

---

## 5. 权限体系

| 角色 | 数据范围 | 实现方式 |
|---|---|---|
| 平台超管 | 全平台（不属于任何租户） | 框架超管账号 |
| 租户管理员（品牌方） | 本租户全部门店 | 租户超管角色 |
| 店长 | 本店 | RuoYi 数据权限 = 本部门 |
| 店员/收银员 | 本店操作级 | 岗位权限 |

- 员工组织：RuoYi 的 dept 树映射 `租户总部 → 各门店` 两层
- 同一统计接口，店长查到 1 家店、租户管理员查到 N 家店——数据权限自动拼过滤条件，业务代码不写 if
- 加盟模式建议：租户管理员默认仅统计聚合权限，明细权限按需单独授予（兼容直营/加盟两种数据敏感度）

---

## 6. 运营模式

**开通流程**（平台超管操作，框架原生功能零开发）：

1. 商家谈妥后，超管登录后台 → 租户管理 → 新增租户：填商家名、设租户管理员账号密码、选套餐、设到期时间
2. 账号发给商家
3. 商家登录后自建门店（ops-store）、拉员工、配角色

**租户套餐 = 版本分级**：套餐即菜单权限集——免费版（收银+会员）、标准版（+积分）、旗舰版（+预约+报表），改勾选即可调整售卖版本，不动代码。到期时间 = 订阅周期，过期自动停用。

**分工边界**：超管只管到租户级（开通、套餐、续费、停用）；门店级事务全部下放商家自管。

---

## 7. 绩效统计

核心口诀：**所有口径按 store_id 聚合**，不依赖会员主表：

| 指标 | 算法 |
|---|---|
| 开卡数 | member 按 store_id group |
| 营业额/单量 | order 按 store_id group |
| 新客 vs 老客 | order join member，是否本店首单 |
| 会员到店频次/常消费店 | order 按 member_id 聚合 |
| 积分产出/消耗 | point_log 按 store_id 分正负聚合 |

**报表形态**（租户视角）：全品牌大盘（营业额/单量/新客/会员总数 + 环比）→ 门店对比排行 → 单店下钻趋势。

**性能预留**：初期实时 group by；数据量上来后加 `report_store_daily` 日预聚合表（定时任务），查询切汇总表，接口不变。

**归因规则**：初期只做硬数据口径（开卡店 + 消费店），分摊类规则后续再加，表结构无需预留。

---

## 8. 小程序设计

### 8.1 小程序形态：平台共用一个 appid

**所有商家共用平台注册的一个小程序 appid**（客如云、美团收银同模式）：

- 每店生成专属小程序码，码带 `scene` 参数（store_id），顾客扫码即定位门店
- 商家零注册成本，平台发布一次全租户生效
- 单 appid 下同一微信用户 open_id 唯一，天然支持将来的跨店识别
- 代价：小程序品牌是平台品牌而非商家品牌（SaaS 行业标准接受）

### 8.2 双角色同端（参考瑞幸早期模式）

- 底部 tabbar 固定顾客视角：首页 / 积分商城 / 我的（**不做动态 tabbar**，uni-app 自定义 tabbar 坑多）
- 身份判定：登录后查手机号是否绑定员工身份，绑定则"我的"页显示**商家工作台**入口
- 工作台 = 功能宫格页（收银开单、预约核销、会员查询、今日流水），页面栈独立，退出回顾客视角
- 顾客"当前店"：默认按最近一次消费/扫码的门店自动切换，也可在门店列表手动切换（列表仅展示已注册过会员的门店）；进入新门店的唯一途径是扫该店小程序码，会员页跟随当前店

### 8.3 顾客身份与鉴权（重要）

**顾客 member 不是 sys_user**，框架登录体系（Sa-Token 员工登录态、数据权限）对顾客接口不生效，需自建一层：

- Sa-Token 多端：`pc` = 员工登录（走框架原生），`mp` = 顾客登录（自定义）
- 微信登录接口（ruoyi-auth 新增）：`wx.login → code2session → 校验/注册 member → 颁发 mp 端 token`
- 网关白名单：小程序顾客接口（`/mp/**` 前缀）放行框架登录校验，走自定义拦截
- **顾客接口手动校验归属**：member 的 store_id / tenant_id 必须显式比对，禁止依赖框架数据权限

### 8.4 支付通道（分阶段）

| 阶段 | 方案 | 说明 |
|---|---|---|
| 首版（收银上线） | 记账式收银 | 现金/到店付标记收款，不接真实支付 |
| 上线后迭代 | 租户直连商户号 | 租户配置存 `wx_mchid`/证书，每商家配自己的微信支付商户号 |
| 远期（规模化） | 微信支付服务商模式 | 平台申请服务商资质，商家为子商户（有申请门槛） |

租户配置表预留 `wx_mchid` 字段，首版不启用。

### 8.5 订单幂等

- 单号：雪花 ID 生成 + `order_no` 数据库唯一约束（兜底）
- 收银接口：前端防重复提交 + 后端幂等 token（复用 Redis token 机制，与 springLearningProject P2② 相同模式）

---

## 9. 实施路线

| 阶段 | 内容 | 验收标准 |
|---|---|---|
| P0 环境与骨架 | 装 JDK 17，拉 RuoYi-Cloud-Plus + UI，重组 store-ops 结构，导 SQL，起 Nacos/MySQL/Redis | 原样跑通：登录管理台、可见租户管理菜单 |
| P1 门店与组织 | ops-store 模块 + dept 映射门店 + 店长数据权限 | 超管开租户 → 商家建店 → 店长只见本店 |
| P2 会员与收银 | ops-member + ops-order（记账式收银） | 店员开单收款 → 会员余额/积分联动 |
| P3 积分 | ops-point（消费攒分、积分商城兑换核销） | 顾客端积分闭环 |
| P4 报表 | 租户大盘 + 门店对比 + 下钻 | 三层报表可演示 |
| P5 上线 | 部署上线、report_store_daily 预聚合 | MVP 可对外演示 |
| P6+ 上线后迭代 | ops-booking 预约模块、真实微信支付（租户商户号 → 服务商模式） | 按运营反馈排期 |

**首版 MVP 范围**：会员管理 + 记账收银 + 积分商城 + 报表；小程序首版功能 = 注册/查看积分 + 店员收银 + 积分兑换核销，预约入口不上线。预约相关表（booking / booking_slot / service）设计保留，实现推迟。

**开发环境注意**：

- 微服务全量 8+ 进程（gateway/auth/system/5 个业务模块）× 500M~1G 内存，**开发期只起 gateway + auth + system + 当前开发模块**
- 新版框架默认集成 SnailJob 分布式任务调度，启动文档需确认是否要额外起调度服务
- 业务表避免 `sys_` 前缀（框架表被排除在租户过滤外）；P1 第一张业务表生成后验证一次租户拦截器是否生效

**P0 开工前置确认**：

- [ ] 代码目录（建议 `C:\Users\jsdawn\Codes\store-ops`）
- [ ] JDK 17/21 安装
- [ ] 中间件现状盘点：MySQL 8 / Redis / Nacos（Docker 单机即可）
