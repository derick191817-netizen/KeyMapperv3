# KeyMapper 0.14.52

## DevOps e compatibilidade

- Adicionado `tools/build-tests/check-toolchain-compatibility.py` para validar a matriz Gradle/Kotlin/AGP antes de iniciar o build Android.
- Verificada a compatibilidade oficial da combinação AGP 8.5.2 + Kotlin/Compose 2.4.21 + Gradle 8.8; a configuração atual está dentro das faixas suportadas.
- O CI agora confere também JDK/JVM 17, compileSdk, NDK, CMake, presença do checksum SHA-256 configurado no wrapper e alinhamento do artefato com `versionName`.
- Atualizados `versionCode` para 68 e `versionName` para 0.14.52.
- Nenhuma alteração foi feita no motor de entrada, nos mapeamentos ou nas funções de jogo nesta versão.
