#!/data/data/com.termux/files/usr/bin/bash


NODEJS_VERSION="v24.2.0"
PLAYWRIGHT_VERSION="1.49.1"
CONTAINER_NAME="hermes2"

if ! proot-distro list | grep -q "Installed: yes" | grep "ubuntu"; then
    proot-distro install ubuntu:24.04 --name $CONTAINER_NAME
fi


proot-distro login $CONTAINER_NAME -- bash -c "
        set -e
        export DEBIAN_FRONTEND=noninteractive
        apt-get update -qq
        apt-get install -y -qq --no-install-recommends curl ca-certificates

        echo '--- Installing Node.js ---'
        curl -fsSL https://nodejs.org/dist/$NODEJS_VERSION/node-$NODEJS_VERSION-linux-arm64.tar.gz | tar xz -C /usr/local --strip-components=1
        node --version

        echo '--- Installing Playwright Chromium ---'
        npx playwright@$PLAYWRIGHT_VERSION install chromium 2>&1

        PW_DIR=/root/.cache/ms-playwright

        # full chromium 디렉토리 삭제 (chromium-XXXX)
        FULL_CHROME_DIR=\$(find \$PW_DIR -maxdepth 1 -type d -name 'chromium-*' 2>/dev/null | head -1)
        if [ -n \"\$FULL_CHROME_DIR\" ]; then
            echo \"Removing full chrome: \$FULL_CHROME_DIR (\$(du -sh \$FULL_CHROME_DIR | cut -f1))\"
            rm -rf \"\$FULL_CHROME_DIR\"
        fi

        # headless_shell 경량화
        HS_DIR=\$(find \$PW_DIR -maxdepth 1 -type d -name 'chromium_headless_shell-*' 2>/dev/null | head -1)
        if [ -n \"\$HS_DIR\" ]; then
            HS_CHROME_DIR=\"\$HS_DIR/chrome-linux\"
            if [ -d \"\$HS_CHROME_DIR/locales\" ]; then
                find \"\$HS_CHROME_DIR/locales\" -name '*.pak' ! -name 'en-US.pak' -delete
                echo \"Locales trimmed\"
            fi
            rm -f \"\$HS_CHROME_DIR/chrome_crashpad_handler\" 2>/dev/null
            rm -rf \"\$HS_CHROME_DIR/MEIPreload\" 2>/dev/null
            echo \"headless_shell size: \$(du -sh \$HS_DIR | cut -f1)\"
        else
            echo \"WARNING: headless_shell not found!\"
        fi
        echo \"Total playwright size: \$(du -sh \$PW_DIR | cut -f1)\"

        echo '--- Creating playwright bundle ---'
        cd /
        tar czf /tmp/playwright.tar.gz \\
            root/.cache/ms-playwright
        ls -lh /tmp/playwright.tar.gz
        echo '--- DONE ---'
"