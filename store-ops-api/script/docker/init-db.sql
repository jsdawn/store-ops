-- store-ops 开发库初始化：创建 4 个业务库
-- 库名与 RuoYi-Cloud-Plus 6.0.0 的 Nacos 数据源配置（datasource.yml）保持一致
-- 注：seata-server 采用官方默认 file 存储模式，无需 TC 存储库（ry-seata）
CREATE DATABASE IF NOT EXISTS `ry-cloud`    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS `ry-job`      DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS `ry-workflow` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS `ry-ai`       DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
