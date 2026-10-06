# IDEA 启动与调试服务指南

> 适用环境：IntelliJ IDEA Community 2024.3+，项目 `store-ops-api`（RuoYi-Cloud-Plus 6.0.0，JDK 21 + Spring Boot 4.1.1）。  
> 本指南取代"命令行 mvn 编译 + java -jar 启动"的旧流程：日常开发全程 IDEA，无需手动跑 Maven。

## 1. 首次配置

### 1.1 导入项目

1. `File → Open` 选择 `store-ops-api` 目录（项目根 = 该目录，后续相对路径都基于它）
2. 等待右下角 Maven 索引进度跑完（首次几分钟）
3. 弹窗提示 Lombok / annotation processing 时一律确认启用；未弹则检查：
   - `Settings → Plugins`：**Lombok** 已安装启用
   - `Settings → Build → Compiler → Annotation Processors`：勾选 **Enable annotation processing**

### 1.2 指定 JDK 21

`File → Project Structure`（Ctrl+Alt+Shift+S）→ Project → SDK 选 `21`（Temurin 21.0.12.1）；Language level 选 SDK default。

> 若黄色警告条提示 "JDK 与系统架构不匹配"，是 IDEA 读不到该 JDK 目录架构元数据的误报（实际 `os.arch=amd64`），无视即可。

### 1.3 安装 EnvFile 插件（凭据注入的关键）

`Settings → Plugins → Marketplace` 搜索 **EnvFile** → Install → 重启 IDEA。

项目凭据统一存放在 `script/docker/.env`（gitignored，不入库），服务启动所需的 `NACOS_PASSWORD`、`MYSQL_PASSWORD` 等变量全部从这里注入。**缺失变量时服务会当场报错退出（fail fast），不会带病运行。**

### 1.4 配置运行配置模板（新建配置自动继承）

`Run → Edit Configurations...` → 左下 **编辑配置模板...** → 选 **应用程序**：

- **缩短命令行**：先点右上 `修改选项(M)` 勾选该项出现下拉框，选 **`@argfile (Java 9+)`**  
  （本框架 classpath 巨长，Windows 32KB 命令行上限会直接报"命令行过长"，argfile 必配）
- **EnvFile 标签页**：勾选 `Enable EnvFile` → 点 `+` → `.env file` → 路径填 `script/docker/.env`（相对路径按项目根解析）→ 确认该行 Enabled 勾选

### 1.5 创建各服务启动配置

每个服务一份（后建的配置会自动继承模板，只需改名称和主类）：

1. 打开主类文件，点 `main` 方法旁的绿色三角 → **运行**，IDEA 自动生成"应用程序"配置
2. `Run → Edit Configurations...` 中改名为服务名，确认模板里的 @argfile / EnvFile 已继承

| 配置名            | 模块 classpath                 | 主类                                              | 端口                  |
| -------------- | ---------------------------- | ----------------------------------------------- | ------------------- |
| ruoyi-gateway  | ruoyi-gateway                | `org.dromara.gateway.RuoYiGatewayApplication`   | 8080                |
| ruoyi-auth     | ruoyi-auth                   | `org.dromara.auth.RuoYiAuthApplication`         | 9210                |
| ruoyi-system   | ruoyi-modules/ruoyi-system   | `org.dromara.system.RuoYiSystemApplication`     | 9201                |
| ruoyi-resource | ruoyi-modules/ruoyi-resource | `org.dromara.resource.RuoYiResourceApplication` | 9204                |
| （后续新模块照抄）      | ruoyi-modules/ruoyi-xxx      | `org.dromara.xxx.RuoYiXxxApplication`           | 见各自 application.yml |

> 注意区分：左侧"未知"分组里那批红叉的 ruoyi-* 配置来自仓库 `.run/` 目录，是官方 **Docker 镜像构建**配置（依赖 Docker 插件），不用于本地启动——无视即可，也不要删除。

## 2. 日常启动流程

### 2.1 启动哪些服务

按项目约定：**开发期只起 gateway + auth + system + 当前开发模块**，不要全量起。

| 依赖                    | 必须先启动                                                                      |
| --------------------- | -------------------------------------------------------------------------- |
| MySQL / Redis / Nacos | `docker compose -f script/docker/docker-compose.dev.yml up -d`（容器常驻，一般不用管） |
| 网关路由                  | ruoyi-gateway（8080，前端所有请求入口）                                               |
| 登录鉴权                  | ruoyi-auth（9210）                                                           |
| 用户/权限/菜单 RPC          | ruoyi-system（9201）                                                         |
| 消息推送(SSE)/文件          | ruoyi-resource（9204，不起则前端控制台报 503 `/resource/message`，登录本身不受影响）            |

### 2.2 启动与验证

1. 右上角下拉选配置 → 点**Debug图标**以 Debug 模式启动（推荐，可断点）
2. 控制台等待 `Started XxxApplication in xxx seconds`（logback 提示找不到 logstash/skylog 配置的 WARN 是预期噪音）
3. Nacos 控制台 `http://localhost:18080` → 服务列表确认实例已注册且健康
4. 前端：`store-ops-admin` 目录 `pnpm dev`，浏览器登录 admin/admin123 验证全链路

## 3. 调试

- **断点**：行号右侧点击打红点，Debug 模式下请求命中即挂起，可查看变量/调用栈
- **改代码后的常规循环**：改代码 → 点该服务的 Debug（重启）即可。IDEA 在 Run/Debug 前自动增量编译改动模块，**不需要手动跑 Maven**
- **条件断点**：右键断点可加条件表达式，多租户/循环场景强烈建议使用

## 4. 与 Maven 的关系

- IDEA 构建与 Maven 同为 javac 系，产物混存于各模块 `target/classes`，互相兼容、可交替构建
- **日常构建、启动全由 IDEA 托管**；只有以下场景需要命令行 `./mvnw clean package -DskipTests`：
  - 打可执行 jar 包（部署场景）
  - IDEA 构建出现异常需要"清干净重来"（先试 `Build → Rebuild Project`）
- Maven 面板 Profiles 保持 `dev` 勾选（默认激活）即可；`prod` 仅打生产镜像时使用，两者不要同时勾选

## 5. 常见问题排查

| 症状                                                | 原因与处理                                                                           |
| ------------------------------------------------- | ------------------------------------------------------------------------------- |
| 启动即退出，报 `NACOS_PASSWORD` 等变量缺失                    | EnvFile 没配或没勾 Enabled；检查运行配置的 EnvFile 标签页                                       |
| 报错"命令行过长"                                         | 该配置没设缩短命令行；改选 `@argfile (Java 9+)`（见 1.4）                                       |
| 登录报 `ConvertException: cannot find converter`     | 编译产物不一致。先 `Build → Rebuild Project`，仍不行再 `./mvnw clean package -DskipTests` 后重启 |
| 前端某接口 503 `Unable to find instance for ruoyi-xxx` | 对应服务没启动，起它即可（见 2.1 表）                                                           |
| 编辑器大量红线但能正常构建                                     | 等索引进度跑完；仍红则 `File → Invalidate Caches → Invalidate and Restart`                 |
| Rebuild 报 N 个警告                                   | 警告来自框架上游源码（deprecation 等），不处理不改上游                                               |
| 中文日志乱码（如"鏈嶅姟寮傚父"）                                 | Windows GBK 控制台解码问题，纯显示问题，IDEA 已配 UTF-8 无此困扰                                    |
