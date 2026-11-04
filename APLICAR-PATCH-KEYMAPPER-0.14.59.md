# Como aplicar o patch KeyMapper 0.14.59

Este patch é cumulativo: parte do pacote fonte 0.14.58 e inclui as correções Kadb/Jetifier, Studio/HUD, sessão Shizuku e detecção de teclado HID compacto. A base remota consultada ainda estava em 0.14.55; confira o commit do seu repositório antes de substituir arquivos.

1. Faça uma cópia/commit do estado atual do repositório.
2. Extraia o conteúdo do ZIP diretamente na raiz do projeto, mantendo os caminhos `app/`, `tools/`, `.github/` e arquivos de documentação.
3. Confirme `app/build.gradle.kts`: `versionName = "0.14.59"` e `versionCode = 75`.
4. Confirme que o workflow publica `keymapper-0.14.59-debug`.
5. Execute primeiro os testes host (`tools/native-tests/run.sh` e demais verificadores) e depois o workflow completo.
6. Só distribua o APK depois de `lintDebug`, `testDebugUnitTest` e `assembleDebug` passarem.

O patch não contém APK. Não substitua um branch mais novo pela versão 0.14.58/0.14.59 sem comparar os commits; aplique as mudanças sobre a versão realmente atual.
