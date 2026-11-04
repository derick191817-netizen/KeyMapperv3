# Matriz de comparação funcional — KeyMapper 0.14.54

**Referência principal:** SS KeyMouse Pro / KeyMouse Pro.

**Método:** inspeção do código-fonte do KeyMapper 0.14.53 e documentação pública das referências. As páginas dos fornecedores descrevem recursos anunciados; isso não prova desempenho, latência ou compatibilidade em todos os dispositivos. Não foram copiados código, binários, nomes internos ou assets proprietários.

## Comparação das áreas gerais do mapeador

| Função | KeyMapper 0.14.53 (código inspecionado) | KeyMouse Pro (documentação pública) | Panda Mouse Pro (documentação pública) | K2er / GG Mouse (documentação/visual público) | Decisão desta etapa |
|---|---|---|---|---|---|
| Mapeamento de teclado e mouse | `HudButton`, `MappingEngine`, `TouchInjector` e captura nativa; aceita códigos de teclado e botões M1–M5 | Mapeamento de teclas e botões do mouse para toques é a função central anunciada | A ficha oficial anuncia compatibilidade ampla com teclados e mouses | K2er lista Tap para teclado/mouse; GG apresenta Touch/Tap na biblioteca | Preservado; a próxima auditoria deve comprovar cada caminho de evento de ponta a ponta |
| Editor HUD / keymap | `HudView`, `OverlayManager`, `ConfigPanelView`; mover, configurar, duplicar, apagar e persistir | HUD flutuante com opacidade ajustável | Editor visual de keymap e sobreposição | K2er/GG apresentam biblioteca de componentes e editor de keymap | Corrigidos os pontos de criação/duplicação de controles padrão que usavam posição fixa e podiam sobrepor controles |
| Movimentação WASD | Áreas `joyX`, `joyY`, `joyR` e processamento no `MappingEngine` | WASD analógico configurável anunciado | A ficha documenta uso de teclado/mouse, mas não detalha toda a matemática do analógico | K2er lista D-pad e D-pad Stick | Preservado nesta etapa; transições, diagonais e liberação serão auditadas separadamente |
| Gestos de toque e arrasto | O motor tem primitives DOWN/MOVE/UP; não foi confirmado um componente Swipe independente na biblioteca atual | O guia público do Script descreve operações gerais de toque, pausa e arrasto | A documentação pública não é suficiente para confirmar toda a semântica atual de gestos | K2er lista Swipe; GG apresenta Swipe na biblioteca visual | Não duplicar componentes sem necessidade; primeiro ligar o gesto manual ao modelo de dados e aos testes |
| Scroll do mouse | `MouseScrollAccumulator` e processamento por eixo em `MappingEngine` | A página pública consultada não detalha a configuração de scroll | Histórico público registra zoom com Ctrl + roda em versões anteriores | GG apresenta Mouse Scroll; K2er documenta suporte a dispositivos de entrada diversos | Mantido; validar orientação, passos e conversão em etapa própria |
| Macros / Script genérico | `HudButton.macro` é uma lista limitada de IDs de controles acionados em conjunto; não é uma linguagem de sequência temporal | Guia público descreve uma DSL com operações temporizadas e coordenadas percentuais | Histórico público registra evolução de recursos macro em versões anteriores | K2er lista Macro; GG documenta triggers e operações | Lacuna real registrada. Uma implementação futura exige parser limitado, estado por execução, cancelamento e teste de não execução após cancelar |
| Captura e ativação | evdev/C++ com Shizuku, AIDL e fallback Pointer Capture | Requisitos públicos incluem Shizuku e permissão de sobreposição | A ficha documenta ativação direta em Android 11+ e por PC/Mac em Android antigo | K2er documenta métodos de ativação e diagnóstico; GG anuncia operação sem root | Manter somente rotas suportadas pelas APIs disponíveis e comunicar limitações reais |
| Perfis e persistência | `AppProfile`, `AppSettings`, `SettingsRepository` | A documentação apresenta keymap por jogo | A ficha menciona inicialização direta de jogos | K2er/GG tratam layouts/keymaps | Não alterar esquema nesta etapa; preservar compatibilidade com perfis antigos |
| Múltiplos dispositivos | Captura mantém estado por `deviceId`; hot-plug tratado por `InputDeviceManager` | Página pública não detalha agregação de dispositivos | Compatibilidade ampla é anunciada, sem especificar o modelo de estado simultâneo | K2er declara mapeamento simultâneo de múltiplos dispositivos | Preservado; exige validação física com mais de um periférico conectado |
| Posicionamento automático | Antes desta versão, criação no centro fixo e duplicação com deslocamento fixo | Keymap visual com componentes posicionáveis | Editor visual | Editores visuais dos outros mapeadores permitem posicionamento de componentes | Adicionada política determinística que procura espaço livre e mantém o controle dentro da área |

