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
scroll 1500
shot light_flowchart
scroll 1100
shot light_sequence

adb shell cmd uimode night yes
shot dark_sequence 8
scroll 1100
shot dark_pie

adb shell cmd uimode night no
