from __future__ import annotations

from pathlib import Path
import tkinter as tk
from tkinter import filedialog, messagebox, ttk

from .adb import AdbClient
from .backup import create_backup, default_backup_root
from .errors import FireManagerError
from .executor import disable_packages, restore_backup
from .launcher import install_launcher
from .probe import package_state, probe_device
from .profiles import compatibility, load_profiles
from .safety import build_plan, validate_selection

ROOT = Path(__file__).resolve().parents[2]


class ManagerWindow(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("Fire Tablet Cleaner")
        self.geometry("980x650")
        self.minsize(780, 500)
        self.client: AdbClient | None = None
        self.info = None
        self.match = None
        self.state = None
        self.serial_by_label: dict[str, str] = {}
        self._build()
        self.after(100, self.refresh_devices)

    def _build(self) -> None:
        top = ttk.Frame(self, padding=12)
        top.pack(fill="x")
        ttk.Label(top, text="Connected tablet:").pack(side="left")
        self.device_box = ttk.Combobox(top, state="readonly", width=45)
        self.device_box.pack(side="left", padx=8)
        self.device_box.bind("<<ComboboxSelected>>", lambda _event: self.scan())
        ttk.Button(top, text="Refresh", command=self.refresh_devices).pack(side="left")
        ttk.Button(top, text="Install launcher…", command=self.install).pack(side="right")

        self.device_text = tk.StringVar(value="Connect a Fire tablet with USB debugging enabled.")
        ttk.Label(self, textvariable=self.device_text, padding=(12, 0), wraplength=930).pack(fill="x")
        self.compat_text = tk.StringVar(value="")
        ttk.Label(self, textvariable=self.compat_text, padding=(12, 6), foreground="#8a3d00", wraplength=930).pack(fill="x")

        columns = ("title", "package", "risk", "status", "effects")
        self.tree = ttk.Treeview(self, columns=columns, show="headings", selectmode="extended")
        widths = {"title": 170, "package": 250, "risk": 80, "status": 90, "effects": 300}
        for column in columns:
            self.tree.heading(column, text=column.title())
            self.tree.column(column, width=widths[column], anchor="w")
        self.tree.pack(fill="both", expand=True, padx=12, pady=6)

        bottom = ttk.Frame(self, padding=12)
        bottom.pack(fill="x")
        ttk.Label(bottom, text="Select rows to disable. Safe defaults are preselected only for exact verified profiles.").pack(side="left")
        self.restore_button = ttk.Button(bottom, text="Restore backup…", command=self.restore)
        self.restore_button.pack(side="right")
        self.apply_button = ttk.Button(bottom, text="Back up and disable selected", command=self.apply, state="disabled")
        self.apply_button.pack(side="right", padx=8)

    def _error(self, exc: Exception) -> None:
        messagebox.showerror("Fire Tablet Cleaner", str(exc), parent=self)

    def refresh_devices(self) -> None:
        try:
            self.client = AdbClient()
            devices = self.client.devices()
            self.serial_by_label.clear()
            labels = []
            for device in devices:
                label = f"{device.model_hint or device.serial} [{device.state}]"
                labels.append(label)
                self.serial_by_label[label] = device.serial
            self.device_box["values"] = labels
            if labels:
                self.device_box.current(0)
                self.scan()
            else:
                self.device_text.set("No ADB devices found. Enable Developer Options and USB debugging, then reconnect.")
                self.compat_text.set("")
                self.apply_button.configure(state="disabled")
        except (FireManagerError, ValueError, OSError) as exc:
            self._error(exc)

    def selected_serial(self) -> str:
        label = self.device_box.get()
        if label not in self.serial_by_label:
            raise FireManagerError("Select a connected tablet.")
        return self.serial_by_label[label]

    def scan(self) -> None:
        try:
            assert self.client
            serial = self.selected_serial()
            device = next(device for device in self.client.devices() if device.serial == serial)
            if device.state != "device":
                raise FireManagerError(f"The tablet is {device.state}. Accept the USB debugging prompt on it.")
            self.info = probe_device(self.client, serial)
            self.state = package_state(self.client, serial)
            self.match = compatibility(self.info, load_profiles(ROOT / "profiles" / "verified"))
            self.device_text.set(f"{self.info.display_name} — SDK {self.info.sdk}\n{self.info.fingerprint}")
            self.compat_text.set(self.match.reason)
            self.tree.delete(*self.tree.get_children())
            if self.match.profile:
                defaults = []
                for item in build_plan(self.match.profile, self.state):
                    if not item.installed:
                        continue
                    iid = item.rule.package
                    status = "disabled" if item.disabled else "enabled"
                    self.tree.insert("", "end", iid=iid, values=(item.rule.title, item.rule.package,
                                     item.rule.risk, status, item.rule.effects))
                    if item.selected:
                        defaults.append(iid)
                self.tree.selection_set(defaults)
            self.apply_button.configure(state="normal" if self.match.operations_allowed else "disabled")
        except (FireManagerError, StopIteration) as exc:
            self._error(exc)

    def apply(self) -> None:
        try:
            assert self.client and self.info and self.match and self.match.profile and self.state
            selected = list(self.tree.selection())
            if not selected:
                raise FireManagerError("Select at least one package.")
            selected = validate_selection(self.match.profile, selected)
            details = "\n".join(f"• {package}" for package in selected)
            if not messagebox.askyesno("Confirm reversible changes",
                    f"The following packages will be disabled for user 0:\n\n{details}\n\nA recovery file will be saved first.", parent=self):
                return
            backup = create_backup(default_backup_root(), self.info, self.state, self.match.profile, selected)
            done = disable_packages(self.client, self.info.serial, selected, backup)
            messagebox.showinfo("Completed", f"Disabled {len(done)} packages.\nRecovery file:\n{backup}", parent=self)
            self.scan()
        except (FireManagerError, ValueError, OSError) as exc:
            self._error(exc)

    def restore(self) -> None:
        path = filedialog.askopenfilename(title="Choose a recovery JSON file", filetypes=[("JSON", "*.json")])
        if not path:
            return
        try:
            assert self.client
            serial = self.selected_serial()
            info = probe_device(self.client, serial)
            restored = restore_backup(self.client, serial, Path(path), info.fingerprint)
            messagebox.showinfo("Restored", f"Restored {len(restored)} package states.", parent=self)
            self.scan()
        except (FireManagerError, ValueError, OSError) as exc:
            self._error(exc)

    def install(self) -> None:
        default = ROOT / "launcher" / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"
        path = filedialog.askopenfilename(title="Choose launcher APK", initialdir=default.parent,
                                          filetypes=[("Android APK", "*.apk")])
        if not path:
            return
        try:
            assert self.client
            result = install_launcher(self.client, self.selected_serial(), Path(path))
            messagebox.showinfo("Launcher installed", result + "\n\nAmazon Home was left enabled.", parent=self)
        except (FireManagerError, ValueError, OSError) as exc:
            self._error(exc)


def main() -> None:
    ManagerWindow().mainloop()


if __name__ == "__main__":
    main()
