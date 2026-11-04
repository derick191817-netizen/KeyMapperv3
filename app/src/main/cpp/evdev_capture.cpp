#include <jni.h>
#include <linux/input.h>
#include <dirent.h>
#include <fcntl.h>
#include <sys/epoll.h>
#include <sys/eventfd.h>
#include <sys/inotify.h>
#include <sys/ioctl.h>
#include <unistd.h>
#include <errno.h>
#include <stdint.h>
#include <time.h>
#include <chrono>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <algorithm>
#include <limits>
#include <atomic>
#include <mutex>
#include <string>
#include <thread>
#include <vector>
#include "evdev_batch_recovery.h"
#include "evdev_device_classification.h"

// Compatibilidade com headers UAPI antigos de alguns toolchains NDK.
#ifndef REL_WHEEL_HI_RES
#define REL_WHEEL_HI_RES 0x0b
#endif
#ifndef REL_HWHEEL_HI_RES
#define REL_HWHEEL_HI_RES 0x0c
#endif

namespace {
constexpr int DEVICE_MOUSE = 1;
constexpr int DEVICE_KEYBOARD = 2;
constexpr int RECORD_FIELDS = 6;
constexpr size_t MAX_BATCH_RECORDS = 96;

JavaVM* g_vm = nullptr;
std::mutex g_lifecycle;
std::atomic<bool> g_running{false};
std::atomic<bool> g_enabled{false};
std::thread g_worker;
int g_epoll_fd = -1;
int g_wakeup_fd = -1;
int g_inotify_fd = -1;
jobject g_service = nullptr;
jmethodID g_batch_method = nullptr;
jmethodID g_state_method = nullptr;

struct CaptureStats {
    uint64_t events_read = 0;
    uint64_t events_queued = 0;
    uint64_t sync_dropped = 0;
    uint64_t read_errors = 0;
    uint64_t devices_removed = 0;
    uint64_t batch_delivery_failures = 0;
};

struct Device {
    std::string path;
    std::string name;
    int fd = -1;
    int id = -1;
    bool mouse = false;
    bool keyboard = false;
    bool high_res_vertical_wheel = false;
    bool high_res_horizontal_wheel = false;
    bool grab_supported = false;
    bool grabbed = false;
    bool syncing_after_drop = false;
    bool epoll_registered = false;
    bool monotonic_clock = true;
    bool realtime_offset_valid = false;
    int64_t realtime_to_monotonic_ns = 0;
    int grab_error = 0;
};

bool bitSet(const unsigned long* bits, unsigned int bit) {
    constexpr unsigned int bitsPerWord = sizeof(unsigned long) * 8;
    return (bits[bit / bitsPerWord] & (1UL << (bit % bitsPerWord))) != 0;
}

template <size_t N>
bool getBits(int fd, unsigned int type, unsigned long (&bits)[N]) {
    memset(bits, 0, sizeof(bits));
    return ioctl(fd, EVIOCGBIT(type, sizeof(bits)), bits) >= 0;
}

int eventNumber(const std::string& path) {
    const auto slash = path.find_last_of('/');
    if (slash == std::string::npos) return -1;
    const std::string suffix = path.substr(slash + 1);
    if (suffix.rfind("event", 0) != 0) return -1;
    char* end = nullptr;
    const long parsed = strtol(suffix.c_str() + 5, &end, 10);
    if (end == suffix.c_str() + 5 || *end != '\0' || parsed < 0 || parsed > 100000) return -1;
    return static_cast<int>(parsed);
}

bool isMouseButton(unsigned int code) {
    return code == BTN_LEFT || code == BTN_RIGHT || code == BTN_MIDDLE ||
           code == BTN_SIDE || code == BTN_EXTRA || code == BTN_BACK ||
           code == BTN_FORWARD || code == BTN_MOUSE;
}

bool isKeyboardKey(unsigned int code) {
    // BTN_* ocupa a faixa a partir de BTN_MISC e não deve vazar como tecla Android
    // em HID composto que também anuncie um teclado.
    return keymapper::input::isKeyboardKeyCode(code);
}

bool inspectDevice(Device& device) {
    unsigned long eventBits[(EV_MAX + sizeof(unsigned long) * 8) / (sizeof(unsigned long) * 8)]{};
    unsigned long relBits[(REL_MAX + sizeof(unsigned long) * 8) / (sizeof(unsigned long) * 8)]{};
    unsigned long keyBits[(KEY_MAX + sizeof(unsigned long) * 8) / (sizeof(unsigned long) * 8)]{};
    if (!getBits(device.fd, 0, eventBits) || !getBits(device.fd, EV_REL, relBits) ||
        !getBits(device.fd, EV_KEY, keyBits)) return false;
    if (!bitSet(eventBits, EV_KEY)) return false;

    const bool hasRel = bitSet(eventBits, EV_REL);
    const bool hasXY = hasRel && bitSet(relBits, REL_X) && bitSet(relBits, REL_Y);
    device.high_res_vertical_wheel = hasRel && bitSet(relBits, REL_WHEEL_HI_RES);
    device.high_res_horizontal_wheel = hasRel && bitSet(relBits, REL_HWHEEL_HI_RES);
    const bool hasButtons = bitSet(keyBits, BTN_LEFT) || bitSet(keyBits, BTN_MOUSE) ||
                            bitSet(keyBits, BTN_RIGHT);
    device.mouse = hasXY && hasButtons;

    // Qualquer tecla real abaixo de BTN_MISC basta para reconhecer um teclado HID.
    // O limite anterior de quatro códigos excluía macro pads de 1–3 teclas e numpads
    // compactos. Botões BTN_* (gamepads, joysticks e mouse) continuam fora desta classe.
    device.keyboard = keymapper::input::hasKeyboardCapabilities(keyBits, sizeof(keyBits) / sizeof(keyBits[0]));
    return device.mouse || device.keyboard;
}

bool sendBatch(JNIEnv* env, std::vector<jlong>& batch,
               EvdevBatchRecovery& recovery, CaptureStats& stats) {
    if (batch.empty() && !recovery.hasPending()) return true;

    // JNI allocation/copy/callback failures are also input loss. The markers must precede any
    // newer records so Kotlin releases stale DOWN state before accepting the new sequence.
    recovery.prependMarkers(batch);
    bool delivered = false;
    if (g_service && g_batch_method && batch.size() <= static_cast<size_t>(std::numeric_limits<jsize>::max())) {
        jlongArray array = env->NewLongArray(static_cast<jsize>(batch.size()));
        if (array) {
            env->SetLongArrayRegion(array, 0, static_cast<jsize>(batch.size()), batch.data());
            if (!env->ExceptionCheck()) {
                env->CallVoidMethod(g_service, g_batch_method, array);
            }
            delivered = !env->ExceptionCheck();
            if (env->ExceptionCheck()) env->ExceptionClear();
            env->DeleteLocalRef(array);
        } else if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
    } else if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }

