# KeyMapper 0.14.29 — revisão incremental de confiabilidade

**versionName:** 0.14.29  
**versionCode:** 45  
**Base:** fonte 0.14.28  
**Escopo desta revisão:** pipeline de validação, teste de fronteira de validade do snapshot ADB e conferência estática do estado arquitetural.

## Resultado executivo

A 0.14.29 é uma atualização incremental de validação. Ela não deve ser apresentada como uma reescrita do motor nem como uma versão comprovadamente estável em telefone. O maior avanço desta revisão é tornar a integração contínua mais rigorosa: Android Lint agora é uma etapa obrigatória antes dos testes e da geração do APK.

## Alterações aplicadas

1. `versionName` atualizado para `0.14.29` e `versionCode` para `45`.
2. GitHub Actions executa `:app:lintDebug`, depois `:app:testDebugUnitTest` e `:app:assembleDebug`.
3. O artefato da CI foi renomeado para `keymapper-0.14.29-debug`.
4. Foi adicionado teste unitário que fixa a fronteira do snapshot ADB: 900.000 ms é aceito; 900.001 ms é rejeitado.

## Arquitetura confirmada pela leitura do código

| Subsistema | Estado observado |
|---|---|
| Captura de mouse/teclado | `CaptureView` com Pointer Capture e fallback apenas para eixos explicitamente relativos |
| Saída de eventos | `TouchInjector` e `ShizukuInjector` via Binder/InputManager |
| ADB sem fio | `WirelessAdbClient` usa Kadb para pareamento TLS, conexão e comandos diagnósticos fixos |
| ADB como backend do motor | Não conectado ao `MappingService`; não declarar como backend de entrada |
| Root independente | Não implementado como backend |
| evdev/uinput | Não implementados |
| Display/DeX | `DisplayHelper`, `DisplayUtils`, contexto por display e seleção explícita |
| Alteração global de resolução/densidade | Não implementada; apenas leitura/fallback de métricas |
| Rollback persistente de resolução/densidade | Não implementado |

## Referências de mercado

A documentação de inspeção da 0.14.28 continua válida: os pacotes Panda Mouse Pro, SS KeyMouse Pro e ZTE/GG Mouse foram analisados estaticamente (manifestos, DEX, strings e bibliotecas). A semelhança arquitetural mais útil é separar ativação, serviço privilegiado, captura, saída, HUD e gerenciamento de display. A análise estática não demonstra comportamento real em runtime e nenhum código proprietário foi copiado.

## Testes executados nesta sessão

- Compilação isolada de sete políticas Kotlin puras com `kotlinc` 1.9.0: aprovada.
- Smoke test manual de 26 cenários: 26/26 aprovados, incluindo IPv4/IPv6, rejeição de hostname/shell, portas, código de pareamento, limite API 30, parsing `wm size`/`wm density`, origem local, idade de snapshot, seleção de display, geometria, retry de captura, deltas relativos e limites de ponteiros.
- O primeiro smoke test usou 900.000 ms como snapshot vencido; isso revelou que o limite é inclusivo. O caso foi corrigido para 900.001 ms, e a fronteira foi adicionada como teste unitário explícito. Não era bug na política; era expectativa incorreta no teste exploratório.
- XML do Manifesto validado.
- YAML do workflow validado sintaticamente.
- Integridade do ZIP de referência anterior preservada na base.

## O que não foi validado

- `:app:lintDebug`, `:app:testDebugUnitTest` e `:app:assembleDebug` não foram executados neste ambiente.
- O ambiente não possui Gradle instalado/cacheado nem Android SDK configurado; o wrapper 8.8 precisa de rede para baixar a distribuição.
- Não foi gerado APK, instalado ou testado no Samsung Note 20 Ultra / Android 13 / DeX.
- Não foram validados pareamento ADB real, autorização Shizuku, injeção, captura em periféricos físicos, overlays, rotação ou desconexão do DeX.

## Próximas correções estruturais necessárias

1. Criar um modelo de capacidades por backend e ligar o resultado à interface, distinguindo `disponível`, `autorizado`, `inicializado` e `verificado`.
2. Não apresentar o ADB TLS como backend de entrada até existir implementação integrada e testes end-to-end.
3. Projetar alteração de resolução/densidade como operações tipadas, limitadas ao aparelho local, com confirmação e rollback persistente que sobreviva ao encerramento do processo/reboot.
4. Validar o lifecycle do serviço, overlay e captura em dispositivo real, incluindo troca de display, revogação de permissão e queda/reconexão do Shizuku.
5. Executar a CI completa e corrigir todos os erros de Lint/compilação antes de distribuir APK.

**Conclusão:** a 0.14.29 melhora a barreira de qualidade do CI e fixa uma fronteira de validade ADB com teste explícito. Não há base para afirmar que o aplicativo inteiro está sem bugs ou crashes. O próximo avanço relevante é integrar um modelo real de capacidades e completar testes end-to-end, não apenas adicionar mais opções na interface.
