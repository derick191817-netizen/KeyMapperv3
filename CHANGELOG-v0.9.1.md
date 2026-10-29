# KeyMapper 0.9.1 — estabilidade do pipeline de entrada

## Alterações

- O cursor lógico agora usa wrap matemático por resto, incluindo movimentos finitos que cruzam o limite mais de uma vez.
- `VirtualCursorController.reset()` reinicia o estado lógico sem reposicionar o cursor físico de Android.
- `MouseInputProcessor` rejeita entradas zero/não finitas e satura amostras extremas antes que cheguem ao motor.
- O buffer de deltas em `MappingEngine` e `TouchInjector` usa soma em `Double` e saturação para evitar que a acumulação se transforme em `Infinity`.
- `SensitivityEngine` valida deltas e escalas temporais, usa cálculos de curva em `Double`, limita ganho e saída por frame e limpa o resto fracionário após uma entrada inválida.
- Adicionados testes de regressão para wrap, saturação, metadados de entrada, valores não finitos e limpeza de estado fracionário.
- Versão do aplicativo: `0.9.1` (`versionCode` 10).

## Limites conhecidos

- A entrega de `SOURCE_MOUSE_RELATIVE` depende do dispositivo, versão do Android, implementação de Shizuku e aplicativo de destino. O wrap lógico não garante por si só que todos os jogos aceitem eventos relativos.
- O ciclo completo de captura/liberação, injeção e foco precisa de teste instrumentado/dispositivo físico; os testes unitários do core não substituem esse teste.
- O build Android completo não pôde ser executado neste ambiente porque o Wrapper não conseguiu resolver `services.gradle.org` por indisponibilidade de DNS/rede.