    if (delivered) {
        recovery.markDelivered();
    } else {
        recovery.markDroppedBatch(batch);
        ++stats.batch_delivery_failures;
    }
    batch.clear();
    return delivered;
}

void sendState(JNIEnv* env, const std::vector<Device>& devices, bool enabled,
               const char* reason, const CaptureStats& stats) {
    if (!g_service || !g_state_method) return;
    int mouseCount = 0;
    int keyboardCount = 0;
    for (const auto& device : devices) {
        const bool ready = device.fd >= 0 && (enabled ? device.grabbed : device.grab_supported);
        if (!ready) continue;
        if (device.mouse) ++mouseCount;
        if (device.keyboard) ++keyboardCount;
    }
    int grabError = 0;
    for (const auto& device : devices) {
        if (device.grab_error != 0) { grabError = device.grab_error; break; }
    }
    char detail[384];
    if (enabled && mouseCount == 0 && keyboardCount == 0) {
        if (grabError != 0) {
            snprintf(detail, sizeof(detail),
                     "%s; nenhum EVIOCGRAB concedido (errno=%d); mouse=%d; teclado=%d; lidos=%llu; enfileirados=%llu; sync_dropped=%llu; erros_leitura=%llu; removidos=%llu; falhas_lote_JNI=%llu",
                     reason, grabError, mouseCount, keyboardCount,
                     static_cast<unsigned long long>(stats.events_read),
                     static_cast<unsigned long long>(stats.events_queued),
                     static_cast<unsigned long long>(stats.sync_dropped),
                     static_cast<unsigned long long>(stats.read_errors),
                     static_cast<unsigned long long>(stats.devices_removed),
                     static_cast<unsigned long long>(stats.batch_delivery_failures));
        } else {
            snprintf(detail, sizeof(detail),
                     "%s; nenhum periférico evdev compatível acessível; mouse=%d; teclado=%d; lidos=%llu; enfileirados=%llu; sync_dropped=%llu; erros_leitura=%llu; removidos=%llu; falhas_lote_JNI=%llu",
                     reason, mouseCount, keyboardCount,
                     static_cast<unsigned long long>(stats.events_read),
                     static_cast<unsigned long long>(stats.events_queued),
                     static_cast<unsigned long long>(stats.sync_dropped),
                     static_cast<unsigned long long>(stats.read_errors),
                     static_cast<unsigned long long>(stats.devices_removed),
                     static_cast<unsigned long long>(stats.batch_delivery_failures));
        }
    } else {
        snprintf(detail, sizeof(detail),
                 "%s; mouse=%d; teclado=%d; lidos=%llu; enfileirados=%llu; sync_dropped=%llu; erros_leitura=%llu; removidos=%llu; falhas_lote_JNI=%llu",
                 reason, mouseCount, keyboardCount,
                 static_cast<unsigned long long>(stats.events_read),
                 static_cast<unsigned long long>(stats.events_queued),
                 static_cast<unsigned long long>(stats.sync_dropped),
                 static_cast<unsigned long long>(stats.read_errors),
                 static_cast<unsigned long long>(stats.devices_removed),
                 static_cast<unsigned long long>(stats.batch_delivery_failures));
    }
    jstring message = env->NewStringUTF(detail);
    if (message) {
        env->CallVoidMethod(g_service, g_state_method, mouseCount, keyboardCount,
                            static_cast<jboolean>(enabled), message);
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(message);
    } else if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
}

