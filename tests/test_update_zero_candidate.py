"""Test the Update Zero builder's manifest rules without Android tools or a phone."""
import runpy
import tempfile
import unittest
from pathlib import Path

MANIFEST = """block_size: 4096
partitions {
  partition_name: "system"
  new_partition_info {
    size: 4096
  }
  version: "1794182400"
}
partitions {
  partition_name: "boot"
  operations {
    type: SOURCE_COPY
  }
}
dynamic_partition_metadata {
  cow_version: 2
}
partial_update: true
"""


class CandidateTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.factory = Path(temp.name)
        script = Path(__file__).resolve().parents[1] / "ota/update-zero/build_candidate.py"
        self.api = runpy.run_path(str(script))
        steps = "".join(f'<step operation="flash" partition="{p}"/>'
                        for p in ("partition", "bootloader", "radio", "boot", "super"))
        (self.factory / "servicefile.xml").write_text(
            '<flashing><header><phone_model model="fogona_g"/><software_version '
            'version="fogona_g-user 14 U1TFS34.100-35-14-1-21 1 release-keys"/></header>'
            f'<steps>{steps}<step operation="erase" partition="userdata"/></steps></flashing>')
        for group, part in (("bootloader", "abl"), ("radio", "modem")):
            (self.factory / (group + ".img")).write_bytes(
                f'<recipe><flash partition="{part}"/></recipe>'.encode())
        self.inventory = self.factory / "inventory.txt"
        self.inventory.write_text("".join(f"{row}\n" for row in (
            "abl_a 8192 8192 " + "a" * 64 + " " + "b" * 64,
            "boot_a 4096 4096 " + "c" * 64 + " " + "d" * 64,
            "dsp_a 4096 4096 " + "e" * 64 + " " + "e" * 64,
            "modem_a 4096 4096 " + "f" * 64 + " " + "f" * 64)))

    def test_every_firmware_copy_carries_the_version(self):
        copies = self.api["copy_operations"](self.factory, self.inventory, 1794182400)
        self.assertEqual(["abl", "boot"], [c.split('"')[1] for c in copies])
        for copy in copies:
            self.assertIn('version:"1794182400"', copy)

    def test_unversioned_names_what_a_partial_update_would_reject(self):
        self.assertEqual(["boot"], self.api["unversioned"](MANIFEST))
        self.assertEqual([], self.api["unversioned"](MANIFEST.replace(
            "  operations {", '  version: "1794182400"\n  operations {')))

    def test_build_date_requires_one_numeric_system_date(self):
        build_date = self.api["build_date"]
        self.assertEqual(1794182400, build_date(["ro.build.id=X", "ro.system.build.date.utc=1794182400"]))
        for props in ([], ["ro.system.build.date.utc="], ["ro.system.build.date.utc=Sat Nov 7"]):
            with self.assertRaises(ValueError):
                build_date(props)


if __name__ == "__main__":
    unittest.main()
