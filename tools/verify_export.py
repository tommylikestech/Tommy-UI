"""Inspect the rebuilt image read-only and export the v1.003 flash kit."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import ssl
import struct
import subprocess
import tempfile
import zipfile

PROJECT = Path(__file__).resolve().parents[1]
RELEASE = PROJECT / "updater"
STEM = "Tommy-UI-v1.003"
IMAGE = STEM + "-Android16-arm64-system.img"

def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()

def run(*args):
    result = subprocess.run([str(arg) for arg in args], capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(f"{args[0]} failed: {result.stderr[-1500:]} {result.stdout[-600:]}")
    return result.stdout

def require(ok, message):
    if not ok:
        raise RuntimeError(message)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("aosp", type=Path)
    parser.add_argument("--output", type=Path, default=PROJECT / "dist")
    args = parser.parse_args()
    root = args.aosp.resolve()
    config = json.loads((PROJECT / "release.json").read_text())
    changes = {name: row["result"] for name, row in json.loads((PROJECT / "files.json").read_text()).items()}
    for name, sha in changes.items():
        require(digest(root / name) == sha, "Checkout mismatch: " + name)

    product = root / "out/target/product/generic_arm64"
    image = product / "system.img"
    checks = ["Locked source changes and inherited Tommy UI features verified"]
    with image.open("rb") as stream:
        stream.seek(1080)
        require(stream.read(2) == bytes.fromhex("53ef"), "Expected raw ext4")
    run("e2fsck", "-f", "-n", image)
    checks.append("Raw ext4 passed a read-only filesystem check")
    props = dict(line.split("=", 1) for line in run("debugfs", "-R", "cat /system/build.prop", image).splitlines()
                 if "=" in line and not line.startswith("#"))
    for prop, value in (("ro.build.version.incremental", config["build_number"]),
                        ("ro.build.version.release", "16"), ("ro.build.version.sdk", "36"),
                        ("ro.build.version.security_patch", config["security_patch"]), ("ro.build.type", "userdebug")):
        require(props.get(prop) == value, f"Unexpected {prop}: {props.get(prop)}")
    checks.append("Android 16, API 36, exact v1.003 build ID and security patch label verified")

    host = root / "out/host/linux-x86"
    aapt = host / "bin/aapt2"
    java = root / "prebuilts/jdk/jdk21/linux-x86/bin/java"
    cert = hashlib.sha256(ssl.PEM_cert_to_DER_cert((root /
            "build/make/target/product/security/platform.x509.pem").read_text())).hexdigest()
    require(cert == "c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8", "Platform key mismatch")
    package_hashes = {}
    with tempfile.TemporaryDirectory(prefix="tommy-v1003-verify-") as folder:
        tmp = Path(folder)
        def extract(name):
            target = tmp / Path(name).name
            run("debugfs", "-R", f"dump /system/{name} {target}", image)
            require(target.is_file() and target.stat().st_size > 0, "Missing from image: " + name)
            return target

        def packaged(name):
            apk = extract(name)
            require(digest(apk) == digest(product / "system" / name), "Image differs from compiled APK: " + name)
            require(cert in run(java, "-jar", host / "framework/apksigner.jar",
                    "verify", "--print-certs", apk), "APK signer mismatch: " + name)
            package_hashes[apk.name] = digest(apk)
            return apk

        storage = packaged("system_ext/priv-app/TommyStorage/TommyStorage.apk")
        installer = packaged("priv-app/PackageInstaller/PackageInstaller.apk")
        settings = packaged("system_ext/priv-app/Settings/Settings.apk")
        checks.append("The three changed system APKs match their compiled outputs and platform signer")

        def dex(apk):
            with zipfile.ZipFile(apk) as archive:
                require(archive.testzip() is None, "Corrupt APK: " + apk.name)
                return b"".join(archive.read(n) for n in archive.namelist() if n.endswith(".dex"))

        manifest = run(aapt, "dump", "badging", storage)
        require("versionCode='1005'" in manifest and "versionName='1.003'" in manifest, "Wrong mover version")
        for perm in ("MANAGE_USERS", "WRITE_SECURE_SETTINGS", "MOVE_PACKAGE", "MOUNT_UNMOUNT_FILESYSTEMS",
                     "INTERACT_ACROSS_USERS_FULL", "POST_NOTIFICATIONS"):
            require("android.permission." + perm in manifest, "Missing permission " + perm)
        app_code = dex(storage)
        for label in ("Set up or format SD card", "Photos and files location", "StorageWizardInit",
                      "StorageWizardMigrateConfirm", "New app storage choices", "Change storage", "Browse photos and files"):
            require(label.encode() in app_code, "Missing Tommy Storage code: " + label)
        checks.append("Tommy Storage 1.003 includes card setup, primary storage migration, file browsing, app moves and new-install notifications")
        require("com.tommyui.storage.TARGET_UUID".encode() in dex(settings), "Settings migration destination validation missing")
        tree = run(aapt, "dump", "xmltree", settings, "--file", "AndroidManifest.xml")
        blocks = re.split(r"(?m)^\s*E: (?:activity|activity-alias|receiver|service|provider)\b", tree)
        migration = next(b for b in blocks if ".deviceinfo.StorageWizardMigrateConfirm" in b)
        require("android.permission.MOUNT_UNMOUNT_FILESYSTEMS" in migration and
                re.search(r'android:exported[^\r\n]*(?:true|0xffffffff)', migration),
                "Migration wizard is not correctly permission-protected and exported")
        require("v1.003 (experimental)" in run(aapt, "dump", "resources", settings), "Settings version missing")
        checks.append("Settings exposes the guarded migration confirmation and shows v1.003")
        browser = extract("product/app/Browser2/Browser2.apk")
        browser_manifest = run(aapt, "dump", "badging", browser)
        require("Tommy Browser" in browser_manifest and "TommyBrowser" in browser_manifest, "Browser missing")
        require(b"application/vnd.android.package-archive" in dex(browser), "APK download support missing")
        require("Update Tommy UI".encode() in app_code, "About/update entry missing")
        for layout in ("top_level_settings", "top_level_settings_v2", "top_level_settings_expressive"):
            home = run(aapt, "dump", "xmltree", settings, "--file", f"res/xml/{layout}.xml")
            require("about_tommy_ui" in home and "com.tommyui.storage.AboutTommy" in home,
                    "About Tommy UI navigation missing in " + layout)
        phone = run(aapt, "dump", "xmltree", settings, "--file", "res/xml/my_device_info.xml")
        require("tommy_ui_update" in phone and "com.android.settings.tommyui.UpdatePage" in phone,
                "About phone update shortcut missing")
        about = run(aapt, "dump", "xmltree", storage, "--file", "AndroidManifest.xml")
        require(".AboutTommy" in about and b"Touch the stars" in app_code, "About activity or Easter egg missing")
        require(b"ro.boot.hardware.sku" in dex(settings), "Hardware model lookup absent")
        require("ro.product.product.model=moto g play - 2024" in
                run("debugfs", "-R", "cat /system/product/etc/build.prop", image), "Moto product model missing")
        checks.append("About Tommy UI, Pages update entry and Tommy Browser download activity verified")
        install_code = dex(installer)
        for label in ("The selected storage is no longer available for this app.", "Phone storage", "SD card"):
            require(label.encode() in install_code, "Install destination chooser missing: " + label)
        checks.append("System APK installer contains phone/SD destination selection and stale-selection handling")
        permissions = extract("system_ext/etc/permissions/privapp-permissions-tommy-storage.xml")
        require(digest(permissions) == digest(root / "packages/apps/TommyStorage/privapp-permissions-tommy-storage.xml"),
                "Storage allowlist differs")
        checks.append("Original same-partition privileged storage permission allowlist preserved")
        animation = extract("media/bootanimation.zip")
        require(digest(animation) == changes["device/tommyui/bootanimation.zip"], "Wrong boot animation")
        with zipfile.ZipFile(animation) as archive:
            require(archive.testzip() is None, "Corrupt boot animation")
        checks.append("v1.003 boot animation present and verified")
        launcher = extract("system_ext/priv-app/Launcher3QuickStep/Launcher3QuickStep.apk")
        require("application-label:'Tommy Home'" in run(aapt, "dump", "badging", launcher), "Wrong launcher")
        require("ARM aarch64" in run("file", extract("bin/vold")), "Wrong storage daemon")
        checks.append("Tommy Home and the ARM64 storage daemon are present")
        # Confirm the explicit per-user notification delivery is in the compiled service.
        services = extract("framework/services.jar")
        with zipfile.ZipFile(services) as archive:
            data = b"".join(archive.read(n) for n in archive.namelist() if n.endswith(".dex"))
        require(b"com.tommyui.storage" in data, "Explicit new-app broadcast is absent from system services")
        checks.append("Compiled system services include the targeted new-app broadcast")

    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=True)
    target = out / IMAGE
    sha = digest(image)
    require(image.stat().st_size < 6710886400, "Image exceeds super capacity")
    if target.exists():
        require(digest(target) == sha, "Different exported v1.003 image already exists")
    else:
        part = target.with_suffix(".img.partial")
        part.unlink(missing_ok=True)
        with image.open("rb") as inp, part.open("xb") as dest:
            shutil.copyfileobj(inp, dest, 8 * 1024 * 1024)
        require(digest(part) == sha, "Export digest mismatch")
        part.rename(target)
    meta = dict(config, image=IMAGE, image_bytes=target.stat().st_size, image_sha256=sha,
                checks=checks, changed_apks=package_hashes, device_tested=False, sd_transfer_tested=False,
                flashed=False, scope="Build only. No v1.003 installation or card formatting.")
    metadata = out / (STEM + "-build.json")
    metadata.write_text(json.dumps(meta, indent=2) + "\n")
    kit = out / (STEM + "-flash-kit.zip")
    with zipfile.ZipFile(kit, "w", zipfile.ZIP_DEFLATED, compresslevel=1) as archive:
        for item in (target, metadata, RELEASE / "flash_later.py", RELEASE / "FLASH-AND-STORAGE.txt", RELEASE / "Tommy-USB-Updater.pyw"):
            archive.write(item, item.name)
    with zipfile.ZipFile(kit) as archive:
        require(archive.testzip() is None, "Flash kit integrity check failed")
    (out / (STEM + "-SHA256SUMS.txt")).write_text(
        "".join(f"{digest(p)}  {p.name}\n" for p in (target, metadata, kit)))
    print(json.dumps(meta, indent=2), flush=True)

if __name__ == "__main__":
    main()