void wakeWorker() {
    if (g_wakeup_fd < 0) return;
    const uint64_t value = 1;
    (void)write(g_wakeup_fd, &value, sizeof(value));
}

void drainWakeupFd() {
    if (g_wakeup_fd < 0) return;
    uint64_t value = 0;
    while (read(g_wakeup_fd, &value, sizeof(value)) == sizeof(value)) {}
}

void closeControlFds() {
    if (g_epoll_fd >= 0) close(g_epoll_fd);
    if (g_wakeup_fd >= 0) close(g_wakeup_fd);
    if (g_inotify_fd >= 0) close(g_inotify_fd);
    g_epoll_fd = -1;
    g_wakeup_fd = -1;
    g_inotify_fd = -1;
}

bool registerEpollFd(int fd, uint32_t flags) {
    if (g_epoll_fd < 0 || fd < 0) return false;
    epoll_event event{};
    event.events = flags;
    event.data.fd = fd;
    if (epoll_ctl(g_epoll_fd, EPOLL_CTL_ADD, fd, &event) == 0) return true;
    return errno == EEXIST;
}

int createInputDirectoryWatcher() {
    const int fd = inotify_init1(IN_NONBLOCK | IN_CLOEXEC);
    if (fd < 0) return -1;
    const uint32_t mask = IN_CREATE | IN_DELETE | IN_MOVED_FROM | IN_MOVED_TO | IN_ATTRIB;
    if (inotify_add_watch(fd, "/dev/input", mask) < 0) {
        close(fd);
        return -1;
    }
    return fd;
}

