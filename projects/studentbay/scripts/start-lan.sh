#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"

export DB_USERNAME="${DB_USERNAME:-studentbay}"
export SERVER_ADDRESS="${SERVER_ADDRESS:-0.0.0.0}"
export SERVER_PORT="${SERVER_PORT:-8080}"

if [[ -z "${DB_PASSWORD:-}" ]]; then
    read -r -s -p "请输入 StudentBay 的 MySQL 密码: " DB_PASSWORD
    printf '\n'
    export DB_PASSWORD
fi

lan_ip="$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || true)"
if [[ -z "$lan_ip" ]]; then
    printf '未检测到 Wi-Fi 局域网地址，请先连接校园网。\n' >&2
    exit 1
fi

printf '正在构建 StudentBay...\n'
./mvnw -DskipTests package

jar_path="$(find target -maxdepth 1 -type f -name 'studentbay-*.jar' ! -name '*.original' -print -quit)"
if [[ -z "$jar_path" ]]; then
    printf '构建完成，但未找到可运行的 StudentBay jar。\n' >&2
    exit 1
fi

printf '\nStudentBay 校园网访问地址: http://%s:%s\n' "$lan_ip" "$SERVER_PORT"
printf '保持此终端窗口、MySQL 和 Mac 处于运行状态。按 Control-C 停止服务。\n\n'

exec java -jar "$jar_path"
