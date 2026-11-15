# M1 Automated & Interactive QA Script for FF Launcher v1.2.1
$DEV = "52ab3f9e"
$PKG = "com.ff.launcher"
$OUT = "docs\m1"
New-Item -ItemType Directory -Force $OUT | Out-Null

function A { adb -s $DEV @args }
function Tap($x,$y) { A shell input tap $x $y }
function Swipe($x1,$y1,$x2,$y2,$ms=300) { A shell input swipe $x1 $y1 $x2 $y2 $ms }
function LongPress($x,$y,$ms=900) { A shell input swipe $x $y $x $y $ms }
function Snap($name) { A shell screencap -p /sdcard/$name.png; A pull /sdcard/$name.png $OUT\$name.png | Out-Null }

Write-Host "=== Starting M1 Verification Pass for FF Launcher v1.2.1 ==="

# 1. Device and Build Facts
A shell getprop ro.build.version.release | Out-File $OUT\device.txt -Encoding utf8
A shell getprop ro.miui.ui.version.name   | Out-File $OUT\device.txt -Append -Encoding utf8
A shell wm size                           | Out-File $OUT\device.txt -Append -Encoding utf8
A shell dumpsys package $PKG | Select-String "versionName|versionCode|firstInstallTime" |
  Out-File $OUT\device.txt -Append -Encoding utf8
$apk = Get-Item app\build\outputs\apk\debug\app-debug.apk
"APK size: $([math]::Round($apk.Length / 1MB, 2)) MB ($($apk.Length) bytes)" | Out-File $OUT\device.txt -Append -Encoding utf8

# 2. Reset logcat and start app
A logcat -G 16M
A logcat -b all -c
A shell am force-stop $PKG
A shell am start -W -n "$PKG/.MainActivity" | Out-File $OUT\cold_start.txt -Encoding utf8
Start-Sleep 3
$appPid = (A shell pidof $PKG).Trim()
"pid: $appPid" | Out-File $OUT\pids.txt -Encoding utf8
Write-Host "Launcher running with PID $appPid"

# Snap initial home screen
Snap "01_home_screen"
Write-Host "Captured 01_home_screen.png"
