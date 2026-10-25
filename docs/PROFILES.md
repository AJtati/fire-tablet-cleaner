# Verified profiles

A profile is permission to change one exact tablet firmware, not a general package blacklist.

Copy `profiles/examples/profile-template.json`, replace every placeholder, and keep it outside
`profiles/verified/` while testing. A verified profile requires:

- the exact `ro.product.model`, `ro.build.fingerprint` and Android SDK;
- an explanation and user-visible effects for every package;
- `default: true` only for packages classified `safe`;
- no Android core, Amazon Home, Settings, System UI, keyboard, package installer, account,
  connectivity, OTA or recovery package;
- a successful restore test and at least one reboot on the physical tablet.

Move the file to `profiles/verified/` only after those checks. The manager deliberately performs no
debloat operation when the exact fingerprint is unknown.

Risk meanings:

- `safe`: tested with no lost tablet functionality on this exact firmware.
- `optional`: removes a feature some owners use; never selected by default.
- `advanced`: significant side effects or limited recovery paths; never selected by default.
- `essential`: documented for recognition only and always blocked from disabling.
