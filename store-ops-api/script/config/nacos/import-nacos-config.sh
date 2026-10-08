#!/bin/bash
# ============================================================
# store-ops 自建脚本: 批量导入 Nacos 配置(2.6.2)
# 将本目录全部 *.yml / *.properties 上传到 Nacos:
#   namespace = dev(与客户端 ${spring.profiles.active} 一致)
#   group     = DEFAULT_GROUP
#   dataId    = 文件名
# 幂等: 重复执行覆盖同 dataId
# 用法(Nacos 起来后执行): ./import-nacos-config.sh
# 可用环境变量覆盖默认值: NACOS_SERVER / NACOS_USERNAME / NACOS_PASSWORD / NAMESPACE / GROUP
# ============================================================
set -euo pipefail

NACOS_SERVER="${NACOS_SERVER:-127.0.0.1:8848}"
NACOS_USERNAME="${NACOS_USERNAME:-nacos}"
NACOS_PASSWORD="${NACOS_PASSWORD:-nacos}"
NAMESPACE="${NAMESPACE:-dev}"
GROUP="${GROUP:-DEFAULT_GROUP}"

echo ">>> 登录 Nacos http://${NACOS_SERVER} ..."
TOKEN=$(curl -sf -X POST "http://${NACOS_SERVER}/nacos/v1/auth/login" \
  -d "username=${NACOS_USERNAME}&password=${NACOS_PASSWORD}" \
  | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
if [ -z "${TOKEN}" ]; then
  echo "!! 登录失败(检查 Nacos 是否就绪 / 凭据是否为 ${NACOS_USERNAME})" >&2
  exit 1
fi
echo "    登录成功"

echo ">>> 确保 namespace [${NAMESPACE}] 存在 ..."
# 已存在时接口报错,不阻断
curl -sf -X POST "http://${NACOS_SERVER}/nacos/v1/console/namespaces" \
  -d "customNamespaceId=${NAMESPACE}&namespaceName=${NAMESPACE}&namespaceDesc=store-ops ${NAMESPACE}" \
  >/dev/null || echo "    (namespace 已存在,跳过创建)"

cd "$(dirname "$0")"

ok=0; fail=0
for f in *.yml *.properties; do
  [ -e "$f" ] || continue
  case "$f" in
    *.yml)        TYPE=yaml ;;
    *.properties) TYPE=properties ;;
  esac
  RES=$(curl -sf -X POST "http://${NACOS_SERVER}/nacos/v1/cs/configs" \
    --data-urlencode "dataId=${f}" \
    --data-urlencode "group=${GROUP}" \
    --data-urlencode "tenant=${NAMESPACE}" \
    --data-urlencode "type=${TYPE}" \
    --data-urlencode "accessToken=${TOKEN}" \
    --data-urlencode "content@${f}") || RES="request-error"
  if [ "${RES}" = "true" ]; then
    echo "    [OK]   ${f}"
    ok=$((ok+1))
  else
    echo "    [FAIL] ${f} -> ${RES}"
    fail=$((fail+1))
  fi
done

echo ">>> 导入完成: 成功 ${ok} 个, 失败 ${fail} 个 (namespace=${NAMESPACE}, group=${GROUP})"
[ "${fail}" -eq 0 ]
