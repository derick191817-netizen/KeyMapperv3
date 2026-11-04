#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PROPS="$ROOT/gradle.properties"

if ! grep -Eq '^android\.jetifier\.ignorelist=bc\.\*-jdk18on$' "$PROPS"; then
  echo 'ERRO: JARs Bouncy Castle multi-release não estão protegidos da transformação Jetifier.' >&2
  exit 1
fi

if ! grep -Eq '^android\.enableJetifier=true$' "$PROPS"; then
  echo 'ERRO: Jetifier foi alterado; esta correção pressupõe manter a compatibilidade existente.' >&2
  exit 1
fi

printf '%s\n' 'PASS: artefatos Bouncy Castle jdk18on são excluídos apenas da transformação Jetifier.'
