#!/bin/sh
# Ativa o Shizuku via ADB (Android 10 ou inferior, ou qualquer versao com cabo USB).
# Requisitos: adb instalado, Depuracao USB ligada, app Shizuku aberto ao menos uma vez.
adb start-server
adb devices
adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
