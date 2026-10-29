
## v3.0 — arquitetura e compartilhamento
A tela de configurações usa `SettingsViewModel` com `StateFlow`; `SettingsRepository` continua usando Preferences DataStore e é compartilhado pelo `KeyMapperApp` entre Activity e Service. A aba Sistema contém as ações **Compartilhar Configurações** e **Compartilhar Log de Erro** pelo seletor nativo do Android. `CrashReporter` registra o último crash não tratado para inclusão no relatório quando o usuário o solicita.

Hilt não foi incluído porque a restrição desta atualização proíbe alterar os arquivos Gradle e as dependências existentes. A composição das dependências é manual. Consulte `RELATORIO-v3.0-arquitetura-compartilhamento.txt` para limitações e validação.

## v4.1 — editor e seleção ampla de aplicativos

Esta revisão acrescenta um editor de controles para a lista `HudButton`, com área de referência identificada como ilustrativa, seleção/arrasto, criação de tecla/tiro/mira e persistência pelo repositório existente. A seleção de aplicativos deixou de ser filtrada apenas para Free Fire/Free Fire MAX e passou a listar apps iniciáveis do dispositivo. Os pacotes selecionados compartilham a configuração global nesta versão; perfis independentes por app ainda não estão implementados. Consulte `RELATORIO-v4.1-editor-apps.txt` para limites e validações.

## v4.9 — redesign responsivo do editor e HUD

O editor Compose agora separa título/estado das ações, permite rolar a faixa de ações e rolar verticalmente o conteúdo compacto para alcançar as propriedades. A barra inferior de Configurações se adapta a larguras inferiores a 430 dp. No HUD, as ações de edição e o seletor de tipo usam coordenadas relativas à janela, e a biblioteca lateral foi simplificada para evitar duplicação com o botão +. O rótulo OLHAR foi substituído por CÂMERA sem remover a área de câmera do motor. Consulte `RELATORIO-v4.9-redesign-responsivo.txt` para testes e limitações. A compilação Gradle e a validação em dispositivo ainda precisam ser executadas num ambiente Android configurado.
