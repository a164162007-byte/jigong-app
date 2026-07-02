#!/bin/sh
set -e

# 自动创建数据和备份目录
mkdir -p /app/data /app/backups

# 执行传入的启动命令
exec "$@"
