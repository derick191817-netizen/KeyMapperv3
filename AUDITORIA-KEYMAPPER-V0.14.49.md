# Auditoria KeyMapper 0.14.49

## Falha reproduzida
O GitHub Actions falhou em `:app:compileDebugAidl` com `Could not find com.flyfishxu:kadb-mdns:2.1.5`. O erro é de resolução de dependência, anterior à compilação do AIDL.

## Causa e correção
A origem oficial do Kadb documenta 2.1.5, mas o catálogo consultado do Maven Central identifica o módulo raiz `com.flyfishxu:kadb-mdns` em 2.1.3. A troca anterior para 2.1.4 não tinha evidência de publicação suficiente. A versão 0.14.49 usa a coordenada comprovada `com.flyfishxu:kadb-mdns:2.1.3`, mantendo `com.flyfishxu:kadb:2.1.5`, que já não era a dependência reportada como ausente.

## Arquivos alterados
- `app/build.gradle.kts`: versionCode 65, versionName 0.14.49 e dependência mDNS 2.1.3.
- `.github/workflows/build.yml`: artefato `keymapper-0.14.49-debug`.
- `tools/dependency-tests/check-kadb-coordinates.sh`: valida o par Kadb 2.1.5 + mDNS 2.1.3 e rejeita mDNS 2.1.4/2.1.5.
- `README.md` e `CHANGELOG-KEYMAPPER-V0.14.49.md`.

## Verificações locais
- Coordenadas e comentários conferidos por script.
- `tools/native-tests/run.sh`: executar novamente no ambiente atual.
- `tools/native-tests/check-syntax.sh`: executar novamente no ambiente atual.
- `tools/native-tests/check-library-contract.sh`: executar novamente no ambiente atual.
- Gradle real não executável no ambiente atual devido à rede DNS indisponível; sem afirmação de build Android aprovado.

## Pendência
O GitHub Actions deverá confirmar a resolução de `kadb-mdns:2.1.3`, depois rodar Lint, testes e APK. Se a API da 2.1.3 apresentar diferenças de compilação, usar o log seguinte para compatibilidade de API; não voltar a adivinhar coordenadas.
