# KeyMapper 0.13.1

## Correção
- O motor não converte mais qualquer botão de mouse desconhecido em botão central. Somente os códigos explicitamente suportados (primário, secundário e terciário) são encaminhados ao mapeador. Isso evita que botões laterais/auxiliares disparem uma ação configurada para o botão central.

## Validação
- A política pura `InputBindingPolicy` já possui testes para os três botões suportados e para máscaras desconhecidas.
- Compilação Android e execução JUnit continuam pendentes neste ambiente porque o Gradle Wrapper não consegue baixar Gradle 8.8 (`UnknownHostException: services.gradle.org`).
- Não houve validação em dispositivo, com Shizuku ou com jogo real.
