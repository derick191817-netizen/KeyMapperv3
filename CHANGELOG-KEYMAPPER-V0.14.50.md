# KeyMapper 0.14.50

## Correção da dependência de descoberta mDNS Android

- Mantido `com.flyfishxu:kadb:2.1.5` para o cliente ADB principal.
- Substituído `com.flyfishxu:kadb-mdns:2.1.3` pelo artefato Android explícito `com.flyfishxu:kadb-mdns-android:2.1.3`.
- A coordenada Android é publicada no Maven Central como AAR; a variante contém a API `KadbMdnsAndroid` utilizada pelo diálogo ADB sem fio.
- Conferida a API-fonte da tag 2.1.3 no repositório oficial: `KadbMdnsAndroid`, `MdnsConfig`, `MdnsEndpoint` e `MdnsServiceType` permanecem no pacote `com.flyfishxu.kadb.mdns`.
- O teste CI verifica a coordenada Android explícita, os imports, o repositório Maven Central e a consistência de versão/artefato.
- Mantida a migração `compilerOptions` / JVM 17 introduzida na 0.14.47.

## Versões

- `versionName`: `0.14.50`
- `versionCode`: `66`
- Artefato CI: `keymapper-0.14.50-debug`

## Limites de validação

- Teste de dependência por inspeção estática: aprovado.
- Testes nativos host, sintaxe C++ e contrato de biblioteca: aprovados.
- A resolução real pelo Gradle, Android Lint, testes unitários Gradle e `assembleDebug` ainda dependem de execução no GitHub Actions com acesso ao Maven Central.
- Nenhum APK foi declarado como gerado nesta execução.
