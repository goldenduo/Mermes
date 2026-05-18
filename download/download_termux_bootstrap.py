#!/usr/bin/env python3
"""Download Termux bootstrap packages for all architectures."""

import os
import sys
import json
import urllib.request
import urllib.error

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
BOOTSTRAP_DIR = os.path.join(SCRIPT_DIR, "bootstrap")

ARCHITECTURES = {
    "aarch64": "arm64",
    "arm": "arm32",
    "i686": "x86",
    "x86_64": "x64",
}

GITHUB_API = "https://api.github.com/repos/termux/termux-packages/releases/latest"


def get_latest_release():
    """Fetch latest bootstrap release info from GitHub."""
    req = urllib.request.Request(GITHUB_API, headers={"Accept": "application/vnd.github.v3+json"})
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read().decode())
    tag = data["tag_name"]
    assets = {}
    for a in data.get("assets", []):
        name = a["name"]
        for arch in ARCHITECTURES:
            if name == f"bootstrap-{arch}.zip":
                assets[arch] = a["browser_download_url"]
    return tag, assets


def download_file(url, dest_path):
    """Download a file with progress bar."""
    req = urllib.request.Request(url, headers={"Accept": "application/octet-stream"})
    try:
        with urllib.request.urlopen(req) as resp:
            total = resp.headers.get("Content-Length")
            total = int(total) if total else None
            downloaded = 0
            block_size = 65536

            with open(dest_path, "wb") as f:
                while True:
                    chunk = resp.read(block_size)
                    if not chunk:
                        break
                    f.write(chunk)
                    downloaded += len(chunk)
                    if total:
                        pct = downloaded * 100 // total
                        bar_len = 40
                        filled = bar_len * downloaded // total
                        bar = "#" * filled + "-" * (bar_len - filled)
                        sys.stdout.write(f"\r  [{bar}] {pct}%  {downloaded}/{total} bytes")
                    else:
                        sys.stdout.write(f"\r  Downloaded {downloaded} bytes")
                    sys.stdout.flush()
            sys.stdout.write("\n")
            return True
    except Exception as e:
        sys.stdout.write("\n")
        print(f"  Error: {e}")
        if os.path.exists(dest_path):
            os.remove(dest_path)
        return False


def main():
    os.makedirs(BOOTSTRAP_DIR, exist_ok=True)

    print("Fetching latest Termux bootstrap release info...")
    try:
        tag, assets = get_latest_release()
    except Exception as e:
        print(f"Error fetching release info: {e}")
        sys.exit(1)

    print(f"Latest release: {tag}")
    print(f"Found {len(assets)} architecture(s)")

    downloaded = 0
    skipped = 0
    failed = 0

    for arch_termux, arch_label in ARCHITECTURES.items():
        if arch_termux not in assets:
            print(f"\n  [{arch_label}] No bootstrap available, skipping")
            continue

        url = assets[arch_termux]
        filename = f"bootstrap-{arch_termux}.zip"
        dest = os.path.join(BOOTSTRAP_DIR, filename)

        print(f"\n{'=' * 60}")
        print(f"  Architecture: {arch_label} ({arch_termux})")
        print(f"{'=' * 60}")

        if os.path.exists(dest):
            skipped += 1
            print(f"  SKIP (exists): {filename}")
            continue

        print(f"  Downloading: {filename}")
        if download_file(url, dest):
            downloaded += 1
        else:
            failed += 1

    print(f"\n{'=' * 60}")
    print(f"  Done: {downloaded} downloaded, {skipped} skipped, {failed} failed")
    print(f"  Output: {BOOTSTRAP_DIR}")
    print(f"{'=' * 60}")


if __name__ == "__main__":
    main()
