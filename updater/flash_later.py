"""Check or flash the v1.003 system image on the Moto G Play 2024 already running v1.002."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

IMAGE = "Tommy-UI-v1.003-Android16-arm64-system.img"
BASE_BYTES = 2041208832

def sha256(path):
    with path.open("rb") as data:
        return hashlib.file_digest(data, "sha256").hexdigest()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fastboot", default="fastboot")
    parser.add_argument("--serial", help="Expected USB device serial")
    parser.add_argument("--image", type=Path)
    parser.add_argument("--flash", action="store_true", help="Write system_a and reboot")
    args = parser.parse_args()
    folder = Path(__file__).resolve().parent
    image = args.image or folder / IMAGE
    if not image.exists() and args.image is None:
        image = folder.parent / "dist" / IMAGE
    manifest = json.loads((image.parent / "Tommy-UI-v1.003-build.json").read_text(encoding="utf-8"))
    if (manifest["build_number"] != "TommyUI.v1.003.dev.20261106" or
            image.stat().st_size != manifest["image_bytes"] or sha256(image) != manifest["image_sha256"]):
        raise SystemExit("Image verification failed.")

    def run(command, timeout=25):
        result = subprocess.run([args.fastboot, *command], capture_output=True, text=True, timeout=timeout)
        output = result.stdout + result.stderr
        if result.returncode or re.search(r'\bFAILED\b|\berror:', output, re.I):
            raise SystemExit(output or "Fastboot failed.")
        return output

    devices = [line.split() for line in run(["devices"]).splitlines() if line.strip()]
    if len(devices) != 1 or len(devices[0]) != 2 or devices[0][1] != "fastboot":
        raise SystemExit("Connect this phone alone in fastbootd, then retry.")
    if args.serial and devices[0][0] != args.serial:
        raise SystemExit("The USB device changed.")
    target = ["-s", devices[0][0]]

    def getvar(name):
        output = run(target + ["getvar", name])
        match = re.search(r"^(?:\(bootloader\)\s*)?" + re.escape(name) + r":\s*(.*?)\s*$", output, re.M)
        if not match:
            raise SystemExit("Could not read " + name)
        return match.group(1)

    for name, expected in (("product", "fogona"), ("current-slot", "a"), ("is-userspace", "yes"),
                            ("unlocked", "yes"), ("snapshot-update-status", "none"), ("is-logical:system_a", "yes")):
        value = getvar(name)
        if value != expected:
            raise SystemExit(f"Expected {name}={expected}, found {value}.")
    old_size = int(getvar("partition-size:system_a"), 0)
    size = image.stat().st_size
    if int(getvar("partition-size:super"), 0) != 6710886400 or old_size not in (BASE_BYTES, size):
        raise SystemExit("Partition layout differs from the existing Tommy UI v1.002 installation.")
    print("Verified the image and the connected fogona in fastbootd on slot a.", flush=True)
    if not args.flash:
        print("Read-only checks passed. Run again with --flash to install.")
        return
    if old_size != size:
        print(run(target + ["resize-logical-partition", "system_a", str(size)], timeout=60))
        if int(getvar("partition-size:system_a"), 0) != size:
            raise SystemExit("Partition size verification failed.")
    print("Writing system_a. Keep the USB cable connected.", flush=True)
    print(run(target + ["flash", "system_a", str(image.resolve())], timeout=600), flush=True)
    print(run(target + ["reboot"]), flush=True)

if __name__ == "__main__":
    main()
