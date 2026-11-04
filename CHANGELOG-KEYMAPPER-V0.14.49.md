# KeyMapper 0.14.49

## Correção de resolução Kadb mDNS

- `kadb` permanece em 2.1.5.
- `kadb-mdns` foi fixado em 2.1.3, coordenada raiz confirmada no catálogo público do Maven Central.
- Removida a suposição não verificada de que `kadb-mdns` 2.1.4 estaria publicado.
- Atualizado o teste CI de coordenadas para aceitar apenas o par declarado e rejeitar versões mDNS não confirmadas.
- Mantida a migração Kotlin `compilerOptions` / JVM 17 de 0.14.47.

## Limitações de validação

- O ambiente desta execução não consegue resolver `repo.maven.apache.org` nem `services.gradle.org`; não foi possível executar resolução Gradle real, Lint Android ou `assembleDebug`.
- A coordenada `kadb-mdns:2.1.3` aparece no catálogo do Maven Central; a confirmação final de resolução/compilação deve vir do GitHub Actions.
