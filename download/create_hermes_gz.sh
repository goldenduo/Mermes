#!/data/data/com.mermes/files/usr/bin/bash

set -e

# Colors
RED='\033[0;31m'
GRN='\033[0;32m'
YLW='\033[1;33m'
CYN='\033[0;36m'
RST='\033[0m'

clear

echo -e "${CYN}=====================================================${RST}"
echo -e "${GRN}                126111.xyz"
echo -e "${CYN}=====================================================${RST}"
echo -e "${GRN}         ☤ HERMES AGENT MERMES GZ ☤"
echo -e "${CYN}=====================================================${RST}"


# Install Ubuntu (Check if already installed to avoid error)
if ! proot-distro list | grep -q "Installed: yes" | grep "ubuntu"; then
    proot-distro install ubuntu:24.04 --name hermes
fi

# Use proot-distro login with -- to execute commands inside Ubuntu
proot-distro login hermes -- bash -c "
    export DEBIAN_FRONTEND=noninteractive
    apt update && apt upgrade -y -o Dpkg::Options::='--force-confold'
    apt install python3 python3-pip python3-venv git curl build-essential nodejs npm -y

    if [ ! -d \"hermes-agent\" ]; then
        git clone --depth 1 --branch v2026.5.16 https://github.com/NousResearch/hermes-agent.git
        rm -rf hermes-agent/.git
    fi

    cd hermes-agent

    python3 -m venv venv
    source venv/bin/activate

    pip install --upgrade pip
    pip install -e .
"

#proot-distro backup hermes --output hermes.tar.gz