@echo off
setlocal
title CHRONOS - one-click ADB install
set "ROOT=%~dp0"
set "APK=%ROOT%apk\release\app-release.apk"
if not exist "%APK%" set "APK=%ROOT%app-release.apk"

if not exist "%APK%" (
  echo [X] app-release.apk not found.
  echo     Put this script in the repo root ^(next to the apk folder^)
  echo     or in the same folder as app-release.apk.
  pause
  exit /b 1
)

set "ADB="
where adb >nul 2>&1 && set "ADB=adb"
if not defined ADB if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB (
  echo [X] adb not found.
  echo     Install Android Platform Tools and add it to PATH,
  echo     or put adb.exe in this folder.
  echo     https://developer.android.com/tools/releases/platform-tools
  pause
  exit /b 1
)

echo --- connected devices ---------------------------------------
"%ADB%" devices
echo.
echo If the phone shows "unauthorized": unlock it and tap Allow.
pause

echo Installing:
echo   %APK%
"%ADB%" install -r "%APK%"
if errorlevel 1 (
  echo.
  echo [X] Install failed. Check USB debugging, unlock the phone,
  echo     and accept the "Allow USB debugging" prompt.
  pause
  exit /b 1
)

echo.
echo [OK] Installed. Opening CHRONOS...
"%ADB%" shell monkey -p com.chronos.clock -c android.intent.category.LAUNCHER 1 >nul 2>&1
echo Done.
pause
