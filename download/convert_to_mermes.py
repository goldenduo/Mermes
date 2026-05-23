#!/usr/bin/env python3
"""
将 Termux deb 包的包名从 com.termux 转换为 com.mermes。

转换操作：
1. 解析 deb 文件（ar 归档格式），提取出 control.tar 和 data.tar
2. 解压 data.tar，遍历所有文件：
   - 将文件内容中的 com.termux 替换为 com.mermes（包括二进制文件，因为两者长度相同均为10字节，不会破坏二进制结构）
   - 更新符号链接（symlink）的目标路径中的 com.termux → com.mermes
   - 重命名包含 com.termux 的目录名和文件名
3. 解压 control.tar，同样执行上述替换操作（更新包元数据中的路径引用）
4. 将修改后的 control.tar 和 data.tar 重新打包为新的 deb 文件

输出目录：download/mermes_deb/{arch}/
"""

import os
import sys
import subprocess
import tarfile
import tempfile
import shutil
import struct

OLD_PKG = "com.termux"
NEW_PKG = "com.mermes"

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DEB_DIR = os.path.join(SCRIPT_DIR, "deb")
MERMES_DIR = os.path.join(SCRIPT_DIR, "mermes_deb")

AR_MAGIC = b"!<arch>\n"
AR_HEADER_SIZE = 60


def ar_extract(deb_path):
    """Extract an ar archive (deb file) and return list of (name, data) tuples."""
    members = []
    with open(deb_path, "rb") as f:
        magic = f.read(len(AR_MAGIC))
        if magic != AR_MAGIC:
            raise ValueError(f"Not an ar archive: {deb_path}")

        while True:
            header = f.read(AR_HEADER_SIZE)
            if len(header) < AR_HEADER_SIZE:
                break

            # Parse header: name(16) timestamp(12) owner(6) group(6) mode(8) size(10) end(2)
            name = header[0:16].decode("ascii").strip()
            size = int(header[48:58].decode("ascii").strip())
            end = header[58:60]
            if end != b"`\n":
                break

            # Remove trailing '/' from name
            if name.endswith("/"):
                name = name[:-1]

            data = f.read(size)
            # Pad to 2-byte boundary
            if size % 2 != 0:
                f.read(1)

            members.append((name, data))

    return members


def ar_create(output_path, members):
    """Create an ar archive from list of (name, data) tuples."""
    with open(output_path, "wb") as f:
        f.write(AR_MAGIC)
        for name, data in members:
            size = len(data)
            # Build header
            name_field = (name + "/").encode("ascii")[:16].ljust(16)
            ts_field = b"0".ljust(12)
            owner_field = b"0".ljust(6)
            group_field = b"0".ljust(6)
            mode_field = b"100644".ljust(8)
            size_field = str(size).encode("ascii").ljust(10)
            end_field = b"`\n"
            header = name_field + ts_field + owner_field + group_field + mode_field + size_field + end_field
            f.write(header)
            f.write(data)
            if size % 2 != 0:
                f.write(b"\n")


def get_compression(filename):
    """Detect compression format from filename."""
    if filename.endswith(".zst"):
        return "zst"
    if filename.endswith(".xz"):
        return "xz"
    if filename.endswith(".gz"):
        return "gz"
    if filename.endswith(".bz2"):
        return "bz2"
    return "none"


# Python tarfile supports these compression types natively
_TARFILE_MODE = {
    "gz": "r:gz",
    "bz2": "r:bz2",
    "xz": "r:xz",
    "none": "r",
}

_TARFILE_WRITE = {
    "gz": "w:gz",
    "bz2": "w:bz2",
    "xz": "w:xz",
    "none": "w",
}


def extract_tar(archive_path, dest_dir, compression):
    """Extract a tar archive preserving Unix permission bits."""
    # zst not supported by Python tarfile, decompress first
    if compression == "zst":
        zstd_path = shutil.which("zstd")
        if not zstd_path:
            raise RuntimeError("zstd not found. Install with: pacman -S zstd")
        tar_path = archive_path.rstrip(".zst") if archive_path.endswith(".zst") else archive_path + ".tar"
        print(f"      [zstd] decompressing ...", flush=True)
        subprocess.run([zstd_path, "-d", archive_path, "-o", tar_path, "-f"], check=True)
        print(f"      [tar] extracting ...", flush=True)
        with tarfile.open(tar_path, "r:") as tf:
            tf.extractall(dest_dir)
        if os.path.exists(tar_path) and tar_path != archive_path:
            os.remove(tar_path)
    else:
        mode = _TARFILE_MODE.get(compression, "r")
        print(f"      [tar] extracting ({compression}) ...", flush=True)
        with tarfile.open(archive_path, mode) as tf:
            tf.extractall(dest_dir)
    print(f"      [tar] done", flush=True)


