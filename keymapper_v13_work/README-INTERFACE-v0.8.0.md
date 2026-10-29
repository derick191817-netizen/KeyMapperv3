# KeyMapper 0.8.0 — interface mobile responsiva

## Direção visual

- Identidade própria: fundo grafite azulado, superfícies em camadas, violeta como cor primária e ciano como acento secundário.
- Barra de navegação inferior persistente para Painel, Editor e Ajustes.
- Ícone de inicialização vetorial próprio, sem logotipos de terceiros.
- Painel com estados de serviço, Shizuku e permissão de sobreposição lidos das APIs existentes; não são valores ilustrativos.
- Layout do painel em uma coluna em celulares e duas colunas em larguras amplas.

## Fluxos preservados

- O serviço continua sendo iniciado/interrompido pelo `MappingService`.
- A ativação e a solicitação de permissão continuam passando por `Activation` e pelo sistema Android.
- A lista de aplicativos continua sendo lida das configurações salvas. A gestão, a pesquisa e o filtro de aplicativos continuam na aba Apps das Configurações.
- O editor continua usando `HudButton`, `AppProfile` e `SettingsViewModel`; salvar alterações segue chamando `saveProfile`.
- O editor em telas estreitas usa um painel inferior de propriedades depois que o usuário escolhe editar ou adiciona um controle. Seleção e arrasto de marcadores continuam separados para não abrir o painel no meio de um gesto de arrasto.
- As configurações existentes, os perfis e os dados serializados não foram migrados nem apagados.
- O painel do Shizuku continua abrindo as configurações do desenvolvedor e o app Shizuku; o estado real é atualizado ao retornar ou tocar em Atualizar.

## Mudanças técnicas

- `MainActivity.kt`: navegação inferior compartilhada e nova paleta padrão.
- `HomeScreen.kt`: painel responsivo redesenhado; lista compacta de aplicativos; indicadores claros; ações de serviço/permissões; ativação via Wi-Fi em folha inferior.
- `MappingEditorScreen.kt`: ações de edição em duas linhas nas telas estreitas; sensibilidade X/Y/Geral empilhada em celular; propriedades em folha inferior no layout móvel.
- `AndroidManifest.xml` e `res/drawable/ic_keymapper_launcher.xml`: identidade vetorial própria.
- `app/build.gradle.kts`: versão 0.8.0 (versionCode 8).

## Validação

A estrutura e o código foram inspecionados estaticamente e o XML do ícone/Manifest pode ser validado localmente. O ambiente de edição não tem Gradle nem Android SDK instalados; portanto, a compilação final precisa ser confirmada pelo workflow GitHub Actions. Não considerar a atualização concluída em termos de runtime até testar navegação, editor, persistência, Shizuku e overlay em dispositivo.


## Gradle Wrapper

O projeto agora versiona `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` e `gradle/wrapper/gradle-wrapper.properties` para Gradle 8.8. A distribuição é validada por `distributionSha256Sum`, com checksum publicado pelo Gradle.
