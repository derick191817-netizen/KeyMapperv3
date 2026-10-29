# KeyMapper v7.8 — relatório da atualização e auditoria

Data: 29/10/2026  
Base usada: arquivo `KeyMapper-v7.6-atualizado-workflow.zip` disponível nesta sessão. Não houve acesso confirmado ao repositório remoto do usuário.

## A. Resumo executivo

**Classificação: pronto para tentativa no GitHub Actions, com riscos conhecidos.** A atualização v7.7 acrescenta uma suite de testes unitários para o motor matemático de sensibilidade e executa essa suite no workflow antes de publicar o APK. O YAML e o Manifest foram parseados localmente. O arquivo `SensitivityEngine.kt` também foi compilado com `kotlinc` e cinco verificações de comportamento, equivalentes aos cenários cobertos pelos testes adicionados, passaram.

**A compilação Android completa ainda não está confirmada.** Este ambiente não possui Gradle nem Android SDK e não consegue resolver a distribuição Gradle pela rede. Portanto, não foram executados `:app:compileDebugKotlin`, `:app:testDebugUnitTest` pelo Gradle, `:app:assembleDebug`, `aapt` nem testes em um dispositivo Android. Não há APK nesta entrega.

O Gradle Wrapper oficial ainda está ausente. Para CI, o workflow instala explicitamente Gradle 8.8 por meio de `gradle/actions/setup-gradle`; isso permite uma tentativa no runner, mas não oferece o fluxo reprodutível `./gradlew` localmente.

## B. Alterações da v7.7

1. Adicionada a dependência de testes `junit:junit:4.13.2`.
2. Criado `app/src/test/java/com/example/keymapper/domain/SensitivityEngineTest.kt`, com cinco testes para curva linear, escalas X/Y independentes, acúmulo fracionário, sensibilidade inválida e limpeza do estado por `reset()`.
3. O workflow agora executa `:app:testDebugUnitTest` antes de gerar o APK. Se a suite falhar, o job é interrompido e o artefato não é publicado como aprovado.
4. Mantidas as verificações do Android SDK 34, `aapt`, pacote do APK, integridade ZIP e checksum SHA-256.
5. Atualizado este relatório para registrar a diferença entre testes locais isolados e build Android completo.

Nenhuma tela, classe de funcionalidade, recurso ou integração com Shizuku foi removida nesta atualização; foram adicionados testes, dependência de teste e uma etapa de CI.

## C. Matriz de compatibilidade declarada

| Componente | Configuração do projeto/workflow | Avaliação documental |
|---|---|---|
| Java | JDK 17 no runner | Compatível com AGP 8.5 |
| Gradle | 8.8 instalado pela action | Atende ao mínimo do AGP 8.5 e ao limite totalmente suportado pelo Kotlin 2.0.20 |
| Android Gradle Plugin | 8.5.2 | Combina com Gradle 8.8 e JDK 17 |
| Kotlin Gradle Plugin | 2.0.20 | Matriz oficial indica suporte a Gradle até 8.8 e AGP até 8.5 |
| Compose Compiler | Plugin `org.jetbrains.kotlin.plugin.compose` 2.0.20 | Versão alinhada com Kotlin 2.0.20 |
| Android SDK | `compileSdk = 34`, `targetSdk = 34`, `minSdk = 26` | Workflow solicita plataforma Android 34 |
| Build Tools | 34.0.0 | Workflow solicita versão e verifica `aapt` |
| Shizuku | API/Provider 13.1.5 | Declarados nas dependências; integração não foi exercitada em dispositivo |
| Testes | JUnit 4.13.2 | Configuração adicionada; execução por Gradle aguarda CI |

Esta matriz é uma verificação de compatibilidade documental; não substitui resolução de dependências ou compilação real.

## D. Evidências e resultados

| Verificação | Estado | Evidência/limite |
|---|---|---|
| Estrutura do ZIP | Aprovada | Arquivos de build no nível raiz, sem pasta-projeto aninhada |
| Integridade ZIP da v7.6 usada como base | Aprovada | `ZipFile.testzip()` retornou `None` |
| YAML do workflow v7.7 | Aprovado estaticamente | Parser YAML carregou a estrutura; workflow não foi executado pelo GitHub |
| Manifest XML | Aprovado estaticamente | XML parseado com sucesso |
| Motor `SensitivityEngine.kt` | Compilado isoladamente | Compilado com `kotlinc` local |
| Cinco cenários matemáticos de sensibilidade | Aprovados em smoke test Kotlin | Execução local no motor puro Kotlin: linear, X/Y, fracionários, sensibilidade inválida, reset |
| Suite JUnit adicionada | Não executada pelo Gradle | Gradle e resolução de dependências ausentes |
| `:app:compileDebugKotlin` | Não executado | Requer ambiente Gradle/Android e dependências |
| `:app:assembleDebug` | Não executado | Nenhum APK foi produzido nesta sessão |
| APK e `aapt dump badging` | Não executados | Só serão verificados no runner, após o build |
| Testes reais em Android / Shizuku | Não executados | Requer dispositivo e autorização de Shizuku |
| Gradle Wrapper | Ausente | `gradlew`, `gradlew.bat`, JAR e properties não estão versionados |

## E. Símbolos Android verificados

