#!/usr/bin/env bash
# Installs the debug APK on a running emulator and captures README screenshots into docs/screenshots.
set -euo pipefail

APK=composeApp/build/outputs/apk/debug/composeApp-debug.apk
PKG=io.github.mikolajkakol.easymarkdown.debug
OUT=docs/screenshots
mkdir -p "$OUT"

close_dialogs() { adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null || true; }
shot() { sleep "${2:-4}"; close_dialogs; sleep 1; adb exec-out screencap -p > "$OUT/$1.png"; echo "captured $1"; }
scroll() { close_dialogs; adb shell input swipe 540 1800 540 $((1800 - $1)) 600; }

adb install -r "$APK"

# Taps the first or last on-screen element with the given accessibility label.
tap_label() {
    close_dialogs
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null
    read -r X Y < <(adb exec-out cat /sdcard/ui.xml | python3 -c '
import re, sys
label, which = sys.argv[1], sys.argv[2]
nodes = re.findall(r"content-desc=\"" + re.escape(label) + r"\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", sys.stdin.read())
x1, y1, x2, y2 = map(int, nodes[0 if which == "first" else -1])
print((x1 + x2) // 2, (y1 + y2) // 2)' "$1" "$2")
    echo "tapping '$1' at $X,$Y"
    adb shell input tap "$X" "$Y"
}

# Verify the app is offered for markdown files, including ones reported with a generic MIME type.
check_handler() {
    if adb shell cmd package query-activities --brief -a android.intent.action.VIEW -d "$1" -t "$2" | grep -q "$PKG/"; then
        echo "OK: handles $2 $1"
    else
        echo "FAIL: not offered for $2 $1"; exit 1
    fi
}
check_handler "content://com.android.externalstorage.documents/document/primary%3ADownload%2Fnotes.md" "text/markdown"
check_handler "content://com.android.externalstorage.documents/document/primary%3ADownload%2Fnotes.md" "application/octet-stream"
check_handler "content://com.example.files/root/my.notes.v2.markdown" "application/octet-stream"
check_handler "file:///sdcard/Download/readme.md" "text/x-markdown"

# The emulator launcher tends to ANR right after boot and its dialog swallows input; it is not needed here.
sleep 20
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher || true
close_dialogs

# Clean status bar.
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false

adb shell cmd uimode night no
adb shell am start -W -n "$PKG/io.github.mikolajkakol.easymarkdown.MainActivity"
shot light_top 10
tap_label "Markdown table" last
shot light_table 4
adb shell input keyevent KEYCODE_BACK
sleep 2
scroll 1300
scroll 700
shot light_diagrams
# Tap the first diagram on screen (found by its accessibility label) to open it full screen.
tap_label "Mermaid diagram" first
shot light_fullscreen 6
echo "--- WebView console ---"; adb logcat -d -s EasyMarkdown:D | tail -40
adb shell input keyevent KEYCODE_BACK

# Switching theme recreates the activity; scroll again once diagrams re-rendered.
adb shell cmd uimode night yes
sleep 8
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/io.github.mikolajkakol.easymarkdown.MainActivity"
sleep 8
close_dialogs
scroll 1300
scroll 900
scroll 700
shot dark_diagrams
# Scroll to the ASCII diagram at the end and open it full screen.
scroll 1500
scroll 1500
scroll 1500
tap_label "Code block" last
shot dark_code 4
adb shell input keyevent KEYCODE_BACK

adb shell cmd uimode night no
