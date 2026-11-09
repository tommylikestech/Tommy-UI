"""Read fixed-slot firmware sizes and hashes for the original fogona test configuration."""
import argparse
from pathlib import Path
import re
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    adb = ["adb", "-s", args.serial, "shell"]

    def query(*command):
        return subprocess.check_output([*adb, *command], text=True).strip()

    for key, expected in {
        "ro.boot.hardware.sku": "XT2413-2", "ro.product.vendor.device": "fogona",
        "ro.boot.slot_suffix": "_a", "ro.build.version.incremental": "TommyUI.v1.003.dev.20261106",
        "ro.vendor.build.fingerprint": "motorola/fogona/fogona:13/U1TFS34.100-35-14-1-12/ad64fa:user/release-keys",
        "ro.virtual_ab.enabled": "true",
    }.items():
        if query("getprop", key) != expected:
            parser.error("Unsupported device configuration: " + key)
    if query("su", "0", "bootctl", "get-snapshot-merge-status") != "none":
        parser.error("A snapshot update is active")
    for command, expected in [
        ("get-number-slots", "2"), ("get-current-slot", "0"), ("get-active-boot-slot", "0"),
        ("is-slot-marked-successful 0", "1"),
    ]:
        if query("su", "0", "bootctl", *command.split()) != expected:
            parser.error("Boot control mismatch: " + command)
    layout = query("su", "0", "lpdump", "--slot=0")
    if ("Header flags: virtual_ab_device" not in layout
            or not re.search(r"Name: mot_dp_group_a\s+Maximum size: 6706692096 bytes", layout)):
        parser.error("Unexpected super partition group")
    if query("su", "0", "sha256sum", "/dev/block/mapper/system_a").split()[0] != (
            "58672f248a7491fa026a8343f845bd648d31eb9536dd9f2481b55745d1e7d76f"):
        parser.error("The active system partition differs from the original v1.003 image")
    rows = subprocess.check_output([*adb, "su", "0", "sh"], text=True, input="""
set -e
for a in /dev/block/by-name/*_a; do
    b=${a%_a}_b
    test -b "$b"
    sa=$(blockdev --getsize64 "$a")
    sb=$(blockdev --getsize64 "$b")
    ha=$(sha256sum "$a")
    hb=$(sha256sum "$b")
    echo "${a##*/} $sa $sb ${ha%% *} ${hb%% *}"
done
""").splitlines()
    if not rows or any(not re.fullmatch(r"[a-z0-9_]+_a \d+ \d+ [a-f0-9]{64} [a-f0-9]{64}", r)
                       for r in rows):
        parser.error("Incomplete A/B inventory")
    args.output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"Recorded {len(rows)} partition pairs.")


if __name__ == "__main__":
    main()
