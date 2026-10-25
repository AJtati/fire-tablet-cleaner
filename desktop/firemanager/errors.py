class FireManagerError(Exception):
    """Expected operational error safe to show to a user."""


class AdbError(FireManagerError):
    """ADB could not complete a requested operation."""


class ProfileError(FireManagerError):
    """A compatibility profile is invalid or does not match."""