void drainInputDirectoryWatcher(int fd) {
    if (fd < 0) return;
    alignas(inotify_event) char buffer[4096];
    while (true) {
        const ssize_t bytes = read(fd, buffer, sizeof(buffer));
        if (bytes < 0 && errno == EINTR) continue;
        if (bytes <= 0) break;
    }
}

void releaseDevice(Device& device) {
    if (device.fd < 0) return;
    if (device.epoll_registered && g_epoll_fd >= 0) {
        (void)epoll_ctl(g_epoll_fd, EPOLL_CTL_DEL, device.fd, nullptr);
        device.epoll_registered = false;
    }
    if (device.grabbed && ioctl(device.fd, EVIOCGRAB, 0) < 0) {
        // close(fd) também libera EVIOCGRAB. Nunca mantenha um descritor possivelmente
        // agarrado aberto se o ioctl explícito de liberação falhar.
        fprintf(stderr, "W: EVIOCGRAB release failed for %s (errno=%d); closing fd\n",
                device.path.c_str(), errno);
    }
    device.grabbed = false;
    close(device.fd);
    device.fd = -1;
}

void releaseAll(std::vector<Device>& devices) {
    for (auto& device : devices) releaseDevice(device);
    devices.clear();
}

void applyGrabState(Device& device, bool enabled) {
    if (device.fd < 0) return;
    if (!enabled) {
        if (device.grabbed && ioctl(device.fd, EVIOCGRAB, 0) < 0) {
            // Falha de ungrab não pode deixar o periférico preso. Fechar o fd força o kernel
            // a liberar a captura; o scanner reabre o nó numa passagem subsequente.
            device.grab_error = errno;
            if (device.epoll_registered && g_epoll_fd >= 0) {
                (void)epoll_ctl(g_epoll_fd, EPOLL_CTL_DEL, device.fd, nullptr);
                device.epoll_registered = false;
            }
            close(device.fd);
            device.fd = -1;
        }
        device.grabbed = false;
        return;
    }
    if (device.grabbed) return;
    if (ioctl(device.fd, EVIOCGRAB, 1) == 0) {
        device.grabbed = true;
        device.grab_supported = true;
        device.grab_error = 0;
    } else {
        device.grab_error = errno;
    }
}

bool containsPath(const std::vector<Device>& devices, const std::string& path) {
    return std::any_of(devices.begin(), devices.end(), [&](const Device& d) {
        return d.path == path && d.fd >= 0;
    });
}

bool scanDevices(std::vector<Device>& devices, bool enabled) {
    bool changed = false;
    DIR* dir = opendir("/dev/input");
    if (!dir) return false;
    dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        if (strncmp(entry->d_name, "event", 5) != 0) continue;
        const std::string path = std::string("/dev/input/") + entry->d_name;
        if (containsPath(devices, path)) continue;
        const int number = eventNumber(path);
        if (number < 0) continue;
        int fd = open(path.c_str(), O_RDONLY | O_NONBLOCK | O_CLOEXEC);
        if (fd < 0) continue;
        Device device;
        device.path = path;
        device.id = -(number + 100);
        device.fd = fd;
        char name[128]{};
        if (ioctl(fd, EVIOCGNAME(sizeof(name) - 1), name) >= 0) device.name = name;
        // Request monotonic timestamps so MouseSampleClock receives the same time base
        // as Android elapsedRealtimeNanos(). Older kernels may reject this ioctl; evdev's
        // default clock remains in use in that case.
        int clockId = CLOCK_MONOTONIC;
        device.monotonic_clock = ioctl(fd, EVIOCSCLOCKID, &clockId) == 0;
        if (!device.monotonic_clock) {
            // Preserve relative event timing on kernels that keep CLOCK_REALTIME.
            // Bracket the realtime sample with monotonic samples to reduce offset error.
            timespec monoBefore{};
            timespec realtime{};
            timespec monoAfter{};
            if (clock_gettime(CLOCK_MONOTONIC, &monoBefore) == 0 &&
                clock_gettime(CLOCK_REALTIME, &realtime) == 0 &&
                clock_gettime(CLOCK_MONOTONIC, &monoAfter) == 0) {
                const int64_t beforeNs = static_cast<int64_t>(monoBefore.tv_sec) * 1000000000LL + monoBefore.tv_nsec;
                const int64_t realtimeNs = static_cast<int64_t>(realtime.tv_sec) * 1000000000LL + realtime.tv_nsec;
                const int64_t afterNs = static_cast<int64_t>(monoAfter.tv_sec) * 1000000000LL + monoAfter.tv_nsec;
                device.realtime_to_monotonic_ns = ((beforeNs + afterNs) / 2LL) - realtimeNs;
                device.realtime_offset_valid = true;
            }
        }
        if (!inspectDevice(device)) {
            close(fd);
            continue;
        }
        applyGrabState(device, enabled);
        devices.push_back(std::move(device));
        changed = true;
    }
    closedir(dir);
    return changed;
}

