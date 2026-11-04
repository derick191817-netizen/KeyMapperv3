# KeyMapper 0.14.65 — Android HUD Mapper

`versionName=0.14.65` · `versionCode=81`

## Alterações desta revisão

- Controle de contatos touchscreen com ownership por origem: entrada física, Combos diferentes, Fire ligado à mira e toque transitório podem compartilhar um contato sem soltar uns aos outros.
- Fire ligado à mira não libera indevidamente uma mira que continua pressionada pela tecla física ou por outro Fire.
- Fire acionado pela mira agora seleciona apenas controles `Kind.FIRE`; não dispara Swipe, Macro ou controles comuns por engano.
- A seleção de câmera mantém a última mira ativa e restaura a anterior quando a mais recente é liberada.
- `profiles: null` e qualquer tipo não-objeto inválido geram backup antes da sanitização; campo ausente segue compatível com perfis antigos.
- Testes e contratos de CI adicionados para propriedade de contatos e recuperação de perfis.

## Estado de validação

Os testes host C++/JNI, EVDEV, políticas de arquitetura, contratos de dependência, toolchain, políticas de sessão Shizuku, HUD e overlays são executados separadamente. Os testes puros Kotlin desta revisão podem ser compilados em harness local, mas o Gradle Wrapper não conseguiu baixar Gradle 8.8 neste ambiente por falha DNS em `services.gradle.org`. Assim, `:app:lintDebug`, `:app:testDebugUnitTest`, `:app:assembleDebug` e o teste físico Android 13/One UI/DeX permanecem pendentes. Este pacote contém código-fonte, não APK.

O repositório remoto consultado permanece na versão 0.14.55. A integração de escrita no GitHub recusou operações de branch com HTTP 403; esta entrega é local e não foi publicada no remoto.


## Correção 0.14.65

A recuperação agora cria backup também quando `buttons` ou `games` aparecem como JSON `null` ou com tipo inválido. Campos ausentes continuam compatíveis com configurações antigas. Ver `AUDITORIA-KEYMAPPER-V0.14.65.md`.
