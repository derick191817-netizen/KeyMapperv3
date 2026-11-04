# Auditoria técnica — KeyMapper 0.14.65

## Escopo desta correção

A revisão foi aplicada à árvore de código 0.14.64. Foi encontrada uma falha na normalização do JSON persistido: `buttons` e `games` presentes com valor JSON `null` não geravam backup porque a condição anterior excluía explicitamente `JSONObject.NULL`. A leitura posterior adotava os padrões e um salvamento subsequente poderia substituir a coleção inválida sem preservar uma cópia do JSON original.

## Correção aplicada

- Política `SettingsRecoveryPolicy.shouldBackupInvalidArray(fieldPresent, valueIsArray)` adicionada.
- O campo ausente permanece aceito para compatibilidade com versões antigas.
- Campo presente com `null`, objeto, string, número ou qualquer valor que não seja `JSONArray` é considerado inválido.
- `SettingsRepository` salva o texto JSON original (limitado a 2.000.000 caracteres) em `settings_json_recovery_backup` antes de remover o campo inválido e normalizar a configuração.
- Testes unitários ampliados para arrays ausentes, válidos e inválidos.

## Verificações executadas

Nesta entrega devem ser executados os scripts nativos, de dependências, arquitetura e toolchain. Esses testes não substituem `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug` nem a validação em hardware.

## Pendências conhecidas, sem falsa declaração de suporte

1. Wireless ADB faz pareamento/conexão e leitura de diagnóstico, não inicia o motor de captura/injeção.
2. Root ainda não está implementado como provedor independente.
3. `MappingService.setup()` ainda depende de Shizuku (`ShizukuInjector` / `ShizukuInputCaptureBridge`).
4. Não há confirmação de build APK nesta entrega: o ambiente atual não tem distribuição Gradle 8.8 em cache nem acesso DNS a `services.gradle.org`, e não há Android SDK instalado.
5. Testes físicos no Note 20 Ultra, Android 13, One UI e Samsung DeX permanecem pendentes.

## Versão

- `versionName`: 0.14.65
- `versionCode`: 81
