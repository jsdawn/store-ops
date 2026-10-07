# script/docker — 环境编排说明

本目录承载 store-ops 后端的本地开发环境与生产部署编排。**基础设施配置是项目资产**：
dev/prod 两套编排自建维护，`docker-compose.yml`（RuoYi-Cloud-Plus upstream 原样）仅作版本升级参照，不参与日常开发。

## 目录结构

```
script/docker/
├── docker-compose.yml        # upstream 原样参照（只读，升级时 diff 用，勿直接改）
├── docker-compose.dev.yml    # 开发编排（本机：mysql/redis/nacos/seata-server）
├── docker-compose.prod.yml   # 部署编排（服务器 host 网络，upstream 全家桶精简演进中）
├── init-db.sql               # MySQL 容器首启自动建库（4 个业务库；seata 用 file 模式无需 TC 库）
├── .env.example              # 环境变量模板（cp 为 .env 后填写，.env 不入库）
├── fetch-legacy-adapter.sh   # Nacos v1 兼容插件恢复脚本（jar 已入库，仅损坏时重下）
└── seata/
    ├── conf/application.yml  # TC 生效配置：upstream 原样，仅 registry 段接 Nacos（dev/prod 共用）
    └── nacos-plugins/        # nacos-api-legacy-adapter jar（已入库，挂载至 nacos 容器）
```

## 环境变量（.env）

模板见 `.env.example`，compose 会自动读取本目录下的 `.env`。分两组：

| 变量 | 用途 | 消费方 |
|---|---|---|
| `MYSQL_PASSWORD` | MySQL root 密码 | dev/prod compose、Nacos 配置模板、后端服务 |
| `REDIS_PASSWORD` | Redis 密码 | dev compose、Nacos 配置模板 |
| `NACOS_USERNAME` / `NACOS_PASSWORD` | Nacos 管理员 | 控制台登录、配置导入脚本、seata-server、后端服务 |
| `NACOS_TOKEN` | Nacos v3 鉴权 token（`openssl rand -base64 64`） | compose（nacos 容器） |
| `NACOS_IDENTITY_VALUE` | Nacos 服务端互信身份 | compose（nacos 容器） |
| `MONITOR_PASSWORD` | Spring Boot Admin 凭据 | Nacos 配置模板 |
| `RABBITMQ_PASSWORD` | spring-cloud-bus 总线 | Nacos 配置模板 |
| `RUOYI_IMAGE_PREFIX` / `RUOYI_IMAGE_TAG` | 后端服务镜像前缀/版本 | **仅 prod**（P6 推 GHCR 后改 `ghcr.io/<user>`） |
| `MINIO_PASSWORD` / `GRAFANA_ADMIN_PASSWORD` | MinIO / Grafana 凭据 | **仅 prod** |

dev/prod 共用同一批凭据变量；**环境间差异尽量收敛到变量，而不是改 compose**。

## 开发环境启动（本机）

```bash
cd script/docker
cp .env.example .env          # 首次：填写真实值
docker compose -f docker-compose.dev.yml up -d
```

- 首启时 `init-db.sql` 自动建 4 个业务库，再按 `script/sql/` 顺序导入表结构与种子数据
- 首启后执行 `script/config/nacos/import-nacos-config.sh` 导入配置模板（占位符在模板里，凭据由后端服务运行期从环境变量解析，仓库零明文）
- 后端服务启动方式见 `docs/development/idea-启动调试指南.md`

## 生产部署（服务器）

服务器约定目录 `/docker/<服务名>/`（upstream 惯例，配置与日志持久化均挂载于此）。

```bash
# 1. 上传文件到服务器（首次）
#    - script/docker/.env            -> /docker/env 或 compose 同目录（.env 需与 compose 同目录）
#    - seata/conf/application.yml    -> /docker/seata/conf/
#    - nacos-plugins/*.jar           -> 挂载路径按 compose 内 volumes 实际为准
# 2. 启动（按需起服务，不必全家桶）
docker compose -f docker-compose.prod.yml up -d mysql nacos redis seata-server
```

- prod 全栈 `network_mode: "host"`（upstream 设计，端口直通无 NAT）；**不建议**单独把某个服务改 bridge（见下）
- seata 的 Nacos 地址、镜像前缀/版本、MinIO/Grafana 凭据均在 `.env` 覆盖，**部署只改 .env**

## 配置一致性原则

1. **同一配置只留一份**：seata `conf/application.yml` dev/prod 共用，环境差异（Nacos 地址）由各编排写死的容器变量 `NACOS_SERVER_ADDR` 注入（dev=`nacos:8848` / prod=`127.0.0.1:8848`，随网络模式而定，非 .env 部署项）
2. **凭据零明文**：仓库内只有 `${VAR}` 占位符；真实值只存在于本机/服务器的 `.env`
3. **compose 只读变量，不写死环境值**：部署期会变的（地址、凭据、镜像）一律 `${VAR:-默认值}`
4. **镜像版本在 compose 中锁定**（mysql 8.4.9 / nacos v3.2.3 / redis 8.6.3 / seata 2.6.0），升级编排属代码变更、走评审

## 注意事项

### Seata（TC）
- 配置 = upstream 原样 `seata/conf/application.yml`，唯一改动 registry 段（`ruoyi-seata-server` 注册进 Nacos）；**store 保持官方默认 file 模式**，无需 TC 四表，将来切 db 按官方样例（子键必须 kebab-case，camelCase 会触发 SPI `name is null`）
- seata 自身配置系统只认 `${VAR}` 纯变量，**不认 `${VAR:default}`**（会整串当主机名）
- 客户端从 Nacos 拉 `seata-server.properties`（vgroupMapping）；TC 自己不读它（`config.type: file`）
- 端到端 AT 回滚需要各业务库有 `undo_log` 表

### Nacos 版本耦合（重要）
- seata 2.6.0 内嵌 nacos-client 1.4.6 只走 **v1 API**，而 Nacos 3.2.0 起主程序移除了 v1/v2 API
- 解法：`seata/nacos-plugins/nacos-api-legacy-adapter-3.2.0.jar` 挂载至 **nacos 容器** `/home/nacos/plugins/`（150KB，Apache-2.0，已入库；jar 损坏时跑 `fetch-legacy-adapter.sh` 恢复）
- 升级检查点：**升 Nacos** → 确认 adapter 有对应版本；**升 seata** → 看 nacos-client 是否已升 3.x 客户端，是则删插件与挂载
- seata-server 2.6.0 镜像无 servlet 容器（`web-application-type: none`），TC 控制台不可用，不要映射 7091

### 网络模式（host vs bridge）
- dev 用 compose 默认 bridge（服务名互访，`nacos:8848`）；prod 全栈 host（`127.0.0.1:8848`）
- 差异由编排各自写死的 `NACOS_SERVER_ADDR` 容器变量吸收（dev/prod 共用一份 seata 配置），**不建议**把 prod 改成 bridge：upstream 全家桶（30+ 服务）按 host 设计，单独改某服务会连不上 host 网络里的 Nacos；整体迁移收益低、回归成本高。dev 也不建议改 host：后端服务跑在 Windows 宿主 IDEA 里，bridge + 端口映射才是这层混合拓扑的标准解法

### 其他
- `import-nacos-config.sh` 幂等覆盖导入，修改 `script/config/nacos/` 下任何模板后需重跑
- 本地重建 dev 环境：`docker compose -f docker-compose.dev.yml down -v`（会清数据卷，业务数据先备份）
