# Changelog — KeyMapper 0.14.33

**versionCode:** 49
**Base:** fonte 0.14.32

## Workspace estilo emulador de PC

- A prévia 16:9 agora permite arrastar controles padrão e grava as posições normalizadas no perfil selecionado. O snap segue a grade configurada; o HUD ao vivo permanece para ajustes finos.

- Navegação desktop substituída por uma sidebar lateral compacta, com identidade KeyMapper Studio e destinos Painel, Editor e Ajustes; a navegação inferior do celular permanece.
- Editor reorganizado como workspace responsivo: estado do serviço/permissões/perfil, ferramentas de edição, prévia 16:9 arrastável, tabela de associações e painel de propriedades.
- O layout de três colunas é habilitado a partir de 760 dp de largura útil, depois da sidebar, para caber em Samsung DeX com densidade típica de telefone; em telas menores, as seções são empilhadas.
- Adicionada ação real para criar uma associação padrão sem tecla atribuída, gerar ID livre, persistir no perfil ativo e abrir o painel existente de propriedades. O utilizador escolhe a tecla física no painel.
- Controles especializados legados continuam listados como legado; o workspace novo não altera os parâmetros de mira/disparo.

## Correções funcionais

- A abertura do aplicativo selecionado respeita `useExternalDisplay` persistido antes de resolver o display. Se o monitor externo selecionado não estiver disponível, a tela apresenta falha em vez de enviar o app silenciosamente para o display errado.
- Classificação nativa de teclado deixou de exigir a presença simultânea de A, Espaço e Enter; agora usa a quantidade mínima de quatro capacidades de tecla na faixa de teclado. Isso abrange teclados compactos e numpads sem classificar botões de mouse como teclas.
- Quando `EVIOCSCLOCKID(CLOCK_MONOTONIC)` é recusado, o leitor converte os timestamps padrão `CLOCK_REALTIME` para a base monotônica usando uma amostra de offset. Isso preserva a diferença temporal entre eventos; `clock_gettime(CLOCK_MONOTONIC)` permanece como fallback final.
- Corrigida a divergência da auditoria anterior: o lote nativo continua sendo encaminhado ao looper principal nesta versão. Não se anuncia despacho mouse-only em thread separada nem melhoria de latência de movimento relativo.

## Ferramentas

- Kotlin Gradle Plugin e Compose Compiler Plugin atualizados para 2.4.21.
- Mantidos AGP 8.5.2, Gradle Wrapper 8.8, JDK 17, compileSdk/targetSdk 34, NDK 26.3.11579264 e CMake 3.22.1.
- Versionamento atualizado para `0.14.33` / `49`; artefato do GitHub Actions passa a usar `keymapper-0.14.33-debug`.

## Limites

- Saída continua usando `InputManager` por Shizuku; o backend touchscreen próprio via `/dev/uinput` ainda não foi implementado.
- Captura evdev, `EVIOCGRAB`, injeção InputManager, foco do overlay e display externo ainda precisam ser validados no firmware Samsung/Android 13/DeX.
- Esta entrega não inclui APK validado nem teste físico ponta a ponta; não é possível afirmar que esteja jogável.
