"""Exercise the private audit gate with a simulated phone and no device writes."""
import hashlib
import runpy
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


class AuditTest(unittest.TestCase):
    def setUp(self):
        script = Path(__file__).resolve().parents[1] / "ota/update-zero/audit_staged_r2.py"
        self.env = runpy.run_path(script)["main"].__globals__
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.file = Path(temp.name) / "candidate-r2.metadata"
        self.file.write_bytes(b"fixture")
        self.env["METADATA"] = hashlib.sha256(b"fixture").hexdigest()
        self.calls = []
        self.wrong_system = self.wrong_pair = self.bad_verify = self.different_extent = False
        self.selected = "0"
        self.state = "initiated"
        self.prefs = "true"
        self.status = 0
        mocks = [patch.object(sys, "argv", [str(script), "--serial", "fake", str(self.file), "--authorize-ui"]),
                 patch.object(subprocess, "check_output", side_effect=self.command),
                 patch.object(subprocess, "run", side_effect=lambda argv, **kw:
                     subprocess.CompletedProcess(argv, 124, "", f"onStatusUpdate(state ({self.status}), 0)")
                     if "--follow" in argv else subprocess.CompletedProcess(argv, 0))]
        for mock in mocks:
            mock.start()
            self.addCleanup(mock.stop)

    def command(self, argv, **kwargs):
        args = list(argv)[4:]
        if args[:2] == ["su", "0"]:
            args = args[2:]
        self.calls.append(args)
        if args[0] == "getprop":
            return {"ro.build.version.incremental": self.env["SOURCE"],
                    "ro.vendor.build.fingerprint": self.env["VENDOR"], "ro.boot.hardware.sku": "XT2413-2",
                    "ro.product.vendor.device": "fogona", "ro.boot.slot_suffix": "_a"}[args[1]]
        if args[0] == "settings":
            if args[1] == "get":
                return "27" if args[-1] == "boot_count" else self.grants()[-1]
            return ""
        if args[0] == "bootctl":
            return {"get-current-slot": "0", "get-active-boot-slot": self.selected,
                    "is-slot-marked-successful": "1", "get-snapshot-merge-status": "none"}[args[1]]
        if args[0] == "snapshotctl": return "Update state: " + self.state
        if args[0] == "cat": return self.prefs
        if args[0] == "ls":
            if args[1] == "/dev/block/mapper": return "system_a\nsystem_b"
            return "\n".join(n + "_a" for n in self.env["FIXED"])
        if args[0] == "sha256sum":
            if args[1].endswith("/system_a"): return self.env["SYSTEM_A"] + "  system_a"
            if args[1].endswith("/system_b"): return ("bad" if self.wrong_system else self.env["SYSTEM_B"]) + "  system_b"
            if len(args) == 2: return self.env["METADATA"] + "  metadata"
            return "a" * 64 + "  a\n" + ("b" if self.wrong_pair else "a") * 64 + "  b"
        if args[0] == "lpdump":
            slot = "b" if args[-1].endswith("1") else "a"
            start = 2 if slot == "b" and self.different_extent else 1
            return "\n".join(f"  Name: {n}_{slot}\n  Group: g\n  Extents:\n    0 .. 7 linear super {start}\n------"
                             for n in self.env["UNCHANGED_DYNAMIC"])
        if args[0] == "blockdev": return "2041208832"
        if args[0] == "update_engine_client":
            return "Payload is not applicable." if self.bad_verify else "Payload is applicable."
        if args[0] in ("cp", "chcon", "chmod", "rm"): return ""
        self.fail(str(args))

    def grants(self):
        return [args[-1] for args in self.calls if args[:2] == ["settings","put"]]

    def test_valid_stage_authorizes_only_this_boot(self):
        self.env["main"]()
        self.assertEqual(self.grants(), ["r2:27"])

    def test_already_selected_requires_same_audit(self):
        self.selected = "1"
        self.env["main"]()
        self.assertEqual(self.grants(), ["r2:27:selected"])

    def test_failed_checks_do_not_authorize(self):
        for attr, value in [("state","none"), ("prefs","false"), ("status",3), ("wrong_pair",True),
                            ("different_extent",True), ("wrong_system",True), ("bad_verify",True)]:
            with self.subTest(attr=attr), patch.object(self, attr, value):
                self.calls.clear()
                with self.assertRaises(ValueError):
                    self.env["main"]()
                self.assertEqual(self.grants(), [])


if __name__ == "__main__":
    unittest.main()
