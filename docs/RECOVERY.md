# Recovery

Every apply operation writes a JSON recovery record before sending the first change. Its default
location is the operating system's per-user application-state folder under
`fire-tablet-cleaner/backups/<hashed-device-id>/`.

## Restore with the GUI

Connect the same tablet and choose **Restore backup…**, then select the JSON file. The firmware
fingerprint must match the record.

## Restore with the CLI

```sh
fire-tablet-manager-cli restore <adb-serial> /path/to/recovery.json
```

## Manual emergency restore

If the desktop application is unavailable, open the JSON and inspect `states`. For every package
whose original state is `enabled`:

```sh
adb -s <adb-serial> shell pm enable PACKAGE_NAME
```

Never disable Amazon Home in a profile. Keeping it enabled ensures the tablet retains a usable Home
screen if the custom launcher cannot start. After recovery, reboot and verify Settings, Wi-Fi,
keyboard, lock screen, Appstore and account access before making any further change.
