import json
from pathlib import Path
import tempfile
import unittest

from firemanager.errors import ProfileError
from firemanager.models import DeviceInfo
from firemanager.profiles import compatibility, load_profile


class ProfileTest(unittest.TestCase):
    def write(self, folder: Path, **changes):
        value = {
            "id": "kft-build", "name": "Test build", "models": ["KFT"],
            "fingerprints": ["amazon/kft/build"], "sdk": 30, "tested_on": "unit",
            "packages": [{"package": "com.amazon.optional", "risk": "safe", "title": "Optional",
                          "reason": "tested", "default": True}],
        }
        value.update(changes)
        path = folder / "profile.json"
        path.write_text(json.dumps(value), encoding="utf-8")
        return path

    def info(self, fingerprint="amazon/kft/build", sdk=30):
        return DeviceInfo("secret", "KFT", "Amazon", sdk, "11", "8", fingerprint)

    def test_exact_match_allows_operations(self):
        with tempfile.TemporaryDirectory() as tmp:
            loaded = load_profile(self.write(Path(tmp)))
            result = compatibility(self.info(), [loaded])
            self.assertTrue(result.operations_allowed)
            self.assertEqual(result.profile.profile_id, "kft-build")

    def test_model_match_with_other_fingerprint_is_inventory_only(self):
        with tempfile.TemporaryDirectory() as tmp:
            loaded = load_profile(self.write(Path(tmp)))
            result = compatibility(self.info("amazon/kft/new-ota"), [loaded])
            self.assertFalse(result.operations_allowed)
            self.assertIn("fingerprint", result.reason)

    def test_empty_fingerprint_list_is_invalid(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(ProfileError):
                load_profile(self.write(Path(tmp), fingerprints=[]))


if __name__ == "__main__":
    unittest.main()
