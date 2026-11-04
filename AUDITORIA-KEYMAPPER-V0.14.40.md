# Auditoria técnica — KeyMapper 0.14.40

**Data:** 2026-11-04  
**Base:** KeyMapper 0.14.39 / versionCode 55  
**Entrega:** KeyMapper 0.14.40 / versionCode 56  
**Foco desta iteração:** sincronização dos vetores relativos no backend evdev e consistência de sensibilidade entre backends.

## 1. Problema identificado

A captura MotionEvent entregava normalmente X/Y como uma única amostra de movimento. O backend evdev nativo, porém, encaminhava cada `EV_REL/REL_X` e `EV_REL/REL_Y` em chamadas separadas a `MappingEngine.onDelta()`.

`MappingEngine` aplica `MouseSampleClock.timeScale()` a cada chamada. Portanto, dois eixos que pertenciam ao mesmo relatório físico podiam receber escalas temporais diferentes, dependendo da ordem, do espaçamento de timestamps e da resolução reportada pelo driver. Isso poderia alterar a relação X/Y sem que o usuário mudasse a calibração.

## 2. Correção implementada

### Protocolo JNI

`evdev_capture.cpp` agora inclui um registro `EV_SYN/SYN_REPORT` para cada relatório de dispositivo mouse. O registro usa o mesmo formato de seis campos já existente; o formato JNI não foi expandido.

### Agregação por dispositivo

`NativeMouseReportAggregator` acumula `REL_X` e `REL_Y` por `deviceId` e publica uma amostra conjunta ao receber `SYN_REPORT`. O estado persiste se o relatório for dividido entre lotes JNI diferentes.

A acumulação também é fechada antes de eventos `EV_KEY` e scroll do mesmo dispositivo, para manter a ordem de transições de controle. Nesses casos, usa-se o timestamp do último eixo acumulado. Para um relatório fechado normalmente, usa-se o timestamp de `SYN_REPORT`.

### Lifecycle e integridade

- `SYN_DROPPED`: descarta o relatório parcial daquele dispositivo antes da limpeza do estado físico.
- Remoção de dispositivo: elimina somente o estado acumulado do dispositivo removido.
- Reset global: limpa todos os relatórios parciais para que nenhum movimento anterior reapareça após a retomada.
- Deltas por eixo são acumulados em `Double` e limitados a 100.000 unidades por relatório, alinhados ao limite de integridade da camada de entrada.
- Atualização do retículo nativo usa os mesmos vetores X/Y agregados encaminhados ao `MappingEngine`.

## 3. Arquivos alterados

- `app/src/main/cpp/evdev_capture.cpp`
- `app/src/main/java/com/example/keymapper/input/LinuxEvdevEvent.kt`
- `app/src/main/java/com/example/keymapper/input/NativeMouseReportAggregator.kt` (novo)
- `app/src/main/java/com/example/keymapper/service/MappingService.kt`
- `app/src/test/java/com/example/keymapper/input/NativeMouseReportAggregatorTest.kt` (novo)
- `app/build.gradle.kts`
- `.github/workflows/build.yml`
- `README.md`
- `CHANGELOG-KEYMAPPER-V0.14.40.md`
- `AUDITORIA-KEYMAPPER-V0.14.40.md`
- `TEST-RESULTS-KEYMAPPER-V0.14.40.txt`

## 4. Testes e validação

- 56 testes Kotlin/JVM isolados passaram: 48 anteriores + 8 novos testes para o agregador.
- Cenários novos: vetor X/Y conjunto, soma de múltiplos eventos de mesmo eixo, relatório repartido entre lotes JNI, isolamento por periférico, flush antes de evento de controle, perda de sincronização, reset global e saturação numérica.
- O arquivo nativo `evdev_capture.cpp` passou por `g++ -std=c++17 -fsyntax-only`, usando headers JNI do JDK 21 e headers Linux do host.
- Versão: `0.14.40`, código 56.
- Artefato GitHub Actions: `keymapper-0.14.40-debug`.

O harness Kotlin usa `kotlinc` JVM e stubs locais de JUnit. Ele testa as classes puras selecionadas, mas não representa execução de `:app:testDebugUnitTest`.

## 5. Configuração de build revisada

- Android Gradle Plugin: 8.5.2.
- Gradle Wrapper: 8.8.
- Kotlin / Compose plugin: 2.4.21.
- JDK configurado no workflow: 17.
- `compileSdk`: 34.
- `targetSdk`: 34.
- `minSdk`: 26.
- NDK: 26.3.11579264.
- CMake: 3.22.1.

A tabela oficial do Kotlin lista Kotlin Gradle Plugin 2.4.20–2.4.21 como compatível com Gradle 7.6.3–9.7.0 e AGP 8.5.2–9.3.1. O par configurado Kotlin 2.4.21 / AGP 8.5.2 / Gradle 8.8 está dentro dessas faixas. AGP 8.x requer JDK 17. Referências: documentação oficial Kotlin de configuração Gradle e documentação Android de JDK para builds.

## 6. O que ainda não foi validado

- Gradle `:app:lintDebug`, `:app:testDebugUnitTest` e `:app:assembleDebug`.
- Compilação com NDK/CMake Android (o teste nativo realizado foi somente sintático no host).
- Execução GitHub Actions remota.
- APK instalável.
- Compatibilidade real com Shizuku e permissões da ROM.
- Funcionamento no Samsung Note 20 Ultra, Android 13, One UI e Samsung DeX.
- Consumo de eventos relativos e touchscreen pelos jogos de destino.
- Latência ponta a ponta e comportamento de Nonstop em dispositivos/jogos.

## 7. Conclusão

A versão 0.14.40 corrige uma inconsistência real no caminho evdev: os eixos X/Y agora são normalizados como um vetor por relatório, em vez de duas amostras independentes. Isso torna o caminho nativo mais coerente com o caminho MotionEvent e reduz assimetrias de sensibilidade causadas pelo relógio de amostras.

A mudança possui testes puros aprovados e verificação sintática do C++, mas ainda não está homologada em build Android nem em dispositivo físico. O Continuous Camera Movement permanece sujeito à validação do backend de saída e da interpretação do destino.
