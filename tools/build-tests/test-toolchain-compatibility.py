#!/usr/bin/env python3
"""Negative tests: ensure the toolchain gate fails closed on common CI drift."""
from __future__ import annotations

import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FILES = (
    "build.gradle.kts",
    "gradle/wrapper/gradle-wrapper.properties",
    "app/build.gradle.kts",
    "gradle.properties",
    ".github/workflows/build.yml",
    "tools/build-tests/check-toolchain-compatibility.py",
)
CASES = (
    (
        "Compose desalinhado",
        "build.gradle.kts",
        'id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"',
        'id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"',
        "diverge do Kotlin",
    ),
    (
        "Gradle abaixo do mínimo",
        "gradle/wrapper/gradle-wrapper.properties",
        "gradle-8.8-bin.zip",
        "gradle-8.6-bin.zip",
        "diverge da combinação auditada",
    ),
    (
        "NDK ausente no runner",
        ".github/workflows/build.yml",
        "ndk;26.3.11579264",
        "ndk;25.2.9519653",
        "não instala NDK",
    ),
    (
        "nome de artefato divergente",
        ".github/workflows/build.yml",
        "keymapper-0.14.65-debug",
        "keymapper-0.14.54-debug",
        "nome do artefato CI",
    ),
)


def main() -> None:
    checker_relative = "tools/build-tests/check-toolchain-compatibility.py"
    for label, relative, original, replacement, expected in CASES:
        with tempfile.TemporaryDirectory(prefix="keymapper-toolchain-test-") as temporary:
            sandbox = Path(temporary)
            for item in FILES:
                destination = sandbox / item
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(ROOT / item, destination)

            fixture = sandbox / relative
            content = fixture.read_text(encoding="utf-8")
            if original not in content:
                raise AssertionError(f"fixture não encontrada para {label}")
            fixture.write_text(content.replace(original, replacement, 1), encoding="utf-8")

            result = subprocess.run(
                ["python3", str(sandbox / checker_relative)],
                text=True,
                capture_output=True,
                check=False,
            )
            combined = result.stdout + result.stderr
            if result.returncode == 0 or expected not in combined:
                raise AssertionError(
                    f"o verificador não rejeitou {label}; código={result.returncode}\n{combined}"
                )
            print(f"[PASS] Verificador rejeita: {label}")


if __name__ == "__main__":
    main()
