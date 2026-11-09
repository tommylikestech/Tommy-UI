"""Test pre-flash decisions without opening a USB connection."""
import json
import runpy
import shutil
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


class UsbUpdaterTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        folder = Path(temp.name)
        script = Path(__file__).resolve().parents[1] / "updater/Tommy-USB-Updater.pyw"
        self.image = folder / "system.img"
        (folder / "Tommy-UI-v1.003-build.json").write_text(json.dumps({
            "build_number": "TommyUI.v1.003.dev.20261108",
            "image": self.image.name, "image_sha256": "0" * 64,
        }))
        self.api = runpy.run_path(shutil.copy(script, folder))
        self.env = self.api["inspect"].__globals__
        self.props = {"ro.product.vendor.device": "fogona", "ro.boot.hardware.sku": "XT2413-2",
                      "ro.build.version.incremental": self.api["BASE"]}
        self.calls = []
        self.adb = "List of devices attached\nphone device"
        mocks = patch.dict(self.env, {"run": self.command})
        mocks.start()
        self.addCleanup(mocks.stop)
        which = patch.object(shutil, "which", return_value="platform-tool")
        which.start()
        self.addCleanup(which.stop)

    def command(self, *args, **kwargs):
        self.calls.append(args)
        if args == ("adb", "devices"):
            return self.adb
        if args[:5] == ("adb", "-s", "phone", "shell", "getprop"):
            return self.props[args[-1]]
        self.fail(f"Unexpected device operation: {args}")

    def test_check_reads_hardware_and_version(self):
        self.assertEqual(self.api["inspect"](), ("phone", self.api["BASE"]))

    def test_unauthorized_phone(self):
        self.adb = "List of devices attached\nphone unauthorized"
        with self.assertRaisesRegex(RuntimeError, "accept"):
            self.api["inspect"]()

    def test_other_model_blocked(self):
        self.props["ro.boot.hardware.sku"] = "XT9999"
        with self.assertRaisesRegex(RuntimeError, "XT2413-2"):
            self.api["install"]("phone", self.fail)

    def test_other_base_blocked(self):
        self.props["ro.build.version.incremental"] = self.api["EXPECTED"]
        with self.assertRaisesRegex(RuntimeError, "base version"):
            self.api["install"]("phone", self.fail)

    def test_corrupt_image_never_reboots(self):
        self.image.write_bytes(b"bad image")
        with self.assertRaisesRegex(RuntimeError, "checksum"):
            self.api["install"]("phone", self.fail)


if __name__ == "__main__":
    unittest.main()
