#!/usr/bin/env python3
"""Repeatable static guardrails for the KeyMapper crash-stability iteration.

These checks are not a substitute for Gradle compilation or device testing.
Run from the project root: python3 scripts/audit_stability_checks.py
"""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
errors = []
checks = 0

def check(condition: bool, message: str) -> None:
    global checks
    checks += 1
    if not condition:
        errors.append(message)

service = (ROOT / "app/src/main/java/com/example/keymapper/service/MappingService.kt").read_text()
capture = (ROOT / "app/src/main/java/com/example/keymapper/input/CaptureView.kt").read_text()
injector = (ROOT / "app/src/main/java/com/example/keymapper/injection/TouchInjector.kt").read_text()
engine = (ROOT / "app/src/main/java/com/example/keymapper/mapping/MappingEngine.kt").read_text()
watcher = (ROOT / "app/src/main/java/com/example/keymapper/injection/ForegroundWatcher.kt").read_text()
overlay = (ROOT / "app/src/main/java/com/example/keymapper/overlay/OverlayManager.kt").read_text()
receiver = (ROOT / "app/src/main/java/com/example/keymapper/service/BootReceiver.kt").read_text()
settings = (ROOT / "app/src/main/java/com/example/keymapper/data/AppSettings.kt").read_text()
button = (ROOT / "app/src/main/java/com/example/keymapper/data/HudButton.kt").read_text()
repository = (ROOT / "app/src/main/java/com/example/keymapper/data/SettingsRepository.kt").read_text()
app = (ROOT / "app/src/main/java/com/example/keymapper/KeyMapperApp.kt").read_text()
manifest = ROOT / "app/src/main/AndroidManifest.xml"
manifest_text = manifest.read_text()

check('Log.d("KeyMapper", "Mouse' not in service, "Per-event mouse logging remains in MappingService")
check('Log.d(TAG, "Mouse' not in capture, "Per-event mouse logging remains in CaptureView")
check('Log.d("KeyMapper", "Injetando evento' not in injector, "Per-event injection logging remains in TouchInjector")
check('runBlocking' not in receiver, "BootReceiver still blocks the broadcast thread")
check('goAsync()' in receiver and 'Dispatchers.IO' in receiver, "BootReceiver does not perform asynchronous IO")
check('START_NOT_STICKY' in service and 'return START_STICKY' not in service, "Service still requests sticky restart")
check('PARTIAL_WAKE_LOCK' not in service, "Service still holds a partial wakelock")
check('android.permission.WAKE_LOCK' not in manifest_text, "Manifest still requests WAKE_LOCK")
check('token != generation' in watcher and 'handler.postDelayed({ tick(token) }, 1000L)' in watcher,
      "Foreground watcher is missing stale-generation cancellation")
check('postOnAnimation(cursorMoveFrame)' in overlay and 'wm?.updateViewLayout(cursor, lp)' in overlay,
      "Virtual cursor is not coalesced to animation frames")
check('MAX_SETTINGS_CHARS' in settings and 'MAX_PROFILES' in settings and 'MAX_BUTTONS' in settings,
      "Settings parser is missing payload/profile/button bounds")
check('isFinite()' in button and 'takeIf { it.isFinite() }' in button,
      "HUD button parser is missing finite numeric validation")
check('settings_json_recovery_backup' in repository and 'preserveInvalidRawSettings' in repository,
      "Malformed settings are not backed up before replacement")
check('profiles = current.profiles' in repository and 'selectedProfilePackage = current.selectedProfilePackage' in repository,
      "Global saves may roll back the most recently stored profile data")
check('pauseAndReleaseAll()' in engine and 'resumeAfterConfigurationChange()' in engine and 'if (paused) return' in engine,
      "Engine lacks synchronous input freeze around profile changes")
check('addHiddenApiExemptions("L")' not in app and 'Landroid/hardware/input/IInputManager;' in app,
      "Hidden API bypass is not scoped to required system interfaces")

try:
    ET.parse(manifest)
    check(True, "AndroidManifest.xml is not well-formed XML")
except ET.ParseError as exc:
    check(False, f"AndroidManifest.xml is invalid XML: {exc}")

if errors:
    print(f"FAIL: {len(errors)} of {checks} checks failed")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)
print(f"PASS: {checks} static stability checks passed")
print("NOTE: static checks do not compile Android code or validate behavior on a device.")
