
## v3.0 — arquitetura e compartilhamento
A tela de configurações usa `SettingsViewModel` com `StateFlow`; `SettingsRepository` continua usando Preferences DataStore e é compartilhado pelo `KeyMapperApp` entre Activity e Service. A aba Sistema contém as ações **Compartilhar Configurações** e **Compartilhar Log de Erro** pelo seletor nativo do Android. `CrashReporter` registra o último crash não tratado para inclusão no relatório quando o usuário o solicita.

Hilt não foi incluído porque a restrição desta atualização proíbe alterar os arquivos Gradle e as dependências existentes. A composição das dependências é manual. Consulte `RELATORIO-v3.0-arquitetura-compartilhamento.txt` para limitações e validação.
