#include "../../app/src/main/cpp/evdev_device_classification.h"

#include <cassert>
#include <cstddef>
#include <vector>

static void setCapability(std::vector<unsigned long>& bits, unsigned int code) {
    constexpr unsigned int bitsPerWord = sizeof(unsigned long) * 8;
    const std::size_t word = code / bitsPerWord;
    assert(word < bits.size());
    bits[word] |= (1UL << (code % bitsPerWord));
}

int main() {
    constexpr unsigned int bitsPerWord = sizeof(unsigned long) * 8;
    constexpr std::size_t words = (KEY_MAX + bitsPerWord) / bitsPerWord;
    std::vector<unsigned long> bits(words, 0UL);

    assert(!keymapper::input::hasKeyboardCapabilities(bits.data(), bits.size()));
    assert(!keymapper::input::hasKeyboardCapabilities(nullptr, 0));
    assert(!keymapper::input::hasKeyboardCapabilities(bits.data(), 0));

    // Compact macro pads remain discoverable, even with only one to three keys.
    setCapability(bits, KEY_A);
    assert(keymapper::input::isKeyboardKeyCode(KEY_A));
    assert(!keymapper::input::isKeyboardKeyCode(KEY_RESERVED));
    assert(keymapper::input::hasKeyboardCapabilities(bits.data(), bits.size()));
    bits.assign(words, 0UL);
    setCapability(bits, KEY_KP1);
    setCapability(bits, KEY_KP2);
    setCapability(bits, KEY_KP3);
    assert(keymapper::input::hasKeyboardCapabilities(bits.data(), bits.size()));

    // Consumer/media/navigation keys are KEY_* even when their numbers exceed BTN_MISC.
    assert(KEY_OK > BTN_MISC);
    assert(KEY_CHANNELUP > BTN_MISC);
    assert(keymapper::input::isKeyboardKeyCode(KEY_OK));
    assert(keymapper::input::isKeyboardKeyCode(KEY_CHANNELUP));
    assert(keymapper::input::isKeyboardKeyCode(KEY_PLAYPAUSE));
    bits.assign(words, 0UL);
    setCapability(bits, KEY_OK);
    setCapability(bits, KEY_CHANNELUP);
    assert(keymapper::input::hasKeyboardCapabilities(bits.data(), bits.size()));

    // Only actual BTN_* intervals are excluded; common controller ranges stay out.
    // Include the newer grip-button codes adjacent to the D-pad range (0x224-0x227).
    const unsigned int buttonCodes[] = {
        BTN_0, BTN_LEFT, BTN_TASK, BTN_TRIGGER, BTN_SOUTH, BTN_TOOL_PEN,
        BTN_GEAR_UP, BTN_DPAD_UP, BTN_DPAD_RIGHT, 0x224, 0x225, 0x226, 0x227,
        BTN_TRIGGER_HAPPY1
    };
    for (const auto code : buttonCodes) {
        assert(keymapper::input::isButtonCode(code));
        assert(!keymapper::input::isKeyboardKeyCode(code));
    }
    bits.assign(words, 0UL);
    setCapability(bits, BTN_LEFT);
    setCapability(bits, BTN_GAMEPAD);
    setCapability(bits, BTN_DPAD_UP);
    setCapability(bits, 0x224); // BTN_GRIPL
    setCapability(bits, 0x227); // BTN_GRIPR2
    setCapability(bits, BTN_TRIGGER_HAPPY1);
    assert(!keymapper::input::hasKeyboardCapabilities(bits.data(), bits.size()));

    // Mixed HID: valid consumer keys are accepted while adjacent BTN codes are rejected.
    bits.assign(words, 0UL);
    setCapability(bits, KEY_ENTER);
    setCapability(bits, KEY_OK);
    setCapability(bits, BTN_SOUTH);
    setCapability(bits, BTN_DPAD_UP);
    assert(keymapper::input::hasKeyboardCapabilities(bits.data(), bits.size()));
    assert(keymapper::input::isKeyboardKeyCode(KEY_ENTER));
    assert(keymapper::input::isKeyboardKeyCode(KEY_OK));
    assert(!keymapper::input::isKeyboardKeyCode(BTN_SOUTH));
    assert(!keymapper::input::isKeyboardKeyCode(BTN_DPAD_UP));

    return 0;
}