- `InputDeviceManager.kt` usa `InputDevice.supportsSource(int)`. A referência oficial atual documenta `supportsSource(int)` como método público e não lista `hasSource()` na API `InputDevice`.
- Não foi encontrada ocorrência de `Configuration.DENSITY_DEFAULT` nos arquivos Kotlin/Gradle da base v7.6.
- A configuração usa o plugin Compose correspondente ao Kotlin 2.0.20; não declara separadamente a versão antiga do compiler extension.
- As telas, serviços, motor de mapeamento, classes de entrada, arquivos de perfil e injeção Shizuku continuaram presentes na cópia v7.7; nenhum desses fluxos foi funcionalmente testado em Android durante esta tarefa.

## F. Riscos restantes

| Risco | Gravidade | Evidência | Próximo passo |
|---|---|---|---|
| Build completo não verificado | Alta | Não há execução Gradle/Android SDK nesta sessão | Executar o workflow e corrigir a primeira falha real do log |
| Gradle Wrapper ausente | Média | Não há `gradlew` nem arquivos em `gradle/wrapper` | Gerar Wrapper oficial usando Gradle 8.8 em ambiente confiável e versionar os arquivos oficiais |
| Dependências remotas podem falhar | Média | Dependências Compose, AndroidX e Shizuku precisam ser resolvidas no CI | Confirmar resolução Maven no GitHub Actions |
| Testes JUnit ainda não rodaram por Gradle | Média | Só os cenários equivalentes em harness Kotlin foram executados localmente | Conferir `:app:testDebugUnitTest` no CI |
| Permissões/runtime de Shizuku | Alta para uso funcional; não é bloqueio estático do build | Manifest e classes existem, mas o consentimento não pode ser inferido do código | Instalar APK e testar os fluxos de permissão e injeção em dispositivo compatível |
| Comparação com remoto não realizada | Média | Só foi possível usar o ZIP v7.6 disponível localmente | Comparar com o arquivo/repositório remoto atual antes de publicar, se divergir |

## G. Próximos passos

1. Enviar o ZIP v7.7 para a raiz do repositório GitHub, preservando a estrutura sem pasta superior extra.
2. Abrir **Actions → Build APK → Run workflow** (ou enviar um `push` que dispare o workflow).
3. Conferir primeiro as etapas `Compilar Kotlin Debug`, `Executar testes unitários Debug` e `Compilar APK Debug`.
4. Só considerar o build aprovado se a geração do APK, o `aapt dump badging`, a integridade do arquivo e o upload terminarem sem erro.
5. Para máxima reprodutibilidade, gerar e incluir o Gradle Wrapper oficial. Não foi criado um Wrapper manual/fictício nesta atualização.

## H. Fontes técnicas

- Android Gradle Plugin e requisitos: https://developer.android.com/build/releases/about-agp
- JDK em builds Android: https://developer.android.com/build/jdks
- Build Android pela linha de comando: https://developer.android.com/build/building-cmdline
- Android SDK Manager: https://developer.android.com/tools/sdkmanager
- Compatibilidade Kotlin/Gradle/AGP: https://kotlinlang.org/docs/gradle-configure-project.html
- Gradle Wrapper: https://docs.gradle.org/current/userguide/gradle_wrapper.html
- Gradle Actions: https://github.com/gradle/actions
- Android SDK setup action: https://github.com/android-actions/setup-android
- setup-java: https://github.com/actions/setup-java
- upload-artifact: https://github.com/actions/upload-artifact
- Compose setup: https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
- API `InputDevice`: https://developer.android.com/reference/android/view/InputDevice (consultada em 29/10/2026)
- Shizuku-API: https://github.com/RikkaApps/Shizuku-API
- Exemplo público Android/Compose/CI: https://github.com/android/nowinandroid/blob/main/.github/workflows/Build.yaml
- Exemplo público de build APK/artefatos: https://github.com/termux/termux-app/blob/master/.github/workflows/debug_build.yml

## Conclusão

A v7.7 melhora a qualidade de CI adicionando testes unitários e verificações de regressão executáveis. Os testes equivalentes do motor matemático foram executados localmente com Kotlin e passaram, mas a suite JUnit ainda depende do runner. A classificação final é **pronto para tentativa com riscos conhecidos**, não “build Android aprovado”, até o GitHub Actions gerar e validar o APK real.


## Atualização v7.8 — bootstrap do Gradle Wrapper

A versão v7.8 altera o workflow para resolver o problema do Wrapper ausente sem inventar um `gradle-wrapper.jar`: instala Gradle 8.8 pela action oficial, executa `gradle wrapper --gradle-version 8.8 --distribution-type bin`, verifica a presença de `gradlew`, `gradlew.bat`, `gradle-wrapper.jar` e `gradle-wrapper.properties`, confere que a distribuição declarada é Gradle 8.8 e executa `./gradlew --version`. As tarefas `:app:tasks`, `:app:compileDebugKotlin`, `:app:testDebugUnitTest` e `:app:assembleDebug` passam a usar `./gradlew`. O workflow publica também um artefato separado `keymapper-gradle-wrapper` com os arquivos oficiais gerados.

**Limitação importante:** os arquivos do Wrapper são gerados no runner do GitHub Actions, não incorporados como binários neste ZIP, porque o ambiente de criação não tem acesso à rede para obter e validar a distribuição/JAR oficial. O bootstrap do Wrapper e a compilação ainda precisam ser confirmados executando o workflow real. Nenhuma compilação Android foi alegada nesta sessão.
