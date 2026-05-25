#!/usr/bin/env python3
import os
import sys
import time
import subprocess
import urllib.request
import argparse

# ================= 配置区 =================
DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 8787

# ‼️请在此处填入：通过 nous_agent.sh 安装的 hermes-agent 核心源码的【绝对路径】
# 它应该包含 `run_agent.py` 文件（例如：/root/Hermes-Agent 或 /root/.hermes/hermes-agent）
AGENT_DIR = "/root/Hermes-Agent" 
# =========================================

def info(msg: str):
    print(f"[*] {msg}")

def error(msg: str):
    print(f"[-] ERROR: {msg}", file=sys.stderr)
    sys.exit(1)

def ensure_dependencies():
    """ 确保当前 Ubuntu 环境安装了 WebUI 运行所需的 Python 包 """
    info("Checking and installing WebUI Python dependencies...")
    try:
        # 直接使用当前环境的 pip 安装，确保环境统一
        subprocess.run([sys.executable, "-m", "pip", "install", "-r", "requirements.txt"], check=True)
    except subprocess.CalledProcessError:
        error("Failed to install dependencies from requirements.txt. Please run 'pip install -r requirements.txt' manually.")

def wait_for_health(url: str, timeout: float = 30.0) -> bool:
    """ 轮询等待 WebUI 服务上线 """
    deadline = time.time() + timeout
    info(f"Waiting for WebUI to become healthy at {url}...")
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(url, timeout=2) as response:
                if b'"status": "ok"' in response.read():
                    return True
        except Exception:
            time.sleep(0.5)
    return False

def start_server(host: str, port: int):
    """ 注入 PYTHONPATH 并拉起 WebUI 后端 """
    server_script = "server.py" # 确保你在 hermes-webui 目录下执行此脚本
    if not os.path.exists(server_script):
        error(f"Cannot find {server_script}. Please run this bootstrap script from the hermes-webui root directory.")

    # 关键改造：强行注入你的安卓版 Agent 路径到 Python 寻路环境中
    env = os.environ.copy()
    env["PYTHONPATH"] = f"{AGENT_DIR}:{env.get('PYTHONPATH', '')}"
    # 既然在 Termux/Proot 里，就没必要走 Onboarding 重新配置底层的端口或核心了
    env["HERMES_WEBUI_SKIP_ONBOARDING"] = "1" 

    cmd = [sys.executable, server_script, "--host", host, "--port", str(port)]
    info(f"Starting WebUI server: {' '.join(cmd)}")
    
    # 异步非阻塞启动
    proc = subprocess.Popen(cmd, env=env)
    return proc

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default=DEFAULT_HOST)
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    args = parser.parse_args()

    # 1. 补全 WebUI 自身依赖
    ensure_dependencies()

    # 2. 启动服务
    proc = start_server(args.host, args.port)

    # 3. 健康检查
    health_url = f"http://{args.host}:{args.port}/health"
    if wait_for_health(health_url):
        print("\n" + "="*50)
        print(" 🎉 Hermes WebUI 已经在 Termux Proot 中成功启动！")
        print(f" 👉 请在手机/电脑浏览器中访问: http://localhost:{args.port}")
        print("="*50 + "\n")
    else:
        error("WebUI server started but failed health check.")

    # 保持主进程不退出，等待子进程
    try:
        proc.wait()
    except KeyboardInterrupt:
        info("Shutting down server...")
        proc.terminate()

if __name__ == "__main__":
    main()