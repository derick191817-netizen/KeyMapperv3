# KeyMapper 0.14.66 — Android HUD Mapper

`versionName=0.14.66` · `versionCode=82`

## Correções desta revisão

- Gradle Wrapper atualizado de 8.8 para 8.14.6, com SHA-256 oficial da distribuição.
- JitPack incluído no conjunto de repositórios de dependências para resolver a dependência transitiva `com.github.Flyfish233:spake2-java:1.1.1` exigida pelo Kadb Android 2.1.4.
- Import AIDL explícito de `IKeyMapperInputCallback` em `IKeyMapperShellService.aidl`, eliminando a resolução ambígua da interface irmã pelo compilador AIDL.
- Teste estrutural dedicado para garantir os dois contratos AIDL, o pacote e o import.
- Verificador de toolchain atualizado para exigir Gradle >= 8.14.4 e <= 9.7.0, mantendo AGP 8.5.2, Kotlin/Compose 2.4.21 e JDK 17.
- Workflow, verificador de dependências e identificador do artefato atualizados para 0.14.66 / versionCode 82.

## Estado de validação

Os verificadores host de C++/JNI, EVDEV, arquitetura, HUD, overlay, sessão Shizuku, recuperação de perfis, coordenadas Kadb, JitPack, AIDL e matriz do toolchain passaram localmente. O ambiente de edição não possui Android SDK nem Gradle instalado, e não conseguiu iniciar uma compilação Android completa; por isso, `:app:lintDebug`, `:app:testDebugUnitTest` e `:app:assembleDebug` ainda precisam ser confirmados no GitHub Actions. Este pacote contém código-fonte e patch, não APK.

A publicação automática no repositório remoto foi bloqueada pela integração GitHub com HTTP 403 ao tentar criar uma branch. As alterações desta revisão estão entregues em ZIP para aplicação na raiz do projeto.

## Histórico imediato

A versão 0.14.65 reforçou a recuperação de perfis quando `buttons` ou `games` aparecem como JSON `null` ou com tipo inválido, criando backup antes da sanitização. Essa alteração foi preservada.
