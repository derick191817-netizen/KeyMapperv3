# Matriz de requisitos — revisão 0.14.14

| Requisito | Estado | Evidência / limite |
|---|---|---|
| Captura Pointer Capture | Parcial implementado | Depende do foco da janela solicitante. |
| Entrada relativa por `MotionEvent` genérico | Implementado no parser | Aceita apenas `AXIS_RELATIVE_X/Y`; precisa de validação em cada firmware. |
| Estado de fallback sem Pointer Capture | Implementado | `RELATIVE_FALLBACK_ACTIVE` distinto de captura confirmada. |
| Timeout de captura | Melhorado | Não gera falso `ERROR` após amostra fallback recente. |
| Inventário Android de dispositivos | Implementado | `InputDeviceManager` / `InputManager.InputDeviceListener`. |
| Multi-dispositivo físico | Parcial | Estado por `deviceId`; falta ensaio com múltiplos periféricos reais. |
| Fila pré-alocada | Implementado/testado | 149.930 assertions e 50.000 eventos em smoke. |
| Diagnóstico de fila/injeção | Implementado | Métricas locais; retorno do injetor não prova consumo pelo destino. |
| Shizuku/InputManager | Parcial | Chamada disponível, mas entrega a outra janela não validada no firmware. |
| Captura global evdev | Não implementada | Sem leitor nativo próprio. |
| `EVIOCGRAB` | Não implementado | Sem teste/acesso a nó físico. |
| Saída virtual `/dev/uinput` | Não implementada | Sem writer nativo. |
| Processo auxiliar de entrada | Não implementado | Não foi copiado nem criado a partir dos pacotes de referência. |
| Engenharia reversa Panda | Parcial estática | APK idêntico à amostra anterior; Manifest/DEX/ELF/scripts analisados; sem runtime. |
| Build Android / APK | Não validado | Sem Gradle/Android SDK configurado no ambiente. |
| Teste Samsung DeX / jogo | Pendente | Nenhum teste físico nesta revisão. |

**Resultado:** 0.14.14 corrige a classificação de uma rota de movimento relativo que já era aceita pelo parser, mas não era representada corretamente na máquina de estados. Não elimina a dependência de foco de Pointer Capture e não conclui V23.
