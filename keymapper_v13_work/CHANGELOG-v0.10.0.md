# KeyMapper 0.10.0 — evolução de estabilidade

## Alterações desta revisão

- Atualizada a versão Android para `versionCode 11` / `versionName 0.10.0`.
- Acrescentados testes de regressão para cruzamento dos limites lógicos do cursor virtual sem clamp e para sequências rápidas de mudança de direção.
- Mantida a arquitetura existente de editor flutuante sobre o aplicativo-alvo, separando edição da execução.
- Mantido o processamento de deltas relativos e das amostras históricas em `CaptureView`; não foi introduzida uma simulação visual como substituto do overlay real.

## Limites conhecidos

- A janela de overlay e o editor foram implementados no código, mas a operação real sobre um jogo precisa ser validada em dispositivo Android, com permissão de sobreposição e mecanismo de injeção autorizado.
- O movimento contínuo depende de o dispositivo fornecer deltas relativos e de o alvo aceitar o mecanismo de entrada usado. Desenhar uma mira/overlay não garante, por si só, injeção em qualquer jogo.
- Esta revisão não declara build Android aprovado: o ambiente de execução não consegue baixar a distribuição Gradle 8.8 por falta de conectividade a `services.gradle.org`.
