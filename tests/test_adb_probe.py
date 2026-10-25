import os
from pathlib import Path
import stat
import tempfile
import unittest

from firemanager.adb import AdbClient, find_adb
from firemanager.models import AdbDevice
from firemanager.probe import parse_props, probe_device


class FakeClient:
    def shell(self, serial, *args):
        self.last = (serial, args)
        return "\n".join((
            "[ro.product.model]: [KFTUWI]",
            "[ro.product.manufacturer]: [Amazon]",
            "[ro.product.device]: [tungsten]",
            "[ro.build.version.sdk]: [30]",
            "[ro.build.version.release]: [11]",
            "[ro.build.version.fireos]: [8.3.2.1]",
            "[ro.build.fingerprint]: [Amazon/KFTUWI/build]",
        ))


class ProbeTest(unittest.TestCase):
    def test_property_parser_ignores_noise(self):
        self.assertEqual(parse_props("noise\n[a]: [b]\n"), {"a": "b"})

    def test_probe_reads_identity(self):
        info = probe_device(FakeClient(), "serial")
        self.assertEqual((info.model, info.sdk, info.fire_os), ("KFTUWI", 30, "8.3.2.1"))
        self.assertEqual(info.fingerprint, "Amazon/KFTUWI/build")


class AdbTest(unittest.TestCase):
    def fake_adb(self, folder: Path) -> Path:
        path = folder / "adb"
        path.write_text("#!/bin/sh\nprintf 'List of devices attached\\nSERIAL device product:x model:KFTUWI transport_id:4\\n'\n")
        path.chmod(path.stat().st_mode | stat.S_IXUSR)
        return path

    def test_explicit_adb_path_wins(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = self.fake_adb(Path(tmp))
            self.assertEqual(find_adb(str(path)), str(path.resolve()))

    def test_devices_parser_keeps_state_and_model(self):
        with tempfile.TemporaryDirectory() as tmp:
            client = AdbClient(str(self.fake_adb(Path(tmp))))
            self.assertEqual(client.devices(), [AdbDevice("SERIAL", "device", "KFTUWI", "4")])


if __name__ == "__main__":
    unittest.main()
