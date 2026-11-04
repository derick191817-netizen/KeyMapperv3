# Auditoria técnica — KeyMapper 0.14.64

## Escopo

Revisão de continuidade sobre a árvore local 0.14.63, com foco no caminho de estado dos contatos touchscreen, interações simultâneas Fire/Aim/Combo e recuperação de dados de perfis. A análise do SS KeyMouse Pro foi usada como referência de comportamento público e arquitetura observada anteriormente; não houve cópia de código proprietário.

## Defeitos encontrados e correções

### 1. `ACTION_UP` prematuro em Fire/Aim

**Antes:** `press()` e `release()` usavam apenas o ID do controle. Uma entrada física, um Fire ligado à mira e uma mira que acionava Fire podiam compartilhar o mesmo contato. Quando qualquer uma soltava, chamava `release()` e emitia `ACTION_UP`, mesmo que outra origem ainda estivesse pressionando o controle.

**Correção:** adicionado `ControlPressOwnership`, com owners identificados por tipo e origem. O primeiro owner cria o contato; owners subsequentes compartilham; o `ACTION_UP` só é enviado quando o último owner sai. `TouchInjector.isReleasePending()` evita reutilizar o ID enquanto a confirmação de soltura está pendente.

### 2. Disputa entre dois Combos e uma tecla física

**Antes:** alvos de dois Combos diferentes usavam a mesma identidade de origem do controle. A soltura de um Combo podia encerrar um contato ainda mantido pelo outro.

**Correção:** cada Combo recebe `PressSourceKind.COMBO` próprio. `standardKey`, `fireKey`, `aimKey` e `linkAim` propagam a origem. Estados Toggle passaram a ser indexados por controle e origem, mantendo alternâncias independentes.

### 3. Seleção de câmera perdida ao liberar uma das fontes

**Antes:** `aimId` era uma variável singular. Se uma mira fosse ativada por tecla e também por Fire, a primeira soltura podia zerar a seleção mesmo com a outra origem ativa. Duas miras diferentes também não restauravam a anterior ao liberar a mais recente.

**Correção:** `AimActivationRegistry` mantém as origens por mira e seleciona a ativada mais recentemente. Ao remover a última origem de uma mira, a seleção volta para a mira anterior ainda ativa.

### 4. Fire disparado por ação de mira em controles incompatíveis

**Antes:** `fireWithAim` percorria qualquer controle não-Aim com a mesma `shootKey`. Isso podia pressionar um controle comum ou criar toque na posição de Macro/Swipe.

**Correção:** a opção só percorre `Kind.FIRE`, usa owner derivado da origem da mira e respeita TAP/HOLD. Múltiplas miras não liberam um Fire que ainda esteja ativo por outra origem.

### 5. Backup ignorado para `profiles: null`

**Antes:** `SettingsRepository.readCurrentOrDefault()` não considerava `JSONObject.NULL` como tipo inválido. Um salvamento posterior podia sanitizar o campo sem antes guardar o JSON original no backup de recuperação.

**Correção:** `SettingsRecoveryPolicy.shouldBackupInvalidProfiles()` trata qualquer campo presente que não seja `JSONObject` como inválido. A cópia limitada é salva em `settings_json_recovery_backup` antes da remoção. Campo ausente segue compatível com versões antigas.

## Arquivos centrais alterados

- `app/src/main/java/com/example/keymapper/domain/ControlActivationPolicy.kt` — ownership de contatos e seleção de mira.
- `app/src/main/java/com/example/keymapper/domain/SettingsRecoveryPolicy.kt` — validação pura do campo profiles.
- `app/src/main/java/com/example/keymapper/mapping/MappingEngine.kt` — aplicação de ownership no motor, Fire/Aim/Combo e Toggle.
- `app/src/main/java/com/example/keymapper/injection/TouchInjector.kt` — sinalização de ponteiro indisponível durante liberação pendente.
- `app/src/main/java/com/example/keymapper/data/SettingsRepository.kt` — backup de profiles nulo/tipo incorreto.
- `app/src/test/java/com/example/keymapper/domain/ControlActivationPolicyTest.kt` — testes de origens simultâneas.
- `app/src/test/java/com/example/keymapper/domain/SettingsRecoveryPolicyTest.kt` — testes da política de recuperação.
- `tools/architecture-tests/check-control-activation-ownership.sh` — contrato de integração do motor.
- `tools/architecture-tests/check-settings-profile-recovery.sh` — contrato de recuperação.
- `.github/workflows/build.yml` e verificadores de versão — CI atualizado para 0.14.64.

## Referência SS KeyMouse Pro

A documentação pública do SS KeyMouse Pro descreve mapeamento de teclas e botões do mouse para toques, analógico WASD, visão FPS e HUD flutuante com opacidade ajustável. O guia público de Macro/Script descreve etapas tap, wait, swipe, hold, mouseUp, cursorMove e loops; o guia de ativação lista Shizuku e permissão de sobreposição como requisitos. O KeyMapper usa esses comportamentos como referência de produto, mas mantém implementação própria.

Comparação funcional desta revisão: `docs/comparison/KEYMOUSE-PRO-FUNCTIONAL-COMPARISON-0.14.64.md`.

## Validação realizada nesta sessão

Aprovados:

- `tools/native-tests/run.sh` — EVDEV batch recovery e classificação HID.
- `tools/native-tests/check-syntax.sh` — sintaxe C++17.
- `tools/native-tests/check-library-contract.sh` — contratos CMake/JNI/biblioteca.
- Verificadores Kadb 2.1.4, Jetifier e teste de seleção de dependência.
- Todos os scripts em `tools/architecture-tests/*.sh`, incluindo ownership, recuperação de perfis, Shizuku, Macro, HUD e overlay.
- `tools/build-tests/check-toolchain-compatibility.py` e testes negativos.
- Harness Kotlin local de `ControlPressOwnership`, `AimActivationRegistry` e `SettingsRecoveryPolicy`.

Não executados / pendentes:

- `:app:lintDebug`, `:app:testDebugUnitTest`, `:app:assembleDebug` via Gradle.
- Teste instrumentado e teste físico Android 13 / One UI / Samsung DeX.
- Verificação de comportamento em hardware real para todas as rotas `InputManager` / uinput.

## Bloqueio objetivo do build

O Wrapper tenta baixar `https://services.gradle.org/distributions/gradle-8.8-bin.zip` e termina com `java.net.UnknownHostException: services.gradle.org`. A falha ocorre antes de o Gradle iniciar; não é um resultado de compilação. Não há APK nesta entrega.

## Situação de publicação

O pacote foi produzido localmente. O repositório remoto ainda estava em 0.14.55 quando consultado; a integração GitHub disponível retornou HTTP 403 para criação de branch. Nenhum commit ou push remoto foi feito.
