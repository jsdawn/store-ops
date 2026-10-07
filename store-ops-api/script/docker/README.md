# script/docker 编排说明（RuoYi-Cloud-Plus 2.6.2 基线）

本目录提供三套编排资产：

| 文件 | 用途 |
| --- | --- |
| `docker-compose.yml` | **官方原版母本**（upstream 原样保留，host 网络 + `/docker` 绝对路径 + 烘焙密钥），仅作版本/配置/端口参照，不日常使用 |
| `docker-compose.dev.yml` | **dev 编排**（Windows 开发机：中间件五件套进容器，微服务宿主 IDEA 运行） |
| `docker-compose.prod.yml` | **prod 编排**（Linux 服务器：官方全栈 host 网络，密钥改走 `.env`） |

dev/prod 的镜像版本、配置、端口与官方编排完全一致，差异仅在：网络拓扑、密钥来源（`.env`）、dev 便利性增强（自动建库 / 失败自动重试）。

## 目录结构

```
script/docker/
├── docker-compose.yml          # 官方原版（只读参照）
├── docker-compose.dev.yml      # dev 编排（bridge 网络）
├── docker-compose.prod.yml     # prod 编排（host 网络）
├── .env.example                # 环境变量模板（复制为 .env 使用）
├── mysql/init/01-init-databases.sh   # 自建：dev 自动建库 + 导官方 SQL
├── nacos/
│   ├── conf/cluster.conf       # 官方原样（单机自指 127.0.0.1:8848）
│   └── override/application.properties  # 自建：覆盖镜像内 db 连接三键
├── seata/override/application.yml       # 自建：覆盖镜像内 registry/config 三键
└── redis/conf/redis.conf       # 官方原样（requirepass 由 compose 命令行参数覆盖）
```

## 一、镜像构建（首次必做）

`ruoyi/ruoyi-nacos:2.6.2` 与 `ruoyi/ruoyi-seata-server:2.6.2` **不在 Docker Hub**，官方即为本地构建（Dockerfile 在 `ruoyi-visual/ruoyi-nacos` 与 `ruoyi-visual/ruoyi-seata-server`，nacos server 内嵌 2.5.1、seata 2.6.0）。

```bash
# 1. Maven 打包（需 JDK 21 环境；构建期烘焙 @nacos.server@/@profiles.active@ 占位符，默认 dev profile）
export JAVA_HOME=/c/Users/jsdawn/MyApp/JavaEnv/jdk-21.0.12.1
cd store-ops-api
./mvnw -pl ruoyi-visual/ruoyi-nacos,ruoyi-visual/ruoyi-seata-server package -DskipTests

# 2. 构建镜像（基础镜像 bellsoft/liberica-openjdk-rocky:17.0.16-cds 首次需拉取）
docker build -t ruoyi/ruoyi-nacos:2.6.2        ruoyi-visual/ruoyi-nacos/
docker build -t ruoyi/ruoyi-seata-server:2.6.2 ruoyi-visual/ruoyi-seata-server/
```

> prod 还需其余业务镜像（`ruoyi-gateway/auth/system/...`），构建方式相同：
> `./mvnw package -DskipTests` 全量打包后，逐模块 `docker build -t ruoyi/ruoyi-<module>:2.6.2 <模块目录>/`。

## 二、.env 环境变量

```bash
cd script/docker
cp .env.example .env   # 然后按需修改
```

| 变量 | 说明 | dev | prod |
| --- | --- | --- | --- |
| `MYSQL_ROOT_PASSWORD` | root 密码，业务库/配置库/Seata 库/Nacos 配置库共用 | `ruoyi123` | 必须改强密码 |
| `REDIS_PASSWORD` | 经 `--requirepass` 命令行参数覆盖 redis.conf 烘焙值 | `ruoyi123` | 必须改强密码 |
| `MINIO_ROOT_USER/PASSWORD` | MinIO 管理台凭据 | `ruoyi/ruoyi123` | 必须改强密码 |
| `NACOS_USERNAME/PASSWORD` | Nacos 凭据（可选，默认 `nacos/nacos`） | 默认 | 建议修改并同步控制台改密 |
| `SEATA_IP` | TC 注册到 Nacos 的 IP（客户端经此连接 TC） | **必填**宿主机局域网 IP | 留空自动检测；云服务器建议显式填 |

修改 `MYSQL_ROOT_PASSWORD` / `MINIO_ROOT_PASSWORD` 后需 `down -v` 删卷重建（密码在首次初始化时写入）。

## 三、dev 编排（Windows 开发机）

**拓扑**：mysql / nacos / redis / minio / seata-server 五件套进容器（bridge 网络），gateway/auth/system 等微服务在宿主 IDEA 运行。

```bash
cd script/docker
docker compose -p store-ops-dev -f docker-compose.dev.yml up -d

# 首次启动 mysql 会自动建库并导入 script/sql/ 全部官方 SQL：
#   ry-cloud / ry-config / ry-job / ry-workflow / ry-seata
# 查看初始化日志: docker compose -p store-ops-dev logs mysql

# 停止（保留数据）: docker compose -p store-ops-dev -f docker-compose.dev.yml down
# 停止并清数据:     docker compose -p store-ops-dev -f docker-compose.dev.yml down -v
```

容器内互访走服务名（`mysql:3306` / `nacos:8848`），宿主 IDEA 微服务连 `localhost:3306|8848|6379|9000|8091`。

