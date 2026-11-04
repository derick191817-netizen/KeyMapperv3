# KeyMapper 0.14.42

## Recuperação de perda no transporte JNI/NDK

- `sendBatch()` agora confirma separadamente a alocação do array, a cópia `SetLongArrayRegion` e a execução do callback Java.
- Falhas de alocação/cópia/callback não descartam silenciosamente a sequência: os dispositivos presentes no lote perdido são registrados em `EvdevBatchRecovery`.
- O próximo lote começa com um `EV_SYN/SYN_DROPPED` por dispositivo afetado, antes de qualquer evento novo. O consumidor Kotlin limpa o estado desse periférico antes de aceitar entradas posteriores.
- A lista de recuperação agrega classes de dispositivo por ID e evita marcadores duplicados.
- Enquanto houver recuperação pendente e não houver movimento novo, o worker tenta reenviar marcadores em intervalo limitado de 20 ms, sem busy loop.
- O contador `falhas_lote_JNI` passa a aparecer no diagnóstico do backend evdev.
- Foram adicionados testes C++ host para união por dispositivo, ordenação dos marcadores, falha repetida, sucesso que limpa o estado e recuperação somente com marcadores.

## Compatibilidade

- `versionName`: `0.14.42`
- `versionCode`: `58`
- Mantidos Kotlin, Android APIs, Shizuku, uinput, perfis e os modos Sight/Aim existentes.
- Nenhuma alteração nos algoritmos de câmera, sensibilidade, HUD ou política de reancoragem nesta versão.
