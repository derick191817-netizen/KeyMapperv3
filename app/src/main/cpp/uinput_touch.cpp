#include <jni.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <fcntl.h>
#include <poll.h>
#include <sys/time.h>
#include <sys/ioctl.h>
#include <unistd.h>
#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <algorithm>
#include <cmath>
#include <chrono>
#include <thread>
#include <array>
#include <mutex>
#include <string>
#include <vector>

namespace {
constexpr int kMaxPointerIds = 32;
constexpr int kAbsMaximum = 65535;
constexpr int kMaxFrameEvents = 160;

// Android MotionEvent actionMasked values. Kept here to avoid linking native code to Android UI APIs.
constexpr jint kActionDown = 0;
constexpr jint kActionUp = 1;
constexpr jint kActionMove = 2;
constexpr jint kActionCancel = 3;
constexpr jint kActionPointerDown = 5;
constexpr jint kActionPointerUp = 6;

std::mutex g_touch_mutex;
int g_touch_fd = -1;
bool g_touch_created = false;
int g_touch_slots = 0;
int g_touch_active_count = 0;
int g_next_tracking_id = 1;
std::array<bool, kMaxPointerIds> g_slot_active{};
std::array<int, kMaxPointerIds> g_tracking_ids{};
std::array<int, kMaxPointerIds> g_last_x{};
std::array<int, kMaxPointerIds> g_last_y{};
std::string g_touch_path;
std::string g_touch_status = "uinput não inicializado";

void setStatus(const std::string& status) {
    g_touch_status = status;
}

bool ioctlValue(int fd, unsigned long request, int value) {
    if (ioctl(fd, request, value) == 0) return true;
    char message[192];
    snprintf(message, sizeof(message), "ioctl 0x%lx valor %d falhou (errno=%d)",
             request, value, errno);
    setStatus(message);
    return false;
}

bool setupAbsAxis(int fd, uint16_t code, int minimum, int maximum, int fuzz = 0, int flat = 0) {
    uinput_abs_setup axis{};
    axis.code = code;
    axis.absinfo.minimum = minimum;
    axis.absinfo.maximum = maximum;
    axis.absinfo.fuzz = fuzz;
    axis.absinfo.flat = flat;
    if (ioctl(fd, UI_ABS_SETUP, &axis) == 0) return true;
    char message[192];
    snprintf(message, sizeof(message), "UI_ABS_SETUP eixo %u falhou (errno=%d)", code, errno);
    setStatus(message);
    return false;
}

void closeLocked();

bool writeFrameLocked(const std::vector<input_event>& events) {
    if (g_touch_fd < 0 || events.empty()) return false;
    const char* bytes = reinterpret_cast<const char*>(events.data());
    size_t remaining = events.size() * sizeof(input_event);
    while (remaining > 0) {
        const ssize_t written = write(g_touch_fd, bytes, remaining);
        if (written > 0) {
            if (static_cast<size_t>(written) % sizeof(input_event) != 0) {
                setStatus("write uinput retornou quantidade parcial desalinhada de input_event");
                return false;
            }
            bytes += written;
            remaining -= static_cast<size_t>(written);
            continue;
        }
        if (written < 0 && errno == EINTR) continue;
        if (written < 0 && (errno == EAGAIN || errno == EWOULDBLOCK)) {
            pollfd descriptor{};
            descriptor.fd = g_touch_fd;
            descriptor.events = POLLOUT;
            int ready;
            do {
                ready = poll(&descriptor, 1, 2);
            } while (ready < 0 && errno == EINTR);
            if (ready > 0 && (descriptor.revents & POLLOUT) &&
                !(descriptor.revents & (POLLERR | POLLHUP | POLLNVAL))) continue;
            // Não bloquear a thread serial de entrada por dezenas de milissegundos. Um
            // transporte congestionado deve falhar fechado e ser recriado pelo recovery.
            setStatus("backpressure uinput: quadro não foi liberado em até 2 ms");
            return false;
        }
        char message[160];
        snprintf(message, sizeof(message), "write uinput falhou (errno=%d)", errno);
        setStatus(message);
        return false;
    }
    return true;
}

input_event makeEvent(uint16_t type, uint16_t code, int32_t value) {
    input_event event{};
    timeval now{};
    gettimeofday(&now, nullptr);
    event.time = now;
    event.type = type;
    event.code = code;
    event.value = value;
    return event;
}

int normalizedCoordinate(float value) {
    if (!std::isfinite(value)) return 0;
    const double normalized = (static_cast<double>(value) / static_cast<double>(kAbsMaximum));
    // Valores enviados pelo app já estão em coordenadas da tela. A normalização exata
    // é feita com largura/altura do display no lado Kotlin, antes desta função.
    return static_cast<int>(std::llround(std::clamp(normalized, 0.0, 1.0) * kAbsMaximum));
}

void append(std::vector<input_event>& events, uint16_t type, uint16_t code, int32_t value) {
    if (events.size() < kMaxFrameEvents) events.push_back(makeEvent(type, code, value));
}

void destroyDeviceLocked() {
    const int fd = g_touch_fd;
    const bool created = g_touch_created;
    // Clear the process-side handle before issuing ioctls/close. This helper never attempts
    // to release contacts, so it is safe on every error path and cannot recurse.
    g_touch_fd = -1;
    g_touch_created = false;
    if (fd >= 0) {
        if (created) (void)ioctl(fd, UI_DEV_DESTROY);
        close(fd);
    }
    g_touch_slots = 0;
    g_touch_active_count = 0;
    g_slot_active.fill(false);
    g_tracking_ids.fill(-1);
    g_last_x.fill(0);
    g_last_y.fill(0);
    g_touch_path.clear();
}

bool releaseAllLocked() {
    if (g_touch_fd < 0 || g_touch_active_count == 0) {
        g_slot_active.fill(false);
        g_tracking_ids.fill(-1);
        g_touch_active_count = 0;
        return true;
    }

    std::vector<input_event> events;
    events.reserve(g_touch_active_count * 2 + 3);
    for (int slot = 0; slot < g_touch_slots; ++slot) {
        if (!g_slot_active[slot]) continue;
        append(events, EV_ABS, ABS_MT_SLOT, slot);
        append(events, EV_ABS, ABS_MT_TRACKING_ID, -1);
    }
    append(events, EV_KEY, BTN_TOUCH, 0);
    append(events, EV_KEY, BTN_TOOL_FINGER, 0);
    append(events, EV_SYN, SYN_REPORT, 0);

    if (!writeFrameLocked(events)) {
        const std::string failure = g_touch_status;
        // A partial frame may have reached the kernel. Destroying the virtual device is the
        // only reliable way to guarantee InputReader drops all remaining slots.
        destroyDeviceLocked();
        setStatus("uinput destruído após falha ao liberar contatos: " + failure);
        return false;
    }

    g_slot_active.fill(false);
    g_tracking_ids.fill(-1);
    g_touch_active_count = 0;
    return true;
}

void closeLocked() {
    if (g_touch_fd < 0) {
        destroyDeviceLocked();
        return;
    }
    if (g_touch_active_count > 0 && !releaseAllLocked()) {
        // releaseAllLocked() already destroyed the device after a failed/partial write.
        return;
    }
    destroyDeviceLocked();
}

bool configureDevice(int fd, int slots) {
    const int eventTypes[] = {EV_SYN, EV_KEY, EV_ABS};
    for (const int type : eventTypes) {
        if (!ioctlValue(fd, UI_SET_EVBIT, type)) return false;
    }
    const int keyTypes[] = {BTN_TOUCH, BTN_TOOL_FINGER};
    for (const int code : keyTypes) {
        if (!ioctlValue(fd, UI_SET_KEYBIT, code)) return false;
    }
    const int absTypes[] = {
        ABS_X, ABS_Y, ABS_MT_SLOT, ABS_MT_TRACKING_ID,
        ABS_MT_POSITION_X, ABS_MT_POSITION_Y, ABS_MT_PRESSURE
    };
    for (const int code : absTypes) {
        if (!ioctlValue(fd, UI_SET_ABSBIT, code)) return false;
    }
    if (ioctl(fd, UI_SET_PROPBIT, INPUT_PROP_DIRECT) < 0) {
        char message[160];
        snprintf(message, sizeof(message), "UI_SET_PROPBIT INPUT_PROP_DIRECT falhou (errno=%d)", errno);
        setStatus(message);
        return false;
    }

    int version = 0;
    if (ioctl(fd, UI_GET_VERSION, &version) < 0 || version < 5) {
        char message[160];
        snprintf(message, sizeof(message), "uinput versão %d; é necessário UI_DEV_SETUP/UI_ABS_SETUP (errno=%d)",
                 version, errno);
        setStatus(message);
        return false;
    }

    uinput_setup setup{};
    setup.id.bustype = BUS_VIRTUAL;
    setup.id.vendor = 0x4B4D;
    setup.id.product = 0x0001;
    setup.id.version = 1;
    snprintf(setup.name, sizeof(setup.name), "%s", "KeyMapper Virtual Touch");
    if (ioctl(fd, UI_DEV_SETUP, &setup) < 0) {
        char message[160];
        snprintf(message, sizeof(message), "UI_DEV_SETUP falhou (errno=%d)", errno);
        setStatus(message);
        return false;
    }

    if (!setupAbsAxis(fd, ABS_X, 0, kAbsMaximum) ||
        !setupAbsAxis(fd, ABS_Y, 0, kAbsMaximum) ||
        !setupAbsAxis(fd, ABS_MT_SLOT, 0, slots - 1) ||
        !setupAbsAxis(fd, ABS_MT_TRACKING_ID, 0, 65535) ||
        !setupAbsAxis(fd, ABS_MT_POSITION_X, 0, kAbsMaximum) ||
        !setupAbsAxis(fd, ABS_MT_POSITION_Y, 0, kAbsMaximum) ||
        !setupAbsAxis(fd, ABS_MT_PRESSURE, 0, 255)) {
        return false;
    }
    return true;
}

jboolean openDeviceLocked(jint requestedSlots) {
    const int slots = std::clamp(static_cast<int>(requestedSlots), 1, kMaxPointerIds);
    // UserService may retry capture startup while uinput is already serving a held gesture.
    // Treat an already-open device with enough slots as success; never recreate it mid-contact.
    if (g_touch_fd >= 0 && g_touch_created && g_touch_slots >= slots) {
        setStatus(std::string("uinput já estava aberto em ") + g_touch_path +
                  "; slots=" + std::to_string(g_touch_slots));
        return JNI_TRUE;
    }
    closeLocked();
    const char* candidates[] = {"/dev/uinput", "/dev/input/uinput"};
    int fd = -1;
    for (const char* path : candidates) {
        fd = open(path, O_WRONLY | O_NONBLOCK | O_CLOEXEC);
        if (fd >= 0) {
            g_touch_path = path;
            break;
        }
        char message[192];
        snprintf(message, sizeof(message), "open %s negado/indisponível (errno=%d)", path, errno);
        setStatus(message);
    }
    if (fd < 0) return JNI_FALSE;

    if (!configureDevice(fd, slots)) {
        close(fd);
        g_touch_path.clear();
        return JNI_FALSE;
    }
    if (ioctl(fd, UI_DEV_CREATE) < 0) {
        char message[160];
        snprintf(message, sizeof(message), "UI_DEV_CREATE falhou (errno=%d)", errno);
        setStatus(message);
        close(fd);
        g_touch_path.clear();
        return JNI_FALSE;
    }

    g_touch_fd = fd;
    g_touch_created = true;
    g_touch_slots = slots;
    g_touch_active_count = 0;
    g_slot_active.fill(false);
    g_tracking_ids.fill(-1);
    g_last_x.fill(0);
    g_last_y.fill(0);
    g_next_tracking_id = 1;
    // O InputReader observa hot-plug de /dev/input de forma assíncrona. A pausa ocorre
    // somente na inicialização, fora da thread de entrada/mapeamento do aplicativo.
    std::this_thread::sleep_for(std::chrono::milliseconds(200));
    setStatus(std::string("uinput criado em ") + g_touch_path +
              "; slots=" + std::to_string(slots) +
              "; display alvo permitido pelo app: 0");
    return JNI_TRUE;
}

bool sendTouchFrameLocked(jint action, jint actionIndex,
                          const std::vector<jint>& ids,
                          const std::vector<jfloat>& xs,
                          const std::vector<jfloat>& ys) {
    if (g_touch_fd < 0 || g_touch_slots <= 0) {
        setStatus("dispositivo uinput não está aberto");
        return false;
    }
    if (ids.size() != xs.size() || ids.size() != ys.size() || ids.size() > 16) {
        setStatus("quadro touchscreen inválido: tamanhos divergentes ou mais de 16 ponteiros");
        return false;
    }
    // Rejeitar coordenadas corrompidas antes de alterar a tabela local de slots. Na versão
    // anterior, NaN/Inf era convertido em zero e podia produzir um toque no canto da tela.
    for (size_t i = 0; i < xs.size(); ++i) {
        if (!std::isfinite(xs[i]) || !std::isfinite(ys[i]) ||
            xs[i] < 0.0f || ys[i] < 0.0f ||
            xs[i] > static_cast<float>(kAbsMaximum) ||
            ys[i] > static_cast<float>(kAbsMaximum)) {
            setStatus("quadro touchscreen rejeitado: coordenada não finita ou fora de 0..65535");
            return false;
        }
    }

    std::vector<input_event> events;
    events.reserve(64);
    auto validateSlot = [&](size_t index) -> int {
        if (index >= ids.size()) return -1;
        const int slot = ids[index];
        if (slot < 0 || slot >= g_touch_slots) return -1;
        return slot;
    };
    auto emitPosition = [&](int slot, float x, float y) -> bool {
        if (slot < 0 || slot >= g_touch_slots) return false;
        const int px = normalizedCoordinate(x);
        const int py = normalizedCoordinate(y);
        append(events, EV_ABS, ABS_MT_SLOT, slot);
        append(events, EV_ABS, ABS_MT_POSITION_X, px);
        append(events, EV_ABS, ABS_MT_POSITION_Y, py);
        append(events, EV_ABS, ABS_MT_PRESSURE, 255);
        g_last_x[slot] = px;
        g_last_y[slot] = py;
        return true;
    };
    std::array<bool, kMaxPointerIds> seen{};
    auto validatePointerSet = [&](bool requireAllActive) -> bool {
        for (size_t i = 0; i < ids.size(); ++i) {
            const int slot = validateSlot(i);
            if (slot < 0 || seen[slot]) return false;
            seen[slot] = true;
            if (requireAllActive && !g_slot_active[slot]) return false;
        }
        if (requireAllActive && static_cast<int>(ids.size()) != g_touch_active_count) return false;
        return true;
    };

    if (action == kActionDown || action == kActionPointerDown) {
        if (actionIndex < 0 || static_cast<size_t>(actionIndex) >= ids.size() ||
            static_cast<int>(ids.size()) != g_touch_active_count + 1 || !validatePointerSet(false)) {
            setStatus("ACTION_DOWN com índice, IDs ou quantidade de ponteiros inconsistente");
            return false;
        }
        const int slot = validateSlot(static_cast<size_t>(actionIndex));
        if (slot < 0 || g_slot_active[slot]) {
            setStatus("ACTION_DOWN com slot inválido ou já ativo");
            return false;
        }
        for (size_t i = 0; i < ids.size(); ++i) {
            if (static_cast<int>(i) != actionIndex && !g_slot_active[ids[i]]) {
                setStatus("ACTION_POINTER_DOWN contém ponteiro anterior sem tracking ativo");
                return false;
            }
        }
        const bool first = g_touch_active_count == 0;
        g_slot_active[slot] = true;
        g_tracking_ids[slot] = g_next_tracking_id++;
        if (g_next_tracking_id > 65535) g_next_tracking_id = 1;
        ++g_touch_active_count;
        append(events, EV_ABS, ABS_MT_SLOT, slot);
        append(events, EV_ABS, ABS_MT_TRACKING_ID, g_tracking_ids[slot]);
        emitPosition(slot, xs[static_cast<size_t>(actionIndex)], ys[static_cast<size_t>(actionIndex)]);
        if (actionIndex == 0) {
            append(events, EV_ABS, ABS_X, g_last_x[slot]);
            append(events, EV_ABS, ABS_Y, g_last_y[slot]);
        }
        if (first) {
            append(events, EV_KEY, BTN_TOUCH, 1);
            append(events, EV_KEY, BTN_TOOL_FINGER, 1);
        }
        append(events, EV_SYN, SYN_REPORT, 0);
    } else if (action == kActionMove) {
        if (!validatePointerSet(true)) {
            setStatus("ACTION_MOVE contém IDs duplicados, inativos ou conjunto incompleto");
            return false;
        }
        for (size_t i = 0; i < ids.size(); ++i) {
            const int slot = validateSlot(i);
            emitPosition(slot, xs[i], ys[i]);
            if (i == 0) {
                append(events, EV_ABS, ABS_X, g_last_x[slot]);
                append(events, EV_ABS, ABS_Y, g_last_y[slot]);
            }
        }
        append(events, EV_SYN, SYN_REPORT, 0);
    } else if (action == kActionUp || action == kActionPointerUp) {
        if (actionIndex < 0 || static_cast<size_t>(actionIndex) >= ids.size() ||
            !validatePointerSet(true)) {
            setStatus("ACTION_UP com índice ou conjunto de IDs inconsistente");
            return false;
        }
        const int slot = validateSlot(static_cast<size_t>(actionIndex));
        if (slot < 0 || !g_slot_active[slot]) {
            setStatus("ACTION_UP com slot sem tracking ativo");
            return false;
        }
        append(events, EV_ABS, ABS_MT_SLOT, slot);
        append(events, EV_ABS, ABS_MT_TRACKING_ID, -1);
        g_slot_active[slot] = false;
        g_tracking_ids[slot] = -1;
        g_touch_active_count = std::max(0, g_touch_active_count - 1);
        if (g_touch_active_count == 0) {
            append(events, EV_KEY, BTN_TOUCH, 0);
            append(events, EV_KEY, BTN_TOOL_FINGER, 0);
        } else {
            for (int candidate = 0; candidate < g_touch_slots; ++candidate) {
                if (!g_slot_active[candidate]) continue;
                append(events, EV_ABS, ABS_X, g_last_x[candidate]);
                append(events, EV_ABS, ABS_Y, g_last_y[candidate]);
                break;
            }
        }
        append(events, EV_SYN, SYN_REPORT, 0);
    } else if (action == kActionCancel) {
        return releaseAllLocked();
    } else {
        setStatus("ação MotionEvent não suportada pelo backend uinput");
        return false;
    }

    if (!writeFrameLocked(events)) {
        const std::string failure = g_touch_status;
        // The frame may have been partially consumed by the kernel. Do not send a second
        // speculative frame: destroy the virtual device, which cancels every outstanding slot.
        destroyDeviceLocked();
        setStatus("uinput fechado após falha de quadro: " + failure);
        return false;
    }
    setStatus("uinput ativo; quadro touchscreen enviado ao kernel (consumo pelo app destino não confirmado)");
    return true;
}
} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_keymapper_injection_KeyMapperShellService_nativeOpenVirtualTouch(
    JNIEnv*, jobject, jint maxSlots) {
    std::lock_guard<std::mutex> guard(g_touch_mutex);
    return openDeviceLocked(maxSlots);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_keymapper_injection_KeyMapperShellService_nativeIsVirtualTouchOpen(
    JNIEnv*, jobject) {
    std::lock_guard<std::mutex> guard(g_touch_mutex);
    return g_touch_fd >= 0 && g_touch_created ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_keymapper_injection_KeyMapperShellService_nativeSendVirtualTouchFrame(
    JNIEnv* env, jobject, jint action, jint actionIndex, jint pointerCount,
    jintArray pointerIds, jfloatArray pointerXs, jfloatArray pointerYs) {
    if (pointerIds == nullptr || pointerXs == nullptr || pointerYs == nullptr ||
        pointerCount < 0 || pointerCount > 16) return JNI_FALSE;
    const jsize idCount = env->GetArrayLength(pointerIds);
    const jsize xCount = env->GetArrayLength(pointerXs);
    const jsize yCount = env->GetArrayLength(pointerYs);
    if (idCount < pointerCount || xCount < pointerCount || yCount < pointerCount) return JNI_FALSE;

    std::vector<jint> ids(static_cast<size_t>(pointerCount));
    std::vector<jfloat> xs(static_cast<size_t>(pointerCount));
    std::vector<jfloat> ys(static_cast<size_t>(pointerCount));
    if (pointerCount > 0) {
        env->GetIntArrayRegion(pointerIds, 0, pointerCount, ids.data());
        env->GetFloatArrayRegion(pointerXs, 0, pointerCount, xs.data());
        env->GetFloatArrayRegion(pointerYs, 0, pointerCount, ys.data());
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return JNI_FALSE;
        }
    }
    std::lock_guard<std::mutex> guard(g_touch_mutex);
    return sendTouchFrameLocked(action, actionIndex, ids, xs, ys) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_keymapper_injection_KeyMapperShellService_nativeReleaseVirtualTouch(
    JNIEnv*, jobject) {
    std::lock_guard<std::mutex> guard(g_touch_mutex);
    const bool result = releaseAllLocked();
    setStatus(result ? "todos os contatos virtuais foram liberados" : "falha liberando contatos virtuais");
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_keymapper_injection_KeyMapperShellService_nativeCloseVirtualTouch(
    JNIEnv*, jobject) {
    std::lock_guard<std::mutex> guard(g_touch_mutex);
    closeLocked();
    setStatus("dispositivo uinput fechado");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_keymapper_injection_KeyMapperShellService_nativeVirtualTouchStatus(
    JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> guard(g_touch_mutex);
    return env->NewStringUTF(g_touch_status.c_str());
}
