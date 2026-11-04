# Aplicar patch — KeyMapper 0.14.64

1. Faça backup do repositório atual antes de substituir arquivos.
2. A base esperada é o `main` remoto consultado na versão 0.14.55. Este patch é cumulativo: contém as mudanças já entregues de 0.14.56 até 0.14.63 e as correções desta 0.14.64.
3. Extraia `KeyMapper-v0.14.64-PATCH-OVERLAY.zip` diretamente na raiz do repositório, preservando as pastas `app/`, `.github/`, `tools/` e `docs/`. Não extraia dentro de uma pasta intermediária.
4. Confirme `app/build.gradle.kts`: `versionName = "0.14.64"` e `versionCode = 80`.
5. Confirme o artefato CI `keymapper-0.14.64-debug` no `.github/workflows/build.yml`.
6. Execute primeiro os scripts host da etapa “Validar políticas nativas no host”; depois `:app:lintDebug`, `:app:testDebugUnitTest` e `:app:assembleDebug`.
7. Só instale o APK quando `assembleDebug` terminar com sucesso e o artifact tiver sido publicado pelo GitHub Actions.

## Mudanças principais da 0.14.64

- Ownership de contato por origem: entrada física, Combo, toque transitório, tecla de mira, Fire ligado à mira e mira ligada ao Fire não encerram o contato umas das outras.
- A mira ativa é rastreada por origem; soltar a origem mais recente restaura outra mira ainda ativa.
- A opção “Atirar com a tecla de mira” só aciona controles `Kind.FIRE` associados ao `shootKey`; Swipe, Macro e controles comuns não recebem um toque incorreto.
- `profiles: null` ou outro tipo inválido gera cópia no backup antes da sanitização. A ausência de `profiles` continua válida para configurações antigas.
- Novos testes Kotlin e barreiras de contrato são executados pelo workflow.

## Limitação conhecida da entrega

Este pacote contém código-fonte e patch, não APK. Nesta sessão, o Gradle Wrapper 8.8 não conseguiu resolver `services.gradle.org` por DNS. Portanto, Lint, JUnit via Gradle e `assembleDebug` ainda precisam ser confirmados em um runner com rede. O teste físico Android 13/One UI/DeX também continua pendente.
