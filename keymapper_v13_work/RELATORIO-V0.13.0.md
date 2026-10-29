# Relatório de entrega — KeyMapper 0.13.0

## Classificação

**Validação parcial; ainda não compilado como aplicativo Android.** Não foi gerado APK nesta sessão e o projeto não foi validado em dispositivo real.

## O que foi corrigido

1. **Integridade dos IDs dos controles.** A nova política `MappingDataPolicy` gera IDs únicos ao ler e persistir perfis, preserva a primeira ocorrência de um ID, reatribui duplicatas sem tomar IDs originais que aparecem depois e limita o perfil a 128 controles. Também remove macros com destino inexistente e ligações de mira inválidas.
2. **Captura de botões do mouse.** O painel contextual captura os botões padrão esquerdo, direito e central durante a associação e associa-os aos códigos internos existentes. Botões laterais/extra não são fingidos como suportados; o painel informa a limitação. A captura de atalhos globais continua sendo por teclado.
3. **Rótulo correto do botão central.** O código -3 passa a aparecer como “Mouse do Meio” em vez de “Scroll”.
4. **Validação de configuração.** HUD style, tema, refresh rate, modo de desempenho e atalhos globais são limitados; atalhos duplicados ou inválidos são normalizados ao carregar JSON.
5. **Seletor de atalhos.** O seletor inclui o valor atual, mesmo quando ele é NUM3 ou outra tecla legada fora da lista F1–F12, e ainda oferece opções F1–F12.
6. **Atalhos globais versus todos os perfis.** A tela de configurações compara as teclas globais de editar/pausar com as teclas associadas no perfil global e em cada perfil de aplicativo carregado. O conflito é exibido com nome do controle, e Salvar fica desabilitado até ser resolvido; a associação não é alterada silenciosamente.
7. **Persistência resistente ao fechamento do serviço.** Gravações pedidas pelo overlay agora usam `KeyMapperApp.persistenceScope`, que não é cancelado quando `MappingService.onDestroy()` cancela seu escopo operacional. As gravações preservam snapshots e não engolem cancelamento de corrotina.
8. **CI.** O workflow já executava build, testes e publicação do APK. Atualizei `actions/upload-artifact` para v7.0.2. A action é publicada no repositório oficial: https://github.com/actions/upload-artifact/releases.

## Evidências de validação

- **15/15 assertions** passaram ao compilar com `kotlinc` e executar um harness independente das políticas `MappingDataPolicy`, `InputBindingPolicy` e `MappingEditorPolicy`, usando stubs simples para os modelos de controle.
- **11/11 checagens estáticas** passaram para versionamento, integração das políticas, prevenção de salvamento conflitante, captura de mouse e arquivos do Wrapper.
- Verificação da integridade do JAR do Gradle Wrapper: passou (`unzip -t`). O checksum local do `gradle-wrapper.jar` coincide com o valor verificado pelo workflow.
- Testes JUnit acrescentados: 7 métodos novos nesta versão (4 para normalização de dados; 2 para associação de mouse; 1 para conflito de atalhos globais), mas **não executados** via Gradle.
- Build solicitado: `./gradlew :app:testDebugUnitTest :app:assembleDebug --stacktrace`.
- Resultado: falhou antes da configuração/compilação porque não foi possível resolver `services.gradle.org` (`java.net.UnknownHostException`). Não é possível concluir a partir disso se o código Android compila.

## Estado por requisito

- **Implementado no código:** normalização de perfis; rótulos; captura de mouse no painel; escopo de persistência ligado à aplicação; seletores de atalho com valor válido; testes adicionados; CI de build/test/artifact existente.
- **Testado com harness Kotlin:** políticas puras de ID/referências, tradução de botões de mouse e conflito de atalhos globais (15 assertions).
- **Não testado via JUnit Gradle:** testes novos e existentes.
- **Não validado em dispositivo:** captura de mouse do painel, edição sobre jogo, persistência ao encerrar o serviço, injeção de eventos e liberação do estado em transições.
- **APK:** não produzido.

## Como compilar em ambiente com acesso a dependências

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug --stacktrace
```

O workflow `.github/workflows/build.yml` realiza essas tarefas e publica `app/build/outputs/apk/debug/*.apk` como artefato se o build passar.

## Proveniência

Atualização local do projeto disponível em `/mnt/data/keymapper_v13_work`, derivada do pacote local v0.12.0 nesta sessão. Nenhuma alteração foi enviada para repositório remoto.
