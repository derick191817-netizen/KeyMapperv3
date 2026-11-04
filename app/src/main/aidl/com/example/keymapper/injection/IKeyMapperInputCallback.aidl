package com.example.keymapper.injection;

/** One-way high-frequency input channel from the privileged UserService to the app. */
oneway interface IKeyMapperInputCallback {
    void onInputEvents(in long[] records);
    void onCaptureState(int mouseDevices, int keyboardDevices, boolean enabled, String detail);
}
