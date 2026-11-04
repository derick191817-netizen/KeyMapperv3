# Changelog — KeyMapper 0.14.65

## Correção de recuperação das configurações

- `buttons` e `games` com valor JSON `null` ou tipo incorreto agora são identificados como dados inválidos.
- O JSON original é copiado para `settings_json_recovery_backup` antes da sanitização, evitando que um salvamento posterior apague o layout ou a lista de aplicativos sem cópia recuperável.
- Campos ausentes continuam compatíveis com arquivos de configuração de versões anteriores.
- Testes de política ampliados para cobrir arrays ausentes, válidos, `null` e de tipo incorreto.

## Limitações que permanecem

- Wireless ADB ainda está limitado a pareamento/conexão e diagnóstico somente leitura; não substitui o backend de entrada.
- Root ainda não possui provedor de ativação integrado.
- A ativação do serviço de mapeamento permanece dependente do Shizuku nesta versão.
- Build Gradle e teste físico no Android 13/One UI/DeX ainda precisam ser executados em ambiente com Android SDK e acesso à distribuição Gradle.
