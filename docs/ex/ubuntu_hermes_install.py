#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
⚕ Hermes Agent - Pure Ubuntu Automated Installer
Hermes 智能体 - 专属 Ubuntu 自动化安装部署程序

This installer is a zero-dependency Python script designed specifically for Ubuntu/Debian.
It automates system package provisioning, uv-managed Python 3.11 runtimes, sandboxed Node.js,
secure editable pip dependencies sync, Playwright browser tools setup, and Systemd integration.
"""

import os
import sys
import shutil
import subprocess
import urllib.request
import platform

# Color Constants / 终端色彩常量
RED = '\033[0;31m'
GREEN = '\033[0;32m'
YELLOW = '\033[0;33m'
BLUE = '\033[0;34m'
MAGENTA = '\033[0;35m'
CYAN = '\033[0;36m'
NC = '\033[0m'  # No Color
BOLD = '\033[1m'

def log_info(msg_zh, msg_en):
    print(f"{CYAN}→{NC} {msg_zh} / {msg_en}")

def log_success(msg_zh, msg_en):
    print(f"{GREEN}✓{NC} {msg_zh} / {msg_en}")

def log_warn(msg_zh, msg_en):
    print(f"{YELLOW}⚠{NC} {msg_zh} / {msg_en}")

def log_error(msg_zh, msg_en):
    print(f"{RED}✗{NC} {msg_zh} / {msg_en}")

def print_banner():
    banner = f"""{MAGENTA}{BOLD}
