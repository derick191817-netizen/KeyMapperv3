# Aplicação do patch — KeyMapper 0.14.60

Este patch é cumulativo em relação à base remota 0.14.55 e reúne as correções 0.14.56–0.14.60.

1. Faça backup/commit do estado atual do repositório.
2. Confira que está aplicando sobre `main` na base 0.14.55. Se a branch já tiver commits posteriores, não sobrescreva: compare os arquivos antes de extrair.
3. Extraia o conteúdo de `KeyMapper-v0.14.60-PATCH-OVERLAY.zip` diretamente na raiz do repositório, preservando os caminhos `app/`, `tools/`, `.github/`, `docs/` e os arquivos Markdown.
4. Verifique `app/build.gradle.kts`: `versionName = "0.14.60"` e `versionCode = 76`.
5. Verifique o workflow `.github/workflows/build.yml`: o artefato deve ser `keymapper-0.14.60-debug`.
6. Execute as verificações host/native descritas em `TEST-RESULTS-KEYMAPPER-V0.14.60.txt`.
7. Execute, no GitHub Actions, `:app:lintDebug`, `:app:testDebugUnitTest` e `:app:assembleDebug`.
8. Instale o APK gerado num aparelho de teste e confira mouse, teclado compacto, teclas de mídia, conexão/desconexão, HUD e saída de toque.

O patch não contém APK. Não declare release pronta até o GitHub Actions concluir e o teste físico passar.
