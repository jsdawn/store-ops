# store-ops · 多租户门店经营系统

会员管理、积分兑换、记账式收银、积分商城的多租户门店经营 SaaS。单店 = 1 租户 1 门店，连锁 = 1 租户 N 门店，模型统一。

- 平台超管在管理台开租户、设套餐与到期时间；商家登录后自建门店、管理员工
- 顾客端为微信小程序（规划中），店员与顾客同端分角色
- 首版 MVP：会员 + 记账收银 + 积分商城 + 报表；路线 P0 骨架 → P1 门店组织 → P2 会员+收银 → P3 积分 → P4 报表 → P5 上线
- 小程序UI设计稿地址：<https://ardot.tencent.com/file/732718476352464?web_only=1>

## 整体架构

```
┌─────────────┐     ┌──────────────────────────┐
│ 管理台 (Vue3) │     │ 顾客小程序 uni-app (规划中) │
└──────┬──────┘     └────────────┬─────────────┘
       │  HTTP /dev-api           │ /mp/** 网关白名单
       ▼                          ▼
┌─────────────────────────────────────────┐
│         ruoyi-gateway :8080             │
└───────┬──────────┬──────────┬───────────┘
        ▼          ▼          ▼
   ruoyi-auth  ruoyi-system  ops-* 业务模块(规划)
     :9210       :9201      ops-store / ops-member /
                            ops-point / ops-order
        └────── Dubbo RPC + Nacos 注册/配置 ──────┘
                     ▼
        MySQL 8 · Redis · Nacos(18080 控制台)
```

- **后端**：RuoYi-Cloud-Plus 6.0.0（dromara 版）· JDK 21 · Spring Boot 4.1.1 · Spring Cloud Alibaba · Sa-Token · MyBatis-Plus 多租户
- **管理台**：RuoYi-Cloud-Plus-UI（Vue3 + TS + Element Plus + Vite）
- **业务模块命名**：框架模块保留 `ruoyi-` 原名，自研业务模块统一 `ops-` 前缀
- **数据模型**：业务表统一带 `tenant_id + store_id`，会员/积分/订单挂门店级，绩效按 store_id 聚合

## 目录结构

```
store-ops/
├── docs/                        # 【整体级】需求分析 / 架构 / 原型（跨端共享）
│   ├── store-ops-架构设计方案.md    # 架构方案（必读，v1.3）
│   ├── store-ops-产品需求文档PRD.md # 产品需求文档（v1.0 定稿）
│   ├── store-ops-UI设计规范.md     # 视觉基调与组件规范
│   ├── store-ops-api-项目上下文.md  # 后端架构/模块/端口速览
│   └── prototype/                # 低保真可交互原型（小程序 + 管理台 HTML）
├── store-ops-api/               # 后端微服务（RuoYi-Cloud-Plus 多模块工程）
│   ├── ruoyi-gateway / ruoyi-auth / ruoyi-modules/...
│   ├── script/docker/           # dev/prod Docker 编排 + .env 凭据模板
│   ├── script/config/nacos/     # Nacos 配置模板（占位符化，无明文密钥）
│   └── docs/                    # 【后端】开发文档（environment/IDE 指南等）
├── store-ops-admin/             # 管理台前端（plus-ui，pnpm 工程）
│   └── docs/                    # 【管理台】前端相关文档（如有）
└── README.md                    # 本文件
```

## 文档归属规则

| 文档内容                              | 存放位置                    |
| --------------------------------- | ----------------------- |
| 经营门店整体的需求分析、PRD、架构设计、UI 规范、原型     | `docs/`（仓库根）            |
| 后端相关文档（环境搭建、开发规范、模块设计、接口说明）       | `store-ops-api/docs/`   |
| 管理台相关文档（页面开发、组件、构建部署）             | `store-ops-admin/docs/` |
| 小程序端相关文档                          | `mp/docs/`              |
| 各端专属文档一律进各自项目内的 `docs/` 目录，不混入根目录 | —                       |

## 快速开始

**后端**（首次配置详见 `store-ops-api/docs/development/idea-启动调试指南.md`）：

1. Docker 起中间件：`docker compose -f store-ops-api/script/docker/docker-compose.dev.yml up -d`
2. IDEA 打开 `store-ops-api`，按指南配置 EnvFile + 运行配置
3. 开发期只启动 `ruoyi-gateway` + `ruoyi-auth` + `ruoyi-system` + 当前开发模块（Debug 模式）

**管理台**：

```bash
cd store-ops-admin
pnpm install
pnpm dev        # 默认 http://localhost:80，登录 admin / admin123
```

## 协作约定

- 单仓 monorepo，`main` 分支；commit / push 由开发者手动执行
- 后端构建统一 `./mvnw`（Maven Wrapper），勿用系统 mvn
- 后端凭据一律走 `script/docker/.env`（gitignored），配置模板内只允许 `${VAR}` 占位符，禁止明文密钥入库
- 后端中间件编排以 `script/docker/docker-compose.dev.yml` 为准；官方 upstream 编排仅作升级参照