┌─────────────────────────────────────────────────────────┐
│         ⚕ Hermes Agent Ubuntu Installer                 │
│         ⚕ Hermes 智能体 Ubuntu 专属自动安装器                │
├─────────────────────────────────────────────────────────┤
│  Tailored specifically for Ubuntu / Debian systems      │
│  专为 Ubuntu / Debian 操作系统设计与优化                   │
└─────────────────────────────────────────────────────────┘{NC}"""
    print(banner)

def run_cmd(args, shell=False, check=True, capture=False):
    """Safe execution of shell/system commands / 安全执行系统命令"""
    try:
        if capture:
            res = subprocess.run(args, shell=shell, check=check, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
            return res.stdout.strip()
        else:
            subprocess.run(args, shell=shell, check=check)
            return True
    except subprocess.CalledProcessError as e:
        log_error(f"命令执行失败: {e}", f"Command failed: {e}")
        if capture:
            return ""
        sys.exit(1)

def check_ubuntu_env():
    """Ensure running on Ubuntu/Debian Linux / 验证运行环境是否为 Ubuntu"""
    log_info("正在扫描系统运行环境与兼容性...", "Scanning target environment and compatibility...")
    
    if platform.system() != "Linux":
        log_error("本安装程序仅支持 Linux (Ubuntu/Debian) 系统", "This installer only supports Linux (Ubuntu/Debian) systems")
        sys.exit(1)
        
    is_debian_based = False
    if os.path.exists("/etc/os-release"):
        with open("/etc/os-release", "r") as f:
            content = f.read().lower()
            if "ubuntu" in content or "debian" in content:
                is_debian_based = True
                
    if not is_debian_based:
        log_warn("未检测到标准的 Ubuntu/Debian 系统发行版属性", "No standard Ubuntu/Debian release metadata found")
        sys.exit(1)
        
    log_success("验证通过: Linux (Ubuntu/Debian)", "Verification passed: Linux (Ubuntu/Debian)")

def install_system_dependencies():
    """Install required binary packages via apt / 使用 apt 安装基础工具链"""
    log_info("准备检查并补全 Ubuntu 系统依赖工具包...", "Checking and provisioning Ubuntu system packages...")
    
    # Configure Debian frontend to prevent interactive prompts blocking installation
    os.environ["DEBIAN_FRONTEND"] = "noninteractive"
    os.environ["NEEDRESTART_MODE"] = "a"
    
    # Detect privilege level
    is_root = os.getuid() == 0
    sudo_prefix = [] if is_root else ["sudo"]
    
    log_info("正在执行 apt-get update 更新软件源...", "Updating apt package list...")
    run_cmd(sudo_prefix + ["apt-get", "update", "-qq"])
    
    log_info("正在安装 gcc 构建链、Git、ripgrep 和 ffmpeg...", "Installing build-essential, git, ripgrep, and ffmpeg...")
    pkgs = ["build-essential", "python3-dev", "libffi-dev", "git", "ripgrep", "ffmpeg", "curl"]
    run_cmd(sudo_prefix + ["apt-get", "install", "-y", "-qq"] + pkgs)
    
    log_success("Ubuntu 系统依赖包安装完成", "Ubuntu system dependencies successfully installed")

def setup_node_and_uv(hermes_home):
    """Setup Astral UV and Sandboxed Node.js / 在沙盒中部署 UV 与 Node"""
    # Setup UV
    log_info("正在检查或自动安装 Astral uv 包管理器...", "Checking or installing Astral uv package manager...")
    uv_path = shutil.which("uv")
    if not uv_path:
        local_uv = os.path.expanduser("~/.local/bin/uv")
        if os.path.exists(local_uv):
            uv_path = local_uv
        else:
            log_info("正在下载 uv 安装脚本...", "Downloading uv installer...")
            urllib.request.urlretrieve("https://astral.sh/uv/install.sh", "/tmp/uv-install.sh")
            run_cmd(["sh", "/tmp/uv-install.sh"])
            uv_path = local_uv
            
    log_success(f"Astral uv 部署完成: {uv_path}", f"Astral uv is ready at: {uv_path}")
    
    # Setup Python 3.11 using uv
    log_info("正在通过 uv 寻址或自动拉取 Python 3.11 独立环境...", "Finding or downloading Python 3.11 environment via uv...")
    python_find = run_cmd([uv_path, "python", "find", "3.11"], capture=True)
    if not python_find:
        log_info("正在下载预编译 Python 3.11 运行底座...", "Downloading precompiled Python 3.11 runtime...")
        run_cmd([uv_path, "python", "install", "3.11"])
    
    # Setup Sandboxed Node.js
    log_info("正在校验 Node.js 开发环境...", "Verifying Node.js environment...")
    node_path = shutil.which("node")
    if not node_path:
        node_dir = os.path.join(hermes_home, "node")
        if os.path.exists(os.path.join(node_dir, "bin/node")):
            node_path = os.path.join(node_dir, "bin/node")
        else:
            log_info("未检测到系统 Node.js，准备在沙盒中闭环部署 Node.js v22...", "No system Node.js found, auto-deploying Node.js v22 sandbox...")
            arch = platform.machine()
            node_arch = "x64" if arch in ["x86_64", "AMD64"] else "arm64" if arch in ["aarch64", "arm64"] else None
            if not node_arch:
                log_warn("架构不支持 Node.js 自动下载，跳过 Node 自动部署", "Unsupported CPU architecture for Node auto-deploy, skip node...")
                return uv_path
                
            # Download and extract
            url = f"https://nodejs.org/dist/latest-v22.x/node-v22.11.0-linux-{node_arch}.tar.xz"
            tmp_tar = "/tmp/node.tar.xz"
            log_info(f"下载 Node 二进制包: {url}", f"Downloading Node binary: {url}")
            urllib.request.urlretrieve(url, tmp_tar)
            
            os.makedirs(node_dir, exist_ok=True)
            log_info("正在解包 Node 到 ~/.hermes/node ...", "Extracting Node to ~/.hermes/node ...")
            run_cmd(["tar", "xf", tmp_tar, "-C", node_dir, "--strip-components=1"])
            
            # Symlink binaries to ~/.local/bin
            local_bin = os.path.expanduser("~/.local/bin")
            os.makedirs(local_bin, exist_ok=True)
            for bin_name in ["node", "npm", "npx"]:
                src = os.path.join(node_dir, "bin", bin_name)
                dst = os.path.join(local_bin, bin_name)
                if os.path.exists(dst) or os.path.islink(dst):
                    os.unlink(dst)
                os.symlink(src, dst)
            node_path = os.path.join(node_dir, "bin/node")
            
    log_success(f"Node.js 环境就绪: {node_path}", f"Node.js is ready at: {node_path}")
    return uv_path

def clone_and_build_venv(install_dir, uv_path):
    """Git clone and python venv creation / 克隆代码并构建虚拟运行环境"""
    log_info(f"准备下载与部署源码至: {install_dir}", f"Preparing download and repository deploy at: {install_dir}")
    
    if os.path.exists(install_dir):
        log_info("检测到已有安装文件夹，正在执行 Git 拉取更新...", "Existing directory detected, pulling latest git updates...")
        os.chdir(install_dir)
        run_cmd(["git", "fetch", "origin"])
        run_cmd(["git", "checkout", "main"])
        run_cmd(["git", "pull", "--ff-only", "origin", "main"])
    else:
        log_info("正在克隆 Hermes Agent 官方主仓库...", "Cloning Hermes Agent repository...")
        run_cmd(["git", "clone", "--branch", "main", "https://github.com/NousResearch/hermes-agent.git", install_dir])
        
    os.chdir(install_dir)
    
    # Create Python Venv
    log_info("正在通过 uv 构建隔离的 Python 3.11 虚拟运行环境...", "Building isolated Python 3.11 virtual environment via uv...")
    if os.path.exists("venv"):
        shutil.rmtree("venv")
    run_cmd([uv_path, "venv", "venv", "--python", "3.11"])
    log_success("Ubuntu UV 虚拟环境部署就绪", "Ubuntu UV virtual environment is successfully ready")

def install_python_dependencies(install_dir, uv_path):
    """Install dependencies in venv with safety verification / 强安全性同步依赖包"""
    log_info("开始安装 Python 三方库依赖与环境编译组装...", "Installing Python dependencies and compiling packages...")
    
    os.environ["VIRTUAL_ENV"] = os.path.join(install_dir, "venv")
    
    if os.path.exists("uv.lock"):
        log_info("发现锁定依赖文件: 正在启用 Tier-0 Hash 安全一致性同步...", "uv.lock found: enabling Tier-0 hash safety sync...")
        run_cmd([uv_path, "sync", "--extra", "all", "--locked"])
    else:
        run_cmd([uv_path, "pip", "install", "-e", ".[all]"])
        
    # Playwright auto download and setup
    log_info("正在下载与安装 Playwright 网页自动化内核...", "Downloading and installing Playwright web automation browser...")
    venv_npx = os.path.expanduser("~/.local/bin/npx") if os.path.exists(os.path.expanduser("~/.local/bin/npx")) else "npx"
    
    is_root = os.getuid() == 0
    sudo_prefix = [] if is_root else ["sudo"]
    
    run_cmd([venv_npx, "playwright", "install", "chromium"])
    try:
        log_info("正在补齐 Playwright 系统音视频及渲染依赖库...", "Provisioning Playwright system rendering libraries...")
        run_cmd(sudo_prefix + [venv_npx, "playwright", "install-deps", "chromium"])
    except Exception:
        log_warn("系统渲染库安装跳过，请由管理员手动补齐", "Skip system libraries provisioning, let admin complete manually")
        
    log_success("Python 所有核心依赖库与网页引擎安装成功", "All Python core libraries and browser engines successfully installed")

def setup_launcher_and_configs(install_dir, hermes_home):
    """Setup configs, folder structs and Global Commands / 初始化配置文件与命令包装器"""
    log_info("开始初始化本地运行文件夹结构与配置...", "Initializing local database and configurations...")
    
    # Create directories
    for sub in ["cron", "sessions", "logs", "pairing", "hooks", "image_cache", "audio_cache", "memories", "skills"]:
        os.makedirs(os.path.join(hermes_home, sub), exist_ok=True)
        
    # Templates copy
    env_file = os.path.join(hermes_home, ".env")
    if not os.path.exists(env_file):
        ex_env = os.path.join(install_dir, ".env.example")
        if os.path.exists(ex_env):
            shutil.copy(ex_env, env_file)
            log_success("已成功从模板生成密钥配置文件: ~/.hermes/.env", "Created key config from example: ~/.hermes/.env")
        else:
            open(env_file, "w").close()
    os.chmod(env_file, 0o600)  # Restrict permissions
    
    yaml_config = os.path.join(hermes_home, "config.yaml")
    if not os.path.exists(yaml_config):
        ex_yaml = os.path.join(install_dir, "cli-config.yaml.example")
        if os.path.exists(ex_yaml):
            shutil.copy(ex_yaml, yaml_config)
            log_success("已成功从模板生成主配置文件: ~/.hermes/config.yaml", "Created yaml config from example: ~/.hermes/config.yaml")

    # Command wrapper generator
    bin_dir = os.path.expanduser("~/.local/bin")
    os.makedirs(bin_dir, exist_ok=True)
    
    launcher_path = os.path.join(bin_dir, "hermes")
    if os.path.exists(launcher_path) or os.path.islink(launcher_path):
        os.unlink(launcher_path)
        
    wrapper_content = f"""#!/bin/sh
