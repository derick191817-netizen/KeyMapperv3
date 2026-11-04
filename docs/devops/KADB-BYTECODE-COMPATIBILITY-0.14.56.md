# Compatibilidade de bytecode Kadb — 0.14.56

## Erro de origem

O GitHub Actions reportou:

`Failed to transform kadb.aar (com.flyfishxu:kadb-android:2.1.5)`

`Unsupported class file major version 69`

O major 69 representa class files Java 25. O Jetifier do AGP 8.5.2 não conseguiu ler uma classe dentro do AAR antes de executar Lint/compilação.

## Seleção

A aplicação Android agora depende diretamente de:

- `com.flyfishxu:kadb-android:2.1.4`
- `com.flyfishxu:kadb-mdns-android:2.1.3`

A versão 2.1.4 é publicada como AAR para Android/JVM 21; 2.1.5 é a publicação recente que trouxe bytecode Java 25. O projeto não usa classes Java 25 e não deve aumentar JDK/Gradle apenas para acomodar esse artefato.

## Por que não mudar Gradle/JDK

A mensagem sobre Gradle 8.14.4 trata da compatibilidade futura com Kotlin 2.5.0. A build atual usa Kotlin 2.4.21, cuja matriz oficial inclui Gradle 8.8 e AGP 8.5.2. Alterar o wrapper ou JDK não corrige bytecode de uma biblioteca incompatível com Jetifier; a dependência Android é que deve ser fixada numa publicação legível.

## Regressão automática

O CI executa `dependencyInsight` para `com.flyfishxu:kadb-android` na configuração `debugCompileClasspath`, exige 2.1.4 e falha caso apareça 2.1.5.


## Tratamento de Bouncy Castle no Jetifier e atualização de segurança

Kadb 2.1.4 declara Bouncy Castle 1.84 (`bcprov-jdk18on` e `bcpkix-jdk18on`). A versão 1.84 antecede as correções publicadas na versão 1.85 para vulnerabilidades como CVE-2026-8763 e CVE-2026-13506. Para não trazer essas versões vulneráveis, o KeyMapper fixa `bcprov`, `bcpkix` e `bcutil` em 1.86, alinhando a família criptográfica.

Os artefatos JDK 18-on do Bouncy Castle são JARs multi-release. Foi adicionada a propriedade `android.jetifier.ignorelist=bc.*-jdk18on`: o Jetifier não transforma esses JARs, mas eles continuam no classpath e no empacotamento normal. A exceção é restrita aos artefatos Bouncy Castle e não desativa Jetifier para o restante do projeto. Essa abordagem corresponde à orientação documentada para `Unsupported class file major version 69` nesses JARs.

O CI verifica com `dependencyInsight` que Kadb Android 2.1.4 e os três módulos Bouncy Castle 1.86 são as versões realmente selecionadas, rejeitando Kadb 2.1.5 e BC 1.84.

Referências: documentação de known issues da Jumio sobre Bouncy Castle multi-release/Jetifier; aviso oficial de release Bouncy Castle 1.85 com CVEs corrigidos; implementação do Jetifier no Android Gradle Plugin, que aceita regex separadas por vírgulas.
