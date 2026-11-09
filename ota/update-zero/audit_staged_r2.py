"""Audit the withdrawn R2 target. --authorize-ui enables a separate on-phone decision."""
import argparse
import hashlib
from pathlib import Path
import re
import subprocess
import uuid

SOURCE = "TommyUI.v1.003.dev.20261106"
VENDOR = "motorola/fogona/fogona:13/U1TFS34.100-35-14-1-12/ad64fa:user/release-keys"
METADATA = "026f47d7579541683dc4a911f6d32433627e625c6ac56e0ff8f6db6e2d784c51"
# From the signed R2 manifest's system partition old/new PartitionInfo.
SYSTEM_A = "58672f248a7491fa026a8343f845bd648d31eb9536dd9f2481b55745d1e7d76f"
SYSTEM_B = "344de55db5e061e2028d0b1105850809ce506aca7ebb4f0d9704c5e954088297"
FIXED = set("abl bluetooth boot cmnlib64 cmnlib devcfg dsp dtbo fsg hyp init_boot keymaster "
            "logo modem prov qupfw recovery rpm storsec tz uefisecapp vbmeta vbmeta_system "
            "vendor_boot xbl xbl_config".split())
UNCHANGED_DYNAMIC = {"system_dlkm", "system_ext", "vendor", "vendor_dlkm"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def extents(dump, name):
    blocks = [b for b in re.split(r"(?m)^-{3,}[ \t]*$", dump)
              if re.search(r"(?m)^[ \t]*Name: " + re.escape(name) + r"[ \t]*$", b)]
    require(len(blocks) == 1 and "Extents:" in blocks[0], "Missing logical partition " + name)
    rows = tuple(" ".join(line.split()) for line in blocks[0].split("Extents:", 1)[1].splitlines()
                 if line.strip())
    require(rows and all(re.fullmatch(r"\d+ \.\. \d+ linear \S+ \d+", line) for line in rows),
            "Unexpected extent layout for " + name)
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("metadata", type=Path, help="Local candidate-r2.metadata")
    parser.add_argument("--authorize-ui", action="store_true",
                        help="Prepare engine-readable metadata and enable Finish for this boot only")
    args = parser.parse_args()
    require(hashlib.sha256(args.metadata.read_bytes()).hexdigest() == METADATA, "Wrong R2 metadata")
    adb = ["adb", "-s", args.serial]

    def shell(*args):
        return subprocess.check_output([*adb, "shell", *args], text=True, timeout=150).strip()

    def root(*args):
        return shell("su", "0", *args)

    for key, expected in {"ro.build.version.incremental": SOURCE, "ro.vendor.build.fingerprint": VENDOR,
                          "ro.boot.hardware.sku": "XT2413-2", "ro.product.vendor.device": "fogona",
                          "ro.boot.slot_suffix": "_a"}.items():
        require(shell("getprop", key) == expected, "Unexpected " + key)
    boot = shell("settings", "get", "global", "boot_count")
    require(re.fullmatch(r"\d+", boot), "Unknown boot generation")
    if args.authorize_ui:
        shell("settings", "delete", "global", "tommyui_verified_target")

    active = root("bootctl", "get-active-boot-slot")
    require(active in ("0", "1"), "Unknown selected slot")

    def check_state():
        probe = subprocess.run([*adb, "shell", "su", "0", "timeout", "2", "update_engine_client", "--follow"],
                               capture_output=True, text=True, timeout=12)
        statuses = re.findall(r"onStatusUpdate\([^\n]*?\((\d+)\),", probe.stdout + probe.stderr)
        require(probe.returncode in (0, 124, 143) and statuses
                and all(s in ("0", "6") for s in statuses), "Update engine is busy or its status is unavailable")
        require(root("bootctl", "get-current-slot") == "0" and
                root("bootctl", "get-active-boot-slot") == active and
                root("bootctl", "is-slot-marked-successful", "0") == "1",
                "The running or selected slot changed")
        state = root("snapshotctl", "dump")
        require(re.search(r"(?im)^Update state:\s*(initiated|unverified)\s*$", state),
                "No staged snapshot for this transition")
        require(root("bootctl", "get-snapshot-merge-status").lower() in ("none", "snapshotted"),
                "Snapshot merge or cancellation in progress")
        require(root("cat", "/data/misc/update_engine/prefs/post-install-succeeded") == "true",
                "Android has no successful postinstall record")
        require(shell("settings", "get", "global", "boot_count") == boot, "Phone rebooted during audit")

    check_state()
    require(root("sha256sum", "/dev/block/mapper/system_a").split()[0] == SYSTEM_A, "Wrong system A")
    fixed = {line[:-2] for line in root("ls", "/dev/block/by-name").splitlines() if line.endswith("_a")}
    require(fixed == FIXED, "Fixed partition inventory changed")
    for part in sorted(fixed):
        pair = root("sha256sum", "/dev/block/by-name/" + part + "_a",
                    "/dev/block/by-name/" + part + "_b").splitlines()
        require(len(pair) == 2 and re.fullmatch(r"[0-9a-f]{64}", pair[0].split()[0])
                and pair[0].split()[0] == pair[1].split()[0],
                "Inactive firmware differs for " + part)
    left, right = (root("lpdump", "--slot=" + slot) for slot in ("0", "1"))
    for part in sorted(UNCHANGED_DYNAMIC):
        require(extents(left, part + "_a") == extents(right, part + "_b"),
                "Untouched logical partition has different backing extents: " + part)
    mapped = root("ls", "/dev/block/mapper").splitlines()
    if "system_b" not in mapped:
        require(args.authorize_ui, "system_b is unmapped. --authorize-ui can map staged snapshots for verification.")
        root("snapshotctl", "map")
    require(root("blockdev", "--getsize64", "/dev/block/mapper/system_b") == "2041208832" and
            root("sha256sum", "/dev/block/mapper/system_b").split()[0] == SYSTEM_B, "Wrong staged system B")
    check_state()
    print("Verified R2 system B, fixed firmware pairs, untouched logical extents and postinstall state.")
    if not args.authorize_ui:
        return

    scratch = "/data/local/tmp/tommy-audit-" + uuid.uuid4().hex
    final = "/data/misc/tommyui-r2-" + boot + ".metadata"
    granted = False
    try:
        subprocess.run([*adb, "push", args.metadata, scratch], check=True)
        root("cp", scratch, final)
        root("chcon", "u:object_r:ota_package_file:s0", final)
        root("chmod", "644", final)
        require(root("sha256sum", final).split()[0] == METADATA, "Metadata copy differs")
        # The client can exit zero for a successful Binder call that returned applicable=false.
        verified = subprocess.check_output([*adb, "shell", "su", "0", "update_engine_client",
            "--verify", "--metadata=" + final], text=True, stderr=subprocess.STDOUT, timeout=150)
        require("Payload is applicable." in verified and "Payload is not applicable." not in verified,
                "Android did not confirm R2 applicability")
        check_state()
        ticket = "r2:" + boot + (":selected" if active == "1" else "")
        shell("settings", "put", "global", "tommyui_verified_target", ticket)
        require(shell("settings", "get", "global", "tommyui_verified_target") == ticket,
                "Could not persist the current-boot audit ticket")
        granted = True
    finally:
        root("rm", "-f", scratch)
        if not granted:
            root("rm", "-f", final)
    print("Finish verified trial is authorized on this boot. The boot slot has not been switched.")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, subprocess.SubprocessError, IndexError) as e:
        raise SystemExit("Target audit stopped: " + str(e))
