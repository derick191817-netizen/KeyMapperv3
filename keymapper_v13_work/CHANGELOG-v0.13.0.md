# KeyMapper 0.13.0 — integridade dos perfis, captura de mouse e persistência

## Correções

- Adicionada `MappingDataPolicy` e aplicada nas fronteiras de leitura e gravação de `AppSettings` e `AppProfile`. O carregamento corrige IDs repetidos sem descartar controles válidos, evita roubar IDs válidos que apareçam mais adiante e remove macros/referências de mira quebradas.
- Valores persistidos de `hudStyle`, `refreshHz`, `perfMode`, `theme`, `editKey` e `pauseKey` passam por validação de faixa. Os atalhos globais são normalizados para não ficarem iguais quando um JSON antigo estiver malformado.
- A captura de associação no painel agora recebe cliques de mouse esquerdo, direito e central durante o modo de captura, em vez de confiar somente em `KeyEvent`. Outros botões são informados como não suportados; o atalho global de pausa continua exigindo tecla física.
- `keyLabel(-3)` exibe “Mouse do Meio” porque esse código representa o botão central, não a roda de rolagem.
- A tela de atalhos inclui o valor atual nas opções quando ele não está na lista F1–F12; isso preserva e exibe corretamente o padrão legado NUM3.
- As gravações solicitadas pelo overlay agora usam escopo de persistência ligado ao processo da aplicação, em vez do escopo cancelado junto com o serviço. Snapshots do perfil/configuração são capturados antes da gravação e `CancellationException` não é engolida.
- Atualizada `actions/upload-artifact` para v7.0.2 no workflow de CI.
- Acrescentados testes para normalização de IDs/referências, mapeamento dos três botões padrão do mouse e detecção de conflito entre atalhos globais e controles de perfis.

## Limitações de validação

- `./gradlew :app:testDebugUnitTest :app:assembleDebug --stacktrace` não alcançou a fase de configuração: `UnknownHostException: services.gradle.org` ao baixar Gradle 8.8.
- O harness Kotlin isolado de `MappingDataPolicy`, `InputBindingPolicy` e `MappingEditorPolicy` passou em 15 assertions. Esse teste compila as políticas puras com stubs dos modelos e não substitui o build Android.
- 11/11 checagens estáticas do pacote e dos principais pontos de integração passaram; isso não equivale a uma compilação do app.
- Os novos testes JUnit foram adicionados ao projeto, mas não puderam ser executados via Gradle neste ambiente.
- Captura de botão em hardware real, persistência após encerramento do processo, overlay sobre jogo real e injeção Shizuku continuam pendentes de teste em aparelho.

- A configuração agora detecta atalhos globais de editar/pausar que colidem com teclas usadas por controles em qualquer perfil carregado; o salvamento fica bloqueado até o conflito ser resolvido.

- O salvamento de configurações agora detecta colisões entre atalhos globais e controles em perfis de aplicativos carregados, mostra o controle conflitante e bloqueia o salvamento até a correção.
