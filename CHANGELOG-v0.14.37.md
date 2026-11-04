# Changelog — KeyMapper 0.14.37

**versionCode:** 53  
**Base:** KeyMapper 0.14.36 — roteamento uinput/geometry com fallback InputManager.

## Correção do estado físico multidevice na captura evdev

- O caminho nativo evdev encaminhava `EV_KEY` diretamente ao `MappingEngine`, sem agregar o estado por `deviceId`. Com dois mouses/teclados, um UP de uma origem podia soltar prematuramente uma tecla/botão ainda mantido por outra origem.
- A instância `PhysicalInputState` agora também é usada pelo caminho evdev, separada da instância pertencente ao `CaptureView`/MotionEvent. Teclas Android mapeadas e botões de mouse são encaminhados somente nas transições globais solto→pressionado e pressionado→solto.
- O método `updateButton(deviceId, bit, pressed)` aceita somente máscaras de um bit dentro do conjunto conhecido; repetições e bits inválidos são ignorados.

## Desconexão e perda de sincronização seletivas

- `SYN_DROPPED` e remoção do descritor limpam apenas o estado físico do `deviceId` afetado.
- O serviço emite UP somente para teclas/botões que deixaram de ser mantidos por todos os dispositivos. O uso anterior de `MappingEngine.releaseAll()` nesse caminho foi removido, pois interrompia entradas de periféricos ainda conectados.
- Continua descartando movimento/scroll pendentes da origem removida por `onInputDeviceRemoved(deviceId)`.
- Pausa, edição, perda de foco/captura, troca de display, falha do Shizuku, falha de setup e destruição do serviço limpam o estado nativo antes da liberação global do motor. A transição publicada do leitor privilegiado de ativo para inativo também funciona como reset de contingência se o Binder/worker encerrar sem entregar a remoção de cada dispositivo.

## Testes adicionados

- Dois mouses compartilhando o mesmo botão primário.
- Dois teclados compartilhando a mesma tecla.
- Desconexão de uma origem sem liberar o estado ainda mantido pela outra.
- Desconexão da última origem, com snapshot correto de releases.
- Rejeição de botão zero, máscara de múltiplos bits e bits desconhecidos.

## Build e validação

- `versionName`: `0.14.37`; `versionCode`: `53`.
- Artefato GitHub Actions: `keymapper-0.14.37-debug`.
- O workflow mantém Lint → testes unitários → `assembleDebug` → upload.
- Os 14 métodos de `PhysicalInputStateTest.kt` foram executados em harness isolado com `kotlinc` e uma shim mínima da API JUnit: todos passaram, incluindo o teste aleatório de 10.000 transições.
- Um segundo harness executou cinco cenários explícitos de lifecycle/aggregation, incluindo aliases de códigos Linux: `PASS: 5 lifecycle/aggregation scenarios`.
- O comando `./gradlew :app:testDebugUnitTest` foi tentado, mas o wrapper não conseguiu baixar Gradle 8.8 por `UnknownHostException: services.gradle.org`. Portanto, nenhum build Android/ APK é declarado nesta sessão.

## Limites

- Ainda é necessário executar os testes JUnit completos via GitHub Actions, compilar APK e realizar testes de stress com dois mouses/teclados reais, desconexão durante tecla pressionada, pause/resume e troca de display no Note 20 Ultra.
