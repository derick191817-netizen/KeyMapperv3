# Changelog — KeyMapper 0.14.64

## Corrigido

- Ownership de toque por origem: entrada física, Combo, toque transitório, tecla de mira, Fire->Aim e Aim->Fire deixam de disputar o mesmo `ACTION_UP`.
- Combos distintos podem acionar o mesmo controle sem que a soltura de um deles encerre o toque mantido pelo outro. Estados Toggle também são identificados pela origem.
- `releaseOwned()` só envia o último `ACTION_UP` quando a última origem é removida.
- IDs de ponteiro são preservados quando o backend ainda está aguardando a confirmação de liberação.
- Aim selecionada é rastreada por origem; ao liberar a mira mais recente, outra mira ainda ativa volta a ser selecionada.
- A ação “Atirar com a tecla de mira” só aciona controles Fire associados a `shootKey`, não quaisquer controles do mesmo código.
- `profiles: null` passa a ser tratado como coleção inválida e salvo no backup de recuperação antes de ser removido; `profiles` ausente continua válido para settings legados.

## Testes

- Casos de ownership simultâneo, Fire concorrente, tap transitório sobre Hold e seleção/restauração de Aim.
- Casos da política de recuperação para campo ausente, objeto válido e valor nulo/outro tipo inválido.
- Contratos CI para impedir regressão destas rotas.

## Limitações não resolvidas nesta entrega

- Gradle/Android Lint/JUnit via Gradle/assembleDebug dependem de baixar Gradle 8.8; falha DNS do ambiente impediu inicialização.
- Nenhum teste físico no Galaxy Note 20 Ultra / Android 13 / Samsung DeX foi executado.
- GitHub remoto não foi alterado por falta de permissão de escrita (HTTP 403).
