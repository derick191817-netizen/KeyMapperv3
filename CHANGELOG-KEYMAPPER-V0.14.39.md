# Changelog — KeyMapper 0.14.39

**versionName:** `0.14.39`  
**versionCode:** `55`  
**Base:** KeyMapper 0.14.38

## Continuous Camera Movement — Sight/Aim

- Adicionada a estratégia `NONSTOP` global e por controle Sight/Aim. Durante a recentralização, o novo contato touchscreen recebe `ACTION_POINTER_DOWN` antes de o contato anterior receber `ACTION_POINTER_UP`. O conjunto de contatos não fica vazio durante o handover.
- O modo `NONSTOP` apresenta dois ponteiros simultâneos por um intervalo curto. Jogos que interpretam multitouch podem rejeitar ou alterar o gesto; por isso o modo é selecionável e não é assumido como universal.
- Criada `TouchHandoffPolicy`: se não houver slot adicional disponível dentro do limite de ponteiros adotado pelo app, o motor degrada aquele handover para a sequência clássica `UP/DOWN`. Falhas reais de envio continuam sendo falhas, não são ocultadas como sucesso.
- `TouchInjector.swap()` passa a retornar sucesso/falha real. `MappingEngine` só altera o ID ativo depois de um handover concluído; falhas cancelam a cadeia de câmera e limpam deltas pendentes para evitar movimento antigo.
- `beginLook()` só marca a visão ativa depois de confirmar que o contato existe no injetor.
- As configurações globais e individuais dos controles Sight/Aim oferecem Automático, Nonstop e Mouse Relativo Nativo. Valores persistidos anteriores `CENTER`, `AUTO`, `INFINITE` e `OFF` continuam aceitos.
- O painel flutuante usa o rótulo explícito “Movimento contínuo da câmera”. A interface esclarece que o modo global é apenas o padrão e pode ser sobrescrito por cada Sight/Aim.

## Precisão subpixel

- O resto fracionário interno de `SensitivityEngine` foi alterado de `Float` para `Double`, mantendo os deltas de entrada/saída na API existente. Isso evita que erros próximos a um inteiro negativo sejam truncados para zero e criem viés direcional em sensibilidades baixas.
- Acrescentados testes de deltas de `0,35 px`, sensibilidade `0,1`, reversão de direção e cancelamento do caminho.

## Testes

- Testes de planejamento de câmera: quatro bordas, diagonais, múltiplos limites, deltas grandes em lotes e mudanças de direção.
- Testes de cursor lógico: coordenadas independentes da dimensão física do display e reancoragem visual.
- Testes de política de modo: Automático, Nonstop, relativo nativo, override por controle e prioridade de OFF.
- Testes de capacidade de ponteiros: uso de handover sobreposto somente quando existe slot adicional; fallback sequencial quando não existe.
- Testes de sensibilidade: acumulação fracionária, sensibilidade baixa, inversão de direção, reset e estabilidade por polling.

**Harness Kotlin isolado desta entrega:** 48 testes passaram. Essa execução não inclui classes Android dependentes de framework nem simula o consumo dos eventos pelo jogo.

## Toolchain e limites

- Incremento para `0.14.39` / `55`.
- GitHub Actions executa Lint, testes Gradle e `assembleDebug`, e publica `keymapper-0.14.39-debug` somente após sucesso.
- O build Gradle local não iniciou: o wrapper tentou obter Gradle 8.8 e falhou com `UnknownHostException: services.gradle.org`. O ambiente não possui Android SDK configurado. Portanto, não há APK compilado nem resultado de Lint/Gradle nesta entrega.
- Instalação e validação física no Samsung Note 20 Ultra, Android 13, One UI e Samsung DeX continuam pendentes.
