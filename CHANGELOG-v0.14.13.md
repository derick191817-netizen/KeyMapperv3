# KeyMapper 0.14.14

## Correções
- Separados os estados `RELATIVE_ACTIVE` (amostra relativa recebida enquanto Pointer Capture está confirmado) e `RELATIVE_FALLBACK_ACTIVE` (eixos relativos recebidos pelo caminho `onGenericMotionEvent` sem Pointer Capture).
- A máquina de estados agora aceita a recuperação para o estado fallback a partir de `GRAB_REQUESTED` e do `ERROR` causado pelo timeout de Pointer Capture.
- O timeout de Pointer Capture não gera erro falso quando houve uma amostra relativa alternativa recente. A janela de atividade recente é medida pelo relógio monotônico do Android.
- O diagnóstico contabiliza amostras relativas recebidas sem Pointer Capture e mantém o indicador de captura confirmada separado. Entrada relativa recebida não é tratada como prova de captura global.

## Engenharia reversa de referência
- Reconfirmado que o APK reenviado dentro de `Panda Mouse Pro(2).zip` tem o mesmo SHA-256 da amostra Panda já analisada; não é uma versão nova.
- Inspecionados os assets DEX/ELF e os scripts de inicialização: variantes nativas ARM32, AArch64 e x86-64; JNI_OnLoad; imports de I/O de dispositivos; serviços separados de eventos/injeção; processo auxiliar iniciado via `app_process`.
- Nenhum código, biblioteca, DEX, script ou asset do Panda foi copiado para o KeyMapper.
- A configuração global de toques não confiáveis vista nos scripts de referência não é reproduzida pelo KeyMapper.

## Validação
- 117 métodos de teste Kotlin offline.
- Smoke de fila circular: 149.930 assertions, com stream de 50.000 eventos.
- Smoke matemático do pipeline: 8 assertions.
- Smoke de diagnóstico: 15 checks, incluindo modo fallback sem Pointer Capture.

## Limitações
- Build Android/Gradle e APK não gerados nesta revisão; o ambiente continua sem Android SDK/Gradle configurado.
- Nenhum teste em aparelho físico, Samsung DeX ou jogo foi executado.
- A rota `SOURCE_MOUSE_RELATIVE`/`AXIS_RELATIVE_X/Y` sem Pointer Capture depende dos eventos que o firmware realmente encaminhar à janela; validamos a política e o código, não o comportamento em todos os dispositivos.
- Captura global evdev/`EVIOCGRAB`, processo auxiliar próprio e saída nativa `/dev/uinput` continuam não implementados.
