#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERIFY="$ROOT/tools/dependency-tests/verify-kadb-selected-version.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

cat > "$TMP/gradle-good" <<'GOOD'
#!/usr/bin/env bash
case "$*" in
  *kadb-android*) echo 'com.flyfishxu:kadb-android:2.1.4' ;;
  *bcprov*) echo 'org.bouncycastle:bcprov-jdk18on:1.86' ;;
  *bcpkix*) echo 'org.bouncycastle:bcpkix-jdk18on:1.86' ;;
  *bcutil*) echo 'org.bouncycastle:bcutil-jdk18on:1.86' ;;
  *) exit 2 ;;
esac
GOOD

cat > "$TMP/gradle-kadb-bad" <<'BAD'
#!/usr/bin/env bash
echo 'com.flyfishxu:kadb-android:2.1.5'
BAD

cat > "$TMP/gradle-bc-bad" <<'BCBAD'
#!/usr/bin/env bash
case "$*" in
  *kadb-android*) echo 'com.flyfishxu:kadb-android:2.1.4' ;;
  *bcprov*) echo 'org.bouncycastle:bcprov-jdk18on:1.84' ;;
  *bcpkix*) echo 'org.bouncycastle:bcpkix-jdk18on:1.86' ;;
  *bcutil*) echo 'org.bouncycastle:bcutil-jdk18on:1.86' ;;
  *) exit 2 ;;
esac
BCBAD

cat > "$TMP/gradle-missing" <<'MISSING'
#!/usr/bin/env bash
echo 'No dependencies matching requested module'
MISSING

chmod +x "$TMP"/gradle-*
GRADLEW_BIN="$TMP/gradle-good" bash "$VERIFY" >/dev/null
if GRADLEW_BIN="$TMP/gradle-kadb-bad" bash "$VERIFY" >/dev/null 2>&1; then
  echo 'ERRO: o verificador aceitou Kadb 2.1.5.' >&2
  exit 1
fi
if GRADLEW_BIN="$TMP/gradle-bc-bad" bash "$VERIFY" >/dev/null 2>&1; then
  echo 'ERRO: o verificador aceitou Bouncy Castle 1.84.' >&2
  exit 1
fi
if GRADLEW_BIN="$TMP/gradle-missing" bash "$VERIFY" >/dev/null 2>&1; then
  echo 'ERRO: o verificador aceitou ausência das dependências esperadas.' >&2
  exit 1
fi

printf '%s\n' 'PASS: verificador aceita Kadb 2.1.4 + BC 1.86 e rejeita versões incompatíveis/vulneráveis.'
