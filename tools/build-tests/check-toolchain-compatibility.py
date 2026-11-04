#!/usr/bin/env python3
"""Fail fast when the Android/Gradle/Kotlin CI toolchain drifts out of its verified range."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        fail(f"arquivo obrigatório ausente: {relative}")
    return path.read_text(encoding="utf-8")


def require(pattern: str, source: str, description: str, flags: int = 0) -> re.Match[str]:
    match = re.search(pattern, source, flags)
    if not match:
        fail(f"não foi possível confirmar {description}")
    return match


def version_tuple(value: str) -> tuple[int, ...]:
    try:
        return tuple(int(part) for part in value.split("."))
    except ValueError:
        fail(f"versão inválida: {value}")


def main() -> None:
    root_gradle = read("build.gradle.kts")
    wrapper = read("gradle/wrapper/gradle-wrapper.properties")
    app_gradle = read("app/build.gradle.kts")
    workflow = read(".github/workflows/build.yml")
    gradle_properties = read("gradle.properties")
    if "android.jetifier.ignorelist=bc.*-jdk18on" not in gradle_properties:
        fail("Jetifier ignorelist para JARs multi-release Bouncy Castle está ausente")

    agp = require(
        r'id\("com\.android\.application"\)\s+version\s+"([0-9.]+)"',
        root_gradle, "versão do Android Gradle Plugin",
    ).group(1)
    kotlin = require(
        r'id\("org\.jetbrains\.kotlin\.android"\)\s+version\s+"([0-9.]+)"',
        root_gradle, "versão do Kotlin Gradle Plugin",
    ).group(1)
    compose = require(
        r'id\("org\.jetbrains\.kotlin\.plugin\.compose"\)\s+version\s+"([0-9.]+)"',
        root_gradle, "versão do plugin Compose",
    ).group(1)
    gradle = require(
        r'distributionUrl=.*?/gradle-([0-9.]+)-bin\.zip',
        wrapper, "distribuição Gradle do wrapper",
    ).group(1)
    require(r"distributionSha256Sum=[0-9a-f]{64}", wrapper, "checksum SHA-256 do Gradle Wrapper")

    # Keep the pinned Android/Kotlin plugins and enforce the runner's Gradle minimum.
    # KGP 2.4.21 supports Gradle 7.6.3–9.7.0; the CI log requires >= 8.14.4.
    if agp != "8.5.2" or kotlin != "2.4.21":
        fail(f"AGP/Kotlin não correspondem à combinação validada: {(agp, kotlin)}")
    if compose != kotlin:
        fail(f"plugin Compose {compose} diverge do Kotlin {kotlin}")
    if version_tuple(gradle) < version_tuple("8.14.4"):
        fail(f"Gradle {gradle} abaixo do mínimo 8.14.4 exigido pelo pipeline Kotlin/Gradle")
    if not (version_tuple("7.6.3") <= version_tuple(gradle) <= version_tuple("9.7.0")):
        fail(f"Gradle {gradle} fora da faixa oficial suportada pelo KGP {kotlin} (7.6.3–9.7.0)")

    require(r"java-version:\s*['\"]17['\"]", workflow, "JDK 17 no workflow")
    require(r"sourceCompatibility\s*=\s*JavaVersion\.VERSION_17", app_gradle, "sourceCompatibility Java 17")
    require(r"targetCompatibility\s*=\s*JavaVersion\.VERSION_17", app_gradle, "targetCompatibility Java 17")
    require(r"jvmTarget\s*=\s*org\.jetbrains\.kotlin\.gradle\.dsl\.JvmTarget\.fromTarget\(\"17\"\)", app_gradle, "bytecode Kotlin JVM 17")

    compile_sdk = require(r"compileSdk\s*=\s*(\d+)", app_gradle, "compileSdk").group(1)
    ndk = require(r'ndkVersion\s*=\s*"([0-9.]+)"', app_gradle, "NDK version").group(1)
    cmake = require(r'version\s*=\s*"([0-9.]+)"', app_gradle, "CMake version").group(1)
    for package, description in (
        (f"platforms;android-{compile_sdk}", "plataforma Android SDK"),
        ("build-tools;34.0.0", "Android Build Tools"),
        (f"ndk;{ndk}", "NDK"),
        (f"cmake;{cmake}", "CMake"),
    ):
        if package not in workflow:
            fail(f"workflow não instala {description} correspondente ao build.gradle.kts: {package}")

    code = require(r"versionCode\s*=\s*(\d+)", app_gradle, "versionCode").group(1)
    name = require(r'versionName\s*=\s*"([0-9.]+)"', app_gradle, "versionName").group(1)
    if (code, name) != ("82", "0.14.66"):
        fail(f"versão do módulo {(code, name)} diverge da release auditada (82, 0.14.66)")
    artifact = f"keymapper-{name}-debug"
    if f"name: {artifact}" not in workflow:
        fail(f"nome do artefato CI não corresponde ao versionName: esperado {artifact}")
    if not re.search(r"versionCode\s*=\s*\d+", app_gradle):
        fail("versionCode ausente")

    print("[PASS] Matriz Kotlin/AGP/Gradle dentro das faixas verificadas")
    print(f"[PASS] AGP {agp} | Kotlin/Compose {kotlin} | Gradle {gradle}")
    print(f"[PASS] JDK/JVM 17 | compileSdk {compile_sdk} | NDK {ndk} | CMake {cmake}")
    print(f"[PASS] versionCode {code} | versionName {name} | artefato {artifact}")


if __name__ == "__main__":
    main()
