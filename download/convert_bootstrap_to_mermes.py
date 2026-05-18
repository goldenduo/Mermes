#!/usr/bin/env python3
"""
将 Termux bootstrap 包的包名从 com.termux 转换为 com.mermes。

转换操作：
1. 解压 bootstrap zip 文件（包含完整的 Termux 根文件系统）
2. 遍历所有文件，将文件内容中的 com.termux 替换为 com.mermes：
   - 包括二进制文件（ELF 可执行文件等），因为 com.termux 和 com.mermes 长度相同（10字节），替换不会破坏二进制结构
   - 包括 SYMLINKS.txt 中的绝对路径（如 /data/data/com.termux/files/usr/...）
   - 包括 shell 脚本、配置文件等文本文件中的路径引用
3. 将修改后的文件重新打包为新的 zip 文件

输出目录：download/mermes_bootstrap/
"""

import os
import sys
import zipfile
import tempfile

OLD_PKG = "com.termux"
NEW_PKG = "com.mermes"

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
BOOTSTRAP_DIR = os.path.join(SCRIPT_DIR, "bootstrap")
MERMES_BOOTSTRAP_DIR = os.path.join(SCRIPT_DIR, "mermes_bootstrap")


def replace_in_bytes(data, old_bytes, new_bytes):
    """Replace bytes in data if present."""
    if old_bytes in data:
        return data.replace(old_bytes, new_bytes), True
    return data, False


def convert_bootstrap(input_path, output_path):
    """Convert a single bootstrap zip from com.termux to com.mermes."""
    old_bytes = OLD_PKG.encode()
    new_bytes = NEW_PKG.encode()

    with tempfile.TemporaryDirectory() as tmpdir:
        extract_dir = os.path.join(tmpdir, "extract")
        os.makedirs(extract_dir)

        # Step 1: Extract zip
        with zipfile.ZipFile(input_path, "r") as zin:
            zin.extractall(extract_dir)

        # Step 2: Replace in all file contents
        files_changed = 0
        for root, dirs, filenames in os.walk(extract_dir):
            for fname in filenames:
                fpath = os.path.join(root, fname)
                try:
                    with open(fpath, "rb") as f:
                        content = f.read()
                except Exception:
                    continue

                new_content, changed = replace_in_bytes(content, old_bytes, new_bytes)
                if changed:
                    with open(fpath, "wb") as f:
                        f.write(new_content)
                    files_changed += 1

        # Step 3: Create new zip
        with zipfile.ZipFile(output_path, "w", zipfile.ZIP_DEFLATED) as zout:
            for root, dirs, filenames in os.walk(extract_dir):
                for fname in filenames:
                    fpath = os.path.join(root, fname)
                    arcname = os.path.relpath(fpath, extract_dir)
                    zout.write(fpath, arcname)

        return files_changed


def main():
    if not os.path.isdir(BOOTSTRAP_DIR):
        print(f"Error: {BOOTSTRAP_DIR} not found. Run download_termux_bootstrap.py first.")
        sys.exit(1)

    os.makedirs(MERMES_BOOTSTRAP_DIR, exist_ok=True)

    bootstrap_files = sorted(f for f in os.listdir(BOOTSTRAP_DIR) if f.endswith(".zip"))

    if not bootstrap_files:
        print("No bootstrap zip files found in bootstrap/")
        sys.exit(1)

    print(f"Found {len(bootstrap_files)} bootstrap file(s)")

    total_converted = 0
    total_skipped = 0
    total_failed = 0

    for i, fname in enumerate(bootstrap_files, 1):
        src = os.path.join(BOOTSTRAP_DIR, fname)
        dst = os.path.join(MERMES_BOOTSTRAP_DIR, fname)

        if os.path.exists(dst):
            total_skipped += 1
            print(f"[{i}/{len(bootstrap_files)}] {fname} - SKIP (exists)")
            continue

        print(f"[{i}/{len(bootstrap_files)}] {fname} ...")
        try:
            files_changed = convert_bootstrap(src, dst)
            total_converted += 1
            print(f"  OK ({files_changed} files modified)")
        except Exception as e:
            total_failed += 1
            print(f"  FAILED: {e}")

    print(f"\n{'=' * 60}")
    print(f"  Done: {total_converted} converted, {total_skipped} skipped, {total_failed} failed")
    print(f"  Output: {MERMES_BOOTSTRAP_DIR}")
    print(f"{'=' * 60}")


if __name__ == "__main__":
    main()
