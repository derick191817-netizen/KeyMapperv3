# KeyMapper 0.14.48 — correção da dependência mDNS do Kadb

**VersionCode:** `64`

## Causa da falha

O GitHub Actions conseguiu avaliar os scripts Kotlin depois da migração para `compilerOptions`, mas falhou ao resolver `com.flyfishxu:kadb-mdns:2.1.5` durante `:app:compileDebugAidl`. O módulo opcional mDNS não acompanhou a publicação 2.1.5 do cliente principal Kadb. A busca atual do catálogo Maven identifica os módulos mDNS com publicação mais recente em 05/09/2026, enquanto o core `kadb` 2.1.5 foi publicado em 16/10/2026.

## Correção

- Mantido `com.flyfishxu:kadb:2.1.5`, cujo artefato está publicado no Maven Central.
- Fixado `com.flyfishxu:kadb-mdns:2.1.4`, última versão publicada identificada para o módulo opcional mDNS.
- Mantidos `mavenCentral()` e as APIs `KadbMdnsAndroid`, `MdnsConfig`, `MdnsEndpoint` e `MdnsServiceType`.
- Adicionado `tools/dependency-tests/check-kadb-coordinates.sh` ao GitHub Actions para detectar regressão da coordenada.
- Atualizados versão e nome do artefato CI para `0.14.48`, `64` e `keymapper-0.14.48-debug`.

## Limite da validação

O teste local confirma apenas a coerência da coordenada e a configuração do repositório. A resolução do artefato e a compilação real precisam ser confirmadas no GitHub Actions, com acesso ao Maven Central.
