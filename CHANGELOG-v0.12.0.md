# KeyMapper 0.12.0 — Editor de overlay real

## Alterações

- Removida da aba principal a área de posicionamento 16:9 simulada. A aba Editor agora orienta o fluxo de edição sobre o app real, mostra o estado do serviço/permissão/Shizuku e permite abrir o app do perfil quando possível.
- Mantido o editor real em janela `TYPE_APPLICATION_OVERLAY`; a posição dos marcadores deve ser ajustada com o jogo aberto.
- Ações desenhadas no HUD só são ativadas quando o gesto termina como toque, evitando que um arraste iniciado sobre a barra execute uma ação.
- Marcadores selecionáveis podem ser arrastados pelo corpo ou pela alça; em áreas sobrepostas, a seleção favorece o marcador mais próximo do centro normalizado.
- Incluídos comandos para salvar e sair da sessão inteira ou cancelar a sessão e restaurar o snapshot de entrada; fechar o overlay pelo X durante a edição também descarta antes de encerrar.
- Incluída duplicação de controle com ID livre e sem associação de tecla copiada; Mira/Tiro também limpam referências de associação potencialmente inválidas na cópia.
- A criação de um controle detecta associação duplicada e cria o controle sem tecla, em vez de sobrescrever/duplicar silenciosamente.
- O editor consome eventos de teclado durante a edição, mantendo a captura do ponteiro parada; abrir o painel de propriedades restaura o foco para editar associações.
- Exclusão limpa referências `macro` e `linkAimId`; o motor ignora tecla `0`, usada para controles ainda sem associação.
- Regras puras de associação, conflito, IDs, gestos e coordenadas normalizadas foram extraídas para `MappingEditorPolicy` com testes unitários.
- Versão atualizada para `versionCode 13` e `versionName 0.12.0`.

## Limitações conhecidas

- Não há confirmação de compilação Android completa: o Gradle Wrapper não conseguiu baixar Gradle 8.8 devido a falha DNS para `services.gradle.org`.
- Nenhum APK foi gerado, os testes JUnit do Gradle não foram executados e o overlay não foi verificado em aparelho real nesta sessão.
- Captura de teclado, comportamento em jogo protegido, compatibilidade específica do dispositivo e injeção via Shizuku exigem validação manual.
