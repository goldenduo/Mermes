#!/usr/bin/env python3
"""Download Termux deb packages and their dependencies for all architectures."""

import os
import sys
import urllib.request
import urllib.error
from urllib.parse import urljoin

BASE_URL = "https://packages.termux.dev/apt/termux-main/"
ARCHITECTURES = {
    "aarch64": "arm64",
    "arm": "arm32",
    "i686": "x86",
    "x86_64": "x64",
}
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DEB_DIR = os.path.join(SCRIPT_DIR, "deb")


def fetch_packages(arch):
    """Fetch and parse the Packages file for a given architecture."""
    url = f"{BASE_URL}dists/stable/main/binary-{arch}/Packages"
    try:
        with urllib.request.urlopen(url) as resp:
            data = resp.read().decode("utf-8")
    except urllib.error.URLError as e:
        print(f"  Error fetching package list: {e}")
        return {}

    packages = {}
    current = {}
    for line in data.splitlines():
        if line == "":
            if "Package" in current:
                packages[current["Package"]] = current
            current = {}
        elif line[0] in (" ", "\t"):
            # continuation line, append to previous field
            pass
        else:
            key, _, value = line.partition(":")
            current[key.strip()] = value.strip()
    if "Package" in current:
        packages[current["Package"]] = current
    return packages


def resolve_deps(pkg_name, packages, resolved=None):
    """Recursively resolve all dependencies of a package."""
    if resolved is None:
        resolved = set()
    if pkg_name in resolved:
        return resolved
    resolved.add(pkg_name)

    info = packages.get(pkg_name)
    if not info:
        return resolved

    depends = info.get("Depends", "")
    if not depends:
        return resolved

    for group in depends.split(","):
        # Handle alternatives: pkg1 | pkg2
        alternatives = [alt.strip().split("(")[0].strip() for alt in group.split("|")]
        # Pick the first alternative that exists
        for alt_name in alternatives:
            if alt_name in packages:
                resolve_deps(alt_name, packages, resolved)
                break
    return resolved


def download_file(url, dest_path):
    """Download a file with progress bar."""
    try:
        with urllib.request.urlopen(url) as resp:
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
        # Clean up partial download
        if os.path.exists(dest_path):
            os.remove(dest_path)
        return False


def main():
    if len(sys.argv) < 2:
        print(f"Usage: python {os.path.basename(__file__)} <package_name> [package_name ...]")
        print(f"Example: python {os.path.basename(__file__)} python vim git")
        sys.exit(1)

    package_names = sys.argv[1:]

    for arch_termux, arch_label in ARCHITECTURES.items():
        print(f"\n{'=' * 60}")
        print(f"  Architecture: {arch_label} ({arch_termux})")
        print(f"{'=' * 60}")

        print(f"  Fetching package list for {arch_termux}...")
        packages = fetch_packages(arch_termux)
        if not packages:
            print(f"  Failed to fetch package list for {arch_termux}, skipping.")
            continue
        print(f"  Loaded {len(packages)} packages")

        # Resolve all dependencies for requested packages
        all_deps = set()
        for name in package_names:
            if name not in packages:
                print(f"  Warning: package '{name}' not found for {arch_termux}")
                continue
            resolve_deps(name, packages, all_deps)

        if not all_deps:
            print("  No packages to download.")
            continue

        # Sort for deterministic order
        all_deps = sorted(all_deps)
        print(f"  Need to download {len(all_deps)} package(s) (including dependencies)")

        out_dir = os.path.join(DEB_DIR, arch_label)
        os.makedirs(out_dir, exist_ok=True)

        skipped = 0
        downloaded = 0
        failed = 0

        for i, pkg in enumerate(all_deps, 1):
            info = packages[pkg]
            filename = info.get("Filename", "")
            version = info.get("Version", "")
            if not filename:
                print(f"  [{i}/{len(all_deps)}] {pkg}: no Filename field, skipping")
                failed += 1
                continue

            deb_name = os.path.basename(filename)
            dest = os.path.join(out_dir, deb_name)

            if os.path.exists(dest):
                skipped += 1
                print(f"  [{i}/{len(all_deps)}] {pkg} ({version}) - SKIP (exists)")
                continue

            url = urljoin(BASE_URL, filename)
            print(f"  [{i}/{len(all_deps)}] {pkg} ({version}) - {deb_name}")
            if download_file(url, dest):
                downloaded += 1
            else:
                failed += 1

        print(f"\n  Summary for {arch_label}: {downloaded} downloaded, {skipped} skipped, {failed} failed")

    print(f"\nDone. Files saved to: {DEB_DIR}")


if __name__ == "__main__":
    main()
