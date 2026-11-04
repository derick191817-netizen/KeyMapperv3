cmake_minimum_required(VERSION 3.22.1)
project(keymapper_evdev LANGUAGES CXX)

# Must match NativeBackendLibrary.LOAD_NAME / FILE_NAME in the Kotlin app.
add_library(keymapper_evdev SHARED evdev_capture.cpp uinput_touch.cpp)
target_compile_features(keymapper_evdev PRIVATE cxx_std_17)
target_compile_options(keymapper_evdev PRIVATE -Wall -Wextra -Werror=return-type)
find_library(log-lib log)
target_link_libraries(keymapper_evdev PRIVATE ${log-lib})
