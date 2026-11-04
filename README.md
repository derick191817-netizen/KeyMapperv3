# KeyMapper 0.14.55 — projeto Android

## Mudanças da versão 0.14.55

- Painel de configuração do HUD agora usa `OverlayWindowPlacementPolicy` para calcular posição e dimensões dentro dos limites do display.
- Em paisagem, o painel fica no lado oposto ao controle selecionado; em retrato, na borda oposta vertical.
- Margens levam em conta a densidade da interface e são limitadas em displays pequenos.
- O posicionamento usa gravidade física TOP|LEFT e coordenadas explícitas, sem depender da direção RTL.
- Removida uma chamada duplicada de notificação ao restaurar a captura ao fechar o painel.
- Cinco testes de política e uma barreira arquitetural adicionados ao GitHub Actions.

Versão Android: `versionName 0.14.55` / `versionCode 71`; artefato CI `keymapper-0.14.55-debug`.

**Validação:** política de posicionamento compilada/executada com Kotlin; testes nativos, C++/CMake, dependências, arquitetura, isolamento Shizuku e toolchain passaram. O Gradle Wrapper não conseguiu baixar Gradle 8.8 neste ambiente por falha DNS; portanto JUnit via Gradle, Lint, APK e teste físico ainda estão pendentes.

Documentação detalhada:
- `AUDITORIA-KEYMAPPER-V0.14.55.md`
- `CHANGELOG-KEYMAPPER-V0.14.55.md`
- `TEST-RESULTS-KEYMAPPER-V0.14.55.txt`
- `docs/architecture/OVERLAY-PANEL-PLACEMENT-0.14.55.md`
- `docs/COMPARACAO-FUNCIONAL-REFERENCIAS-0.14.55.md`

---

## Base técnica

- Android phone-first, compatibilidade planejada com Samsung DeX.
- Kotlin + Jetpack Compose para interface, regras e integração Android.
- C++17/NDK para o backend nativo existente.
- Shizuku/ADB com interfaces internas e recuperação limitada.
- GitHub Actions executa verificações host antes de Lint, testes Android e `assembleDebug`.
