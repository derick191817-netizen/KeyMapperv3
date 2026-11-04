# Como aplicar o patch KeyMapper 0.14.58

## Base necessária

O `PATCH-OVERLAY` desta entrega contém, em conjunto, as alterações desde a base remota observada `0.14.55` até a versão local `0.14.58`. Ele já incorpora as correções 0.14.56 e 0.14.57; não é necessário aplicar os patches antigos separadamente.

## Aplicação

1. Faça uma cópia/backup do repositório `KeyMapperv3`.
2. Extraia o conteúdo do ZIP diretamente na raiz do repositório — no mesmo nível de `settings.gradle.kts`, `gradlew`, `app/` e `.github/`.
3. Permita substituir os arquivos correspondentes.
4. Confira `app/build.gradle.kts`: deve conter `versionName = "0.14.58"` e `versionCode = 74`.
5. Confira se o workflow `.github/workflows/build.yml` contém as verificações host, `:app:lintDebug`, `:app:testDebugUnitTest` e `:app:assembleDebug`, e se o artefato se chama `keymapper-0.14.58-debug`.
6. Faça commit em uma branch própria e envie ao GitHub para executar o Actions.

## Importante

- Não extraia o ZIP dentro de uma pasta `KeyMapper-v0.14.58/`; os caminhos devem cair diretamente na raiz do repositório.
- Não misture com arquivos de uma versão posterior, caso o `main` já tenha avançado desde a auditoria.
- A atualização ainda não foi publicada no GitHub porque a integração de escrita recebeu HTTP 403. O ZIP é a entrega local aplicável.
- O ZIP não contém APK: só será possível gerar o APK depois que o CI concluir `lintDebug`, `testDebugUnitTest` e `assembleDebug`.
