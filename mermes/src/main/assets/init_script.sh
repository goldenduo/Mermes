#!/bin/bash
# init_script.sh
# 自动在后台启动本地 Web 控制台服务，监听 20265 端口

echo "[Init] Starting Mermes Local Web Service on port 20265..."

# 开启 Python 内置 Web 服务器用于测试加载本地控制台
python3 -m http.server 20265 > "$HOME/web_server.log" 2>&1 &

# 保存子进程 PID 以供外部控制或检测
echo $! > "$HOME/web_server.pid"

echo "[Init] Mermes Local Web Service successfully launched."
