# Auditoria técnica — KeyMapper 0.14.45

## Conclusão executiva

A verificação encontra um projeto fonte consistente nos pontos verificados, com cobertura unitária isolada e testes host do C++ aprovados. **Não há comprovação de funcionamento completo no Android nesta sessão**, pois o Gradle Wrapper não conseguiu baixar Gradle 8.8 e o Android SDK não está instalado/configurado no ambiente. Não foi produzido APK nem realizado teste físico no Note 20 Ultra/DeX.

A correção concreta desta versão é de diagnóstico e contrato de carregamento do backend nativo. Não houve alteração nos motores de movimento ou nos modos de recentralização.

## Defeitos encontrados e corrigidos

### 1. Nome inconsistente da biblioteca JNI

- CMake produzia `keymapper_evdev` a partir de `evdev_capture.cpp` e `uinput_touch.cpp`, ou seja, `libkeymapper_evdev.so`.
- O serviço Shizuku carregava `keymapper_evdev`.
- A sondagem local procurava o nome legado `libkeymapper_input.so`.
- Consequência: o painel de diagnóstico podia dizer que o módulo não estava incluído mesmo quando o APK continha a biblioteca correta.

Correção: `NativeBackendLibrary` agora centraliza `LOAD_NAME` e `FILE_NAME`. O serviço e a sondagem usam essa definição; o script `tools/native-tests/check-library-contract.sh` verifica a correspondência com o target CMake e os arquivos fonte que são linkados.

### 2. Estado desconhecido apresentado como falha confirmada

A sondagem local não consulta o estado do worker no processo Shizuku. Antes, passava `backendInitialized=false` e `endToEndVerified=false`, o que classificava a captura como `MODULE_PRESENT_NOT_INITIALIZED`. Isso não era uma conclusão suportada pela sondagem.

Correção: `BackendCapabilityPolicy` aceita `Boolean?`. Quando a inicialização não foi consultada, informa `MODULE_PRESENT_NOT_PROBED`. `MODULE_PRESENT_NOT_INITIALIZED` fica reservado para falha realmente observada; `VERIFIED_ON_DEVICE` continua exigindo comprovação explícita end-to-end.

### 3. Identificação da saída virtual

`evdev_capture.cpp` e `uinput_touch.cpp` são compilados na mesma biblioteca compartilhada. A sondagem agora identifica essa relação, mas mantém inicialização e validação end-to-end como não consultadas. Ela não abre `/dev/input`, não executa ioctl, não cria uinput nem tenta `EVIOCGRAB`.

## Arquivos alterados

- `app/src/main/java/com/example/keymapper/injection/NativeBackendLibrary.kt` (novo; centraliza nome de carregamento e arquivo)
- `app/src/main/java/com/example/keymapper/injection/KeyMapperShellService.kt`
- `app/src/main/java/com/example/keymapper/diagnostics/InputBackendProbe.kt`
- `app/src/main/java/com/example/keymapper/diagnostics/BackendCapabilityPolicy.kt`
- `app/src/main/cpp/CMakeLists.txt` (comentário do contrato)
- `app/src/test/java/com/example/keymapper/injection/NativeBackendLibraryContractTest.kt` (novo)
- `app/src/test/java/com/example/keymapper/diagnostics/BackendCapabilityPolicyTest.kt`
- `tools/native-tests/check-library-contract.sh` (novo)
- `.github/workflows/build.yml`
- `app/build.gradle.kts`
- `README.md` e documentos desta versão

## Validações executadas

- 242 métodos de teste unitário selecionados e executados por harness isolado Kotlin/JVM: passaram.
- Testes host da recuperação de lotes JNI/C++: passaram.
- Verificação sintática host dos arquivos `evdev_capture.cpp` e `uinput_touch.cpp`: passou.
- Verificação do contrato CMake ↔ JNI ↔ diagnóstico: passou.
- Workflow YAML parseado localmente e artefato alinhado à versão.
- ZIP validado com `unzip -t` e SHA-256.

O harness Kotlin usa stubs temporários para APIs Android, Shizuku e JUnit. Isso não equivale a `:app:testDebugUnitTest` do Gradle nem à compilação do projeto Android completo.

## Build e validação física

Tentativa Gradle: `./gradlew --no-daemon :app:lintDebug :app:testDebugUnitTest :app:assembleDebug --stacktrace --console=plain`.

Resultado: bloqueio antes da configuração/compilação por `UnknownHostException: services.gradle.org` ao baixar o Gradle 8.8. `ANDROID_HOME` e `ANDROID_SDK_ROOT` não estão configuradas. Não houve Lint Android, compilação Kotlin/Android via Gradle, linkagem NDK/Android, APK ou execução remota do GitHub Actions nesta sessão.

Não foram executados testes físicos em Android 13, Samsung One UI, Galaxy Note 20 Ultra ou DeX. O diagnóstico precisa ser conferido no aparelho para validar o caminho real de `nativeLibraryDir`, inicialização Shizuku, permissões SELinux, evdev/uinput e consumo do evento no destino.

## Estado final

- Fonte e configuração verificadas nos contratos alterados: **aprovadas**.
- Testes isolados e testes host: **aprovados**.
- Build Android / APK: **não verificado**.
- Funcionamento end-to-end no telefone: **não verificado**.

Não classificar a aplicação como totalmente funcional até executar build Gradle e testes reais no dispositivo.
