# Docker 镜像构建与开发环境启动指南

> 适用环境：Windows + Docker Desktop + Git Bash，项目 `store-ops-api`（RuoYi-Cloud-Plus 2.6.2，JDK 21）。  
> 本指南覆盖：本地构建自建镜像 → 起中间件栈（dev 编排）→ 导入 Nacos 配置 → 启动后端服务 → 全链路验证。  
> IDEA 内启动服务的方式见同目录《idea-启动调试指南.md》；两者共用中间件栈，不冲突。

## 0. 总览

```
阶段 1  准备 .env                （首次，1 分钟）
阶段 2  Maven 打包 + 构建镜像      （首次/依赖变更时，产出 fat jar + 2 个自建镜像）
阶段 3  起中间件五件套             （docker compose dev 编排，含 mysql 自动建库导 SQL）
阶段 4  导入 Nacos 配置            （首次，13 个配置文件）
阶段 5  启动后端服务               （gateway + auth + system）
阶段 6  验证全链路                 （curl 租户列表接口）
```

**为什么有自建镜像**：官方编排引用的 `ruoyi/ruoyi-nacos:2.6.2`、`ruoyi/ruoyi-seata-server:2.6.2` 不在 Docker Hub——它们是本仓库 `ruoyi-visual/` 下的 Java 模块（nacos 内嵌 2.5.1 发行 jar + actuator 加固、seata 2.6.0），打成 Spring Boot fat jar 再 `docker build` 而来。Dockerfile 与 jar 依赖全部在仓库内，**不依赖外网拉取**（仅基础镜像需拉一次）。

## 1. 准备 .env（首次）

```bash
cd /c/Users/jsdawn/Codes/java/store-ops/store-ops-api/script/docker
cp .env.example .env
```

编辑 `.env`，**必改项**：

- `SEATA_IP`：宿主机真实局域网 IP（`ipconfig` 查，形如 `192.168.x.x`，**不能是 127.x**）——seata 容器向 Nacos 注册的对外地址，宿主 IDEA 里的微服务要凭它连 TC；127.0.0.1 在 seata 侧是 FORBIDDEN 黑名单地址，会被静默拒绝

密码三项默认值（MYSQL_ROOT_PASSWORD / REDIS_PASSWORD / MINIO\_*）与 Nacos 配置模板一致，本地开发可不改。

## 2. Maven 打包 + 构建镜像

### 2.1 全量打包

```bash
cd /c/Users/jsdawn/Codes/java/store-ops/store-ops-api
export JAVA_HOME=/c/Users/jsdawn/MyApp/JavaEnv/jdk-21.0.12.1
export PATH="$JAVA_HOME/bin:$PATH"

./mvnw clean package -DskipTests
```

一次构建同时产出：ruoyi-nacos / ruoyi-seata-server 的**镜像 jar** + gateway/auth/system 的**运行 jar**（阶段 5 用）。

**两个必知坑**：

1. **upstream 2.X 分支不带 mvnw wrapper**（mvnw / mvnw.cmd / .mvn/ 三件套）。本仓库的 wrapper 是从 6.X 分支补回的（wrapper 3.3.4 + Maven 3.9.12，华为云镜像）。若 `./mvnw: No such file or directory`，说明 wrapper 丢失，参照 git 历史找回，勿用系统 mvn（本机未装独立 Maven）。
2. **IDEA 的 Build/Rebuild Project 不产 jar**——它只编译到 `target/classes`；fat jar 是 `spring-boot-maven-plugin` 在 Maven **package** 阶段 repackage 出来的。要 jar 就必须跑 `./mvnw package`（或 IDEA Maven 工具窗 → Lifecycle → package），IDE 构建按钮无效。**不要省 `clean`**，IDE 产物与 javac 产物混存有污染风险。

### 2.2 构建两个自建镜像

