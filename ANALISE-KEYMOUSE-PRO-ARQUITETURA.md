# Análise técnica — KeyMouse Pro como referência principal

**Amostra:** `com.sousasantoslogic.sspro_1.0.76.xapk`  
**APK base analisado:** `com.sousasantoslogic.sspro.apk`  
**Split nativo:** `config.arm64_v8a.apk`  
**Biblioteca:** `lib/arm64-v8a/libsspro_input.so`  
**Método:** inspeção estática de manifesto, DEX, ELF, símbolos dinâmicos, imports e strings. Nenhum app foi executado.

## 1. Evidências observadas no KeyMouse Pro

### Captura de entrada

A biblioteca exporta funções JNI com estes nomes:

- `nativeStart`
- `nativeStop`
- `nativeSetGrabMouse`
- `nativeReleaseGrab`
- `nativeRefreshDevices`
- `nativeCensus`
- `nativeRecoverStuckGrab`
- `nativeIsRunning`

Imports e strings encontrados incluem `eventfd`, `epoll_create1`, `epoll_ctl`, `epoll_wait`, `inotify`, `pthread`, `ioctl`, `/dev/input` e `/sys/class/input`. Isso é consistente com um serviço nativo persistente que monitora vários descritores, recebe sinais de encerramento/alteração e acompanha hot-plug de periféricos.

A biblioteca contém mensagens de diagnóstico para `EBUSY`, falha de grab, descritor morto, reautenticação, mouse sem movimento e recuperação. As strings de heartbeat mencionam contagem de dispositivos, capturas concedidas/falhas, leituras, teclas, movimentos, travamento e recuperação. Essas são evidências de capacidades previstas no binário, não prova de que funcionaram numa sessão real.

### Saída de toque

A biblioteca também exporta uma interface JNI separada:

- `nativeTouchOpen`
- `nativeTouchClose`
- `nativeTouchIsOpen`
- `nativeTouchDown`
- `nativeTouchMove`
- `nativeTouchUp`

Strings encontradas incluem `/dev/uinput`, `uinput open %dx%d slots=%d` e `sspro_virt_touch`. Em conjunto, esses símbolos e strings indicam um segundo subsistema: criação/controle de um dispositivo virtual de toque, separado da captura de mouse/teclado.

### Conclusão sobre o modelo do KeyMouse Pro

O desenho observado separa claramente:

1. descoberta e captura dos periféricos físicos;
2. transporte contínuo de eventos para o processo do app;
3. criação e controle de uma interface touchscreen virtual;
4. estado/recuperação do serviço e dos descritores.

É este desenho que deve orientar o KeyMapper. Não há evidência suficiente na análise estática para afirmar quais permissões ou modo de ativação foram usados em cada execução, nem que o uinput esteja disponível em todo firmware.

## 2. Comparação com a árvore atual do KeyMapper

A árvore de trabalho `0.14.31` já contém uma primeira implementação de captura nativa:

- `app/src/main/cpp/evdev_capture.cpp`: enumera `/dev/input/event*`, classifica mouse/teclado, tenta `EVIOCGRAB`, lê eventos e os agrupa;
- `KeyMapperShellService`: executa no UserService Shizuku e carrega `libkeymapper_evdev.so`;
- `ShizukuInputCaptureBridge`: mantém o vínculo AIDL e transporta lotes de eventos;
- `MappingService`: encaminha `REL_X`, `REL_Y`, botões, teclas e scroll para o motor;
- `TouchInjector`: atualmente injeta `MotionEvent` por `InputManager` via Shizuku.

Diferenças concretas em relação à arquitetura observada no KeyMouse Pro:

| Área | KeyMouse Pro (evidência estática) | KeyMapper 0.14.31 (código-fonte) |
|---|---|---|
| Descoberta/captura | `/dev/input`, epoll, eventfd, inotify | `/dev/input`, `poll`, pipe de wake-up |
| Hot-plug | Import/string de inotify e refresh nativo | Nova varredura periódica de 1 segundo |
| Recuperação | `nativeRecoverStuckGrab`, refresh/census e heartbeat | Nova tentativa periódica de grab e estado AIDL; sem telemetria equivalente completa |
| Captura exclusiva | Funções explícitas set/release grab | `EVIOCGRAB` no backend C++ |
| Saída de toque | Funções `nativeTouch*`, `/dev/uinput`, `sspro_virt_touch` | `MotionEvent` via `InputManager`/Shizuku |
| Telemetria | Strings de heartbeat, falhas e recuperação | Estado básico com contagem de dispositivos e motivo |

