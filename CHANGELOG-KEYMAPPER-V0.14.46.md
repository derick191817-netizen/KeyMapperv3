# KeyMapper 0.14.46 — ciclo de vida e reconexão do UserService Shizuku

## Alterações

- Corrigido o registro dos listeners do Shizuku no `ShizukuInputCaptureBridge`: Binder recebido, Binder morto e resultado de permissão possuem estados de registro independentes. Uma falha ao registrar um deles não impede a repetição dos demais.
- O bridge mantém listeners durante a sessão ativa e remove somente os que foram efetivamente registrados durante `stop()`.
- O vínculo é reconstruído após `onBindingDied` e `onNullBinding`; o vínculo antigo, timeout e estado de captura são invalidados antes do rebind.
- `onServiceDisconnected` não força unbind imediato; mantém o `ServiceConnection` para permitir a reconexão automática do Android ao mesmo serviço.
- Implementada política de backoff limitado: 1 s, 2 s, 5 s, 10 s e 30 s, até cinco tentativas automáticas por rodada.
- Após esgotar uma rodada, uma nova solicitação de captura ativa pode recomeçar as tentativas, desde que Binder, autorização e versão do UserService estejam disponíveis e não exista binding ou retry pendente.
- Desativar a captura não inicia um UserService ausente.
- Adicionados testes para elegibilidade da retomada explícita e para prevenção de vínculos duplicados.

## Versão

- `versionName`: `0.14.46`
- `versionCode`: `62`
- Artefato GitHub Actions: `keymapper-0.14.46-debug`

## Limites conhecidos

- O teste de tipos da ponte foi executado com stubs de contrato Android/Shizuku; não substitui o compilador Android/Gradle e a dependência Shizuku real.
- O Gradle Wrapper 8.8 pode continuar bloqueado caso `services.gradle.org` não esteja acessível.
- O ciclo de vida precisa de teste físico: parar/reiniciar Shizuku, revogar/conceder autorização, matar o UserService e retomar a captura em Android 13/One UI/DeX.
- Não foi criado APK nesta etapa.
