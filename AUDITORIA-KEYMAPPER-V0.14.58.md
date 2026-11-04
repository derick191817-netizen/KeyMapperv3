# Auditoria técnica — KeyMapper 0.14.58

## Escopo

Esta entrega parte do código-fonte 0.14.57 e mantém as correções de compatibilidade Kadb/Jetifier (0.14.56) e de visibilidade/edição de controles (0.14.57). A mudança funcional nova concentra-se no ciclo de vida da captura nativa Shizuku.

## Defeito confirmado por inspeção de fluxo

`ShizukuInputCaptureBridge.start()` criava um novo `SessionEpoch` e um novo `IKeyMapperInputCallback` em toda chamada. Entretanto, quando `remote != null` e `captureStarted == true`, o fluxo não registrava esse callback novo no UserService: apenas chamava `setEnabled(initiallyEnabled)`.

O UserService continuava chamando o callback antigo, mas esse callback verificava `sessionEpoch.isCurrent(sessionToken)`. Como `start()` já havia incrementado o epoch, os eventos recebidos pelo callback antigo eram descartados. O Binder continuava conectado, então o estado poderia aparentar normalidade enquanto os eventos de input não chegavam ao motor.

Esse cenário é possível porque `start()` também é chamado por `restartFromSavedRequest()` após notificações/reconexões do Binder, além da inicialização inicial do serviço.

## Correção aplicada

Foi introduzida `SessionStartPolicy.shouldCreateFreshSession(isActive, hasInputCallback)`.

- Primeira inicialização: cria um novo epoch e callback.
- Chamada repetida com serviço ativo e callback registrado: preserva a identidade da sessão e o callback remoto.
- Estado ativo sem callback: gera um novo epoch, recria callback e limpa os flags de captura para permitir recuperação no Binder atual.
- Serviço inativo: não reutiliza callback antigo; cria uma sessão nova.

O callback mantido continua lendo os campos mutáveis `onEvents`, `onState` e `onTouchOutputState`, que são atualizados na nova chamada `start()`. Assim, atualizar os consumidores não exige trocar a identidade do callback já registrado no UserService.

## Testes adicionados

- `SessionStartPolicyTest.kt`: quatro cenários da política, incluindo prova de que o token do callback registrado continua atual em uma chamada repetida e de que uma recuperação invalida o token anterior.
- `check-session-start-idempotency.sh`: contrato de arquitetura confirma que a criação do epoch está condicionada à política e que `start()` reutiliza o callback quando a sessão já está ativa.
- Teste independente com `kotlinc` 1.9.0 executou a política e a validade do token em chamadas repetidas/recuperação: PASS.

## Validações executadas nesta sessão

PASS:

- `tools/native-tests/run.sh`
- `tools/native-tests/check-syntax.sh`
- `tools/native-tests/check-library-contract.sh`
- `tools/dependency-tests/check-kadb-coordinates.sh`
- `tools/dependency-tests/check-jetifier-ignorelist.sh`
- `tools/dependency-tests/test-kadb-selected-version.sh`
- `tools/architecture-tests/check-wireless-adb-boundary.sh`
- `tools/architecture-tests/check-shizuku-session-isolation.sh`
- `tools/architecture-tests/check-session-start-idempotency.sh`
- `tools/architecture-tests/check-hud-placement-policy.sh`
- `tools/architecture-tests/check-overlay-window-placement.sh`
- `tools/architecture-tests/check-visible-control-types.sh`
- `tools/build-tests/check-toolchain-compatibility.py`
- `tools/build-tests/test-toolchain-compatibility.py`
- Parse do YAML do workflow e presença das etapas Lint/JUnit/assembleDebug/publicação.
- Teste Kotlin isolado para `SessionStartPolicy`: PASS.

## Verificações ainda pendentes

Não foi possível executar o Gradle real neste ambiente. O Gradle Wrapper não estava em cache e o download do Gradle 8.8 foi bloqueado por falha de resolução DNS de `services.gradle.org`.

Portanto, estes resultados não foram obtidos e não devem ser considerados aprovados:

- `:app:lintDebug`
- `:app:testDebugUnitTest`
- `:app:assembleDebug`
- instalação e teste físico em Android 13 / One UI / Samsung DeX

O teste isolado com `kotlinc` valida somente a política pura; não substitui a compilação Android nem a execução do JUnit pelo Gradle.

## Estado do repositório remoto

Na inspeção feita em 4 de novembro de 2026, `main` estava em `0.14.55`, commit `ebc7c148f3a51af8b6614513aa558950580a4186`. O último workflow observado, run `41810803293`, falhou antes dos testes e da geração do APK ao transformar `com.flyfishxu:kadb-android:2.1.5` pelo Jetifier, com `Unsupported class file major version 69`.

Foi tentada a criação da branch `ai/keymapper-0.14.57-integration`; o GitHub Connector devolveu HTTP 403 `Resource not accessible by integration`. Logo, esta atualização foi produzida e testada localmente, mas ainda não foi enviada ao `main` nem a uma branch remota.

## Identificação da versão

- `versionName`: `0.14.58`
- `versionCode`: `74`
- Artefato CI planejado: `keymapper-0.14.58-debug`

Nenhum APK é incluído nesta entrega porque `assembleDebug` ainda não foi executado com sucesso.
