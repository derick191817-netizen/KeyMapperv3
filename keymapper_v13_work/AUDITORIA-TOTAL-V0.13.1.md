# Auditoria complementar — KeyMapper 0.13.1

## Escopo real desta passagem
Foi feita nova leitura dos caminhos de entrada e injeção em `MappingEngine`, `InputBindingPolicy`, `CaptureView`, `TouchInjector`, `InputStateMachine`, `SensitivityEngine` e das políticas de persistência. Esta passagem não substitui uma auditoria instrumentada em dispositivo.

## Achado corrigido

- **Gravidade: média.** `MappingEngine.onMouseButton` usava um `else` que classificava qualquer máscara diferente de botão primário/secundário como botão central. Um evento desconhecido/auxiliar poderia acionar um controle associado ao botão central.
- **Causa raiz:** tradução permissiva de códigos físicos, sem validar suporte.
- **Correção:** agora usa `InputBindingPolicy.mouseButtonCode(bit)` e ignora códigos não suportados. O mapeamento e a captura compartilham a mesma política de códigos.
- **Arquivos:** `app/src/main/java/com/example/keymapper/mapping/MappingEngine.kt`, `app/build.gradle.kts`, `CHANGELOG-v0.13.1.md`.

## Revisão técnica complementar

- `SensitivityEngine`: contém validação de NaN/Infinity, limites de entrada/saída, sensibilidade X/Y separada e acúmulo fracionário; há testes unitários para esses casos.
- `MappingDataPolicy`: normaliza IDs, limita o número de controles e remove referências quebradas de macros/links de mira; há testes unitários.
- `TouchInjector`: usa `recycle()` em `finally`, agrupa movimento relativo e mantém estado de contatos na thread de injeção. A correção real de falhas de injeção e a sequência multitoque ainda exigem teste de integração com Shizuku/dispositivo.
- `InputStateMachine`: estados e transições são sincronizados, mas a validade do fluxo completo depende de cenários de foco, permissão e serviço em aparelho real.
- `InputDiagnostics`: coleta contadores agregados e uma janela limitada de latências, sem armazenar histórico de teclas individuais.

## Estado de validação

| Item | Estado |
|---|---|
| Correção do mapeamento de botões desconhecidos | Implementada |
| Testes existentes de `InputBindingPolicy` | Presentes no código; JUnit não executado nesta sessão |
| Testes isolados de política pura | Executáveis com Kotlin local, fora do Gradle |
| Compilação Android / APK | Pendente — wrapper tenta baixar Gradle 8.8 e falha com `UnknownHostException: services.gradle.org` |
| Overlay sobre jogo real | Pendente — não testado em dispositivo |
| Shizuku, recuperação de falha e multitoque | Pendente — não testado em dispositivo |
| Auditoria visual de todas as telas em tamanhos/orientações | Parcial; requer execução visual em emulador/dispositivo |

## Limites
Esta entrega não declara que todas as funções avançadas solicitadas estejam implementadas. Não foi adicionada interface fictícia para opções que não estão ligadas ao motor. A validação completa de compilação, integração e hardware continua pendente até haver Gradle/dependências disponíveis e dispositivo de teste.
