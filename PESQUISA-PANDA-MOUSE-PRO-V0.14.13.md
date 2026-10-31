# Engenharia reversa estática — Panda Mouse Pro (amostra reenviada em 31/10/2026)

## 1. Identidade da amostra

O arquivo `Panda Mouse Pro(2).zip` contém apenas `Panda Mouse Pro.apk`. O APK foi comparado com a amostra Panda extraída anteriormente e os dois hashes SHA-256 são iguais:

`f09f329a3661c8ec0d9d4845b5ac7096e59ada1527dd2e59a70ab43ea27910fb`

Conclusão: é o mesmo binário, não uma atualização do Panda Mouse Pro. Metadados anteriormente extraídos indicam package `com.panda.mouse`, minSdk 23 e targetSdk 36.

## 2. Conteúdo de interesse

| Asset | Formato / arquitetura | Tamanho |
|---|---|---:|
| `com.panda.mouseinject.dex` | DEX auxiliar | 477.044 bytes |
| `com.panda.mouselibinject.so` | ELF32, ARM | 40.892 bytes |
| `x64com.panda.mouselibinject.so` | ELF64, AArch64 | 61.544 bytes |
| `x86_64com.panda.mouselibinject.so` | ELF64, x86-64 | 60.728 bytes |

As bibliotecas ficam em `assets/`, não nos diretórios de ABI convencionais `lib/<abi>/`. As três variantes exportam `JNI_OnLoad`.

## 3. Evidências no módulo nativo

Imports observados incluem `ioctl`, `poll`, `read`, `write`, `inotify_init`, `inotify_add_watch`, `opendir`, `readdir`, `popen` e `pclose`. Strings incluem `/dev/input`, `ABS_*`, `REL_*`, `BTN_*` e mensagens/opções de enumeração, informação e contagem de eventos.

Interpretação restrita: o módulo foi preparado para interagir com inventário e fluxos de eventos de entrada em nível nativo. Os imports e strings não comprovam que um nó foi aberto, que `ioctl` teve sucesso, que um dispositivo foi capturado, nem que exista um `EVIOCGRAB` funcional em todo firmware.

## 4. Evidências DEX e serviços

O DEX principal e `com.panda.mouseinject.dex` contêm referências a:

- `GetEventService`
- `InjectService`
- `NativeDeviceInfo`
- `CursorInputMapper`
- `TouchInputMapper`
- `KeyboardInputMapper`
- `JoystickInputMapper`

Também aparece o fallback textual `MODE_POINTER_RELATIVE` para `MODE_POINTER`, além de nomes como `invokeInjectInputEvent1111/2222/3333`. Esses identificadores ajudam a reconstruir a divisão de responsabilidades, mas não substituem decompilação completa e análise de fluxo.

## 5. Inicialização e processo auxiliar

Os assets `activate.sh`, `inject2.sh` e `nohup_activate.sh` indicam que o aplicativo prepara arquivos auxiliares num diretório temporário e inicia `InjectService` por `app_process`, com DEX separado para componentes de mouse, toque e gamepad. Isso sustenta a hipótese de serviços de entrada/injeção separados do processo de interface.

Os scripts também tentam alterar uma configuração global relacionada a toques não confiáveis. O KeyMapper não reproduz essa mudança, não reduz proteções globais do Android e não copia scripts, bibliotecas ou DEX do produto de referência.

## 6. O que pode ser aproveitado conceitualmente

- Separação explícita entre descoberta/leitura de dispositivos, transformação de dados e saída.
- Capacidade e estado por dispositivo, em vez de uma suposição única para todos os periféricos.
- Fallback explícito quando um modo de ponteiro não está disponível.
- Diagnóstico separado para módulo presente, inicialização, autorização, recebimento e entrega.
- Recuperação e liberação de estado quando um dispositivo ou processo auxiliar desaparece.

O KeyMapper 0.14.13 aplica a parte compatível com o backend Android já existente: `RELATIVE_ACTIVE` e `RELATIVE_FALLBACK_ACTIVE` agora são estados distintos, com contagem independente de eventos recebidos sem Pointer Capture.

## 7. Limites da análise

A inspeção foi estática (`zip`, hash, strings, cabeçalhos ELF, imports e identificadores DEX/scripts). Não houve execução, instalação, instrumentação, attach de debugger, teste de permissões nem captura de tráfego. Portanto, não é possível afirmar qual processo/UID executa cada serviço, quais permissões efetivas são obtidas em cada firmware, ou se a rota produz entrada utilizável num jogo específico.

Nenhum binário, DEX, script ou asset do Panda foi incorporado ao KeyMapper.
