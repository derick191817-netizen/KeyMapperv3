# Auditoria focada do editor — 0.12.0

## Escopo observado

O repositório local contém um editor de HUD flutuante e um editor Compose na aplicação. O HUD usa `TYPE_APPLICATION_OVERLAY`, enquanto o caminho Compose anterior mantinha um canvas de posicionamento simulado. O caminho simulado foi retirado para não insinuar que aquelas coordenadas eram as da tela real do jogo.

## Causas e correções

| Problema observado no código | Correção nesta versão | Evidência / limite |
|---|---|---|
| Posicionamento em uma área 16:9 dentro do KeyMapper | A tela Compose agora é um guia para ativar e abrir o app alvo; posicionamento pelo HUD real | Verificado estaticamente em `MappingEditorScreen.kt`; falta teste no aparelho |
| Ações da barra disparadas ao toque inicial e confundidas com arraste | A ação ocorre no final do gesto se for toque dentro do slop | `HudView.onTouchEvent` e `MappingEditorPolicy.isTap`; testes puros |
| Seleção ambígua em marcadores sobrepostos | Escolhe candidato com menor distância normalizada | Implementação estática, sem teste instrumentado da View |
| Duplicar poderia repetir associação de tecla | Novo controle recebe tecla `0` e exige associação posterior | Código verificado; teste end-to-end ausente |
| Duplicatas de tecla silenciosas na criação | Verifica conflito, informa ao usuário e deixa o novo controle sem tecla | Código verificado; UX precisa ser testada na tela |
| Cancelar só o painel não revertia a sessão inteira | Botão `↶` restaura o snapshot do começo da sessão; `✓` salva e sai; X descarta antes de fechar | Persistência via host; não validado sob encerramento abrupto |
| Tecla não atribuída poderia ser interpretada como ação | `MappingEngine` ignora tecla `0` | Código estático |
| Exclusão podia deixar referências internas | Limpa `macro` e `linkAimId` apontados para o controle removido | Código estático |
| Configuração global ocupava espaço em cada painel | Seção de ajustes globais fica recolhida inicialmente | Interface não inspecionada em dispositivo |

## Associações e propriedades

O painel individual existente foi preservado e ajustado. A seção de mouse/câmera é recolhida inicialmente; os campos não foram substituídos por opções cosméticas novas. A captura de tecla rejeita `KEYCODE_UNKNOWN` e conserva o estado de espera. Há detecção de conflito com outras associações e atalhos globais, enquanto WASD continua reservado para o joystick conforme as regras atuais.

A duplicação não clona a tecla principal; também limpa a tecla de disparo para uma cópia de mira e remove o vínculo de mira para uma cópia de tiro. Isso reduz ativação involuntária antes de o utilizador configurar a cópia.

## Limitações importantes

A presença de `TYPE_APPLICATION_OVERLAY` comprova apenas que a arquitetura tenta desenhar uma janela acima de outros apps. Não comprova que cada jogo deixe o overlay visível, que o fabricante conceda foco a uma janela de captura minúscula, ou que o mecanismo de injeção via Shizuku funcione em todas as versões de Android.

Os controles não têm reconhecimento automático da interface do jogo — o usuário os posiciona manualmente, conforme solicitado. Não foi adicionada funcionalidade de desfazer/refazer, pois as alterações do perfil já são tratadas pelo modelo atual e uma pilha transacional exigiria refatoração adicional e testes Android; a sessão completa oferece salvar/cancelar. Também não foi possível testar o APK porque o download da distribuição Gradle falhou.
