"""Exercise the flash helper with a simulated fastboot executable. No device connection."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

HELPER = Path(__file__).resolve().parents[1] / "updater/flash_later.py"
FAKE = """#!/usr/bin/env python3
import json,os,sys
from pathlib import Path
folder=Path(os.environ["MOCK_FASTBOOT_DIR"])
args=sys.argv[1:]
with (folder/"calls").open("a") as f: f.write(json.dumps(args)+"\\n")
if args == ["devices"]: print("simulated fastboot"); sys.exit()
assert args[:2] == ["-s","simulated"]
args=args[2:]
if args[0]=="getvar":
 key=args[1]
 values={"product":"fogona","current-slot":"a","is-userspace":"yes","unlocked":"yes",
 "snapshot-update-status":os.environ.get("MOCK_SNAPSHOT","none"),"is-logical:system_a":"yes",
 "partition-size:super":"0x190000000","partition-size:system_a":(folder/"size").read_text()}
 print(key+": "+values[key])
elif args[0]=="resize-logical-partition":
 assert args[1]=="system_a"
 (folder/"size").write_text(args[2])
elif args[0]=="flash":
 assert args[1]=="system_a"
 print("FAILED (simulated write error)" if os.environ.get("MOCK_FAIL") else "OKAY")
else: assert args==["reboot"]
"""

@unittest.skipIf(os.name != "posix", "Uses a temporary POSIX command shim")
class FlashHelperTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)
        self.image = self.folder / "fixture.img"
        self.image.write_bytes(bytes(8192))
        (self.folder / "Tommy-UI-v1.003-build.json").write_text(json.dumps({
            "build_number": "TommyUI.v1.003.dev.20261106", "image_bytes": 8192,
            "image_sha256": hashlib.sha256(self.image.read_bytes()).hexdigest()}))
        self.fake = self.folder / "simulated-fastboot"
        self.fake.write_text(FAKE)
        self.fake.chmod(0o700)
        (self.folder / "size").write_text("2041208832")

    def commands(self, *options, **env):
        result = subprocess.run([sys.executable, HELPER, "--fastboot", self.fake,
                                 "--image", self.image, *options], capture_output=True,
                                env=dict(os.environ, MOCK_FASTBOOT_DIR=str(self.folder), **env))
        calls = [json.loads(x) for x in (self.folder / "calls").read_text().splitlines()]
        writes = [c[2:] for c in calls if len(c) > 2 and c[2] != "getvar"]
        return result.returncode, writes

    def test_replaced_device_prevents_writes(self):
        status, writes = self.commands('--flash', '--serial', 'different')
        self.assertNotEqual(status, 0)
        self.assertEqual(writes, [])

    def test_check_only(self):
        self.assertEqual(self.commands(), (0, []))

    def test_snapshot_prevents_writes(self):
        status, writes = self.commands("--flash", MOCK_SNAPSHOT="merging")
        self.assertNotEqual(status, 0)
        self.assertEqual(writes, [])

    def test_only_system_written_then_rebooted(self):
        status, writes = self.commands("--flash")
        self.assertEqual(status, 0)
        self.assertEqual(writes, [["resize-logical-partition", "system_a", "8192"],
                                  ["flash", "system_a", str(self.image)], ["reboot"]])

    def test_fastboot_reports_failure_with_zero_exit(self):
        status, writes = self.commands("--flash", MOCK_FAIL="1")
        self.assertNotEqual(status, 0)
        self.assertEqual(writes[-1][0], "flash")

if __name__ == "__main__":
    unittest.main()