void registerDeviceDescriptors(std::vector<Device>& devices) {
    for (auto& device : devices) {
        if (device.fd < 0 || device.epoll_registered) continue;
        if (registerEpollFd(device.fd, EPOLLIN | EPOLLPRI | EPOLLERR | EPOLLHUP)) {
            device.epoll_registered = true;
        } else {
            device.grab_error = errno;
            fprintf(stderr, "W: epoll_ctl add failed for %s (errno=%d); closing device\n",
                    device.path.c_str(), device.grab_error);
            // Um fd que não está no epoll não pode ser contado como dispositivo funcional.
            // Fechar também desfaz qualquer EVIOCGRAB adquirido antes da falha.
            releaseDevice(device);
        }
    }
}

bool pathExists(const std::string& path) {
    return access(path.c_str(), F_OK) == 0;
}

void removeDisconnected(std::vector<Device>& devices, std::vector<jlong>& batch) {
    auto it = devices.begin();
    while (it != devices.end()) {
        if (it->fd < 0 || !pathExists(it->path)) {
            const int deviceClass = (it->mouse ? DEVICE_MOUSE : 0) | (it->keyboard ? DEVICE_KEYBOARD : 0);
            batch.insert(batch.end(), {static_cast<jlong>(deviceClass), EV_SYN, SYN_DROPPED,
                                       1, static_cast<jlong>(it->id), 0});
            releaseDevice(*it);
            it = devices.erase(it);
        } else {
            ++it;
        }
    }
}

jlong eventTimeNanos(const input_event& event, bool monotonicClock,
                     bool realtimeOffsetValid, int64_t realtimeToMonotonicNs) {
    const int64_t eventNs = static_cast<int64_t>(event.time.tv_sec) * 1000000000LL +
                            static_cast<int64_t>(event.time.tv_usec) * 1000LL;
    if (monotonicClock) return static_cast<jlong>(eventNs);
    // Convert CLOCK_REALTIME into the monotonic domain without discarding the event's
    // original spacing. A processing-time stamp is only the last-resort fallback.
    if (realtimeOffsetValid) return static_cast<jlong>(eventNs + realtimeToMonotonicNs);
    timespec now{};
    if (clock_gettime(CLOCK_MONOTONIC, &now) == 0) {
        return static_cast<jlong>(now.tv_sec) * 1000000000LL +
               static_cast<jlong>(now.tv_nsec);
    }
    return 0;
}

void appendEvent(std::vector<jlong>& batch, int deviceClass, const input_event& event,
                 int deviceId, const Device& device) {
    const jlong timeNs = eventTimeNanos(event, device.monotonic_clock,
                                        device.realtime_offset_valid,
                                        device.realtime_to_monotonic_ns);
    batch.insert(batch.end(), {static_cast<jlong>(deviceClass), static_cast<jlong>(event.type),
                               static_cast<jlong>(event.code), static_cast<jlong>(event.value),
                               static_cast<jlong>(deviceId), timeNs});
}

