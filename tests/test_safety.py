import tempfile
from pathlib import Path
import unittest

from firemanager.errors import ProfileError
from firemanager.models import PackageRule, PackageState, Profile
from firemanager.safety import build_plan, is_protected, validate_profile, validate_selection


def profile(*rules):
    return Profile("id", "name", ("KFT",), ("amazon/fingerprint",), 30, "physical unit",
                   tuple(rules), Path("profile.json"))


class SafetyTest(unittest.TestCase):
    def test_core_packages_are_protected(self):
        for package in ("android", "com.android.systemui", "com.android.settings",
                        "com.google.android.permissioncontroller", "com.android.providers.settings"):
            self.assertTrue(is_protected(package), package)

    def test_optional_provider_exceptions_are_not_globally_blocked(self):
        self.assertFalse(is_protected("com.android.providers.calendar"))

    def test_fire_home_keyboard_and_ota_are_protected(self):
        for package in ("com.amazon.firelauncher", "com.amazon.device.settings",
                        "com.amazon.device.software.ota", "com.amazon.inputmethod.korean"):
            self.assertTrue(is_protected(package), package)

    def test_profile_cannot_name_core_package(self):
        value = profile(PackageRule("com.android.systemui", "safe", "System UI", "bad", default=True))
        with self.assertRaises(ProfileError):
            validate_profile(value)

    def test_only_safe_defaults_are_selected(self):
        value = profile(
            PackageRule("com.amazon.safe", "safe", "Safe", "tested", default=True),
            PackageRule("com.amazon.optional", "optional", "Optional", "choice", default=True),
        )
        plan = build_plan(value, PackageState({"com.amazon.safe", "com.amazon.optional"}, set()))
        self.assertEqual([item.selected for item in plan], [True, False])

    def test_selection_must_belong_to_profile(self):
        value = profile(PackageRule("com.amazon.safe", "safe", "Safe", "tested"))
        with self.assertRaises(ProfileError):
            validate_selection(value, ["com.amazon.not.reviewed"])


if __name__ == "__main__":
    unittest.main()
