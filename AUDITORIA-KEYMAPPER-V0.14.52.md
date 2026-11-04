# Auditoria KeyMapper 0.14.52 — Toolchain e CI

## Objetivo

Adicionar uma barreira preventiva de DevOps para impedir atualizações parciais ou incompatíveis do ambiente de build Android.

## Resultado da investigação

A combinação observada em 0.14.51 não era incompatível. A matriz oficial de Kotlin 2.4.20–2.4.21 suporta Gradle 7.6.3–9.7.0 e AGP 8.5.2–9.3.1. A documentação Android indica que AGP 8.5 requer Gradle 8.7 ou superior. O projeto utiliza Gradle 8.8, portanto satisfaz os dois requisitos.

## Alterações

1. Criado `tools/build-tests/check-toolchain-compatibility.py` e seu teste negativo `tools/build-tests/test-toolchain-compatibility.py`.
2. O script valida AGP/KGP/Compose/Gradle, presença do checksum SHA-256 configurado no Wrapper, JDK e bytecode 17, compileSdk, NDK e CMake.
3. O script verifica o alinhamento entre `versionName`, nome do artefato e a versão do app.
4. A etapa foi incorporada ao `.github/workflows/build.yml` antes das tarefas de build.
5. Adicionado documento de compatibilidade com fontes oficiais.
6. `versionCode=68`, `versionName=0.14.52`.

## Validações executadas

- `tools/build-tests/check-toolchain-compatibility.py`: PASS.
- Teste negativo do verificador (4 divergências simuladas): PASS.
- Testes nativos de recuperação de lote: PASS.
- Sintaxe C++ e contrato CMake/JNI/biblioteca: PASS.
- Coordenadas Kadb e fronteira arquitetural mDNS: PASS.
- Alinhamento do workflow e versionamento 0.14.52: PASS.

## Limites

A tentativa local do wrapper foi bloqueada antes de iniciar Gradle por `UnknownHostException: services.gradle.org`. A verificação é estática; ela não substitui `:app:lintDebug`, `:app:testDebugUnitTest` ou `:app:assembleDebug`. A execução dessas tarefas depende de Gradle e repositórios Maven disponíveis. Nenhum APK é declarado como gerado nesta etapa sem executar a compilação completa.