bool processDeviceEvents(JNIEnv* env, Device& device, std::vector<jlong>& batch,
                         CaptureStats& stats, EvdevBatchRecovery& batchRecovery) {
    if (device.fd < 0) return false;
    input_event events[48];
    while (true) {
        const ssize_t bytes = read(device.fd, events, sizeof(events));
        if (bytes < 0) {
            if (errno == EINTR) continue;
            if (errno == EAGAIN || errno == EWOULDBLOCK) return true;
            ++stats.read_errors;
            return false;
        }
        if (bytes == 0 || bytes % static_cast<ssize_t>(sizeof(input_event)) != 0) {
            ++stats.read_errors;
            return false;
        }
        const size_t count = static_cast<size_t>(bytes) / sizeof(input_event);
        stats.events_read += count;
        for (size_t i = 0; i < count; ++i) {
            const input_event& event = events[i];
            if (!device.grabbed || !g_enabled.load()) continue;
            if (event.type == EV_SYN && event.code == SYN_DROPPED) {
                ++stats.sync_dropped;
                device.syncing_after_drop = true;
                const int deviceClass = (device.mouse ? DEVICE_MOUSE : 0) | (device.keyboard ? DEVICE_KEYBOARD : 0);
                batch.insert(batch.end(), {static_cast<jlong>(deviceClass), EV_SYN, SYN_DROPPED,
                                           1, static_cast<jlong>(device.id), 0});
                continue;
            }
            if (device.syncing_after_drop) {
                // evdev requires ignoring the unreliable tail after SYN_DROPPED until
                // the next SYN_REPORT. Key/touch state is released by the app callback.
                if (event.type == EV_SYN && event.code == SYN_REPORT) {
                    device.syncing_after_drop = false;
                }
                continue;
            }
            if (event.type == EV_SYN && event.code == SYN_REPORT && device.mouse) {
                // Mantém o limite do relatório evdev no fluxo JNI. O consumidor combina REL_X/Y
                // e aplica a normalização temporal uma única vez por vetor, não uma vez por eixo.
                appendEvent(batch, DEVICE_MOUSE, event, device.id, device);
                ++stats.events_queued;
            } else if (event.type == EV_REL && device.mouse) {
                if (event.code == REL_WHEEL && device.high_res_vertical_wheel) {
                    // Quando existe canal hi-res, o kernel também pode emitir REL_WHEEL
                    // legado para o mesmo giro. Preferimos o canal de maior resolução.
                    continue;
                }
                if (event.code == REL_HWHEEL && device.high_res_horizontal_wheel) {
                    continue;
                }
                if (event.code == REL_X || event.code == REL_Y || event.code == REL_WHEEL ||
                    event.code == REL_HWHEEL || event.code == REL_WHEEL_HI_RES ||
                    event.code == REL_HWHEEL_HI_RES) {
                    appendEvent(batch, DEVICE_MOUSE, event, device.id, device);
                    ++stats.events_queued;
                }
            } else if (event.type == EV_KEY) {
                if (device.mouse && isMouseButton(event.code)) {
                    appendEvent(batch, DEVICE_MOUSE, event, device.id, device);
                    ++stats.events_queued;
                } else if (device.keyboard && isKeyboardKey(event.code)) {
                    appendEvent(batch, DEVICE_KEYBOARD, event, device.id, device);
                    ++stats.events_queued;
                }
            }
            if (batch.size() >= MAX_BATCH_RECORDS * RECORD_FIELDS) sendBatch(env, batch, batchRecovery, stats);
        }
    }
    return true;
}

