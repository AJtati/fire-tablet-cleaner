from pathlib import Path
import tempfile
import unittest

from firemanager.backup import create_backup, read_backup
from firemanager.executor import disable_packages, restore_backup
from firemanager.models import DeviceInfo, PackageRule, PackageState, Profile


class FakeAdb:
    def __init__(self):
        self.installed = {"com.amazon.a", "com.amazon.b"}
        self.disabled = {"com.amazon.b"}
        self.commands = []

    def shell(self, serial, *args, **kwargs):
        self.commands.append(args)
        if args[:3] == ("pm", "disable-user", "--user"):
            self.disabled.add(args[-1])
            return "Package new state: disabled-user\n"
        if args[:4] == ("pm", "enable", "--user", "0"):
            self.disabled.discard(args[-1])
            return "Package new state: enabled\n"
        if args[:4] == ("pm", "list", "packages", "--user"):
            names = self.disabled if args[-1] == "-d" else self.installed
            return "".join(f"package:{name}\n" for name in sorted(names))
        raise AssertionError(args)


class RecoveryTest(unittest.TestCase):
    def setUp(self):
        self.info = DeviceInfo("secret-serial", "KFT", "Amazon", 30, "11", "8", "amazon/kft/build")
        self.profile = Profile("id", "name", ("KFT",), ("amazon/kft/build",), 30, "unit", (
            PackageRule("com.amazon.a", "safe", "A", "test"),
            PackageRule("com.amazon.b", "safe", "B", "test"),
        ), Path("profile.json"))

    def test_backup_does_not_store_serial_and_restore_replays_original_states(self):
        client = FakeAdb()
        with tempfile.TemporaryDirectory() as tmp:
            path = create_backup(Path(tmp), self.info, PackageState(client.installed, client.disabled),
                                 self.profile, ["com.amazon.a", "com.amazon.b"])
            self.assertNotIn("secret-serial", path.read_text(encoding="utf-8"))
            disable_packages(client, "secret-serial", ["com.amazon.a"], path)
            self.assertIn("com.amazon.a", client.disabled)
            restored = restore_backup(client, "secret-serial", path, self.info.fingerprint)
            self.assertEqual(restored, ["com.amazon.a", "com.amazon.b"])
            self.assertNotIn("com.amazon.a", client.disabled)
            self.assertIn("com.amazon.b", client.disabled)

    def test_completed_steps_are_journaled(self):
        client = FakeAdb()
        with tempfile.TemporaryDirectory() as tmp:
            path = create_backup(Path(tmp), self.info, PackageState(client.installed, client.disabled),
                                 self.profile, ["com.amazon.a"])
            disable_packages(client, "serial", ["com.amazon.a"], path)
            self.assertEqual(read_backup(path)["completed"], ["com.amazon.a"])


if __name__ == "__main__":
    unittest.main()
