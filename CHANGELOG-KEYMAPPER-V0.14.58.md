# KeyMapper 0.14.58 — confiabilidade do ciclo de sessão Shizuku

- Corrigido `start()` para ser idempotente dentro da mesma sessão ativa.
- Evita incrementar `SessionEpoch` e invalidar callback já registrado no UserService em chamadas repetidas.
- Recupera estado inconsistente `active=true` sem callback, criando nova geração e liberando flags de captura para reinicialização.
- Adicionado teste unitário para primeira inicialização, repetição, callback ausente e serviço inativo com callback antigo.
- Adicionada barreira de arquitetura no GitHub Actions para impedir regressão no fluxo de criação do callback.
- Atualizados os verificadores de versão/artefato para `0.14.58` / `74`.

Mantém todas as correções 0.14.56 (Kadb Android AAR 2.1.4 e Bouncy Castle 1.86) e 0.14.57 (controles Tecla/Tiro/Mira visíveis e configuráveis, posicionamento sem colisão e persistência coerente).

**Validação local:** testes de contrato, nativos, dependências, toolchain e política Kotlin passaram. A compilação Gradle real, Lint, JUnit via Gradle, geração do APK e teste físico continuam pendentes por indisponibilidade de rede para baixar o Gradle Wrapper neste ambiente.
