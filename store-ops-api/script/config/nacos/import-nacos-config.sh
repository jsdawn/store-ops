#!/usr/bin/env bash
# 导入 Nacos 配置模板到 public 命名空间 / DEFAULT_GROUP（v3 Open API）。
# 仓库内 *.yml 均为占位模板（${MYSQL_PASSWORD} 等），不含任何真实密钥；
# 真实凭据从 script/docker/.env 读取（gitignored）。
# 用法：bash import-nacos-config.sh
set -euo pipefail
cd "$(dirname "$0")"

# shellcheck disable=SC1091
set -a; source ../../docker/.env; set +a

BASE=http://localhost:8848/nacos
TOKEN=$(curl -s -X POST "$BASE/v3/auth/user/login" -d "username=${NACOS_USERNAME:-nacos}&password=${NACOS_PASSWORD}" \
  | sed 's/.*"accessToken":"\([^"]*\)".*/\1/')
if [ -z "$TOKEN" ]; then
  echo "登录失败：请检查 .env 的 NACOS_PASSWORD 与 Nacos 管理员密码是否一致" >&2
  exit 1
fi

FAIL=0
for f in *.yml; do
  r=$(curl -s -X POST "$BASE/v3/admin/cs/config" \
    --data-urlencode "dataId=$f" \
    --data-urlencode "groupName=DEFAULT_GROUP" \
    --data-urlencode "namespaceId=public" \
    --data-urlencode "type=yaml" \
    --data-urlencode "content@$f" \
    --data-urlencode "accessToken=$TOKEN")
  echo "$f -> $r"
  echo "$r" | grep -q '"code":0' || FAIL=1
done

if [ "$FAIL" -eq 0 ]; then
  echo "全部配置导入成功（seata-server.properties 未导入：本期不起 seata）"
else
  echo "存在导入失败的配置，见上方返回" >&2
  exit 2
fi
