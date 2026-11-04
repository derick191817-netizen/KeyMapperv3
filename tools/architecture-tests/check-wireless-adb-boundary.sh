#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
UI="$ROOT/app/src/main/java/com/example/keymapper/ui/WirelessAdbDialog.kt"
CONTRACT="$ROOT/app/src/main/java/com/example/keymapper/injection/WirelessAdbDiscoveryContract.kt"
POLICY="$ROOT/app/src/main/java/com/example/keymapper/injection/WirelessAdbDiscoveryPolicy.kt"
ADAPTER="$ROOT/app/src/main/java/com/example/keymapper/injection/KadbWirelessAdbDiscovery.kt"

for file in "$UI" "$CONTRACT" "$POLICY" "$ADAPTER"; do
  test -s "$file" || { echo "ERRO: arquivo arquitetural ausente: $file" >&2; exit 1; }
done
if grep -Eq 'import com\.flyfishxu\.kad[b]?\.mdns\.' "$UI"; then
  echo 'ERRO: a tela Compose não pode importar tipos do fornecedor mDNS diretamente.' >&2
  exit 1
fi
grep -Fq 'WirelessAdbDiscoveryFactory.create' "$UI"
grep -Fq 'WirelessAdbDiscoveryPolicy.sanitize' "$UI"
grep -Fq 'interface WirelessAdbDiscovery' "$CONTRACT"
grep -Fq 'class KadbWirelessAdbDiscovery' "$ADAPTER"
grep -Fq 'MdnsServiceType.TLS_CONNECT' "$ADAPTER"
grep -Fq 'MdnsServiceType.TLS_PAIRING' "$ADAPTER"
printf '%s\n' 'PASS: fronteira UI -> contrato interno -> adaptador Kadb preservada.'
