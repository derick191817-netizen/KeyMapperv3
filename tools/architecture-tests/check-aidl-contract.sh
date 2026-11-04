#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
AIDL_DIR="$ROOT/app/src/main/aidl/com/example/keymapper/injection"
SERVICE="$AIDL_DIR/IKeyMapperShellService.aidl"
CALLBACK="$AIDL_DIR/IKeyMapperInputCallback.aidl"

for file in "$SERVICE" "$CALLBACK"; do
  if [[ ! -s "$file" ]]; then
    echo "ERRO: arquivo AIDL obrigatório ausente ou vazio: $file" >&2
    exit 1
  fi
done

grep -Fq 'package com.example.keymapper.injection;' "$SERVICE"
grep -Fq 'import com.example.keymapper.injection.IKeyMapperInputCallback;' "$SERVICE"
grep -Fq 'oneway interface IKeyMapperInputCallback' "$CALLBACK"
grep -Fq 'boolean startInputCapture(IKeyMapperInputCallback callback, boolean enabled)' "$SERVICE"

printf '%s\n' 'PASS: contrato AIDL de serviço e callback está completo e importado explicitamente.'
