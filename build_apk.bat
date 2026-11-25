@echo off
setlocal EnableExtensions DisableDelayedExpansion
set "PAUSE_AFTER_BUILD=1"
if /i "%~1"=="--no-pause" set "PAUSE_AFTER_BUILD=0"

pushd "%~dp0"
if errorlevel 1 (
    echo ERROR: Cannot open the project folder "%~dp0".
    set "BUILD_RESULT=1"
    goto :Finish
)

call :Build
set "BUILD_RESULT=%ERRORLEVEL%"
popd

:Finish
if not "%BUILD_RESULT%"=="0" echo Build failed. Check the error above.
if "%PAUSE_AFTER_BUILD%"=="1" (
    echo.
    pause
)
exit /b %BUILD_RESULT%

:Build

echo === TG WS Proxy APK Build Script ===
echo === Output: 3 APKs (universal, arm64-v8a, armeabi-v7a) ===
echo.

set "VERSION_NAME="
for /f "tokens=2 delims==" %%V in ('findstr /R /C:"versionName *= *\".*\"" "app\build.gradle.kts"') do (
    set "VERSION_NAME=%%V"
)
set "VERSION_NAME=%VERSION_NAME:"=%"
set "VERSION_NAME=%VERSION_NAME: =%"
if not defined VERSION_NAME (
    echo ERROR: Could not read versionName from app\build.gradle.kts
    exit /b 1
)
set "RELEASE_PREFIX=v%VERSION_NAME%-android"
echo Version: %VERSION_NAME%
echo Base prefix: %RELEASE_PREFIX%
echo.

call :BuildNativeLibraries
if errorlevel 1 (
    echo Native build failed. APK packaging skipped.
    exit /b 1
)
if not defined ANDROID_HOME if not defined ANDROID_SDK_ROOT (
    findstr /B /L /C:"sdk.dir=" "local.properties" >nul 2>&1
    if errorlevel 1 if exist "%LOCALAPPDATA%\Android\Sdk\platforms" (
        set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
    )
)

echo Building release APKs...
call "%~dp0gradlew.bat" assembleRelease --no-daemon

if %errorlevel% neq 0 (
    echo.
    echo BUILD FAILED! Please check the errors above.
    exit /b 1
)

if not exist "app\release" mkdir "app\release"

echo.
echo Copying APKs to release folder...

set "APK_DIR=app\build\outputs\apk"

if exist "%APK_DIR%\universal\release\app-universal-release.apk" (
    copy /Y "%APK_DIR%\universal\release\app-universal-release.apk" "app\release\%RELEASE_PREFIX%-universal.apk" >nul || exit /b 1
    for %%F in ("app\release\%RELEASE_PREFIX%-universal.apk") do echo   [OK] %%~nxF  [%%~zF bytes]
) else (
    echo   [!!] Universal APK not found
    exit /b 1
)

if exist "%APK_DIR%\arm64\release\app-arm64-release.apk" (
    copy /Y "%APK_DIR%\arm64\release\app-arm64-release.apk" "app\release\%RELEASE_PREFIX%-v8a-minsdk24.apk" >nul || exit /b 1
    for %%F in ("app\release\%RELEASE_PREFIX%-v8a-minsdk24.apk") do echo   [OK] %%~nxF  [%%~zF bytes]
) else (
    echo   [!!] arm64-v8a APK not found
    exit /b 1
)

if exist "%APK_DIR%\arm32\release\app-arm32-release.apk" (
    copy /Y "%APK_DIR%\arm32\release\app-arm32-release.apk" "app\release\%RELEASE_PREFIX%-v7a-minsdk21.apk" >nul || exit /b 1
    for %%F in ("app\release\%RELEASE_PREFIX%-v7a-minsdk21.apk") do echo   [OK] %%~nxF  [%%~zF bytes]
) else (
    echo   [!!] armeabi-v7a APK not found
    exit /b 1
)

echo.
echo === DONE ===
echo Output directory: app\release\
echo.
echo   %RELEASE_PREFIX%-universal.apk    - both ARM architectures
echo   %RELEASE_PREFIX%-v8a-minsdk24.apk - 64-bit ARM
echo   %RELEASE_PREFIX%-v7a-minsdk21.apk - 32-bit ARM

echo.
exit /b 0

:BuildNativeLibraries
setlocal
echo === Building Rust proxy library for Android (cdylib) ===
echo === Architectures: arm64-v8a (SDK 24), armeabi-v7a (SDK 21) ===
echo.

set "ROOT_DIR=%~dp0"
if defined ANDROID_NDK_HOME goto :NdkResolved

set "SDK_PATH=%ANDROID_SDK_ROOT%"
if not defined SDK_PATH set "SDK_PATH=%ANDROID_HOME%"
if defined SDK_PATH goto :SdkResolved
if not exist "%ROOT_DIR%local.properties" goto :SdkFromDefault
set "SDK_PATH="
for /f "usebackq delims=" %%A in (`powershell -NoProfile -Command "$bs=[string][char]92; $co=[string][char]58; $l=(Get-Content -LiteralPath (Join-Path $env:ROOT_DIR 'local.properties') | Select-String '^sdk\.dir=' | Select-Object -First 1); if ($l) { $l.Line.Substring(8).Replace($bs+$co,$co).Replace($bs+$bs,$bs) }"`) do set "SDK_PATH=%%A"
if defined SDK_PATH goto :SdkResolved

