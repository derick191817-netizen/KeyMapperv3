# KeyMapper 0.14.55 — geometria segura do painel flutuante

## Alterações

- Extraída `OverlayWindowPlacementPolicy`, política Kotlin pura para calcular tamanho e posição do painel de configuração.
- Em paisagem, o painel fica no lado oposto ao controle selecionado; em retrato, ocupa a borda oposta vertical.
- Dimensões e coordenadas são limitadas à geometria disponível do display, com margem proporcional à densidade e limitada a 8% da menor dimensão para evitar colapsar painéis em displays pequenos.
- Entradas inválidas de densidade ou posição usam valores seguros; displays muito pequenos não produzem coordenadas negativas.
- `OverlayManager.openConfig()` usa gravidade física TOP|LEFT e posição explícita, sem depender de START/END e da direção RTL.
- Removida uma notificação duplicada de `onCaptureRequestChanged()` no fechamento do painel.
- Adicionados cinco testes para paisagem, retrato, display pequeno, entradas inválidas e display de 1×1.
- Novo contrato estático executado no GitHub Actions.

## O que não mudou

- Nenhuma alteração no motor de entrada C++/JNI/uinput, em Shizuku, no esquema de perfis ou nas associações existentes.
- Nenhuma alteração a funções especializadas de assistência de mira/disparo ou automação de combate.

## Versão

- `versionName`: `0.14.55`
- `versionCode`: `71`
- Artefato GitHub Actions: `keymapper-0.14.55-debug`

## Validação

- Política compilada/executada diretamente com `kotlinc-jvm 1.9.0`; cinco cenários aprovados.
- Testes nativos evdev/JNI, sintaxe C++17 e contrato CMake/JNI aprovados.
- Verificadores de toolchain, dependências Kadb, arquitetura ADB, sessão Shizuku e posicionamento HUD/overlay aprovados.
- Quatro testes negativos do toolchain rejeitados como esperado.
- `:app:testDebugUnitTest` foi tentado, mas o Gradle Wrapper não conseguiu resolver `services.gradle.org` (`UnknownHostException`) antes de iniciar. Lint, build completo, APK e teste físico permanecem pendentes.