```bash
docker build -t ruoyi/ruoyi-nacos:2.6.2        ruoyi-visual/ruoyi-nacos/
docker build -t ruoyi/ruoyi-seata-server:2.6.2 ruoyi-visual/ruoyi-seata-server/

docker images | grep ruoyi   # 验证两个 2.6.2 镜像在列
```

> 基础镜像 `bellsoft/liberica-openjdk-rocky` 若拉取超时（Docker Hub 直连被墙）：Docker Desktop → Settings → Resources → Proxies 配 `http://127.0.0.1:7897`（需 Clash 运行中）。

镜像一旦构建好长期复用，只在 nacos/seata 模块源码变更时重建。

## 3. 起中间件五件套（dev 编排）

```bash
cd script/docker
docker compose -f docker-compose.dev.yml up -d
docker compose -f docker-compose.dev.yml ps     # 验证：5 个容器全部 Up
```

五件套：mysql 8.0.42 / nacos（自建镜像，8848）/ redis 7.2.8 / minio（9000+9001）/ seata-server（自建镜像，file 存储模式）。

**mysql 首次启动自动建库导 SQL**（docker-entrypoint-initdb.d 机制，仅数据卷首次创建时执行一次）：

```bash
docker compose -f docker-compose.dev.yml logs -f mysql
# 末行出现即成功（Ctrl+C 退出）：
#   >>> [store-ops] databases initialized: ry-cloud / ry-config / ry-job / ry-workflow
```

逐项验证：

| 服务    | 验证方式                                                                           |
| ----- | ------------------------------------------------------------------------------ |
| Nacos | 浏览器 <http://localhost:8848/nacos> ，登录 nacos / nacos                            |
| MinIO | <http://localhost:9001> ，登录 ruoyi / ruoyi123（或 .env 里的 MINIO_ROOT_PASSWORD）    |
| Redis | `docker exec store-ops-dev-redis-1 redis-cli -a "$REDIS_PASSWORD" ping` → PONG |
| MySQL | 日志出现 `ready for connections`                                                   |
| Seata | 日志出现注册 Nacos 成功；Nacos 控制台 → 服务列表 → dev 命名空间可见 ruoyi-seata-server               |

> nacos 配了失败自动重试（depends_on 健康检查 + restart 策略），mysql 就绪后它会自己爬起来，首启等 1~2 分钟属正常。

## 4. 导入 Nacos 配置（首次）

官方 `ry-config.sql` 只含表结构和内置用户，**不含 ruoyi 配置数据**——13 个配置文件（application-common.yml / datasource.yml / seata-server.properties 等）需导入：

```bash
cd script/config/nacos
./import-nacos-config.sh
# 预期：13 个文件全部 [OK]（namespace=dev / DEFAULT_GROUP，幂等可重复执行）
```

**必知坑**：Nacos 2.x 的 Tomcat context path 是 `/nacos`，API 路径必须是 `/nacos/v1/...`（写 `/v1/...` 直接 404，curl -f 退出码 22）。脚本已内置正确前缀。

导入后到控制台确认：<http://localhost:8848/nacos> → 左下角命名空间切到 **dev** → 配置列表 13 条。

配置模板已全量变量化（密码为 `${VAR}` 占位符），修改模板后重跑脚本覆盖即可。

## 5. 启动后端服务

开发期只起 **gateway + auth + system（+ 当前开发模块）**，不全量起。

### 5.1 IDEA 启动方式（推荐）

日常开发推荐用 IDEA 启动（EnvFile 插件自动注入），见《idea-启动调试指南.md》。

### 5.2 命令行方式（3 个终端，**每个终端**先执行前置）：

```bash
cd /c/Users/jsdawn/Codes/java/store-ops/store-ops-api
export JAVA_HOME=/c/Users/jsdawn/MyApp/JavaEnv/jdk-21.0.12.1
export PATH="$JAVA_HOME/bin:$PATH"
set -a; source script/docker/.env; set +a    # 注入 MYSQL_ROOT_PASSWORD 等占位符变量
```

三个终端分别：

