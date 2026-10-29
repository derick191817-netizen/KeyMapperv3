# Compilar o KeyMapper v7.8

## Pelo GitHub Actions

1. Extraia o conteúdo do ZIP na raiz do repositório, sem colocar tudo dentro de uma subpasta adicional.
2. Faça commit e push, ou abra a aba **Actions** e execute **Build APK → Run workflow**.
3. Espere todas as etapas terminarem. O workflow interrompe o processo se a compilação Kotlin, os testes, a geração do APK ou a verificação do pacote falhar.
4. Em uma execução aprovada, abra os artefatos da execução e baixe `keymapper-debug`.

## Arquivo esperado

`app/build/outputs/apk/debug/app-debug.apk`

O workflow usa o `aapt` para verificar o identificador `com.example.keymapper`, testa a integridade ZIP do APK e calcula um checksum SHA-256.

## Limitações conhecidas

O ZIP inicial não inclui binários do Wrapper. Para evitar inventar ou empacotar um JAR não verificado, o workflow instala Gradle 8.8 pela action oficial, gera o Wrapper oficial com a tarefa `wrapper`, valida os quatro arquivos e usa `./gradlew` nas etapas seguintes. Também publica o arquivo `keymapper-gradle-wrapper`, contendo os arquivos gerados para que possam ser adicionados ao repositório após a execução. O APK não está pré-gerado; só existe build aprovado depois de o workflow terminar com sucesso.
