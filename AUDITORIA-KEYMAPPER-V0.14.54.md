# Auditoria técnica — KeyMapper 0.14.54

## Escopo executado

Base: código-fonte 0.14.53.

Objetivo desta etapa: melhorar um caminho funcional verificável do editor HUD sem alterar o motor de entrada nativo, as integrações Shizuku ou o formato de persistência.

## Auditoria do fluxo de criação e duplicação

| Camada | Implementação inspecionada | Estado antes | Alteração 0.14.54 |
|---|---|---|---|
| Interface | `overlay/HudView.kt` | controles padrão criados em `0.5, 0.5` | Controles padrão usam posição escolhida pela política; tipos especializados mantêm o caminho anterior |
| Configuração | `data/HudButton.kt`, `overlay/ConfigPanelView.kt` | Posição e tamanho existentes e persistidos | Mantidos sem alteração de schema |
| Persistência | `SettingsRepository`, `AppSettings` | `host.persist()` após criar/duplicar | Mantida |
| Regra de domínio | `domain/MappingEditorPolicy.kt` | Clamp/IDs/conflitos de teclas existentes | Nova `HudPlacementPolicy` cuida apenas da geometria de colocação |
| Overlay | `overlay/HudView.kt` e `OverlayManager.kt` | Raio e posição desenhados em coordenadas normalizadas da área do jogo | Usa mesmas dimensões e raio visual do renderer para evitar bordas fora da área |
| Motor de entrada | `mapping/MappingEngine.kt` | Não é envolvido na criação visual do controle | Não alterado |
| Saída Android | `TouchInjector`, `VirtualTouchOutput`, backend C++ | Não é envolvida na criação visual do controle | Não alterada |

## Arquivos alterados

- `app/src/main/java/com/example/keymapper/domain/HudPlacementPolicy.kt` — nova política determinística e pura.
- `app/src/main/java/com/example/keymapper/overlay/HudView.kt` — integração em criação e duplicação de controles.
- `app/src/test/java/com/example/keymapper/domain/HudPlacementPolicyTest.kt` — cinco testes unitários.
- `tools/architecture-tests/check-hud-placement-policy.sh` — validação da integração dos dois caminhos.
- `tools/build-tests/check-toolchain-compatibility.py` e `tools/build-tests/test-toolchain-compatibility.py` — metadados da nova versão.
- `tools/dependency-tests/check-kadb-coordinates.sh` — alinhamento de versionCode, versionName e artefato CI.
- `.github/workflows/build.yml` — inclui a nova barreira e publica artefato com nome 0.14.54.
- `README.md`, changelog, matriz de comparação e relatório de testes — documentação.

## Decisões técnicas

- A regra está em Kotlin puro porque é uma política matemática pequena, executada no editor e testável sem Android.
- C++/NDK não é adequado para esta alteração: o posicionamento ocorre na camada visual e não participa de captura/eventos.
- Não houve mudança em contratos AIDL, Shizuku, JNI, uinput, InputManager ou formatos JSON existentes.
- A criação/duplicação de controles padrão mantém o centro como preferência quando não há ocupação. Quando há ocupação, a política avalia uma grade determinística e escolhe a posição com maior afastamento mínimo.
- Tipos especializados mantêm o posicionamento anterior; esta entrega não modifica nem amplia recursos especializados.

## Validação executada

- Cinco cenários Kotlin puros compilados/executados por `kotlinc-jvm 1.9.0` com Java 21.
- Testes host nativos de recuperação evdev/JNI.
- Sintaxe C++17 dos arquivos nativos.
- Contrato CMake/JNI/biblioteca nativa.
- Coordenadas Kadb e repositórios.
- Fronteira da tela ADB, isolamento de sessão Shizuku e novo contrato de posicionamento HUD.
- Verificador do toolchain e quatro cenários negativos.
- YAML do GitHub Actions parseado e versionamento confirmado.
- ZIP validado com `unzip -t`; SHA-256 registrado no arquivo correspondente.

## Pendências e limitações

- A execução `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` foi tentada. O Gradle Wrapper falhou antes de iniciar porque não conseguiu resolver `services.gradle.org` (`UnknownHostException`).
- Portanto, o build completo, Android Lint e testes JUnit executados pelo Gradle não estão confirmados nesta sessão.
- Nenhum APK foi gerado nesta etapa.
- Não houve teste físico no Samsung Galaxy Note 20 Ultra, Android 13, One UI ou Samsung DeX.
- A matriz pública das referências e as lacunas confirmadas estão em `docs/COMPARACAO-FUNCIONAL-REFERENCIAS-0.14.54.md`.
