# Auditoria técnica — KeyMapper 0.14.56

## Escopo

Correção da falha de resolução/transformação Android encontrada no GitHub Actions em `:app:compileDebugAidl`, executada como dependência do Lint.

## Diagnóstico baseado no log do runner

O log mostra:

- Configuração afetada: `:app:debugCompileClasspath`.
- Artefato que falha: `com.flyfishxu:kadb-android:2.1.5` (`kadb.aar`).
- Transformação afetada: `JetifyTransform` / `AndroidXRefScanner`.
- Exceção: `java.lang.IllegalArgumentException: Unsupported class file major version 69`.

Class file major `69` corresponde a Java 25. O projeto usa AGP 8.5.2 e Jetifier nessa etapa; o problema é o bytecode da dependência, não o código-fonte do KeyMapper.

## Correção aplicada

| Antes | Depois |
|---|---|
| `com.flyfishxu:kadb:2.1.5` (selecionava variante `kadb-android:2.1.5`) | `com.flyfishxu:kadb-android:2.1.4` diretamente |
| `com.flyfishxu:kadb-mdns-android:2.1.3` | Mantido |
| JDK 17 | Mantido |
| AGP 8.5.2 | Mantido |
| Kotlin/Compose 2.4.21 | Mantido |
| Gradle Wrapper 8.8 | Mantido |

A versão 2.1.4 do AAR Android é publicada para Android/JVM 21; a versão 2.1.5 está associada a bytecode Java 25. A coordenada foi fixada no AAR Android específico, evitando a variante multiplataforma mais recente.

## Proteção contra regressão

- `tools/dependency-tests/check-kadb-coordinates.sh` valida as coordenadas fonte e rejeita a coordenada antiga.
- `tools/dependency-tests/verify-kadb-selected-version.sh` executa `dependencyInsight` em `debugCompileClasspath`, exige que o Gradle selecione `kadb-android:2.1.4` e rejeita `2.1.5`.
- O workflow executa essa resolução real antes de `lintDebug`, para falhar cedo e exibir a versão selecionada no log.

## Aviso Gradle/Kotlin

O runner também mostra um aviso de depreciação indicando que Kotlin 2.5.0 elevará o mínimo de Gradle para 8.14.4. O projeto ainda usa Kotlin 2.4.21; a matriz oficial dessa versão inclui Gradle 8.8. Esse aviso não causou a falha mostrada e não justifica atualizar o wrapper isoladamente, pois isso alteraria outra dimensão do toolchain sem resolver o AAR Java 25.

## Validação desta entrega

Validado localmente:

- Coordenadas Kadb estáticas e versão da release.
- Verificador de toolchain e seus quatro testes negativos.
- YAML do workflow parseável e etapa de `dependencyInsight` posicionada antes do Lint.
- Testes host C++/JNI, sintaxe C++17 e contrato CMake/JNI.
- Fronteiras arquiteturais Wireless ADB, sessão Shizuku, HUD e Overlay.
- Integridade do ZIP e checksum SHA-256.

Ainda depende do GitHub Actions com acesso ao Maven Central:

- Resolução real da coordenada Kadb 2.1.4 pelo Gradle.
- `:app:lintDebug`, `:app:testDebugUnitTest` e `:app:assembleDebug`.

Nenhum APK é declarado como compilado nesta sessão. O teste físico no Android 13/One UI/DeX também não foi executado.


### Proteção complementar do Jetifier e dependências criptográficas

Kadb 2.1.4 declara Bouncy Castle 1.84. Essa versão antecede correções de segurança publicadas em 1.85 para CVE-2026-8763 e CVE-2026-13506. O projeto agora fixa `bcprov-jdk18on`, `bcpkix-jdk18on` e `bcutil-jdk18on` em 1.86.

Como os JARs `*-jdk18on` são multi-release e podem conter bytecode Java 25 em diretórios de versões, foi configurado `android.jetifier.ignorelist=bc.*-jdk18on`. A propriedade exclui esses artefatos apenas da transformação Jetifier; eles permanecem no classpath e no empacotamento.

O verificador de CI consulta `dependencyInsight` para Kadb e os três módulos Bouncy Castle. Testes host simulam resolução correta, Kadb 2.1.5, Bouncy Castle 1.84 e dependências ausentes.
