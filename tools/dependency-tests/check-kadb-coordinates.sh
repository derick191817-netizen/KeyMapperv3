#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
APP="$ROOT/app/build.gradle.kts"
SETTINGS="$ROOT/settings.gradle.kts"
ADAPTER="$ROOT/app/src/main/java/com/example/keymapper/injection/KadbWirelessAdbDiscovery.kt"
WORKFLOW="$ROOT/.github/workflows/build.yml"

# Use the Android AAR explicitly and avoid the 2.1.5 artifact built for Java 25.
grep -Fq 'implementation("com.flyfishxu:kadb-android:2.1.4")' "$APP"
grep -Fq 'implementation("com.flyfishxu:kadb-mdns-android:2.1.3")' "$APP"
for module in bcprov bcpkix bcutil; do
  grep -Fq "implementation(\"org.bouncycastle:${module}-jdk18on:1.86\")" "$APP"
done
if grep -Eq 'com\.flyfishxu:kadb(:|\-android:2\.1\.5)|com\.flyfishxu:kadb-mdns(:|\-android:2\.1\.[45])' "$APP"; then
  echo 'ERRO: dependência Kadb Java 25 ou coordenada mDNS não confirmada.' >&2
  exit 1
fi
for symbol in KadbMdnsAndroid MdnsConfig MdnsEndpoint MdnsServiceType; do
  grep -Fq "import com.flyfishxu.kadb.mdns.$symbol" "$ADAPTER"
done
grep -Fq 'mavenCentral()' "$SETTINGS"
grep -Fq 'keymapper-0.14.65-debug' "$WORKFLOW"
grep -Fq 'versionCode = 81' "$APP"
grep -Fq 'versionName = "0.14.65"' "$APP"
printf '%s\n' 'PASS: Kadb Android AAR 2.1.4 + mDNS Android AAR 2.1.3; adapter e CI consistentes.'

# A política separada garante que Jetifier não tente analisar os JARs multi-release do Bouncy Castle.
