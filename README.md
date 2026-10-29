# KeyMapper v2.1

Mapeador de teclado e mouse para Android (Kotlin + Jetpack Compose): converte teclas e movimento
do mouse em toques, com HUD arrastável estilo emulador e suporte a Samsung DeX / monitor externo.

> **Fora do escopo, de propósito:** randomização/jitter de toque, controle/compensação automática de recuo
> e rajada/turbo (repetição automática de tiro). O app só mapeia entrada: um aperto = um toque, e "segurar"
> mantém o toque enquanto a tecla estiver pressionada. Verifique os termos de uso do jogo.



## Modos de rotação da câmera (v2.1)

Configurações › Mira (global) e na engrenagem de cada botão de mira (por botão):

- **Somente Centro (padrão, fallback seguro):** o dedo virtual soma os deltas do mouse; ao passar da borda real da tela
  faz MOVE até a borda, UP e DOWN no centro (ponto de origem da câmera) na mesma chamada (`TouchInjector.swap`) e
  continua dali, sem pausa.
- **Somente Infinito (jogos 3D):** coordenadas virtuais sem limite de tela e sem UP; o MOVE sai com o x/y virtual.
  Proteção numérica em ±100.000 px (reancora na origem sem saltar a coordenada).
- **Desligado:** a mira para na borda.
- **Automático:** Infinito em Free Fire, PUBG e Call of Duty Mobile; Centro nos demais.

Estabilidade do `TouchInjector`: throttling de 8 ms (120 Hz) nos ACTION_MOVE (a última posição sempre chega antes de
um UP/DOWN), `MotionEvent.recycle()` em try/finally, injeção em try/catch (Throwable) e parada suave se o Shizuku cair
(volta sozinho quando responder).

## Novidades da v0.6.3

- **Câmera infinita (canvas virtual):** o dedo virtual soma o movimento do mouse sem limite de tela e sem voltar ao
  centro; o evento sai pelo Shizuku com coordenadas virtuais. Modos compatíveis (recomeçam do centro) ficam como opção.
- **Câmera/mira como em emulador de PC:** os movimentos do mouse (até 1000 Hz) são somados e enviados ao jogo em
  intervalos fixos (padrão 4 ms), com curvas normalizadas por tempo. Ao parar o mouse o dedo virtual solta e o
  próximo movimento recomeça do centro da área; giros longos quase nunca chegam à borda (360º sem parar).
  Dois modos de recomeço (soltar e tocar no mesmo instante, ou dois dedos) e mouse relativo nativo (experimental).
- **Auto-start/stop:** o serviço fica de prontidão e liga o overlay quando um dos apps escolhidos abre, desligando ao sair.
  A tela inicial tem um botão "Iniciar" para cada app escolhido.
- **Lista completa de apps** (permissão QUERY_ALL_PACKAGES, a única mudança no Manifest) com busca.
- **Editor do botão com aplicação imediata** (sem Salvar). O tipo (Padrão, Tiro, Mira) fica no topo; ao criar um botão
  com + você escolhe o tipo e o painel já abre. Em tela deitada o painel é lateral e mostra todas as opções.
- **Tela preta:** janela de captura de 1 pixel, HUD transparente e não focável, sem fixar modo de tela, tela do
  aparelho como padrão e chaves de diagnóstico em Configurações › Sistema.

## Botões (só 3 tipos, como no BlueStacks)

- **Padrão:** simula qualquer tecla. Modos: toque simples, segurar, alternar, duplo toque; tempo de resposta 0 a 500 ms; macro opcional.
- **Tiro:** segurar contínuo (ACTION_DOWN ao apertar, ACTION_UP só ao soltar a tecla) ou toque único.
- **Mira:** sensibilidade X/Y (1 a 10, 1 = neutro; digite 0 a 1000), suspender (toggle), deadzone; o restante
  (tecla de tiro, sensibilidade geral/mouse, aceleração, cruz de mira, posição, inverter, mouse aim, zoom)
  fica em "Opções avançadas".

## Câmera 360º

O mouse vira um arrasto de dedo na área de câmera. Ao chegar no limite da área, um segundo dedo já é posto no
centro antes de soltar o primeiro: a câmera nunca para e dá para girar sem fim. (Eventos de mouse relativos não
são injetados porque o jogo é controlado por toque e ignoraria esses eventos.)

## Tela inicial e ativação

Painel com status colorido do Shizuku (verde/vermelho), Iniciar/Parar Mapeamento, permissões, ativação por
depuração sem fio (passo a passo e atalho para as Opções do desenvolvedor), jogo e configurações.

## Configurações

Abas laterais: **Mouse** (velocidade do ponteiro 1 a 11, padrão 6, "Melhorar precisão do ponteiro", X/Y,
deadzone, curvas, calibração), **Mira**, **Aparência** (estilos de HUD, grade, tema, escala), **Tela / DeX**,
**Jogos** e **Sistema** (desempenho, atalhos, diagnóstico, reset).

## Como usar

1. Instale e inicie o **Shizuku** (guia no painel inicial).
2. Permita **Sobreposição** e a **Permissão Shizuku**; toque em **Iniciar Mapeamento**.
3. Abra o jogo. Toque na bolha de engrenagem para o menu rápido. No modo edição, arraste a alça (pontinhos) para
   mover e toque na engrenagem do botão para configurar.

## Limitações conhecidas

- Não foi compilado nem testado em aparelho pelo autor deste pacote.
- A detecção do jogo em primeiro plano usa API interna do Android via Shizuku e pode não funcionar em todas as versões.
- Modo janela do DeX: a janela do jogo é informada manualmente (aba Tela / DeX).
