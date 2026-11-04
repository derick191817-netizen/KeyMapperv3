# Changelog — KeyMapper 0.14.61

**Versão:** `0.14.61`  
**versionCode:** `77`  
**Base de integração:** pacote-fonte 0.14.60 + correções e reestruturação da biblioteca HUD.

## Biblioteca principal

- Reorganizada a biblioteca HUD na ordem: **Tap, Hold, Fire, WASD / Stick, Aim / Sight e Swipe**.
- Fire permanece como terceiro controle principal, com identidade visual e configuração próprias; não é um alias de Tap ou Hold.
- Tap e Hold mantêm modos distintos no motor padrão de toque.
- Aim e Sight são apresentados como uma única ferramenta de controle de câmera.
- WASD / Stick reaproveita a zona virtual de movimento existente, sem anunciar suporte a joystick analógico físico que ainda não existe.
- Swipe tem associação de entrada, origem visual, destino X/Y e duração configuráveis.
- Ícones vetoriais padronizados e cores por tipo no editor e nos marcadores do HUD.

## Configuração e motor

- Configuração do Fire separada, com associação física configurável, modo de toque único/segurar e vínculo opcional com Aim.
- Fire sugere botão esquerdo do mouse apenas quando M1 está livre e não conflita com atalhos globais; caso contrário fica sem associação, evitando roubar um binding existente.
- Swipe recebe entrada física configurável e usa origem, destino e duração persistidos.
- Rapid Tap tem intervalo delimitado e cancelamento no evento de soltura.
- Combo ganhou identidade persistida (`isCombo`) independente de possuir destinos, eliminando o fallback incorreto para um toque comum quando está vazio.
- Combo vazio não injeta eventos; Combo não aceita a si próprio, outros Combos, Swipe ou Rapid Tap como destinos.
- Rótulos comuns da prévia, tabela e propriedades foram alinhados.
- Interface informa explicitamente que Macro sequencial e entrada analógica física de gamepad ainda não estão implementadas.
- Scroll é apresentado honestamente como mapeamento global de roda do mouse, não como ferramenta HUD independente.

## Persistência e compatibilidade

- Campo `isCombo` serializado em JSON e migrado a partir de perfis antigos pelo nome/macro legado.
- Preservados os campos existentes de posições, IDs, associação e perfis.
- `versionCode` incrementado para 77 e artefato CI atualizado para `keymapper-0.14.61-debug`.
- Verificadores de versão, dependências e tipos visíveis atualizados.

## Validação

- Testes nativos, de arquitetura, políticas e toolchain host aprovados (consultar `TEST-RESULTS-KEYMAPPER-V0.14.61.txt`).
- Harness Kotlin isolado para as políticas puras aprovado.
- Lint, JUnit pelo Gradle, `assembleDebug` e teste físico permanecem pendentes: o Gradle Wrapper não conseguiu baixar Gradle 8.8 por falha DNS de `services.gradle.org`.
- Nenhuma publicação no GitHub foi realizada; a integração conectada anteriormente recusou escrita com HTTP 403.
