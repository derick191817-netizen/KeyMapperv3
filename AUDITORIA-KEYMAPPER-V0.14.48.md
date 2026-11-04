# Auditoria KeyMapper 0.14.48 — dependência Kadb mDNS

**Base:** 0.14.47 / versionCode 63  
**Entrega:** 0.14.48 / versionCode 64

## Diagnóstico da falha enviada

O log do GitHub Actions identifica explicitamente `ModuleVersionNotFoundException: Could not find com.flyfishxu:kadb-mdns:2.1.5`, requerido pelo módulo `:app`. O script Kotlin já avançou e a falha ocorre na resolução de dependências durante `:app:compileDebugAidl`; portanto, não é uma regressão da migração `compilerOptions`.

A inspeção de `settings.gradle.kts` confirmou `mavenCentral()` em `dependencyResolutionManagement`. Não havia necessidade de adicionar JitPack ou outro repositório. O problema é a versão do artefato: o core Kadb 2.1.5 foi publicado em 16/10/2026, enquanto os módulos opcionais mDNS aparecem com publicação mais recente em 05/09/2026. A documentação do próprio Kadb ainda apresenta 2.1.4 para o artefato mDNS.

## Alteração

- Mantido `com.flyfishxu:kadb:2.1.5` (core), pois este artefato está publicado no Maven Central.
- Alterado o módulo opcional para `com.flyfishxu:kadb-mdns:2.1.4`, a versão correspondente à última publicação identificada para o módulo mDNS.
- Mantido `mavenCentral()` já existente.
- Preservados imports e uso `KadbMdnsAndroid`, `MdnsConfig`, `MdnsEndpoint` e `MdnsServiceType` em `WirelessAdbDialog.kt`; não foi removida a descoberta mDNS nem a integração ADB.
- Adicionado `tools/dependency-tests/check-kadb-coordinates.sh` para detectar retorno acidental à coordenada não resolvível 2.1.5 do mDNS. O script foi integrado ao workflow.
- Incrementados `versionName=0.14.48`, `versionCode=64` e artefato CI `keymapper-0.14.48-debug`.

## Arquivos modificados

- `app/build.gradle.kts`
- `.github/workflows/build.yml`
- `README.md`
- `CHANGELOG-KEYMAPPER-V0.14.48.md`
- `tools/dependency-tests/check-kadb-coordinates.sh` (novo)

## Resultado

A correção elimina a coordenada `com.flyfishxu:kadb-mdns:2.1.5` identificada no log e usa a versão publicada documentada. A resolução efetiva do Maven ainda precisa ser confirmada pelo GitHub Actions: este ambiente não possui conectividade DNS para Maven Central/Gradle e não consegue executar uma compilação Gradle. Não afirmar que Lint, `compileDebugAidl`, testes Gradle ou `assembleDebug` passaram até a nova execução remota.
