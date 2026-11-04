package com.example.keymapper.injection;

/**
 * Explicit Shizuku UserService contract. Transaction IDs 16777114 and 1 are reserved
 * by Shizuku for destroy()/exit(); existing inspectRuntime transaction remains 2.
 * No arbitrary shell-command API is exposed.
 */
interface IKeyMapperShellService {
    void destroy() = 16777114;
    void exit() = 1;
    String inspectRuntime() = 2;
    boolean startInputCapture(IKeyMapperInputCallback callback, boolean enabled) = 3;
    void setInputCaptureEnabled(boolean enabled) = 4;
    void stopInputCapture() = 5;
    boolean openVirtualTouch(int maxSlots) = 6;
    boolean sendVirtualTouchFrame(int actionMasked, int actionIndex, int pointerCount,
        in int[] pointerIds, in float[] pointerXs, in float[] pointerYs) = 7;
    boolean releaseVirtualTouch() = 8;
    void closeVirtualTouch() = 9;
    String virtualTouchStatus() = 10;
    boolean isVirtualTouchOpen() = 11;
}
