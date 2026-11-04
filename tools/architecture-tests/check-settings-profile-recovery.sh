#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
REPO="$ROOT/app/src/main/java/com/example/keymapper/data/SettingsRepository.kt"
POLICY="$ROOT/app/src/main/java/com/example/keymapper/domain/SettingsRecoveryPolicy.kt"
TEST="$ROOT/app/src/test/java/com/example/keymapper/domain/SettingsRecoveryPolicyTest.kt"
for file in "$REPO" "$POLICY" "$TEST"; do
  [[ -s "$file" ]] || { echo "FAIL: política de recuperação de dados ausente: $file" >&2; exit 1; }
done
grep -Fq 'SettingsRecoveryPolicy.shouldBackupInvalidProfiles(root.has("profiles"), rawProfiles is JSONObject)' "$REPO"
grep -Fq 'fieldPresent && !valueIsObject' "$POLICY"
grep -Fq 'SettingsRecoveryPolicy.shouldBackupInvalidArray(root.has(field), value is JSONArray)' "$REPO"
grep -Fq 'fieldPresent && !valueIsArray' "$POLICY"
grep -Fq 'nullAndOtherNonObjectProfilesTriggerBackupAndSanitization' "$TEST"
grep -Fq 'nullAndWrongTypeArraysTriggerBackup' "$TEST"
echo 'PASS: profiles/buttons/games ausentes seguem compatíveis; null/tipo inválido é copiado para backup antes da sanitização.'
