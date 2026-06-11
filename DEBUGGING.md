# Debugging

## adb logcat commands, run from a computer
Linux and macOS:
`adb logcat --pid=$(adb shell pidof org.cygnusx1.continuum)`

Windows CMD:
`FOR /F "tokens=*" %i IN ('adb shell pidof org.cygnusx1.continuum') DO set APP_PID=%i adb logcat --pid=%APP_PID%`

## adb install and setup instructions:
https://github.com/cygnusx-1-org/Slide/blob/master/docs/DEBUGGING.md

# Android device only
## Steps
1. Install Shizuku https://github.com/RikkaApps/Shizuku/releases/
2. Install F-Droid if necessary. https://f-droid.org/en/packages/org.fdroid.fdroid/
3. Install LogFox from F-Droid.
4. Setup Shizuku.
  - Enable Developer Options
  - Enable Wireless Debugging in Developer Options
  - Disable Disable adb authorization timeout in Developer Options
5. Run LogFox.
  - If you get an error when pressing the Shizuku button, make sure Shizuku is running.
6. Go to the Crashes tab in LogFox.
7. Find the crash. Recause the crash if needed.
8. Copy the crash log from LogFox, and paste it here.
