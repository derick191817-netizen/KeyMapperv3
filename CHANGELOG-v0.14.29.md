# KeyMapper 0.14.29

**versionCode:** 45

## Alterações

- Pipeline do GitHub Actions agora executa `:app:lintDebug` antes dos testes unitários e da geração do APK Debug. Erros de análise estática passam a bloquear a publicação do artefato.
- Adicionado teste de fronteira para a validade do snapshot ADB: 15 minutos exatos continuam válidos; 15 minutos e 1 ms já são expirados.
- Atualizados nome e código da versão para 0.14.29 / 45.

## Limites

- Nenhuma rota nova de injeção foi declarada. ADB sem fio continua sendo pareamento/conexão/diagnóstico; o caminho de entrada do serviço permanece Shizuku + InputManager.
- Nenhuma alteração global de resolução/densidade foi ativada nesta versão; falta o fluxo completo de confirmação e rollback persistente.
- Não há APK validado nesta sessão: o ambiente não tem Gradle instalado/cacheado nem Android SDK configurado, e o wrapper precisa baixar Gradle 8.8.