def create_tar(source_dir, output_path, compression):
    """Create a tar archive preserving Unix permission bits from extracted files."""
    # zst not supported by Python tarfile, compress after packing
    if compression == "zst":
        zstd_path = shutil.which("zstd")
        if not zstd_path:
            raise RuntimeError("zstd not found")
        tar_path = output_path.rstrip(".zst") if output_path.endswith(".zst") else output_path + ".tar"
        print(f"      [tar] packing ...", flush=True)
        with tarfile.open(tar_path, "w:") as tf:
            for entry in os.listdir(source_dir):
                tf.add(os.path.join(source_dir, entry), arcname=entry)
        print(f"      [zstd] compressing ...", flush=True)
        subprocess.run([zstd_path, "-o", output_path, tar_path, "-f"], check=True)
        if os.path.exists(tar_path) and tar_path != output_path:
            os.remove(tar_path)
    else:
        mode = _TARFILE_WRITE.get(compression, "w")
        print(f"      [tar] packing ({compression}) ...", flush=True)
        with tarfile.open(output_path, mode) as tf:
            for entry in os.listdir(source_dir):
                tf.add(os.path.join(source_dir, entry), arcname=entry)
    print(f"      [tar] done", flush=True)


def replace_in_file(filepath, old_bytes, new_bytes):
    """Replace bytes in a file (works for both text and binary files).

    Since com.termux and com.mermes are the same length (10 bytes),
    this is safe for binary files too.
    """
    try:
        with open(filepath, "rb") as f:
            content = f.read()
    except Exception:
        return False

    if old_bytes not in content:
        return False

    content = content.replace(old_bytes, new_bytes)
    with open(filepath, "wb") as f:
        f.write(content)
    return True


def rename_comtermux_paths(root_dir):
    """Walk bottom-up and rename any path component containing com.termux."""
    count = 0
    for dirpath, dirnames, filenames in os.walk(root_dir, topdown=False):
        for fname in filenames:
            if OLD_PKG in fname:
                old = os.path.join(dirpath, fname)
                new = os.path.join(dirpath, fname.replace(OLD_PKG, NEW_PKG))
                os.rename(old, new)
                count += 1
        for dname in dirnames:
            if OLD_PKG in dname:
                old = os.path.join(dirpath, dname)
                new = os.path.join(dirpath, dname.replace(OLD_PKG, NEW_PKG))
                os.rename(old, new)
                count += 1
    return count


def replace_in_tree(root_dir):
    """Replace com.termux with com.mermes in all files (handling symlinks properly)."""
    old_bytes = OLD_PKG.encode()
    new_bytes = NEW_PKG.encode()
    count = 0
    for dirpath, dirnames, filenames in os.walk(root_dir):
        # Handle symlinks in dirnames (directory symlinks)
        for dname in list(dirnames):
            dpath = os.path.join(dirpath, dname)
            if os.path.islink(dpath):
                try:
                    target = os.readlink(dpath)
                    if OLD_PKG in target:
                        new_target = target.replace(OLD_PKG, NEW_PKG)
                        os.remove(dpath)
                        os.symlink(new_target, dpath)
                        count += 1
                except Exception as e:
                    print(f"    Warning: Failed to update directory symlink {dpath}: {e}")

        # Handle files and file symlinks in filenames
        for fname in filenames:
            fpath = os.path.join(dirpath, fname)
            if os.path.islink(fpath):
                try:
                    target = os.readlink(fpath)
                    if OLD_PKG in target:
                        new_target = target.replace(OLD_PKG, NEW_PKG)
                        os.remove(fpath)
                        os.symlink(new_target, fpath)
                        count += 1
                except Exception as e:
                    print(f"    Warning: Failed to update symlink {fpath}: {e}")
            else:
                if replace_in_file(fpath, old_bytes, new_bytes):
                    count += 1
    return count