:SdkFromDefault
set "SDK_PATH=%LOCALAPPDATA%\Android\Sdk"

:SdkResolved
if exist "%SDK_PATH%" goto :SdkOk
echo Error: Android SDK not found (resolved: "%SDK_PATH%").
echo   Set ANDROID_SDK_ROOT, or add sdk.dir to local.properties
exit /b 1

:SdkOk
echo Using SDK: %SDK_PATH%

set "NDK_ROOT=%SDK_PATH%\ndk"
if not exist "%NDK_ROOT%" (
    echo Error: no NDK in "%NDK_ROOT%".
    echo Install NDK using the SDK Manager in Android Studio.
    exit /b 1
)
set "NDK_VER="
for /f "delims=" %%D in ('dir /b /ad /o-n "%NDK_ROOT%"') do (
    if not defined NDK_VER set "NDK_VER=%%D"
)
if not defined NDK_VER (
    echo Error: no NDK version found in "%NDK_ROOT%".
    exit /b 1
)
echo Using NDK: %NDK_VER%

set "ANDROID_NDK_HOME=%NDK_ROOT%\%NDK_VER%"
:NdkResolved
set "NDK_BIN=%ANDROID_NDK_HOME%\toolchains\llvm\prebuilt\windows-x86_64\bin"

for %%F in (aarch64-linux-android24-clang.cmd armv7a-linux-androideabi21-clang.cmd llvm-ar.exe) do (
    if not exist "%NDK_BIN%\%%F" (
        echo ERROR: NDK tool not found at "%NDK_BIN%\%%F".
        exit /b 1
    )
)
where cargo >nul 2>nul
if errorlevel 1 (
    echo Error: cargo not found in PATH. Install Rust from https://rustup.rs
    exit /b 1
)

call rustup target add aarch64-linux-android armv7-linux-androideabi
if errorlevel 1 exit /b 1

set "CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER=%NDK_BIN%\aarch64-linux-android24-clang.cmd"
set "CC_aarch64_linux_android=%NDK_BIN%\aarch64-linux-android24-clang.cmd"
set "AR_aarch64_linux_android=%NDK_BIN%\llvm-ar.exe"

set "CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_LINKER=%NDK_BIN%\armv7a-linux-androideabi21-clang.cmd"
set "CC_armv7_linux_androideabi=%NDK_BIN%\armv7a-linux-androideabi21-clang.cmd"
set "AR_armv7_linux_androideabi=%NDK_BIN%\llvm-ar.exe"

set "RUSTFLAGS=--remap-path-prefix=%USERPROFILE%\.cargo=CARGO_HOME"

if not exist "%ROOT_DIR%app\src\main\jniLibs\arm64-v8a"   mkdir "%ROOT_DIR%app\src\main\jniLibs\arm64-v8a"
if not exist "%ROOT_DIR%app\src\main\jniLibs\armeabi-v7a" mkdir "%ROOT_DIR%app\src\main\jniLibs\armeabi-v7a"

echo.
echo [1/2] arm64-v8a (release, API 24)...
call cargo build --locked --release --target aarch64-linux-android
if errorlevel 1 (
    echo BUILD FAILED for arm64-v8a!
    exit /b 1
)
copy /y "target\aarch64-linux-android\release\libtgwsproxy.so" "app\src\main\jniLibs\arm64-v8a\libtgwsproxy.so" >nul
if errorlevel 1 (
    echo COPY FAILED for arm64-v8a!
    exit /b 1
)

echo.
echo [2/2] armeabi-v7a (release, API 21)...
call cargo build --locked --release --target armv7-linux-androideabi
if errorlevel 1 (
    echo BUILD FAILED for armeabi-v7a!
    exit /b 1
)
copy /y "target\armv7-linux-androideabi\release\libtgwsproxy.so" "app\src\main\jniLibs\armeabi-v7a\libtgwsproxy.so" >nul
if errorlevel 1 (
    echo COPY FAILED for armeabi-v7a!
    exit /b 1
)

for %%F in ("%ROOT_DIR%app\src\main\jniLibs\arm64-v8a\libtgwsproxy.so") do (
    echo arm64-v8a:   OK [%%~zF bytes]
)
for %%F in ("%ROOT_DIR%app\src\main\jniLibs\armeabi-v7a\libtgwsproxy.so") do (
    echo armeabi-v7a: OK [%%~zF bytes]
)

echo.
echo === Native libraries ready ===
echo   arm64-v8a:   app\src\main\jniLibs\arm64-v8a\libtgwsproxy.so
echo   armeabi-v7a: app\src\main\jniLibs\armeabi-v7a\libtgwsproxy.so
echo.
exit /b 0
