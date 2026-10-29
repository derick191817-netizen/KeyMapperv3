# Relatório de entrega — KeyMapper 0.13.1

## Resultado desta passagem

Foi corrigida uma falha de roteamento de botões do mouse: eventos desconhecidos não são mais tratados como botão central. A correção usa a política compartilhada `InputBindingPolicy`, que aceita apenas os códigos internos de mouse esquerdo, direito e central.

## Arquivos modificados nesta passagem

- `app/src/main/java/com/example/keymapper/mapping/MappingEngine.kt`
- `app/build.gradle.kts` (versionCode 15, versionName 0.13.1)
- `README-COMPILACAO.md`
- `CHANGELOG-v0.13.1.md`
- `AUDITORIA-TOTAL-V0.13.1.md`
- `RELATORIO-V0.13.1.md`

## Evidências

- **Implementado:** validação explícita de botões físicos suportados.
- **Teste isolado executado:** 9/9 verificações de `InputBindingPolicy` passaram com `kotlinc` local.
- **Checagens estáticas executadas:** 4/4 passaram (versão, presença da guarda de botões desconhecidos, busca de placeholders de implementação e parse de XML Android).
- **Integridade do Wrapper JAR:** `unzip -t` passou.
- **JUnit/Gradle:** não executados; o Wrapper tentou obter Gradle 8.8 e falhou antes da configuração do projeto por `UnknownHostException: services.gradle.org`, mesmo com `--offline`.
- **APK:** não gerado.
- **Dispositivo real/jogo/Shizuku:** não validado.

Os testes isolados não provam a compilação Android nem a integração do evento até o jogo. A auditoria global permanece parcial até ser possível compilar com dependências disponíveis e executar testes de integração em dispositivo.

## Proveniência

Alteração local em `/mnt/data/keymapper_v13_work`, baseada no projeto local disponível nesta sessão. Nenhuma alteração foi enviada a um repositório remoto.
