# KeyMapper 0.14.57 — Studio funcional e exposição completa dos controles

## Workspace / UI
- A prévia representa todos os controles persistidos: Tecla, Tiro e Mira.
- A prévia desenha as zonas WASD e FPS View a partir das coordenadas/raios do perfil.
- Os marcadores Tecla/Tiro/Mira podem ser selecionados, reposicionados e configurados.
- A tabela e o painel Propriedades deixam de classificar Tiro/Mira como controles legados sem edição.
- O arraste na prévia fica bloqueado até serviço, permissão de sobreposição, Shizuku e perfil ativo estarem confirmados, evitando movimento visual que não seria persistido.
- A seleção do controle permanece após uma alteração de posição; só muda automaticamente quando o item é removido do perfil.
- O Studio oferece criação direta dos três tipos e abre o formulário correspondente do HUD.
- A tabela identifica o tipo de controle e a tecla física/mouse associada.

## Motor / posicionamento
- A criação pelo Studio abre configuração real após persistir o controle.
- Novos controles e duplicatas Tecla/Tiro/Mira usam a política de colisão do HUD; deixam de nascer empilhados no centro.
- IDs continuam determinísticos e limitados a 128 por perfil.
- O nome da ação antiga `ACTION_ADD_STANDARD_CONTROL` foi preservado para compatibilidade; a nova ação aceita tipos validados.
- A rota de configuração antiga agora aceita qualquer controle cadastrado, pois `ConfigPanelView` já tem formulários próprios para Tecla, Tiro e Mira.

## Perfis / preferências
- Lista de aplicativos mostra se existe perfil personalizado e quantos controles ele contém.
- Texto de ajuda corrigido para refletir a persistência real de perfis por pacote e o fallback global.
- Sensibilidade global limitada no campo digitável a 0,01×–10×, igual aos limites de serialização de `AppSettings` e `AppProfile`; evita aceitar um número e reduzi-lo silenciosamente depois.

## CI / versão
- Adicionado `check-visible-control-types.sh` para detectar recursos implementados sem rota visível de criação, seleção ou configuração.
- Atualizado `check-hud-placement-policy.sh` para exigir posicionamento sem colisão para todos os três tipos.
- Versão Android: `0.14.57`, `versionCode 73`.
- Artefato esperado: `keymapper-0.14.57-debug`.
- Mantida a correção de compatibilidade Kadb/Jetifier da 0.14.56.
