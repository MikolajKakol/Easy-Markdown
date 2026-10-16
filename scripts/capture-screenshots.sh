#!/usr/bin/env bash
# Installs the debug APK on a running emulator and captures README screenshots into docs/screenshots.
set -euo pipefail

APK=composeApp/build/outputs/apk/debug/composeApp-debug.apk
PKG=io.github.mikolajkakol.easymarkdown.debug
OUT=docs/screenshots
mkdir -p "$OUT"

shot() { sleep "${2:-4}"; adb exec-out screencap -p > "$OUT/$1.png"; echo "captured $1"; }
scroll() { adb shell input swipe 540 1800 540 $((1800 - $1)) 600; }

adb install -r "$APK"

# Clean status bar.
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false

adb shell cmd uimode night no
adb shell am start -W -n "$PKG/io.github.mikolajkakol.easymarkdown.MainActivity"
shot light_top 10
scroll 1300
shot light_diagrams
# Tap the flowchart to open it full screen.
adb shell input tap 540 1080
shot light_fullscreen 6
adb shell input keyevent KEYCODE_BACK

# Switching theme recreates the activity; scroll again once diagrams re-rendered.
adb shell cmd uimode night yes
sleep 8
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/io.github.mikolajkakol.easymarkdown.MainActivity"
sleep 8
scroll 1300
scroll 900
shot dark_diagrams

adb shell cmd uimode night no