**bridge 网络的两处挂载覆盖**（自建文件，键级合并、其余配置继承镜像内官方值）：

- `nacos/override/application.properties` → `/ruoyi/nacos/config/application.properties`：db 地址改指服务名 `mysql`，密码接 `.env`
- `seata/override/application.yml` → `/ruoyi/seata-server/config/application.yml`：registry/config 的 server-addr 改指 `nacos:8848`，凭据接 `.env`

> 原理：Spring Boot 外部配置 `file:./config/` 优先级高于 jar 内 application.properties/yml，且同名文件间按**键级合并**（后读优先），因此覆盖文件只需写差异键。

**启动顺序**：nacos 与 seata-server 配置了 `restart: unless-stopped`，依赖服务（mysql/nacos）就绪前若启动失败会自动重试，一条命令全起即可。

**`SEATA_IP`（dev 必填）**：seata-server 在容器内绑定 0.0.0.0，但注册到 Nacos 的地址取 `SEATA_IP`；宿主 IDEA 里的微服务客户端必须能访问该地址，因此填宿主机局域网 IP（如 `192.168.3.21`），经端口映射 8091 进入容器。若不填会注册为容器内网 IP（172.x），客户端连不上 TC。

## 四、prod 编排（Linux 服务器）

**部署约定**（与官方一致）：将本目录内容上传至服务器 `/docker` 下（含 `.env`、`mysql/`、`nacos/`、`seata/`、`redis/`、`nginx/` 等子目录），挂载路径 `/docker/...` 与官方编排相同。

```bash
# 1. 准备
cd /docker
cp .env.example .env    # 填写 prod 强密码与 SEATA_IP
# 2. 上传/构建全部 ruoyi 镜像（见「镜像构建」），基础镜像官方源
# 3. 导库（官方为手动导库；如需自动建库可参照 dev 的 mysql/init 脚本自行启用挂载）
docker exec -i mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "CREATE DATABASE IF NOT EXISTS \`ry-cloud\` DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci; ..."
docker exec -i mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" --default-character-set=utf8mb4 ry-cloud  < <仓库>/script/sql/ry-cloud.sql
#   ry-config / ry-job / ry-workflow / ry-seata 同理
# 4. 分步启动（保证 nacos 先于 seata-server 就绪）
docker compose -f docker-compose.prod.yml up -d mysql nacos redis minio
#    待 nacos 就绪(8848 可访问)并在控制台导入 script/config/nacos/ 配置后：
docker compose -f docker-compose.prod.yml up -d
```

prod 与官方编排的**唯一差异**：密钥走 `.env`；nacos/seata 挂载 override 覆盖文件（host 网络下地址值与官方烘焙值相同 `127.0.0.1`，仅密码改为接 `.env`，保证强密码与镜像内烘焙 `root/root` 不脱节）。扩展服务（elk/rocketmq/rabbitmq/kafka/skywalking/prometheus/grafana 等）按需参照官方 `docker-compose.yml` 扩展段添加。

## 五、dev / prod / 官方差异对照表

| 项 | 官方 docker-compose.yml | dev | prod |
| --- | --- | --- | --- |
| 网络 | host | bridge + 端口映射 | host |
| 服务面 | 全栈（14 服务） | 中间件五件套 | 全栈（14 服务） |
| 挂载路径 | `/docker/...` 绝对路径 | 仓库相对路径 + 命名卷 | `/docker/...`（同官方） |
| 密钥 | 烘焙明文（ruoyi123 等） | `.env` | `.env` |
| nacos 配置库 | jar 内 `127.0.0.1:3306/root/root` | override → `mysql:3306` + `.env` 密码 | override → `127.0.0.1:3306` + `.env` 密码 |
| seata registry | jar 内 `127.0.0.1:8848/nacos/nacos` | override → `nacos:8848` + `.env` 凭据 | override → `127.0.0.1:8848` + `.env` 凭据 |
| redis 密码 | redis.conf 烘焙 `ruoyi123` | `--requirepass ${REDIS_PASSWORD}` | 同 dev |
| mysql 建库导表 | 手动 | `mysql/init` 脚本自动（首次） | 手动（命令见上） |
| 失败重试 | 无 | nacos/seata `restart: unless-stopped` | 无（分步启动） |
| `SEATA_IP` | 注释态（自动检测） | 必填宿主局域网 IP | 可选（自动检测/显式填） |

## 六、后续步骤（环境就绪后）

1. **Nacos 配置导入**：`script/config/nacos/` 下各 yml（datasource/application-common/gateway 等）导入 Nacos `dev` 命名空间（ry-config.sql 内置的是占位内容「将项目路径：config/下对应文件中内容复制到此处」，需替换为真实内容；密钥引用 `.env` 同源变量）。
2. **`seata-server.properties`**（Nacos config，data-id 同名）：vgroupMapping 保持官方；`store.mode=db` 时 dev 的 store.db url 需指向 `mysql:3306`（bridge），prod 指向 `127.0.0.1:3306`。
3. **启动微服务**：宿主 IDEA 按 `docs/development/idea-启动调试指南.md` 启动 gateway/auth/system/resource（seata 客户端 enabled 默认 false，按需开启）。
4. **管理台**：`store-ops-admin`（plus-ui 5.X）dev server，登录验证租户选择器。
