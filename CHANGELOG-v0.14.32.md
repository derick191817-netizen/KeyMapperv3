# Changelog — KeyMapper 0.14.32

## Captura evdev e recuperação do serviço privilegiado

- Substituído `poll` + pipe por `epoll` + `eventfd`, aproximando a multiplexação do padrão observado no KeyMouse Pro.
- Adicionado watcher `inotify` opcional para mudanças em `/dev/input`; mantém conferência periódica a cada 5 segundos com watcher e reduz para 1 segundo quando `inotify` não está disponível.
- O loop de captura acorda com hot-plug, mudança de estado e solicitação de encerramento, sem depender apenas de uma varredura lenta de dispositivos.
- O worker publica seu estado real; se terminar por erro, o processo Shizuku consegue detectar que o leitor não está mais ativo e reiniciá-lo no mesmo UserService.
- Limpeza de `eventfd`, `epoll`, watcher e referências JNI antigas antes de reiniciar um worker encerrado.
- Um erro definitivo de leitura/EOF ou falha de registro do descritor no epoll fecha o fd, envia evento de perda de sincronização e agenda nova descoberta em vez de manter dispositivo fantasma.
- O bridge serializa chamadas de inicialização Binder, preserva a última solicitação de pausa/retomada e limpa o vínculo após timeout ou Binder nulo para permitir tentativa nova.
- Scroll de alta resolução (`REL_WHEEL_HI_RES` / `REL_HWHEEL_HI_RES`) é encaminhado como fração de detente; o evento legado duplicado do mesmo eixo é descartado quando a capacidade hi-res foi anunciada.
- Heartbeat nativo periódico inclui eventos lidos/enfileirados, `SYN_DROPPED`, erros de leitura e dispositivos removidos, facilitando identificar em qual estágio o fluxo de entrada parou.
- Se `EVIOCGRAB` não puder ser desfeito com ioctl, o descritor é fechado imediatamente para que o kernel libere o periférico; o scanner poderá reabri-lo depois.
- O serviço Kotlin consulta `nativeIsCaptureRunning()` antes de assumir que a captura segue ativa.
- O bridge reutiliza o Binder já conectado quando uma inicialização falha; evita criar um segundo vínculo para o mesmo UserService e limpa vínculos mortos/vazios.
- Mantido o `MappingEngine`, as políticas de transformação de movimento e a lógica de mapeamento sem alteração nesta versão.
- Versão `0.14.32`, `versionCode=48`; artefato CI atualizado.

## Verificações

- Compilação do translation unit JNI/C++ com Clang 17 e `-Wall -Wextra -Werror=return-type`: aprovada.
- Link de biblioteca host para smoke test JNI: aprovado.
- Smoke test JNI de iniciar, pausar, retomar e parar em três ciclos, com restart final: aprovado.
- Build Android/Gradle, Android Lint, JUnit no Gradle e teste físico no Samsung/DeX: não executados neste ambiente. O Wrapper Gradle 8.8 tentou baixar `services.gradle.org`, mas a resolução DNS falhou; Android SDK não está configurado.

## Limites

- A captura evdev depende de acesso real a `/dev/input` e de `EVIOCGRAB` permitido pela ROM/SELinux.
- A saída continua via InputManager/Shizuku; `/dev/uinput` ainda não foi implementado.
- O smoke test local não tem periféricos Android reais e não valida o comportamento no jogo/aplicativo de destino.