unset PYTHONPATH
unset PYTHONHOME
exec "{os.path.join(install_dir, 'venv/bin/hermes')}" "$@"
"""
    with open(launcher_path, "w") as f:
        f.write(wrapper_content)
    os.chmod(launcher_path, 0o755)
    
    log_success(f"已向全局注入命令包装器: {launcher_path}", f"Command wrapper injected: {launcher_path}")
    
    # Sync skills
    log_info("正在同步预置 Agent 核心工具包与技能树...", "Syncing bundled Agent core toolkits and skills...")
    try:
        run_cmd([os.path.join(install_dir, "venv/bin/python"), os.path.join(install_dir, "tools/skills_sync.py")])
        log_success("智能体核心插件技能树同步就绪", "Agent core plugins and skills tree synced successfully")
    except Exception:
        log_warn("技能树快捷同步失败，跳过...", "Skip skills tree fast sync...")

def register_gateway_systemd(install_dir):
    """Register Systemd Service for boot persistent daemon / 将网关配置为开机自启守护进程"""
    log_info("正在准备拉起网关守护进程与系统保活...", "Preparing gateway daemon registration and system service...")
    
    venv_hermes = os.path.join(install_dir, "venv/bin/hermes")
    
    if shutil.which("systemctl"):
        log_info("正在将网关注册为 Systemd 系统服务...", "Registering gateway as standard Systemd system service...")
        try:
            # Runs 'hermes gateway install' which auto-generates systemd config at /etc/systemd/system/hermes.service
            run_cmd([venv_hermes, "gateway", "install"])
            run_cmd([venv_hermes, "gateway", "start"])
            log_success("Hermes 网关 Systemd 系统级开机自启服务注册并拉起成功！", "Hermes Gateway system service registered and launched successfully!")
            return
        except Exception as e:
            log_warn(f"Systemd 服务注册受限，将降级退守后台挂起保活模式: {e}", f"Systemd register restricted, falling back to background nohup: {e}")
            
    # Nohup fallback
    log_info("正在通过 nohup 在后台异步挂起保活网关...", "Spawning background gateway via nohup...")
    log_file = os.path.expanduser("~/.hermes/logs/gateway.log")
    
    os.makedirs(os.path.dirname(log_file), exist_ok=True)
    subprocess.Popen(
        f"nohup {venv_hermes} gateway > {log_file} 2>&1 &",
        shell=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        start_new_session=True
    )
    log_success("网关已成功在后台挂起运行 (可执行 'hermes' 终端进行聊天)", 
                "Gateway successfully spawned in background (run 'hermes' to chat)")

def main():
    print_banner()
    
    check_ubuntu_env()
    
    hermes_home = os.path.expanduser("~/.hermes")
    install_dir = os.path.join(hermes_home, "hermes-agent")
    
    # Run Ubuntu-focused installation sequence / 顺序调度 Ubuntu 专属安装流程
    install_system_dependencies()
    uv_path = setup_node_and_uv(hermes_home)
    clone_and_build_venv(install_dir, uv_path)
    install_python_dependencies(install_dir, uv_path)
    setup_launcher_and_configs(install_dir, hermes_home)
    register_gateway_systemd(install_dir)
    
    success_msg = f"""
{GREEN}{BOLD}┌─────────────────────────────────────────────────────────┐
│           ✓ Installation & Deploy Complete!             │
│           ✓ Hermes 智能体专属 Ubuntu 安装部署顺利完成!          │
└─────────────────────────────────────────────────────────┘{NC}

🚀 {CYAN}可用全局指令 / Commands:{NC}
   {GREEN}hermes{NC}              进入终端智能对话交互 / Start chatting
   {GREEN}hermes setup{NC}        配置模型/密钥及设置 / Configure model keys & wizard
   {GREEN}hermes gateway{NC}      管理网关后台平台机器人 / Manage background gateway
   {GREEN}hermes update{NC}       一键静默更新至最新版 / Update to latest version
"""
    print(success_msg)

if __name__ == "__main__":
    main()
