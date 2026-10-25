# Physical-device acceptance test

Do not call a profile verified until all steps pass on the exact fingerprint it names.

1. Run `fire-tablet-manager-cli probe <serial>` and save the non-secret model, SDK and fingerprint.
2. Run `scan`; an unknown fingerprint must report inventory-only mode.
3. Copy the profile template outside `profiles/verified`, investigate packages with
   `adb shell dumpsys package PACKAGE`, and never launch factory/test packages.
4. Back up, disable one candidate, reboot, and test Wi-Fi, Settings, keyboard, rotation, lock/unlock,
   audio, camera, Bluetooth, Appstore, Amazon account and OTA settings.
5. Restore the recovery JSON and prove the package returns to its original state.
6. Install the launcher. Test portrait and landscape, search, favourites, app launches, Home,
   process recreation and reboot. Amazon Home must remain enabled.
7. Repeat for every package and test the final group for several days.
8. Revoke USB debugging authorisations and turn USB debugging off after the session.

An emulator proves ordinary Android behavior only; it cannot verify Amazon package safety or Home
replacement behavior.
