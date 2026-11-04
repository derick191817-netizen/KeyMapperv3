# KeyMapper 0.14.44 — Radius e Auto Release por controle Sight/Aim

## Comportamentos inspirados nos mapeadores de referência

- `aimBoundaryRadiusPct` adiciona um limite circular configurável a cada controle Aim. `0%` mantém a recentralização retangular anterior; valores de `1%` a `45%` definem raio relativo ao menor lado da área do jogo.
- `LookBoundaryPolicy.resolveRadiusPx()` limita o raio à distância disponível entre a origem e as quatro bordas de toque seguras. Geometria inválida ou folga subpixel desativa o raio e mantém o fallback anterior.
- `LookReanchorPlanner.planRadial()` calcula a saída da circunferência por interseção de segmento e círculo em `Double`, cria pontos de recentralização e preserva o vetor excedente, inclusive em diagonais e batches limitados.
- O raio e a origem efetiva são congelados em `MappingEngine` no início do gesto. Alterações na posição/raio feitas enquanto há contato só valem no próximo gesto, evitando saltos ou rejeição de deltas por mudança de centro.
- `aimAutoReleaseMs` adiciona liberação por inatividade independente por controle Aim: `-1` herda `AppSettings.lookIdleMs`, `0` mantém o contato e valores positivos liberam após o tempo configurado.
- A interface esclarece os modos aplicáveis: Radius funciona nos modos de reancoragem AUTO/Nonstop; OFF/Boundary mantém a borda limitada e INFINITE usa o caminho relativo nativo.

## Referências e decisões

- Panda Mouse Pro: histórico público da versão 13 menciona Continuous Camera Movement para Sight e preservação de movimento subpixel; a 14.2 registra melhoria de comportamento junto à borda. A referência inspira o objetivo e a precisão, não fornece código-fonte reutilizável.
- K2er: a documentação de Aiming Mode descreve Boundary, Radius, Re-center Mode, Re-center Delay, Release Delay e Auto Release. A versão atual adapta Radius e Auto Release por controle, mantendo as estratégias AUTO/Nonstop já existentes.
- GG Mouse Pro 3: o changelog 3.1.6 menciona “Freefire prevent view freeze option” e correções relacionadas a reset de dedo de visão/joystick. O APK fornecido é ofuscado e a implementação não foi determinada; nenhuma equivalência interna é alegada.
- SS KeyMouse Pro: inspeção estática do pacote fornecido mostrou API JNI para captura/grab de mouse e API separada para saída touchscreen virtual. O KeyMapper já possui suas próprias rotas `evdev_capture.cpp` e `uinput_touch.cpp`; foram preservadas, sem copiar bibliotecas ou código fechado.

## Compatibilidade

- Campos novos são opcionais no JSON e têm defaults compatíveis com perfis antigos: raio `0f` e Auto Release `-1`.
- `-1` é o único valor sentinela para herdar o global. Outros valores negativos são normalizados para `0`.
- O limite circular se aplica apenas a `AUTO` e `NONSTOP`. `OFF` é propositalmente limitado; `INFINITE` não usa toque recentralizado.

## Versão

- `versionName`: `0.14.44`
- `versionCode`: `60`
- Artefato CI: `keymapper-0.14.44-debug`
