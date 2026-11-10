"""Check or install Tommy UI v1.2 on an unlocked North American retail Moto."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time

BUILD = "TommyUI.v1.200.dev.20261109"
IMAGE_SHA256 = "07af7fbf3aa7464059175bf6fa4d96c1beaf0afa67477138f24e77edcd5ef6ea"
VENDOR = "motorola/fogona/fogona:13/U1TFS34.100-35-14-1-12/ad64fa:user/release-keys"
SOURCES = {
    "TommyUI.v1.101.dev.20261109": "0539fd853af72a1844bfd0384ebc682b7ba6806919418e2a0b76c61934691ca8",
    "TommyUI.v1.002.dev.20261106": "b09409cb8afff2f12462189fffcef7c8defe97d4d2af9d536239e50f726a23e0",
    "TommyUI.v1.003.dev.20261106": "58672f248a7491fa026a8343f845bd648d31eb9536dd9f2481b55745d1e7d76f",
    "TommyUI.v1.100.dev.20261109": "b2c95b70bc2d2a57acb345b026075c50d7224b57730c58ccb0cd1e191bacf95e",
}


def run(*args, timeout=45):
    result = subprocess.run(args, capture_output=True, text=True, timeout=timeout)
    output = (result.stdout + result.stderr).strip()
    if result.returncode:
        raise SystemExit(output or "Command failed: " + args[0])
    return output


def expect(actual, expected, name):
    if actual != expected:
        raise SystemExit(f"Stopped at {name}: expected {expected}, found {actual}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--image", type=Path, required=True)
    parser.add_argument("--install", action="store_true", help="Flash after checking the image and phone")
    args = parser.parse_args()
    image = args.image.resolve()
    report = json.loads((image.parent / "Tommy-UI-v1.2-build.json").read_text())
    expect(report["build_number"], BUILD, "target build")
    expect(report["image_sha256"], IMAGE_SHA256, "released image checksum")
    expect(image.name, report["image"], "image name")
    expect(image.stat().st_size, report["image_bytes"], "image size")
    with image.open("rb") as stream:
        expect(hashlib.file_digest(stream, "sha256").hexdigest(), report["image_sha256"], "image checksum")
    def shell(*arguments):
        return run("adb", "-s", args.serial, "shell", *arguments)
    def prop(key):
        return shell("getprop", key)
    expect(prop("ro.boot.hardware.sku"), "XT2413-2", "hardware SKU")
    expect(prop("ro.product.vendor.device"), "fogona", "vendor device")
    expect(prop("ro.vendor.build.fingerprint"), VENDOR, "vendor build")
    expect(prop("ro.boot.flash.locked"), "0", "bootloader lock")
    source = prop("ro.build.version.incremental")
    suffix = prop("ro.boot.slot_suffix")
    if source not in SOURCES or suffix not in ("_a", "_b"):
        raise SystemExit("Unsupported source build or slot: " + source + " " + suffix)
    slot = suffix[1:]
    slot_id = str(int(slot == "b"))
    partition = "system" + suffix
    expect(shell("su", "0", "bootctl", "get-active-boot-slot"), slot_id, "selected boot slot")
    expect(shell("su", "0", "bootctl", "is-slot-marked-successful", slot_id), "1", "successful boot")
    expect(shell("su", "0", "bootctl", "get-snapshot-merge-status"), "none", "snapshot state")
    digest = shell("su", "0", "sha256sum", "/dev/block/mapper/" + partition).split()[0]
    expect(digest, SOURCES[source], "installed system checksum")
    print(f"Verified {source} on slot {slot} and the v1.2 image.", flush=True)
    if not args.install:
        return
    run("adb", "-s", args.serial, "reboot", "fastboot")
    fastboot = ["fastboot", "-s", args.serial]
    def variable(name):
        reply = run(*fastboot, "getvar", name, timeout=120)
        match = re.search(r"^" + re.escape(name) + r":\s*(\S+)", reply, re.M)
        if not match:
            raise SystemExit(reply)
        return match.group(1)
    for name, expected in (("is-userspace","yes"), ("product","fogona"), ("current-slot",slot),
                           ("unlocked","yes"), ("snapshot-update-status","none"), ("is-logical:"+partition,"yes")):
        expect(variable(name), expected, name)
    expect(int(variable("partition-size:super"), 0), 6710886400, "super size")
    print(f"Writing {partition}. Keep USB connected.", flush=True)
    print(run(*fastboot, "flash", partition, str(image), timeout=600), flush=True)
    print(run(*fastboot, "reboot"), flush=True)
    run("adb", "-s", args.serial, "wait-for-device", timeout=300)
    for _ in range(90):
        if prop("sys.boot_completed") == "1":
            expect(prop("ro.build.version.incremental"), BUILD, "running build")
            expect(prop("ro.boot.slot_suffix"), suffix, "running slot")
            print("Android booted Tommy UI v1.2 on slot " + slot)
            return
        time.sleep(2)
    raise SystemExit("Flash completed. Unlock Android and check Settings > About Tommy UI.")


if __name__ == "__main__":
    main()
