#!/usr/bin/env bash
# 下载 Nacos v1/v2 legacy API 适配插件（docker-compose.dev.yml 挂载至 nacos 容器 plugins 目录）。
# 背景：Nacos 3.2.0 起主发行版移除 v1/v2 HTTP API，而 seata 2.6.0 内嵌的 nacos-client 1.4.6 只走 v1
# （TC 拉取 seata-server.properties 与服务注册发现均依赖），必须装此适配器。
# jar 已随仓库入库（150KB，Apache-2.0，源自 nacos-group/nacos-api-legacy-adapter releases），
# 本脚本仅作文件损坏/换源时的恢复手段，日常无需执行。
# 需要代理时先 export https_proxy=http://127.0.0.1:7897
set -euo pipefail
cd "$(dirname "$0")"

JAR=nacos-plugins/nacos-api-legacy-adapter-3.2.0.jar
URL='https://github.com/nacos-group/nacos-api-legacy-adapter/releases/download/3.2.0.2/nacos-api-legacy-adapter-3.2.0.jar'

if [ -f "$JAR" ]; then
  echo "已存在：$JAR"
  exit 0
fi

mkdir -p nacos-plugins
curl -fL --retry 3 --retry-delay 2 -o "$JAR" "$URL"
echo "下载完成：$JAR ($(wc -c < "$JAR") bytes)"
