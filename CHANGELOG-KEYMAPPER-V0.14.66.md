# Changelog — KeyMapper 0.14.66

## Correções reproduzidas a partir do log do GitHub Actions

### 1. Toolchain Gradle
- Wrapper atualizado para Gradle 8.14.6.
- Checksum SHA-256 configurado a partir da referência oficial da distribuição Gradle.
- Verificador agora exige Gradle >= 8.14.4 e mantém o limite superior da matriz KGP 2.4.21 (9.7.0).

### 2. Dependência transitiva Kadb / SPAKE2
- JitPack adicionado em `dependencyResolutionManagement.repositories`.
- A dependência transitiva `com.github.Flyfish233:spake2-java:1.1.1` do Kadb Android 2.1.4 pode, assim, ser obtida do repositório de origem correto.
- A versão Kadb Android 2.1.4 e a família Bouncy Castle 1.86 foram preservadas.

### 3. Compilação AIDL
- `IKeyMapperInputCallback` explicitamente importada em `IKeyMapperShellService.aidl`.
- Adicionado `tools/architecture-tests/check-aidl-contract.sh`, chamado pelo workflow, para detectar a ausência do arquivo, pacote, import ou método que utiliza o callback.

### 4. Release e CI
- `versionName=0.14.66`; `versionCode=82`.
- Nome do artefato Actions atualizado para `keymapper-0.14.66-debug`.
- Testes negativos do verificador atualizados para rejeitar Gradle 8.14.3 e artefatos com versão divergente.

## Limites de validação

Os verificadores locais foram executados com sucesso. Não foi possível executar Gradle, Lint, JUnit Android ou `assembleDebug` neste ambiente porque não há Android SDK e distribuição Gradle instalados localmente. É necessário confirmar a execução real no GitHub Actions. Nenhum APK foi produzido nesta sessão.
