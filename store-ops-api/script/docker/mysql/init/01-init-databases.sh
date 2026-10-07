#!/bin/bash
# ============================================================
# store-ops dev 编排 MySQL 自动初始化脚本
# 由 mysql 官方镜像 docker-entrypoint-initdb.d 机制在数据卷首次创建时执行
# 官方 SQL(script/sql/*.sql)不含 CREATE DATABASE,此脚本先建库再逐一导入
# 重置方式: docker compose -p store-ops-dev -f docker-compose.dev.yml down -v 后重新 up
# ============================================================
set -e

echo ">>> [store-ops] creating databases..."
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<'EOSQL'
CREATE DATABASE IF NOT EXISTS `ry-cloud`    DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS `ry-config`   DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS `ry-job`      DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS `ry-workflow` DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS `ry-seata`    DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
EOSQL

echo ">>> [store-ops] importing ry-cloud.sql ..."
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" --default-character-set=utf8mb4 ry-cloud    < /sql/ry-cloud.sql
echo ">>> [store-ops] importing ry-config.sql ..."
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" --default-character-set=utf8mb4 ry-config   < /sql/ry-config.sql
echo ">>> [store-ops] importing ry-job.sql ..."
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" --default-character-set=utf8mb4 ry-job      < /sql/ry-job.sql
echo ">>> [store-ops] importing ry-workflow.sql ..."
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" --default-character-set=utf8mb4 ry-workflow < /sql/ry-workflow.sql
echo ">>> [store-ops] importing ry-seata.sql ..."
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" --default-character-set=utf8mb4 ry-seata    < /sql/ry-seata.sql

echo ">>> [store-ops] databases initialized: ry-cloud / ry-config / ry-job / ry-workflow / ry-seata"