**Veredito:** a árvore atual do KeyMapper já não depende exclusivamente de Pointer Capture quando a captura evdev está ativa, mas ainda não reproduz a separação de entrada/saída observada no KeyMouse Pro. A principal lacuna restante é a saída touchscreen nativa por uinput, além da robustez do loop de captura.

## 3. Plano de adaptação recomendado

### Etapa A — aproximar a captura do KeyMouse Pro

- substituir `poll` + pipe por `epoll` + `eventfd`;
- observar `/dev/input` com `inotify`, mantendo uma varredura periódica de segurança;
- manter um registro explícito por descritor: dispositivo, classe, estado de grab, último erro e última atividade;
- recuperar descritores desconectados e tentar reabrir periféricos após hot-plug;
- implementar heartbeat com contadores reais: eventos recebidos, eventos encaminhados, falhas de grab, descritores removidos e tempo desde último evento;
- ao perder sincronização (`SYN_DROPPED`), liberar estados e ressincronizar antes de aceitar novos eventos;
- garantir `EVIOCGRAB=0`, fechamento de fd e liberação de estados em stop, erro, morte do Binder e encerramento do processo.

### Etapa B — implementar uma saída virtual de toque independente

Criar um módulo JNI próprio para testar e, quando permitido, criar um dispositivo touchscreen via `/dev/uinput`:

- sondar `open` + `UI_GET_VERSION` antes de habilitar o backend;
- configurar capacidades touchscreen e multitouch corretamente;
- implementar ciclo DOWN/MOVE/UP, tracking IDs, slots, `SYN_REPORT` e fechamento determinístico;
- não ativar automaticamente apenas porque `/dev/uinput` existe: a criação real do dispositivo precisa ser confirmada;
- manter `InputManager` como fallback quando uinput não estiver autorizado ou não puder ser associado com segurança ao display escolhido;
- nunca direcionar entrada para display incorreto: a rota uinput precisa provar qual display recebe os toques, especialmente em Samsung DeX/monitor externo.

### Etapa C — validação funcional ponta a ponta

A sequência mínima de testes é:

1. confirmar que o serviço nativo inicia e recebe periféricos físicos;
2. verificar que um clique físico gera exatamente um DOWN e um UP;
3. verificar que movimentos chegam na ordem e sem duplicação;
4. verificar teclado, repetição, modificadores e liberação ao desconectar;
5. verificar troca entre captura nativa e fallback sem duplicar eventos nem deixar teclas pressionadas;
6. verificar injeção por InputManager e por uinput separadamente;
7. validar display principal, Samsung DeX e monitor externo;
8. executar testes prolongados de hot-plug, pausa, retomada e encerramento do serviço.

## 4. Limites e direitos

`libsspro_input.so` pertence ao KeyMouse Pro e é uma biblioteca fechada na amostra fornecida. O KeyMapper não deve incorporar o binário nem copiar código proprietário. É possível reproduzir o desenho técnico e a interface comportamental com implementação própria, respeitando as licenças e mantendo logs suficientes para provar a operação real.

Afinidade de CPU e outras otimizações de baixo nível não devem ser copiadas sem medição no Note 20 Ultra; podem piorar consumo, aquecimento e escalonamento.

## 5. Verificações feitas nesta análise

- confirmação do split ARM64 do XAPK e localização de `libsspro_input.so`;
- leitura de símbolos JNI exportados e imports ELF;
- extração de strings relevantes de captura, recuperação e uinput;
- inspeção da árvore de código do KeyMapper 0.14.31 para localizar captura, transporte e injeção;
- compilação sintática host-only de `evdev_capture.cpp` com Clang C++17 e headers JNI do JDK: passou.

A verificação host-only não equivale a build NDK/Android. Não houve APK gerado, instalação no Note 20 Ultra ou teste real dos eventos no jogo/aplicativo de destino.