def convert_deb(input_path, output_path):
    """Convert a single deb package from com.termux to com.mermes."""
    with tempfile.TemporaryDirectory() as tmpdir:
        # Step 1: Extract ar archive using pure Python
        print(f"      extracting deb (ar) ...", flush=True)
        try:
            members = ar_extract(input_path)
        except Exception as e:
            print(f"    ar extraction failed: {e}")
            return False

        # Find control and data archives
        control_name = None
        control_data = None
        data_name = None
        data_data = None
        debian_binary = None
        new_members = []

        for name, data in members:
            if name.startswith("control.tar"):
                control_name = name
                control_data = data
            elif name.startswith("data.tar"):
                data_name = name
                data_data = data
            elif name == "debian-binary":
                debian_binary = data

        if not control_name or not data_name:
            print(f"    Missing control.tar or data.tar in deb")
            return False

        control_comp = get_compression(control_name)
        data_comp = get_compression(data_name)

        # Step 2: Extract data.tar to temp dir
        data_dir = os.path.join(tmpdir, "data")
        os.makedirs(data_dir)
        data_tar_path = os.path.join(tmpdir, data_name)
        with open(data_tar_path, "wb") as f:
            f.write(data_data)
        try:
            extract_tar(data_tar_path, data_dir, data_comp)
        except Exception as e:
            print(f"    data extraction failed: {e}")
            return False

        # Step 3: Replace in file contents
        print(f"      replacing com.termux -> com.mermes ...", flush=True)
        files_changed = replace_in_tree(data_dir)
        # Step 4: Rename paths
        paths_changed = rename_comtermux_paths(data_dir)

        if files_changed == 0 and paths_changed == 0:
            print(f"    No com.termux references found, copying as-is")
        else:
            print(f"      {files_changed} files, {paths_changed} paths renamed", flush=True)

        # Step 5: Recreate data.tar
        new_data_path = os.path.join(tmpdir, "new_" + data_name)
        try:
            create_tar(data_dir, new_data_path, data_comp)
        except Exception as e:
            print(f"    data repack failed: {e}")
            return False
        with open(new_data_path, "rb") as f:
            new_data_bytes = f.read()

        # Step 6: Extract control.tar, replace, repack
        print(f"      processing control ...", flush=True)
        control_dir = os.path.join(tmpdir, "control")
        os.makedirs(control_dir)
        control_tar_path = os.path.join(tmpdir, control_name)
        with open(control_tar_path, "wb") as f:
            f.write(control_data)
        try:
            extract_tar(control_tar_path, control_dir, control_comp)
        except Exception as e:
            print(f"    control extraction failed: {e}")
            return False

        replace_in_tree(control_dir)
        rename_comtermux_paths(control_dir)

        new_control_path = os.path.join(tmpdir, "new_" + control_name)
        try:
            create_tar(control_dir, new_control_path, control_comp)
        except Exception as e:
            print(f"    control repack failed: {e}")
            return False
        with open(new_control_path, "rb") as f:
            new_control_bytes = f.read()

        # Step 7: Rebuild ar archive
        out_members = []
        if debian_binary is not None:
            out_members.append(("debian-binary", debian_binary))
        else:
            out_members.append(("debian-binary", b"2.0\n"))
        out_members.append((control_name, new_control_bytes))
        out_members.append((data_name, new_data_bytes))

        try:
            ar_create(output_path, out_members)
        except Exception as e:
            print(f"    ar repack failed: {e}")
            return False

    return True


def main():
    if not os.path.isdir(DEB_DIR):
        print(f"Error: {DEB_DIR} not found. Run download_termux.py first.")
        sys.exit(1)

    os.makedirs(MERMES_DIR, exist_ok=True)

    arch_dirs = sorted(
        d for d in os.listdir(DEB_DIR)
        if os.path.isdir(os.path.join(DEB_DIR, d))
    )

    if not arch_dirs:
        print("No architecture directories found in deb/")
        sys.exit(1)

    total_converted = 0
    total_skipped = 0
    total_failed = 0

    for arch in arch_dirs:
        src_dir = os.path.join(DEB_DIR, arch)
        dst_dir = os.path.join(MERMES_DIR, arch)
        os.makedirs(dst_dir, exist_ok=True)

        deb_files = sorted(f for f in os.listdir(src_dir) if f.endswith(".deb"))
        if not deb_files:
            continue

        print(f"\n{'=' * 60}")
        print(f"  Architecture: {arch}  ({len(deb_files)} packages)")
        print(f"{'=' * 60}")

        for i, fname in enumerate(deb_files, 1):
            src = os.path.join(src_dir, fname)
            dst = os.path.join(dst_dir, fname)

            if os.path.exists(dst):
                total_skipped += 1
                print(f"  [{i}/{len(deb_files)}] {fname} - SKIP (exists)")
                continue

            print(f"  [{i}/{len(deb_files)}] {fname} ...")
            if convert_deb(src, dst):
                total_converted += 1
                print(f"    OK")
            else:
                total_failed += 1
                print(f"    FAILED")

    print(f"\n{'=' * 60}")
    print(f"  Done: {total_converted} converted, {total_skipped} skipped, {total_failed} failed")
    print(f"  Output: {MERMES_DIR}")
    print(f"{'=' * 60}")


if __name__ == "__main__":
    main()
