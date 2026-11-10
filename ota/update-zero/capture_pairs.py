"""Read fixed-slot firmware sizes and hashes for the selected fogona source build."""
import argparse
from pathlib import Path
import re
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("output", type=Path)
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--v111", action="store_true", help="Audit the v1.1.1 source image")
    group.add_argument("--classic", action="store_true", help="Audit the v1.1 Classic source image")
    args = parser.parse_args()
    adb = ["adb", "-s", args.serial, "shell"]

    def query(*command):
        return subprocess.check_output([*adb, *command], text=True).strip()

    suffix = query("getprop", "ro.boot.slot_suffix")
    if suffix not in ("_a", "_b") or (not args.classic and not args.v111 and suffix != "_a"):
        parser.error("Unsupported source slot")
    slot = str(int(suffix == "_b"))
    source = "TommyUI.v1.100.dev.20261109" if args.classic else "TommyUI.v1.003.dev.20261106"
    digest = ("b2c95b70bc2d2a57acb345b026075c50d7224b57730c58ccb0cd1e191bacf95e" if args.classic
              else "58672f248a7491fa026a8343f845bd648d31eb9536dd9f2481b55745d1e7d76f")
    if args.v111:
        source = "TommyUI.v1.101.dev.20261109"
        digest = "0539fd853af72a1844bfd0384ebc682b7ba6806919418e2a0b76c61934691ca8"
    for key, expected in {
        "ro.boot.hardware.sku": "XT2413-2", "ro.product.vendor.device": "fogona",
        "ro.build.version.incremental": source,
        "ro.vendor.build.fingerprint": "motorola/fogona/fogona:13/U1TFS34.100-35-14-1-12/ad64fa:user/release-keys",
        "ro.virtual_ab.enabled": "true",
    }.items():
        if query("getprop", key) != expected:
            parser.error("Unsupported device configuration: " + key)
    if query("su", "0", "bootctl", "get-snapshot-merge-status") != "none":
        parser.error("A snapshot update is active")
    for command, expected in [
        ("get-number-slots", "2"), ("get-current-slot", slot), ("get-active-boot-slot", slot),
        ("is-slot-marked-successful " + slot, "1"),
    ]:
        if query("su", "0", "bootctl", *command.split()) != expected:
            parser.error("Boot control mismatch: " + command)
    layout = query("su", "0", "lpdump", "--slot=" + slot)
    if ("Header flags: virtual_ab_device" not in layout
            or not re.search(rf"Name: mot_dp_group{suffix}\s+Maximum size: 6706692096 bytes", layout)):
        parser.error("Unexpected super partition group")
    if query("su", "0", "sha256sum", "/dev/block/mapper/system" + suffix).split()[0] != digest:
        parser.error("The active system partition differs from the selected source image")
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
    if suffix == "_b":
        swapped = []
        for row in rows:
            name, size_a, size_b, hash_a, hash_b = row.split()
            swapped.append(f"{name[:-2]}_b {size_b} {size_a} {hash_b} {hash_a}")
        rows = swapped
    args.output.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"Recorded {len(rows)} partition pairs.")


if __name__ == "__main__":
    main()
