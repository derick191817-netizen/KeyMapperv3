#pragma once

#include <algorithm>
#include <cstddef>
#include <linux/input.h>

namespace keymapper {
namespace input {

/**
 * Linux EV_KEY uses one numeric namespace for both KEY_* and BTN_* codes.
 * The BTN ranges are not one contiguous interval: KEY_* media/navigation codes
 * (for example KEY_OK and KEY_CHANNELUP) appear between BTN ranges. Exclude only
 * the actual button ranges instead of treating every code >= BTN_MISC as a button.
 */
inline bool isButtonCode(unsigned int code) noexcept {
    return (code >= BTN_MISC && code <= BTN_9) ||
           (code >= BTN_MOUSE && code <= BTN_TASK) ||
           (code >= BTN_JOYSTICK && code <= BTN_DEAD) ||
           (code >= BTN_GAMEPAD && code < BTN_DIGI) ||
           (code >= BTN_DIGI && code <= BTN_TOOL_QUADTAP) ||
           (code >= BTN_WHEEL && code <= BTN_GEAR_UP) ||
           (code >= BTN_DPAD_UP && code <= 0x227) || // BTN_DPAD_* and BTN_GRIP*
           (code >= BTN_TRIGGER_HAPPY && code <= KEY_MAX);
}

/** Returns true for a valid Linux keyboard/consumer key, never for BTN_* input. */
inline bool isKeyboardKeyCode(unsigned int code) noexcept {
    return code > KEY_RESERVED && code <= KEY_MAX && !isButtonCode(code);
}

/**
 * A device is keyboard-capable when its EV_KEY bitmap contains at least one
 * KEY_* code. Macro pads and consumer-control keyboards are valid, including
 * media/navigation keys that live numerically above BTN_MISC.
 */
inline bool hasKeyboardCapabilities(const unsigned long* keyBits, std::size_t wordCount) noexcept {
    if (keyBits == nullptr || wordCount == 0) return false;
    constexpr unsigned int bitsPerWord = sizeof(unsigned long) * 8;
    for (unsigned int code = 1; code <= KEY_MAX; ++code) {
        if (!isKeyboardKeyCode(code)) continue;
        const std::size_t word = code / bitsPerWord;
        if (word >= wordCount) break;
        if ((keyBits[word] & (1UL << (code % bitsPerWord))) != 0) return true;
    }
    return false;
}

} // namespace input
} // namespace keymapper