void workerMain() {
    JNIEnv* env = nullptr;
    bool attached = false;
    if (g_vm->AttachCurrentThread(reinterpret_cast<void**>(&env), nullptr) != JNI_OK) {
        g_running.store(false);
        return;
    }
    attached = true;

    std::vector<Device> devices;
    std::vector<jlong> batch;
    EvdevBatchRecovery batchRecovery;
    CaptureStats stats;
    bool lastEnabled = false;
    int lastMouse = -1;
    int lastKeyboard = -1;
    auto lastBatchFlush = std::chrono::steady_clock::now();
    auto lastHeartbeat = lastBatchFlush;
    auto lastScan = std::chrono::steady_clock::now() - std::chrono::seconds(2);
    bool scanRequested = true;

    while (g_running.load()) {
        const bool enabled = g_enabled.load();
        bool changed = false;
        if (enabled != lastEnabled) {
            for (auto& device : devices) applyGrabState(device, enabled);
            lastEnabled = enabled;
            changed = true;
        }

        const auto now = std::chrono::steady_clock::now();
        // inotify torna a descoberta de mouse/teclado plugado quase imediata. Se o watcher
        // não estiver disponível nesta ROM, a varredura periódica de segurança continua ativa.
        const auto scanInterval = g_inotify_fd >= 0 ? std::chrono::seconds(5)
                                                    : std::chrono::seconds(1);
        if (scanRequested || now - lastScan >= scanInterval) {
            if (enabled) {
                for (auto& device : devices) {
                    if (!device.grabbed) applyGrabState(device, true);
                }
            }
            changed = scanDevices(devices, enabled) || changed;
            removeDisconnected(devices, batch);
            registerDeviceDescriptors(devices);
            lastScan = now;
            scanRequested = false;
        }

        int mouseCount = 0;
        int keyboardCount = 0;
        for (const auto& device : devices) {
            const bool ready = device.fd >= 0 && (enabled ? device.grabbed : device.grab_supported);
            if (!ready) continue;
            if (device.mouse) ++mouseCount;
            if (device.keyboard) ++keyboardCount;
        }
        if (changed || mouseCount != lastMouse || keyboardCount != lastKeyboard) {
            sendState(env, devices, enabled,
                      enabled ? "captura evdev" : "captura pausada; dispositivos liberados", stats);
            lastHeartbeat = now;
            lastMouse = mouseCount;
            lastKeyboard = keyboardCount;
        }

        epoll_event events[64]{};
        const int timeoutMs = batch.empty() ? (batchRecovery.hasPending() ? 20 : 500) : 2;
        const int ready = epoll_wait(g_epoll_fd, events, 64, timeoutMs);
        if (ready < 0) {
            if (errno == EINTR) continue;
            fprintf(stderr, "E: epoll_wait failed (errno=%d)\n", errno);
            break;
        }

        for (int i = 0; i < ready; ++i) {
            const int fd = events[i].data.fd;
            const uint32_t flags = events[i].events;
            if (fd == g_wakeup_fd) {
                drainWakeupFd();
                continue;
            }
            if (fd == g_inotify_fd && g_inotify_fd >= 0) {
                drainInputDirectoryWatcher(g_inotify_fd);
                scanRequested = true;
                continue;
            }

            auto it = std::find_if(devices.begin(), devices.end(), [&](const Device& d) {
                return d.fd == fd;
            });
            if (it == devices.end()) continue;
            if (flags & (EPOLLHUP | EPOLLERR | EPOLLRDHUP)) {
                const int deviceClass = (it->mouse ? DEVICE_MOUSE : 0) |
                                        (it->keyboard ? DEVICE_KEYBOARD : 0);
                batch.insert(batch.end(), {static_cast<jlong>(deviceClass), EV_SYN, SYN_DROPPED,
                                           1, static_cast<jlong>(it->id), 0});
                releaseDevice(*it);
                devices.erase(it);
                ++stats.devices_removed;
                lastMouse = -1;
                lastKeyboard = -1;
                scanRequested = true;
            } else if (flags & (EPOLLIN | EPOLLPRI)) {
                if (!processDeviceEvents(env, *it, batch, stats, batchRecovery)) {
                    const int deviceClass = (it->mouse ? DEVICE_MOUSE : 0) |
                                            (it->keyboard ? DEVICE_KEYBOARD : 0);
                    batch.insert(batch.end(), {static_cast<jlong>(deviceClass), EV_SYN, SYN_DROPPED,
                                               1, static_cast<jlong>(it->id), 0});
                    releaseDevice(*it);
                    devices.erase(it);
                    ++stats.devices_removed;
                    lastMouse = -1;
                    lastKeyboard = -1;
                    scanRequested = true;
                }
            }
        }
        const auto flushNow = std::chrono::steady_clock::now();
        if (batch.size() >= MAX_BATCH_RECORDS * RECORD_FIELDS ||
            (!batch.empty() && flushNow - lastBatchFlush >= std::chrono::milliseconds(2)) ||
            (batch.empty() && batchRecovery.hasPending() &&
             flushNow - lastBatchFlush >= std::chrono::milliseconds(20))) {
            sendBatch(env, batch, batchRecovery, stats);
            lastBatchFlush = flushNow;
        }
        if (flushNow - lastHeartbeat >= std::chrono::seconds(5)) {
            sendState(env, devices, g_enabled.load(), "heartbeat evdev", stats);
            lastHeartbeat = flushNow;
        }
    }

    sendBatch(env, batch, batchRecovery, stats);
    releaseAll(devices);
    sendState(env, devices, false, "captura encerrada; todos os dispositivos liberados", stats);
    g_running.store(false);
    if (attached) g_vm->DetachCurrentThread();
}
} // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_keymapper_injection_KeyMapperShellService_nativeStartCapture(JNIEnv* env, jobject service, jboolean initiallyEnabled) {
    std::lock_guard<std::mutex> guard(g_lifecycle);
    if (g_running.load()) return JNI_TRUE;
    if (g_worker.joinable()) g_worker.join();
    // Um término inesperado não pode deixar descritores de controle ou referências JNI antigas.
    closeControlFds();
    if (g_service) env->DeleteGlobalRef(g_service);
    g_service = nullptr;
    jclass cls = env->GetObjectClass(service);
    g_batch_method = env->GetMethodID(cls, "onNativeInputBatch", "([J)V");
    g_state_method = env->GetMethodID(cls, "onNativeCaptureState", "(IIZLjava/lang/String;)V");
    env->DeleteLocalRef(cls);
    if (!g_batch_method || !g_state_method || env->ExceptionCheck()) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return JNI_FALSE;
    }
    g_service = env->NewGlobalRef(service);
    g_wakeup_fd = eventfd(0, EFD_CLOEXEC | EFD_NONBLOCK);
    g_epoll_fd = epoll_create1(EPOLL_CLOEXEC);
    if (!g_service || g_wakeup_fd < 0 || g_epoll_fd < 0) {
        if (g_service) env->DeleteGlobalRef(g_service);
        g_service = nullptr;
        closeControlFds();
        return JNI_FALSE;
    }
    g_inotify_fd = createInputDirectoryWatcher(); // optional: periodic scan remains the fallback
    if (!registerEpollFd(g_wakeup_fd, EPOLLIN)) {
        env->DeleteGlobalRef(g_service);
        g_service = nullptr;
        closeControlFds();
        return JNI_FALSE;
    }
    if (g_inotify_fd >= 0 && !registerEpollFd(g_inotify_fd, EPOLLIN)) {
        // O watcher é uma otimização; não deve impedir a captura se esta ROM negar o epoll nele.
        close(g_inotify_fd);
        g_inotify_fd = -1;
    }
    g_enabled.store(initiallyEnabled == JNI_TRUE);
    g_running.store(true);
    try {
        g_worker = std::thread(workerMain);
    } catch (...) {
        g_running.store(false);
        closeControlFds();
        env->DeleteGlobalRef(g_service);
        g_service = nullptr;
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_keymapper_injection_KeyMapperShellService_nativeIsCaptureRunning(JNIEnv*, jobject) {
    return g_running.load() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_keymapper_injection_KeyMapperShellService_nativeSetCaptureEnabled(JNIEnv*, jobject, jboolean enabled) {
    g_enabled.store(enabled == JNI_TRUE);
    wakeWorker();
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_keymapper_injection_KeyMapperShellService_nativeStopCapture(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> guard(g_lifecycle);
    if (g_running.exchange(false)) wakeWorker();
    if (g_worker.joinable()) g_worker.join();
    closeControlFds();
    if (g_service) env->DeleteGlobalRef(g_service);
    g_service = nullptr;
    g_batch_method = nullptr;
    g_state_method = nullptr;
}