## Resultado da auditoria desta versão

### Implementado agora

1. `HudPlacementPolicy` é uma política Kotlin pura, sem dependências Android.
2. A criação prefere o centro quando o canvas está vazio; caso contrário, avalia posições candidatas e maximiza a distância mínima para controles já existentes.
3. A duplicação usa a mesma política, tendo o deslocamento legado apenas como preferência inicial.
4. Os limites levam em conta o raio visual do controle e dimensões independentes de largura/altura.
5. Cinco testes cobrem canvas vazio, colisão, limites, geometria inválida e entradas ocupadas inválidas.
6. O GitHub Actions executa um contrato estático para garantir que os caminhos de criação e duplicação continuem usando a política.

### Lacunas confirmadas, não mascaradas

- `HudButton.macro` não é equivalente a um Script temporal: hoje representa relações por IDs de outros controles.
- O motor possui primitivas DOWN/MOVE/UP, mas o editor não expõe todos os gestos documentados nas referências como componentes independentes.
- A presença de campos em `HudButton` ou `AppSettings` não prova que todo parâmetro tenha efeito em todos os backends.
- Testes host não comprovam que um jogo ou app de destino consuma os eventos da mesma forma no Android 13/One UI/DeX.
- Recursos de assistência de combate não fazem parte desta entrega; não foram alterados nem validados nesta etapa.

## Prioridade técnica derivada

1. **P0 — editor:** posição visível, não sobreposta, seleção previsível e persistência (implementado nesta versão).
2. **P1 — integração dos controles gerais:** mapear cada controle existente da biblioteca até o motor e comprovar DOWN/MOVE/UP/UP em falhas e desconexões.
3. **P1 — captura e overlay:** validar perda de foco, mudanças de display, DeX, hot-plug, reinício e encerramento.
4. **P2 — scroll e múltiplos dispositivos:** provar equivalência entre evdev e fallback.
5. **P2 — Script genérico:** especificar parser, limites de instruções/duração, cancelamento imediato, estado por sessão e testes após cancelamento.
6. **P3 — persistência e migração:** validar schemas antigos, perfis grandes e alteração de configuração durante execução.

## Fontes públicas consultadas

- SS KeyMouse Pro: https://keymouse.com.br/index.html
- Guia Script do SS KeyMouse Pro: https://keymouse.com.br/guia/macros.html
- Ficha Google Play do SS KeyMouse Pro: https://play.google.com/store/apps/details?id=com.sousasantoslogic.sspro
- Ficha Google Play do Panda Mouse Pro: https://play.google.com/store/apps/details?id=com.panda.mouse
- Documentação do K2er: https://doc.k2er.com/
- FAQ e catálogo de mapeadores do K2er: https://doc.k2er.com/faq.html
- Ficha Google Play do GG Mouse Pro 3: https://play.google.com/store/apps/details?id=com.zjx.ztezscreenshot

## Limites metodológicos

- Documentação e listagens descrevem capacidades declaradas pelo fornecedor; não são prova de desempenho no Note 20 Ultra.
- Não houve execução instrumentada das aplicações de referência nesta etapa.
- O comportamento privado de aplicativos comerciais não foi inferido de nomes de funções ou de imagens isoladas.
- A análise estática histórica presente em `docs/KEYMOUSE-PRO-REFERENCE-ANALYSIS.md` continua sendo evidência do binário analisado naquela amostra, não uma garantia sobre versões atuais.