```bash
java -jar ruoyi-modules/ruoyi-system/target/ruoyi-system.jar   # 终端1（9201）
java -jar ruoyi-auth/target/ruoyi-auth.jar                     # 终端2（9210）
java -jar ruoyi-gateway/target/ruoyi-gateway.jar               # 终端3（8080）
```

> `set -a; source .env` 不可省：Nacos 里 `datasource.yml` 等配置的密码是 `${MYSQL_ROOT_PASSWORD}` 占位符，由微服务进程在运行期从环境变量解析；缺失时启动直接失败（fail fast，符合约定）。  
> 日常开发推荐改用 IDEA 启动（EnvFile 插件自动注入），见《idea-启动调试指南.md》。

## 6. 验证全链路

1. Nacos 控制台 → 服务列表 → **dev 命名空间**：ruoyi-system / ruoyi-auth / ruoyi-gateway 三个健康实例
2. 一条 curl 打穿「网关 → 认证 → 租户」（多租户基线的标志接口）：

```bash
curl -s http://localhost:8080/auth/tenant/list
# 返回 "code":200 + 租户列表 JSON 即全链路通
```

3.（可选）管理台界面：`cd store-ops-admin && npm run dev`，登录页应出现**租户选择器**（2.6.2 基线多租户回归的标志性确认点）。

## 7. 排障速查表（首跑实测沉淀）

| 症状                                                        | 根因与处置                                                                                                                                                      |
| --------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `docker build` 报 `ADD ./target/ruoyi-nacos.jar not found` | 没跑过 `./mvnw package`（IDEA Build 不产 jar）。补全量打包后再 build                                                                                                      |
| `bash: ./mvnw: No such file or directory`                 | 2.X 分支本无 wrapper，三件套丢失。从 git 历史找回 mvnw / mvnw.cmd / .mvn/wrapper/                                                                                          |
| mysql 日志 `No such file or directory: /sql/ry-cloud.sql`   | init 挂载源路径错（应为 `../sql`，指向 `script/sql`）。已修复；若复现检查 compose 的 volumes                                                                                       |
| redis `FATAL CONFIG FILE ERROR ... dir /redis/data`       | conf 的 `dir` 在解析期即时 chdir 校验，CLI 参数救不了。conf 统一写 `dir /data`，卷挂镜像自带 `/data`（redis 用户属主，避免 Permission denied）                                                |
| nacos 崩溃循环，日志 `[db-load-error]`                           | **Nacos 自研 PropertyUtil 读 `db.url.0` 不解析 `${VAR}` 占位符**（seata 同款坑）。compose 已改 environment 注入 `DB_URL_0/DB_USER_0/DB_PASSWORD_0`（Spring 环境变量属性映射，优先级高于配置文件） |
| import 脚本登录 404 / curl exit 22                            | API 路径少 `/nacos` context path 前缀（Nacos 3.x 没有前缀，惯性坑）                                                                                                       |
| 微服务启动报 `${MYSQL_ROOT_PASSWORD}` 相关失败                      | 终端没 `set -a; source script/docker/.env; set +a`；IDEA 侧检查 EnvFile 配置                                                                                        |
| seata 客户端连不上 TC                                           | `.env` 的 `SEATA_IP` 必须是宿主真实局域网 IP（不能 127.x；seata 对 127.0.0.1 静默拒绝注册）                                                                                       |

**重置开发环境**（清库重来，危险操作先确认）：

```bash
cd script/docker
docker compose -f docker-compose.dev.yml down -v   # 连数据卷一起清（库内数据全丢）
docker compose -f docker-compose.dev.yml up -d     # 重新走自动建库导 SQL
```

## 8. 与 prod 的关系

dev 编排仅中间件五件套（bridge 网络，微服务跑宿主 IDEA）；prod 编排对齐官方全栈 host 网络（14 服务）。两者的密钥统一走 `.env`、nacos/seata 覆盖机制一致（详见 `script/docker/README.md`）。prod 镜像分发走 `docker save/load` 离线导入（构建于本地、scp 传服务器），见 README「prod 部署」一节。
