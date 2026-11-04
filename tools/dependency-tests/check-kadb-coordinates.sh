#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
APP="$ROOT/app/build.gradle.kts"
SETTINGS="$ROOT/settings.gradle.kts"
ADAPTER="$ROOT/app/src/main/java/com/example/keymapper/injection/KadbWirelessAdbDiscovery.kt"
WORKFLOW="$ROOT/.github/workflows/build.yml"

# Keep the Android target on the explicitly published Android AAR.
grep -Fq 'implementation("com.flyfishxu:kadb:2.1.5")' "$APP"
grep -Fq 'implementation("com.flyfishxu:kadb-mdns-android:2.1.3")' "$APP"
if grep -Eq 'com\.flyfishxu:kadb-mdns:|com\.flyfishxu:kadb-mdns-android:2\.1\.[45]' "$APP"; then
  echo 'ERRO: coordenada mDNS ambígua ou não confirmada no Maven Central.' >&2
  exit 1
fi
for symbol in KadbMdnsAndroid MdnsConfig MdnsEndpoint MdnsServiceType; do
  grep -Fq "import com.flyfishxu.kadb.mdns.$symbol" "$ADAPTER"
done
grep -Fq 'mavenCentral()' "$SETTINGS"
grep -Fq 'keymapper-0.14.55-debug' "$WORKFLOW"
grep -Fq 'versionCode = 71' "$APP"
grep -Fq 'versionName = "0.14.55"' "$APP"
printf '%s\n' 'PASS: Kadb core 2.1.5 + Android mDNS AAR 2.1.3; adapter, imports e CI consistentes.'
